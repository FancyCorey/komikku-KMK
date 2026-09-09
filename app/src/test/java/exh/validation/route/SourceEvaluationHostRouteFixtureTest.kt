package exh.validation.route

import android.content.Context
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import exh.recs.evaluation.EvaluationCandidate
import exh.recs.evaluation.ScreenErrorKey
import exh.recs.evaluation.ShizukuSetupHelper
import exh.recs.evaluation.SourceEvaluationCandidateFilter
import exh.recs.evaluation.SourceEvaluationContinuationMetadata
import exh.recs.evaluation.SourceEvaluationJobGateway
import exh.recs.evaluation.SourceEvaluationOptions
import exh.recs.evaluation.SourceEvaluationProgressLabelPolicy
import exh.recs.evaluation.SourceEvaluationQueueState
import exh.recs.evaluation.SourceEvaluationScreenModel
import exh.util.FakePreferenceStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.interactor.ClearSourceEvaluationProbeMarker
import tachiyomi.domain.taste.interactor.ClearSourceEvaluationUnsafe
import tachiyomi.domain.taste.interactor.ClearSourceEvaluations
import tachiyomi.domain.taste.interactor.ClearUnsafeExtensionPackages
import tachiyomi.domain.taste.interactor.DeleteSourceEvaluationUnsafe
import tachiyomi.domain.taste.interactor.DeleteUnsafeExtensionPackage
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.interactor.GetSourceEvaluationProbeMarker
import tachiyomi.domain.taste.interactor.GetSourceEvaluationUnsafeSources
import tachiyomi.domain.taste.interactor.GetSourceEvaluations
import tachiyomi.domain.taste.interactor.GetSourceRecommendationFit
import tachiyomi.domain.taste.interactor.GetTasteProfile
import tachiyomi.domain.taste.interactor.GetUnsafeExtensionPackages
import tachiyomi.domain.taste.interactor.MarkSourceEvaluationUnsafe
import tachiyomi.domain.taste.interactor.UpsertUnsafeExtensionPackage
import tachiyomi.domain.taste.model.SourceEvaluationVerdict
import tachiyomi.domain.taste.model.TasteProfile

class SourceEvaluationHostRouteFixtureTest {
    @Test
    fun `route remains loading until candidates arrive then reaches empty idle state`() {
        HostRouteTestEnvironment().use { environment ->
            val candidates = MutableSharedFlow<SourceEvaluationCandidateFilter.CandidatePoolResult>(replay = 1)
            val fixture = fixture(environment, candidates)

            environment.runCurrent()
            assertTrue(fixture.model.state.value.isLoadingCandidates)

            candidates.tryEmit(pool())
            environment.advanceUntilIdle()

            assertFalse(fixture.model.state.value.isLoadingCandidates)
            assertTrue(fixture.model.state.value.candidates.isEmpty())
            assertEquals(SourceEvaluationQueueState.Status.Idle, fixture.model.state.value.queueState.status)
        }
    }

    @Test
    fun `candidate load failure reaches typed terminal screen error`() {
        HostRouteTestEnvironment().use { environment ->
            val fixture = fixture(
                environment = environment,
                candidateFlow = flow { throw IllegalStateException("synthetic-candidate-failure") },
            )

            environment.advanceUntilIdle()

            assertFalse(fixture.model.state.value.isLoadingCandidates)
            assertTrue(fixture.model.state.value.screenError is ScreenErrorKey.CandidateLoadFailed)
            assertTrue(fixture.model.state.value.candidates.isEmpty())
        }
    }

    @Test
    fun `public start stages a bounded request and reports running progress`() {
        HostRouteTestEnvironment().use { environment ->
            val candidate = candidate("start")
            val fixture = fixture(environment, flowOf(pool(candidate)))
            environment.advanceUntilIdle()

            fixture.model.setBatchSize(1)
            fixture.model.startEvaluation()
            environment.advanceUntilIdle()

            assertEquals(1, fixture.gateway.starts.size)
            assertEquals(listOf(candidate), fixture.gateway.starts.single().candidates)
            assertEquals(SourceEvaluationQueueState.Status.Running, fixture.model.state.value.queueState.status)
            assertEquals(1, fixture.model.state.value.queueState.totalCount)
        }
    }

    @Test
    fun `offline start is rejected before the job gateway`() {
        HostRouteTestEnvironment().use { environment ->
            val fixture = fixture(environment, flowOf(pool(candidate("offline"))), online = false)
            environment.advanceUntilIdle()

            fixture.model.startEvaluation()
            environment.advanceUntilIdle()

            assertTrue(fixture.model.state.value.screenError is ScreenErrorKey.Offline)
            assertTrue(fixture.gateway.starts.isEmpty())
        }
    }

    @Test
    fun `mixed completion preserves successful and failed results`() {
        HostRouteTestEnvironment().use { environment ->
            val fixture = fixture(environment, flowOf(pool(candidate("mixed"))))
            environment.advanceUntilIdle()
            fixture.model.startEvaluation()
            environment.advanceUntilIdle()

            fixture.gateway.emit(
                fixture.gateway.activeRunId,
                SourceEvaluationQueueState(
                    status = SourceEvaluationQueueState.Status.Completed,
                    totalCount = 2,
                    completedCount = 2,
                    failedCount = 1,
                    results = listOf(
                        result("good", SourceEvaluationVerdict.STRONG_FIT),
                        result("failed", SourceEvaluationVerdict.ERROR),
                    ),
                ),
                completedKeys = setOf("good", "failed"),
            )
            environment.advanceUntilIdle()

            val queue = fixture.model.state.value.queueState
            assertEquals(SourceEvaluationQueueState.Status.Completed, queue.status)
            assertEquals(2, queue.results.size)
            assertEquals(1, queue.strongFitCount)
            assertEquals(1, queue.errorCount)
        }
    }

    @Test
    fun `every non-success terminal status remains distinct`() {
        val statuses = listOf(
            SourceEvaluationQueueState.Status.Failed,
            SourceEvaluationQueueState.Status.ConnectivityLost,
            SourceEvaluationQueueState.Status.NoActionableWork,
        )

        statuses.forEach { status ->
            HostRouteTestEnvironment().use { environment ->
                val fixture = fixture(environment, flowOf(pool(candidate(status.name))))
                environment.advanceUntilIdle()
                fixture.model.startEvaluation()
                environment.advanceUntilIdle()

                fixture.gateway.emit(
                    fixture.gateway.activeRunId,
                    SourceEvaluationQueueState(status = status, errorMessage = "synthetic-${status.name}"),
                )
                environment.advanceUntilIdle()

                assertEquals(status, fixture.model.state.value.queueState.status)
            }
        }
    }

    @Test
    fun `public cancel invalidates the run and reaches cancelled`() {
        HostRouteTestEnvironment().use { environment ->
            val fixture = fixture(environment, flowOf(pool(candidate("cancel"))))
            environment.advanceUntilIdle()
            fixture.model.startEvaluation()
            environment.advanceUntilIdle()
            val cancelledRun = fixture.gateway.activeRunId

            fixture.model.cancelEvaluation()
            environment.advanceUntilIdle()

            assertEquals(1, fixture.gateway.cancelCount)
            assertEquals(SourceEvaluationQueueState.Status.Cancelled, fixture.model.state.value.queueState.status)
            fixture.gateway.emit(
                cancelledRun,
                SourceEvaluationQueueState(status = SourceEvaluationQueueState.Status.Completed),
            )
            environment.advanceUntilIdle()
            assertEquals(SourceEvaluationQueueState.Status.Cancelled, fixture.model.state.value.queueState.status)
        }
    }

    @Test
    fun `retry owns a new generation and ignores stale completion`() {
        HostRouteTestEnvironment().use { environment ->
            val fixture = fixture(environment, flowOf(pool(candidate("retry"))))
            environment.advanceUntilIdle()
            fixture.model.startEvaluation()
            environment.advanceUntilIdle()
            val firstRun = fixture.gateway.activeRunId
            fixture.gateway.emit(firstRun, SourceEvaluationQueueState(status = SourceEvaluationQueueState.Status.Failed))
            environment.advanceUntilIdle()

            fixture.model.startEvaluation()
            environment.advanceUntilIdle()
            val secondRun = fixture.gateway.activeRunId
            assertTrue(secondRun > firstRun)

            fixture.gateway.emit(firstRun, SourceEvaluationQueueState(status = SourceEvaluationQueueState.Status.Completed))
            environment.advanceUntilIdle()
            assertEquals(SourceEvaluationQueueState.Status.Running, fixture.model.state.value.queueState.status)

            fixture.gateway.emit(secondRun, SourceEvaluationQueueState(status = SourceEvaluationQueueState.Status.Completed))
            environment.advanceUntilIdle()
            assertEquals(SourceEvaluationQueueState.Status.Completed, fixture.model.state.value.queueState.status)
        }
    }

    @Test
    fun `running job survives route leave and return while terminal completion clears on leave`() {
        val gateway = FakeSourceEvaluationJobGateway()
        val preferences = preferences()
        HostRouteTestEnvironment().use { firstEnvironment ->
            val first = fixture(
                environment = firstEnvironment,
                candidateFlow = flowOf(pool(candidate("return"))),
                gateway = gateway,
                sourcePreferences = preferences,
            )
            firstEnvironment.advanceUntilIdle()
            first.model.startEvaluation()
            firstEnvironment.advanceUntilIdle()
            assertEquals(SourceEvaluationQueueState.Status.Running, first.model.state.value.queueState.status)
        }

        HostRouteTestEnvironment().use { secondEnvironment ->
            val second = fixture(
                environment = secondEnvironment,
                candidateFlow = flowOf(pool(candidate("return"))),
                gateway = gateway,
                sourcePreferences = preferences,
            )
            secondEnvironment.advanceUntilIdle()
            assertEquals(SourceEvaluationQueueState.Status.Running, second.model.state.value.queueState.status)

            gateway.emit(gateway.activeRunId, SourceEvaluationQueueState(status = SourceEvaluationQueueState.Status.Completed))
            secondEnvironment.advanceUntilIdle()
            second.model.clearCompletionOnLeave()
            secondEnvironment.advanceUntilIdle()
            assertNull(gateway.queueState.value)
            assertEquals(1, gateway.resetCount)
        }
    }

    @Test
    fun `disposed route rejects later gateway state while the process job continues`() {
        val gateway = FakeSourceEvaluationJobGateway()
        lateinit var disposedModel: SourceEvaluationScreenModel
        HostRouteTestEnvironment().use { environment ->
            val fixture = fixture(
                environment = environment,
                candidateFlow = flowOf(pool(candidate("disposed"))),
                gateway = gateway,
            )
            disposedModel = fixture.model
            environment.advanceUntilIdle()
            fixture.model.startEvaluation()
            environment.advanceUntilIdle()
            assertEquals(SourceEvaluationQueueState.Status.Running, disposedModel.state.value.queueState.status)
        }

        gateway.emit(
            gateway.activeRunId,
            SourceEvaluationQueueState(status = SourceEvaluationQueueState.Status.Completed),
        )

        assertEquals(SourceEvaluationQueueState.Status.Running, disposedModel.state.value.queueState.status)
    }

    @Test
    fun `Evaluation Mode progress presentation redacts raw extension and source names`() {
        HostRouteTestEnvironment().use { environment ->
            val fixture = fixture(environment, flowOf(pool(candidate("privacy"))))
            environment.advanceUntilIdle()
            fixture.model.startEvaluation()
            environment.advanceUntilIdle()
            fixture.gateway.emit(
                fixture.gateway.activeRunId,
                SourceEvaluationQueueState(
                    status = SourceEvaluationQueueState.Status.Running,
                    currentExtensionName = "Private Extension Name",
                    currentSourceName = "Private Source Name",
                ),
            )
            environment.advanceUntilIdle()

            val labels = SourceEvaluationProgressLabelPolicy.labels(
                fixture.model.state.value.queueState,
                evaluationModeEnabled = true,
            )
            val combined = listOfNotNull(labels.extension, labels.source).joinToString(" ")
            assertFalse(combined.contains("Private Extension Name"))
            assertFalse(combined.contains("Private Source Name"))
            assertTrue(labels.extension?.isNotBlank() == true)
            assertTrue(labels.source?.isNotBlank() == true)

            val unredacted = SourceEvaluationProgressLabelPolicy.labels(
                fixture.model.state.value.queueState,
                evaluationModeEnabled = false,
            )
            assertEquals("Private Extension Name", unredacted.extension)
            assertEquals("Private Source Name", unredacted.source)
        }
    }

    private fun fixture(
        environment: HostRouteTestEnvironment,
        candidateFlow: Flow<SourceEvaluationCandidateFilter.CandidatePoolResult>,
        gateway: FakeSourceEvaluationJobGateway = FakeSourceEvaluationJobGateway(),
        sourcePreferences: SourcePreferences = preferences(),
        online: Boolean = true,
    ): Fixture {
        val context = mockk<Context>(relaxed = true)
        val extensionManager = mockk<ExtensionManager>(relaxed = true) {
            every { installedExtensionsFlow } returns MutableStateFlow(emptyList())
            every { availableExtensionsFlow } returns MutableStateFlow(emptyList())
            every { untrustedExtensionsFlow } returns MutableStateFlow(emptyList())
        }
        val getEvaluations = mockk<GetSourceEvaluations>(relaxed = true) {
            every { subscribeAll() } returns flowOf(emptyList())
        }
        val getUnsafeSources = mockk<GetSourceEvaluationUnsafeSources>(relaxed = true) {
            every { subscribeAll() } returns flowOf(emptyList())
            coEvery { awaitAll() } returns emptyList()
        }
        val getUnsafePackages = mockk<GetUnsafeExtensionPackages>(relaxed = true) {
            every { subscribeAll() } returns flowOf(emptyList())
        }
        val getCandidates = mockk<exh.recs.evaluation.GetSourceEvaluationCandidates> {
            every { subscribe() } returns candidateFlow
        }
        val getProbeMarker = mockk<GetSourceEvaluationProbeMarker>(relaxed = true) {
            coEvery { await() } returns null
        }
        val getTasteProfile = mockk<GetTasteProfile>(relaxed = true) {
            coEvery { await() } returns TasteProfile.EMPTY
        }
        val getMangaTaste = mockk<GetMangaTaste>(relaxed = true) {
            coEvery { awaitAll() } returns emptyList()
        }
        val getRecommendationFit = mockk<GetSourceRecommendationFit>(relaxed = true) {
            coEvery { awaitAll() } returns emptyList()
        }

        val model = environment.createScreenModel {
            SourceEvaluationScreenModel(
                context = context,
                basePreferences = BasePreferences(context, FakePreferenceStore()),
                extensionManager = extensionManager,
                getSourceEvaluations = getEvaluations,
                clearSourceEvaluations = mockk<ClearSourceEvaluations>(relaxed = true),
                getSourceEvaluationCandidates = getCandidates,
                getSourceEvaluationProbeMarker = getProbeMarker,
                clearSourceEvaluationProbeMarker = mockk<ClearSourceEvaluationProbeMarker>(relaxed = true),
                markSourceEvaluationUnsafe = mockk<MarkSourceEvaluationUnsafe>(relaxed = true),
                getSourceEvaluationUnsafeSources = getUnsafeSources,
                deleteSourceEvaluationUnsafe = mockk<DeleteSourceEvaluationUnsafe>(relaxed = true),
                clearSourceEvaluationUnsafe = mockk<ClearSourceEvaluationUnsafe>(relaxed = true),
                getUnsafeExtensionPackages = getUnsafePackages,
                deleteUnsafeExtensionPackage = mockk<DeleteUnsafeExtensionPackage>(relaxed = true),
                clearUnsafeExtensionPackages = mockk<ClearUnsafeExtensionPackages>(relaxed = true),
                getTasteProfile = getTasteProfile,
                getMangaTaste = getMangaTaste,
                clearMangaTaste = mockk<tachiyomi.domain.taste.interactor.ClearMangaTaste>(relaxed = true),
                sourcePreferences = sourcePreferences,
                getSourceRecommendationFit = getRecommendationFit,
                upsertUnsafeExtensionPackage = mockk<UpsertUnsafeExtensionPackage>(relaxed = true),
                evaluationJobGateway = gateway,
                isOnline = { online },
                isRecommendationQualityRunning = { false },
                clock = { 1_000_000L },
                readShizukuState = {
                    ShizukuSetupHelper.State(
                        installed = false,
                        binderAlive = false,
                        permissionGranted = false,
                    )
                },
            )
        }
        environment.registerCleanup(environment::disposeScreenModels)
        return Fixture(model, gateway)
    }

    private fun preferences() = SourcePreferences(FakePreferenceStore()).apply {
        sourceEvaluationConsentGiven().set(true)
        recommendationSourceLanguages().set(setOf("en"))
        evaluationMode().set(true)
    }

    private fun pool(vararg candidates: EvaluationCandidate) =
        SourceEvaluationCandidateFilter.CandidatePoolResult(
            allEligible = candidates.toList(),
            evaluationsByExtensionKey = emptyMap(),
            explicitExtensionKeys = emptySet(),
            dislikedHiddenCount = 0,
            blockExplicit = false,
        )

    private fun candidate(key: String) = EvaluationCandidate(
        extension = Extension.Available(
            name = "Synthetic $key",
            pkgName = "invalid.synthetic.$key",
            versionName = "1.0",
            versionCode = 1L,
            libVersion = 1.4,
            lang = "en",
            isNsfw = false,
            signatureHash = "signature-$key",
            storeName = "synthetic-store",
            sources = emptyList(),
            apkUrl = "https://invalid.example/$key.apk",
            iconUrl = "",
            store = ExtensionStore(
                indexUrl = "https://invalid.example/index.json",
                name = "synthetic-store",
                badgeLabel = "Synthetic",
                signingKey = "synthetic-key",
                contact = ExtensionStore.Contact(website = "", discord = null),
                isLegacy = false,
                extensionListUrl = null,
            ),
        ),
        priorityRank = 0,
    )

    private fun result(key: String, verdict: SourceEvaluationVerdict) =
        SourceEvaluationQueueState.EvaluationResult(
            extensionName = "Synthetic $key",
            sourceName = "Synthetic source $key",
            pkgName = "invalid.synthetic.$key",
            signatureHash = "signature-$key",
            sourceId = key.hashCode().toLong(),
            verdict = verdict,
        )

    private data class Fixture(
        val model: SourceEvaluationScreenModel,
        val gateway: FakeSourceEvaluationJobGateway,
    )

    private class FakeSourceEvaluationJobGateway : SourceEvaluationJobGateway {
        data class Start(
            val runId: Long,
            val candidates: List<EvaluationCandidate>,
            val options: SourceEvaluationOptions,
            val metadata: SourceEvaluationContinuationMetadata,
        )

        private val mutableQueueState = MutableStateFlow<SourceEvaluationQueueState?>(null)
        override val queueState: StateFlow<SourceEvaluationQueueState?> = mutableQueueState
        override var lastCompletedCandidateKeys: Set<String> = emptySet()
        override var continuationMetadata: SourceEvaluationContinuationMetadata? = null
        val starts = mutableListOf<Start>()
        var activeRunId = 0L
            private set
        var cancelCount = 0
            private set
        var resetCount = 0
            private set
        private var running = false

        override fun start(
            candidates: List<EvaluationCandidate>,
            options: SourceEvaluationOptions,
            continuationMetadata: SourceEvaluationContinuationMetadata,
        ) {
            activeRunId += 1L
            running = true
            lastCompletedCandidateKeys = emptySet()
            this.continuationMetadata = continuationMetadata
            starts += Start(activeRunId, candidates, options, continuationMetadata)
            mutableQueueState.value = SourceEvaluationQueueState(
                status = SourceEvaluationQueueState.Status.Running,
                totalCount = candidates.size,
                installerMode = options.installerMode,
                batchSize = options.batchSize,
            )
        }

        override fun cancel() {
            cancelCount += 1
            activeRunId += 1L
            running = false
            lastCompletedCandidateKeys = emptySet()
            mutableQueueState.value = SourceEvaluationQueueState(status = SourceEvaluationQueueState.Status.Cancelled)
        }

        override fun isRunning(): Boolean = running

        override fun reset() {
            resetCount += 1
            activeRunId += 1L
            running = false
            lastCompletedCandidateKeys = emptySet()
            continuationMetadata = null
            mutableQueueState.value = null
        }

        fun emit(
            runId: Long,
            state: SourceEvaluationQueueState,
            completedKeys: Set<String> = emptySet(),
        ) {
            if (runId != activeRunId) return
            mutableQueueState.value = state
            if (state.isTerminal) {
                running = false
                lastCompletedCandidateKeys = completedKeys
            }
        }
    }
}

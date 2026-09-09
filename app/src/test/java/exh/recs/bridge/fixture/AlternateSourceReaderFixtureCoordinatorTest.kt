package exh.recs.bridge.fixture

import exh.recs.bestversion.fixture.BestVersionPairedFixtureGate
import exh.recs.bestversion.fixture.BestVersionPairedFixtureSourceIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class AlternateSourceReaderFixtureCoordinatorTest {
    @Test
    fun `seed applies every overlay step in order and reuses verified complete state`() = runBlocking {
        val recovery = FakeRecoveryStore()
        val dataStore = FakeDataStore()
        val coordinator = coordinator(recovery, dataStore)

        val first = coordinator.seed(activation()) as AlternateSourceReaderFixtureSeedResult.Seeded
        assertEquals(
            listOf(
                AlternateSourceReaderFixtureStep.PRIMARY_GAP,
                AlternateSourceReaderFixtureStep.IDENTITY,
                AlternateSourceReaderFixtureStep.BRIDGE,
                AlternateSourceReaderFixtureStep.SESSION,
                AlternateSourceReaderFixtureStep.ROUTE,
            ),
            dataStore.appliedSteps,
        )
        assertEquals(AlternateSourceReaderFixtureStep.entries.toSet(), first.manifest.completedSteps)
        assertTrue(first.manifest.seededOverlayHash != null)

        val second = coordinator.seed(activation()) as AlternateSourceReaderFixtureSeedResult.Seeded
        assertEquals(first.manifest, second.manifest)
        assertEquals(1, dataStore.prepareCount)
    }

    @Test
    fun `unauthorized activation never touches paired graph`() = runBlocking {
        val dataStore = FakeDataStore()
        val result = coordinator(FakeRecoveryStore(), dataStore).seed(
            activation().copy(evaluationModeEnabled = false),
        )
        assertInstanceOf(AlternateSourceReaderFixtureSeedResult.NotAuthorized::class.java, result)
        assertEquals(0, dataStore.prepareCount)
    }

    @Test
    fun `paired graph preparation outcomes remain distinct`() = runBlocking {
        val outcomes = listOf(
            AlternateSourceReaderPairedGraphPreparation.NotAuthorized to AlternateSourceReaderFixtureSeedResult.NotAuthorized::class.java,
            AlternateSourceReaderPairedGraphPreparation.Collision to AlternateSourceReaderFixtureSeedResult.Collision::class.java,
            AlternateSourceReaderPairedGraphPreparation.RecoveryRequired to AlternateSourceReaderFixtureSeedResult.RecoveryRequired::class.java,
            AlternateSourceReaderPairedGraphPreparation.Failed to AlternateSourceReaderFixtureSeedResult.Failed::class.java,
        )
        outcomes.forEach { (preparation, expected) ->
            val result = coordinator(FakeRecoveryStore(), FakeDataStore(preparation = preparation)).seed(activation())
            assertInstanceOf(expected, result)
        }
    }

    @Test
    fun `malformed paired graph descriptor is rejected before baseline or recovery writes`() = runBlocking {
        val recovery = FakeRecoveryStore()
        val dataStore = FakeDataStore(
            preparation = AlternateSourceReaderPairedGraphPreparation.Ready(
                AlternateSourceReaderPairedGraph("invalid", 11L, 11L),
            ),
        )
        val result = coordinator(recovery, dataStore).seed(activation()) as AlternateSourceReaderFixtureSeedResult.Failed
        assertFalse(result.cleanupCompleted)
        assertEquals(0, dataStore.baselineCount)
        assertEquals(null, recovery.manifest)
    }

    @Test
    fun `baseline failure reports cleanup only when paired absence is proven`() = runBlocking {
        val recovery = FakeRecoveryStore()
        val dataStore = FakeDataStore(failBaseline = true, provePairedAbsent = false)
        val result = coordinator(recovery, dataStore).seed(activation()) as AlternateSourceReaderFixtureSeedResult.Failed
        assertFalse(result.cleanupCompleted)
        assertEquals(listOf("paired", "absent"), dataStore.cleanupOrder)
        assertEquals(null, recovery.manifest)
    }

    @Test
    fun `corrupt recovery and scenario mismatch fail closed without cleanup`() = runBlocking {
        val corrupt = FakeRecoveryStore(corrupt = true)
        assertInstanceOf(
            AlternateSourceReaderFixtureSeedResult.RecoveryRecordCorrupt::class.java,
            coordinator(corrupt, FakeDataStore()).seed(activation()),
        )

        val recovery = FakeRecoveryStore(manifest = validManifest(AlternateSourceReaderFixtureScenario.MISSING))
        val dataStore = FakeDataStore()
        assertInstanceOf(
            AlternateSourceReaderFixtureSeedResult.RecoveryRequired::class.java,
            coordinator(recovery, dataStore).seed(activation(AlternateSourceReaderFixtureScenario.EXACT_GAP)),
        )
        assertEquals(0, dataStore.cleanupOverlayCount)
    }

    @Test
    fun `ordinary step failure performs overlay then paired cleanup and reports completion`() = runBlocking {
        val recovery = FakeRecoveryStore()
        val dataStore = FakeDataStore(failStep = AlternateSourceReaderFixtureStep.BRIDGE)
        val result = coordinator(recovery, dataStore).seed(activation()) as AlternateSourceReaderFixtureSeedResult.Failed

        assertTrue(result.cleanupCompleted)
        assertEquals(listOf("overlay", "paired", "absent"), dataStore.cleanupOrder)
        assertEquals(null, recovery.manifest)
    }

    @Test
    fun `cancellation performs non-cancellable cleanup and is rethrown`() {
        val recovery = FakeRecoveryStore()
        val dataStore = FakeDataStore(cancelStep = AlternateSourceReaderFixtureStep.IDENTITY)
        assertThrows(CancellationException::class.java) {
            runBlocking { coordinator(recovery, dataStore).seed(activation()) }
        }
        assertEquals(listOf("overlay", "paired", "absent"), dataStore.cleanupOrder)
        assertEquals(null, recovery.manifest)
    }

    @Test
    fun `cleanup retains recovery when overlay baseline does not reconcile`() = runBlocking {
        val recovery = FakeRecoveryStore()
        val dataStore = FakeDataStore(restoreBaseline = false)
        coordinator(recovery, dataStore).seed(activation())

        val result = coordinator(recovery, dataStore).cleanup()
        assertInstanceOf(AlternateSourceReaderFixtureCleanupResult.ConflictOrFailure::class.java, result)
        assertTrue(recovery.manifest != null)
        assertEquals(listOf("overlay"), dataStore.cleanupOrder)
    }

    @Test
    fun `cleanup retains recovery when paired graph absence cannot be proven`() = runBlocking {
        val recovery = FakeRecoveryStore()
        val dataStore = FakeDataStore(provePairedAbsent = false)
        coordinator(recovery, dataStore).seed(activation())

        val result = coordinator(recovery, dataStore).cleanup()
        assertInstanceOf(AlternateSourceReaderFixtureCleanupResult.ConflictOrFailure::class.java, result)
        assertTrue(recovery.manifest != null)
        assertEquals(listOf("overlay", "paired", "absent"), dataStore.cleanupOrder)
    }

    @Test
    fun `successful cleanup clears recovery only after overlay and paired absence`() = runBlocking {
        val recovery = FakeRecoveryStore()
        val dataStore = FakeDataStore()
        coordinator(recovery, dataStore).seed(activation())

        assertInstanceOf(AlternateSourceReaderFixtureCleanupResult.Cleaned::class.java, coordinator(recovery, dataStore).cleanup())
        assertEquals(listOf("overlay", "paired", "absent"), dataStore.cleanupOrder)
        assertEquals(null, recovery.manifest)
    }

    @Test
    fun `missing cleanup record still reconciles an orphaned paired graph`() = runBlocking {
        val dataStore = FakeDataStore(hasUnrecordedPairedGraph = true)
        assertInstanceOf(
            AlternateSourceReaderFixtureCleanupResult.Cleaned::class.java,
            coordinator(FakeRecoveryStore(), dataStore).cleanup(),
        )
        assertEquals(listOf("paired-unrecorded", "absent"), dataStore.cleanupOrder)
    }

    private fun coordinator(recovery: FakeRecoveryStore, dataStore: FakeDataStore) =
        AlternateSourceReaderFixtureCoordinator(recovery, dataStore) { NOW }

    private fun activation(
        scenario: AlternateSourceReaderFixtureScenario = AlternateSourceReaderFixtureScenario.EXACT_GAP,
    ): AlternateSourceReaderFixtureActivation {
        val signer = "a".repeat(64)
        return AlternateSourceReaderFixtureActivation(
            isDebugBuild = true,
            scenario = scenario,
            evaluationModeEnabled = true,
            fixtureProfile = AlternateSourceReaderFixtureGate.ISOLATED_PROFILE,
            pairedFixtureProfile = BestVersionPairedFixtureGate.ISOLATED_PROFILE,
            expectedSignerSha256 = signer,
            installedSources = setOf(
                source(BestVersionPairedFixtureGate.ALPHA_PACKAGE, BestVersionPairedFixtureGate.ALPHA_SOURCE_ID, "Fixture Source Alpha", signer),
                source(BestVersionPairedFixtureGate.BETA_PACKAGE, BestVersionPairedFixtureGate.BETA_SOURCE_ID, "Fixture Source Beta", signer),
            ),
        )
    }

    private fun source(packageName: String, sourceId: Long, extensionName: String, signer: String) =
        BestVersionPairedFixtureSourceIdentity(
            packageName,
            sourceId,
            signer,
            extensionName,
            "Fixture Source",
            "1.6.0",
            1L,
        )

    private fun validManifest(scenario: AlternateSourceReaderFixtureScenario) =
        AlternateSourceReaderFixtureManifest(
            operationId = UUID.randomUUID().toString(),
            pairedFixtureOperationId = UUID.randomUUID().toString(),
            scenario = scenario,
            createdAt = NOW,
            completedSteps = setOf(
                AlternateSourceReaderFixtureStep.PAIRED_GRAPH,
                AlternateSourceReaderFixtureStep.BASELINES,
            ),
            pendingStep = AlternateSourceReaderFixtureStep.PRIMARY_GAP,
            primaryMangaId = 11L,
            alternateMangaId = 12L,
            primaryGapChapter = chapterSnapshot(),
            bridgeBaselineHash = BASELINE_BRIDGE,
            identityBaselineHash = BASELINE_IDENTITY,
            actionHistoryBaselineIds = setOf(HISTORY_ID),
        )

    private class FakeRecoveryStore(
        var manifest: AlternateSourceReaderFixtureManifest? = null,
        private val corrupt: Boolean = false,
    ) : AlternateSourceReaderFixtureRecoveryStore {
        override fun load(now: Long): AlternateSourceReaderFixtureManifestLoad = when {
            corrupt -> AlternateSourceReaderFixtureManifestLoad.Corrupt
            manifest == null -> AlternateSourceReaderFixtureManifestLoad.Missing
            else -> AlternateSourceReaderFixtureManifestLoad.Present(requireNotNull(manifest))
        }

        override fun save(manifest: AlternateSourceReaderFixtureManifest, now: Long): Boolean {
            this.manifest = manifest
            return true
        }

        override fun clear(): Boolean {
            manifest = null
            return true
        }
    }

    private class FakeDataStore(
        private val preparation: AlternateSourceReaderPairedGraphPreparation = AlternateSourceReaderPairedGraphPreparation.Ready(
            AlternateSourceReaderPairedGraph(PAIRED_ID, 11L, 12L),
        ),
        private val failStep: AlternateSourceReaderFixtureStep? = null,
        private val cancelStep: AlternateSourceReaderFixtureStep? = null,
        private val restoreBaseline: Boolean = true,
        private val provePairedAbsent: Boolean = true,
        private val failBaseline: Boolean = false,
        private val hasUnrecordedPairedGraph: Boolean = false,
    ) : AlternateSourceReaderFixtureDataStore {
        var prepareCount = 0
        var baselineCount = 0
        val appliedSteps = mutableListOf<AlternateSourceReaderFixtureStep>()
        val cleanupOrder = mutableListOf<String>()
        var cleanupOverlayCount = 0
        private val overlayRows = mutableListOf<String>()
        private var bridgeHash = BASELINE_BRIDGE
        private var identityHash = BASELINE_IDENTITY
        private var routeReady = false
        private var pairedPresent = false

        override suspend fun preparePairedGraph(): AlternateSourceReaderPairedGraphPreparation {
            prepareCount++
            if (preparation is AlternateSourceReaderPairedGraphPreparation.Ready) pairedPresent = true
            return preparation
        }

        override suspend fun captureBaseline(graph: AlternateSourceReaderPairedGraph): AlternateSourceReaderFixtureBaseline {
            baselineCount++
            if (failBaseline) error("baseline-failed")
            return AlternateSourceReaderFixtureBaseline(
                chapterSnapshot(),
                BASELINE_BRIDGE,
                BASELINE_IDENTITY,
                setOf(HISTORY_ID),
            )
        }

        override suspend fun inspect(manifest: AlternateSourceReaderFixtureManifest) =
            AlternateSourceReaderFixtureObservedState(
                overlayRows = overlayRows.toList(),
                bridgeHash = bridgeHash,
                identityHash = identityHash,
                actionHistoryIds = setOf(HISTORY_ID),
                routeReady = routeReady,
            )

        override suspend fun applyStep(
            manifest: AlternateSourceReaderFixtureManifest,
            step: AlternateSourceReaderFixtureStep,
        ): AlternateSourceReaderFixtureManifest {
            if (step == cancelStep) throw CancellationException("cancelled")
            if (step == failStep) error("failed")
            appliedSteps += step
            overlayRows += step.name
            if (step == AlternateSourceReaderFixtureStep.IDENTITY) identityHash = "D".repeat(64)
            if (step == AlternateSourceReaderFixtureStep.BRIDGE) bridgeHash = "C".repeat(64)
            if (step == AlternateSourceReaderFixtureStep.ROUTE) routeReady = true
            return manifest
        }

        override suspend fun cleanupOverlay(manifest: AlternateSourceReaderFixtureManifest): Boolean {
            cleanupOverlayCount++
            cleanupOrder += "overlay"
            overlayRows.clear()
            routeReady = false
            if (restoreBaseline) {
                bridgeHash = BASELINE_BRIDGE
                identityHash = BASELINE_IDENTITY
            }
            return true
        }

        override suspend fun cleanupPairedGraph(operationId: String): Boolean {
            cleanupOrder += "paired"
            pairedPresent = false
            return true
        }

        override suspend fun cleanupUnrecordedPairedGraph(): Boolean {
            cleanupOrder += "paired-unrecorded"
            if (!hasUnrecordedPairedGraph) return true
            pairedPresent = false
            return true
        }

        override suspend fun isPairedGraphAbsent(operationId: String): Boolean {
            cleanupOrder += "absent"
            return provePairedAbsent && !pairedPresent
        }
    }

    companion object {
        private const val NOW = 1_000_000L
        private const val PAIRED_ID = "11111111-1111-4111-8111-111111111111"
        private const val HISTORY_ID = "22222222-2222-4222-8222-222222222222"
        private val BASELINE_BRIDGE = "A".repeat(64)
        private val BASELINE_IDENTITY = "B".repeat(64)

        private fun chapterSnapshot() = AlternateSourceReaderFixtureChapterSnapshot(
            id = 21L,
            mangaId = 11L,
            url = "/kmk-fixture/f2/origin/chapter-2",
            name = "Chapter 2",
            chapterNumber = 2F,
            read = true,
            bookmark = true,
            lastPageRead = 5L,
            dateFetch = NOW,
            dateUpload = NOW,
            sourceOrder = 1L,
        )
    }
}

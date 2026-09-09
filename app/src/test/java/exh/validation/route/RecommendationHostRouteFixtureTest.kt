package exh.validation.route

import android.content.Context
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceRuntimeFailureRegistry
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import exh.recs.BrowsePersonalRecommendationsScreenModel
import exh.recs.ForYouDebugFixtureMode
import exh.recs.PersonalRecommendationResult
import exh.recs.RecommendationSourceStatus
import exh.recs.matching.ConfirmedMangaGroupTargets
import exh.util.FakePreferenceStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.taste.interactor.ClearMangaTaste
import tachiyomi.domain.taste.interactor.ClearRecommendationCache
import tachiyomi.domain.taste.interactor.GetChapterCountsByMangaIds
import tachiyomi.domain.taste.interactor.GetDisabledRecommendationSources
import tachiyomi.domain.taste.interactor.GetKnownRecommendationMangaIds
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.interactor.GetRecommendationCache
import tachiyomi.domain.taste.interactor.GetRecommendationCandidateMemory
import tachiyomi.domain.taste.interactor.GetRecommendationDiscoveryProgress
import tachiyomi.domain.taste.interactor.GetRecommendationExposure
import tachiyomi.domain.taste.interactor.GetTagAliases
import tachiyomi.domain.taste.interactor.GetTasteProfile
import tachiyomi.domain.taste.interactor.PruneRecommendationCandidateMemory
import tachiyomi.domain.taste.interactor.PruneRecommendationExposure
import tachiyomi.domain.taste.interactor.RecordRecommendationExposure
import tachiyomi.domain.taste.interactor.SetMangaTasteBatch
import tachiyomi.domain.taste.interactor.UpsertRecommendationCache
import tachiyomi.domain.taste.interactor.UpsertRecommendationCandidateMemory
import tachiyomi.domain.taste.interactor.UpsertRecommendationDiscoveryProgress
import tachiyomi.domain.taste.model.TasteProfile
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.repository.TrackRepository
import tachiyomi.domain.tracker.repository.LocalTrackerRepository

class RecommendationHostRouteFixtureTest {
    @Test
    fun `loaded route preserves deterministic source order and visible results`() {
        HostRouteTestEnvironment().use { environment ->
            val sourceB = SyntheticRecommendationSource(202L, "Source B") { pageOf("b") }
            val sourceA = SyntheticRecommendationSource(101L, "Source A") { pageOf("a") }
            RecommendationHostRouteFixture(environment, listOf(sourceB, sourceA)).use { route ->
                route.refreshToIdle()

                assertEquals(listOf(202L, 101L), route.state.sourceOrder.map { it.id })
                assertEquals(2, route.state.items.size)
                assertTrue(route.state.items.values.all { it is PersonalRecommendationResult.Success })
                assertTrue(route.state.items.values.all { (it as PersonalRecommendationResult.Success).result.isNotEmpty() })
            }
        }
    }

    @Test
    fun `partial route retains successful source when a sibling source fails`() {
        HostRouteTestEnvironment().use { environment ->
            val success = SyntheticRecommendationSource(303L, "Working") { pageOf("working") }
            val failure = SyntheticRecommendationSource(404L, "Unavailable") {
                throw IllegalStateException("synthetic-source-unavailable")
            }
            RecommendationHostRouteFixture(environment, listOf(success, failure)).use { route ->
                route.refreshToIdle()

                val successResult = route.state.items.getValue(success)
                val failureResult = route.state.items.getValue(failure)
                assertTrue(successResult is PersonalRecommendationResult.Success && successResult.result.isNotEmpty())
                assertTrue(failureResult is PersonalRecommendationResult.Error)
                assertEquals(RecommendationSourceStatus.Shown, route.state.sourceStatuses.getValue(success.id).status)
                assertFalse(route.state.isLoading)
            }
        }
    }

    @Test
    fun `all unavailable sources reach a settled error state`() {
        HostRouteTestEnvironment().use { environment ->
            val first = SyntheticRecommendationSource(505L, "Unavailable A") {
                throw IllegalStateException("synthetic-a-unavailable")
            }
            val second = SyntheticRecommendationSource(606L, "Unavailable B") {
                throw UnsupportedOperationException("synthetic-b-incompatible")
            }
            RecommendationHostRouteFixture(environment, listOf(first, second)).use { route ->
                route.refreshToIdle()

                assertEquals(2, route.state.items.size)
                assertTrue(route.state.items.values.all { it is PersonalRecommendationResult.Error })
                assertEquals(route.state.total, route.state.progress)
                assertFalse(route.state.isLoading)
            }
        }
    }

    @Test
    fun `new refresh supersedes a source that returns stale data after cancellation`() {
        HostRouteTestEnvironment().use { environment ->
            val firstStarted = CompletableDeferred<Unit>()
            var invocation = 0
            val source = SyntheticRecommendationSource(707L, "Superseded") {
                invocation++
                if (invocation == 1) {
                    firstStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } catch (_: CancellationException) {
                        pageOf("stale")
                    }
                } else {
                    pageOf("fresh")
                }
            }
            RecommendationHostRouteFixture(environment, listOf(source)).use { route ->
                route.refresh()
                environment.runCurrent()
                assertTrue(firstStarted.isCompleted)

                route.refreshToIdle()

                val result = route.state.items.getValue(source) as PersonalRecommendationResult.Success
                assertEquals(2L, route.state.resultGeneration)
                assertTrue(result.result.isNotEmpty())
                assertTrue(result.result.all { it.manga.title.startsWith("fresh") })
            }
        }
    }

    @Test
    fun `route leave prevents late state and cache writes from a cancellation-resistant source`() {
        HostRouteTestEnvironment().use { environment ->
            val started = CompletableDeferred<Unit>()
            val source = SyntheticRecommendationSource(808L, "Late") {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } catch (_: CancellationException) {
                    pageOf("late")
                }
            }
            val route = RecommendationHostRouteFixture(environment, listOf(source))
            route.refresh()
            environment.runCurrent()
            assertTrue(started.isCompleted)
            val stateAtLeave = route.state

            route.close()
            environment.advanceUntilIdle()

            assertEquals(stateAtLeave, route.state)
            coVerify(exactly = 0) { route.upsertCache.await(any()) }
        }
    }

    @Test
    fun `debug Top Picks fixture reaches populated real refresh without normal sources`() {
        HostRouteTestEnvironment().use { environment ->
            RecommendationHostRouteFixture(
                environment = environment,
                sources = emptyList(),
                forYouFixtureMode = ForYouDebugFixtureMode.TOP_PICKS,
            ).use { route ->
                route.refreshToIdle()

                assertEquals(listOf("Fixture Source A", "Fixture Source B"), route.state.sourceOrder.map { it.name })
                val topPicks = route.state.combinedResult as PersonalRecommendationResult.Success
                assertTrue(topPicks.result.isNotEmpty())
                assertTrue(topPicks.result.all { it.manga.title.startsWith("Fixture ") })
            }
        }
    }

    @Test
    fun `debug Top Picks partial fixture keeps populated result and bounds failed source`() {
        HostRouteTestEnvironment().use { environment ->
            RecommendationHostRouteFixture(
                environment = environment,
                sources = emptyList(),
                forYouFixtureMode = ForYouDebugFixtureMode.TOP_PICKS_PARTIAL_FAILURE,
            ).use { route ->
                route.refreshToIdle()
                route.refreshToIdle()

                assertTrue(route.state.combinedResult is PersonalRecommendationResult.Success)
                assertEquals(
                    2,
                    route.state.items.values.count { it is PersonalRecommendationResult.Error },
                )
                assertTrue(route.state.items.values.any { it is PersonalRecommendationResult.Success })
                assertFalse(route.state.isLoading)
            }
        }
    }

    @Test
    fun `inline retry requests each current error once and excludes success`() {
        HostRouteTestEnvironment().use { environment ->
            var successCalls = 0
            var firstFailureCalls = 0
            var secondFailureCalls = 0
            val success = SyntheticRecommendationSource(909L, "Working") {
                successCalls++
                pageOf("working")
            }
            val firstFailure = SyntheticRecommendationSource(910L, "Unavailable A") {
                firstFailureCalls++
                throw IllegalStateException("synthetic-a-unavailable")
            }
            val secondFailure = SyntheticRecommendationSource(911L, "Unavailable B") {
                secondFailureCalls++
                throw IllegalStateException("synthetic-b-unavailable")
            }

            RecommendationHostRouteFixture(environment, listOf(success, firstFailure, secondFailure)).use { route ->
                route.refreshToIdle()
                assertTrue(route.state.items.getValue(success) is PersonalRecommendationResult.Success)
                assertTrue(route.state.items.getValue(firstFailure) is PersonalRecommendationResult.Error)
                assertTrue(route.state.items.getValue(secondFailure) is PersonalRecommendationResult.Error)
                successCalls = 0
                firstFailureCalls = 0
                secondFailureCalls = 0
                route.model.retryFailedSourcesOrRefresh()
                environment.advanceUntilIdle()

                assertEquals(0, successCalls)
                assertEquals(firstFailureCalls, secondFailureCalls, "retry fan-out planner attempt counts")
                assertTrue(firstFailureCalls > 0)
                assertTrue(route.state.items.getValue(success) is PersonalRecommendationResult.Success)
            }
        }
    }
}

private class RecommendationHostRouteFixture(
    private val environment: HostRouteTestEnvironment,
    private val sources: List<Source>,
    forYouFixtureMode: ForYouDebugFixtureMode = ForYouDebugFixtureMode.OFF,
) : AutoCloseable {
    private var closed = false
    private val sourcePreferences = SourcePreferences(FakePreferenceStore()).apply {
        recommendationHideKnownManga().set(false)
        recommendationLatestExplorationEnabled().set(false)
        this.forYouFixtureMode().set(forYouFixtureMode.prefValue)
    }
    private val sourceManager = mockk<SourceManager>().also { manager ->
        every { manager.getVisibleSources() } returns sources
    }
    private val getTasteProfile = mockk<GetTasteProfile>().also { interactor ->
        coEvery { interactor.await() } returns TasteProfile(
            learnedTagWeights = mapOf("action" to 4.0),
            explicitTagPreferences = mapOf("action" to 1),
            sourceAffinity = emptyMap(),
            blockedGroups = emptySet(),
        )
    }
    private val getTagAliases = mockk<GetTagAliases>().also { interactor ->
        coEvery { interactor.awaitAliasMap() } returns emptyMap()
        coEvery { interactor.awaitGroupToAliasesMap() } returns emptyMap()
    }
    private val getDisabledSources = mockk<GetDisabledRecommendationSources>().also { interactor ->
        coEvery { interactor.await() } returns emptyList()
    }
    private val networkToLocalManga = mockk<NetworkToLocalManga>().also { interactor ->
        coEvery { interactor.invoke(any<List<Manga>>(), any()) } answers {
            firstArg<List<Manga>>().mapIndexed { index, manga -> manga.copy(id = manga.source * 1_000 + index + 1) }
        }
    }
    private val getMangaTaste = mockk<GetMangaTaste>().also { interactor ->
        coEvery { interactor.awaitAll() } returns emptyList()
    }
    private val getRecommendationCache = mockk<GetRecommendationCache>().also { interactor ->
        coEvery { interactor.await(any()) } returns null
    }
    private val getMemory = mockk<GetRecommendationCandidateMemory>().also { interactor ->
        coEvery { interactor.awaitBySourceQuery(any(), any()) } returns emptyList()
        coEvery { interactor.awaitCountBySource(any()) } returns 0L
    }
    private val getProgress = mockk<GetRecommendationDiscoveryProgress>().also { interactor ->
        coEvery { interactor.awaitBySourceQuery(any(), any()) } returns emptyList()
        coEvery { interactor.awaitEvaluatedPages(any(), any()) } returns emptySet()
    }
    private val getExposure = mockk<GetRecommendationExposure>().also { interactor ->
        coEvery { interactor.awaitBySourceUrls(any()) } returns emptyList()
    }

    val upsertCache = mockk<UpsertRecommendationCache>(relaxed = true)

    val model = BrowsePersonalRecommendationsScreenModel(
        context = mockk<Context>(relaxed = true),
        isOnline = { true },
        clock = { environment.scheduler.currentTime + 10_000L },
        searchDispatcher = environment.dispatcher,
        isLowRamDevice = false,
        autoLoad = false,
        getTasteProfile = getTasteProfile,
        getTagAliases = getTagAliases,
        getDisabledSources = getDisabledSources,
        sourceManager = sourceManager,
        networkToLocalManga = networkToLocalManga,
        getMangaInteractor = mockk<GetManga>(relaxed = true),
        getMangaTaste = getMangaTaste,
        sourcePreferences = sourcePreferences,
        isDebugBuild = true,
        getRecommendationCache = getRecommendationCache,
        upsertRecommendationCache = upsertCache,
        clearRecommendationCache = mockk<ClearRecommendationCache>(relaxed = true),
        getKnownMangaIds = mockk<GetKnownRecommendationMangaIds>(relaxed = true),
        getChapterCounts = mockk<GetChapterCountsByMangaIds>(relaxed = true),
        getMemory = getMemory,
        upsertMemory = mockk<UpsertRecommendationCandidateMemory>(relaxed = true),
        pruneMemory = mockk<PruneRecommendationCandidateMemory>(relaxed = true),
        getDiscoveryProgress = getProgress,
        upsertDiscoveryProgress = mockk<UpsertRecommendationDiscoveryProgress>(relaxed = true),
        setMangaTasteBatch = mockk<SetMangaTasteBatch>(relaxed = true),
        clearMangaTaste = mockk<ClearMangaTaste>(relaxed = true),
        getTracks = GetTracks(mockk<TrackRepository>(relaxed = true)),
        localTrackerRepository = mockk<LocalTrackerRepository>(relaxed = true),
        confirmedMangaGroupTargets = mockk<ConfirmedMangaGroupTargets>(relaxed = true),
        getRecommendationExposure = getExposure,
        recordRecommendationExposure = mockk<RecordRecommendationExposure>(relaxed = true),
        pruneRecommendationExposure = mockk<PruneRecommendationExposure>(relaxed = true),
    )

    val state: BrowsePersonalRecommendationsScreenModel.State
        get() = model.state.value

    init {
        environment.ownScreenModel(model)
        environment.registerCleanup(::close)
    }

    private fun disposeRoute() {
        if (closed) return
        closed = true
        try {
            environment.disposeScreenModels()
        } finally {
            sources.forEach { SourceRuntimeFailureRegistry.clear(it.id) }
        }
    }

    fun refresh() {
        check(!closed) { "recommendation-host-route-closed" }
        model.refresh()
    }

    fun refreshToIdle() {
        refresh()
        environment.advanceUntilIdle()
    }

    override fun close() {
        disposeRoute()
    }
}

private class SyntheticRecommendationSource(
    override val id: Long,
    override val name: String,
    private val search: suspend (String) -> MangasPage,
) : Source {
    override val lang = "en"
    override val supportsLatest = false

    override fun getFilterList() = FilterList()
    override suspend fun getPopularManga(page: Int) = search("popular")
    override suspend fun getLatestUpdates(page: Int) = throw UnsupportedOperationException("latest-not-supported")
    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList) = search(query)
    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ) = SMangaUpdate(manga, chapters)
    override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
}

private fun pageOf(prefix: String): MangasPage = MangasPage(
    mangas = (1..5).map { index ->
        SManga(
            url = "/$prefix-$index",
            title = "$prefix manga $index",
            description = "Synthetic route candidate $index",
            genre = "action",
            status = SManga.ONGOING,
            initialized = true,
        )
    },
    hasNextPage = false,
)

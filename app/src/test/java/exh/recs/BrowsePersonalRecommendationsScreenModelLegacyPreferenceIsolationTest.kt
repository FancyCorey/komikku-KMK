package exh.recs

import android.content.Context
import eu.kanade.domain.source.service.SourcePreferences
import exh.recs.matching.ConfirmedGroupLocalTrackingPropagator
import exh.recs.matching.ConfirmedMangaGroupTargets
import exh.util.FakePreferenceStore
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
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
import tachiyomi.domain.taste.interactor.GetTagAliases
import tachiyomi.domain.taste.interactor.GetTasteProfile
import tachiyomi.domain.taste.interactor.PruneRecommendationCandidateMemory
import tachiyomi.domain.taste.interactor.PruneRecommendationExposure
import tachiyomi.domain.taste.interactor.RecordRecommendationExposure
import tachiyomi.domain.taste.interactor.SetMangaTasteBatch
import tachiyomi.domain.taste.interactor.UpsertRecommendationCache
import tachiyomi.domain.taste.interactor.UpsertRecommendationCandidateMemory
import tachiyomi.domain.taste.interactor.UpsertRecommendationDiscoveryProgress
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.repository.TrackRepository
import tachiyomi.domain.tracker.repository.LocalTrackerRepository

/**
 * R1 correction, production-bound: proves the real [BrowsePersonalRecommendationsScreenModel]'s
 * live rating writers ([BrowsePersonalRecommendationsScreenModel.rateSelected] and
 * [BrowsePersonalRecommendationsScreenModel.markSelectedNotInterested]) never read or write the
 * legacy `seenRecommendationMangaKeys` preference. Constructs the real ScreenModel (same harness
 * shape as [BrowsePersonalRecommendationsScreenModelDirectValidationTest]) with a real
 * [SourcePreferences] backed by an in-memory [FakePreferenceStore] -- a pre-existing legacy key is
 * seeded before the call and asserted byte-for-byte unchanged after, which a source-text guard
 * alone could not prove (it would not catch a re-introduced write to a different preference key
 * with the same net effect).
 */
class BrowsePersonalRecommendationsScreenModelLegacyPreferenceIsolationTest {

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildModel(
        sourcePreferences: SourcePreferences,
        setMangaTasteBatch: SetMangaTasteBatch = mockk(relaxed = true),
        confirmedMangaGroupTargets: ConfirmedMangaGroupTargets = mockk(relaxed = true),
        confirmedGroupLocalTrackingPropagator: ConfirmedGroupLocalTrackingPropagator = mockk(relaxed = true),
        localTrackerRepository: LocalTrackerRepository = mockk(relaxed = true),
    ): BrowsePersonalRecommendationsScreenModel = BrowsePersonalRecommendationsScreenModel(
        context = mockk<Context>(relaxed = true),
        isOnline = { true },
        clock = { 1_000L },
        searchDispatcher = Dispatchers.Main,
        isLowRamDevice = false,
        autoLoad = false,
        getTasteProfile = mockk<GetTasteProfile>(relaxed = true),
        getTagAliases = mockk<GetTagAliases>(relaxed = true),
        getDisabledSources = mockk<GetDisabledRecommendationSources>(relaxed = true),
        sourceManager = mockk<SourceManager>(relaxed = true),
        networkToLocalManga = mockk<NetworkToLocalManga>(relaxed = true),
        getMangaInteractor = mockk<GetManga>(relaxed = true),
        getMangaTaste = mockk<GetMangaTaste>(relaxed = true),
        sourcePreferences = sourcePreferences,
        getRecommendationCache = mockk<GetRecommendationCache>(relaxed = true),
        upsertRecommendationCache = mockk<UpsertRecommendationCache>(relaxed = true),
        clearRecommendationCache = mockk<ClearRecommendationCache>(relaxed = true),
        getKnownMangaIds = mockk<GetKnownRecommendationMangaIds>(relaxed = true),
        getChapterCounts = mockk<GetChapterCountsByMangaIds>(relaxed = true),
        getMemory = mockk<GetRecommendationCandidateMemory>(relaxed = true),
        upsertMemory = mockk<UpsertRecommendationCandidateMemory>(relaxed = true),
        pruneMemory = mockk<PruneRecommendationCandidateMemory>(relaxed = true),
        getDiscoveryProgress = mockk<GetRecommendationDiscoveryProgress>(relaxed = true),
        upsertDiscoveryProgress = mockk<UpsertRecommendationDiscoveryProgress>(relaxed = true),
        setMangaTasteBatch = setMangaTasteBatch,
        clearMangaTaste = mockk<ClearMangaTaste>(relaxed = true),
        localTrackerRepository = localTrackerRepository,
        confirmedMangaGroupTargets = confirmedMangaGroupTargets,
        confirmedGroupLocalTrackingPropagator = confirmedGroupLocalTrackingPropagator,
        getTracks = GetTracks(mockk<TrackRepository>(relaxed = true)),
        getRecommendationExposure = mockk(relaxed = true),
        recordRecommendationExposure = mockk<RecordRecommendationExposure>(relaxed = true),
        pruneRecommendationExposure = mockk<PruneRecommendationExposure>(relaxed = true),
    )

    private fun candidate(sourceId: Long, mangaId: Long, url: String) = Manga.create().copy(
        id = mangaId,
        source = sourceId,
        url = url,
        ogTitle = "Manga $url",
    )

    @Test
    fun `rateSelected never reads or writes the legacy seen-manga preference`() = runTest {
        val store = FakePreferenceStore()
        val sourcePreferences = SourcePreferences(store)
        val staleLegacyValue = "9|/manga/stale"
        sourcePreferences.seenRecommendationMangaKeys().set(staleLegacyValue)
        val setMangaTasteBatch = mockk<SetMangaTasteBatch>(relaxed = true)
        val model = buildModel(sourcePreferences, setMangaTasteBatch)
        val targets = listOf(candidate(1L, 10L, "/manga/a"))

        val outcome = model.rateSelected(targets, MangaRating.LOVE)

        assertEquals(1, outcome.successCount)
        assertEquals(
            staleLegacyValue,
            sourcePreferences.seenRecommendationMangaKeys().get(),
            "rateSelected must not touch the legacy preference -- MangaTaste is the sole rating-family authority",
        )
        coVerify(exactly = 1) { setMangaTasteBatch.await(targets, MangaRating.LOVE) }
    }

    @Test
    fun `markSelectedNotInterested never reads or writes the legacy seen-manga preference`() = runTest {
        val store = FakePreferenceStore()
        val sourcePreferences = SourcePreferences(store)
        val staleLegacyValue = "9|/manga/stale"
        sourcePreferences.seenRecommendationMangaKeys().set(staleLegacyValue)
        val setMangaTasteBatch = mockk<SetMangaTasteBatch>(relaxed = true)
        val model = buildModel(sourcePreferences, setMangaTasteBatch)
        val targets = listOf(candidate(2L, 20L, "/manga/b"))

        val outcome = model.markSelectedNotInterested(targets)

        assertEquals(1, outcome.successCount)
        assertEquals(
            staleLegacyValue,
            sourcePreferences.seenRecommendationMangaKeys().get(),
            "markSelectedNotInterested must not touch the legacy preference -- it writes MangaTaste.NOT_INTERESTED only",
        )
        coVerify(exactly = 1) { setMangaTasteBatch.await(targets, MangaRating.NOT_INTERESTED) }
    }

    @Test
    fun `a failed rating write still leaves the legacy preference untouched`() = runTest {
        val store = FakePreferenceStore()
        val sourcePreferences = SourcePreferences(store)
        val staleLegacyValue = "9|/manga/stale"
        sourcePreferences.seenRecommendationMangaKeys().set(staleLegacyValue)
        val setMangaTasteBatch = mockk<SetMangaTasteBatch>(relaxed = true)
        io.mockk.coEvery { setMangaTasteBatch.await(any(), any()) } throws RuntimeException("db unavailable")
        val model = buildModel(sourcePreferences, setMangaTasteBatch)
        val targets = listOf(candidate(3L, 30L, "/manga/c"))

        val outcome = model.rateSelected(targets, MangaRating.DISLIKE)

        assertEquals(1, outcome.failureCount)
        assertEquals(
            staleLegacyValue,
            sourcePreferences.seenRecommendationMangaKeys().get(),
            "no rollback coordination across MangaTaste and the legacy preference should exist -- there is nothing to roll back",
        )
    }

    @Test
    fun `bulk rating propagates confirmed local tracking only when enabled`() = runTest {
        val store = FakePreferenceStore()
        val sourcePreferences = SourcePreferences(store)
        sourcePreferences.confirmedTrackedVersionLocalTrackingPropagationEnabled().set(true)
        val groupTargets = mockk<ConfirmedMangaGroupTargets>(relaxed = true)
        val propagator = mockk<ConfirmedGroupLocalTrackingPropagator>(relaxed = true)
        val model = buildModel(
            sourcePreferences,
            confirmedMangaGroupTargets = groupTargets,
            confirmedGroupLocalTrackingPropagator = propagator,
        )
        val target = candidate(4L, 40L, "/manga/d")
        val confirmed = candidate(5L, 50L, "/manga/d-confirmed")
        io.mockk.coEvery { groupTargets.await(target) } returns listOf(target, confirmed)

        val outcome = model.rateSelected(listOf(target), MangaRating.LOVE)

        assertEquals(1, outcome.successCount)
        coVerify(exactly = 1) { groupTargets.await(target) }
        coVerify(exactly = 1) { propagator.propagateIfAnyTracked(listOf(target, confirmed)) }
    }
}

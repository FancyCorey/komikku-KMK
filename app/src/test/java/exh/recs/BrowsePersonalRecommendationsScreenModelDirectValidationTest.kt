package exh.recs

import android.content.Context
import cafe.adriel.voyager.core.model.StateScreenModel
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.Source
import exh.recs.matching.ConfirmedMangaGroupTargets
import exh.util.FakePreferenceStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
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
import tachiyomi.domain.taste.model.TasteProfile
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.model.Track
import tachiyomi.domain.track.repository.TrackRepository
import tachiyomi.domain.tracker.repository.LocalTrackerRepository
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.isAccessible

/**
 * Direct production-path validation for the real BrowsePersonalRecommendationsScreenModel.
 *
 * The model is constructed with [autoLoad] disabled only to prevent the Android/network load from
 * starting before a unit test has installed its collaborators. No production method is replaced:
 * tracker resolution and visible-exposure recording execute on the real ScreenModel instance.
 * The full search assembly remains separately classified as partial because it requires the
 * application context, source runtime, and 25-collaborator load graph.
 */
class BrowsePersonalRecommendationsScreenModelDirectValidationTest {

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private data class Harness(
        val model: BrowsePersonalRecommendationsScreenModel,
        val recordExposure: RecordRecommendationExposure,
    )

    private fun buildHarness(
        trackRepository: TrackRepository = mockk(relaxed = true),
        recordExposure: RecordRecommendationExposure = mockk(relaxed = true),
        getTasteProfile: GetTasteProfile = mockk(relaxed = true),
        isOnline: () -> Boolean = { true },
        clock: () -> Long = { 1_000L },
        searchDispatcher: CoroutineDispatcher = Dispatchers.Main,
    ): Harness {
        val model = BrowsePersonalRecommendationsScreenModel(
            context = mockk<Context>(relaxed = true),
            isOnline = isOnline,
            clock = clock,
            searchDispatcher = searchDispatcher,
            isLowRamDevice = false,
            autoLoad = false,
            getTasteProfile = getTasteProfile,
            getTagAliases = mockk<GetTagAliases>(relaxed = true),
            getDisabledSources = mockk<GetDisabledRecommendationSources>(relaxed = true),
            sourceManager = mockk<SourceManager>(relaxed = true),
            networkToLocalManga = mockk<NetworkToLocalManga>(relaxed = true),
            getMangaInteractor = mockk<GetManga>(relaxed = true),
            getMangaTaste = mockk<GetMangaTaste>(relaxed = true),
            sourcePreferences = SourcePreferences(FakePreferenceStore()),
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
            setMangaTasteBatch = mockk<SetMangaTasteBatch>(relaxed = true),
            clearMangaTaste = mockk<ClearMangaTaste>(relaxed = true),
            getTracks = GetTracks(trackRepository),
            localTrackerRepository = mockk<LocalTrackerRepository>(relaxed = true),
            confirmedMangaGroupTargets = mockk<ConfirmedMangaGroupTargets>(relaxed = true),
            getRecommendationExposure = mockk(relaxed = true),
            recordRecommendationExposure = recordExposure,
            pruneRecommendationExposure = mockk<PruneRecommendationExposure>(relaxed = true),
        )
        return Harness(model, recordExposure)
    }

    private fun candidate(
        sourceId: Long,
        mangaId: Long,
        url: String,
        title: String = "Manga $url",
    ): PersonalRecommendation =
        PersonalRecommendation(
            manga = Manga.create().copy(
                id = mangaId,
                source = sourceId,
                url = url,
                ogTitle = title,
            ),
            score = 1.0,
            matchedGroups = emptyList(),
        )

    private fun track(mangaId: Long) = Track(
        id = 1L,
        mangaId = mangaId,
        trackerId = 2L,
        remoteId = 3L,
        libraryId = null,
        title = "Tracked",
        lastChapterRead = 0.0,
        totalChapters = 10L,
        status = 0L,
        score = 0.0,
        remoteUrl = "",
        startDate = 0L,
        finishDate = 0L,
        private = false,
    )

    @Suppress("UNCHECKED_CAST")
    private fun forceState(
        model: BrowsePersonalRecommendationsScreenModel,
        state: BrowsePersonalRecommendationsScreenModel.State,
    ) {
        val field = StateScreenModel::class.java.getDeclaredField("mutableState")
        field.isAccessible = true
        (field.get(model) as kotlinx.coroutines.flow.MutableStateFlow<BrowsePersonalRecommendationsScreenModel.State>).value = state
    }

    private suspend fun resolveTrackedExposureKeys(
        model: BrowsePersonalRecommendationsScreenModel,
        sourceId: Long,
        candidates: List<PersonalRecommendation>,
    ): RecommendationDisplayReranker.TrackedState {
        val function = BrowsePersonalRecommendationsScreenModel::class.declaredFunctions
            .single { it.name == "resolveTrackedExposureKeys" }
            .apply { isAccessible = true }
        return try {
            function.callSuspend(model, sourceId, candidates) as RecommendationDisplayReranker.TrackedState
        } catch (error: InvocationTargetException) {
            throw (error.cause ?: error)
        }
    }

    @Test
    fun `real ScreenModel construction does not eagerly load when autoLoad is disabled`() = runTest {
        val harness = buildHarness()

        assertTrue(harness.model.state.value.isLoading)
        coVerify(exactly = 0) { harness.recordExposure.await(any(), any(), any()) }
    }

    @Test
    fun `public refresh reaches offline terminal state without Android connectivity`() = runTest {
        val harness = buildHarness(isOnline = { false })

        harness.model.refresh()
        advanceUntilIdle()

        assertFalse(harness.model.state.value.isLoading)
        assertTrue(harness.model.state.value.isOffline)
    }

    @Test
    fun `applying focus starts a fresh recommendation refresh`() = runTest {
        val harness = buildHarness(isOnline = { false })

        harness.model.setActiveFocus(
            RecommendationFocusPolicy.FocusCriteria(include = setOf("comedy")),
        )
        advanceUntilIdle()

        assertFalse(harness.model.state.value.isLoading)
        assertTrue(harness.model.state.value.isOffline)
        assertEquals(setOf("comedy"), harness.model.activeFocusCriteria.value.include)
    }

    @Test
    fun `public refresh reaches profile-empty terminal state`() = runTest {
        val getTasteProfile = mockk<GetTasteProfile>()
        coEvery { getTasteProfile.await() } returns TasteProfile.EMPTY
        val harness = buildHarness(getTasteProfile = getTasteProfile)

        harness.model.refresh()
        advanceUntilIdle()

        assertFalse(harness.model.state.value.isLoading)
        assertTrue(harness.model.state.value.profileIsEmpty)
    }

    @Test
    fun `real ScreenModel tracker assembly distinguishes known tracked from known empty`() = runTest {
        val repository = mockk<TrackRepository>()
        val harness = buildHarness(repository)
        val tracked = candidate(sourceId = 7L, mangaId = 42L, url = "/tracked")
        coEvery { repository.getTracksByMangaIds(listOf(42L)) } returns listOf(track(42L))

        val known = resolveTrackedExposureKeys(harness.model, 7L, listOf(tracked))

        assertEquals(
            RecommendationDisplayReranker.TrackedState.Known(
                setOf(RecommendationDisplayReranker.ExposureKey(7L, "/tracked")),
            ),
            known,
        )

        coEvery { repository.getTracksByMangaIds(listOf(42L)) } returns emptyList()
        val knownEmpty = resolveTrackedExposureKeys(harness.model, 7L, listOf(tracked))

        assertEquals(RecommendationDisplayReranker.TrackedState.Known(emptySet()), knownEmpty)
    }

    @Test
    fun `real ScreenModel tracker assembly turns repository failure into Unknown`() = runTest {
        val repository = mockk<TrackRepository>()
        val harness = buildHarness(repository)
        val candidate = candidate(sourceId = 7L, mangaId = 42L, url = "/failure")
        coEvery { repository.getTracksByMangaIds(listOf(42L)) } throws IllegalStateException("fixture failure")

        val result = resolveTrackedExposureKeys(harness.model, 7L, listOf(candidate))

        assertEquals(RecommendationDisplayReranker.TrackedState.Unknown, result)
    }

    @Test
    fun `real ScreenModel tracker assembly propagates cancellation`() {
        val repository = mockk<TrackRepository>()
        val harness = buildHarness(repository)
        val candidate = candidate(sourceId = 7L, mangaId = 42L, url = "/cancel")
        coEvery { repository.getTracksByMangaIds(listOf(42L)) } coAnswers {
            throw CancellationException("cancelled")
        }

        assertThrows(CancellationException::class.java) {
            runBlocking {
                resolveTrackedExposureKeys(harness.model, 7L, listOf(candidate))
            }
        }
    }

    @Test
    fun `visible exposure records one settled generation and preserves source identity`() = runTest {
        val sourceA = mockk<Source>(relaxed = true)
        val sourceB = mockk<Source>(relaxed = true)
        every { sourceA.id } returns 11L
        every { sourceB.id } returns 22L
        val keys = slot<List<Pair<Long, String>>>()
        val recordExposure = mockk<RecordRecommendationExposure>()
        coEvery { recordExposure.await(capture(keys), any(), any()) } just runs
        val harness = buildHarness(recordExposure = recordExposure)
        forceState(
            harness.model,
            BrowsePersonalRecommendationsScreenModel.State(
                items = persistentMapOf(
                    sourceA to PersonalRecommendationResult.Success(listOf(candidate(11L, 1L, "/same", "Source A title"))),
                    sourceB to PersonalRecommendationResult.Success(listOf(candidate(22L, 2L, "/same", "Source B title"))),
                ),
                isLoading = false,
                resultGeneration = 9L,
            ),
        )

        harness.model.recordVisibleExposure()
        harness.model.recordVisibleExposure()
        advanceUntilIdle()

        forceState(
            harness.model,
            harness.model.state.value.copy(resultGeneration = 10L),
        )
        harness.model.recordVisibleExposure()
        advanceUntilIdle()

        coVerify(exactly = 2) { recordExposure.await(any(), any(), any()) }
        assertEquals(setOf(11L to "/same", 22L to "/same"), keys.captured.toSet())
        coVerify(exactly = 2) { recordExposure.await(any(), any(), 1_000L) }
    }

    @Test
    fun `visible exposure ignores loading empty and partial states`() = runTest {
        val recordExposure = mockk<RecordRecommendationExposure>(relaxed = true)
        val harness = buildHarness(recordExposure = recordExposure)
        val sourceA = mockk<Source>(relaxed = true)
        val sourceB = mockk<Source>(relaxed = true)
        every { sourceA.id } returns 11L
        every { sourceB.id } returns 22L
        val success = PersonalRecommendationResult.Success(listOf(candidate(11L, 1L, "/visible")))

        forceState(harness.model, BrowsePersonalRecommendationsScreenModel.State())
        harness.model.recordVisibleExposure()
        forceState(
            harness.model,
            BrowsePersonalRecommendationsScreenModel.State(isLoading = false, resultGeneration = 1L),
        )
        harness.model.recordVisibleExposure()
        forceState(
            harness.model,
            BrowsePersonalRecommendationsScreenModel.State(
                items = persistentMapOf(sourceA to success, sourceB to PersonalRecommendationResult.Loading),
                isLoading = false,
                resultGeneration = 2L,
            ),
        )
        harness.model.recordVisibleExposure()
        advanceUntilIdle()

        coVerify(exactly = 0) { recordExposure.await(any(), any(), any()) }
        assertFalse(harness.model.state.value.isLoading)
    }
}

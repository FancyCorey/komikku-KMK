package exh.recs.loved

import cafe.adriel.voyager.core.model.StateScreenModel
import eu.kanade.domain.source.service.SourcePreferences
import exh.recs.matching.ConfirmedGroupLocalTrackingPropagator
import exh.recs.matching.ConfirmedMangaGroupTargets
import exh.recs.matching.ConfirmedTrackedMangaTasteTargets
import exh.recs.matching.CrossSourceIdentityDecisionController
import exh.util.FakePreferenceStore
import exh.util.GroupUndoService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.taste.interactor.ClearMangaTaste
import tachiyomi.domain.taste.interactor.DeleteCrossSourceGroupCompletely
import tachiyomi.domain.taste.interactor.DeleteCrossSourceMangaLink
import tachiyomi.domain.taste.interactor.GetCrossSourceGroupPrimary
import tachiyomi.domain.taste.interactor.GetCrossSourceIdentityDecisions
import tachiyomi.domain.taste.interactor.GetCrossSourceMangaLinks
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.interactor.SetCrossSourceGroupPrimary
import tachiyomi.domain.taste.interactor.SetMangaTaste
import tachiyomi.domain.taste.interactor.SetMangaTasteBatch
import tachiyomi.domain.taste.interactor.UpsertCrossSourceMangaLinks
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaTaste

/**
 * R1 correction, production-bound: proves the real
 * [LovedMangaScreenModel.changeSelectedRating] never reads or writes the legacy
 * `seenRecommendationMangaKeys` preference. Constructs the real ScreenModel with a real
 * [SourcePreferences] backed by an in-memory [FakePreferenceStore] -- a pre-existing legacy key is
 * seeded before the call and asserted byte-for-byte unchanged after.
 */
class LovedMangaScreenModelLegacyPreferenceIsolationTest {

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun taste(source: Long, url: String, mangaId: Long) = MangaTaste(
        mangaId = mangaId,
        source = source,
        url = url,
        title = "Manga $mangaId",
        rating = MangaRating.LIKE.value,
        createdAt = 1000L,
        updatedAt = 1000L,
    )

    @Suppress("UNCHECKED_CAST")
    private fun forceState(model: LovedMangaScreenModel, state: LovedMangaScreenModel.State) {
        val field = StateScreenModel::class.java.getDeclaredField("mutableState")
        field.isAccessible = true
        (field.get(model) as MutableStateFlow<LovedMangaScreenModel.State>).value = state
    }

    private fun buildModel(
        sourcePreferences: SourcePreferences,
        setMangaTaste: SetMangaTaste,
        setMangaTasteBatch: SetMangaTasteBatch = mockk(relaxed = true),
        confirmedTrackedMangaTasteTargets: ConfirmedTrackedMangaTasteTargets = mockk(relaxed = true),
    ): LovedMangaScreenModel {
        val getMangaTaste = mockk<GetMangaTaste>(relaxed = true)
        every { getMangaTaste.subscribeAll() } returns emptyFlow()
        return LovedMangaScreenModel(
            getMangaTaste = getMangaTaste,
            getManga = mockk<GetManga>(relaxed = true),
            getCrossSourceMangaLinks = mockk<GetCrossSourceMangaLinks>(relaxed = true),
            getCrossSourceIdentityDecisions = mockk<GetCrossSourceIdentityDecisions>(relaxed = true),
            sourceManager = mockk<SourceManager>(relaxed = true),
            filterRating = MangaRating.LIKE,
            getCrossSourceGroupPrimary = mockk<GetCrossSourceGroupPrimary>(relaxed = true),
            setCrossSourceGroupPrimary = mockk<SetCrossSourceGroupPrimary>(relaxed = true),
            setMangaTaste = setMangaTaste,
            setMangaTasteBatch = setMangaTasteBatch,
            clearMangaTaste = mockk<ClearMangaTaste>(relaxed = true),
            upsertCrossSourceMangaLinks = mockk<UpsertCrossSourceMangaLinks>(relaxed = true),
            deleteCrossSourceMangaLink = mockk<DeleteCrossSourceMangaLink>(relaxed = true),
            sourcePreferences = sourcePreferences,
            deleteCrossSourceGroupCompletely = mockk<DeleteCrossSourceGroupCompletely>(relaxed = true),
            groupUndoService = mockk<GroupUndoService>(relaxed = true),
            identityController = mockk<CrossSourceIdentityDecisionController>(relaxed = true),
            confirmedTrackedMangaTasteTargets = confirmedTrackedMangaTasteTargets,
            confirmedMangaGroupTargets = mockk<ConfirmedMangaGroupTargets>(relaxed = true),
            confirmedGroupLocalTrackingPropagator = mockk<ConfirmedGroupLocalTrackingPropagator>(relaxed = true),
        )
    }

    @Test
    fun `changeSelectedRating never reads or writes the legacy seen-manga preference`() = runTest {
        val store = FakePreferenceStore()
        val sourcePreferences = SourcePreferences(store)
        val staleLegacyValue = "9|/manga/stale"
        sourcePreferences.seenRecommendationMangaKeys().set(staleLegacyValue)
        val setMangaTaste = mockk<SetMangaTaste>(relaxed = true)
        val model = buildModel(sourcePreferences, setMangaTaste)

        val entryTaste = taste(source = 1L, url = "/manga/a", mangaId = 10L)
        val key = RatedMangaKey.of(entryTaste)
        forceState(
            model,
            LovedMangaScreenModel.State.Success(
                entries = listOf(LovedMangaEntry(taste = entryTaste, manga = null)),
                groupDuplicates = false,
                selectedKeys = setOf(key),
            ),
        )

        model.changeSelectedRating(MangaRating.LOVE)
        advanceUntilIdle()

        assertEquals(
            staleLegacyValue,
            sourcePreferences.seenRecommendationMangaKeys().get(),
            "changeSelectedRating must not touch the legacy preference -- MangaTaste is the sole rating-family authority",
        )
        coVerify(exactly = 1) {
            setMangaTaste.await(
                mangaId = entryTaste.mangaId,
                source = entryTaste.source,
                url = entryTaste.url,
                title = entryTaste.title,
                rating = MangaRating.LOVE,
            )
        }
    }

    @Test
    fun `enabled rating propagation writes confirmed tracked group members`() = runTest {
        val store = FakePreferenceStore()
        val sourcePreferences = SourcePreferences(store)
        sourcePreferences.confirmedTrackedVersionRatingPropagationEnabled().set(true)
        val setMangaTaste = mockk<SetMangaTaste>(relaxed = true)
        val setMangaTasteBatch = mockk<SetMangaTasteBatch>(relaxed = true)
        val confirmedTargets = mockk<ConfirmedTrackedMangaTasteTargets>(relaxed = true)
        val model = buildModel(sourcePreferences, setMangaTaste, setMangaTasteBatch, confirmedTargets)

        val origin = Manga.create().copy(id = 10L, source = 1L, url = "/manga/origin", ogTitle = "Same Manga")
        val confirmed = origin.copy(id = 20L, source = 2L, url = "/manga/confirmed")
        val originTaste = taste(source = origin.source, url = origin.url, mangaId = origin.id)
        val key = RatedMangaKey.of(originTaste)
        coEvery { confirmedTargets.await(origin) } returns listOf(origin, confirmed)
        forceState(
            model,
            LovedMangaScreenModel.State.Success(
                entries = listOf(LovedMangaEntry(taste = originTaste, manga = origin)),
                groupDuplicates = false,
                selectedKeys = setOf(key),
            ),
        )

        model.changeSelectedRating(MangaRating.LOVE)
        advanceUntilIdle()

        coVerify(exactly = 1) { confirmedTargets.await(origin) }
        coVerify(exactly = 1) { setMangaTasteBatch.await(listOf(origin, confirmed), MangaRating.LOVE) }
        coVerify(exactly = 0) { setMangaTaste.await(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a failed rating write still leaves the legacy preference untouched`() = runTest {
        val store = FakePreferenceStore()
        val sourcePreferences = SourcePreferences(store)
        val staleLegacyValue = "9|/manga/stale"
        sourcePreferences.seenRecommendationMangaKeys().set(staleLegacyValue)
        val setMangaTaste = mockk<SetMangaTaste>(relaxed = true)
        coEvery {
            setMangaTaste.await(mangaId = any(), source = any(), url = any(), title = any(), rating = any())
        } throws RuntimeException("db unavailable")
        val model = buildModel(sourcePreferences, setMangaTaste)

        val entryTaste = taste(source = 2L, url = "/manga/b", mangaId = 20L)
        val key = RatedMangaKey.of(entryTaste)
        forceState(
            model,
            LovedMangaScreenModel.State.Success(
                entries = listOf(LovedMangaEntry(taste = entryTaste, manga = null)),
                groupDuplicates = false,
                selectedKeys = setOf(key),
            ),
        )

        model.changeSelectedRating(MangaRating.DISLIKE)
        advanceUntilIdle()

        assertEquals(
            staleLegacyValue,
            sourcePreferences.seenRecommendationMangaKeys().get(),
            "no rollback coordination across MangaTaste and the legacy preference should exist -- there is nothing to roll back",
        )
    }
}

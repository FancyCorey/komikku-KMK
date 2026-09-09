package exh.recs.bestversion

import cafe.adriel.voyager.core.model.StateScreenModel
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SChapterImpl
import exh.recs.matching.CrossSourceIdentityDecisionController
import exh.recs.matching.MangaIdentityKey
import exh.util.DispatcherHandle
import exh.util.FakePreferenceStore
import exh.util.FakeTasteRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.interactor.GetCrossSourceIdentityDecisions
import tachiyomi.domain.taste.interactor.ReplaceCrossSourceIdentityDecisions

// KMK R2-AUG-05-ACTUAL-BEST-VERSION-FAILURE-PATH -->
/**
 * Behavioral tests for [BestVersionCompareScreenModel.selectManualChapter] -- the bounded manual
 * chapter override required by the canonical contract so a user stuck with a disclosed Nearest
 * (non-exact) auto-match can pick a different chapter from THAT SAME candidate's own already-fetched
 * chapter list. Uses the same `forceState`-via-reflection harness as
 * [BestVersionCompareScreenModelConfirmMigrationTest] to seed a ready state without driving the real
 * network search pipeline.
 */
class BestVersionManualChapterSelectionTest {

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun manga(source: Long, url: String, id: Long = source) =
        Manga.create().copy(id = id, source = source, url = url, ogTitle = "Manga $url", favorite = false)

    private fun chapter(number: Float, name: String = "Chapter $number", url: String = "/ch/$number"): SChapter =
        SChapterImpl().apply {
            this.name = name
            this.chapter_number = number
            this.url = url
        }

    @Suppress("UNCHECKED_CAST")
    private fun BestVersionCompareScreenModel.forceState(newState: BestVersionCompareScreenModel.State) {
        val field = StateScreenModel::class.java.getDeclaredField("mutableState")
        field.isAccessible = true
        (field.get(this) as MutableStateFlow<BestVersionCompareScreenModel.State>).value = newState
    }

    private fun identityController(): CrossSourceIdentityDecisionController {
        val repository = FakeTasteRepository()
        return CrossSourceIdentityDecisionController(
            GetCrossSourceIdentityDecisions(repository),
            ReplaceCrossSourceIdentityDecisions(repository),
        )
    }

    private fun buildModel(): BestVersionCompareScreenModel {
        val getManga = mockk<GetManga>(relaxed = true)
        coEvery { getManga.await(1L) } returns null
        return BestVersionCompareScreenModel(
            originMangaId = 1L,
            sourcePreferences = SourcePreferences(FakePreferenceStore()),
            sourceManager = mockk(relaxed = true),
            getMangaInteractor = getManga,
            getChaptersByMangaId = mockk(relaxed = true),
            networkToLocalManga = mockk(relaxed = true),
            migrateMangaUseCase = mockk(relaxed = true),
            upsertQualitySignal = mockk(relaxed = true),
            isLowRamDevice = false,
            dispatcherHandle = DispatcherHandle(StandardTestDispatcher()),
            candidateSearchGateway = mockk(relaxed = true),
            identityController = identityController(),
        )
    }

    @Test
    fun `selectManualChapter picks a chapter from the candidate's own fetched list and discloses Manual`() {
        val origin = manga(1L, "/origin")
        val candidate = manga(2L, "/candidate")
        val key = MangaIdentityKey(candidate.source, candidate.url)
        val nearestMatch = chapter(12f)
        val manuallyPicked = chapter(30f)
        val model = buildModel()
        model.forceState(
            BestVersionCompareScreenModel.State(
                step = BestVersionStep.SelectChapter,
                originManga = origin,
                candidateChapters = persistentMapOf(
                    key to CandidateChapterState.Available(
                        chapter = nearestMatch,
                        totalChapters = 2,
                        matchDisclosure = ChapterMatchDisclosure.Nearest(originChapterNumber = 145.0, candidateChapterNumber = 12.0),
                    ),
                ),
                candidateChapterLists = mapOf(key to listOf(nearestMatch, manuallyPicked)).toPersistentMap(),
            ),
        )

        model.selectManualChapter(key, manuallyPicked)

        val result = model.state.value.candidateChapters[key]
        assertEquals(true, result is CandidateChapterState.Available)
        result as CandidateChapterState.Available
        assertEquals(manuallyPicked, result.chapter)
        assertEquals(ChapterMatchDisclosure.Manual, result.matchDisclosure)
        assertEquals(2, result.totalChapters)
    }

    @Test
    fun `selectManualChapter never substitutes a chapter from a different candidate's list`() {
        // KMK R2-AUG-05: side-effect-boundary requirement -- must never substitute another
        // candidate's/source's chapter while labeling it as this candidate's own selection.
        val origin = manga(1L, "/origin")
        val candidate = manga(2L, "/candidate")
        val key = MangaIdentityKey(candidate.source, candidate.url)
        val ownChapter = chapter(1f)
        val foreignChapter = chapter(99f, url = "/foreign/99")
        val model = buildModel()
        model.forceState(
            BestVersionCompareScreenModel.State(
                step = BestVersionStep.SelectChapter,
                originManga = origin,
                candidateChapters = persistentMapOf(
                    key to CandidateChapterState.Available(chapter = ownChapter, totalChapters = 1),
                ),
                candidateChapterLists = mapOf(key to listOf(ownChapter)).toPersistentMap(),
            ),
        )

        model.selectManualChapter(key, foreignChapter)

        val result = model.state.value.candidateChapters[key] as CandidateChapterState.Available
        assertEquals(ownChapter, result.chapter, "a chapter not in this candidate's own fetched list must never be accepted")
    }

    @Test
    fun `selectManualChapter is a no-op for a candidate with no fetched chapter list`() {
        val origin = manga(1L, "/origin")
        val candidate = manga(2L, "/candidate")
        val key = MangaIdentityKey(candidate.source, candidate.url)
        val model = buildModel()
        model.forceState(
            BestVersionCompareScreenModel.State(
                step = BestVersionStep.SelectChapter,
                originManga = origin,
                candidateChapters = persistentMapOf(key to CandidateChapterState.Unavailable),
            ),
        )

        model.selectManualChapter(key, chapter(1f))

        assertEquals(CandidateChapterState.Unavailable, model.state.value.candidateChapters[key])
        assertNull(model.state.value.candidateChapterLists[key])
    }
}
// KMK <--

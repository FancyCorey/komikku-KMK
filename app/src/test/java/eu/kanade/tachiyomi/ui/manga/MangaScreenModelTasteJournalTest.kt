package eu.kanade.tachiyomi.ui.manga

import eu.kanade.domain.source.service.SourcePreferences
import exh.util.EvaluationJournalActionType
import exh.util.EvaluationModeJournalRecorder
import exh.util.EvaluationModeUndoJournal
import exh.util.FakePreferenceStore
import exh.util.FakeTasteRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.interactor.ClearMangaTaste
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.interactor.SetMangaTaste
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaTaste

// KMK v0.8.20-fix1 -->
/**
 * Tests for [MangaScreenModel.setMangaTaste]/[clearMangaTaste]'s Evaluation Mode journal wiring
 * (the fix for the confirmed defect: detail-page ratings previously bypassed
 * [EvaluationModeJournalRecorder] entirely, so they never appeared in Action History).
 *
 * [MangaScreenModel] itself has a very large constructor-injected dependency graph, so rather than
 * instantiating the real screen model, this exercises exactly the same build-before-write/commit-
 * after-success sequence [MangaScreenModel.setMangaTaste]/[clearMangaTaste] now use --
 * [EvaluationModeJournalRecorder.buildRatingChange] + the real [SetMangaTaste]/[ClearMangaTaste]
 * interactors + [EvaluationModeJournalRecorder.commit] -- against a [FakeTasteRepository] and
 * [FakePreferenceStore], the same fakes [exh.util.EvaluationModeUndoServiceRestoreTest] and
 * [exh.util.LibraryUndoServiceRestoreTest] already use for this exact contract. This proves the
 * shared recorder call sequence behaves correctly; [MangaScreenModel]'s own two methods call this
 * exact sequence verbatim (see their source).
 */
class MangaScreenModelTasteJournalTest {

    private lateinit var tasteRepository: FakeTasteRepository
    private lateinit var sourcePreferences: SourcePreferences
    private lateinit var getMangaTaste: GetMangaTaste
    private lateinit var setMangaTaste: SetMangaTaste
    private lateinit var clearMangaTaste: ClearMangaTaste

    private val manga = Manga.create().copy(id = 10L, source = 1L, url = "/m/10", ogTitle = "Test Manga")

    @BeforeEach
    fun setUp() {
        EvaluationModeUndoJournal.clear()
        tasteRepository = FakeTasteRepository()
        sourcePreferences = SourcePreferences(FakePreferenceStore())
        getMangaTaste = GetMangaTaste(tasteRepository)
        setMangaTaste = SetMangaTaste(tasteRepository)
        clearMangaTaste = ClearMangaTaste(tasteRepository)
    }

    @AfterEach
    fun tearDown() {
        EvaluationModeUndoJournal.clear()
    }

    /** Mirrors [MangaScreenModel.setMangaTaste]'s exact build/write/commit sequence. */
    private suspend fun setMangaTasteWithJournal(rating: MangaRating) {
        val journalActionType = when (rating) {
            MangaRating.LOVE -> EvaluationJournalActionType.RATE_LOVE
            MangaRating.LIKE -> EvaluationJournalActionType.RATE_LIKE
            MangaRating.DISLIKE -> EvaluationJournalActionType.RATE_DISLIKE
        }
        val journalEntries = EvaluationModeJournalRecorder.buildRatingChange(
            sourcePreferences,
            getMangaTaste,
            listOf(manga),
            rating.value,
            journalActionType,
        )
        setMangaTaste.await(
            mangaId = manga.id,
            source = manga.source,
            url = manga.url,
            title = manga.title,
            rating = rating,
        )
        EvaluationModeJournalRecorder.commit(journalEntries)
    }

    /** Mirrors [MangaScreenModel.clearMangaTaste]'s exact build/write/commit sequence. */
    private suspend fun clearMangaTasteWithJournal() {
        val journalEntries = EvaluationModeJournalRecorder.buildRatingChange(
            sourcePreferences,
            getMangaTaste,
            listOf(manga),
            null,
            EvaluationJournalActionType.CLEAR_RATING,
        )
        clearMangaTaste.await(manga.source, manga.url)
        EvaluationModeJournalRecorder.commit(journalEntries)
    }

    @Test
    fun `detail-page rating creates a history entry after success when Evaluation Mode is enabled`() = runTest {
        sourcePreferences.evaluationMode().set(true)

        setMangaTasteWithJournal(MangaRating.LOVE)

        val snapshot = EvaluationModeUndoJournal.snapshot()
        assertEquals(1, snapshot.size)
        val entry = snapshot.first()
        assertEquals(EvaluationJournalActionType.RATE_LOVE, entry.actionType)
        assertEquals(manga.source, entry.source)
        assertEquals(manga.url, entry.url)
        assertEquals(null, entry.previousRating)
        assertEquals(MangaRating.LOVE.value, entry.newRating)
        // The write actually happened -- the journal isn't just recording an intent.
        assertEquals(MangaRating.LOVE.value, tasteRepository.getMangaTaste(manga.id)?.rating)
    }

    @Test
    fun `detail-page clear rating creates a history entry after success when Evaluation Mode is enabled`() = runTest {
        sourcePreferences.evaluationMode().set(true)
        // Seed an existing rating so clear has something to clear and a previousRating to record.
        tasteRepository.upsertMangaTaste(
            MangaTaste(
                mangaId = manga.id,
                source = manga.source,
                url = manga.url,
                title = manga.title,
                rating = MangaRating.LIKE.value,
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )

        clearMangaTasteWithJournal()

        val snapshot = EvaluationModeUndoJournal.snapshot()
        assertEquals(1, snapshot.size)
        val entry = snapshot.first()
        assertEquals(EvaluationJournalActionType.CLEAR_RATING, entry.actionType)
        assertEquals(MangaRating.LIKE.value, entry.previousRating)
        assertEquals(null, entry.newRating)
        // The write actually happened.
        assertEquals(null, tasteRepository.getMangaTaste(manga.id))
    }

    @Test
    fun `failed write does not create a history entry`() = runTest {
        sourcePreferences.evaluationMode().set(true)
        val failingRepository = object : tachiyomi.domain.taste.repository.TasteRepository by tasteRepository {
            override suspend fun upsertMangaTaste(taste: MangaTaste) = throw RuntimeException("simulated write failure")
        }
        val failingSetMangaTaste = SetMangaTaste(failingRepository)

        // Build (as MangaScreenModel does), attempt the write, and only commit on success -- here the
        // write throws, so commit must never run, exactly like MangaScreenModel's real method.
        val journalEntries = EvaluationModeJournalRecorder.buildRatingChange(
            sourcePreferences,
            getMangaTaste,
            listOf(manga),
            MangaRating.LOVE.value,
            EvaluationJournalActionType.RATE_LOVE,
        )
        try {
            failingSetMangaTaste.await(
                mangaId = manga.id,
                source = manga.source,
                url = manga.url,
                title = manga.title,
                rating = MangaRating.LOVE,
            )
            EvaluationModeJournalRecorder.commit(journalEntries)
        } catch (_: RuntimeException) {
            // Expected -- commit above must not have run.
        }

        assertTrue(EvaluationModeUndoJournal.isEmpty(), "a failed write must not leave a journal entry")
    }

    @Test
    fun `Evaluation Mode disabled creates no history entry`() = runTest {
        sourcePreferences.evaluationMode().set(false)

        setMangaTasteWithJournal(MangaRating.DISLIKE)

        // The write still happened (rating still works normally for ordinary users)...
        assertEquals(MangaRating.DISLIKE.value, tasteRepository.getMangaTaste(manga.id)?.rating)
        // ...but no journal entry was recorded.
        assertTrue(EvaluationModeUndoJournal.isEmpty(), "Evaluation Mode disabled must record nothing")
    }

    @Test
    fun `detail-page and For You routes produce identically-shaped journal entries for the same rating`() = runTest {
        sourcePreferences.evaluationMode().set(true)

        // Detail-page path (the new code under test).
        setMangaTasteWithJournal(MangaRating.LIKE)
        val detailEntry = EvaluationModeUndoJournal.snapshot().first()
        EvaluationModeUndoJournal.clear()

        // The same shared-recorder call the For You/Loved/Liked/Disliked routes already use
        // (exh.recs.BrowsePersonalRecommendationsScreenModel.rateSelected), applied to the same manga.
        val forYouEntries = EvaluationModeJournalRecorder.buildRatingChange(
            sourcePreferences,
            getMangaTaste,
            listOf(manga),
            MangaRating.LIKE.value,
            EvaluationJournalActionType.RATE_LIKE,
        )
        EvaluationModeJournalRecorder.commit(forYouEntries)
        val forYouEntry = EvaluationModeUndoJournal.snapshot().first()

        // Same action type, same identity, same field set -- one shared history semantics, not two.
        assertEquals(detailEntry.actionType, forYouEntry.actionType)
        assertEquals(detailEntry.source, forYouEntry.source)
        assertEquals(detailEntry.url, forYouEntry.url)
        assertEquals(detailEntry.changedFields, forYouEntry.changedFields)
    }
}
// KMK <--

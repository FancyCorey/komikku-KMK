package eu.kanade.tachiyomi.ui.manga

import eu.kanade.domain.source.service.SourcePreferences
import exh.recs.SeenMangaKey
import exh.recs.SeenRecommendationMangaStore
import exh.util.EvaluationJournalActionType
import exh.util.EvaluationModeJournalRecorder
import exh.util.EvaluationModeUndoJournal
import exh.util.EvaluationModeUndoService
import exh.util.FakePreferenceStore
import exh.util.FakeTasteRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.interactor.ClearMangaTaste
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.interactor.SetMangaTaste
import tachiyomi.domain.taste.model.MangaRating

// KMK
/**
 * Tests for the "Not Interested -> Love/Like/Dislike" atomic transition
 * [MangaScreenModel.setMangaTaste] performs when a rating is selected while Not Interested is
 * active: [EvaluationModeJournalRecorder.buildRatingChangeReplacingNotInterested] builds one journal
 * entry covering both axes, the caller clears the Not Interested key and writes the new rating, and
 * [EvaluationModeUndoService] restores both the previous rating and previous Not Interested state from
 * that single entry. Mirrors [MangaScreenModelNotInterestedJournalTest]'s approach of exercising the
 * exact build/write/commit sequence against fakes rather than the real, large-constructor
 * [MangaScreenModel].
 */
class MangaScreenModelNotInterestedToRatingTransitionTest {

    private lateinit var preferenceStore: FakePreferenceStore
    private lateinit var sourcePreferences: SourcePreferences
    private lateinit var tasteRepository: FakeTasteRepository
    private lateinit var getMangaTaste: GetMangaTaste
    private lateinit var setMangaTaste: SetMangaTaste
    private lateinit var clearMangaTaste: ClearMangaTaste
    private lateinit var undoService: EvaluationModeUndoService

    private val manga = Manga.create().copy(id = 30L, source = 3L, url = "/m/30", ogTitle = "Test Manga 3")

    @BeforeEach
    fun setUp() {
        EvaluationModeUndoJournal.clear()
        preferenceStore = FakePreferenceStore()
        sourcePreferences = SourcePreferences(preferenceStore)
        tasteRepository = FakeTasteRepository()
        getMangaTaste = GetMangaTaste(tasteRepository)
        setMangaTaste = SetMangaTaste(tasteRepository)
        clearMangaTaste = ClearMangaTaste(tasteRepository)
        undoService = EvaluationModeUndoService(sourcePreferences, getMangaTaste, setMangaTaste, clearMangaTaste)
    }

    @AfterEach
    fun tearDown() {
        EvaluationModeUndoJournal.clear()
    }

    private fun seenKeys(): Set<SeenMangaKey> =
        SeenRecommendationMangaStore.parse(sourcePreferences.seenRecommendationMangaKeys().get())

    private fun markSeen() {
        val key = SeenMangaKey(manga.source, manga.url)
        sourcePreferences.seenRecommendationMangaKeys().set(
            SeenRecommendationMangaStore.serialize(SeenRecommendationMangaStore.add(seenKeys(), key)),
        )
    }

    /** Mirrors [MangaScreenModel.setMangaTaste]'s exact sequence for the `wasNotInterested == true` branch. */
    private suspend fun setMangaTasteWithJournal(rating: MangaRating) {
        val journalEntries = EvaluationModeJournalRecorder.buildRatingChangeReplacingNotInterested(
            sourcePreferences,
            getMangaTaste,
            listOf(manga),
            rating.value,
            EvaluationJournalActionType.RATE_LOVE,
        )
        val key = SeenMangaKey(manga.source, manga.url)
        sourcePreferences.seenRecommendationMangaKeys().set(
            SeenRecommendationMangaStore.serialize(SeenRecommendationMangaStore.remove(seenKeys(), key)),
        )
        setMangaTaste.await(mangaId = manga.id, source = manga.source, url = manga.url, title = manga.title, rating = rating)
        EvaluationModeJournalRecorder.commit(journalEntries)
    }

    @Test
    fun `selecting a rating while Not Interested is active clears the store and writes the rating atomically`() = runTest {
        sourcePreferences.evaluationMode().set(true)
        markSeen()
        assertTrue(SeenMangaKey(manga.source, manga.url) in seenKeys())

        setMangaTasteWithJournal(MangaRating.LOVE)

        assertFalse(SeenMangaKey(manga.source, manga.url) in seenKeys(), "Not Interested must be cleared")
        assertEquals(MangaRating.LOVE.value, tasteRepository.getMangaTaste(manga.id)?.rating)
    }

    @Test
    fun `the transition produces exactly one journal entry with both changed fields`() = runTest {
        sourcePreferences.evaluationMode().set(true)
        markSeen()

        setMangaTasteWithJournal(MangaRating.LIKE)

        val snapshot = EvaluationModeUndoJournal.snapshot()
        assertEquals(1, snapshot.size)
        val entry = snapshot.first()
        assertEquals(
            setOf(exh.util.EvaluationJournalEntry.FIELD_RATING, exh.util.EvaluationJournalEntry.FIELD_NOT_INTERESTED),
            entry.changedFields,
        )
        assertEquals(true, entry.previousNotInterested)
        assertEquals(false, entry.newNotInterested)
        assertEquals(null, entry.previousRating)
        assertEquals(MangaRating.LIKE.value, entry.newRating)
    }

    @Test
    fun `undo of the combined entry restores both the previous rating and the previous Not Interested state`() = runTest {
        sourcePreferences.evaluationMode().set(true)
        // Establish an underlying rating before Not Interested was ever applied, matching the behavior contract's
        // "Ordinary rating -> Not Interested -> undo restores the prior rating" transition matrix row.
        setMangaTaste.await(mangaId = manga.id, source = manga.source, url = manga.url, title = manga.title, rating = MangaRating.DISLIKE)
        markSeen()

        setMangaTasteWithJournal(MangaRating.LOVE)
        val entry = EvaluationModeUndoJournal.snapshot().first()

        val outcome = undoService.undo(entry.id)

        assertEquals(1, outcome.restoredCount)
        assertEquals(MangaRating.DISLIKE.value, tasteRepository.getMangaTaste(manga.id)?.rating, "prior rating must be restored")
        assertTrue(SeenMangaKey(manga.source, manga.url) in seenKeys(), "prior Not Interested state must be restored")
        assertTrue(EvaluationModeUndoJournal.isEmpty(), "a restored entry must be removed from the journal")
    }

    @Test
    fun `undo of the combined entry with no prior rating clears the rating and restores Not Interested`() = runTest {
        sourcePreferences.evaluationMode().set(true)
        markSeen()

        setMangaTasteWithJournal(MangaRating.LOVE)
        val entry = EvaluationModeUndoJournal.snapshot().first()

        val outcome = undoService.undo(entry.id)

        assertEquals(1, outcome.restoredCount)
        assertEquals(null, tasteRepository.getMangaTaste(manga.id), "no prior rating means the rating must be cleared, not defaulted")
        assertTrue(SeenMangaKey(manga.source, manga.url) in seenKeys())
    }

    @Test
    fun `a later conflicting mutation blocks undo of the stale combined entry`() = runTest {
        sourcePreferences.evaluationMode().set(true)
        markSeen()

        setMangaTasteWithJournal(MangaRating.LOVE)
        val entry = EvaluationModeUndoJournal.snapshot().first()

        // Simulate a subsequent user action that changes the rating again after the journal entry
        // was recorded -- undo must detect the conflict rather than silently overwrite it.
        setMangaTaste.await(mangaId = manga.id, source = manga.source, url = manga.url, title = manga.title, rating = MangaRating.DISLIKE)

        val outcome = undoService.undo(entry.id)

        assertEquals(0, outcome.restoredCount)
        assertEquals(1, outcome.conflictCount)
        assertEquals(MangaRating.DISLIKE.value, tasteRepository.getMangaTaste(manga.id)?.rating, "conflicting state must be left untouched")
        assertFalse(EvaluationModeUndoJournal.isEmpty(), "a conflicted entry must remain in the journal, not be silently dropped")
    }

    @Test
    fun `Evaluation Mode disabled performs the write but records no journal entry`() = runTest {
        sourcePreferences.evaluationMode().set(false)
        markSeen()

        setMangaTasteWithJournal(MangaRating.LOVE)

        assertFalse(SeenMangaKey(manga.source, manga.url) in seenKeys())
        assertEquals(MangaRating.LOVE.value, tasteRepository.getMangaTaste(manga.id)?.rating)
        assertTrue(EvaluationModeUndoJournal.isEmpty())
    }
}
// KMK <--

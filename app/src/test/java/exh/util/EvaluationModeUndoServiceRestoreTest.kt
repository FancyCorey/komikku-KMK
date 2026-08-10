package exh.util

import eu.kanade.domain.source.service.SourcePreferences
import exh.recs.SeenMangaKey
import exh.recs.SeenRecommendationMangaStore
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.interactor.ClearMangaTaste
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.interactor.SetMangaTaste
import tachiyomi.domain.taste.model.MangaTaste

// KMK v0.8.19 -->
/**
 * Interactor-level coverage for [EvaluationModeUndoService.restoreOne] -- the actual DB/preference-
 * touching restore logic that [EvaluationModeUndoServiceTest]'s pure-logic tests do not exercise.
 * Backed by [FakePreferenceStore] (a real [SourcePreferences] over an in-memory [PreferenceStore]) and
 * [FakeTasteRepository] (real [GetMangaTaste]/[SetMangaTaste]/[ClearMangaTaste] over an in-memory
 * [tachiyomi.domain.taste.repository.TasteRepository]), so these tests exercise the real interactor
 * chain end to end without a database.
 */
class EvaluationModeUndoServiceRestoreTest {

    private val preferenceStore = FakePreferenceStore()
    private val sourcePreferences = SourcePreferences(preferenceStore)
    private val tasteRepository = FakeTasteRepository()
    private val getMangaTaste = GetMangaTaste(tasteRepository)
    private val setMangaTaste = SetMangaTaste(tasteRepository)
    private val clearMangaTaste = ClearMangaTaste(tasteRepository)
    private val service = EvaluationModeUndoService(sourcePreferences, getMangaTaste, setMangaTaste, clearMangaTaste)

    @AfterEach
    fun tearDown() {
        EvaluationModeUndoJournal.clear()
    }

    private suspend fun seedTaste(mangaId: Long, source: Long, url: String, rating: Int) {
        tasteRepository.upsertMangaTaste(
            MangaTaste(mangaId = mangaId, source = source, url = url, title = "Test Manga", rating = rating, createdAt = 0L, updatedAt = 0L),
        )
    }

    private fun ratingEntry(mangaId: Long, source: Long, url: String, previousRating: Int?, newRating: Int) =
        EvaluationJournalEntry(
            id = EvaluationJournalEntry.newId(),
            timestamp = System.currentTimeMillis(),
            actionType = EvaluationJournalActionType.RATE_LOVE,
            mangaId = mangaId,
            source = source,
            url = url,
            previousRating = previousRating,
            newRating = newRating,
            previousNotInterested = false,
            newNotInterested = false,
            isBulk = false,
            bulkOperationId = null,
            changedFields = setOf(EvaluationJournalEntry.FIELD_RATING),
        )

    @Test
    fun `undo restores a rating change back to the previous rating`() = runTest {
        seedTaste(mangaId = 1L, source = 10L, url = "/m/1", rating = 2)
        val entry = ratingEntry(mangaId = 1L, source = 10L, url = "/m/1", previousRating = 1, newRating = 2)
        EvaluationModeUndoJournal.record(entry)

        val outcome = service.undo(entry.id)

        assertTrue(outcome.allRestored)
        assertEquals(1, getMangaTaste.await(10L, "/m/1")?.rating)
        assertTrue(EvaluationModeUndoJournal.snapshot().isEmpty())
    }

    @Test
    fun `undo of a rating that was previously unrated clears the taste entirely`() = runTest {
        seedTaste(mangaId = 1L, source = 10L, url = "/m/1", rating = 2)
        val entry = ratingEntry(mangaId = 1L, source = 10L, url = "/m/1", previousRating = null, newRating = 2)
        EvaluationModeUndoJournal.record(entry)

        val outcome = service.undo(entry.id)

        assertTrue(outcome.allRestored)
        assertNull(getMangaTaste.await(10L, "/m/1"))
    }

    @Test
    fun `undo is a conflict and does not touch state when the rating changed after journaling`() = runTest {
        seedTaste(mangaId = 1L, source = 10L, url = "/m/1", rating = 999) // diverged from entry.newRating
        val entry = ratingEntry(mangaId = 1L, source = 10L, url = "/m/1", previousRating = 1, newRating = 2)
        EvaluationModeUndoJournal.record(entry)

        val outcome = service.undo(entry.id)

        assertTrue(outcome.noneRestored)
        assertEquals(999, getMangaTaste.await(10L, "/m/1")?.rating)
        // Conflicting entry is left in the journal, not silently dropped.
        assertEquals(1, EvaluationModeUndoJournal.snapshot().size)
    }

    @Test
    fun `undo restores Not Interested state back to false`() = runTest {
        sourcePreferences.seenRecommendationMangaKeys().set(
            SeenRecommendationMangaStore.serialize(setOf(SeenMangaKey(10L, "/m/1"))),
        )
        val entry = EvaluationJournalEntry(
            id = EvaluationJournalEntry.newId(),
            timestamp = System.currentTimeMillis(),
            actionType = EvaluationJournalActionType.NOT_INTERESTED,
            mangaId = null,
            source = 10L,
            url = "/m/1",
            previousRating = null,
            newRating = null,
            previousNotInterested = false,
            newNotInterested = true,
            isBulk = false,
            bulkOperationId = null,
            changedFields = setOf(EvaluationJournalEntry.FIELD_NOT_INTERESTED),
        )
        EvaluationModeUndoJournal.record(entry)

        val outcome = service.undo(entry.id)

        assertTrue(outcome.allRestored)
        val seen = SeenRecommendationMangaStore.parse(sourcePreferences.seenRecommendationMangaKeys().get())
        assertFalse(SeenMangaKey(10L, "/m/1") in seen)
    }

    @Test
    fun `undoBulk restores every entry in the group and removes the whole group from the journal`() = runTest {
        seedTaste(mangaId = 1L, source = 10L, url = "/m/1", rating = 2)
        seedTaste(mangaId = 2L, source = 10L, url = "/m/2", rating = 2)
        val bulkId = EvaluationJournalEntry.newBulkId()
        val entry1 = ratingEntry(mangaId = 1L, source = 10L, url = "/m/1", previousRating = 1, newRating = 2).copy(isBulk = true, bulkOperationId = bulkId)
        val entry2 = ratingEntry(mangaId = 2L, source = 10L, url = "/m/2", previousRating = null, newRating = 2).copy(isBulk = true, bulkOperationId = bulkId)
        EvaluationModeUndoJournal.record(entry1)
        EvaluationModeUndoJournal.record(entry2)

        val outcome = service.undoBulk(bulkId)

        assertTrue(outcome.allRestored)
        assertEquals(1, getMangaTaste.await(10L, "/m/1")?.rating)
        assertNull(getMangaTaste.await(10L, "/m/2"))
        assertTrue(EvaluationModeUndoJournal.entriesForBulk(bulkId).isEmpty())
    }
}
// KMK <--

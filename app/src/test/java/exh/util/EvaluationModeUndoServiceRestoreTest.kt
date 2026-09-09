package exh.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.interactor.ClearMangaTaste
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.interactor.SetMangaTaste
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaTaste

// KMK v0.8.19 -->
/**
 * Interactor-level coverage for [EvaluationModeUndoService.restoreOne] -- the actual DB-touching
 * restore logic that [EvaluationModeUndoServiceTest]'s pure-logic tests do not exercise. Backed by
 * [FakeTasteRepository] (real [GetMangaTaste]/[SetMangaTaste]/[ClearMangaTaste] over an in-memory
 * [tachiyomi.domain.taste.repository.TasteRepository]), so these tests exercise the real interactor
 * chain end to end without a database.
 *
 * KMK v0.8.21-fix3: R1 correction -- [EvaluationModeUndoService] no longer takes a
 * `SourcePreferences` (there is no second store for it to restore); restore is a single
 * `SetMangaTaste`/`ClearMangaTaste` write. Not Interested ([MangaRating.NOT_INTERESTED]) is
 * exercised as an ordinary rating value throughout.
 */
class EvaluationModeUndoServiceRestoreTest {

    private val tasteRepository = FakeTasteRepository()
    private val getMangaTaste = GetMangaTaste(tasteRepository)
    private val setMangaTaste = SetMangaTaste(tasteRepository)
    private val clearMangaTaste = ClearMangaTaste(tasteRepository)
    private val service = EvaluationModeUndoService(getMangaTaste, setMangaTaste, clearMangaTaste)

    @AfterEach
    fun tearDown() {
        EvaluationModeUndoJournal.clear()
    }

    private suspend fun seedTaste(mangaId: Long, source: Long, url: String, rating: Int) {
        tasteRepository.upsertMangaTaste(
            MangaTaste(mangaId = mangaId, source = source, url = url, title = "Test Manga", rating = rating, createdAt = 0L, updatedAt = 0L),
        )
    }

    private fun ratingEntry(mangaId: Long, source: Long, url: String, previousRating: Int?, newRating: Int?) =
        EvaluationJournalEntry(
            id = EvaluationJournalEntry.newId(),
            timestamp = System.currentTimeMillis(),
            actionType = EvaluationJournalActionType.RATE_LOVE,
            mangaId = mangaId,
            source = source,
            url = url,
            previousRating = previousRating,
            newRating = newRating,
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
    fun `undo of marking Not Interested clears the MangaTaste row -- the same single-store path as any other rating`() = runTest {
        seedTaste(mangaId = 1L, source = 10L, url = "/m/1", rating = MangaRating.NOT_INTERESTED.value)
        val entry = ratingEntry(mangaId = 1L, source = 10L, url = "/m/1", previousRating = null, newRating = MangaRating.NOT_INTERESTED.value)
            .copy(actionType = EvaluationJournalActionType.NOT_INTERESTED)
        EvaluationModeUndoJournal.record(entry)

        val outcome = service.undo(entry.id)

        assertTrue(outcome.allRestored)
        assertNull(getMangaTaste.await(10L, "/m/1"), "undo of Not Interested with no prior rating clears the row")
    }

    @Test
    fun `undo of a Not-Interested-to-rating transition restores the prior Not Interested state, not an unrated row`() = runTest {
        seedTaste(mangaId = 1L, source = 10L, url = "/m/1", rating = MangaRating.LOVE.value)
        val entry = ratingEntry(
            mangaId = 1L,
            source = 10L,
            url = "/m/1",
            previousRating = MangaRating.NOT_INTERESTED.value,
            newRating = MangaRating.LOVE.value,
        )
        EvaluationModeUndoJournal.record(entry)

        val outcome = service.undo(entry.id)

        assertTrue(outcome.allRestored)
        assertEquals(MangaRating.NOT_INTERESTED.value, getMangaTaste.await(10L, "/m/1")?.rating)
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

    @Test
    fun `missing manga is reported and remains available for retry`() = runTest {
        val entry = ratingEntry(mangaId = 0L, source = 10L, url = "/missing", previousRating = 1, newRating = 2)
            .copy(mangaId = null, newRating = null)
        EvaluationModeUndoJournal.record(entry)

        val outcome = service.undo(entry.id)

        assertEquals(1, outcome.missingCount)
        assertEquals(1, EvaluationModeUndoJournal.snapshot().size)
    }

    @Test
    fun `a write failure preserves state and does not consume the journal entry, then retry succeeds`() = runTest {
        seedTaste(mangaId = 1L, source = 10L, url = "/m/1", rating = 2)
        val entry = ratingEntry(mangaId = 1L, source = 10L, url = "/m/1", previousRating = 1, newRating = 2)
        EvaluationModeUndoJournal.record(entry)
        tasteRepository.tasteWriteFailure = IllegalStateException("simulated write failure")

        assertEquals(1, service.undo(entry.id).failedCount)
        assertEquals(2, getMangaTaste.await(10L, "/m/1")?.rating, "a failed restore must not change the current rating")
        assertEquals(1, EvaluationModeUndoJournal.snapshot().size, "a failed restore must not consume the journal entry")

        tasteRepository.tasteWriteFailure = null
        assertTrue(service.undo(entry.id).allRestored)
        assertEquals(1, getMangaTaste.await(10L, "/m/1")?.rating)
        assertTrue(EvaluationModeUndoJournal.isEmpty())
    }

    @Test
    fun `cancellation propagates and does not consume the journal entry`() = runTest {
        val entry = ratingEntry(mangaId = 1L, source = 10L, url = "/m/1", previousRating = 1, newRating = 2)
        EvaluationModeUndoJournal.record(entry)
        tasteRepository.tasteReadFailure = CancellationException("cancelled")

        assertThrows(CancellationException::class.java) { kotlinx.coroutines.runBlocking { service.undo(entry.id) } }
        assertEquals(1, EvaluationModeUndoJournal.snapshot().size)
    }

    @Test
    fun `bulk undo restores successes and leaves conflicting entries`() = runTest {
        seedTaste(mangaId = 1L, source = 10L, url = "/m/1", rating = 2)
        seedTaste(mangaId = 2L, source = 10L, url = "/m/2", rating = 999)
        val bulkId = EvaluationJournalEntry.newBulkId()
        val first = ratingEntry(1L, 10L, "/m/1", previousRating = 1, newRating = 2).copy(isBulk = true, bulkOperationId = bulkId)
        val conflict = ratingEntry(2L, 10L, "/m/2", previousRating = 1, newRating = 2).copy(isBulk = true, bulkOperationId = bulkId)
        EvaluationModeUndoJournal.record(first)
        EvaluationModeUndoJournal.record(conflict)

        val outcome = service.undoBulk(bulkId)

        assertEquals(2, outcome.requestedCount)
        assertEquals(1, outcome.restoredCount)
        assertEquals(1, outcome.conflictCount)
        assertEquals(listOf(conflict.id), EvaluationModeUndoJournal.entriesForBulk(bulkId).map { it.id })
    }
}
// KMK <--

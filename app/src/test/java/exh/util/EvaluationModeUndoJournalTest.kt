package exh.util

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK v0.8.19 -->
// Evaluation Mode Action Undo Journal: pure in-memory store coverage (record/eviction/bulk grouping).
// The journal is a singleton object (matching EvaluationModeFormatter's precedent), so each test
// clears it first/after to avoid cross-test leakage.
class EvaluationModeUndoJournalTest {

    @AfterEach
    fun tearDown() {
        EvaluationModeUndoJournal.clear()
    }

    private fun entry(
        id: String = EvaluationJournalEntry.newId(),
        actionType: EvaluationJournalActionType = EvaluationJournalActionType.RATE_LOVE,
        bulkId: String? = null,
        timestamp: Long = System.currentTimeMillis(),
    ) = EvaluationJournalEntry(
        id = id,
        timestamp = timestamp,
        actionType = actionType,
        mangaId = 1L,
        source = 10L,
        url = "/manga/1",
        previousRating = null,
        newRating = 2,
        previousNotInterested = false,
        newNotInterested = false,
        isBulk = bulkId != null,
        bulkOperationId = bulkId,
        changedFields = setOf(EvaluationJournalEntry.FIELD_RATING),
    )

    @Test
    fun `single love action is journaled and appears in snapshot`() {
        val e = entry()
        EvaluationModeUndoJournal.record(e)
        assertEquals(listOf(e), EvaluationModeUndoJournal.snapshot())
    }

    @Test
    fun `journal size limit evicts only the oldest entry`() {
        val first = entry(id = "first")
        EvaluationModeUndoJournal.record(first)
        repeat(EvaluationModeUndoJournal.MAX_ENTRIES) {
            EvaluationModeUndoJournal.record(entry())
        }
        val snapshot = EvaluationModeUndoJournal.snapshot()
        assertEquals(EvaluationModeUndoJournal.MAX_ENTRIES, snapshot.size)
        assertFalse(snapshot.any { it.id == "first" }, "oldest entry should have been evicted")
    }

    @Test
    fun `eviction never removes more than the oldest overflow entries`() {
        repeat(EvaluationModeUndoJournal.MAX_ENTRIES + 5) { i ->
            EvaluationModeUndoJournal.record(entry(id = "e$i"))
        }
        val snapshot = EvaluationModeUndoJournal.snapshot()
        assertEquals(EvaluationModeUndoJournal.MAX_ENTRIES, snapshot.size)
        // The 5 oldest (e0..e4) must be gone; the most recent MAX_ENTRIES must remain.
        assertFalse(snapshot.any { it.id == "e0" })
        assertTrue(snapshot.any { it.id == "e${EvaluationModeUndoJournal.MAX_ENTRIES + 4}" })
    }

    @Test
    fun `bulk action groups multiple entries under one bulkOperationId`() {
        val bulkId = EvaluationJournalEntry.newBulkId()
        val e1 = entry(id = "a", bulkId = bulkId)
        val e2 = entry(id = "b", bulkId = bulkId)
        val single = entry(id = "c")
        EvaluationModeUndoJournal.record(e1)
        EvaluationModeUndoJournal.record(e2)
        EvaluationModeUndoJournal.record(single)

        val grouped = EvaluationModeUndoJournal.entriesForBulk(bulkId)
        assertEquals(2, grouped.size)
        assertTrue(grouped.all { it.bulkOperationId == bulkId })
    }

    @Test
    fun `removeByBulkId removes exactly the entries in that bulk operation`() {
        val bulkId = EvaluationJournalEntry.newBulkId()
        EvaluationModeUndoJournal.record(entry(id = "a", bulkId = bulkId))
        EvaluationModeUndoJournal.record(entry(id = "b", bulkId = bulkId))
        EvaluationModeUndoJournal.record(entry(id = "c"))

        EvaluationModeUndoJournal.removeByBulkId(bulkId)

        val remaining = EvaluationModeUndoJournal.snapshot()
        assertEquals(1, remaining.size)
        assertEquals("c", remaining.single().id)
    }

    @Test
    fun `clear removes every entry`() {
        EvaluationModeUndoJournal.record(entry())
        EvaluationModeUndoJournal.record(entry())
        assertFalse(EvaluationModeUndoJournal.isEmpty())

        EvaluationModeUndoJournal.clear()

        assertTrue(EvaluationModeUndoJournal.isEmpty())
        assertTrue(EvaluationModeUndoJournal.snapshot().isEmpty())
    }

    @Test
    fun `snapshot returns most recent entries first`() {
        val first = entry(id = "first", timestamp = 1000L)
        val second = entry(id = "second", timestamp = 2000L)
        EvaluationModeUndoJournal.record(first)
        EvaluationModeUndoJournal.record(second)

        val snapshot = EvaluationModeUndoJournal.snapshot()
        assertEquals("second", snapshot.first().id)
        assertEquals("first", snapshot.last().id)
    }

    @Test
    fun `removeById only removes the matching entry`() {
        EvaluationModeUndoJournal.record(entry(id = "keep"))
        EvaluationModeUndoJournal.record(entry(id = "remove"))

        EvaluationModeUndoJournal.removeById("remove")

        val remaining = EvaluationModeUndoJournal.snapshot()
        assertEquals(1, remaining.size)
        assertEquals("keep", remaining.single().id)
    }
}
// KMK <--

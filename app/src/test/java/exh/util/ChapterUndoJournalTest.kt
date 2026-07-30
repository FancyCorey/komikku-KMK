package exh.util

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK Undo Expansion Phase 2 -->
class ChapterUndoJournalTest {

    @AfterEach
    fun tearDown() {
        ChapterUndoJournal.clear()
    }

    private fun entry(id: String, chapterId: Long = 1L, bulkId: String? = null) = ChapterJournalEntry(
        id = id,
        timestamp = System.currentTimeMillis(),
        actionType = ChapterJournalActionType.BOOKMARK,
        chapterId = chapterId,
        previousRead = null,
        expectedPostRead = null,
        previousBookmark = false,
        expectedPostBookmark = true,
        isBulk = bulkId != null,
        bulkOperationId = bulkId,
    )

    @Test
    fun `record then snapshot returns newest first`() {
        ChapterUndoJournal.record(entry("e1"))
        ChapterUndoJournal.record(entry("e2"))
        assertEquals(listOf("e2", "e1"), ChapterUndoJournal.snapshot().map { it.id })
    }

    @Test
    fun `eviction is bulk-group-aware`() {
        val bulkId = "bulk-1"
        ChapterUndoJournal.record(entry("b1", chapterId = 1L, bulkId = bulkId))
        ChapterUndoJournal.record(entry("b2", chapterId = 2L, bulkId = bulkId))
        repeat(ChapterUndoJournal.MAX_ENTRIES) { i -> ChapterUndoJournal.record(entry("f$i", chapterId = i.toLong() + 10)) }
        val snapshot = ChapterUndoJournal.snapshot()
        assertTrue(snapshot.none { it.bulkOperationId == bulkId })
        assertEquals(ChapterUndoJournal.MAX_ENTRIES, snapshot.size)
    }

    @Test
    fun `isEmpty and clear behave as expected`() {
        assertTrue(ChapterUndoJournal.isEmpty())
        ChapterUndoJournal.record(entry("e1"))
        assertFalse(ChapterUndoJournal.isEmpty())
        ChapterUndoJournal.clear()
        assertTrue(ChapterUndoJournal.isEmpty())
    }

    @Test
    fun `500-chapter bound policy rejects oversized batches`() {
        assertFalse(ChapterUndoBoundPolicy.exceedsBound(500))
        assertTrue(ChapterUndoBoundPolicy.exceedsBound(501))
    }
}
// KMK <--

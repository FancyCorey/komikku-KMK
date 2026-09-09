package exh.util

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK -->
class LibraryUndoJournalTest {

    @AfterEach
    fun tearDown() {
        LibraryUndoJournal.clear()
    }

    private fun favoriteEntry(id: String, mangaId: Long = 1L, bulkId: String? = null) = LibraryJournalEntry(
        id = id,
        timestamp = System.currentTimeMillis(),
        actionType = LibraryJournalActionType.FAVORITE,
        mangaId = mangaId,
        previousFavorite = false,
        previousDateAdded = 0L,
        expectedPostFavorite = true,
        previousCategoryIds = null,
        expectedPostCategoryIds = null,
        isBulk = bulkId != null,
        bulkOperationId = bulkId,
    )

    @Test
    fun `record then snapshot returns newest first`() {
        LibraryUndoJournal.record(favoriteEntry("e1"))
        LibraryUndoJournal.record(favoriteEntry("e2"))
        assertEquals(listOf("e2", "e1"), LibraryUndoJournal.snapshot().map { it.id })
    }

    @Test
    fun `isEmpty reflects journal state`() {
        assertTrue(LibraryUndoJournal.isEmpty())
        LibraryUndoJournal.record(favoriteEntry("e1"))
        assertFalse(LibraryUndoJournal.isEmpty())
    }

    @Test
    fun `eviction is bulk-group-aware, mirroring the rating journal`() {
        val bulkId = "bulk-1"
        LibraryUndoJournal.record(favoriteEntry("b1", mangaId = 1L, bulkId = bulkId))
        LibraryUndoJournal.record(favoriteEntry("b2", mangaId = 2L, bulkId = bulkId))
        repeat(LibraryUndoJournal.MAX_ENTRIES) { i -> LibraryUndoJournal.record(favoriteEntry("f$i", mangaId = i.toLong() + 10)) }
        val snapshot = LibraryUndoJournal.snapshot()
        assertTrue(snapshot.none { it.bulkOperationId == bulkId })
        assertEquals(LibraryUndoJournal.MAX_ENTRIES, snapshot.size)
    }

    @Test
    fun `removeById removes exactly one entry`() {
        LibraryUndoJournal.record(favoriteEntry("e1"))
        LibraryUndoJournal.record(favoriteEntry("e2"))
        LibraryUndoJournal.removeById("e1")
        assertEquals(listOf("e2"), LibraryUndoJournal.snapshot().map { it.id })
    }

    @Test
    fun `entriesForBulk returns only that bulk group`() {
        LibraryUndoJournal.record(favoriteEntry("b1", bulkId = "g1"))
        LibraryUndoJournal.record(favoriteEntry("b2", bulkId = "g1"))
        LibraryUndoJournal.record(favoriteEntry("other", bulkId = "g2"))
        assertEquals(2, LibraryUndoJournal.entriesForBulk("g1").size)
    }

    @Test
    fun `clear empties the journal`() {
        LibraryUndoJournal.record(favoriteEntry("e1"))
        LibraryUndoJournal.clear()
        assertTrue(LibraryUndoJournal.isEmpty())
    }
}
// KMK <--

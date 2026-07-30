package exh.util

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK v0.8.20 -->
class GroupUndoJournalTest {

    @AfterEach
    fun tearDown() {
        GroupUndoJournal.clear()
    }

    private fun entry(id: String, keys: Set<RatedLinkKey> = setOf(RatedLinkKey(1L, "/m/$id"))) = GroupJournalEntry(
        id = id,
        timestamp = System.currentTimeMillis(),
        actionType = GroupJournalActionType.UNGROUP,
        touchedKeys = keys,
        previousLinks = keys.associateWith { null },
        expectedPostLinks = keys.associateWith { null },
        touchedGroupIds = emptySet(),
        previousPrimaries = emptyMap(),
        expectedPostPrimaries = emptyMap(),
    )

    @Test
    fun `record then snapshot returns the entry newest-first`() {
        GroupUndoJournal.record(entry("e1"))
        GroupUndoJournal.record(entry("e2"))
        val snapshot = GroupUndoJournal.snapshot()
        assertEquals(listOf("e2", "e1"), snapshot.map { it.id })
    }

    @Test
    fun `journal starts empty and reports isEmpty correctly`() {
        assertTrue(GroupUndoJournal.isEmpty())
        GroupUndoJournal.record(entry("e1"))
        assertFalse(GroupUndoJournal.isEmpty())
    }

    @Test
    fun `eviction never splits an operation -- the oldest whole entry is evicted, not partial rows`() {
        // Each entry already encapsulates a whole operation (potentially many touched keys), so
        // eviction always removes a whole entry -- this test documents that invariant by recording
        // more than MAX_ENTRIES operations, each with multiple touched keys, and confirming every
        // surviving entry is intact (no entry ever has a subset of its original touchedKeys).
        val recorded = (1..GroupUndoJournal.MAX_ENTRIES + 5).map { i ->
            val e = entry("e$i", keys = setOf(RatedLinkKey(1L, "/m/$i/a"), RatedLinkKey(1L, "/m/$i/b")))
            GroupUndoJournal.record(e)
            e
        }
        val snapshot = GroupUndoJournal.snapshot()
        assertEquals(GroupUndoJournal.MAX_ENTRIES, snapshot.size)
        // The 5 oldest entries were fully evicted -- none of their ids survive.
        val evictedIds = recorded.take(5).map { it.id }
        assertTrue(snapshot.none { it.id in evictedIds })
        // Every surviving entry still has both of its original touched keys -- eviction removed whole
        // entries, never trimmed a surviving entry's touchedKeys down to a partial set.
        snapshot.forEach { assertEquals(2, it.touchedKeys.size) }
    }

    @Test
    fun `removeById removes exactly one entry`() {
        GroupUndoJournal.record(entry("e1"))
        GroupUndoJournal.record(entry("e2"))
        GroupUndoJournal.removeById("e1")
        assertEquals(listOf("e2"), GroupUndoJournal.snapshot().map { it.id })
    }

    @Test
    fun `clear empties the journal`() {
        GroupUndoJournal.record(entry("e1"))
        GroupUndoJournal.clear()
        assertTrue(GroupUndoJournal.isEmpty())
    }
}
// KMK <--

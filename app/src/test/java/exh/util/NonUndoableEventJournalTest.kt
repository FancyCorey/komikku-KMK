package exh.util

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

// KMK v0.8.20-fix1 -->
/**
 * Tests for [NonUndoableEventJournal] -- the bounded in-memory record for operations (migration,
 * extension install) that have no safe inverse. Unlike [EvaluationModeUndoJournal] and its typed
 * siblings, entries here are never restorable; these tests pin the journal's own mechanics
 * (recording, bounding, clearing) and its privacy contract (no identifying fields exist on the
 * entry type at all, so there is nothing to assert is absent -- see [NonUndoableEvent]'s shape).
 */
class NonUndoableEventJournalTest {

    @BeforeEach
    fun setUp() {
        NonUndoableEventJournal.clear()
    }

    @AfterEach
    fun tearDown() {
        NonUndoableEventJournal.clear()
    }

    @Test
    fun `starts empty`() {
        assertTrue(NonUndoableEventJournal.isEmpty())
        assertEquals(0, NonUndoableEventJournal.snapshot().size)
    }

    @Test
    fun `recording an event makes it visible in the snapshot`() {
        val event = NonUndoableEvent(
            id = NonUndoableEvent.newId(),
            timestamp = 1000L,
            eventType = NonUndoableEventType.MIGRATION_COMPLETED,
        )
        NonUndoableEventJournal.record(event)

        val snapshot = NonUndoableEventJournal.snapshot()
        assertEquals(1, snapshot.size)
        assertEquals(NonUndoableEventType.MIGRATION_COMPLETED, snapshot.first().eventType)
    }

    @Test
    fun `snapshot is most-recent-first`() {
        NonUndoableEventJournal.record(NonUndoableEvent(NonUndoableEvent.newId(), 1000L, NonUndoableEventType.EXTENSION_INSTALLED))
        NonUndoableEventJournal.record(NonUndoableEvent(NonUndoableEvent.newId(), 2000L, NonUndoableEventType.MIGRATION_COMPLETED))

        val snapshot = NonUndoableEventJournal.snapshot()
        assertEquals(2000L, snapshot[0].timestamp)
        assertEquals(1000L, snapshot[1].timestamp)
    }

    @Test
    fun `bounded at MAX_ENTRIES -- oldest entries are evicted first`() {
        repeat(NonUndoableEventJournal.MAX_ENTRIES + 5) { i ->
            NonUndoableEventJournal.record(
                NonUndoableEvent(NonUndoableEvent.newId(), i.toLong(), NonUndoableEventType.EXTENSION_INSTALLED),
            )
        }

        val snapshot = NonUndoableEventJournal.snapshot()
        assertEquals(NonUndoableEventJournal.MAX_ENTRIES, snapshot.size)
        // The 5 oldest (timestamps 0-4) must have been evicted -- the oldest surviving entry is 5.
        assertEquals(5L, snapshot.last().timestamp)
    }

    @Test
    fun `clear empties the journal`() {
        NonUndoableEventJournal.record(NonUndoableEvent(NonUndoableEvent.newId(), 1000L, NonUndoableEventType.MIGRATION_COMPLETED))
        assertTrue(!NonUndoableEventJournal.isEmpty())

        NonUndoableEventJournal.clear()

        assertTrue(NonUndoableEventJournal.isEmpty())
        assertEquals(0, NonUndoableEventJournal.snapshot().size)
    }

    @Test
    fun `entries never carry manga, source, or extension identity -- only a type and a timestamp`() {
        // NonUndoableEvent's constructor only accepts id/timestamp/eventType -- this test documents
        // and pins that contract so a future change can't quietly add an identifying field.
        val event = NonUndoableEvent(
            id = NonUndoableEvent.newId(),
            timestamp = 1000L,
            eventType = NonUndoableEventType.EXTENSION_INSTALLED,
        )
        val fields = NonUndoableEvent::class.java.declaredFields
            .filterNot { it.isSynthetic || it.name == "Companion" || it.name == "\$stable" }
            .map { it.name }
            .toSet()
        assertEquals(setOf("id", "timestamp", "eventType"), fields)
        assertEquals(NonUndoableEventType.EXTENSION_INSTALLED, event.eventType)
    }
}
// KMK <--

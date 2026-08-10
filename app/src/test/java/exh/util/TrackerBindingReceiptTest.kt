package exh.util

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK -->
class TrackerBindingReceiptTest {

    @AfterEach
    fun tearDown() {
        NonUndoableEventJournal.clear()
        TrackerBindingReceiptJournal.clear()
    }

    private fun receipt(id: String, timestamp: Long = 1L) = TrackerBindingReceipt(
        id = id,
        timestamp = timestamp,
        mangaId = 10L,
        trackerId = 20L,
        remoteId = 30L,
    )

    @Test
    fun `disabled evaluation mode records nothing`() {
        recordSuccessfulTrackerBinding(false, 10L, 20L, 30L)
        assertTrue(TrackerBindingReceiptJournal.isEmpty())
        assertTrue(NonUndoableEventJournal.isEmpty())
    }

    @Test
    fun `enabled evaluation mode records correlated event and receipt`() {
        recordSuccessfulTrackerBinding(true, 10L, 20L, 30L)
        val event = NonUndoableEventJournal.snapshot().single()
        assertEquals(NonUndoableEventType.TRACKER_BOUND, event.eventType)
        assertEquals(event.id, TrackerBindingReceiptJournal.snapshot().single().id)
    }

    @Test
    fun `journal is newest first and bounded`() {
        repeat(TrackerBindingReceiptJournal.MAX_ENTRIES + 1) {
            TrackerBindingReceiptJournal.record(receipt(it.toString(), it.toLong()))
        }
        assertEquals(TrackerBindingReceiptJournal.MAX_ENTRIES, TrackerBindingReceiptJournal.snapshot().size)
        assertNull(TrackerBindingReceiptJournal.forId("0"))
        assertEquals("20", TrackerBindingReceiptJournal.snapshot().first().id)
    }

    @Test
    fun `receipt contains only opaque recovery fields`() {
        val allowed = setOf("id", "timestamp", "mangaId", "trackerId", "remoteId")
        val fields = TrackerBindingReceipt::class.java.declaredFields
            .filterNot { it.isSynthetic || it.name == "Companion" || it.name == "\$stable" }
            .map { it.name }
            .toSet()
        assertEquals(allowed, fields)
    }
}
// KMK <--

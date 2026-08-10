package exh.util

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK -->
class TrackWriteReceiptJournalTest {

    @AfterEach
    fun tearDown() {
        TrackWriteReceiptJournal.clear()
    }

    private fun receipt(id: String, timestamp: Long = 1L) = TrackWriteReceipt(
        id = id,
        timestamp = timestamp,
        mangaId = 10L,
        trackerId = 20L,
        field = TrackWriteField.STATUS,
        previousStatus = 1L,
        previousScore = null,
        previousChapterProgress = null,
    )

    @Test
    fun `a fresh journal is empty`() {
        assertTrue(TrackWriteReceiptJournal.isEmpty())
        assertEquals(emptyList<TrackWriteReceipt>(), TrackWriteReceiptJournal.snapshot())
    }

    @Test
    fun `record makes the journal addressable by id`() {
        val item = receipt("a")
        TrackWriteReceiptJournal.record(item)
        assertEquals(item, TrackWriteReceiptJournal.forId("a"))
    }

    @Test
    fun `missing id returns null`() {
        assertNull(TrackWriteReceiptJournal.forId("missing"))
    }

    @Test
    fun `snapshot is most recent first`() {
        TrackWriteReceiptJournal.record(receipt("a", 1L))
        TrackWriteReceiptJournal.record(receipt("b", 2L))
        assertEquals(listOf("b", "a"), TrackWriteReceiptJournal.snapshot().map { it.id })
    }

    @Test
    fun `journal is bounded and evicts the oldest receipt`() {
        repeat(TrackWriteReceiptJournal.MAX_ENTRIES + 1) { TrackWriteReceiptJournal.record(receipt("$it")) }
        assertEquals(TrackWriteReceiptJournal.MAX_ENTRIES, TrackWriteReceiptJournal.snapshot().size)
        assertNull(TrackWriteReceiptJournal.forId("0"))
    }

    @Test
    fun `clear empties the journal`() {
        TrackWriteReceiptJournal.record(receipt("a"))
        TrackWriteReceiptJournal.clear()
        assertTrue(TrackWriteReceiptJournal.isEmpty())
    }

    @Test
    fun `receipt fields stay limited to opaque recovery data`() {
        val allowed = setOf(
            "id",
            "timestamp",
            "mangaId",
            "trackerId",
            "field",
            "previousStatus",
            "previousScore",
            "previousChapterProgress",
            "previousStartDate",
            "previousFinishDate",
            "previousPrivate",
        )
        val fields = TrackWriteReceipt::class.java.declaredFields
            .filterNot { it.isSynthetic || it.name == "Companion" || it.name == "\$stable" }
            .map { it.name }
            .toSet()
        assertTrue(fields.all { it in allowed })
        assertEquals(allowed, fields)
    }
}
// KMK <--

package exh.util

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK Universal Action History Recovery Plan 2026-08-01 -->
/**
 * Direct tests for [DownloadReceiptJournal] -- the private, bounded, most-recent-first store that
 * lets [ActionHistoryRegistry]'s `downloadFollowUpFor` correlate a rendered `DOWNLOAD_DELETED` event
 * back to the exact chapter ids a "Re-download" follow-up needs. Mirrors
 * [MigrationReceiptJournalTest]'s own test shape.
 */
class DownloadReceiptJournalTest {

    @AfterEach
    fun tearDown() {
        DownloadReceiptJournal.clear()
    }

    private fun receipt(id: String, mangaId: Long = 1L, chapterIds: List<Long> = listOf(10L, 11L)) = DownloadReceipt(
        id = id,
        timestamp = System.currentTimeMillis(),
        mangaId = mangaId,
        sourceId = 100L,
        chapterIds = chapterIds,
    )

    @Test
    fun `a fresh journal is empty`() {
        assertTrue(DownloadReceiptJournal.isEmpty())
        assertEquals(emptyList<DownloadReceipt>(), DownloadReceiptJournal.snapshot())
    }

    @Test
    fun `record makes the journal non-empty and forId resolves it`() {
        val r = receipt(id = "a")

        DownloadReceiptJournal.record(r)

        assertTrue(DownloadReceiptJournal.isEmpty().not())
        assertEquals(r, DownloadReceiptJournal.forId("a"))
    }

    @Test
    fun `forId returns null for an id that was never recorded`() {
        DownloadReceiptJournal.record(receipt(id = "a"))

        assertNull(DownloadReceiptJournal.forId("does-not-exist"))
    }

    @Test
    fun `snapshot returns most recent first`() {
        DownloadReceiptJournal.record(receipt(id = "a"))
        DownloadReceiptJournal.record(receipt(id = "b"))
        DownloadReceiptJournal.record(receipt(id = "c"))

        val ids = DownloadReceiptJournal.snapshot().map { it.id }

        assertEquals(listOf("c", "b", "a"), ids)
    }

    @Test
    fun `the journal evicts the oldest entry once it exceeds MAX_ENTRIES`() {
        repeat(DownloadReceiptJournal.MAX_ENTRIES + 5) { i ->
            DownloadReceiptJournal.record(receipt(id = "id-$i"))
        }

        val snapshot = DownloadReceiptJournal.snapshot()

        assertEquals(DownloadReceiptJournal.MAX_ENTRIES, snapshot.size)
        assertNull(DownloadReceiptJournal.forId("id-0"), "the oldest entries must have been evicted")
        assertEquals("id-${DownloadReceiptJournal.MAX_ENTRIES + 4}", snapshot.first().id)
    }

    @Test
    fun `clear empties the journal`() {
        DownloadReceiptJournal.record(receipt(id = "a"))

        DownloadReceiptJournal.clear()

        assertTrue(DownloadReceiptJournal.isEmpty())
        assertNull(DownloadReceiptJournal.forId("a"))
    }
}
// KMK <--

package exh.util

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK -->
/**
 * Direct tests for [MigrationReceiptJournal] -- the private, bounded, most-recent-first store that
 * lets [exh.util.ActionHistoryRegistry]'s `migrationFollowUpFor` correlate a rendered
 * `MIGRATION_COMPLETED` event back to the origin/target manga identity a "Migrate back" follow-up
 * needs. Mirrors [PackageOperationJournal]'s own test coverage shape (bound, ordering, id lookup,
 * clear).
 */
class MigrationReceiptJournalTest {

    @AfterEach
    fun tearDown() {
        MigrationReceiptJournal.clear()
    }

    private fun receipt(id: String, originMangaId: Long = 1L, targetMangaId: Long = 2L) = MigrationReceipt(
        id = id,
        timestamp = System.currentTimeMillis(),
        originMangaId = originMangaId,
        originSourceId = 10L,
        targetMangaId = targetMangaId,
        targetSourceId = 20L,
        replace = false,
    )

    @Test
    fun `a fresh journal is empty`() {
        assertTrue(MigrationReceiptJournal.isEmpty())
        assertEquals(emptyList<MigrationReceipt>(), MigrationReceiptJournal.snapshot())
    }

    @Test
    fun `record makes the journal non-empty and forId resolves it`() {
        val r = receipt(id = "a")

        MigrationReceiptJournal.record(r)

        assertTrue(MigrationReceiptJournal.isEmpty().not())
        assertEquals(r, MigrationReceiptJournal.forId("a"))
    }

    @Test
    fun `forId returns null for an id that was never recorded`() {
        MigrationReceiptJournal.record(receipt(id = "a"))

        assertNull(MigrationReceiptJournal.forId("does-not-exist"))
    }

    @Test
    fun `snapshot returns most recent first`() {
        MigrationReceiptJournal.record(receipt(id = "a"))
        MigrationReceiptJournal.record(receipt(id = "b"))
        MigrationReceiptJournal.record(receipt(id = "c"))

        val ids = MigrationReceiptJournal.snapshot().map { it.id }

        assertEquals(listOf("c", "b", "a"), ids)
    }

    @Test
    fun `the journal evicts the oldest entry once it exceeds MAX_ENTRIES`() {
        repeat(MigrationReceiptJournal.MAX_ENTRIES + 5) { i ->
            MigrationReceiptJournal.record(receipt(id = "id-$i"))
        }

        val snapshot = MigrationReceiptJournal.snapshot()

        assertEquals(MigrationReceiptJournal.MAX_ENTRIES, snapshot.size)
        assertNull(MigrationReceiptJournal.forId("id-0"), "the oldest entries must have been evicted")
        assertEquals("id-${MigrationReceiptJournal.MAX_ENTRIES + 4}", snapshot.first().id)
    }

    @Test
    fun `clear empties the journal`() {
        MigrationReceiptJournal.record(receipt(id = "a"))

        MigrationReceiptJournal.clear()

        assertTrue(MigrationReceiptJournal.isEmpty())
        assertNull(MigrationReceiptJournal.forId("a"))
    }
}
// KMK <--

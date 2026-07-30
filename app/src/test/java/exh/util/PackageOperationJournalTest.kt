package exh.util

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK Confirmed Blocker Remediation Corrective Completion Plan V2 2026-07-29 -->
class PackageOperationJournalTest {

    @AfterEach
    fun tearDown() {
        PackageOperationJournal.clear()
    }

    private fun receipt(
        id: String = PackageOperationReceipt.newId(),
        kind: PackageOperationKind = PackageOperationKind.INSTALL,
        packageName: String = "eu.kanade.tachiyomi.extension.en.a",
        signatureHash: String? = "sig1",
        versionCode: Long? = 1L,
        artifactUri: String? = "https://example.invalid/a.apk",
        timestamp: Long = System.currentTimeMillis(),
    ) = PackageOperationReceipt(
        id = id,
        timestamp = timestamp,
        kind = kind,
        packageName = packageName,
        signatureHash = signatureHash,
        versionCode = versionCode,
        artifactUri = artifactUri,
    )

    @Test
    fun `starts empty`() {
        assertTrue(PackageOperationJournal.isEmpty())
        assertTrue(PackageOperationJournal.snapshot().isEmpty())
    }

    @Test
    fun `recording a receipt makes it visible in the snapshot`() {
        val r = receipt()
        PackageOperationJournal.record(r)

        assertEquals(listOf(r), PackageOperationJournal.snapshot())
    }

    @Test
    fun `snapshot is most-recent-first`() {
        val first = receipt(id = "1", timestamp = 100L)
        val second = receipt(id = "2", timestamp = 200L)
        PackageOperationJournal.record(first)
        PackageOperationJournal.record(second)

        assertEquals(listOf(second, first), PackageOperationJournal.snapshot())
    }

    @Test
    fun `bounded at MAX_ENTRIES -- oldest entries are evicted first`() {
        repeat(PackageOperationJournal.MAX_ENTRIES + 5) { i ->
            PackageOperationJournal.record(receipt(id = "id-$i", timestamp = i.toLong()))
        }

        val snapshot = PackageOperationJournal.snapshot()
        assertEquals(PackageOperationJournal.MAX_ENTRIES, snapshot.size)
        assertTrue(snapshot.none { it.id == "id-0" }, "the oldest entry must have been evicted")
        assertTrue(snapshot.any { it.id == "id-${PackageOperationJournal.MAX_ENTRIES + 4}" }, "the newest entry must survive")
    }

    @Test
    fun `clear empties the journal`() {
        PackageOperationJournal.record(receipt())
        PackageOperationJournal.clear()

        assertTrue(PackageOperationJournal.isEmpty())
    }

    @Test
    fun `latestFor returns the most recent receipt for a package name and ignores others`() {
        val other = receipt(id = "other", packageName = "eu.kanade.tachiyomi.extension.en.other", timestamp = 1L)
        val older = receipt(id = "older", packageName = "eu.kanade.tachiyomi.extension.en.a", kind = PackageOperationKind.INSTALL, timestamp = 2L)
        val newer = receipt(id = "newer", packageName = "eu.kanade.tachiyomi.extension.en.a", kind = PackageOperationKind.UPDATE, timestamp = 3L)
        PackageOperationJournal.record(other)
        PackageOperationJournal.record(older)
        PackageOperationJournal.record(newer)

        assertEquals(newer, PackageOperationJournal.latestFor("eu.kanade.tachiyomi.extension.en.a"))
    }

    @Test
    fun `latestFor returns null when no receipt exists for that package name`() {
        assertNull(PackageOperationJournal.latestFor("eu.kanade.tachiyomi.extension.en.unknown"))
    }
}
// KMK <--

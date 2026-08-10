package eu.kanade.tachiyomi.data.sync.service

import eu.kanade.tachiyomi.data.backup.models.BackupCrossSourceGroupPrimary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for [SyncService.Companion.mergeCrossSourceGroupPrimariesPure] (v0.8.1-fix1).
 *
 * Run with: ./gradlew :app:testDebugUnitTest --tests "*.SyncServiceCrossSourceGroupPrimaryMergeTest"
 */
class SyncServiceCrossSourceGroupPrimaryMergeTest {

    private fun primary(groupId: String, source: Long = 1L, url: String = "/a", updatedAt: Long = 0L) =
        BackupCrossSourceGroupPrimary(groupId = groupId, source = source, url = url, updatedAt = updatedAt)

    @Test
    fun `local-only primary is kept`() {
        val result = SyncService.mergeCrossSourceGroupPrimariesPure(
            localPrimaries = listOf(primary("group-1", updatedAt = 100L)),
            remotePrimaries = null,
        )
        assertEquals(listOf(primary("group-1", updatedAt = 100L)), result)
    }

    @Test
    fun `remote-only primary is kept`() {
        val result = SyncService.mergeCrossSourceGroupPrimariesPure(
            localPrimaries = null,
            remotePrimaries = listOf(primary("group-1", updatedAt = 100L)),
        )
        assertEquals(listOf(primary("group-1", updatedAt = 100L)), result)
    }

    @Test
    fun `conflict keeps the newer updatedAt — remote wins`() {
        val local = primary("group-1", source = 1L, url = "/local", updatedAt = 100L)
        val remote = primary("group-1", source = 2L, url = "/remote", updatedAt = 200L)
        val result = SyncService.mergeCrossSourceGroupPrimariesPure(listOf(local), listOf(remote))
        assertEquals(listOf(remote), result)
    }

    @Test
    fun `conflict keeps the newer updatedAt — local wins`() {
        val local = primary("group-1", source = 1L, url = "/local", updatedAt = 300L)
        val remote = primary("group-1", source = 2L, url = "/remote", updatedAt = 200L)
        val result = SyncService.mergeCrossSourceGroupPrimariesPure(listOf(local), listOf(remote))
        assertEquals(listOf(local), result)
    }

    @Test
    fun `equal updatedAt keeps local (tie-break matches mergeCrossSourceMangaLinks)`() {
        val local = primary("group-1", source = 1L, url = "/local", updatedAt = 100L)
        val remote = primary("group-1", source = 2L, url = "/remote", updatedAt = 100L)
        val result = SyncService.mergeCrossSourceGroupPrimariesPure(listOf(local), listOf(remote))
        assertEquals(listOf(local), result)
    }

    @Test
    fun `distinct groups from both sides are all preserved`() {
        val result = SyncService.mergeCrossSourceGroupPrimariesPure(
            localPrimaries = listOf(primary("group-local")),
            remotePrimaries = listOf(primary("group-remote")),
        )
        assertEquals(setOf("group-local", "group-remote"), result.map { it.groupId }.toSet())
    }

    @Test
    fun `blank groupId rows are filtered from both sides`() {
        val result = SyncService.mergeCrossSourceGroupPrimariesPure(
            localPrimaries = listOf(primary("")),
            remotePrimaries = listOf(primary("  ")),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `both null returns empty list`() {
        val result = SyncService.mergeCrossSourceGroupPrimariesPure(null, null)
        assertTrue(result.isEmpty())
    }

    // KMK --> v0.8.1-fix2: sync merge validation hardened to match
    // CrossSourceGroupPrimaryRestorePolicy.isValid() (blank groupId, source == 0L, blank url)

    @Test
    fun `source equal to zero is filtered from both sides`() {
        val result = SyncService.mergeCrossSourceGroupPrimariesPure(
            localPrimaries = listOf(primary("group-1", source = 0L)),
            remotePrimaries = listOf(primary("group-2", source = 0L)),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `blank url is filtered from both sides`() {
        val result = SyncService.mergeCrossSourceGroupPrimariesPure(
            localPrimaries = listOf(primary("group-1", url = "")),
            remotePrimaries = listOf(primary("group-2", url = "  ")),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `valid rows still merge normally after invalid rows are filtered out`() {
        val validLocal = primary("group-valid", source = 1L, url = "/valid-local", updatedAt = 100L)
        val invalidLocal = primary("group-invalid", source = 0L, url = "/invalid")
        val validRemote = primary("group-remote-valid", source = 2L, url = "/valid-remote", updatedAt = 50L)
        val invalidRemote = primary("", source = 3L, url = "/also-invalid")

        val result = SyncService.mergeCrossSourceGroupPrimariesPure(
            localPrimaries = listOf(validLocal, invalidLocal),
            remotePrimaries = listOf(validRemote, invalidRemote),
        )

        assertEquals(setOf(validLocal, validRemote), result.toSet())
    }
    // KMK <--
}

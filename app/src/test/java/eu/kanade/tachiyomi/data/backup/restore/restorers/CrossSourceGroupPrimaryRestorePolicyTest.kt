package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCrossSourceGroupPrimary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for [CrossSourceGroupPrimaryRestorePolicy] (v0.8.1-fix1).
 *
 * Run with: ./gradlew :app:testDebugUnitTest --tests "*.CrossSourceGroupPrimaryRestorePolicyTest"
 */
class CrossSourceGroupPrimaryRestorePolicyTest {

    private fun primary(groupId: String = "g", source: Long = 1L, url: String = "/a", updatedAt: Long = 0L) =
        BackupCrossSourceGroupPrimary(groupId = groupId, source = source, url = url, updatedAt = updatedAt)

    // --- isValid ---

    @Test
    fun `blank groupId is invalid`() {
        assertFalse(CrossSourceGroupPrimaryRestorePolicy.isValid(primary(groupId = "")))
    }

    @Test
    fun `source zero is invalid`() {
        assertFalse(CrossSourceGroupPrimaryRestorePolicy.isValid(primary(source = 0L)))
    }

    @Test
    fun `blank url is invalid`() {
        assertFalse(CrossSourceGroupPrimaryRestorePolicy.isValid(primary(url = "")))
    }

    @Test
    fun `a row with all fields populated is valid`() {
        assertTrue(CrossSourceGroupPrimaryRestorePolicy.isValid(primary()))
    }

    // --- newestOf ---

    @Test
    fun `newestOf picks the highest updatedAt among candidates for the same group`() {
        val older = primary(url = "/old", updatedAt = 100L)
        val newer = primary(url = "/new", updatedAt = 200L)
        assertEquals(newer, CrossSourceGroupPrimaryRestorePolicy.newestOf(listOf(older, newer)))
    }

    // --- shouldRestore: precedence rules from plan §B3 ---

    @Test
    fun `missing existing primary always restores the backup row`() {
        assertTrue(CrossSourceGroupPrimaryRestorePolicy.shouldRestore(existingUpdatedAt = null, backupUpdatedAt = 100L))
    }

    @Test
    fun `newer backup wins over an older existing primary`() {
        assertTrue(CrossSourceGroupPrimaryRestorePolicy.shouldRestore(existingUpdatedAt = 100L, backupUpdatedAt = 200L))
    }

    @Test
    fun `newer existing primary wins over an older backup`() {
        assertFalse(CrossSourceGroupPrimaryRestorePolicy.shouldRestore(existingUpdatedAt = 300L, backupUpdatedAt = 200L))
    }

    @Test
    fun `equal updatedAt keeps the existing primary — restore is not triggered on a tie`() {
        assertFalse(CrossSourceGroupPrimaryRestorePolicy.shouldRestore(existingUpdatedAt = 100L, backupUpdatedAt = 100L))
    }
}

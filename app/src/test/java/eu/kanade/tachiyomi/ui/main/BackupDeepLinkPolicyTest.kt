package eu.kanade.tachiyomi.ui.main

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupDeepLinkPolicyTest {

    @Test
    fun `file and content backup paths are allowed`() {
        assertTrue(BackupDeepLinkPolicy.isAllowed("file", "/downloads/backup.tachibk"))
        assertTrue(BackupDeepLinkPolicy.isAllowed("content", "/document/backup.tachibk"))
    }

    @Test
    fun `null and network schemes are rejected`() {
        assertFalse(BackupDeepLinkPolicy.isAllowed(null, null))
        assertFalse(BackupDeepLinkPolicy.isAllowed("https", "/backup.tachibk"))
        assertFalse(BackupDeepLinkPolicy.isAllowed("tachiyomi", "/backup.tachibk"))
    }

    @Test
    fun `non-backup paths are rejected`() {
        assertFalse(BackupDeepLinkPolicy.isAllowed("content", "/document/backup.zip"))
        assertFalse(BackupDeepLinkPolicy.isAllowed("content", "/document/backup.tachibk.tmp"))
    }

    @Test
    fun `query parameters do not change the validated backup path`() {
        assertTrue(BackupDeepLinkPolicy.isAllowed("content", "/document/backup.tachibk"))
    }
}

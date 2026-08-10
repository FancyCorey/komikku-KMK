package eu.kanade.tachiyomi.data.sync

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class SyncManagerPrivacyLoggingTest {
    @Test
    fun `sync diagnostics do not expose cache uri or remote manga titles`() {
        val source = File("src/main/java/eu/kanade/tachiyomi/data/sync/SyncManager.kt").readText()

        assertFalse(source.contains("Got Backup Uri: \$backupUri"))
        assertFalse(source.contains("Adding to favorites: \${remoteManga.title}"))
        assertFalse(source.contains("Already up-to-date favorite: \${remoteManga.title}"))
        assertFalse(source.contains("Adding to non-favorites: \${remoteManga.title}"))
        assertTrue(source.contains("Sync backup cache prepared"))
    }

    @Test
    fun `sync cache failure diagnostics do not include throwable payload`() {
        val source = File("src/main/java/eu/kanade/tachiyomi/data/sync/SyncManager.kt").readText()

        assertFalse(source.contains("logcat(LogPriority.ERROR, throwable = e)"))
        assertFalse(source.contains("logcat(LogPriority.ERROR, e) { \"Failed to write sync data to cache\" }"))
        assertTrue(source.contains("logcat(LogPriority.ERROR) { \"Failed to write sync data to cache\" }"))
    }
}

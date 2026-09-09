package eu.kanade.tachiyomi.data.backup.restore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Protects the restore bookkeeping boundary: section jobs may remain concurrent, but shared
 * progress and error mutations must stay behind the serialized helpers.
 */
class BackupRestorerBookkeepingSourceTest {

    private val source = File("src/main/java/eu/kanade/tachiyomi/data/backup/restore/BackupRestorer.kt")
        .readText()

    @Test
    fun `shared restore bookkeeping uses a mutex`() {
        assertTrue(source.contains("private val bookkeepingMutex = Mutex()"))
        assertTrue(source.contains("bookkeepingMutex.withLock"))
    }

    @Test
    fun `direct progress and error mutations exist only in bookkeeping helpers`() {
        assertEquals(1, Regex("restoreProgress \\+= 1").findAll(source).count())
        assertEquals(1, Regex("errors\\.add\\(").findAll(source).count())
        assertTrue(source.contains("recordProgress("))
        assertTrue(source.contains("recordError("))
    }
}

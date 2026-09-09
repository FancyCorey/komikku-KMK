package eu.kanade.tachiyomi.data.updater

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class AppUpdateBroadcastSourceTest {
    @Test
    fun `package replacement notification is outside receiver main thread`() {
        val source = File("src/main/java/eu/kanade/tachiyomi/data/updater/AppUpdateBroadcast.kt").readText()
        val replacementBlock = source.substringAfter("Intent.ACTION_MY_PACKAGE_REPLACED")

        assertTrue(replacementBlock.contains("goAsync()"))
        assertTrue(replacementBlock.contains("Dispatchers.Default"))
        assertTrue(replacementBlock.contains("pendingResult.finish()"))
        assertTrue(replacementBlock.contains("return"))
    }
}

package eu.kanade.tachiyomi.extension.util

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ExtensionInstallServiceSourceTest {

    @Test
    fun `service explicitly removes foreground state during destruction`() {
        val source = File(
            "src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionInstallService.kt",
        ).readText()

        assertTrue(source.contains("stopForeground(STOP_FOREGROUND_REMOVE)"))
    }
}

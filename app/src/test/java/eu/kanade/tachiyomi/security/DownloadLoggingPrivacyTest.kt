package eu.kanade.tachiyomi.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class DownloadLoggingPrivacyTest {
    @Test
    fun `download diagnostics do not expose paths titles names or throwable payloads`() {
        val provider = File(
            "src/main/java/eu/kanade/tachiyomi/data/download/DownloadProvider.kt",
        ).readText()
        val manager = File(
            "src/main/java/eu/kanade/tachiyomi/data/download/DownloadManager.kt",
        ).readText()

        listOf(
            "Failed to create source download directory: ${'$'}displayablePath",
            "Failed to create manga download directory: ${'$'}displayablePath",
        ).forEach { fragment ->
            assertFalse(provider.contains(fragment), "Sensitive provider fragment remains: $fragment")
        }

        listOf(
            "Cache and download folder doesn't match for",
            "${'$'}{oldFolder.name}",
            "${'$'}{oldNames.joinToString()}",
            "logcat(LogPriority.ERROR, e)",
        ).forEach { fragment ->
            assertFalse(manager.contains(fragment), "Sensitive manager fragment remains: $fragment")
        }

        listOf(
            "Source download directory creation failed",
            "Manga download directory creation failed",
        ).forEach { marker ->
            assertTrue(provider.contains(marker), "Expected provider marker is missing: $marker")
        }

        listOf(
            "Manga download folder lookup failed",
            "Download cache and folder mismatch",
            "Source download folder rename failed",
            "Manga download folder rename failed",
            "Downloaded chapter rename failed",
        ).forEach { marker ->
            assertTrue(manager.contains(marker), "Expected manager marker is missing: $marker")
        }
    }
}

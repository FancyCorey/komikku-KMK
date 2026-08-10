package eu.kanade.tachiyomi.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ReaderLoggingPrivacyTest {
    @Test
    fun `reader diagnostics do not expose throwable or exception-message payloads`() {
        val source = File(
            "src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt",
        ).readText()

        listOf(
            "logcat(LogPriority.ERROR, error)",
            "logcat(LogPriority.ERROR, result.error)",
            "Error updating Discord RPC: ${'$'}{e.message}",
        ).forEach { fragment ->
            assertFalse(source.contains(fragment), "Sensitive reader diagnostic remains: $fragment")
        }

        listOf(
            "Reader initial chapter load failed",
            "Reader image save failed",
            "Reader Discord RPC update failed",
        ).forEach { marker ->
            assertTrue(source.contains(marker), "Expected reader marker is missing: $marker")
        }
    }
}

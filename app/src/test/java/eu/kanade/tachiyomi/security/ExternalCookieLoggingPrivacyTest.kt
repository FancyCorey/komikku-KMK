package eu.kanade.tachiyomi.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ExternalCookieLoggingPrivacyTest {

    @Test
    fun `extension details diagnostics do not expose urls or throwables`() {
        val source = File(
            "src/main/java/eu/kanade/tachiyomi/ui/browse/extension/details/ExtensionDetailsScreenModel.kt",
        ).readText()

        assertFalse(source.contains("logcat(LogPriority.ERROR, throwable)"))
        assertFalse(source.contains("Failed to clear cookies for \$it"))
        assertFalse(source.contains("Cleared \$cleared cookies for: \${urls.joinToString()}"))
        assertTrue(source.contains("Failed to load extension source list"))
        assertTrue(source.contains("Extension cookies cleared: \$cleared"))
    }

    @Test
    fun `discord diagnostics do not expose login exception text or callback url`() {
        val source = File("src/main/java/eu/kanade/tachiyomi/ui/setting/connections/DiscordLoginScreen.kt").readText()

        assertFalse(source.contains("logcat(LogPriority.ERROR, e)"))
        assertFalse(source.contains("Discord login error: \${e.message}"))
        assertFalse(source.contains("Cleared \$cleared cookies for: \$url"))
        assertTrue(source.contains("Discord login failed"))
        assertTrue(source.contains("Discord cookies cleared: \$cleared"))
    }
}

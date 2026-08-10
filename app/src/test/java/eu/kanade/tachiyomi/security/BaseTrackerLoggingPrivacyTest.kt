package eu.kanade.tachiyomi.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class BaseTrackerLoggingPrivacyTest {
    @Test
    fun `shared tracker update diagnostics do not expose provider identifiers or throwables`() {
        val source = File(
            "src/main/java/eu/kanade/tachiyomi/data/track/BaseTracker.kt",
        ).readText()

        assertFalse(
            source.contains("logcat(LogPriority.ERROR, e) { \"Failed to update remote track data id=${'$'}id\" }"),
        )
        assertTrue(source.contains("Remote tracker update failed"))
    }
}

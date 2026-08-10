package eu.kanade.tachiyomi.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class TrackInfoDialogLoggingPrivacyTest {
    @Test
    fun `tracker dialog diagnostics do not expose IDs or throwable payloads`() {
        val source = File(
            "src/main/java/eu/kanade/tachiyomi/ui/manga/track/TrackInfoDialog.kt",
        ).readText()

        val forbiddenFragments = listOf(
            "logcat(LogPriority.ERROR, it)",
            "logcat(LogPriority.ERROR, e)",
            "mangaId=${'$'}mangaId for service ${'$'}{track!!.id}",
        )
        forbiddenFragments.forEach { fragment ->
            assertFalse(source.contains(fragment), "Sensitive tracker-dialog fragment remains: $fragment")
        }

        listOf(
            "Tracker list loading failed",
            "Tracker metadata ID lookup failed",
            "Tracker bind-by-ID failed",
            "Tracker refresh failed",
            "Tracker registration failed",
            "Remote tracker deletion failed",
        ).forEach { marker ->
            assertTrue(source.contains(marker), "Expected tracker-dialog marker is missing: $marker")
        }
    }
}

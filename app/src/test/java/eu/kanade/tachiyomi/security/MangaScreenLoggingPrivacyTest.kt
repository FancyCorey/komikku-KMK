package eu.kanade.tachiyomi.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class MangaScreenLoggingPrivacyTest {
    @Test
    fun `manga detail diagnostics do not expose URLs IDs or throwable payloads`() {
        val source = File(
            "src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreenModel.kt",
        ).readText()

        listOf(
            "Found accepted manga %s",
            "logcat(LogPriority.ERROR, e)",
            "logcat(LogPriority.ERROR, error)",
            "logcat(LogPriority.ERROR, it)",
            "mangaId=${'$'}mangaId for service ${'$'}{track!!.id}",
        ).forEach { fragment ->
            assertFalse(source.contains(fragment), "Sensitive manga-detail fragment remains: $fragment")
        }

        listOf(
            "Accepted manga root found",
            "Accepted chapter-chain loading failed",
            "Manga remote refresh failed",
            "Manga folder opening failed",
            "Manga tracker observation failed",
            "Manga tracker state observation failed",
            "Manga exception handler received a failure",
            "Manga tracker refresh failed",
            "Manga chapter deletion failed",
            "Manga downloaded-data deletion failed",
            "Manga chapter-record deletion failed",
            "Manga tracker stream failed",
        ).forEach { marker ->
            assertTrue(source.contains(marker), "Expected manga-detail marker is missing: $marker")
        }
    }
}

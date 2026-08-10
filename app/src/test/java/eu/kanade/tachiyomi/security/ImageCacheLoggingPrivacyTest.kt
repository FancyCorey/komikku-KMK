package eu.kanade.tachiyomi.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ImageCacheLoggingPrivacyTest {
    @Test
    fun `image cache diagnostics do not expose cache identifiers or throwable payloads`() {
        val pagePreview = File(
            "src/main/java/eu/kanade/tachiyomi/data/coil/PagePreviewFetcher.kt",
        ).readText()
        val mangaCover = File(
            "src/main/java/eu/kanade/tachiyomi/data/coil/MangaCoverFetcher.kt",
        ).readText()

        listOf(
            "logcat(LogPriority.ERROR, e)",
            "page preview cache ${'$'}diskCacheKey",
            "cover cache ${'$'}{cacheFile.name}",
        ).forEach { fragment ->
            assertFalse(pagePreview.contains(fragment), "Sensitive page-preview fragment remains: $fragment")
            assertFalse(mangaCover.contains(fragment), "Sensitive manga-cover fragment remains: $fragment")
        }

        listOf(
            "Page-preview snapshot cache write failed",
            "Page-preview response cache write failed",
        ).forEach { marker ->
            assertTrue(pagePreview.contains(marker))
        }
        listOf(
            "Manga-cover snapshot cache write failed",
            "Manga-cover response cache write failed",
        ).forEach { marker ->
            assertTrue(mangaCover.contains(marker))
        }
    }
}

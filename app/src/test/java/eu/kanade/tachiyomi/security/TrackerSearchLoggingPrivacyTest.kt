package eu.kanade.tachiyomi.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class TrackerSearchLoggingPrivacyTest {
    @Test
    fun `tracker search-by-ID diagnostics do not expose input or throwable details`() {
        val myAnimeList = File(
            "src/main/java/eu/kanade/tachiyomi/data/track/myanimelist/MyAnimeList.kt",
        ).readText()
        val aniList = File(
            "src/main/java/eu/kanade/tachiyomi/data/track/anilist/Anilist.kt",
        ).readText()

        listOf(
            "Invalid ID format for searchById: ${'$'}id",
            "Error during searchById '${'$'}id': ${'$'}{e.message}",
            "xLogW(\"Error during searchById '${'$'}id': ${'$'}{e.message}\", e)",
        ).forEach { fragment ->
            assertFalse(myAnimeList.contains(fragment), "Sensitive MyAnimeList fragment remains: $fragment")
            assertFalse(aniList.contains(fragment), "Sensitive AniList fragment remains: $fragment")
        }

        assertTrue(myAnimeList.contains("MyAnimeList search-by-ID input was invalid"))
        assertTrue(myAnimeList.contains("MyAnimeList search-by-ID failed"))
        assertTrue(aniList.contains("AniList search-by-ID failed"))
    }
}

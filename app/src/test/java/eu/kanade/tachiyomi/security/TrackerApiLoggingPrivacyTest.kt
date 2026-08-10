package eu.kanade.tachiyomi.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class TrackerApiLoggingPrivacyTest {
    @Test
    fun `tracker API diagnostics do not expose URLs IDs or throwable payloads`() {
        val sources = mapOf(
            "KavitaApi.kt" to File(
                "src/main/java/eu/kanade/tachiyomi/data/track/kavita/KavitaApi.kt",
            ).readText(),
            "KomgaApi.kt" to File(
                "src/main/java/eu/kanade/tachiyomi/data/track/komga/KomgaApi.kt",
            ).readText(),
            "MangaUpdates.kt" to File(
                "src/main/java/eu/kanade/tachiyomi/data/track/mangaupdates/MangaUpdates.kt",
            ).readText(),
        )

        val forbiddenFragments = listOf(
            "API URL: ${'$'}apiUrl",
            "URL '${'$'}apiUrl'",
            "Request:${'$'}requestUrl",
            "itemRequest: ${'$'}requestUrl",
            "Could not get item: ${'$'}url",
            "Error during searchById '${'$'}id': ${'$'}{e.message}",
            "xLogW(\"Error during searchById '${'$'}id': ${'$'}{e.message}\", e)",
        )
        sources.forEach { (name, source) ->
            forbiddenFragments.forEach { fragment ->
                assertFalse(source.contains(fragment), "Sensitive tracker fragment remains in $name: $fragment")
            }
        }

        listOf(
            "Kavita token request returned a server error",
            "Kavita token request timed out",
            "Kavita chapter-count request failed",
            "Kavita latest-chapter request failed",
            "Kavita tracking lookup failed",
        ).forEach { marker ->
            assertTrue(sources.getValue("KavitaApi.kt").contains(marker))
        }
        assertTrue(sources.getValue("KomgaApi.kt").contains("Komga tracking lookup failed"))
        assertTrue(sources.getValue("MangaUpdates.kt").contains("MangaUpdates search-by-ID failed"))
    }
}

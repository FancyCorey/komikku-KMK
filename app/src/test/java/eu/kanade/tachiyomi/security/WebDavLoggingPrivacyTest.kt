package eu.kanade.tachiyomi.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class WebDavLoggingPrivacyTest {
    @Test
    fun `WebDAV diagnostics do not expose URLs ETags response bodies or throwables`() {
        val source = File(
            "src/main/java/eu/kanade/tachiyomi/data/sync/service/WebDavSyncService.kt",
        ).readText()

        val forbiddenFragments = listOf(
            "Failed to create folder: ${'$'}currentPath",
            "ETag(%s)",
            "xLogE(\"WebDAV sync error:\", e)",
            "xLogE(\"Invalid backup format:\", e)",
            "Failed to download: ${'$'}body",
            "Upload failed: ${'$'}bodyStr",
        )
        forbiddenFragments.forEach { fragment ->
            assertFalse(source.contains(fragment), "Sensitive WebDAV diagnostic fragment remains: $fragment")
        }

        listOf(
            "WebDAV sync failed",
            "WebDAV remote data available for merge",
            "WebDAV remote data unavailable; using local data",
            "Failed to download WebDAV sync data (HTTP ",
            "Failed to upload WebDAV sync data (HTTP ",
            "WebDAV sync data format invalid",
        ).forEach { marker ->
            assertTrue(source.contains(marker), "Expected generic WebDAV marker is missing: $marker")
        }
    }
}

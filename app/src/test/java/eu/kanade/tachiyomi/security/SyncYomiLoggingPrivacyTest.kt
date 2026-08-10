package eu.kanade.tachiyomi.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class SyncYomiLoggingPrivacyTest {
    @Test
    fun `SyncYomi diagnostics do not expose host ETag response bodies or exception messages`() {
        val source = File(
            "src/main/java/eu/kanade/tachiyomi/data/sync/service/SyncYomiSyncService.kt",
        ).readText()

        val forbiddenFragments = listOf(
            "ETag(${ '$' }etag)",
            "SyncError: ${ '$' }responseBody",
            "Failed to download sync data: ${ '$' }responseBody",
            "Failed to upload sync data: ${ '$' }responseBody",
            "Error syncing: ${ '$' }{e.message}",
            "Failed to report sync event: ${ '$' }{e.message}",
        )
        forbiddenFragments.forEach { fragment ->
            assertFalse(source.contains(fragment), "Sensitive SyncYomi log fragment remains: $fragment")
        }

        listOf(
            "SyncYomi remote data available for merge",
            "SyncYomi remote data unavailable; using local data",
            "SyncYomi download failed: HTTP ",
            "SyncYomi upload failed: HTTP ",
            "SyncYomi event reporting failed",
        ).forEach { marker ->
            assertTrue(source.contains(marker), "Expected generic SyncYomi marker is missing: $marker")
        }
    }
}

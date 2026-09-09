package eu.kanade.tachiyomi.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class GoogleDriveSyncLoggingPrivacyTest {
    @Test
    fun `Google Drive diagnostics do not expose identifiers metadata or throwable payloads`() {
        val source = File(
            "src/main/java/eu/kanade/tachiyomi/data/sync/service/GoogleDriveSyncService.kt",
        ).readText()
        val activitySource = File(
            "src/main/java/eu/kanade/tachiyomi/ui/setting/track/GoogleDriveLoginActivity.kt",
        ).readText()

        val forbiddenFragments = listOf(
            "Local device ID:",
            "Last sync device ID:",
            "Google Drive File ID:",
            "AppData folder file list: ${'$'}fileList",
            "with file ID: ${'$'}fileId",
            "with file ID: ${'$'}{uploadedFile.id}",
            "throwable = e",
            "Error syncing: ${'$'}{e.message}",
            "Failed to refresh access token ${'$'}{e.message}",
            "onFailure(e.localizedMessage",
        )
        forbiddenFragments.forEach { fragment ->
            assertFalse(source.contains(fragment), "Sensitive Google Drive log fragment remains: $fragment")
        }

        listOf(
            "Compared local and remote sync device state",
            "Google Drive sync file located",
            "Google Drive app-data sync file count=",
            "Google Drive sync download failed",
            "Google Drive access-token refresh failed",
        ).forEach { marker ->
            assertTrue(source.contains(marker), "Expected generic Google Drive marker is missing: $marker")
        }

        assertFalse(
            activitySource.contains("providerError ?: stringResource"),
            "Provider callback error must not be shown directly to the user",
        )
        assertTrue(
            activitySource.contains("SYMR.strings.google_drive_not_signed_in"),
            "Callback failure must use the stable localized fallback",
        )
    }
}

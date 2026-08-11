package eu.kanade.tachiyomi.data.updater

import eu.kanade.tachiyomi.BuildConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppUpdateCheckerRepositoryTest {

    @Test
    fun `stable and preview checks stay inside the KMK fork`() {
        assertEquals("FancyCorey/komikku-KMK", getGithubRepo(peekIntoPreview = false))
        assertEquals("FancyCorey/komikku-KMK", getGithubRepo(peekIntoPreview = true))
    }

    @Test
    fun `development build is isolated from public update and Drive channels`() {
        assertTrue(BuildConfig.APPLICATION_ID.endsWith(".dev"))
        assertFalse(BuildConfig.UPDATER_ENABLED)
        assertFalse(BuildConfig.GOOGLE_DRIVE_SYNC_ENABLED)
    }
}

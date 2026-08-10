package eu.kanade.tachiyomi.data.updater

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppUpdateCheckerRepositoryTest {

    @Test
    fun `stable and preview checks stay inside the KMK fork`() {
        assertEquals("FancyCorey/komikku-KMK", getGithubRepo(peekIntoPreview = false))
        assertEquals("FancyCorey/komikku-KMK", getGithubRepo(peekIntoPreview = true))
    }
}

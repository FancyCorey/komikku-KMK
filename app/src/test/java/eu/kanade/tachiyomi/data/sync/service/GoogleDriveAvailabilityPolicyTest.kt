package eu.kanade.tachiyomi.data.sync.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GoogleDriveAvailabilityPolicyTest {
    @Test
    fun `missing client configuration skips background sync`() {
        assertTrue(GoogleDriveAvailabilityPolicy.shouldSkipBackgroundSync(clientSecretsAvailable = false))
    }

    @Test
    fun `available client configuration keeps background sync enabled`() {
        assertFalse(GoogleDriveAvailabilityPolicy.shouldSkipBackgroundSync(clientSecretsAvailable = true))
    }

    @Test
    fun `only available configuration keeps background sync enabled`() {
        assertFalse(
            GoogleDriveAvailabilityPolicy.shouldSkipBackgroundSync(
                GoogleDriveClientConfigurationState.AVAILABLE,
            ),
        )
        assertTrue(
            GoogleDriveAvailabilityPolicy.shouldSkipBackgroundSync(
                GoogleDriveClientConfigurationState.CLIENT_ID_MISMATCH,
            ),
        )
    }
}

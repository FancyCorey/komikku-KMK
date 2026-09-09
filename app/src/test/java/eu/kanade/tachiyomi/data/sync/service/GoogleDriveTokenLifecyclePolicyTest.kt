package eu.kanade.tachiyomi.data.sync.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GoogleDriveTokenLifecyclePolicyTest {
    @Test
    fun `missing configuration fails closed before refresh`() {
        assertEquals(
            GoogleDriveTokenLifecycleState.MISSING_CONFIGURATION,
            GoogleDriveTokenLifecyclePolicy.initialState(
                configurationAvailable = false,
                refreshTokenPresent = true,
            ),
        )
    }

    @Test
    fun `missing refresh token is signed out without a network attempt`() {
        assertEquals(
            GoogleDriveTokenLifecycleState.MISSING_REFRESH_TOKEN,
            GoogleDriveTokenLifecyclePolicy.initialState(
                configurationAvailable = true,
                refreshTokenPresent = false,
            ),
        )
    }

    @Test
    fun `complete credentials are ready for refresh`() {
        assertEquals(
            GoogleDriveTokenLifecycleState.READY,
            GoogleDriveTokenLifecyclePolicy.initialState(
                configurationAvailable = true,
                refreshTokenPresent = true,
            ),
        )
    }

    @Test
    fun `cancellation remains distinct from a retryable failure`() {
        assertEquals(
            GoogleDriveTokenLifecycleState.CANCELED,
            GoogleDriveTokenLifecyclePolicy.failureState(canceled = true),
        )
        assertEquals(
            GoogleDriveTokenLifecycleState.TRANSIENT_FAILURE,
            GoogleDriveTokenLifecyclePolicy.failureState(),
        )
    }

    @Test
    fun `revocation is the only failure that clears stored credentials`() {
        assertTrue(
            GoogleDriveTokenLifecyclePolicy.shouldClearStoredCredentials(
                GoogleDriveTokenLifecycleState.AUTHORIZATION_REVOKED,
            ),
        )
        assertFalse(
            GoogleDriveTokenLifecyclePolicy.shouldClearStoredCredentials(
                GoogleDriveTokenLifecycleState.TRANSIENT_FAILURE,
            ),
        )
        assertFalse(
            GoogleDriveTokenLifecyclePolicy.shouldClearStoredCredentials(
                GoogleDriveTokenLifecycleState.MALFORMED_RESPONSE,
            ),
        )
    }
}

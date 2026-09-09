package eu.kanade.tachiyomi.data.sync.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class GoogleDriveTokenRefreshCoordinatorTest {
    @Test
    fun `successful refresh saves the new access token`() = runBlocking {
        val store = TestTokenStore(refreshToken = "refresh-sentinel")

        val state = GoogleDriveTokenRefreshCoordinator.refresh(
            configurationAvailable = true,
            tokenStore = store,
            tokenRefresher = GoogleDriveTokenRefresher { refreshToken ->
                assertEquals("refresh-sentinel", refreshToken)
                GoogleDriveRefreshResult.Success("access-sentinel")
            },
        )

        assertEquals(GoogleDriveTokenLifecycleState.READY, state)
        assertEquals("access-sentinel", store.savedAccessToken)
        assertTrue(!store.cleared)
    }

    @Test
    fun `missing configuration and refresh token do not call the client`() = runBlocking {
        val missingConfiguration = TestTokenStore(refreshToken = "refresh-sentinel")
        var calls = 0
        val client = GoogleDriveTokenRefresher {
            calls += 1
            GoogleDriveRefreshResult.Success("unexpected")
        }

        assertEquals(
            GoogleDriveTokenLifecycleState.MISSING_CONFIGURATION,
            GoogleDriveTokenRefreshCoordinator.refresh(false, missingConfiguration, client),
        )
        val missingRefresh = TestTokenStore(refreshToken = "")
        assertEquals(
            GoogleDriveTokenLifecycleState.MISSING_REFRESH_TOKEN,
            GoogleDriveTokenRefreshCoordinator.refresh(true, missingRefresh, client),
        )
        assertEquals(0, calls)
    }

    @Test
    fun `revocation clears both stored credentials`() = runBlocking {
        val store = TestTokenStore(refreshToken = "refresh-sentinel")

        val state = GoogleDriveTokenRefreshCoordinator.refresh(
            configurationAvailable = true,
            tokenStore = store,
            tokenRefresher = GoogleDriveTokenRefresher {
                GoogleDriveRefreshResult.AuthorizationRevoked
            },
        )

        assertEquals(GoogleDriveTokenLifecycleState.AUTHORIZATION_REVOKED, state)
        assertTrue(store.cleared)
    }

    @Test
    fun `transient and malformed failures do not mutate credentials`() = runBlocking {
        val transientStore = TestTokenStore(refreshToken = "refresh-sentinel")
        val transientState = GoogleDriveTokenRefreshCoordinator.refresh(
            configurationAvailable = true,
            tokenStore = transientStore,
            tokenRefresher = GoogleDriveTokenRefresher {
                GoogleDriveRefreshResult.TransientFailure
            },
        )
        assertEquals(GoogleDriveTokenLifecycleState.TRANSIENT_FAILURE, transientState)
        assertEquals(null, transientStore.savedAccessToken)
        assertTrue(!transientStore.cleared)

        val malformedStore = TestTokenStore(refreshToken = "refresh-sentinel")
        val malformedState = GoogleDriveTokenRefreshCoordinator.refresh(
            configurationAvailable = true,
            tokenStore = malformedStore,
            tokenRefresher = GoogleDriveTokenRefresher {
                GoogleDriveRefreshResult.Success("")
            },
        )
        assertEquals(GoogleDriveTokenLifecycleState.MALFORMED_RESPONSE, malformedState)
        assertEquals(null, malformedStore.savedAccessToken)
        assertTrue(!malformedStore.cleared)
    }

    @Test
    fun `io failure is transient and cancellation propagates`() = runBlocking {
        val ioStore = TestTokenStore(refreshToken = "refresh-sentinel")
        assertEquals(
            GoogleDriveTokenLifecycleState.TRANSIENT_FAILURE,
            GoogleDriveTokenRefreshCoordinator.refresh(
                configurationAvailable = true,
                tokenStore = ioStore,
                tokenRefresher = GoogleDriveTokenRefresher { throw IOException("network") },
            ),
        )

        val cancellation = CancellationException("cancelled")
        assertThrows(CancellationException::class.java) {
            runBlocking {
                GoogleDriveTokenRefreshCoordinator.refresh(
                    configurationAvailable = true,
                    tokenStore = TestTokenStore(refreshToken = "refresh-sentinel"),
                    tokenRefresher = GoogleDriveTokenRefresher { throw cancellation },
                )
            }
        }
    }

    private class TestTokenStore(private val refreshToken: String) : GoogleDriveTokenStore {
        var savedAccessToken: String? = null
        var cleared = false

        override fun refreshToken(): String = refreshToken

        override fun saveAccessToken(accessToken: String) {
            savedAccessToken = accessToken
        }

        override fun clearCredentials() {
            cleared = true
        }
    }
}

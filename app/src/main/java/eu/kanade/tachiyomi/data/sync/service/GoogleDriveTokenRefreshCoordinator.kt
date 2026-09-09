package eu.kanade.tachiyomi.data.sync.service

import kotlinx.coroutines.CancellationException
import java.io.IOException

/** In-memory boundary used to exercise the existing Google Drive refresh contract on the host. */
internal interface GoogleDriveTokenStore {
    fun refreshToken(): String

    fun saveAccessToken(accessToken: String)

    fun clearCredentials()
}

internal sealed interface GoogleDriveRefreshResult {
    data class Success(val accessToken: String) : GoogleDriveRefreshResult

    data object AuthorizationRevoked : GoogleDriveRefreshResult

    data object MalformedResponse : GoogleDriveRefreshResult

    data object TransientFailure : GoogleDriveRefreshResult
}

internal fun interface GoogleDriveTokenRefresher {
    fun refresh(refreshToken: String): GoogleDriveRefreshResult
}

internal object GoogleDriveTokenRefreshCoordinator {
    suspend fun refresh(
        configurationAvailable: Boolean,
        tokenStore: GoogleDriveTokenStore,
        tokenRefresher: GoogleDriveTokenRefresher,
    ): GoogleDriveTokenLifecycleState {
        val refreshToken = tokenStore.refreshToken()
        val initialState = GoogleDriveTokenLifecyclePolicy.initialState(
            configurationAvailable = configurationAvailable,
            refreshTokenPresent = refreshToken.isNotBlank(),
        )
        if (initialState != GoogleDriveTokenLifecycleState.READY) {
            return initialState
        }

        return try {
            when (val result = tokenRefresher.refresh(refreshToken)) {
                is GoogleDriveRefreshResult.Success -> {
                    if (result.accessToken.isBlank()) {
                        GoogleDriveTokenLifecycleState.MALFORMED_RESPONSE
                    } else {
                        tokenStore.saveAccessToken(result.accessToken)
                        GoogleDriveTokenLifecycleState.READY
                    }
                }

                GoogleDriveRefreshResult.AuthorizationRevoked -> {
                    tokenStore.clearCredentials()
                    GoogleDriveTokenLifecycleState.AUTHORIZATION_REVOKED
                }

                GoogleDriveRefreshResult.MalformedResponse ->
                    GoogleDriveTokenLifecycleState.MALFORMED_RESPONSE

                GoogleDriveRefreshResult.TransientFailure ->
                    GoogleDriveTokenLifecycleState.TRANSIENT_FAILURE
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            GoogleDriveTokenLifecycleState.TRANSIENT_FAILURE
        } catch (e: Exception) {
            GoogleDriveTokenLifecycleState.MALFORMED_RESPONSE
        }
    }
}

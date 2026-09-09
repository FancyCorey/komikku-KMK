package eu.kanade.tachiyomi.data.sync.service

/** Classifies token lifecycle outcomes without carrying tokens or provider payloads. */
internal enum class GoogleDriveTokenLifecycleState {
    READY,
    MISSING_CONFIGURATION,
    MISSING_REFRESH_TOKEN,
    AUTHORIZATION_REVOKED,
    TRANSIENT_FAILURE,
    MALFORMED_RESPONSE,
    CANCELED,
}

internal object GoogleDriveTokenLifecyclePolicy {
    fun initialState(
        configurationAvailable: Boolean,
        refreshTokenPresent: Boolean,
    ): GoogleDriveTokenLifecycleState = when {
        !configurationAvailable -> GoogleDriveTokenLifecycleState.MISSING_CONFIGURATION
        !refreshTokenPresent -> GoogleDriveTokenLifecycleState.MISSING_REFRESH_TOKEN
        else -> GoogleDriveTokenLifecycleState.READY
    }

    fun failureState(
        canceled: Boolean = false,
        authorizationRevoked: Boolean = false,
        malformedResponse: Boolean = false,
    ): GoogleDriveTokenLifecycleState = when {
        canceled -> GoogleDriveTokenLifecycleState.CANCELED
        authorizationRevoked -> GoogleDriveTokenLifecycleState.AUTHORIZATION_REVOKED
        malformedResponse -> GoogleDriveTokenLifecycleState.MALFORMED_RESPONSE
        else -> GoogleDriveTokenLifecycleState.TRANSIENT_FAILURE
    }

    fun shouldClearStoredCredentials(state: GoogleDriveTokenLifecycleState): Boolean =
        state == GoogleDriveTokenLifecycleState.AUTHORIZATION_REVOKED
}

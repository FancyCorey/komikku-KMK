package eu.kanade.tachiyomi.data.sync.service

internal enum class GoogleDriveClientConfigurationState {
    AVAILABLE,
    MISSING_ASSET,
    MALFORMED_ASSET,
    CLIENT_ID_NOT_CONFIGURED,
    CLIENT_ID_MISMATCH,
}

internal object GoogleDriveClientConfigurationPolicy {
    fun classify(
        clientSecretsAvailable: Boolean,
        parsedClientId: String?,
        expectedClientId: String,
    ): GoogleDriveClientConfigurationState {
        if (!clientSecretsAvailable) {
            return GoogleDriveClientConfigurationState.MISSING_ASSET
        }
        if (parsedClientId.isNullOrBlank()) {
            return GoogleDriveClientConfigurationState.MALFORMED_ASSET
        }
        if (expectedClientId.isBlank()) {
            return GoogleDriveClientConfigurationState.CLIENT_ID_NOT_CONFIGURED
        }
        if (parsedClientId != expectedClientId) {
            return GoogleDriveClientConfigurationState.CLIENT_ID_MISMATCH
        }
        return GoogleDriveClientConfigurationState.AVAILABLE
    }
}

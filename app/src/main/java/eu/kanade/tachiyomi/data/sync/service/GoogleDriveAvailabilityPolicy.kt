package eu.kanade.tachiyomi.data.sync.service

/** Keeps a missing build-time Google client asset from becoming a repeated background exception. */
internal object GoogleDriveAvailabilityPolicy {
    fun shouldSkipBackgroundSync(
        configurationState: GoogleDriveClientConfigurationState,
    ): Boolean = configurationState != GoogleDriveClientConfigurationState.AVAILABLE

    fun shouldSkipBackgroundSync(clientSecretsAvailable: Boolean): Boolean = !clientSecretsAvailable
}

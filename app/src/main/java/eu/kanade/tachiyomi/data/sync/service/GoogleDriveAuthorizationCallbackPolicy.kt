package eu.kanade.tachiyomi.data.sync.service

/** Keeps the existing Komikku callback contract explicit before token exchange. */
internal enum class GoogleDriveAuthorizationCallbackState {
    SUCCESS,
    PROVIDER_ERROR,
    NO_RESULT,
    UNSUPPORTED_ROUTE,
    MALFORMED,
}

internal object GoogleDriveAuthorizationCallbackPolicy {
    const val REDIRECT_SCHEME = "eu.kanade.google.oauth"
    const val REDIRECT_PATH = "/oauth2redirect"

    fun classify(
        scheme: String?,
        host: String?,
        path: String?,
        codePresent: Boolean,
        errorPresent: Boolean,
    ): GoogleDriveAuthorizationCallbackState {
        if (scheme == null && host == null && path == null && !codePresent && !errorPresent) {
            return GoogleDriveAuthorizationCallbackState.NO_RESULT
        }

        if (scheme != REDIRECT_SCHEME || !host.isNullOrEmpty() || path != REDIRECT_PATH) {
            return GoogleDriveAuthorizationCallbackState.UNSUPPORTED_ROUTE
        }

        return when {
            codePresent && !errorPresent -> GoogleDriveAuthorizationCallbackState.SUCCESS
            errorPresent && !codePresent -> GoogleDriveAuthorizationCallbackState.PROVIDER_ERROR
            else -> GoogleDriveAuthorizationCallbackState.MALFORMED
        }
    }
}

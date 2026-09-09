package eu.kanade.tachiyomi.data.sync.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GoogleDriveAuthorizationCallbackPolicyTest {
    @Test
    fun `accepts the existing Komikku success callback`() {
        assertEquals(
            GoogleDriveAuthorizationCallbackState.SUCCESS,
            GoogleDriveAuthorizationCallbackPolicy.classify(
                scheme = "eu.kanade.google.oauth",
                host = "",
                path = "/oauth2redirect",
                codePresent = true,
                errorPresent = false,
            ),
        )
    }

    @Test
    fun `classifies provider denial without exposing callback values`() {
        assertEquals(
            GoogleDriveAuthorizationCallbackState.PROVIDER_ERROR,
            GoogleDriveAuthorizationCallbackPolicy.classify(
                scheme = "eu.kanade.google.oauth",
                host = "",
                path = "/oauth2redirect",
                codePresent = false,
                errorPresent = true,
            ),
        )
    }

    @Test
    fun `rejects an unsupported route before token exchange`() {
        assertEquals(
            GoogleDriveAuthorizationCallbackState.UNSUPPORTED_ROUTE,
            GoogleDriveAuthorizationCallbackPolicy.classify(
                scheme = "https",
                host = "example.invalid",
                path = "/oauth2redirect",
                codePresent = true,
                errorPresent = false,
            ),
        )
    }

    @Test
    fun `rejects a callback with both code and error`() {
        assertEquals(
            GoogleDriveAuthorizationCallbackState.MALFORMED,
            GoogleDriveAuthorizationCallbackPolicy.classify(
                scheme = "eu.kanade.google.oauth",
                host = "",
                path = "/oauth2redirect",
                codePresent = true,
                errorPresent = true,
            ),
        )
    }

    @Test
    fun `rejects a callback with neither code nor error`() {
        assertEquals(
            GoogleDriveAuthorizationCallbackState.MALFORMED,
            GoogleDriveAuthorizationCallbackPolicy.classify(
                scheme = "eu.kanade.google.oauth",
                host = "",
                path = "/oauth2redirect",
                codePresent = false,
                errorPresent = false,
            ),
        )
    }

    @Test
    fun `classifies absent callback data as no result`() {
        assertEquals(
            GoogleDriveAuthorizationCallbackState.NO_RESULT,
            GoogleDriveAuthorizationCallbackPolicy.classify(
                scheme = null,
                host = null,
                path = null,
                codePresent = false,
                errorPresent = false,
            ),
        )
    }
}

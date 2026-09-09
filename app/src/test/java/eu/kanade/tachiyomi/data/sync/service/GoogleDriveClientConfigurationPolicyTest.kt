package eu.kanade.tachiyomi.data.sync.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GoogleDriveClientConfigurationPolicyTest {
    @Test
    fun `missing asset is unavailable`() {
        assertEquals(
            GoogleDriveClientConfigurationState.MISSING_ASSET,
            GoogleDriveClientConfigurationPolicy.classify(
                clientSecretsAvailable = false,
                parsedClientId = null,
                expectedClientId = "client-id",
            ),
        )
    }

    @Test
    fun `missing client id is malformed`() {
        assertEquals(
            GoogleDriveClientConfigurationState.MALFORMED_ASSET,
            GoogleDriveClientConfigurationPolicy.classify(
                clientSecretsAvailable = true,
                parsedClientId = null,
                expectedClientId = "client-id",
            ),
        )
    }

    @Test
    fun `missing expected id is not configured`() {
        assertEquals(
            GoogleDriveClientConfigurationState.CLIENT_ID_NOT_CONFIGURED,
            GoogleDriveClientConfigurationPolicy.classify(
                clientSecretsAvailable = true,
                parsedClientId = "client-id",
                expectedClientId = "",
            ),
        )
    }

    @Test
    fun `different ids are rejected`() {
        assertEquals(
            GoogleDriveClientConfigurationState.CLIENT_ID_MISMATCH,
            GoogleDriveClientConfigurationPolicy.classify(
                clientSecretsAvailable = true,
                parsedClientId = "other-client-id",
                expectedClientId = "client-id",
            ),
        )
    }

    @Test
    fun `matching ids are available`() {
        assertEquals(
            GoogleDriveClientConfigurationState.AVAILABLE,
            GoogleDriveClientConfigurationPolicy.classify(
                clientSecretsAvailable = true,
                parsedClientId = "client-id",
                expectedClientId = "client-id",
            ),
        )
    }
}

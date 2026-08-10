package eu.kanade.tachiyomi.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class DiscordRpcLoggingPrivacyTest {
    @Test
    fun `Discord RPC service diagnostics do not expose identifiers URLs or exception payloads`() {
        val source = File(
            "src/main/java/eu/kanade/tachiyomi/data/connections/discord/DiscordRPCService.kt",
        ).readText()

        listOf(
            "Error setting initial screen: ${'$'}{e.message}",
            "Failed to initialize Discord RPC: ${'$'}{e.message}",
            "Failed to restart Discord RPC: ${'$'}{e.message}",
            "Failed to stop Discord RPC service: ${'$'}{e.message}",
            "Failed to send restart intent: ${'$'}{e.message}",
            "Missing required data for reader activity: thumbnailUrl=",
            "Error setting reader activity: ${'$'}{e.message}",
            "Error getting Discord URI: ${'$'}{e.message}",
            "Timber.tag(TAG).e(e,",
        ).forEach { fragment ->
            assertFalse(source.contains(fragment), "Sensitive Discord RPC fragment remains: $fragment")
        }

        listOf(
            "Discord initial screen update failed",
            "Discord RPC initialization failed",
            "Discord RPC restart failed",
            "Discord RPC stop request failed",
            "Discord RPC restart request failed",
            "Missing required data for reader activity",
            "Discord reader activity update failed",
            "Discord thumbnail resolution failed",
        ).forEach { marker ->
            assertTrue(source.contains(marker), "Expected Discord RPC marker is missing: $marker")
        }
    }
}

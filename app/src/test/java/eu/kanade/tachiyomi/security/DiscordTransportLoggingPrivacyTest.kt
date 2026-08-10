package eu.kanade.tachiyomi.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class DiscordTransportLoggingPrivacyTest {
    @Test
    fun `Discord transport diagnostics do not expose payloads or exception messages`() {
        val assetSource = File(
            "src/main/java/eu/kanade/tachiyomi/data/connections/discord/RPCExternalAsset.kt",
        ).readText()
        val websocketSource = File(
            "src/main/java/eu/kanade/tachiyomi/data/connections/discord/DiscordWebsocket.kt",
        ).readText()

        listOf(
            "Discord API rate limit reached: ${'$'}{res.body.string()}",
            "Discord API error: HTTP ${'$'}{res.code} - ${'$'}{res.body.string()}",
            "Exception while fetching Discord external asset: ${'$'}{e.message}",
        ).forEach { fragment ->
            assertFalse(assetSource.contains(fragment), "Sensitive asset diagnostic remains: $fragment")
        }

        listOf(
            "Sending message: ${'$'}message",
            "Message received : ${'$'}text",
            "Server Closed : ${'$'}code ${'$'}reason",
            "Failure : ${'$'}{t.message}",
            "Timeout waiting for Discord connection - skipping activity update: ${'$'}{e.message}",
            "Error sending Discord activity: ${'$'}{e.message}",
        ).forEach { fragment ->
            assertFalse(websocketSource.contains(fragment), "Sensitive gateway diagnostic remains: $fragment")
        }

        listOf(
            "Discord external asset request rate limited",
            "Discord external asset request failed: HTTP",
            "Discord external asset request raised an exception",
        ).forEach { marker ->
            assertTrue(assetSource.contains(marker), "Expected asset marker is missing: $marker")
        }

        listOf(
            "Sending Discord presence update",
            "Discord gateway message received",
            "Discord gateway closed",
            "Discord gateway failure",
            "Discord activity update failed",
            "catch (e: CancellationException)",
            "throw e",
        ).forEach { marker ->
            assertTrue(websocketSource.contains(marker), "Expected gateway marker is missing: $marker")
        }
    }
}

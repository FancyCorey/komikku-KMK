package eu.kanade.tachiyomi.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class PackageInstallerLoggingPrivacyTest {
    @Test
    fun `package installer diagnostics do not expose intents identifiers URIs or throwables`() {
        val source = File(
            "src/main/java/eu/kanade/tachiyomi/extension/installer/PackageInstallerInstaller.kt",
        ).readText()

        listOf(
            "Fatal error for ${'$'}intent",
            "Failed to install extension ${'$'}{entry.downloadId} ${'$'}{entry.uri}",
            "xLogE(\"Failed to install extension",
        ).forEach { fragment ->
            assertFalse(source.contains(fragment), "Sensitive package-installer fragment remains: $fragment")
        }

        listOf(
            "Package installer user action sanitization failed",
            "Package installer extension installation failed",
        ).forEach { marker ->
            assertTrue(source.contains(marker), "Expected package-installer marker is missing: $marker")
        }
    }
}

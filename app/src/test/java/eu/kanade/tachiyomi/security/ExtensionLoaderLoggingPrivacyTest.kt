package eu.kanade.tachiyomi.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ExtensionLoaderLoggingPrivacyTest {
    @Test
    fun `extension loader diagnostics do not expose package names or throwable payloads`() {
        val source = File(
            "src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt",
        ).readText()

        listOf(
            "Failed to copy extension file.",
            "unsafe extension package ${'$'}pkgName",
            "blocked reload of unsafe extension package ${'$'}pkgName",
            "Extension package is not found (${'$'}pkgName)",
            "Missing versionName for extension ${'$'}extName",
            "Package ${'$'}pkgName isn't signed",
            "Extension ${'$'}pkgName isn't trusted",
            "NSFW extension ${'$'}pkgName not allowed",
            "Extension load error: ${'$'}extName (${'$'}pkgName)",
            "Extension load error: ${'$'}extName (${'$'}it)",
            "logcat(LogPriority.ERROR, e)",
        ).forEach { fragment ->
            assertFalse(source.contains(fragment), "Sensitive extension-loader fragment remains: $fragment")
        }

        listOf(
            "Extension file copy failed",
            "KMK extension safety: unsafe extension blocked",
            "KMK extension safety: unsafe extension reload blocked",
            "Extension package lookup failed",
            "Extension metadata is missing a version name",
            "Extension signature is missing",
            "Extension trust check failed",
            "NSFW extension rejected by policy",
            "Extension class-loader creation failed",
            "Extension source loading failed",
        ).forEach { marker ->
            assertTrue(source.contains(marker), "Expected extension-loader marker is missing: $marker")
        }
    }
}

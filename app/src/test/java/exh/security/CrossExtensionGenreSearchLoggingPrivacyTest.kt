package exh.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class CrossExtensionGenreSearchLoggingPrivacyTest {

    @Test
    fun `cross-extension diagnostics do not expose source names titles or throwables`() {
        val source = File("src/main/java/exh/recs/sources/CrossExtensionGenreSearchSource.kt").readText()

        val forbiddenFragments = listOf(
            "logcat(LogPriority.WARN, throwable)",
            "CrossExtensionGenreSearch[\${catalogueSource.name}]",
            "for \"\$title\"",
            "for \${smanga.title}",
        )
        forbiddenFragments.forEach { fragment ->
            assertFalse(source.contains(fragment), "Sensitive cross-extension log fragment remains: $fragment")
        }
        assertTrue(source.contains("Cross-extension filter loading failed"))
        assertTrue(source.contains("Cross-extension search results"))
        assertTrue(source.contains("Cross-extension title fallback failed"))
        assertTrue(source.contains("Cross-extension manga enrichment failed"))
    }
}

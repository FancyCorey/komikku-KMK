package eu.kanade.presentation.browse.components

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

// KMK v0.8.21-fix4 -->
/**
 * Regression coverage for the product decision that the rating/Other Versions surface is a
 * search-and-selection workflow, not an identity-rejection workflow. A Compose UI test isn't
 * available in this codebase's existing test setup, so this uses the established source-text
 * guard pattern to ensure the removed action cannot quietly return.
 */
class GlobalSearchCardRowRejectAffordanceSourceTest {

    @Test
    fun `the rating search does not expose a reject or hide affordance`() {
        val text = source("app/src/main/java/eu/kanade/presentation/browse/components/GlobalSearchCardRow.kt")

        assertFalse(text.contains("onReject"), "rating search cards must not expose a per-item reject callback")
        assertFalse(text.contains("rejectContentDescription"), "rating search cards must not expose reject semantics")
        assertFalse(text.contains("Icons.Outlined.Block"), "the reject icon must not be reintroduced")
        assertFalse(
            text.contains("Not the same manga") || text.contains("hide this pair"),
            "identity-rejection copy must not be embedded in the rating search row",
        )
    }

    private fun source(relativePath: String): String {
        val direct = Path.of(relativePath)
        val fromParent = Path.of("..").resolve(relativePath)
        val path = listOf(direct, fromParent)
            .map(Path::toAbsolutePath)
            .firstOrNull(Files::isRegularFile)
            ?: error("Unable to resolve source file: $relativePath")
        return String(Files.readAllBytes(path), Charsets.UTF_8)
    }
}
// KMK <--

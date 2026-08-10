package exh.recs.share

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.io.File

class RecommendationBundlePrivacyLoggingTest {
    @Test
    fun `import screen model logs do not expose imported content or throwables`() {
        val source = File("src/main/java/exh/recs/share/RecommendationBundleImportScreenModel.kt").readText()

        assertFalse(source.contains("logcat(LogPriority.WARN, e)"))
        assertFalse(source.contains("Failed to resolve bundle item: \${item.title}"))
        assertFalse(source.contains("NetworkToLocal failed for \${entry.bundleItem.title}"))
        assertFalse(source.contains("Extension install failed: \${ext.name}"))
    }

    @Test
    fun `library adder logs do not expose imported content or throwables`() {
        val source = File("src/main/java/exh/recs/share/RecommendationBundleLibraryAdder.kt").readText()

        assertFalse(source.contains("logcat(LogPriority.WARN, e)"))
        assertFalse(source.contains("logcat(LogPriority.ERROR, e)"))
        assertFalse(source.contains("Metadata fetch failed for imported manga: \${manga.title}"))
        assertFalse(source.contains("Failed to add \${manga.title} to library"))
    }
}

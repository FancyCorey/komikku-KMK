package exh.recs.settings

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/** Regression guard for the Compose infinite-height crash reported in the quick-access panel. */
class RecommendationQuickAccessPanelSourceTest {

    @Test
    fun `scrollable quick access panel uses a finite max height instead of fill height`() {
        val text = Files.readAllLines(
            Path.of("src/main/java/exh/recs/settings/RecommendationSettingsSharedComponents.kt"),
        ).joinToString("\n")
        val panel = text.substringAfter("internal fun <T> EdgeQuickAccessPanel(")
            .substringBefore("// KMK v0.8.19: RecommendationCollectionQuickAccessDestination")

        assertTrue(panel.contains("val maxPanelHeight"))
        assertTrue(panel.contains(".heightIn(max = maxPanelHeight)"))
        assertFalse(
            panel.contains(".fillMaxHeight(EdgeQuickAccessPanelLayoutPolicy.MAX_HEIGHT_FRACTION)"),
            "fillMaxHeight plus wrapContentHeight can leave verticalScroll unbounded",
        )
    }
}

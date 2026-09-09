package exh.recs.bestversion

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Keeps the Find Best Version settings affordance tied to the existing anchored preference. */
class BestVersionSettingsNavigationSourceTest {

    @Test
    fun `best version exposes the existing per-source result setting`() {
        val source = File(
            "src/main/java/exh/recs/bestversion/BestVersionCompareScreen.kt",
        ).readText()

        assertTrue(source.contains("best_version_settings_action"))
        assertTrue(source.contains("RecommendationDiagnosticsSettingsScreen("))
        assertTrue(source.contains("anchor = \"same_manga_results_per_source\""))
    }
}

package exh.recs.settings

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Guards the EC-04 2026-09-04 Recommendation Settings navigation-latency fix: all four destination
 * screens must share one [RecommendationsSettingsScreenModel] instance per Navigator via
 * `rememberNavigatorScreenModel`, not one independent instance per screen via `rememberScreenModel`,
 * so its eager init (source scan/filter/order, language processing, several preference-store parses)
 * does not rerun on every navigation between them.
 */
class RecommendationsSettingsScreenModelSharedInstanceSourceTest {

    private fun readSource(path: String): String {
        val file = File(path)
        assertTrue(file.exists(), "expected source file at $path")
        return file.readText()
    }

    @Test
    fun `every RecommendationsSettingsScreenModel destination screen uses the navigator-scoped constructor`() {
        val screens = listOf(
            "RecommendationSourcePrioritySettingsScreen.kt",
            "RecommendationTasteTagsSettingsScreen.kt",
            "RecommendationNonInstalledDiscoverySettingsScreen.kt",
            "RecommendationDiagnosticsSettingsScreen.kt",
        )

        screens.forEach { name ->
            val source = readSource("src/main/java/exh/recs/settings/$name")

            assertTrue(
                source.contains("import cafe.adriel.voyager.core.model.rememberNavigatorScreenModel"),
                "$name must import rememberNavigatorScreenModel",
            )
            assertTrue(
                source.contains("navigator.rememberNavigatorScreenModel { RecommendationsSettingsScreenModel() }"),
                "$name must construct RecommendationsSettingsScreenModel through the shared Navigator scope",
            )
            assertFalse(
                source.contains("rememberScreenModel { RecommendationsSettingsScreenModel() }"),
                "$name must not construct RecommendationsSettingsScreenModel with the per-screen rememberScreenModel",
            )
            assertFalse(
                source.contains("import cafe.adriel.voyager.core.model.rememberScreenModel"),
                "$name no longer needs the per-screen rememberScreenModel import",
            )
        }
    }

    @Test
    fun `RecommendationsSettingsScreenModel reactively refreshes visible sources instead of relying only on init`() {
        val source = readSource("src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt")

        // Sharing one long-lived instance across navigation only preserves state freshness if source/
        // extension changes are already observed reactively rather than read once at construction.
        assertTrue(
            source.contains("extensionManager.installedExtensionsFlow.collectLatest { refreshVisibleSources() }"),
            "shared instance must keep reactively refreshing visible sources on extension changes",
        )
    }
}

package exh.recs.settings

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class RecommendationSettingsContextNavigationSourceTest {

    @Test
    fun `settings search pushes its selected anchored destination`() {
        val search = source("src/main/java/exh/recs/settings/RecommendationSettingsSearchScreen.kt")

        assertTrue(search.contains("onItemClick = { entry ->"))
        assertTrue(search.contains("navigator.push(entry.destination)"))
        assertTrue(search.contains("destination = RecommendationSourcePrioritySettingsScreen(anchor ="))
        assertTrue(search.contains("anchor = \"latest_exploration\""))
    }

    @Test
    fun `detail destinations consume anchors through the shared scroll owner`() {
        val screens = listOf(
            "RecommendationSourcePrioritySettingsScreen.kt",
            "RecommendationTasteTagsSettingsScreen.kt",
            "RecommendationNonInstalledDiscoverySettingsScreen.kt",
            "RecommendationDiagnosticsSettingsScreen.kt",
        )

        screens.forEach { name ->
            val text = source("src/main/java/exh/recs/settings/$name")
            assertTrue(text.contains("val anchor: String? = null"), "$name must accept an anchor")
            assertTrue(text.contains("ScrollToAnchorEffect("), "$name must consume its anchor")
        }
    }

    @Test
    fun `quick access routes are closed and replace the current detail screen`() {
        val shared = source("src/main/java/exh/recs/settings/RecommendationSettingsSharedComponents.kt")
        val detail = source("src/main/java/exh/recs/settings/RecommendationSourcePrioritySettingsScreen.kt")

        assertTrue(shared.contains("enum class RecommendationSettingsQuickAccessDestination"))
        assertTrue(shared.contains("items(\n            items = RecommendationSettingsQuickAccessDestination.entries"))
        assertTrue(shared.contains("if (!selected) onNavigate(destination)"))
        assertTrue(detail.contains("onNavigate = { destination -> navigator.replace(destination.toScreen()) }"))
    }

    private fun source(path: String): String = File(path).also {
        assertTrue(it.isFile, "expected source file at $path")
    }.readText()
}

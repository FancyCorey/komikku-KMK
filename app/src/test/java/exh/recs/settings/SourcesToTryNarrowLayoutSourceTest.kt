package exh.recs.settings

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Guards wrapping for Sources To Try actions with localized, variable-length labels. */
class SourcesToTryNarrowLayoutSourceTest {

    @Test
    fun `bulk actions and install feedback use the shared wrapping layout`() {
        val route = File("src/main/java/exh/recs/settings/RecommendationNonInstalledDiscoverySettingsScreen.kt").readText()
        val bulkStart = route.indexOf("item(key = \"suggestions_bulk_install\")")
        val feedbackStart = route.indexOf("private fun SourcesToTryInstallFeedbackContent(")
        assertTrue(bulkStart >= 0, "expected the bulk-install action owner")
        assertTrue(feedbackStart > bulkStart, "expected the feedback owner after the route")

        val bulk = route.substring(bulkStart, feedbackStart)
        val feedback = route.substring(feedbackStart)
        assertTrue(bulk.contains("FlowRow("), "bulk actions must wrap on narrow layouts")
        assertTrue(feedback.contains("FlowRow("), "install feedback actions must wrap on narrow layouts")
        assertTrue(
            bulk.contains("verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall)"),
            "bulk actions must keep a readable vertical rhythm when wrapped",
        )
        assertTrue(
            feedback.contains("verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall)"),
            "feedback actions must keep a readable vertical rhythm when wrapped",
        )
    }

    @Test
    fun `suggestion card action strip uses the shared wrapping layout`() {
        val shared = File("src/main/java/exh/recs/settings/RecommendationSettingsSharedComponents.kt").readText()
        val start = shared.indexOf("internal fun SourceSuggestionItem(")
        val end = shared.indexOf("private fun TasteSuggestionRow(", start)
        assertTrue(start >= 0, "expected the shared suggestion-card owner")
        assertTrue(end > start, "expected the suggestion-card owner boundary")
        val owner = shared.substring(start, end)
        assertTrue(owner.contains("FlowRow("), "suggestion-card actions must wrap on narrow layouts")
        assertTrue(
            owner.contains("verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall)"),
            "suggestion-card actions must keep a readable vertical rhythm when wrapped",
        )
    }
}

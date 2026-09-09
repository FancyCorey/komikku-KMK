package exh.recs.settings

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Host contract for the mounted Sources To Try route; this does not claim rendered-device proof. */
class SourcesToTryRouteContractTest {

    private fun readSource(path: String): String {
        val file = File(path)
        assertTrue(file.exists(), "expected source file at $path")
        return file.readText()
    }

    @Test
    fun `mounted route keeps search sort selection bulk recovery and navigation owners`() {
        val route = readSource("src/main/java/exh/recs/settings/RecommendationNonInstalledDiscoverySettingsScreen.kt")

        listOf(
            "SourcesToTrySearchAndSort.search",
            "SourcesToTrySearchAndSort.sort",
            "screenModel.toggleSuggestionSelected(suggestion)",
            "screenModel.installSelectedSuggestions(visibleSuggestions)",
            "screenModel.cancelSuggestionInstall(suggestion)",
            "screenModel::cancelBulkSuggestionInstall",
            "screenModel::retrySuggestionInstallFailures",
            "screenModel::dismissSuggestionInstallFeedback",
            "navigateUp = navigator::pop",
            "navigator.replace(destination.toScreen())",
        ).forEach { contract ->
            assertTrue(route.contains(contract), "Sources To Try route must retain $contract")
        }
    }

    @Test
    fun `header count follows filtered results while searching`() {
        val route = readSource("src/main/java/exh/recs/settings/RecommendationNonInstalledDiscoverySettingsScreen.kt")

        assertTrue(route.contains("val displayedSuggestionCount = if (isSearching)"))
        assertTrue(route.contains("filteredSuggestions.size"))
        assertTrue(
            route.contains("count = displayedSuggestionCount, displayedSuggestionCount"),
            "Sources To Try header must use the filtered count during search",
        )
    }

    @Test
    fun `suggestion cards keep generic Evaluation Mode labels and complete overflow actions`() {
        val shared = readSource("src/main/java/exh/recs/settings/RecommendationSettingsSharedComponents.kt")

        listOf(
            "EvaluationModeFormatter.sourceLabel(suggestion.evaluationSourceKey)",
            "EvaluationModeFormatter.repoLabel(suggestion.displayRepoName)",
            "Button(onClick = onInstall, enabled = !isInstalling)",
            "TextButton(onClick = onCancelInstall)",
            "onDismiss()",
            "onLike()",
            "onDislike()",
            "onMarkQualityPoor()",
            "onMarkQualityExplicit()",
            "onClearQualityMark()",
            "Icons.Outlined.MoreVert",
        ).forEach { contract ->
            assertTrue(shared.contains(contract), "suggestion card must retain $contract")
        }
    }
}

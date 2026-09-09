package exh.recs

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class RecommendationHubOwnershipSourceTest {
    @Test
    fun `quick access keeps recommendation concepts in their named owners`() {
        val shared = source("src/main/java/exh/recs/settings/RecommendationSettingsSharedComponents.kt")
        val eval = source("src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt")
        assertTrue(shared.contains("ForYouSources"))
        assertTrue(shared.contains("TasteAndFilters"))
        assertTrue(shared.contains("SourceEvaluation"))
        assertTrue(shared.contains("SourcesToTry"))
        assertTrue(shared.contains("ManagementAndDiagnostics"))
        assertTrue(eval.contains("RecommendationSettingsQuickAccessRow"))
    }

    @Test
    fun `sources to try and diagnostics retain distinct policy owners`() {
        val model = source("src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt")
        val sources = source("src/main/java/exh/recs/settings/SourceMetadataTagDiagnostics.kt")
        assertTrue(model.contains("SourcesToTryInstallFeedback"))
        assertTrue(model.contains("suggestionInstallFeedback"))
        assertTrue(sources.contains("SourceMetadataTagDiagnosticsPolicy.latestPerSource"))
        assertTrue(sources.contains("sourceLabelFor"))
    }

    @Test
    fun `source evaluation owns eligibility and row-action paths`() {
        val screen = source("src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt")
        val model = source("src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt")
        assertTrue(screen.contains("SourceEvaluationRowActionPolicy"))
        assertTrue(screen.contains("onSearch = { navigator.push(RecommendationSettingsSearchScreen()) }"))
        assertTrue(model.contains("SourceEvaluationCandidateQueuePolicy"))
        assertTrue(model.contains("startOrContinueStaleReassessment"))
    }

    private fun source(path: String): String = File(path).also {
        assertTrue(it.isFile, "expected source file at $path")
    }.readText()
}

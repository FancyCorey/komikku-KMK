package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

// KMK -->
/**
 * Source-level integration guard for the transient per-source error path.
 *
 * The JVM test suite does not provide a Compose device harness here. This assertion keeps the
 * existing queue-state field and the user-visible summary branch coupled, while the device fixture
 * verifies the actual rendered route separately.
 */
class SourceEvaluationErrorSummaryContractTest {
    @Test
    fun `summary renders a bounded localized count for transient source errors`() {
        val screen = File("src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt").readText()
        val plurals = File("../i18n-kmk/src/commonMain/moko-resources/base/plurals.xml").readText()

        assertTrue(screen.contains("if (queueState.errorCount > 0)"))
        assertTrue(screen.contains("KMR.plurals.source_evaluation_error_count"))
        assertTrue(screen.contains("count = queueState.errorCount"))
        assertTrue(plurals.contains("name=\"source_evaluation_error_count\""))
    }

    @Test
    fun `evaluation result row keeps primary and expanded details on shared vertical rhythm`() {
        val screen = File("src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt").readText()

        assertTrue(
            screen.contains(
                """Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),""",
            ),
        )
        assertTrue(
            screen.contains(
                "Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall))",
            ),
        )
        assertTrue(
            screen.contains(
                "KMR.strings.source_evaluation_catalogue_sample_sources,",
            ) && screen.contains(
                "style = MaterialTheme.typography.bodySmall,",
            ),
        )
    }

    @Test
    fun `past evaluations use the bounded progressive reveal policy`() {
        val screen = File("src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt").readText()

        assertTrue(screen.contains("TasteSuggestionVisibilityPolicy.DEFAULT_VISIBLE"))
        assertTrue(screen.contains("items = visiblePastEvaluations"))
        assertTrue(screen.contains("KMR.plurals.taste_settings_tag_group_show_n_more"))
        assertTrue(screen.contains("KMR.strings.taste_settings_tag_group_show_fewer"))
        assertTrue(!screen.contains("TasteSuggestionVisibilityPolicy.canShowAll(sortedEvaluations.size"))
        assertTrue(!screen.contains("pastEvaluationsVisibleCount = sortedEvaluations.size"))
    }

    @Test
    fun `management and past evaluations use the shared section header hierarchy`() {
        val screen = File("src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt").readText()

        assertTrue(screen.contains("SectionHeader(stringResource(KMR.strings.source_evaluation_management_title))"))
        assertTrue(screen.contains("screenModel::toggleManagementSection"))
        assertTrue(screen.contains("SectionHeader(stringResource(KMR.strings.source_evaluation_past_results))"))
        assertTrue(screen.contains("screenModel::requestClearAllEvaluations"))
    }

    @Test
    fun `evaluation actions use button semantics and phases use localized labels`() {
        val screen = File("src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt").readText()
        val strings = File("../i18n-kmk/src/commonMain/moko-resources/base/strings.xml").readText()

        assertTrue(screen.contains("onClickLabel = label"))
        assertTrue(screen.contains("role = Role.Button"))
        assertTrue(screen.contains("sourceEvaluationPhaseLabel(queueState.currentPhase)"))
        assertTrue(!screen.contains("queueState.currentPhase.name"))
        assertTrue(strings.contains("name=\"source_evaluation_phase_loading_sources\""))
        assertTrue(strings.contains("name=\"source_evaluation_phase_cleanup\""))
    }

    @Test
    fun `For You refreshes once after returning from a nested recommendation screen`() {
        val tab = File("src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt").readText()

        assertTrue(tab.contains("DisposableEffect(navigator.lastEvent)"))
        assertTrue(tab.contains("navigator.lastEvent == StackEvent.Push"))
        assertTrue(tab.contains("navigator.lastEvent == StackEvent.Idle && nestedRecommendationScreenPushed"))
        assertTrue(tab.contains("nestedRecommendationScreenPushed = false"))
        assertTrue(tab.contains("screenModel.refresh()"))
    }
}
// KMK <--

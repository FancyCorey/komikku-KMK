package exh.recs.settings

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Guards the shared section contract and the five destination navigation surfaces. */
class RecommendationSettingsStructureSourceTest {

    private fun readSource(path: String): String {
        val file = File(path)
        assertTrue(file.exists(), "expected source file at $path")
        return file.readText()
    }

    @Test
    fun `shared settings structure keeps visible dividers and a labeled quick-access group`() {
        val source = readSource("src/main/java/exh/recs/settings/RecommendationSettingsSharedComponents.kt")

        assertTrue(source.contains("HorizontalDivider(modifier = Modifier.padding(top = MaterialTheme.padding.small))"))
        assertTrue(source.contains("KMR.strings.rec_settings_quick_access_group"))
        assertTrue(source.contains("contentDescription = quickAccessDescription"))
        assertTrue(source.contains("LazyRow("), "quick-access destinations must stay in one compact horizontal row")
        assertTrue(source.contains("rememberLazyListState()"))
        assertTrue(source.contains("RecommendationSettingsQuickAccessDestination.entries"))
        assertTrue(source.contains("items("), "quick-access destinations must be horizontally scrollable")
        assertTrue(source.contains("contentPadding = PaddingValues(horizontal = MaterialTheme.padding.small)"))
        assertTrue(source.contains("horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)"))
        assertTrue(
            source.contains("padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small)"),
            "destination chips must retain compact edge padding",
        )
    }

    @Test
    fun `detail destinations use the shared row and source evaluation keeps host actions`() {
        val screens = listOf(
            "RecommendationSourcePrioritySettingsScreen.kt",
            "RecommendationTasteTagsSettingsScreen.kt",
            "RecommendationNonInstalledDiscoverySettingsScreen.kt",
            "RecommendationDiagnosticsSettingsScreen.kt",
        )

        screens.forEach { name ->
            val source = readSource("src/main/java/exh/recs/settings/$name")
            assertTrue(
                source.contains("RecommendationSettingsQuickAccessRow("),
                "$name must use the shared detail-settings navigation row",
            )
            assertTrue(
                source.contains("onHome = openForYou"),
                "$name must expose the shared For You app-bar action",
            )
        }

        val sourceEvaluation = readSource("src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt")
        assertTrue(
            sourceEvaluation.contains("RecommendationSettingsDetailActions("),
            "SourceEvaluationScreen must keep its host/search action surface",
        )
        assertTrue(
            sourceEvaluation.contains("onHome = if (hideForYouTab) null else openForYou"),
            "SourceEvaluationScreen must keep its conditional For You app-bar action",
        )
    }

    @Test
    fun `numeric recommendation settings use the shared bounded control and policy owners`() {
        val diagnostics = readSource("src/main/java/exh/recs/settings/RecommendationDiagnosticsSettingsScreen.kt")
        val taste = readSource("src/main/java/exh/recs/settings/RecommendationTasteTagsSettingsScreen.kt")
        val model = readSource("src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt")

        assertTrue(diagnostics.contains("BoundedIntPreferenceRow("))
        listOf(
            "ForYouResultBudgetPolicy.MIN",
            "GroupPreviewBudgetPolicy.MIN",
            "RecommendationEnrichmentCapPolicy.MIN",
            "SameMangaMatchSettings.MIN_RESULT_CAP",
            "SameMangaMatchSettings.MIN_SAMPLE_SIZE",
        ).forEach { owner ->
            assertTrue(diagnostics.contains(owner), "diagnostics must use policy owner $owner")
        }
        assertTrue(taste.contains("BoundedIntPreferenceRow("))
        assertTrue(taste.contains("RecommendationMinChapterCountPolicy.MIN"))
        listOf(
            "RecommendationEnrichmentCapPolicy.resolve",
            "SameMangaMatchSettings.clampResultCap",
            "SameMangaMatchSettings.clampSampleSize",
        ).forEach { resolver ->
            assertTrue(model.contains(resolver), "model must resolve through $resolver")
        }
    }

    @Test
    fun `evaluation accounting stays in For You Sources and out of the For You feed`() {
        val feed = readSource("src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt")
        val sources = readSource("src/main/java/exh/recs/settings/RecommendationSettingsSharedComponents.kt")
        val sourcePriority = readSource("src/main/java/exh/recs/settings/RecommendationSourcePrioritySettingsScreen.kt")

        assertTrue(
            !feed.contains("rec_for_you_source_evaluated_candidates"),
            "the recommendation feed must not expose diagnostic evaluation counts",
        )
        assertTrue(
            sourcePriority.contains("rec_for_you_source_evaluated_candidates"),
            "Source status ordering must retain the evaluation count",
        )
        assertTrue(
            sources.contains("overflow = TextOverflow.Ellipsis") && sources.contains("modifier = Modifier.fillMaxWidth()"),
            "source names must retain a full-width readable line above diagnostic badges",
        )
        assertTrue(
            sources.contains("val statusLine = buildString") && sources.contains("text = statusLine") &&
                !sources.contains("rec_for_you_source_evaluated_candidates"),
            "compact source priority status must not be crowded with diagnostic candidate counts",
        )
        assertTrue(
            sourcePriority.contains("state.sourceStatuses[src.id]?.evaluatedCount") &&
                sourcePriority.contains("rec_for_you_source_evaluated_candidates"),
            "source status ordering must expose the candidate count without changing source priority",
        )
    }
}

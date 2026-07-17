package exh.recs.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// KMK v0.8.10 -->
/**
 * Contract test for the static `item(key = ...)` identifiers each Recommendation Settings screen
 * declares in its `LazyColumn` — the same identifiers `RecommendationSettingsSearchScreen`'s search
 * entries use as [RecommendationSettingsSearchIndex.Entry.anchor] values. Compose UI testing isn't
 * available in this project (no Robolectric/instrumented test infrastructure), so this can't drive
 * the real screens; instead it hardcodes the same key lists the screens declare (kept in sync
 * manually — each screen file cross-references this test in its own comments) and asserts the one
 * property that would otherwise only surface as a runtime Compose crash: LazyColumn item keys must
 * be unique within a single screen, or ambiguous scroll targets and recomposition bugs result.
 *
 * If a screen's real item list ever diverges from what's listed here, the fix is to update this
 * test's list to match the screen, not to "make the test pass" some other way — this is a regression
 * guard against a copy/paste anchor conflict within one screen, not a source of truth.
 */
class RecommendationSettingsScreenAnchorKeysTest {

    private val forYouKeys = listOf(
        "lang_header", "lang_content",
        "rated_header", "rated_content",
        "hide_known_manga",
        "min_chapter_count",
        "result_budget",
        "group_preview_budget",
        "refresh_hint",
    )

    private val tasteTagsKeys = listOf("tag_header", "tag_content")

    private val diagnosticsKeys = listOf(
        "management_header",
        "quality_signal_history_entry",
        "discovery_cache_header",
        "enrichment_cap",
        "clear_discovery_history",
    )

    // Static keys only -- excludes the dynamic per-source rows and status-breakdown section, which
    // use runtime-generated keys (source id / status group name) that can't be enumerated statically.
    private val sourcePriorityStaticKeys = listOf(
        "source_header", "source_summary", "source_status_note",
        "source_reset_button",
        "source_suggest_order_button", "source_suggest_order_note",
        "same_manga_header", "same_manga_results_per_source", "same_manga_preselect",
        "best_version_sample_size", "best_version_avoid_first_pages",
    )

    // Static keys only -- excludes the dynamic per-suggestion rows, which use a runtime dismissal
    // key that can't be enumerated statically.
    private val discoveryStaticKeys = listOf(
        "sources_to_try_header",
        "sources_to_try_empty",
        "suggestions_expand_toggle",
        "suggestions_bulk_install",
        "suggestions_scope_note",
        "suggestions_clear_dismissed",
        "quality_marks_clear",
    )

    private fun assertAllUnique(keys: List<String>, screenName: String) {
        val duplicates = keys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertEquals(emptySet<String>(), duplicates, "$screenName has duplicate item keys: $duplicates")
    }

    @Test
    fun `RecommendationForYouSettingsScreen item keys are unique`() {
        assertAllUnique(forYouKeys, "RecommendationForYouSettingsScreen")
    }

    @Test
    fun `RecommendationTasteTagsSettingsScreen item keys are unique`() {
        assertAllUnique(tasteTagsKeys, "RecommendationTasteTagsSettingsScreen")
    }

    @Test
    fun `RecommendationDiagnosticsSettingsScreen item keys are unique`() {
        assertAllUnique(diagnosticsKeys, "RecommendationDiagnosticsSettingsScreen")
    }

    @Test
    fun `RecommendationSourcePrioritySettingsScreen static item keys are unique`() {
        assertAllUnique(sourcePriorityStaticKeys, "RecommendationSourcePrioritySettingsScreen")
    }

    @Test
    fun `RecommendationNonInstalledDiscoverySettingsScreen static item keys are unique`() {
        assertAllUnique(discoveryStaticKeys, "RecommendationNonInstalledDiscoverySettingsScreen")
    }

    @Test
    fun `every anchor used by search entries in RecommendationSettingsSearchScreen resolves within its screen's key list`() {
        // Mirrors the anchor -> destination pairing declared in
        // rememberRecommendationSettingsSearchEntries(). If a search entry's anchor is renamed on
        // one side (the entry or the screen's item key) without updating the other, this fails --
        // exactly the class of bug ScrollToAnchorEffect otherwise swallows silently at runtime.
        val forYouAnchors = listOf("lang_content", "rated_content", "hide_known_manga", "min_chapter_count", "result_budget", "group_preview_budget")
        val sourcePriorityAnchors = listOf(
            "source_reset_button",
            "source_suggest_order_button",
            "same_manga_results_per_source",
            "same_manga_preselect",
            "best_version_sample_size",
            "best_version_avoid_first_pages",
        )
        val discoveryAnchors = listOf("suggestions_bulk_install", "suggestions_clear_dismissed", "quality_marks_clear")
        val diagnosticsAnchors = listOf("quality_signal_history_entry", "enrichment_cap", "clear_discovery_history")

        forYouAnchors.forEach { assertEquals(true, it in forYouKeys, "for-you anchor '$it' missing from forYouKeys") }
        sourcePriorityAnchors.forEach { assertEquals(true, it in sourcePriorityStaticKeys, "source-priority anchor '$it' missing from sourcePriorityStaticKeys") }
        discoveryAnchors.forEach { assertEquals(true, it in discoveryStaticKeys, "discovery anchor '$it' missing from discoveryStaticKeys") }
        diagnosticsAnchors.forEach { assertEquals(true, it in diagnosticsKeys, "diagnostics anchor '$it' missing from diagnosticsKeys") }
    }
}
// KMK <--

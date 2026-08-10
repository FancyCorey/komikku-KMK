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
 *
 * KMK v0.8.14: `RecommendationForYouSettingsScreen` and `RecommendationMatchingVersionsSettingsScreen`
 * were retired as top-level destinations (and deleted as files -- their controls all moved to
 * `RecommendationTasteTagsSettingsScreen`/`RecommendationDiagnosticsSettingsScreen`), so their
 * dedicated key lists/tests were removed rather than kept as dead placeholders. Every former key of
 * theirs is still covered below, just merged into the screen that now owns it.
 *
 * KMK v0.8.14-fix1: `RecommendationSourcePrioritySettingsScreen` (renamed "For You sources") lost its
 * language keys -- `LanguageSelectorContent` moved to `RecommendationDiagnosticsSettingsScreen`
 * (renamed `source_languages_header`/`source_languages_content` -> `recommendation_languages_header`/
 * `recommendation_languages_content`). The preview action moved from the bottom to the top of the
 * source screen (`preview_for_you` is now the first key). Source Evaluation gained
 * The pre-run outdated-source disclosure was later removed; excluded rows use the neutral row
 * state instead.
 */
class RecommendationSettingsScreenAnchorKeysTest {

    // KMK v0.8.14: rated_header/rated_content/hide_known_manga/min_chapter_count moved in from the
    // retired For You screen -- see RecommendationTasteTagsSettingsScreen's class doc.
    private val tasteTagsKeys = listOf(
        "rated_header",
        "rated_content",
        "hide_known_manga",
        "min_chapter_count",
        "tag_header",
        "tag_content",
        "suggestions_header",
        "suggestions_content",
    )

    // KMK v0.8.14: display_performance_header/result_budget/refresh_hint moved in from the retired
    // For You screen; versions_quality_header/same_manga_*/best_version_*/group_preview_budget moved
    // in from the retired Matching and versions screen. See RecommendationDiagnosticsSettingsScreen's
    // class doc.
    // KMK v0.8.14-fix1: recommendation_languages_header/recommendation_languages_content moved in
    // from For You sources (formerly Sources and languages).
    private val diagnosticsKeys = listOf(
        "recommendation_languages_header",
        "recommendation_languages_content",
        "display_performance_header",
        "result_budget",
        "refresh_hint",
        "versions_quality_header",
        "same_manga_results_per_source",
        "same_manga_preselect",
        "best_version_header",
        "best_version_sample_size",
        "best_version_avoid_first_pages",
        "group_preview_budget",
        "management_header",
        "quality_signal_history_entry",
        "discovery_cache_header",
        "enrichment_cap",
        "clear_discovery_history",
        "taste_diagnostics_header",
        "taste_diagnostics_content",
    )

    // Static keys only -- excludes the dynamic per-source rows and status-breakdown section, which
    // use runtime-generated keys (source id / status group name) that can't be enumerated statically.
    // KMK v0.8.12: same_manga_*/best_version_* keys moved to diagnosticsKeys above.
    // KMK v0.8.13-fix1: source_languages_header/source_languages_content moved in from For You.
    // KMK v0.8.14: renamed from Source Priority to Sources and languages -- same key set.
    // KMK v0.8.14-fix1: renamed to "For You sources"; source_languages_header/source_languages_content
    // moved OUT to RecommendationDiagnosticsSettingsScreen (see diagnosticsKeys above);
    // preview_for_you moved from the bottom of the screen to the top.
    private val forYouSourcesKeys = listOf(
        "preview_for_you",
        "source_header",
        "source_status_note",
        "source_reset_button",
        "source_suggest_order_button",
        "source_suggest_order_note",
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

    // KMK v0.8.11: static SourceEvaluationScreen anchor targets (Phase F). Excludes dynamic
    // per-evaluation-row keys.
    // KMK v0.8.14: setup_options_toggle/installer_details_toggle added -- Phase E collapses the
    // skip/explicit toggles, candidate diagnostics, and installer-mode selector behind disclosures.
    // KMK v0.8.20-fix2: the pre-run outdated-source disclosure was removed; excluded rows use the
    // neutral row state and are not represented by a static anchor.
    private val sourceEvaluationStaticKeys = listOf(
        "run_header",
        "batch_size",
        "setup_options_toggle",
        "installer_header",
        "installer_details_toggle",
        "installer_mode",
        "reassess_header",
        "rec_quality_section",
        "diagnostics_header",
    )

    private fun assertAllUnique(keys: List<String>, screenName: String) {
        val duplicates = keys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertEquals(emptySet<String>(), duplicates, "$screenName has duplicate item keys: $duplicates")
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
        assertAllUnique(forYouSourcesKeys, "RecommendationSourcePrioritySettingsScreen")
    }

    @Test
    fun `RecommendationNonInstalledDiscoverySettingsScreen static item keys are unique`() {
        assertAllUnique(discoveryStaticKeys, "RecommendationNonInstalledDiscoverySettingsScreen")
    }

    @Test
    fun `SourceEvaluationScreen static item keys are unique`() {
        assertAllUnique(sourceEvaluationStaticKeys, "SourceEvaluationScreen")
    }

    @Test
    fun `every anchor used by search entries in RecommendationSettingsSearchScreen resolves within its screen's key list`() {
        // Mirrors the anchor -> destination pairing declared in
        // rememberRecommendationSettingsSearchEntries(). If a search entry's anchor is renamed on
        // one side (the entry or the screen's item key) without updating the other, this fails --
        // exactly the class of bug ScrollToAnchorEffect otherwise swallows silently at runtime.
        val tasteTagsAnchors = listOf("rated_content", "hide_known_manga", "min_chapter_count")
        val forYouSourcesAnchors = listOf(
            "source_reset_button",
            "source_suggest_order_button",
            "preview_for_you",
        )
        val diagnosticsAnchors = listOf(
            "recommendation_languages_content",
            "result_budget",
            "same_manga_results_per_source",
            "same_manga_preselect",
            "best_version_sample_size",
            "best_version_avoid_first_pages",
            "group_preview_budget",
            "quality_signal_history_entry",
            "enrichment_cap",
            "clear_discovery_history",
        )
        val discoveryAnchors = listOf("suggestions_bulk_install", "suggestions_clear_dismissed", "quality_marks_clear")
        // KMK v0.8.11: Source Evaluation control search entries now carry real anchors (Phase A/F).
        val sourceEvaluationAnchors = listOf("batch_size", "reassess_header", "diagnostics_header", "installer_header")

        tasteTagsAnchors.forEach { assertEquals(true, it in tasteTagsKeys, "taste-filters anchor '$it' missing from tasteTagsKeys") }
        forYouSourcesAnchors.forEach { assertEquals(true, it in forYouSourcesKeys, "for-you-sources anchor '$it' missing from forYouSourcesKeys") }
        diagnosticsAnchors.forEach { assertEquals(true, it in diagnosticsKeys, "diagnostics anchor '$it' missing from diagnosticsKeys") }
        discoveryAnchors.forEach { assertEquals(true, it in discoveryStaticKeys, "discovery anchor '$it' missing from discoveryStaticKeys") }
        sourceEvaluationAnchors.forEach { assertEquals(true, it in sourceEvaluationStaticKeys, "source-evaluation anchor '$it' missing from sourceEvaluationStaticKeys") }
    }
}
// KMK <--

package exh.recs.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.util.Screen
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

// KMK v0.8.8 -->
/**
 * "Management and diagnostics" detail screen -- reset/repair/history/advanced diagnostics, plus
 * (as of v0.8.14) the former For You result budget and the former Matching-and-versions controls,
 * which are all advanced/secondary controls rather than a meaningful top-level destination on their
 * own. Sectioned into:
 *
 * - Recommendation languages: which languages are searched (moved in v0.8.14-fix1, see below).
 * - Display and performance: For You result budget, refresh hint.
 * - Manga versions and quality: same-manga matching, Best Version preview, group-preview budget.
 * - Discovery and cache: Best Version history, enrichment cap, reset discovery history.
 * - Diagnostics: local taste diagnostics.
 *
 * KMK v0.8.14: moved in from the retired `RecommendationForYouSettingsScreen`
 * (`setResultBudget`/`state.resultBudget`) and the retired `RecommendationMatchingVersionsSettingsScreen`
 * (`setSameMangaResultsPerSource`/`setSameMangaPreselectResults`/`setBestVersionPreviewSampleSize`/
 * `setBestVersionAvoidFirstPages`/`setGroupPreviewBudget`) -- every control, `screenModel` method, and
 * preference read/write is byte-for-byte identical to before; only the screen boundary and section
 * grouping changed. See the v0.8.14 implementation report.
 *
 * KMK v0.8.14-fix1: `LanguageSelectorContent` moved in from `RecommendationSourcePrioritySettingsScreen`
 * (formerly "Sources and languages", now "For You sources") -- live-device review confirmed language
 * selection affects the whole recommendation system, not just source ordering, so it does not belong
 * bundled with source priority. Same `state.recommendationLanguages`/`state.availableLanguages`/
 * `screenModel::toggleRecommendationLanguage` as before -- pure move, same preference key, every
 * previously-available language remains selectable.
 */
class RecommendationDiagnosticsSettingsScreen(
    // KMK v0.8.10: see former RecommendationForYouSettingsScreen.anchor.
    val anchor: String? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { RecommendationsSettingsScreenModel() }
        val state by screenModel.state.collectAsState()
        val lazyListState = rememberLazyListState()
        val itemKeysInOrder = remember {
            listOf(
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
                "source_metadata_tag_diagnostics_header",
                "source_metadata_tag_diagnostics_content",
            )
        }
        ScrollToAnchorEffect(lazyListState, itemKeysInOrder, anchor)

        // KMK v0.8.18-fix1: the right-edge quick-access panel was removed from this and every other
        // Recommendation Settings detail screen -- see exh.recs.settings shared components for its new
        // home (For You / Loved / Liked / Disliked only).
        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.rec_settings_index_diagnostics),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            val layoutDirection = LocalLayoutDirection.current
            val listContentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
                bottom = contentPadding.calculateBottomPadding(),
            )
            Column(modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
                RecommendationSettingsQuickAccessRow(
                    current = RecommendationSettingsQuickAccessDestination.ManagementAndDiagnostics,
                    onNavigate = { destination -> navigator.replace(destination.toScreen()) },
                )
                LazyColumn(state = lazyListState, contentPadding = listContentPadding, modifier = Modifier.weight(1f)) {
                    // KMK v0.8.14-fix1: moved in from the former "Sources and languages" screen -- see
                    // class doc above.
                    item(key = "recommendation_languages_header") {
                        SectionHeader(
                            stringResource(KMR.strings.rec_settings_management_languages_header),
                            summary = stringResource(KMR.strings.rec_settings_summary_languages, state.recommendationLanguages.size),
                        )
                    }
                    item(key = "recommendation_languages_content") {
                        LanguageSelectorContent(
                            selectedLanguages = state.recommendationLanguages,
                            availableLanguages = state.availableLanguages,
                            onToggle = screenModel::toggleRecommendationLanguage,
                        )
                    }

                    // KMK v0.8.14: moved in from the retired For You screen.
                    item(key = "display_performance_header") {
                        SectionHeader(stringResource(KMR.strings.rec_settings_display_performance_header))
                    }
                    item(key = "result_budget") {
                        // KMK v0.8.18-fix1: audited against Komikku's official SliderPreference during
                        // the numeric-settings standardization pass. This control's options (5, 10, 15,
                        // 20, 30) are evenly spaced and would technically fit `5..30 step 5`, but that
                        // range also silently introduces 25 as a newly-selectable value that was never
                        // part of this control's tested/intentional option set. Adding it is not
                        // necessary for standardization, so this control stays a list/dialog control to
                        // preserve exact existing behavior (see group_preview_budget below -- same
                        // reasoning, kept consistent).
                        SameMangaListPrefRow(
                            title = stringResource(KMR.strings.rec_result_budget_title),
                            summary = stringResource(KMR.strings.rec_result_budget_summary),
                            current = state.resultBudget,
                            options = listOf(5, 10, 15, 20, 30),
                            onSelect = screenModel::setResultBudget,
                        )
                    }
                    item(key = "refresh_hint") {
                        Text(
                            text = stringResource(KMR.strings.rec_settings_refresh_hint_short),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = MaterialTheme.padding.medium,
                                vertical = MaterialTheme.padding.extraSmall,
                            ),
                        )
                    }

                    // KMK v0.8.14: moved in from the retired Matching and versions screen. The former
                    // section header summary combined the results-per-source count and preselect on/off
                    // state; that detail is still visible on the "Results per source" row itself below,
                    // so this section header now reads as a plain, stable description instead.
                    item(key = "versions_quality_header") {
                        SectionHeader(
                            stringResource(KMR.strings.rec_settings_versions_quality_header),
                            summary = stringResource(KMR.strings.rec_settings_versions_quality_summary),
                        )
                    }
                    item(key = "same_manga_results_per_source") {
                        // KMK v0.8.18-fix1: audited against the official SliderPreference -- options
                        // (1, 2, 5, 10) are irregularly spaced, no IntProgression represents this
                        // cleanly, kept as a list/dialog control.
                        SameMangaListPrefRow(
                            title = stringResource(KMR.strings.same_manga_match_results_per_source_title),
                            summary = stringResource(KMR.strings.same_manga_match_results_per_source_summary),
                            current = state.sameMangaResultsPerSource,
                            options = listOf(1, 2, 5, 10),
                            onSelect = screenModel::setSameMangaResultsPerSource,
                        )
                    }
                    item(key = "same_manga_preselect") {
                        SameMangaSwitchRow(
                            title = stringResource(KMR.strings.same_manga_match_preselect_title),
                            summary = stringResource(KMR.strings.same_manga_match_preselect_summary),
                            enabled = state.sameMangaPreselectResults,
                            onToggle = { screenModel.setSameMangaPreselectResults(!state.sameMangaPreselectResults) },
                        )
                    }
                    item(key = "best_version_header") {
                        SectionHeader(stringResource(KMR.strings.best_version_settings_header))
                    }
                    item(key = "best_version_sample_size") {
                        // KMK v0.8.18-fix1: audited against the official SliderPreference -- only 3
                        // irregularly-spaced options (2, 5, 10); a slider would be heavier than the
                        // current radio-list for a 3-way choice, kept as a list/dialog control.
                        SameMangaListPrefRow(
                            title = stringResource(KMR.strings.best_version_preview_pages_title),
                            summary = stringResource(KMR.strings.best_version_preview_pages_summary),
                            current = state.bestVersionPreviewSampleSize,
                            options = listOf(2, 5, 10),
                            onSelect = screenModel::setBestVersionPreviewSampleSize,
                        )
                    }
                    item(key = "best_version_avoid_first_pages") {
                        SameMangaSwitchRow(
                            title = stringResource(KMR.strings.best_version_avoid_first_pages_title),
                            summary = stringResource(KMR.strings.best_version_avoid_first_pages_summary),
                            enabled = state.bestVersionAvoidFirstPages,
                            onToggle = { screenModel.setBestVersionAvoidFirstPages(!state.bestVersionAvoidFirstPages) },
                        )
                    }
                    item(key = "group_preview_budget") {
                        // KMK v0.8.18-fix1: same reasoning as result_budget above -- evenly spaced but
                        // converting to a slider would silently add 25 as a new selectable value not
                        // previously offered; not necessary for standardization, kept as a list control.
                        SameMangaListPrefRow(
                            title = stringResource(KMR.strings.rec_group_preview_budget_title),
                            summary = stringResource(KMR.strings.rec_group_preview_budget_summary),
                            current = state.groupPreviewBudget,
                            options = listOf(5, 10, 15, 20, 30),
                            onSelect = screenModel::setGroupPreviewBudget,
                        )
                    }

                    item(key = "management_header") {
                        SectionHeader(stringResource(KMR.strings.rec_settings_management_header))
                    }
                    item(key = "quality_signal_history_entry") {
                        // KMK v0.8.11: official TextPreferenceWidget action row instead of a centered
                        // full-width TextButton, matching how official settings screens present
                        // navigation/action rows.
                        TextPreferenceWidget(
                            title = stringResource(KMR.strings.quality_signal_history_open_button),
                            onPreferenceClick = { navigator.push(QualitySignalHistoryScreen()) },
                        )
                    }

                    item(key = "discovery_cache_header") {
                        SectionHeader(stringResource(KMR.strings.rec_settings_discovery_cache_header))
                    }
                    item(key = "enrichment_cap") {
                        // KMK v0.8.18-fix1: audited against the official SliderPreference -- options
                        // (1, 2, 3, 5, 10, 15, 20) are irregularly spaced, no IntProgression represents
                        // this cleanly, kept as a list/dialog control.
                        SameMangaListPrefRow(
                            title = stringResource(KMR.strings.rec_enrichment_cap_title),
                            summary = stringResource(KMR.strings.rec_enrichment_cap_summary),
                            current = state.enrichmentCap,
                            options = listOf(1, 2, 3, 5, 10, 15, 20),
                            onSelect = screenModel::setEnrichmentCap,
                        )
                    }
                    item(key = "clear_discovery_history") {
                        // KMK v0.8.11: official TextPreferenceWidget action row.
                        TextPreferenceWidget(
                            title = stringResource(KMR.strings.rec_reset_discovery_history),
                            onPreferenceClick = screenModel::requestClearDiscoveryHistory,
                        )
                    }
                    // KMK v0.8.10: local, privacy-safe taste diagnostics -- see TasteDiagnosticsContent.
                    item(key = "taste_diagnostics_header") {
                        SectionHeader(
                            stringResource(KMR.strings.taste_diagnostics_header),
                            summary = null,
                        )
                    }
                    item(key = "taste_diagnostics_content") {
                        TasteDiagnosticsContent(diagnostics = state.tasteDiagnostics)
                    }
                    item(key = "source_metadata_tag_diagnostics_header") {
                        SectionHeader(
                            stringResource(KMR.strings.rec_source_metadata_tag_diagnostics_header),
                            summary = null,
                        )
                    }
                    item(key = "source_metadata_tag_diagnostics_content") {
                        SourceMetadataTagDiagnosticsContent(
                            evaluations = state.sourceMetadataTagDiagnostics,
                        )
                    }
                }
            }
        }

        if (state.showClearDiscoveryHistoryDialog) {
            AlertDialog(
                onDismissRequest = screenModel::dismissClearDiscoveryHistoryDialog,
                title = { Text(stringResource(KMR.strings.rec_reset_discovery_history)) },
                text = { Text(stringResource(KMR.strings.rec_reset_discovery_history_confirm)) },
                confirmButton = {
                    TextButton(onClick = screenModel::confirmClearDiscoveryHistory) {
                        Text(stringResource(MR.strings.action_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = screenModel::dismissClearDiscoveryHistoryDialog) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }
    }
}
// KMK <--

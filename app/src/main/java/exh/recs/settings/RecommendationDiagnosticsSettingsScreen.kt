package exh.recs.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import cafe.adriel.voyager.core.model.rememberNavigatorScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.util.Screen
import exh.recs.ForYouResultBudgetPolicy
import exh.recs.GroupPreviewBudgetPolicy
import exh.recs.RecommendationEnrichmentCapPolicy
import exh.recs.SavedFocusMode
import exh.recs.matching.CrossSourceIdentityReviewScreen
import exh.recs.matching.SameMangaMatchSettings
import exh.recs.matching.SameMangaPreselectionMode
import exh.recs.toFocusDisplayLabel
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.core.common.i18n.stringResource as contextStringResource

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
        val openForYou = rememberOpenForYouFromRecommendationSettings(navigator)
        // KMK EC-04 2026-09-04: Navigator-scoped, shared with the other three Recommendation
        // Settings destination screens -- see RecommendationSourcePrioritySettingsScreen.Content().
        val screenModel = navigator.rememberNavigatorScreenModel { RecommendationsSettingsScreenModel() }
        val state by screenModel.state.collectAsState()
        // KMK v0.8.21-fix4: R4/AUG-14 correction -- saved focus modes, shared with the For You
        // page's Focus dialog via the same SavedFocusModeStore-backed preference (see
        // RecommendationsSettingsScreenModel.savedFocusModes' own doc).
        val savedFocusModes by screenModel.savedFocusModes.collectAsState()
        var focusRenameTarget by remember { mutableStateOf<SavedFocusMode?>(null) }
        var focusDeleteTarget by remember { mutableStateOf<SavedFocusMode?>(null) }
        var showPreselectionModeDialog by remember { mutableStateOf(false) }
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
                "rated_manga_action_placement",
                "chapter_completion_rating_prompt",
                "chapter_completion_rating_other_versions_prompt",
                "confirmed_tracked_version_rating_propagation",
                "confirmed_tracked_version_local_tracking_propagation",
                "identity_review",
                "best_version_header",
                "best_version_sample_size",
                "best_version_avoid_first_pages",
                "group_preview_budget",
                "management_header",
                "quality_signal_history_entry",
                "discovery_cache_header",
                "enrichment_cap",
                "clear_discovery_history",
                "focus_header",
                "focus_explanation",
                "focus_saved_modes",
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
                    actions = {
                        RecommendationSettingsDetailActions(
                            onSearch = { navigator.push(RecommendationSettingsSearchScreen()) },
                            onHome = openForYou,
                        )
                    },
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
                            summary = pluralStringResource(KMR.plurals.rec_settings_summary_languages, count = state.recommendationLanguages.size, state.recommendationLanguages.size),
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
                        BoundedIntPreferenceRow(
                            title = stringResource(KMR.strings.rec_result_budget_title),
                            summary = stringResource(KMR.strings.rec_result_budget_summary),
                            valueTitle = stringResource(KMR.strings.rec_result_budget_title),
                            current = ForYouResultBudgetPolicy.validate(state.resultBudget),
                            enabled = true,
                            min = ForYouResultBudgetPolicy.MIN,
                            max = ForYouResultBudgetPolicy.MAX,
                            valueLabel = { it.toString() },
                            onValueChange = screenModel::setResultBudget,
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
                        BoundedIntPreferenceRow(
                            title = stringResource(KMR.strings.same_manga_match_results_per_source_title),
                            summary = stringResource(KMR.strings.same_manga_match_results_per_source_summary),
                            valueTitle = stringResource(KMR.strings.same_manga_match_results_per_source_title),
                            current = SameMangaMatchSettings.clampResultCap(state.sameMangaResultsPerSource),
                            enabled = true,
                            min = SameMangaMatchSettings.MIN_RESULT_CAP,
                            max = SameMangaMatchSettings.MAX_RESULT_CAP,
                            valueLabel = { it.toString() },
                            onValueChange = screenModel::setSameMangaResultsPerSource,
                        )
                    }
                    item(key = "same_manga_preselect") {
                        TextPreferenceWidget(
                            title = stringResource(KMR.strings.same_manga_match_preselect_title),
                            subtitle = stringResource(KMR.strings.same_manga_match_preselect_summary) + " " + when (state.sameMangaPreselectionMode) {
                                SameMangaPreselectionMode.ALL -> stringResource(KMR.strings.same_manga_match_preselect_all)
                                SameMangaPreselectionMode.NONE -> stringResource(KMR.strings.same_manga_match_preselect_none)
                                SameMangaPreselectionMode.EXACT_NAME -> stringResource(KMR.strings.same_manga_match_preselect_exact_name)
                            },
                            onPreferenceClick = { showPreselectionModeDialog = true },
                        )
                    }
                    item(key = "chapter_completion_rating_prompt") {
                        SameMangaSwitchRow(
                            title = stringResource(KMR.strings.chapter_completion_rating_prompt_title),
                            summary = stringResource(KMR.strings.chapter_completion_rating_prompt_summary),
                            enabled = state.chapterCompletionRatingPromptEnabled,
                            onToggle = { screenModel.setChapterCompletionRatingPromptEnabled(!state.chapterCompletionRatingPromptEnabled) },
                        )
                    }
                    item(key = "rated_manga_action_placement") {
                        SameMangaSwitchRow(
                            title = stringResource(KMR.strings.rated_manga_action_placement_title),
                            summary = stringResource(KMR.strings.rated_manga_action_placement_summary),
                            enabled = state.ratedMangaActionsUseSelection,
                            onToggle = {
                                screenModel.setRatedMangaActionsUseSelection(!state.ratedMangaActionsUseSelection)
                            },
                        )
                    }
                    item(key = "chapter_completion_rating_other_versions_prompt") {
                        SameMangaSwitchRow(
                            title = stringResource(KMR.strings.chapter_completion_rating_other_versions_prompt_title),
                            summary = stringResource(KMR.strings.chapter_completion_rating_other_versions_prompt_summary),
                            enabled = state.chapterCompletionRatingOtherVersionsPromptEnabled,
                            onToggle = {
                                screenModel.setChapterCompletionRatingOtherVersionsPromptEnabled(
                                    !state.chapterCompletionRatingOtherVersionsPromptEnabled,
                                )
                            },
                        )
                    }
                    item(key = "confirmed_tracked_version_rating_propagation") {
                        SameMangaSwitchRow(
                            title = stringResource(KMR.strings.confirmed_tracked_version_rating_propagation_title),
                            summary = stringResource(KMR.strings.confirmed_tracked_version_rating_propagation_summary),
                            enabled = state.confirmedTrackedVersionRatingPropagationEnabled,
                            onToggle = {
                                screenModel.setConfirmedTrackedVersionRatingPropagationEnabled(
                                    !state.confirmedTrackedVersionRatingPropagationEnabled,
                                )
                            },
                        )
                    }
                    item(key = "confirmed_tracked_version_local_tracking_propagation") {
                        SameMangaSwitchRow(
                            title = stringResource(KMR.strings.confirmed_tracked_version_local_tracking_propagation_title),
                            summary = stringResource(KMR.strings.confirmed_tracked_version_local_tracking_propagation_summary),
                            enabled = state.confirmedTrackedVersionLocalTrackingPropagationEnabled,
                            onToggle = {
                                screenModel.setConfirmedTrackedVersionLocalTrackingPropagationEnabled(
                                    !state.confirmedTrackedVersionLocalTrackingPropagationEnabled,
                                )
                            },
                        )
                    }
                    item(key = "identity_review") {
                        TextPreferenceWidget(
                            title = stringResource(KMR.strings.identity_review_title),
                            subtitle = stringResource(KMR.strings.identity_review_summary),
                            onPreferenceClick = { navigator.push(CrossSourceIdentityReviewScreen()) },
                        )
                    }
                    item(key = "best_version_header") {
                        SectionHeader(stringResource(KMR.strings.best_version_settings_header))
                    }
                    item(key = "best_version_sample_size") {
                        BoundedIntPreferenceRow(
                            title = stringResource(KMR.strings.best_version_preview_pages_title),
                            summary = stringResource(KMR.strings.best_version_preview_pages_summary),
                            valueTitle = stringResource(KMR.strings.best_version_preview_pages_title),
                            current = SameMangaMatchSettings.clampSampleSize(state.bestVersionPreviewSampleSize),
                            enabled = true,
                            min = SameMangaMatchSettings.MIN_SAMPLE_SIZE,
                            max = SameMangaMatchSettings.MAX_SAMPLE_SIZE,
                            valueLabel = { it.toString() },
                            onValueChange = screenModel::setBestVersionPreviewSampleSize,
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
                        BoundedIntPreferenceRow(
                            title = stringResource(KMR.strings.rec_group_preview_budget_title),
                            summary = stringResource(KMR.strings.rec_group_preview_budget_summary),
                            valueTitle = stringResource(KMR.strings.rec_group_preview_budget_title),
                            current = GroupPreviewBudgetPolicy.validate(state.groupPreviewBudget),
                            enabled = true,
                            min = GroupPreviewBudgetPolicy.MIN,
                            max = GroupPreviewBudgetPolicy.MAX,
                            valueLabel = { it.toString() },
                            onValueChange = screenModel::setGroupPreviewBudget,
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
                        BoundedIntPreferenceRow(
                            title = stringResource(KMR.strings.rec_enrichment_cap_title),
                            summary = stringResource(KMR.strings.rec_enrichment_cap_summary),
                            valueTitle = stringResource(KMR.strings.rec_enrichment_cap_title),
                            current = RecommendationEnrichmentCapPolicy.resolve(state.enrichmentCap),
                            enabled = true,
                            min = RecommendationEnrichmentCapPolicy.MIN,
                            max = RecommendationEnrichmentCapPolicy.MAX,
                            valueLabel = { it.toString() },
                            onValueChange = screenModel::setEnrichmentCap,
                        )
                    }
                    // KMK --> EC-04 2026-09-01: configurable discovery-effort policy
                    item(key = "discovery_effort_header") {
                        SectionHeader(
                            stringResource(KMR.strings.rec_discovery_effort_title),
                            summary = stringResource(KMR.strings.rec_discovery_effort_summary),
                        )
                    }
                    item(key = "discovery_effort_content") {
                        DiscoveryEffortLevelContent(
                            current = state.discoveryEffortLevel,
                            onSelect = screenModel::setDiscoveryEffortLevel,
                        )
                    }
                    item(key = "discovery_candidate_budget") {
                        BoundedIntPreferenceRow(
                            title = stringResource(KMR.strings.rec_discovery_candidate_budget_title),
                            summary = stringResource(KMR.strings.rec_discovery_candidate_budget_summary),
                            valueTitle = stringResource(KMR.strings.rec_discovery_candidate_budget_title),
                            current = state.discoveryCandidateBudget,
                            enabled = true,
                            min = exh.recs.memory.RecommendationDiscoveryCandidateBudgetPolicy.MIN,
                            max = exh.recs.memory.RecommendationDiscoveryCandidateBudgetPolicy.MAX,
                            valueLabel = { it.toString() },
                            onValueChange = screenModel::setDiscoveryCandidateBudget,
                        )
                    }
                    // KMK <--
                    item(key = "clear_discovery_history") {
                        // KMK v0.8.11: official TextPreferenceWidget action row.
                        TextPreferenceWidget(
                            title = stringResource(KMR.strings.rec_reset_discovery_history),
                            onPreferenceClick = screenModel::requestClearDiscoveryHistory,
                        )
                    }
                    // KMK v0.8.21-fix4: R4/AUG-14 correction -- Management and Diagnostics is the
                    // persistent management surface for saved focus modes, per the corrected AUG-14
                    // contract. Rename/delete/reorder here read and write the exact same
                    // SavedFocusModeStore-backed preference as the For You page's Focus dialog --
                    // one owner, one persistence model, not a second system.
                    item(key = "focus_header") {
                        SectionHeader(stringResource(KMR.strings.rec_settings_focus_header))
                    }
                    item(key = "focus_explanation") {
                        Text(
                            text = stringResource(KMR.strings.rec_settings_focus_explanation),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = MaterialTheme.padding.medium,
                                vertical = MaterialTheme.padding.extraSmall,
                            ),
                        )
                    }
                    item(key = "focus_saved_modes") {
                        if (savedFocusModes.isEmpty()) {
                            Text(
                                text = stringResource(KMR.strings.rec_settings_focus_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(MaterialTheme.padding.medium),
                            )
                        } else {
                            Column {
                                savedFocusModes.forEachIndexed { index, mode ->
                                    SavedFocusModeManagementRow(
                                        mode = mode,
                                        canMoveUp = index > 0,
                                        canMoveDown = index < savedFocusModes.lastIndex,
                                        onMoveUp = {
                                            val ids = savedFocusModes.map { it.id }.toMutableList()
                                            ids.add(index - 1, ids.removeAt(index))
                                            screenModel.reorderFocusModes(ids)
                                        },
                                        onMoveDown = {
                                            val ids = savedFocusModes.map { it.id }.toMutableList()
                                            ids.add(index + 1, ids.removeAt(index))
                                            screenModel.reorderFocusModes(ids)
                                        },
                                        onRename = { focusRenameTarget = mode },
                                        onDelete = { focusDeleteTarget = mode },
                                    )
                                }
                            }
                        }
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
                        // KMK v0.8.21-fix2: no longer passes a per-row review callback -- see
                        // SourceMetadataTagDiagnostics.kt's removal note. The top-level Source
                        // Evaluation entry in Recommendation Settings remains the one route.
                        SourceMetadataTagDiagnosticsContent(
                            evaluations = state.sourceMetadataTagDiagnostics,
                        )
                    }
                }
            }
        }
        if (showPreselectionModeDialog) {
            AlertDialog(
                onDismissRequest = { showPreselectionModeDialog = false },
                title = { Text(stringResource(KMR.strings.same_manga_match_preselect_title)) },
                text = {
                    Column {
                        SameMangaPreselectionMode.entries.forEach { mode ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                RadioButton(
                                    selected = state.sameMangaPreselectionMode == mode,
                                    onClick = {
                                        screenModel.setSameMangaPreselectionMode(mode)
                                        showPreselectionModeDialog = false
                                    },
                                )
                                Text(
                                    when (mode) {
                                        SameMangaPreselectionMode.ALL -> stringResource(KMR.strings.same_manga_match_preselect_all)
                                        SameMangaPreselectionMode.NONE -> stringResource(KMR.strings.same_manga_match_preselect_none)
                                        SameMangaPreselectionMode.EXACT_NAME -> stringResource(KMR.strings.same_manga_match_preselect_exact_name)
                                    },
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPreselectionModeDialog = false }) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
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

        // KMK v0.8.21-fix4: R4/AUG-14 correction -- rename/delete dialogs for the Management and
        // Diagnostics saved-focus-mode section above, mirroring the exact same dialogs the For You
        // page's Focus dialog already uses (BrowsePersonalRecommendationsTab.kt).
        focusRenameTarget?.let { target ->
            var renameName by remember(target.id) { mutableStateOf(target.name) }
            AlertDialog(
                onDismissRequest = { focusRenameTarget = null },
                title = { Text(stringResource(KMR.strings.rec_for_you_focus_rename_title)) },
                text = {
                    OutlinedTextField(
                        value = renameName,
                        onValueChange = { renameName = it },
                        label = { Text(stringResource(KMR.strings.rec_for_you_focus_save_as_hint)) },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            screenModel.renameFocusMode(target.id, renameName)
                            focusRenameTarget = null
                        },
                        enabled = renameName.isNotBlank(),
                    ) {
                        Text(stringResource(KMR.strings.rec_for_you_focus_rename_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { focusRenameTarget = null }) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }
        focusDeleteTarget?.let { target ->
            val context = LocalContext.current
            AlertDialog(
                onDismissRequest = { focusDeleteTarget = null },
                title = { Text(stringResource(KMR.strings.rec_for_you_focus_delete_confirm_title)) },
                text = { Text(context.contextStringResource(KMR.strings.rec_for_you_focus_delete_confirm_message, target.name)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            screenModel.deleteFocusMode(target.id)
                            focusDeleteTarget = null
                        },
                    ) {
                        Text(stringResource(MR.strings.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { focusDeleteTarget = null }) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }
    }
}

// KMK v0.8.21-fix5: R4/AUG-14 completion -- Title Case, deterministic (alphabetical) include/
// exclude summary for a saved mode, shared by this screen and the For You focus dialog's own
// saved-mode list.
@Composable
private fun focusModeCriteriaSummary(mode: SavedFocusMode): String {
    val includeLabel = stringResource(
        KMR.strings.rec_for_you_focus_criteria_include_label,
        mode.includeGroups.sorted().joinToString(", ") { it.toFocusDisplayLabel() },
    )
    val excludeLabel = stringResource(
        KMR.strings.rec_for_you_focus_criteria_exclude_label,
        mode.excludeGroups.sorted().joinToString(", ") { it.toFocusDisplayLabel() },
    )
    val parts = buildList {
        if (mode.includeGroups.isNotEmpty()) add(includeLabel)
        if (mode.excludeGroups.isNotEmpty()) add(excludeLabel)
    }
    return parts.joinToString(" · ")
}

// KMK v0.8.21-fix4: R4/AUG-14 correction -- one row in Management and Diagnostics' saved-focus-mode
// list. Deliberately does not offer "Update with current selection" (unlike the For You dialog's
// equivalent row) -- this screen has no live set of currently-available recommendation groups to
// build that editor from; editing a mode's stored criteria remains a For You-page action.
@Composable
private fun SavedFocusModeManagementRow(
    mode: SavedFocusMode,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var manageMenuOpen by remember(mode.id) { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.extraSmall),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = mode.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = focusModeCriteriaSummary(mode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowUp,
                contentDescription = stringResource(KMR.strings.rec_for_you_focus_move_up),
            )
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = stringResource(KMR.strings.rec_for_you_focus_move_down),
            )
        }
        Box {
            IconButton(onClick = { manageMenuOpen = true }) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = stringResource(KMR.strings.rec_for_you_focus_manage),
                )
            }
            DropdownMenu(expanded = manageMenuOpen, onDismissRequest = { manageMenuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(KMR.strings.rec_for_you_focus_rename)) },
                    onClick = {
                        manageMenuOpen = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(MR.strings.action_delete)) },
                    onClick = {
                        manageMenuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}
// KMK <--

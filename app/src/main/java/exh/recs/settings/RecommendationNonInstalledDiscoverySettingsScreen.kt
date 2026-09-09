package exh.recs.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import cafe.adriel.voyager.core.model.rememberNavigatorScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.util.Screen
import exh.recs.discovery.SourcesToTrySearchAndSort
import exh.recs.discovery.SourcesToTrySortMode
import exh.recs.sourceprefs.RecommendationSourcePreference
import exh.recs.sourceprefs.RecommendationSourcePreferenceStore
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

// KMK v0.8.8 -->
/** "Sources To Try" (non-installed discovery) detail screen — extracted verbatim. Pure move, zero preference-behavior change. */
class RecommendationNonInstalledDiscoverySettingsScreen(
    // KMK v0.8.10: stable in-screen scroll target -- see ScrollToAnchorEffect.
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
        val lazyListState = rememberLazyListState()

        // KMK v0.8.10: local search + sort over the already-loaded, already-scored suggestion
        // list -- never re-queries or re-scores anything. A non-blank query bypasses the
        // show-5-then-expand cap entirely (a filtered result set shouldn't be truncated further);
        // sort mode applies regardless of whether a search is active.
        var searchQuery by rememberSaveable { mutableStateOf<String?>(null) }
        var sortMode by rememberSaveable { mutableStateOf(SourcesToTrySortMode.BEST_FIT) }
        val isSearching = !searchQuery.isNullOrBlank()
        val filteredSuggestions = remember(state.nonInstalledSuggestions, searchQuery, sortMode) {
            SourcesToTrySearchAndSort.sort(
                SourcesToTrySearchAndSort.search(state.nonInstalledSuggestions, searchQuery.orEmpty()),
                sortMode,
            )
        }
        val visibleSuggestions = when {
            filteredSuggestions.isEmpty() -> filteredSuggestions
            isSearching || state.suggestionsExpanded -> filteredSuggestions
            else -> filteredSuggestions.take(5)
        }
        // The summary describes the active result set while searching; when the list is merely
        // collapsed it continues to describe the full suggestion pool.
        val displayedSuggestionCount = if (isSearching) {
            filteredSuggestions.size
        } else {
            state.nonInstalledSuggestions.size
        }

        // KMK v0.8.10: mirrors the LazyColumn's item order below, including its conditional
        // sections and the dynamic suggestion-row count, so a static control key placed after the
        // suggestion list still resolves to the correct scroll index. Individual suggestion rows
        // use placeholder keys -- only the static control keys are ever used as search anchors.
        val itemKeysInOrder = remember(
            state.nonInstalledSuggestions.isEmpty(),
            visibleSuggestions.size,
            isSearching,
            filteredSuggestions.isEmpty(),
            state.nonInstalledSuggestions.size,
            state.dismissedSuggestionCount,
            state.qualityDislikedSourceKeys.size,
            state.suggestionInstallFeedback,
        ) {
            buildList {
                add("sources_to_try_header")
                if (state.suggestionInstallFeedback != null) add("sources_to_try_install_result")
                if (state.nonInstalledSuggestions.isEmpty()) {
                    add("sources_to_try_empty")
                } else {
                    add("sources_to_try_sort")
                    add("sources_to_try_filter")
                    if (isSearching && filteredSuggestions.isEmpty()) {
                        add("sources_to_try_search_empty")
                    } else {
                        repeat(visibleSuggestions.size) { add("suggestion_row_$it") }
                        if (!isSearching && state.nonInstalledSuggestions.size > 5) add("suggestions_expand_toggle")
                        add("suggestions_bulk_install")
                        add("suggestions_scope_note")
                        if (state.dismissedSuggestionCount > 0) add("suggestions_clear_dismissed")
                        if (state.qualityDislikedSourceKeys.isNotEmpty()) add("quality_marks_clear")
                    }
                }
            }
        }
        ScrollToAnchorEffect(lazyListState, itemKeysInOrder, anchor)

        // KMK v0.8.18-fix1: the right-edge quick-access panel was removed from this and every other
        // Recommendation Settings detail screen -- see exh.recs.settings shared components for its new
        // home (For You / Loved / Liked / Disliked only).
        Scaffold(
            topBar = { scrollBehavior ->
                // KMK v0.8.10: SearchToolbar -- same reusable search affordance used by
                // RatedMangaScreen (Phase C) and the Library tab, not a bespoke search bar.
                SearchToolbar(
                    titleContent = { Text(stringResource(KMR.strings.rec_settings_index_discovery)) },
                    searchQuery = searchQuery,
                    onChangeSearchQuery = { searchQuery = it },
                    navigateUp = navigator::pop,
                    searchEnabled = false,
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
                    current = RecommendationSettingsQuickAccessDestination.SourcesToTry,
                    onNavigate = { destination -> navigator.replace(destination.toScreen()) },
                )
                LazyColumn(state = lazyListState, contentPadding = listContentPadding, modifier = Modifier.weight(1f)) {
                    item(key = "sources_to_try_header") {
                        SectionHeader(
                            stringResource(KMR.strings.rec_sources_to_try_header),
                            summary = pluralStringResource(KMR.plurals.rec_settings_summary_sources_to_try, count = displayedSuggestionCount, displayedSuggestionCount),
                        )
                    }
                    state.suggestionInstallFeedback?.let { feedback ->
                        item(key = "sources_to_try_install_result") {
                            SourcesToTryInstallFeedbackContent(
                                feedback = feedback,
                                retryAvailable = state.retryableSuggestionInstallFailureCount > 0,
                                onRetry = screenModel::retrySuggestionInstallFailures,
                                onClose = screenModel::dismissSuggestionInstallFeedback,
                            )
                        }
                    }
                    if (state.nonInstalledSuggestions.isEmpty()) {
                        item(key = "sources_to_try_empty") {
                            Text(
                                text = stringResource(KMR.strings.rec_sources_to_try_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(
                                    horizontal = MaterialTheme.padding.medium,
                                    vertical = MaterialTheme.padding.extraSmall,
                                ),
                            )
                        }
                    } else {
                        item(key = "sources_to_try_sort") {
                            SourcesToTrySortRow(current = sortMode, onSelect = { sortMode = it })
                        }
                        item(key = "sources_to_try_filter") {
                            OutlinedTextField(
                                value = searchQuery.orEmpty(),
                                onValueChange = { searchQuery = it },
                                label = { Text(stringResource(KMR.strings.rec_sources_to_try_search_hint)) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = MaterialTheme.padding.medium),
                            )
                        }
                        // KMK v0.8.10: a search that matches nothing is distinct from "no suggestions
                        // exist at all" (the sources_to_try_empty branch above).
                        if (isSearching && filteredSuggestions.isEmpty()) {
                            item(key = "sources_to_try_search_empty") {
                                Text(
                                    text = stringResource(MR.strings.no_results_found),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(
                                        horizontal = MaterialTheme.padding.medium,
                                        vertical = MaterialTheme.padding.extraSmall,
                                    ),
                                )
                            }
                            return@LazyColumn
                        }
                        items(
                            count = visibleSuggestions.size,
                            key = { "suggestion_${visibleSuggestions[it].dismissalKey}" },
                        ) { index ->
                            val suggestion = visibleSuggestions[index]
                            val availKey = RecommendationSourcePreferenceStore.availableKey(
                                suggestion.extension.signatureHash,
                                suggestion.extension.pkgName,
                                suggestion.source?.id,
                            )
                            val isSuggestionLiked = availKey in state.likedSourceKeys
                            val isSuggestionDisliked = availKey in state.dislikedSourceKeys
                            val isSuggestionQualityDisliked = availKey in state.qualityDislikedSourceKeys
                            val isSuggestionQualityExplicit = availKey in state.qualityExplicitSourceKeys
                            SourceSuggestionItem(
                                suggestion = suggestion,
                                onInstall = { screenModel.installSuggestion(suggestion) },
                                onCancelInstall = { screenModel.cancelSuggestionInstall(suggestion) },
                                onDismiss = { screenModel.dismissSuggestion(suggestion) },
                                isLiked = isSuggestionLiked,
                                isDisliked = isSuggestionDisliked,
                                onLike = {
                                    screenModel.setAvailableSourcePreference(
                                        suggestion,
                                        if (isSuggestionLiked) RecommendationSourcePreference.NEUTRAL else RecommendationSourcePreference.LIKE,
                                    )
                                },
                                onDislike = {
                                    screenModel.setAvailableSourcePreference(
                                        suggestion,
                                        if (isSuggestionDisliked) RecommendationSourcePreference.NEUTRAL else RecommendationSourcePreference.DISLIKE,
                                    )
                                },
                                isInstalling = suggestion.dismissalKey in state.installingSuggestionKeys,
                                selectionMode = state.isSuggestionSelectionMode,
                                selected = suggestion.dismissalKey in state.selectedSuggestionKeys,
                                onToggleSelected = { screenModel.toggleSuggestionSelected(suggestion) },
                                isQualityDisliked = isSuggestionQualityDisliked,
                                isQualityExplicit = isSuggestionQualityExplicit,
                                onMarkQualityPoor = { screenModel.markAvailableSourceQualityPoor(suggestion) },
                                onMarkQualityExplicit = { screenModel.markAvailableSourceQualityExplicit(suggestion) },
                                onClearQualityMark = { screenModel.clearAvailableSourceQualityMark(suggestion) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        if (!isSearching && state.nonInstalledSuggestions.size > 5) {
                            item(key = "suggestions_expand_toggle") {
                                TextButton(
                                    onClick = screenModel::toggleExpandSuggestions,
                                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                                ) {
                                    Text(
                                        if (state.suggestionsExpanded) {
                                            stringResource(KMR.strings.rec_suggestions_show_fewer)
                                        } else {
                                            pluralStringResource(KMR.plurals.rec_suggestions_show_more, count = state.nonInstalledSuggestions.size - 5, state.nonInstalledSuggestions.size - 5)
                                        },
                                    )
                                }
                            }
                        }
                        item(key = "suggestions_bulk_install") {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.extraSmall),
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                            ) {
                                if (state.isSuggestionSelectionMode) {
                                    val selectedCount = visibleSuggestions.count { it.dismissalKey in state.selectedSuggestionKeys }
                                    Button(
                                        onClick = { screenModel.installSelectedSuggestions(visibleSuggestions) },
                                        enabled = selectedCount > 0 && !state.isBulkInstallingSuggestions,
                                    ) {
                                        Text(stringResource(KMR.strings.rec_suggestion_install_selected, selectedCount))
                                    }
                                    OutlinedButton(onClick = screenModel::exitSuggestionSelectionMode) {
                                        Text(stringResource(KMR.strings.rec_suggestion_cancel_selection))
                                    }
                                } else if (state.isBulkInstallingSuggestions) {
                                    Button(
                                        onClick = {},
                                        enabled = false,
                                    ) {
                                        Text(stringResource(KMR.strings.rec_suggestion_installing_visible))
                                    }
                                    OutlinedButton(onClick = screenModel::cancelBulkSuggestionInstall) {
                                        Text(stringResource(KMR.strings.rec_suggestion_cancel_install))
                                    }
                                } else {
                                    Button(
                                        onClick = { screenModel.installSuggestions(visibleSuggestions) },
                                        enabled = visibleSuggestions.isNotEmpty(),
                                    ) {
                                        Text(stringResource(KMR.strings.rec_suggestion_install_visible, visibleSuggestions.size))
                                    }
                                    TextButton(
                                        onClick = screenModel::enterSuggestionSelectionMode,
                                        enabled = visibleSuggestions.isNotEmpty(),
                                    ) {
                                        Text(stringResource(KMR.strings.rec_suggestion_select))
                                    }
                                }
                            }
                        }
                        item(key = "suggestions_scope_note") {
                            Text(
                                text = stringResource(KMR.strings.rec_source_preference_scope_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(
                                    horizontal = MaterialTheme.padding.medium,
                                    vertical = MaterialTheme.padding.extraSmall,
                                ),
                            )
                        }
                        // KMK v0.8.11: these were TextButtons visually inline with the suggestion list;
                        // now official TextPreferenceWidget management-action rows, separated from the
                        // per-row suggestion actions above (Phase D4).
                        if (state.dismissedSuggestionCount > 0) {
                            item(key = "suggestions_clear_dismissed") {
                                TextPreferenceWidget(
                                    title = stringResource(KMR.strings.rec_clear_dismissed_suggestions) + " (${state.dismissedSuggestionCount})",
                                    onPreferenceClick = screenModel::clearDismissedSuggestions,
                                )
                            }
                        }
                        if (state.qualityDislikedSourceKeys.isNotEmpty()) {
                            item(key = "quality_marks_clear") {
                                TextPreferenceWidget(
                                    title = stringResource(KMR.strings.source_quality_clear_all_marks) + " (${state.qualityDislikedSourceKeys.size})",
                                    onPreferenceClick = screenModel::clearAllSourceQualityMarks,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
// KMK <--

@Composable
private fun SourcesToTryInstallFeedbackContent(
    feedback: SourcesToTryInstallFeedback,
    retryAvailable: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    val text = when (feedback) {
        is SourcesToTryInstallFeedback.Installed -> stringResource(KMR.strings.rec_suggestion_install_success)
        is SourcesToTryInstallFeedback.Failed -> stringResource(KMR.strings.rec_suggestion_install_failed)
        is SourcesToTryInstallFeedback.BulkInstalled -> pluralStringResource(
            KMR.plurals.rec_suggestion_install_success_count,
            feedback.installedCount,
            feedback.installedCount,
        )
        is SourcesToTryInstallFeedback.BulkPartial -> stringResource(
            KMR.strings.rec_suggestion_install_partial_summary,
            pluralStringResource(
                KMR.plurals.rec_suggestion_install_success_count,
                feedback.installedCount,
                feedback.installedCount,
            ),
            pluralStringResource(
                KMR.plurals.rec_suggestion_install_failed_count,
                feedback.failedCount,
                feedback.failedCount,
            ),
        )
        is SourcesToTryInstallFeedback.BulkFailed -> pluralStringResource(
            KMR.plurals.rec_suggestion_install_failed_count,
            feedback.failedCount,
            feedback.failedCount,
        )
        is SourcesToTryInstallFeedback.Cancelled -> stringResource(KMR.strings.rec_suggestion_install_cancelled)
    }
    val retryable = retryAvailable && (
        feedback is SourcesToTryInstallFeedback.Failed ||
            feedback is SourcesToTryInstallFeedback.BulkPartial ||
            feedback is SourcesToTryInstallFeedback.BulkFailed ||
            feedback is SourcesToTryInstallFeedback.Cancelled
        )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        ) {
            if (retryable) {
                Button(onClick = onRetry) {
                    Text(stringResource(KMR.strings.rec_suggestion_retry_failed))
                }
            }
            TextButton(onClick = onClose) {
                Text(stringResource(KMR.strings.rec_suggestion_close_result))
            }
        }
    }
}

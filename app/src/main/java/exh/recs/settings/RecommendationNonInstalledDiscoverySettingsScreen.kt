package exh.recs.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.util.Screen
import exh.recs.discovery.SourcesToTrySearchAndSort
import exh.recs.discovery.SourcesToTrySortMode
import exh.recs.sourceprefs.RecommendationSourcePreference
import exh.recs.sourceprefs.RecommendationSourcePreferenceStore
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

// KMK v0.8.8 -->
/** "Sources To Try" (non-installed discovery) detail screen — extracted verbatim. Pure move, zero preference-behavior change. */
class RecommendationNonInstalledDiscoverySettingsScreen(
    // KMK v0.8.10: see RecommendationForYouSettingsScreen.anchor.
    val anchor: String? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { RecommendationsSettingsScreenModel() }
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
        ) {
            buildList {
                add("sources_to_try_header")
                if (state.nonInstalledSuggestions.isEmpty()) {
                    add("sources_to_try_empty")
                } else {
                    add("sources_to_try_sort")
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

        Scaffold(
            topBar = { scrollBehavior ->
                // KMK v0.8.10: SearchToolbar -- same reusable search affordance used by
                // RatedMangaScreen (Phase C) and the Library tab, not a bespoke search bar.
                SearchToolbar(
                    titleContent = { Text(stringResource(KMR.strings.rec_settings_index_discovery)) },
                    searchQuery = searchQuery,
                    onChangeSearchQuery = { searchQuery = it },
                    placeholderText = stringResource(KMR.strings.rec_sources_to_try_search_hint),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            LazyColumn(state = lazyListState, contentPadding = contentPadding) {
                item(key = "sources_to_try_header") {
                    SectionHeader(
                        stringResource(KMR.strings.rec_sources_to_try_header),
                        summary = stringResource(KMR.strings.rec_settings_summary_sources_to_try, state.nonInstalledSuggestions.size),
                    )
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
                                        stringResource(KMR.strings.rec_suggestions_show_more, state.nonInstalledSuggestions.size - 5)
                                    },
                                )
                            }
                        }
                    }
                    item(key = "suggestions_bulk_install") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.extraSmall),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
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
                            } else {
                                Button(
                                    onClick = { screenModel.installSuggestions(visibleSuggestions) },
                                    enabled = visibleSuggestions.isNotEmpty() && !state.isBulkInstallingSuggestions,
                                ) {
                                    Text(
                                        if (state.isBulkInstallingSuggestions) {
                                            stringResource(KMR.strings.rec_suggestion_installing_visible)
                                        } else {
                                            stringResource(KMR.strings.rec_suggestion_install_visible, visibleSuggestions.size)
                                        },
                                    )
                                }
                                TextButton(
                                    onClick = screenModel::enterSuggestionSelectionMode,
                                    enabled = visibleSuggestions.isNotEmpty() && !state.isBulkInstallingSuggestions,
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
                    if (state.dismissedSuggestionCount > 0) {
                        item(key = "suggestions_clear_dismissed") {
                            TextButton(
                                onClick = screenModel::clearDismissedSuggestions,
                                modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                            ) {
                                Text(
                                    stringResource(KMR.strings.rec_clear_dismissed_suggestions) +
                                        " (${state.dismissedSuggestionCount})",
                                )
                            }
                        }
                    }
                    if (state.qualityDislikedSourceKeys.isNotEmpty()) {
                        item(key = "quality_marks_clear") {
                            TextButton(
                                onClick = screenModel::clearAllSourceQualityMarks,
                                modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                            ) {
                                Text(
                                    stringResource(KMR.strings.source_quality_clear_all_marks) +
                                        " (${state.qualityDislikedSourceKeys.size})",
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

package exh.recs.settings

// KMK -->
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.CatalogueSource
import exh.recs.RecommendationSourceRunStatus
import exh.recs.RecommendationSourceStatus
import exh.recs.SourceFitLabel
import exh.recs.SourceFitStats
import exh.recs.discovery.NonInstalledSourceSuggestion
import exh.recs.discovery.NonInstalledSuggestionReason
import exh.recs.discovery.SuggestionConfidence
import exh.recs.evaluation.SourceEvaluationScreen
import exh.recs.sourceprefs.RecommendationSourcePreference
import exh.recs.sourceprefs.RecommendationSourcePreferenceStore
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.taste.model.RatedMangaVisibility
import tachiyomi.domain.taste.model.TagPreference
import tachiyomi.domain.taste.model.TagTaste
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

class RecommendationsSettingsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { RecommendationsSettingsScreenModel() }
        val state by screenModel.state.collectAsState()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.taste_settings_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                    // KMK --> v0.6.14: reset action moved to Source Priority section with confirmation dialog
                    // KMK <--
                )
            },
        ) { contentPadding ->
            val lazyListState = rememberLazyListState()
            val sourcesState = remember { state.orderedSources.toMutableStateList() }
            val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                val fromSourceId = from.key as? Long ?: return@rememberReorderableLazyListState
                val toSourceId = to.key as? Long ?: return@rememberReorderableLazyListState
                val fromSourceIndex = sourcesState.indexOfFirst { it.id == fromSourceId }
                val toSourceIndex = sourcesState.indexOfFirst { it.id == toSourceId }
                if (fromSourceIndex == -1 || toSourceIndex == -1) return@rememberReorderableLazyListState

                val item = sourcesState.removeAt(fromSourceIndex)
                sourcesState.add(toSourceIndex.coerceIn(0, sourcesState.size), item)
                screenModel.setSourceOrder(sourcesState.map { it.id })
            }

            LaunchedEffect(state.orderedSources) {
                if (!reorderableState.isAnyItemDragging) {
                    sourcesState.clear()
                    sourcesState.addAll(state.orderedSources)
                }
            }

            LazyColumn(
                state = lazyListState,
                contentPadding = contentPadding,
            ) {
                // KMK --> v0.7.12: reorganized sections per Phase 6/7 plan
                // KMK --> v0.7.14: removed redundant daily_recs_header (was immediately followed by lang_header)
                // Daily recommendations — language controls what sources appear in For You
                item(key = "lang_header") {
                    SectionHeader(stringResource(KMR.strings.rec_settings_daily_recs_header))
                }
                item(key = "lang_content") {
                    LanguageSelectorContent(
                        selectedLanguages = state.recommendationLanguages,
                        availableLanguages = state.availableLanguages,
                        onToggle = screenModel::toggleRecommendationLanguage,
                    )
                }

                // Ratings and known manga
                item(key = "rated_header") {
                    SectionHeader(stringResource(KMR.strings.rec_settings_ratings_known_manga_header))
                }
                item(key = "rated_content") {
                    RatedVisibilityContent(
                        current = state.ratedMangaVisibility,
                        onSelect = screenModel::setRatedMangaVisibility,
                    )
                }
                item(key = "hide_known_manga") {
                    HideKnownMangaRow(
                        enabled = state.hideKnownManga,
                        onToggle = { screenModel.setHideKnownManga(!state.hideKnownManga) },
                    )
                }
                // KMK --> v0.7.26: minimum chapter count filter
                item(key = "min_chapter_count") {
                    val offLabel = stringResource(KMR.strings.rec_min_chapter_count_off)
                    SameMangaListPrefRow(
                        title = stringResource(KMR.strings.rec_min_chapter_count),
                        summary = stringResource(KMR.strings.rec_min_chapter_count_summary),
                        current = state.minChapterCount,
                        options = listOf(0, 5, 10, 20, 50),
                        valueLabel = { if (it == 0) offLabel else "$it" },
                        onSelect = screenModel::setMinChapterCount,
                    )
                }
                // KMK <--
                // KMK --> v0.7.14: compact cache refresh hint
                item(key = "refresh_hint") {
                    Text(
                        text = stringResource(KMR.strings.rec_settings_refresh_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.extraSmall,
                        ),
                    )
                }
                // KMK <--

                // Tags
                item(key = "tag_header") {
                    SectionHeader(stringResource(KMR.strings.taste_settings_tag_prefs))
                }
                item(key = "tag_content") {
                    TagPreferencesContent(
                        tags = state.tagPreferences,
                        onAddClicked = screenModel::openAddTagDialog,
                        onEditClicked = screenModel::openEditTagDialog,
                        onDeleteClicked = screenModel::removeTagPreference,
                    )
                }
                // KMK <--

                // Source priority + enable/disable section
                item(key = "source_header") {
                    SectionHeader(stringResource(KMR.strings.rec_source_priority))
                }
                item(key = "source_summary") {
                    Text(
                        text = stringResource(KMR.strings.rec_source_languages_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.extraSmall,
                        ),
                    )
                }
                // KMK -->
                item(key = "source_status_note") {
                    Text(
                        text = stringResource(KMR.strings.rec_source_status_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.extraSmall,
                        ),
                    )
                }
                // KMK <--
                items(
                    count = sourcesState.size,
                    key = { sourcesState[it].id },
                ) { index ->
                    val source = sourcesState[index]
                    // KMK -->
                    val installedKey = RecommendationSourcePreferenceStore.installedKey(source.id)
                    val isLiked = installedKey in state.likedSourceKeys
                    val isDisliked = installedKey in state.dislikedSourceKeys
                    // KMK <--
                    ReorderableItem(reorderableState, source.id) {
                        SourcePriorityItem(
                            source = source,
                            rank = index + 1,
                            isBoosted = source.id in state.boostedSourceIds,
                            enabled = source.id !in state.disabledSourceIds,
                            status = state.sourceStatuses[source.id],
                            // KMK --> v0.7.19: rolling fit stats for this source
                            fitStats = state.sourceFitStats[source.id],
                            // KMK <--
                            onToggle = { screenModel.toggleSource(source.id) },
                            // KMK -->
                            isLiked = isLiked,
                            isDisliked = isDisliked,
                            onLike = {
                                screenModel.setInstalledSourcePreference(
                                    source.id,
                                    if (isLiked) RecommendationSourcePreference.NEUTRAL else RecommendationSourcePreference.LIKE,
                                )
                            },
                            onDislike = {
                                screenModel.setInstalledSourcePreference(
                                    source.id,
                                    if (isDisliked) RecommendationSourcePreference.NEUTRAL else RecommendationSourcePreference.DISLIKE,
                                )
                            },
                            // KMK <--
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                // KMK --> v0.6.14: restore default source order button (disabled while dragging)
                item(key = "source_reset_button") {
                    TextButton(
                        onClick = screenModel::requestResetSourceOrder,
                        enabled = !reorderableState.isAnyItemDragging,
                        modifier = Modifier.padding(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.extraSmall,
                        ),
                    ) {
                        Text(stringResource(KMR.strings.rec_restore_default_source_order))
                    }
                }
                // KMK <--
                // KMK --> v0.7.19: suggest priority order based on rolling fit stats
                if (state.suggestFitOrderAvailable) {
                    item(key = "source_suggest_order_button") {
                        TextButton(
                            onClick = screenModel::applyFitSuggestedOrder,
                            enabled = !reorderableState.isAnyItemDragging,
                            modifier = Modifier.padding(
                                horizontal = MaterialTheme.padding.medium,
                                vertical = MaterialTheme.padding.extraSmall,
                            ),
                        ) {
                            Text(stringResource(KMR.strings.rec_suggest_source_order_button))
                        }
                    }
                    item(key = "source_suggest_order_note") {
                        Text(
                            text = stringResource(KMR.strings.rec_suggest_source_order_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = MaterialTheme.padding.medium,
                                vertical = MaterialTheme.padding.extraSmall,
                            ),
                        )
                    }
                }
                // KMK <--

                // KMK --> v0.7.12: same-manga matching moved before source status (Phase 6/7)
                item(key = "same_manga_header") {
                    SectionHeader(stringResource(KMR.strings.same_manga_matching_settings_header))
                }
                item(key = "same_manga_results_per_source") {
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
                item(key = "best_version_sample_size") {
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
                // KMK <--

                // KMK --> v0.6.20: source status display order section — shows disliked sources last
                if (state.sourceStatuses.isNotEmpty()) {
                    item(key = "status_order_header") {
                        // KMK --> v0.7.12: cleaner header string
                        SectionHeader(stringResource(KMR.strings.rec_settings_source_status_section_header))
                        // KMK <--
                    }
                    val displayInputs = sourcesState.mapIndexed { idx, src ->
                        val installedKey = exh.recs.sourceprefs.RecommendationSourcePreferenceStore.installedKey(src.id)
                        exh.recs.SourceDisplayOrderInput(
                            sourceId = src.id,
                            priorityIndex = idx,
                            hasMatches = state.sourceStatuses[src.id]?.status == exh.recs.RecommendationSourceStatus.Shown,
                            isDisliked = installedKey in state.dislikedSourceKeys,
                        )
                    }
                    val sortedInputs = exh.recs.SourceStatusDisplayOrder.sort(displayInputs)

                    // Render each source with a group label before each new group
                    val groupedRows: List<Pair<exh.recs.SourceStatusDisplayOrder.Group?, CatalogueSource?>> = buildList {
                        var lastGroup: exh.recs.SourceStatusDisplayOrder.Group? = null
                        for (input in sortedInputs) {
                            val group = exh.recs.SourceStatusDisplayOrder.group(input)
                            if (group != lastGroup) {
                                lastGroup = group
                                add(group to null) // group header marker
                            }
                            val src = sourcesState.find { it.id == input.sourceId }
                            add(null to src)
                        }
                    }

                    groupedRows.forEachIndexed { i, (group, src) ->
                        if (group != null) {
                            item(key = "status_group_${group.name}") {
                                val label = when (group) {
                                    exh.recs.SourceStatusDisplayOrder.Group.HAS_MATCHES ->
                                        stringResource(KMR.strings.source_status_group_matches)
                                    exh.recs.SourceStatusDisplayOrder.Group.NO_MATCHES ->
                                        stringResource(KMR.strings.source_status_group_no_matches)
                                    exh.recs.SourceStatusDisplayOrder.Group.DISLIKED ->
                                        stringResource(KMR.strings.source_status_group_disliked)
                                }
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(
                                        horizontal = MaterialTheme.padding.medium,
                                        vertical = MaterialTheme.padding.extraSmall,
                                    ),
                                )
                            }
                        } else if (src != null) {
                            item(key = "status_src_${src.id}_$i") {
                                Text(
                                    text = src.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(
                                        horizontal = MaterialTheme.padding.medium + MaterialTheme.padding.small,
                                        vertical = MaterialTheme.padding.extraSmall,
                                    ),
                                )
                            }
                        }
                    }
                }
                // KMK <--

                // KMK -->
                // KMK --> v0.7.12: Management section — source discovery + cleanup actions (Phase 6/7)
                item(key = "management_header") {
                    SectionHeader(stringResource(KMR.strings.rec_settings_management_header))
                }
                // KMK --> v0.7.27: Best Version history entry point
                item(key = "quality_signal_history_entry") {
                    TextButton(
                        onClick = { navigator.push(QualitySignalHistoryScreen()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MaterialTheme.padding.medium),
                    ) {
                        Text(stringResource(KMR.strings.quality_signal_history_open_button))
                    }
                }
                // KMK <--
                // KMK <--
                // Sources To Try section
                item(key = "sources_to_try_header") {
                    SectionHeader(stringResource(KMR.strings.rec_sources_to_try_header))
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
                    val visibleSuggestions = if (state.suggestionsExpanded) {
                        state.nonInstalledSuggestions
                    } else {
                        state.nonInstalledSuggestions.take(5)
                    }
                    items(
                        count = visibleSuggestions.size,
                        key = { "suggestion_${visibleSuggestions[it].dismissalKey}" },
                    ) { index ->
                        val suggestion = visibleSuggestions[index]
                        // KMK -->
                        val availKey = RecommendationSourcePreferenceStore.availableKey(
                            suggestion.extension.signatureHash,
                            suggestion.extension.pkgName,
                            suggestion.source?.id,
                        )
                        val isSuggestionLiked = availKey in state.likedSourceKeys
                        val isSuggestionDisliked = availKey in state.dislikedSourceKeys
                        // KMK <--
                        SourceSuggestionItem(
                            suggestion = suggestion,
                            onInstall = { screenModel.installSuggestion(suggestion) },
                            onDismiss = { screenModel.dismissSuggestion(suggestion) },
                            // KMK -->
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
                            // KMK <--
                            modifier = Modifier.animateItem(),
                        )
                    }
                    // KMK --> v0.7.14: extracted hardcoded expand toggle strings to KMR
                    if (state.nonInstalledSuggestions.size > 5) {
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
                    // KMK <--
                    // KMK -->
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
                    // KMK --> v0.7.0: Phase 1 – clear dismissed suggestions
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
                    // KMK <--
                    // KMK <--
                }

                // KMK -->
                // Source Evaluation entry point
                // KMK --> v0.7.12: marked experimental per Phase 6/7 plan
                item(key = "source_eval_header") {
                    SectionHeader(stringResource(KMR.strings.source_evaluation_settings_experimental_header))
                }
                // KMK <--
                item(key = "source_eval_entry") {
                    TextButton(
                        onClick = { navigator.push(SourceEvaluationScreen()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MaterialTheme.padding.medium),
                    ) {
                        Text(stringResource(KMR.strings.source_evaluation_open_button))
                    }
                }
                item(key = "source_eval_desc") {
                    Text(
                        text = stringResource(KMR.strings.source_evaluation_settings_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.extraSmall,
                        ),
                    )
                }
                // KMK <--
                // KMK <--
            }
        }

        // Dialogs
        when (val dialog = state.dialog) {
            RecommendationsSettingsScreenModel.Dialog.AddTag -> {
                TagPreferenceDialog(
                    initialName = "",
                    initialPreference = TagPreference.PREFER,
                    onDismiss = screenModel::dismissDialog,
                    onConfirm = { name, pref ->
                        screenModel.setTagPreference(name, pref)
                        screenModel.dismissDialog()
                    },
                )
            }
            is RecommendationsSettingsScreenModel.Dialog.EditTag -> {
                TagPreferenceDialog(
                    initialName = dialog.tag.displayName,
                    initialPreference = TagPreference.fromValue(dialog.tag.preference) ?: TagPreference.PREFER,
                    onDismiss = screenModel::dismissDialog,
                    onConfirm = { name, pref ->
                        screenModel.setTagPreference(name, pref)
                        screenModel.dismissDialog()
                    },
                )
            }
            null -> {}
        }

        // KMK --> v0.6.14: source order reset confirmation dialog
        if (state.showResetSourceOrderDialog) {
            AlertDialog(
                onDismissRequest = screenModel::dismissResetSourceOrderDialog,
                title = { Text(stringResource(KMR.strings.rec_reset_source_order_dialog_title)) },
                text = { Text(stringResource(KMR.strings.rec_reset_source_order_dialog_message)) },
                confirmButton = {
                    TextButton(onClick = screenModel::confirmResetSourceOrder) {
                        Text(stringResource(KMR.strings.rec_reset_source_order_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = screenModel::dismissResetSourceOrderDialog) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }
        // KMK <--
    }
}

@Composable
private fun ReorderableCollectionItemScope.SourcePriorityItem(
    source: CatalogueSource,
    rank: Int,
    isBoosted: Boolean,
    enabled: Boolean,
    // KMK -->
    status: RecommendationSourceRunStatus?,
    // KMK --> v0.7.19: rolling fit stats for badge display
    fitStats: SourceFitStats? = null,
    // KMK <--
    isLiked: Boolean,
    isDisliked: Boolean,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    // KMK <--
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.extraSmall),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.padding.small,
                    end = MaterialTheme.padding.medium,
                    top = MaterialTheme.padding.extraSmall,
                    bottom = MaterialTheme.padding.extraSmall,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.DragHandle,
                contentDescription = null,
                modifier = Modifier
                    .padding(MaterialTheme.padding.small)
                    .draggableHandle(),
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = source.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isBoosted) {
                        Badge(
                            modifier = Modifier.padding(start = MaterialTheme.padding.small),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Text(
                                text = stringResource(KMR.strings.rec_source_boosted),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    // KMK --> v0.7.0: Phase 6 – source fit label badge
                    // KMK --> v0.7.19: prefer rolling fit label over single-run status
                    if (!isDisliked && enabled) {
                        val rollingLabel: SourceFitLabel? = if (fitStats != null && fitStats.runCount > 0) fitStats.fitLabel else null
                        val fitLabel: String? = when {
                            rollingLabel != null -> when (rollingLabel) {
                                SourceFitLabel.GreatFit -> stringResource(KMR.strings.rec_source_fit_great)
                                SourceFitLabel.GoodFit -> stringResource(KMR.strings.rec_source_fit_good)
                                SourceFitLabel.Mixed -> stringResource(KMR.strings.rec_source_fit_mixed)
                                SourceFitLabel.NoMatchesRecently -> stringResource(KMR.strings.rec_source_fit_no_matches)
                                SourceFitLabel.OftenFiltered -> stringResource(KMR.strings.rec_source_fit_often_filtered)
                                SourceFitLabel.OftenErrors -> stringResource(KMR.strings.rec_source_fit_often_errors)
                                SourceFitLabel.TooLittleData -> null
                            }
                            status != null -> when {
                                status.status == RecommendationSourceStatus.Shown && status.visibleCount >= 5 -> stringResource(KMR.strings.rec_source_fit_great)
                                status.status == RecommendationSourceStatus.Shown && status.visibleCount >= 2 -> stringResource(KMR.strings.rec_source_fit_good)
                                status.status == RecommendationSourceStatus.Shown && status.visibleCount >= 1 -> stringResource(KMR.strings.rec_source_fit_low)
                                status.status == RecommendationSourceStatus.NoMatches -> stringResource(KMR.strings.rec_source_fit_no_matches)
                                status.status == RecommendationSourceStatus.FilteredOut -> stringResource(KMR.strings.rec_source_fit_often_filtered)
                                status.status == RecommendationSourceStatus.Error -> stringResource(KMR.strings.rec_source_fit_often_errors)
                                status.status == RecommendationSourceStatus.HiddenByDuplicateHandling -> stringResource(KMR.strings.rec_source_fit_deduplicated)
                                else -> null
                            }
                            else -> null
                        }
                        // KMK <--
                        val useRolling = rollingLabel != null
                        val fitContainerColor = when {
                            useRolling -> when (rollingLabel) {
                                SourceFitLabel.GreatFit -> MaterialTheme.colorScheme.primaryContainer
                                SourceFitLabel.GoodFit -> MaterialTheme.colorScheme.secondaryContainer
                                SourceFitLabel.OftenErrors -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                            status != null -> when {
                                status.status == RecommendationSourceStatus.Shown && status.visibleCount >= 5 -> MaterialTheme.colorScheme.primaryContainer
                                status.status == RecommendationSourceStatus.Shown && status.visibleCount >= 2 -> MaterialTheme.colorScheme.secondaryContainer
                                status.status == RecommendationSourceStatus.Shown && status.visibleCount >= 1 -> MaterialTheme.colorScheme.tertiaryContainer
                                status.status == RecommendationSourceStatus.Error -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        val fitContentColor = when {
                            useRolling -> when (rollingLabel) {
                                SourceFitLabel.GreatFit -> MaterialTheme.colorScheme.onPrimaryContainer
                                SourceFitLabel.GoodFit -> MaterialTheme.colorScheme.onSecondaryContainer
                                SourceFitLabel.OftenErrors -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            status != null -> when {
                                status.status == RecommendationSourceStatus.Shown && status.visibleCount >= 5 -> MaterialTheme.colorScheme.onPrimaryContainer
                                status.status == RecommendationSourceStatus.Shown && status.visibleCount >= 2 -> MaterialTheme.colorScheme.onSecondaryContainer
                                status.status == RecommendationSourceStatus.Shown && status.visibleCount >= 1 -> MaterialTheme.colorScheme.onTertiaryContainer
                                status.status == RecommendationSourceStatus.Error -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        if (fitLabel != null) {
                            Badge(
                                modifier = Modifier.padding(start = MaterialTheme.padding.small),
                                containerColor = fitContainerColor,
                                contentColor = fitContentColor,
                            ) {
                                Text(
                                    text = fitLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                    // KMK <--
                }
                // KMK -->
                val statusText = when {
                    isDisliked -> stringResource(KMR.strings.rec_source_status_disliked)
                    !enabled -> stringResource(KMR.strings.rec_source_status_disabled)
                    status == null -> stringResource(KMR.strings.rec_source_status_not_checked)
                    else -> when (status.status) {
                        RecommendationSourceStatus.Shown -> stringResource(KMR.strings.rec_source_status_shown, status.visibleCount)
                        RecommendationSourceStatus.NoMatches -> stringResource(KMR.strings.rec_source_status_no_matches)
                        RecommendationSourceStatus.FilteredOut -> stringResource(KMR.strings.rec_source_status_filtered)
                        RecommendationSourceStatus.Error -> stringResource(KMR.strings.rec_source_status_error)
                        RecommendationSourceStatus.Disabled -> stringResource(KMR.strings.rec_source_status_disabled)
                        RecommendationSourceStatus.OutsideAttemptLimit -> stringResource(KMR.strings.rec_source_status_not_searched_limit)
                        RecommendationSourceStatus.HiddenByDuplicateHandling -> stringResource(KMR.strings.rec_source_status_duplicate_hidden)
                    }
                }
                Text(
                    text = "${source.lang.uppercase()} · #$rank · $statusText",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDisliked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // KMK <--
            }
            // KMK -->
            IconButton(onClick = onLike) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                    contentDescription = stringResource(KMR.strings.rec_source_preference_like_for_you),
                    tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDislike) {
                Icon(
                    imageVector = if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                    contentDescription = stringResource(KMR.strings.rec_source_preference_dislike_for_you),
                    tint = if (isDisliked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // KMK <--
            Switch(
                checked = enabled,
                onCheckedChange = { onToggle() },
            )
        }
    }
}

@Composable
private fun HideKnownMangaRow(
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(KMR.strings.rec_hide_known_manga),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(KMR.strings.rec_hide_known_manga_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = { onToggle() },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RatedVisibilityContent(
    current: RatedMangaVisibility,
    onSelect: (RatedMangaVisibility) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium),
    ) {
        listOf(
            RatedMangaVisibility.HIDE_DISLIKED_ONLY to KMR.strings.taste_visibility_hide_disliked,
            RatedMangaVisibility.HIDE_ALL_RATED to KMR.strings.taste_visibility_hide_all,
            RatedMangaVisibility.SHOW_ALL_RATED to KMR.strings.taste_visibility_show_all,
        ).forEach { (option, labelRes) ->
            FilterChip(
                selected = current == option,
                onClick = { onSelect(option) },
                label = { Text(stringResource(labelRes)) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageSelectorContent(
    selectedLanguages: ImmutableSet<String>,
    availableLanguages: ImmutableList<String>,
    onToggle: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium),
    ) {
        availableLanguages.forEach { lang ->
            val selected = lang in selectedLanguages
            FilterChip(
                selected = selected,
                onClick = { onToggle(lang) },
                label = { Text(lang.uppercase()) },
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            horizontal = MaterialTheme.padding.medium,
            vertical = MaterialTheme.padding.small,
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagPreferencesContent(
    tags: List<TagTaste>,
    onAddClicked: () -> Unit,
    onEditClicked: (TagTaste) -> Unit,
    onDeleteClicked: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium),
    ) {
        if (tags.isEmpty()) {
            Text(
                text = stringResource(KMR.strings.taste_settings_tag_prefs_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = MaterialTheme.padding.small),
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                tags.forEach { tag ->
                    val pref = TagPreference.fromValue(tag.preference)
                    FilterChip(
                        selected = true,
                        onClick = { onEditClicked(tag) },
                        label = { Text(tag.displayName) },
                        leadingIcon = {
                            Icon(
                                imageVector = when (pref) {
                                    TagPreference.PREFER -> Icons.Outlined.Done
                                    TagPreference.DISLIKE -> Icons.Outlined.RemoveCircleOutline
                                    TagPreference.BLOCK -> Icons.Outlined.Block
                                    null -> Icons.Outlined.FavoriteBorder
                                },
                                contentDescription = null,
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { onDeleteClicked(tag.normalizedTag) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = null)
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = when (pref) {
                                TagPreference.PREFER -> MaterialTheme.colorScheme.primaryContainer
                                TagPreference.DISLIKE -> MaterialTheme.colorScheme.secondaryContainer
                                TagPreference.BLOCK -> MaterialTheme.colorScheme.errorContainer
                                null -> MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                    )
                }
            }
        }
        TextButton(
            onClick = onAddClicked,
            modifier = Modifier.padding(top = MaterialTheme.padding.extraSmall),
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = null,
                modifier = Modifier.padding(end = MaterialTheme.padding.extraSmall),
            )
            Text(stringResource(KMR.strings.taste_settings_add_tag))
        }
    }
}

// KMK -->
@Composable
private fun SourceSuggestionItem(
    suggestion: NonInstalledSourceSuggestion,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    isLiked: Boolean,
    isDisliked: Boolean,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    isInstalling: Boolean = false,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelected: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.extraSmall)
            .then(if (selectionMode) Modifier.clickable(onClick = onToggleSelected) else Modifier),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelected() },
                    modifier = Modifier.padding(start = MaterialTheme.padding.small),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = if (selectionMode) MaterialTheme.padding.extraSmall else MaterialTheme.padding.medium,
                        end = MaterialTheme.padding.medium,
                        top = MaterialTheme.padding.small,
                        bottom = MaterialTheme.padding.small,
                    ),
            ) {
                Text(
                    text = suggestion.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                )
                val confidenceLabel = when (suggestion.confidence) {
                    SuggestionConfidence.LOW -> stringResource(KMR.strings.rec_suggestion_confidence_low)
                    SuggestionConfidence.MEDIUM -> stringResource(KMR.strings.rec_suggestion_confidence_medium)
                }
                Text(
                    text = buildString {
                        append(suggestion.displayLang.uppercase())
                        if (suggestion.displayRepoName.isNotEmpty()) append(" · ${suggestion.displayRepoName}")
                        append(" · $confidenceLabel")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val reasonTexts = suggestion.reasons.mapNotNull { reason ->
                    when (reason) {
                        is NonInstalledSuggestionReason.LanguageMatch -> stringResource(KMR.strings.rec_suggestion_reason_language_match)
                        is NonInstalledSuggestionReason.SameRepoAsInstalledSources -> stringResource(KMR.strings.rec_suggestion_reason_same_repo)
                        is NonInstalledSuggestionReason.SimilarToInstalledSource -> stringResource(KMR.strings.rec_suggestion_reason_similar_source)
                        NonInstalledSuggestionReason.NeedsTesting -> stringResource(KMR.strings.rec_suggestion_reason_needs_testing)
                        NonInstalledSuggestionReason.UserLikedSource -> stringResource(KMR.strings.rec_suggestion_reason_user_liked)
                        // KMK -->
                        NonInstalledSuggestionReason.EvaluatedStrongFit -> stringResource(KMR.strings.rec_suggestion_reason_evaluated_strong_fit)
                        NonInstalledSuggestionReason.EvaluatedWorthTrying -> stringResource(KMR.strings.rec_suggestion_reason_evaluated_worth_trying)
                        NonInstalledSuggestionReason.EvaluatedExplicitHeavy -> null
                        NonInstalledSuggestionReason.EvaluatedEcchiHeavy -> null
                        // KMK <--
                    }
                }.distinct()
                if (reasonTexts.isNotEmpty()) {
                    Text(
                        text = reasonTexts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = MaterialTheme.padding.extraSmall),
                    )
                }
                Row(
                    modifier = Modifier.padding(top = MaterialTheme.padding.extraSmall),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    if (!selectionMode) {
                        Button(onClick = onInstall, enabled = !isInstalling) {
                            Text(stringResource(KMR.strings.rec_suggestion_install))
                        }
                        OutlinedButton(onClick = onDismiss) {
                            Text(stringResource(KMR.strings.rec_suggestion_dismiss))
                        }
                    }
                    IconButton(onClick = onLike) {
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = stringResource(KMR.strings.rec_source_preference_like_source),
                            tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDislike) {
                        Icon(
                            imageVector = if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                            contentDescription = stringResource(KMR.strings.rec_source_preference_dislike_source),
                            tint = if (isDisliked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
// KMK <--

@Composable
private fun TagPreferenceDialog(
    initialName: String,
    initialPreference: TagPreference,
    onDismiss: () -> Unit,
    onConfirm: (String, TagPreference) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var preference by remember { mutableStateOf(initialPreference) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(KMR.strings.taste_settings_tag_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(KMR.strings.taste_settings_tag_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    listOf(TagPreference.PREFER, TagPreference.DISLIKE, TagPreference.BLOCK).forEach { pref ->
                        FilterChip(
                            selected = preference == pref,
                            onClick = { preference = pref },
                            label = {
                                Text(
                                    stringResource(
                                        when (pref) {
                                            TagPreference.PREFER -> KMR.strings.taste_pref_prefer
                                            TagPreference.DISLIKE -> KMR.strings.taste_pref_dislike
                                            TagPreference.BLOCK -> KMR.strings.taste_pref_block
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, preference) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

// KMK --> v0.7.8: same-manga matching setting composables
@Composable
private fun SameMangaListPrefRow(
    title: String,
    summary: String,
    current: Int,
    options: List<Int>,
    onSelect: (Int) -> Unit,
    // KMK --> v0.7.26: optional display label override (e.g. "Off" for 0)
    valueLabel: (Int) -> String = { "$it" },
    // KMK <--
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "${valueLabel(current)} — $summary",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { value ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(valueLabel(value)) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                    trailingIcon = if (value == current) {
                        (
                            {
                                Icon(Icons.Outlined.Done, contentDescription = null)
                            }
                            )
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun SameMangaSwitchRow(
    title: String,
    summary: String,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = MaterialTheme.padding.medium)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = { onToggle() })
    }
}
// KMK <--

// KMK <--

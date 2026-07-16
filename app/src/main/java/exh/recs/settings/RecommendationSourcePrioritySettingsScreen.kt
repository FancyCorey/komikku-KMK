package exh.recs.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.CatalogueSource
import exh.recs.RecommendationSourceStatus
import exh.recs.SourceDisplayOrderInput
import exh.recs.SourceStatusDisplayOrder
import exh.recs.sourceprefs.RecommendationSourcePreference
import exh.recs.sourceprefs.RecommendationSourcePreferenceStore
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

// KMK v0.8.8 -->
/**
 * "Source Priority" detail screen — extracted verbatim: the drag-and-drop reorderable source list,
 * restore-default/suggest-order actions, the source-status display-order breakdown, and (kept
 * adjacent since neither is one of the plan's seven named categories and both were already
 * physically adjacent in the original screen, immediately following the source list) Same-Manga
 * Matching and Best Version preview settings. Pure move: every control, `screenModel` method, and
 * preference read/write is byte-for-byte identical to before.
 */
class RecommendationSourcePrioritySettingsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { RecommendationsSettingsScreenModel() }
        val state by screenModel.state.collectAsState()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.rec_settings_index_source_priority),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
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
                item(key = "source_header") {
                    val counts = RecommendationSettingsSectionSummaries.sourcePriorityCounts(
                        state.orderedSources.map { it.id to it.name },
                        state.disabledSourceIds,
                    )
                    val sourceSummary = if (counts.topSourceName == null) {
                        stringResource(KMR.strings.rec_settings_summary_source_priority_none)
                    } else {
                        stringResource(KMR.strings.rec_settings_summary_source_priority, counts.enabledCount, counts.topSourceName)
                    }
                    SectionHeader(stringResource(KMR.strings.rec_source_priority), summary = sourceSummary)
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
                items(
                    count = sourcesState.size,
                    key = { sourcesState[it].id },
                ) { index ->
                    val source = sourcesState[index]
                    val installedKey = RecommendationSourcePreferenceStore.installedKey(source.id)
                    val isLiked = installedKey in state.likedSourceKeys
                    val isDisliked = installedKey in state.dislikedSourceKeys
                    ReorderableItem(reorderableState, source.id) {
                        SourcePriorityItem(
                            source = source,
                            rank = index + 1,
                            isBoosted = source.id in state.boostedSourceIds,
                            enabled = source.id !in state.disabledSourceIds,
                            status = state.sourceStatuses[source.id],
                            fitStats = state.sourceFitStats[source.id],
                            onToggle = { screenModel.toggleSource(source.id) },
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
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

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

                item(key = "same_manga_header") {
                    val onOffLabel = if (state.sameMangaPreselectResults) {
                        stringResource(MR.strings.on)
                    } else {
                        stringResource(MR.strings.off)
                    }
                    SectionHeader(
                        stringResource(KMR.strings.same_manga_matching_settings_header),
                        summary = stringResource(KMR.strings.rec_settings_summary_same_manga_matching, state.sameMangaResultsPerSource, onOffLabel),
                    )
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

                if (state.sourceStatuses.isNotEmpty()) {
                    item(key = "status_order_header") {
                        SectionHeader(stringResource(KMR.strings.rec_settings_source_status_section_header))
                    }
                    val displayInputs = sourcesState.mapIndexed { idx, src ->
                        val installedKey = RecommendationSourcePreferenceStore.installedKey(src.id)
                        SourceDisplayOrderInput(
                            sourceId = src.id,
                            priorityIndex = idx,
                            hasMatches = state.sourceStatuses[src.id]?.status == RecommendationSourceStatus.Shown,
                            isDisliked = installedKey in state.dislikedSourceKeys,
                        )
                    }
                    val sortedInputs = SourceStatusDisplayOrder.sort(displayInputs)

                    val groupedRows: List<Pair<SourceStatusDisplayOrder.Group?, CatalogueSource?>> = buildList {
                        var lastGroup: SourceStatusDisplayOrder.Group? = null
                        for (input in sortedInputs) {
                            val group = SourceStatusDisplayOrder.group(input)
                            if (group != lastGroup) {
                                lastGroup = group
                                add(group to null)
                            }
                            val src = sourcesState.find { it.id == input.sourceId }
                            add(null to src)
                        }
                    }

                    groupedRows.forEachIndexed { i, (group, src) ->
                        if (group != null) {
                            item(key = "status_group_${group.name}") {
                                val label = when (group) {
                                    SourceStatusDisplayOrder.Group.HAS_MATCHES ->
                                        stringResource(KMR.strings.source_status_group_matches)
                                    SourceStatusDisplayOrder.Group.NO_MATCHES ->
                                        stringResource(KMR.strings.source_status_group_no_matches)
                                    SourceStatusDisplayOrder.Group.DISLIKED ->
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
            }
        }

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
    }
}
// KMK <--

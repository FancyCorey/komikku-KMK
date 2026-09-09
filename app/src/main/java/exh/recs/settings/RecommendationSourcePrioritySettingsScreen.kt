package exh.recs.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberNavigatorScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.Source
import exh.recs.RecommendationSourceStatus
import exh.recs.SourceDisplayOrderInput
import exh.recs.SourceStatusDisplayOrder
import exh.recs.sourceprefs.RecommendationSourcePreference
import exh.recs.sourceprefs.RecommendationSourcePreferenceStore
import exh.util.EvaluationModeFormatter
import exh.util.rememberEvaluationModeEnabled
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

// KMK v0.8.8 -->
/**
 * "For You sources" detail screen: the drag-and-drop reorderable source list, restore-default/
 * suggest-order actions, source preference marks, source status, and a read-only For You preview.
 *
 * KMK v0.8.12: Same Manga Matching and Best Version preview controls moved out to
 * [RecommendationMatchingVersionsSettingsScreen] -- they are cross-source matching/version controls,
 * not source-ordering controls, and their previous placement here was only ever physical adjacency
 * left over from the single-page settings screen this was extracted from, not a real ownership
 * decision. Source Priority now contains only source ordering, enabled/disabled state, source
 * preference marks, and source status -- see the v0.8.12 implementation report.
 *
 * KMK v0.8.14: renamed from "Source priority" to "Sources and languages" as part of the approved
 * five-section Recommendation Settings structure.
 *
 * KMK v0.8.14-fix1: renamed again, this time to "For You sources" -- live-device review confirmed
 * language selection does not belong bundled with source priority; it affects the whole recommendation
 * system, not just source ordering. `LanguageSelectorContent` and its anchors moved to
 * [RecommendationDiagnosticsSettingsScreen] (see that class's doc). This screen is now source-scope
 * only: order, enable/disable, like/dislike, status, and the read-only For You preview -- see
 * [RecommendationForYouPreviewSnapshotStore]. The preview action moved near the top of the screen
 * (right after the header) instead of after every source-order action, and it now renders actual manga
 * covers/titles from the last successful For You refresh instead of only source order/status.
 */
class RecommendationSourcePrioritySettingsScreen(
    // KMK v0.8.10: see former RecommendationForYouSettingsScreen.anchor.
    val anchor: String? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val openForYou = rememberOpenForYouFromRecommendationSettings(navigator)
        // KMK EC-04 2026-09-04: shared across all four Recommendation Settings destination screens
        // via the enclosing Navigator instead of rememberScreenModel's per-Screen scoping, so the
        // ScreenModel's eager init (source scan/filter/order, language processing, several
        // preference-store parses) runs once per navigator lifetime rather than on every navigation
        // between these sibling screens -- see RecommendationsSettingsScreenModel's shared class doc.
        val screenModel = navigator.rememberNavigatorScreenModel { RecommendationsSettingsScreenModel() }
        val state by screenModel.state.collectAsState()
        // KMK v0.8.14-fix1: read-only For You preview -- see RecommendationForYouPreviewSnapshotStore.
        var showForYouPreview by rememberSaveable { mutableStateOf(false) }

        // KMK v0.8.18-fix1: the right-edge quick-access panel was removed from this and every other
        // Recommendation Settings detail screen -- see exh.recs.settings shared components for its new
        // home (For You / Loved / Liked / Disliked only).
        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.rec_settings_index_for_you_sources),
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
            // KMK v0.8.17-fix1: quick-access row consumes the app bar's top inset; the LazyColumn
            // below keeps every other inset (start/end/bottom) unchanged, so nothing double-pads.
            val layoutDirection = LocalLayoutDirection.current
            val listContentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
                bottom = contentPadding.calculateBottomPadding(),
            )
            val lazyListState = rememberLazyListState()
            val sourcesState = remember { state.orderedSources.toMutableStateList() }
            fun moveSource(fromIndex: Int, toIndex: Int) {
                if (fromIndex !in sourcesState.indices || toIndex !in sourcesState.indices || fromIndex == toIndex) return
                val item = sourcesState.removeAt(fromIndex)
                sourcesState.add(toIndex, item)
                screenModel.setSourceOrder(sourcesState.map { it.id })
            }
            // KMK v0.8.10: mirrors the LazyColumn's item order below, including its conditional
            // sections and the dynamic per-source row count, so a static control key placed after
            // the reorderable source list still resolves to the correct scroll index. The dynamic
            // source rows and the status-breakdown section (last, nothing anchors past it) use
            // placeholder keys -- only the static control keys below are ever used as search anchors.
            val itemKeysInOrder = remember(sourcesState.size, state.suggestFitOrderAvailable) {
                buildList {
                    add("preview_for_you")
                    add("source_header")
                    add("source_status_note")
                    repeat(sourcesState.size) { add("source_row_$it") }
                    add("source_reset_button")
                    if (state.suggestFitOrderAvailable) {
                        add("source_suggest_order_button")
                        add("source_suggest_order_note")
                    }
                }
            }
            ScrollToAnchorEffect(lazyListState, itemKeysInOrder, anchor)
            val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                val fromSourceId = from.key as? Long ?: return@rememberReorderableLazyListState
                val toSourceId = to.key as? Long ?: return@rememberReorderableLazyListState
                val fromSourceIndex = sourcesState.indexOfFirst { it.id == fromSourceId }
                val toSourceIndex = sourcesState.indexOfFirst { it.id == toSourceId }
                if (fromSourceIndex == -1 || toSourceIndex == -1) return@rememberReorderableLazyListState

                moveSource(fromSourceIndex, toSourceIndex.coerceIn(0, sourcesState.lastIndex))
            }

            LaunchedEffect(state.orderedSources) {
                if (!reorderableState.isAnyItemDragging) {
                    sourcesState.clear()
                    sourcesState.addAll(state.orderedSources)
                }
            }

            Column(modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
                RecommendationSettingsQuickAccessRow(
                    current = RecommendationSettingsQuickAccessDestination.ForYouSources,
                    onNavigate = { destination -> navigator.replace(destination.toScreen()) },
                )
                LazyColumn(
                    state = lazyListState,
                    contentPadding = listContentPadding,
                    modifier = Modifier.weight(1f),
                ) {
                    // KMK v0.8.14-fix1: preview action moved near the top of the screen, right after the
                    // header, instead of after every source-order action -- see the class doc.
                    item(key = "preview_for_you") {
                        TextPreferenceWidget(
                            title = stringResource(KMR.strings.rec_preview_for_you_title),
                            subtitle = stringResource(KMR.strings.rec_preview_for_you_summary),
                            onPreferenceClick = { showForYouPreview = true },
                        )
                    }
                    // The Latest-catalogue
                    // exploration share lives on this screen because it governs how For You uses its
                    // *sources*, which is exactly what this destination already owns. Enablement and
                    // the bounded value are separate so disabling preserves the last chosen value.
                    item(key = "latest_exploration") {
                        BoundedIntPreferenceRow(
                            title = stringResource(KMR.strings.rec_latest_exploration),
                            summary = stringResource(KMR.strings.rec_latest_exploration_summary),
                            valueTitle = stringResource(KMR.strings.rec_latest_exploration_value),
                            current = exh.recs.RecommendationLatestBudgetPolicy.validate(state.latestExplorationPercent),
                            enabled = state.latestExplorationEnabled,
                            min = exh.recs.RecommendationLatestBudgetPolicy.MIN_PERCENT,
                            max = exh.recs.RecommendationLatestBudgetPolicy.MAX_PERCENT,
                            valueLabel = { percent -> stringResource(KMR.strings.rec_latest_exploration_percent, percent) },
                            onEnabledChange = screenModel::setLatestExplorationEnabled,
                            onValueChange = screenModel::setLatestExplorationPercent,
                        )
                    }
                    // How long a
                    // repeatedly-shown, untouched title keeps its soft ordering penalty. Placed next
                    // to the Latest control since both govern how For You composes a source's row.
                    item(key = "exposure_window") {
                        BoundedIntPreferenceRow(
                            title = stringResource(KMR.strings.rec_exposure_window),
                            summary = stringResource(KMR.strings.rec_exposure_window_summary),
                            valueTitle = stringResource(KMR.strings.rec_exposure_window_value),
                            current = exh.recs.RecommendationExposurePolicy.validateWindowDays(state.exposureWindowDays),
                            enabled = state.exposureWindowEnabled,
                            min = exh.recs.RecommendationExposurePolicy.MIN_WINDOW_DAYS,
                            max = exh.recs.RecommendationExposurePolicy.MAX_WINDOW_DAYS,
                            valueLabel = { days -> pluralStringResource(KMR.plurals.rec_exposure_window_days, count = days, days) },
                            onEnabledChange = screenModel::setExposureWindowEnabled,
                            onValueChange = screenModel::setExposureWindowDays,
                        )
                    }
                    // User-facing clear action for the
                    // local repeat/exposure history, gated behind an explicit confirmation. The dialog
                    // states plainly that only ordering history is removed -- ratings, library, and
                    // tracking are untouched, which matches what clearExposureHistoryNow() actually does.
                    item(key = "exposure_clear") {
                        var showClearExposureDialog by remember { mutableStateOf(false) }
                        TextPreferenceWidget(
                            title = stringResource(KMR.strings.rec_exposure_clear),
                            subtitle = stringResource(KMR.strings.rec_exposure_clear_summary),
                            onPreferenceClick = { showClearExposureDialog = true },
                        )
                        if (showClearExposureDialog) {
                            AlertDialog(
                                onDismissRequest = { showClearExposureDialog = false },
                                title = { Text(stringResource(KMR.strings.rec_exposure_clear)) },
                                text = { Text(stringResource(KMR.strings.rec_exposure_clear_confirm)) },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            showClearExposureDialog = false
                                            screenModel.clearExposureHistory()
                                        },
                                    ) {
                                        Text(stringResource(MR.strings.action_ok))
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showClearExposureDialog = false }) {
                                        Text(stringResource(MR.strings.action_cancel))
                                    }
                                },
                            )
                        }
                    }
                    item(key = "source_header") {
                        // KMK Confirmed Blocker Remediation 2026-07-28: single pure call replaces the
                        // former inline Evaluation Mode branch -- see
                        // RecommendationSettingsSectionSummaries.sourcePrioritySummary's doc for why
                        // this is the one place this decision is made, instead of being
                        // re-implemented at each summary call site.
                        val summary = RecommendationSettingsSectionSummaries.sourcePrioritySummary(
                            state.orderedSources.map { it.id to it.name },
                            state.disabledSourceIds,
                            rememberEvaluationModeEnabled(),
                        )
                        val sourceSummary = if (summary.topSourceLabel == null) {
                            stringResource(KMR.strings.rec_settings_summary_source_priority_none)
                        } else {
                            stringResource(KMR.strings.rec_settings_summary_source_priority, summary.enabledCount, summary.topSourceLabel)
                        }
                        SectionHeader(stringResource(KMR.strings.rec_source_priority), summary = sourceSummary)
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
                                onMoveUp = if (index > 0) {
                                    { moveSource(index, index - 1) }
                                } else {
                                    null
                                },
                                onMoveDown = if (index < sourcesState.lastIndex) {
                                    { moveSource(index, index + 1) }
                                } else {
                                    null
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    item(key = "source_reset_button") {
                        // KMK v0.8.11: official TextPreferenceWidget action row. The reset action is
                        // disabled while dragging by omitting onPreferenceClick, matching the previous
                        // enabled=false behavior.
                        TextPreferenceWidget(
                            title = stringResource(KMR.strings.rec_restore_default_source_order),
                            onPreferenceClick = if (reorderableState.isAnyItemDragging) null else screenModel::requestResetSourceOrder,
                        )
                    }
                    if (state.suggestFitOrderAvailable) {
                        item(key = "source_suggest_order_button") {
                            TextPreferenceWidget(
                                title = stringResource(KMR.strings.rec_suggest_source_order_button),
                                onPreferenceClick = if (reorderableState.isAnyItemDragging) null else screenModel::applyFitSuggestedOrder,
                            )
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

                    // KMK v0.8.12: Same Manga Matching and Best Version preview sections were here --
                    // moved to RecommendationMatchingVersionsSettingsScreen (later retired, controls now
                    // in RecommendationDiagnosticsSettingsScreen). See the class doc above.

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

                        val groupedRows: List<Pair<SourceStatusDisplayOrder.Group?, Source?>> = buildList {
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
                                    // KMK --> v0.8.19: evaluation mode source-name obfuscation
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                horizontal = MaterialTheme.padding.medium + MaterialTheme.padding.small,
                                                vertical = MaterialTheme.padding.extraSmall,
                                            ),
                                    ) {
                                        Text(
                                            text = if (rememberEvaluationModeEnabled()) {
                                                EvaluationModeFormatter.sourceLabel(src.id)
                                            } else {
                                                src.name
                                            },
                                            // KMK <--
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        state.sourceStatuses[src.id]?.evaluatedCount
                                            ?.takeIf { it > 0 }
                                            ?.let { count ->
                                                Text(
                                                    text = stringResource(
                                                        KMR.strings.rec_for_you_source_evaluated_candidates,
                                                        count,
                                                    ),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                    }
                                }
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

        if (showForYouPreview) {
            ForYouSnapshotPreviewDialog(
                snapshot = state.forYouPreviewSnapshot,
                onDismiss = { showForYouPreview = false },
            )
        }
    }
}

// KMK v0.8.14-fix1 -->
/**
 * Read-only "Preview For You" full-screen dialog -- a real manga snapshot (Top Picks + visible source
 * rows, with covers and titles) from the last successful For You refresh, replacing the v0.8.13-fix1
 * source-order/status-only preview. Built entirely from [snapshot], already loaded by
 * [RecommendationsSettingsScreenModel] from [RecommendationForYouPreviewSnapshotStore]. Never fetches
 * manga, runs a search, calls a source client, or installs/uninstalls anything -- every
 * [eu.kanade.presentation.manga.components.MangaCover] below omits `onClick`, so tapping a card is a
 * no-op, and no row here is clickable to open a manga or source.
 *
 * KMK v0.8.16-fix1: replaced the compact `AlertDialog` (`LazyColumn(heightIn(max = 420.dp))`) with a
 * full-screen `Dialog` + `Scaffold`/`AppBar`, matching how [exh.recs.bestversion.BestVersionCompareScreen]'s
 * fullscreen dialogs are built. The compact dialog was too small to represent the real For You layout.
 * The close action is top-left (`AppBar`'s standard
 * navigation-icon position), matching every other Komikku/KMK top app bar in this app, rather than a
 * top-right icon that would be the only top-right close affordance in the app.
 */
@Composable
private fun ForYouSnapshotPreviewDialog(
    snapshot: RecommendationForYouPreviewSnapshot?,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.rec_preview_for_you_dialog_title),
                    navigateUp = onDismiss,
                    navigationIcon = Icons.Filled.Close,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            Column(modifier = Modifier.padding(contentPadding).fillMaxWidth()) {
                Text(
                    text = stringResource(KMR.strings.rec_preview_for_you_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
                )
                if (snapshot == null || snapshot.rows.isEmpty()) {
                    Text(
                        text = stringResource(KMR.strings.rec_preview_for_you_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.medium),
                    )
                } else {
                    // KMK v0.8.16-fix1: no height cap -- fills the rest of the full-screen dialog and
                    // scrolls normally, unlike the old 420dp-capped AlertDialog list.
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(items = snapshot.rows, key = { it.rowKey }) { row ->
                            ForYouSnapshotPreviewRow(row)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ForYouSnapshotPreviewRow(row: RecommendationForYouPreviewRow) {
    Column(modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small)) {
        Text(
            text = if (row.rowType == RecommendationForYouPreviewRowType.TOP_PICKS) {
                stringResource(KMR.strings.rec_top_picks_title)
            } else {
                row.title
            },
            style = MaterialTheme.typography.titleSmall,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            modifier = Modifier.padding(top = MaterialTheme.padding.extraSmall),
        ) {
            items(items = row.mangas, key = { it.mangaId }) { manga ->
                ForYouSnapshotPreviewCard(manga)
            }
        }
    }
}

@Composable
private fun ForYouSnapshotPreviewCard(manga: RecommendationForYouPreviewManga) {
    Column(modifier = Modifier.width(96.dp)) {
        // KMK v0.8.14-fix1: no `onClick` -- tapping a preview card does nothing, by design.
        MangaCover.Book(
            data = manga.thumbnailUrl,
            contentDescription = manga.title,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = manga.title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = MaterialTheme.padding.extraSmall),
        )
    }
}
// KMK <--
// KMK <--

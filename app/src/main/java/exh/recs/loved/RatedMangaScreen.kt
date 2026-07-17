package exh.recs.loved

// KMK -->
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.components.MangaItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.toast
import exh.recs.KmkRecsReleaseNotes
import exh.recs.RecommendsScreen
import exh.recs.links.LinkGroupManagementScreen
import exh.recs.links.LinkedVersionListScreen
import exh.recs.matching.CrossExtensionMatchMode
import exh.recs.matching.CrossExtensionMatchScreen
import exh.recs.share.RecommendationBundleExporter
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Full-featured rated manga collection screen for LIKE and DISLIKE rating tiers.
 * LOVE uses [LovedMangaScreen] which delegates to [RatedMangaCollectionContent] with the same behavior.
 *
 * v0.8.0: long-press now enters bulk selection mode instead of opening recommendations (see
 * `KMK_RECS_V0_8_0_RATED_MANGA_BULK_SELECTION_AND_GROUP_ACTIONS_PLAN.md`). Recommendations are
 * reached through the per-item action menu instead.
 */
// KMK --> v0.7.36
data class RatedMangaScreen(val ratingValue: Int) : Screen() {

    @Composable
    override fun Content() {
        val rating = MangaRating.fromValue(ratingValue) ?: MangaRating.LIKE
        val screenModel = rememberScreenModel(tag = "rated_$ratingValue") {
            LovedMangaScreenModel(filterRating = rating)
        }
        RatedMangaCollectionContent(rating = rating, screenModel = screenModel)
    }
}
// KMK <--

// KMK --> v0.8.0: which confirmation dialog (if any) is currently shown, and what it acts on.
private sealed interface RatedMangaConfirmAction {
    data object ClearRatings : RatedMangaConfirmAction
    data object NotInterested : RatedMangaConfirmAction
    data object MergeIntoGroup : RatedMangaConfirmAction
    data object RemoveFromGroup : RatedMangaConfirmAction
    data class Ungroup(val groupId: String) : RatedMangaConfirmAction
}
// KMK <--

/**
 * Shared composable implementing the full rated manga collection UI.
 * Called by both [RatedMangaScreen] and [LovedMangaScreen].
 */
// KMK --> v0.7.36
@Composable
internal fun RatedMangaCollectionContent(
    rating: MangaRating,
    screenModel: LovedMangaScreenModel,
) {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by screenModel.state.collectAsState()

    val titleRes = when (rating) {
        MangaRating.LOVE -> KMR.strings.loved_manga_title
        MangaRating.LIKE -> KMR.strings.liked_manga_title
        MangaRating.DISLIKE -> KMR.strings.disliked_manga_title
    }
    val emptyRes = when (rating) {
        MangaRating.LOVE -> KMR.strings.loved_manga_empty
        MangaRating.LIKE -> KMR.strings.liked_manga_empty
        MangaRating.DISLIKE -> KMR.strings.disliked_manga_empty
    }
    val errorRes = when (rating) {
        MangaRating.LOVE -> KMR.strings.loved_manga_error
        MangaRating.LIKE -> KMR.strings.liked_manga_error
        MangaRating.DISLIKE -> KMR.strings.disliked_manga_error
    }
    val exportActionRes = when (rating) {
        MangaRating.LOVE -> KMR.strings.rec_bundle_export_loved_manga
        MangaRating.LIKE -> KMR.strings.rec_bundle_export_liked_manga
        MangaRating.DISLIKE -> KMR.strings.rec_bundle_export_disliked_manga
    }
    val exportFilename = when (rating) {
        MangaRating.LOVE -> "kmk_loved_manga.json"
        MangaRating.LIKE -> "kmk_liked_manga.json"
        MangaRating.DISLIKE -> "kmk_disliked_manga.json"
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val snapState = screenModel.state.value as? LovedMangaScreenModel.State.Success
            ?: return@rememberLauncherForActivityResult
        scope.launch {
            val exporter = RecommendationBundleExporter()
            val bundle = exporter.buildLovedMangaBundle(
                displayItems = snapState.displayItems,
                linkGroupByKey = snapState.linkGroupByKey,
                kmkVersion = KmkRecsReleaseNotes.VERSION_NAME,
            )
            exporter.writeToUri(context, uri, bundle)
                .onSuccess { withUIContext { context.toast(KMR.strings.rec_bundle_export_success) } }
                .onFailure { withUIContext { context.toast(KMR.strings.rec_bundle_export_failure) } }
        }
    }

    // KMK --> v0.8.0: selection mode + item menu + confirmation dialogs
    var confirmAction by remember { mutableStateOf<RatedMangaConfirmAction?>(null) }
    var changeRatingTarget by remember { mutableStateOf(false) }
    val successState = state as? LovedMangaScreenModel.State.Success
    val selectionMode = successState?.selectionMode == true
    val selectedCount = successState?.selectedKeys?.size ?: 0
    // KMK <--

    // KMK v0.8.10 -->
    // Local search over the already-loaded display items -- never a network search. `null` means
    // the search field isn't shown at all (matches SearchToolbar's own null-means-inactive
    // contract); "" means the field is open but empty (shows every item, same as no search).
    // rememberSaveable so the query survives recomposition/rotation; it naturally clears when this
    // screen leaves composition (navigating away), matching the plan's clear-on-navigation policy.
    var searchQuery by rememberSaveable { mutableStateOf<String?>(null) }
    val sourceManager = remember { Injekt.get<SourceManager>() }
    val sourceNameById = remember(successState?.entries) {
        successState?.entries.orEmpty()
            .map { it.taste.source }
            .distinct()
            .associateWith { sourceId -> runCatching { sourceManager.getOrStub(sourceId).name }.getOrNull() }
    }
    // KMK <--
    // KMK v0.8.7: Snackbar-based Undo for Clear Rating and Mark Not Interested — the two bulk
    // actions cheap/safe to restore exactly (re-apply the previous MangaTaste rows, or remove
    // exactly the "not interested" keys that were just added). Merge/Remove From Group/Ungroup are
    // NOT undoable here — restoring cross-source link rows correctly would need snapshotting the
    // whole link-group graph, a larger change than this pass's scope; see the implementation report.
    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(MR.strings.action_undo)
    val clearedSnackbarMessage = stringResource(KMR.strings.rated_manga_undo_cleared, selectedCount)
    val notInterestedSnackbarMessage = stringResource(KMR.strings.rated_manga_undo_not_interested, selectedCount)

    Scaffold(
        topBar = { scrollBehavior ->
            if (selectionMode) {
                // KMK --> v0.8.0: selection app bar — count + close
                AppBar(
                    title = stringResource(KMR.strings.rated_manga_selected_count, selectedCount),
                    navigateUp = screenModel::clearSelection,
                    navigationIcon = Icons.Outlined.Close,
                    scrollBehavior = scrollBehavior,
                )
                // KMK <--
            } else {
                // KMK v0.8.10: SearchToolbar is the same search affordance the Library tab and
                // other collection screens already use (search icon -> inline field -> reset/close
                // icon), reused here rather than building a bespoke search bar. searchQuery == null
                // shows the normal title + actions row; non-null shows the search field in its place.
                SearchToolbar(
                    titleContent = { Text(stringResource(titleRes)) },
                    searchQuery = searchQuery,
                    onChangeSearchQuery = { searchQuery = it },
                    placeholderText = stringResource(KMR.strings.rated_manga_search_hint),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        AppBarActions(
                            persistentListOf(
                                // KMK --> v0.8.0: visible top-right select action for discoverability
                                // KMK v0.8.1-fix1: enters selection mode without auto-selecting the
                                // first item — silently selecting an unintended manga was a safety
                                // gap since bulk actions (clear rating, mark not interested, etc.)
                                // would then apply to it. Long-press still selects the pressed item.
                                AppBar.Action(
                                    title = stringResource(KMR.strings.rated_manga_select),
                                    icon = Icons.Outlined.Checklist,
                                    onClick = { screenModel.enterSelectionMode() },
                                ),
                                // KMK <--
                                AppBar.Action(
                                    title = stringResource(KMR.strings.link_group_management_title),
                                    icon = Icons.Outlined.Link,
                                    onClick = { navigator.push(LinkGroupManagementScreen()) },
                                ),
                                AppBar.Action(
                                    title = stringResource(exportActionRes),
                                    icon = Icons.Outlined.Share,
                                    onClick = {
                                        val s = screenModel.state.value
                                        if (s is LovedMangaScreenModel.State.Success && s.displayItems.isNotEmpty()) {
                                            exportLauncher.launch(exportFilename)
                                        } else {
                                            scope.launch { withUIContext { context.toast(KMR.strings.rec_bundle_export_empty) } }
                                        }
                                    },
                                ),
                            ),
                        )
                    },
                )
            }
        },
        // KMK --> v0.8.0: phone-friendly bottom action bar while in selection mode
        bottomBar = {
            if (successState != null && successState.selectionMode) {
                RatedSelectionBottomBar(
                    selectedCount = selectedCount,
                    onChange = { changeRatingTarget = true },
                    onClear = { confirmAction = RatedMangaConfirmAction.ClearRatings },
                    onGroup = {
                        // "Group" merges selection into one group when 2+ are selected; with a
                        // single confirmed-group selection it offers Select All In Group instead.
                        if (selectedCount >= 2) {
                            confirmAction = RatedMangaConfirmAction.MergeIntoGroup
                        } else {
                            val groupId = successState.displayItems
                                .firstOrNull { it.key in successState.selectedKeys }
                                ?.confirmedGroupId
                            if (groupId != null) screenModel.selectAllInGroup(groupId)
                        }
                    },
                    onMarkNotInterested = { confirmAction = RatedMangaConfirmAction.NotInterested },
                    onRemoveFromGroup = { confirmAction = RatedMangaConfirmAction.RemoveFromGroup },
                    // KMK v0.8.7: "Select All In Group" surfaced in the bulk-selection bottom bar's
                    // More menu too (plan section 3.4), not only the per-item overflow menu. Only
                    // offered when every currently selected item shares the same confirmed group —
                    // see RatedSelectionGroupResolver for the exact conflict rule (empty selection,
                    // any ungrouped item, or 2+ distinct groups all resolve to null/hidden).
                    selectedGroupId = RatedSelectionGroupResolver.resolveSingleGroup(successState.displayItems, successState.selectedKeys),
                    onSelectAllInGroup = { groupId -> screenModel.selectAllInGroup(groupId) },
                )
            }
        },
        // KMK <--
        // KMK v0.8.7: Undo for reversible bulk actions (plan section 3.4), reusing this repo's
        // existing Snackbar-with-action-label undo convention (see LibraryTab.kt's merge-undo
        // Snackbar for the precedent this mirrors) rather than inventing a new mechanism.
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        when (val s = state) {
            is LovedMangaScreenModel.State.Loading -> Box(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            is LovedMangaScreenModel.State.Empty -> Box(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(emptyRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(MaterialTheme.padding.medium),
                )
            }

            is LovedMangaScreenModel.State.Error -> Box(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = s.error.message ?: stringResource(errorRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(MaterialTheme.padding.medium),
                )
            }

            is LovedMangaScreenModel.State.Success -> {
                // KMK v0.8.10: local, in-memory filter only -- never a network search, never
                // changes s.displayItems itself (sort/grouping/selection/bulk actions/export/group
                // management all keep operating on the full underlying state regardless of the
                // active query).
                val items = RatedMangaSearchFilter.filter(
                    items = s.displayItems,
                    query = searchQuery.orEmpty(),
                    sourceNameOf = { sourceId -> sourceNameById[sourceId] },
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp + MaterialTheme.padding.small),
                    contentPadding = contentPadding,
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        RatedGroupDuplicatesToggleRow(
                            checked = s.groupDuplicates,
                            onToggle = screenModel::toggleGroupDuplicates,
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        RatedSortRow(
                            current = s.sortMode,
                            onSelect = screenModel::setSortMode,
                        )
                    }
                    if (s.groupDuplicates && s.entries.isNotEmpty() && s.displayItems.size == s.entries.size) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = stringResource(KMR.strings.loved_manga_no_clear_duplicates),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                    // KMK v0.8.10: a search that matches nothing is distinct from "no rated manga at
                    // all" (State.Empty, a different branch entirely) -- shown only when a non-blank
                    // query is active and every loaded item was filtered out by it.
                    if (!searchQuery.isNullOrBlank() && items.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = stringResource(MR.strings.no_results_found),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(MaterialTheme.padding.medium),
                            )
                        }
                    }
                    items(items, key = { "${it.taste.source}|${it.taste.url}" }) { item ->
                        var showItemMenu by rememberSaveable(item.key) { mutableStateOf(false) }
                        Box {
                            MangaItem(
                                title = item.manga?.title ?: item.taste.title,
                                cover = item.manga?.asMangaCover() ?: ratedFallbackCover(item),
                                isFavorite = item.manga?.favorite ?: false,
                                isSelected = item.key in s.selectedKeys,
                                onClick = {
                                    // KMK --> v0.8.0: tap toggles selection in selection mode;
                                    // otherwise opens the manga as before.
                                    if (s.selectionMode) {
                                        screenModel.toggleSelection(item.key)
                                    } else {
                                        navigator.push(MangaScreen(item.taste.mangaId, true))
                                    }
                                    // KMK <--
                                },
                                onLongClick = {
                                    // KMK --> v0.8.0: long-press enters selection mode; no longer
                                    // opens recommendations directly (see plan §UX Contract).
                                    screenModel.enterSelection(item.key)
                                    // KMK <--
                                },
                            )
                            if (item.versionCount > 1) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.extraSmall,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp),
                                ) {
                                    Text(
                                        text = stringResource(KMR.strings.loved_manga_versions, item.versionCount),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            // KMK --> v0.8.0: item action menu trigger, replacing the old Explore
                            // overlay (recommendation actions moved into the grouped menu below).
                            if (!s.selectionMode) {
                                Box(modifier = Modifier.align(Alignment.TopStart)) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                                        shape = MaterialTheme.shapes.extraSmall,
                                        modifier = Modifier.padding(2.dp),
                                    ) {
                                        IconButton(
                                            onClick = { showItemMenu = true },
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.MoreVert,
                                                contentDescription = stringResource(KMR.strings.rated_manga_item_menu),
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            )
                                        }
                                    }
                                    RatedMangaItemMenu(
                                        expanded = showItemMenu,
                                        onDismiss = { showItemMenu = false },
                                        item = item,
                                        rating = rating,
                                        navigator = navigator,
                                        onChangeRating = {
                                            screenModel.enterSelection(item.key)
                                            changeRatingTarget = true
                                        },
                                        onClearRating = {
                                            screenModel.enterSelection(item.key)
                                            confirmAction = RatedMangaConfirmAction.ClearRatings
                                        },
                                        onMarkNotInterested = {
                                            screenModel.enterSelection(item.key)
                                            confirmAction = RatedMangaConfirmAction.NotInterested
                                        },
                                        onManageGroup = { groupId -> navigator.push(LinkGroupManagementScreen(groupId)) },
                                        onViewLinkedVersions = { groupId -> navigator.push(LinkedVersionListScreen(groupId)) },
                                        onSelectAllInGroup = { groupId -> screenModel.selectAllInGroup(groupId) },
                                        onRemoveFromGroup = {
                                            screenModel.enterSelection(item.key)
                                            confirmAction = RatedMangaConfirmAction.RemoveFromGroup
                                        },
                                        onUngroup = { groupId -> confirmAction = RatedMangaConfirmAction.Ungroup(groupId) },
                                    )
                                }
                            }
                            // KMK <--
                        }
                    }
                }
            }
        }
    }

    // KMK --> v0.8.0: confirmation dialogs for destructive/broad actions
    when (val action = confirmAction) {
        RatedMangaConfirmAction.ClearRatings -> RatedMangaConfirmDialog(
            titleRes = KMR.strings.rated_manga_action_clear_rating,
            message = stringResource(KMR.strings.rated_manga_confirm_clear_rating, selectedCount),
            onConfirm = {
                // KMK v0.8.7: snapshot exactly the taste rows about to be cleared, before clearing,
                // so Undo can re-apply them verbatim (title/rating/mangaId/source/url). The Snackbar
                // message/action-label strings are pre-resolved outside this lambda (see
                // clearedSnackbarMessage/undoLabel above) since stringResource() can only be called
                // directly from composition, not from inside an event-callback lambda.
                val snapshot = successState?.entries
                    ?.filter { RatedMangaKey.of(it.taste) in successState.selectedKeys }
                    ?.map { it.taste }
                    .orEmpty()
                screenModel.clearSelectedRatings()
                if (snapshot.isNotEmpty()) {
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = clearedSnackbarMessage,
                            actionLabel = undoLabel,
                            withDismissAction = true,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            screenModel.restoreRatings(snapshot)
                        }
                    }
                }
            },
            onDismiss = { confirmAction = null },
        )
        RatedMangaConfirmAction.NotInterested -> RatedMangaConfirmDialog(
            titleRes = KMR.strings.rated_manga_action_mark_not_interested,
            message = stringResource(KMR.strings.rated_manga_confirm_not_interested, selectedCount),
            onConfirm = {
                // KMK v0.8.7: snapshot exactly which keys are about to be marked not-interested, so
                // Undo removes only those keys (any other pre-existing "not interested" entries the
                // user had are left untouched).
                val snapshot = successState?.selectedKeys.orEmpty()
                screenModel.markSelectedNotInterested()
                if (snapshot.isNotEmpty()) {
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = notInterestedSnackbarMessage,
                            actionLabel = undoLabel,
                            withDismissAction = true,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            screenModel.undoMarkNotInterested(snapshot)
                        }
                    }
                }
            },
            onDismiss = { confirmAction = null },
        )
        RatedMangaConfirmAction.MergeIntoGroup -> RatedMangaConfirmDialog(
            titleRes = KMR.strings.rated_manga_action_merge_selected_into_group,
            message = stringResource(KMR.strings.rated_manga_confirm_merge, selectedCount),
            onConfirm = { screenModel.mergeSelectedIntoGroup() },
            onDismiss = { confirmAction = null },
        )
        RatedMangaConfirmAction.RemoveFromGroup -> RatedMangaConfirmDialog(
            titleRes = KMR.strings.rated_manga_action_remove_from_group,
            message = stringResource(KMR.strings.rated_manga_confirm_remove_from_group, selectedCount),
            onConfirm = { screenModel.removeSelectedFromGroup() },
            onDismiss = { confirmAction = null },
        )
        is RatedMangaConfirmAction.Ungroup -> RatedMangaConfirmDialog(
            titleRes = KMR.strings.rated_manga_action_ungroup,
            message = stringResource(KMR.strings.rated_manga_confirm_ungroup),
            onConfirm = { screenModel.ungroup(action.groupId) },
            onDismiss = { confirmAction = null },
        )
        null -> {}
    }

    if (changeRatingTarget) {
        RatedMangaChangeRatingDialog(
            onSelect = { newRating ->
                changeRatingTarget = false
                screenModel.changeSelectedRating(newRating)
            },
            onDismiss = { changeRatingTarget = false },
        )
    }
    // KMK <--
}
// KMK <--

// KMK --> v0.8.0
@Composable
private fun RatedSelectionBottomBar(
    selectedCount: Int,
    onChange: () -> Unit,
    onClear: () -> Unit,
    onGroup: () -> Unit,
    onMarkNotInterested: () -> Unit,
    onRemoveFromGroup: () -> Unit,
    // KMK v0.8.7: null when the current selection doesn't unambiguously belong to one confirmed
    // group (empty selection, mixed groups, or no group at all) — the action is hidden rather than
    // shown-but-disabled in that case, since there is no single group it could mean.
    selectedGroupId: String? = null,
    onSelectAllInGroup: (String) -> Unit = {},
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    BottomAppBar {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.padding.small),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TextButton(onClick = onChange, enabled = selectedCount > 0) {
                Text(stringResource(KMR.strings.rated_manga_selection_bar_change))
            }
            TextButton(onClick = onClear, enabled = selectedCount > 0) {
                Text(stringResource(KMR.strings.rated_manga_selection_bar_clear))
            }
            TextButton(onClick = onGroup, enabled = selectedCount > 0) {
                Text(stringResource(KMR.strings.rated_manga_selection_bar_group))
            }
            Box {
                TextButton(onClick = { showMoreMenu = true }, enabled = selectedCount > 0) {
                    Text(stringResource(KMR.strings.rated_manga_selection_bar_more))
                }
                DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                    // KMK v0.8.7: plan section 3.4's "Select All In Group" under More.
                    if (selectedGroupId != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(KMR.strings.rated_manga_action_select_all_in_group)) },
                            onClick = {
                                showMoreMenu = false
                                onSelectAllInGroup(selectedGroupId)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(KMR.strings.rated_manga_action_mark_not_interested)) },
                        onClick = {
                            showMoreMenu = false
                            onMarkNotInterested()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(KMR.strings.rated_manga_action_remove_from_group)) },
                        onClick = {
                            showMoreMenu = false
                            onRemoveFromGroup()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RatedMangaConfirmDialog(
    titleRes: dev.icerock.moko.resources.StringResource,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) {
                Text(stringResource(KMR.strings.rated_manga_confirm_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(KMR.strings.rated_manga_confirm_cancel)) }
        },
    )
}

@Composable
private fun RatedMangaChangeRatingDialog(
    onSelect: (MangaRating) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(KMR.strings.rated_manga_change_rating_dialog_title)) },
        text = {
            Row {
                listOf(
                    MangaRating.LOVE to KMR.strings.rated_manga_rating_love,
                    MangaRating.LIKE to KMR.strings.rated_manga_rating_like,
                    MangaRating.DISLIKE to KMR.strings.rated_manga_rating_dislike,
                ).forEach { (r, labelRes) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = false, onClick = { onSelect(r) })
                        Text(stringResource(labelRes))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(KMR.strings.rated_manga_confirm_cancel)) }
        },
    )
}

@Composable
private fun RatedMangaItemMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    item: LovedDisplayItem,
    rating: MangaRating,
    navigator: cafe.adriel.voyager.navigator.Navigator,
    onChangeRating: () -> Unit,
    onClearRating: () -> Unit,
    onMarkNotInterested: () -> Unit,
    onManageGroup: (String) -> Unit,
    onViewLinkedVersions: (String) -> Unit,
    onSelectAllInGroup: (String) -> Unit,
    onRemoveFromGroup: () -> Unit,
    onUngroup: (String) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        // --- Recommendation Actions ---
        DropdownMenuItem(
            text = { Text(stringResource(KMR.strings.rated_manga_action_see_recommendations)) },
            onClick = {
                onDismiss()
                navigator.push(
                    RecommendsScreen(RecommendsScreen.Args.SingleSourceManga(item.taste.mangaId, item.taste.source)),
                )
            },
        )
        if (item.hasConfirmedGroup) {
            DropdownMenuItem(
                text = { Text(stringResource(KMR.strings.rated_manga_action_see_group_recommendations)) },
                onClick = {
                    onDismiss()
                    navigator.push(
                        RecommendsScreen(
                            RecommendsScreen.Args.CrossSourceGroupSeed(
                                sourceId = item.taste.source,
                                url = item.taste.url,
                                primaryTitle = item.manga?.title ?: item.taste.title,
                            ),
                        ),
                    )
                },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(KMR.strings.rated_manga_action_find_other_versions)) },
            onClick = {
                onDismiss()
                navigator.push(
                    CrossExtensionMatchScreen.fromMode(item.taste.mangaId, CrossExtensionMatchMode.Rating(rating)),
                )
            },
        )
        if (item.hasConfirmedGroup) {
            DropdownMenuItem(
                text = { Text(stringResource(KMR.strings.rated_manga_action_favorite_other_versions)) },
                onClick = {
                    onDismiss()
                    navigator.push(
                        CrossExtensionMatchScreen.fromMode(item.taste.mangaId, CrossExtensionMatchMode.Favorite),
                    )
                },
            )
        }

        // --- Rating Actions ---
        DropdownMenuItem(
            text = { Text(stringResource(KMR.strings.rated_manga_action_change_rating)) },
            onClick = {
                onDismiss()
                onChangeRating()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(KMR.strings.rated_manga_action_clear_rating)) },
            onClick = {
                onDismiss()
                onClearRating()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(KMR.strings.rated_manga_action_mark_not_interested)) },
            onClick = {
                onDismiss()
                onMarkNotInterested()
            },
        )

        // --- Group Actions ---
        if (item.hasConfirmedGroup && item.confirmedGroupId != null) {
            val groupId = item.confirmedGroupId
            DropdownMenuItem(
                text = { Text(stringResource(KMR.strings.rated_manga_action_manage_group)) },
                onClick = {
                    onDismiss()
                    onManageGroup(groupId)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(KMR.strings.rated_manga_action_view_linked_versions)) },
                onClick = {
                    onDismiss()
                    onViewLinkedVersions(groupId)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(KMR.strings.rated_manga_action_select_all_in_group)) },
                onClick = {
                    onDismiss()
                    onSelectAllInGroup(groupId)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(KMR.strings.rated_manga_action_remove_from_group)) },
                onClick = {
                    onDismiss()
                    onRemoveFromGroup()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(KMR.strings.rated_manga_action_ungroup)) },
                onClick = {
                    onDismiss()
                    onUngroup(groupId)
                },
            )
        }
    }
}
// KMK <--

@Composable
private fun RatedGroupDuplicatesToggleRow(
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
        )
        Text(
            text = stringResource(KMR.strings.loved_manga_group_toggle),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun RatedSortRow(
    current: LoveSortMode,
    onSelect: (LoveSortMode) -> Unit,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            LoveSortMode.RECENT to KMR.strings.loved_manga_sort_recent,
            LoveSortMode.OLDEST to KMR.strings.loved_manga_sort_oldest,
            LoveSortMode.TITLE_AZ to KMR.strings.loved_manga_sort_title,
            LoveSortMode.SOURCE to KMR.strings.loved_manga_sort_source,
        ).forEach { (mode, labelRes) ->
            FilterChip(
                selected = current == mode,
                onClick = { onSelect(mode) },
                label = { Text(stringResource(labelRes)) },
            )
        }
    }
}

private fun ratedFallbackCover(item: LovedDisplayItem): MangaCover = MangaCover(
    mangaId = item.taste.mangaId,
    sourceId = item.taste.source,
    isMangaFavorite = false,
    ogUrl = null,
    lastModified = 0L,
)
// KMK <--

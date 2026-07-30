package exh.recs

// KMK -->
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.components.GlobalSearchCardRow
import eu.kanade.presentation.browse.components.GlobalSearchErrorResultItem
import eu.kanade.presentation.browse.components.GlobalSearchLoadingResultItem
import eu.kanade.presentation.browse.components.GlobalSearchResultItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.toast
import exh.recs.bestversion.BestVersionCompareScreen
import exh.recs.loved.LovedMangaScreen
import exh.recs.loved.RatedMangaScreen
import exh.recs.matching.CrossExtensionMatchMode
import exh.recs.matching.CrossExtensionMatchScreen
import exh.recs.matching.MangaIdentityKey
import exh.recs.settings.RecommendationSettingsIndexScreen
import exh.recs.settings.toScreen
import exh.recs.share.RecommendationBundleExporter
import exh.util.EvaluationModeFormatter
import exh.util.rememberEvaluationModeEnabled
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.core.common.i18n.stringResource as contextStringResource

@Composable
fun Screen.personalRecommendationsTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val screenModel = rememberScreenModel { BrowsePersonalRecommendationsScreenModel() }
    val state by screenModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    // KMK --> v0.7.5: source row targeted for export (null = Top Picks)
    var pendingExportSource by remember { mutableStateOf<Source?>(null) }
    var pendingExportIsTopPicks by remember { mutableStateOf(false) }
    // KMK <--

    // KMK --> v0.8.16: For You long-press selection mode -- local UI state only, no domain writes
    // until an action button is pressed. Keyed by MangaIdentityKey (source+url) rather than local id
    // since recommendation results may not all be localized identically.
    var selectedManga by remember { mutableStateOf<Map<MangaIdentityKey, Manga>>(emptyMap()) }
    val selectionMode = ForYouSelectionPolicy.isSelectionMode(selectedManga)

    // KMK v0.8.17-fix1: rate-other-versions continuation -- offered only when exactly one manga was
    // selected for a Love/Like/Dislike action, per the plan's decision not to prompt per-item during
    // a multi-select bulk rate. Holds the just-rated manga id + rating so the dialog below can route
    // into the existing CrossExtensionMatchScreen flow without inventing a new grouping mechanism.
    var pendingRateOtherVersions by remember { mutableStateOf<Pair<Long, MangaRating>?>(null) }
    // KMK <--

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) {
            pendingExportSource = null
            pendingExportIsTopPicks = false
            return@rememberLauncherForActivityResult
        }
        val snapState = screenModel.state.value
        val targetSource = pendingExportSource
        val isTopPicks = pendingExportIsTopPicks
        pendingExportSource = null
        pendingExportIsTopPicks = false

        scope.launch {
            val exporter = RecommendationBundleExporter()
            val bundle = when {
                isTopPicks -> {
                    val detail = snapState.combinedDetailResult as? PersonalRecommendationResult.Success
                    if (detail == null || detail.result.isEmpty()) {
                        withUIContext { context.toast(KMR.strings.rec_bundle_export_empty) }
                        return@launch
                    }
                    exporter.buildTopPicksBundle(detail.result, KmkRecsReleaseNotes.VERSION_NAME)
                }
                targetSource != null -> {
                    val result = snapState.items[targetSource] as? PersonalRecommendationResult.Success
                    if (result == null || result.result.isEmpty()) {
                        withUIContext { context.toast(KMR.strings.rec_bundle_export_empty) }
                        return@launch
                    }
                    exporter.buildSourceRowBundle(targetSource.name, targetSource.lang, result.result, KmkRecsReleaseNotes.VERSION_NAME)
                }
                else -> return@launch
            }
            exporter.writeToUri(context, uri, bundle)
                .onSuccess { withUIContext { context.toast(KMR.strings.rec_bundle_export_success) } }
                .onFailure { withUIContext { context.toast(KMR.strings.rec_bundle_export_failure) } }
        }
    }
    // KMK <--

    // KMK v0.8.17-fix1: rate-other-versions continuation dialog -- offered only after rating exactly
    // one selected For You manga (see onRateSelected above). Routes into the existing
    // CrossExtensionMatchScreen flow already used by manga detail and Rated Manga; no new grouping
    // system. Closing/declining never undoes the rating already committed in onRateSelected.
    pendingRateOtherVersions?.let { (mangaId, rating) ->
        AlertDialog(
            onDismissRequest = { pendingRateOtherVersions = null },
            title = { Text(stringResource(KMR.strings.rec_for_you_rate_other_versions_prompt)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRateOtherVersions = null
                        navigator.push(CrossExtensionMatchScreen.fromMode(mangaId, CrossExtensionMatchMode.Rating(rating)))
                    },
                ) {
                    Text(stringResource(KMR.strings.rec_for_you_rate_other_versions_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRateOtherVersions = null }) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
    // KMK <--

    return TabContent(
        titleRes = KMR.strings.taste_recommendations_tab,
        // KMK v0.8.11: Loved/Liked/Disliked were three separate always-visible top-bar icons plus
        // Refresh/Export/Settings -- six icons total, crowding phone width. Loved/Liked/Disliked are
        // now grouped under one "Rated manga" menu, and Export moved to the overflow menu; Refresh
        // and Settings remain the two always-visible primary actions. No action was removed or
        // hidden behind long-press -- every destination is still one or two taps away.
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.action_webview_refresh),
                icon = Icons.Outlined.Refresh,
                onClick = screenModel::refresh,
            ),
            AppBar.ActionCompose(
                title = stringResource(KMR.strings.rec_rated_manga_menu),
            ) {
                var showRatedMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showRatedMenu = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Favorite,
                            contentDescription = stringResource(KMR.strings.rec_rated_manga_menu),
                        )
                    }
                    DropdownMenu(
                        expanded = showRatedMenu,
                        onDismissRequest = { showRatedMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(KMR.strings.loved_manga_title)) },
                            leadingIcon = { Icon(Icons.Outlined.Favorite, contentDescription = null) },
                            onClick = {
                                showRatedMenu = false
                                navigator.push(LovedMangaScreen())
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(KMR.strings.liked_manga_title)) },
                            leadingIcon = { Icon(Icons.Outlined.ThumbUp, contentDescription = null) },
                            onClick = {
                                showRatedMenu = false
                                navigator.push(RatedMangaScreen(MangaRating.LIKE.value))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(KMR.strings.disliked_manga_title)) },
                            leadingIcon = { Icon(Icons.Outlined.ThumbDown, contentDescription = null) },
                            onClick = {
                                showRatedMenu = false
                                navigator.push(RatedMangaScreen(MangaRating.DISLIKE.value))
                            },
                        )
                    }
                }
            },
            AppBar.Action(
                title = stringResource(KMR.strings.taste_settings_title),
                icon = Icons.Outlined.Settings,
                // KMK v0.8.8: entry point is now the concise settings index, not the single big screen directly.
                onClick = { navigator.push(RecommendationSettingsIndexScreen) },
            ),
            // KMK --> v0.7.5: Export Top Picks -- moved to overflow in v0.8.11 (Phase G).
            AppBar.OverflowAction(
                title = stringResource(KMR.strings.rec_bundle_export_top_picks),
                onClick = {
                    pendingExportIsTopPicks = true
                    exportLauncher.launch("kmk_top_picks.json")
                },
            ),
            // KMK <--
        ),
        content = { contentPadding, _ ->
            // KMK v0.8.18-fix1: right-edge quick-access panel overlay -- the v0.8.18-fix1 replacement
            // placement for the panel formerly on Recommendation Settings detail screens. This tab's
            // content is rendered inside BrowseTab's own shared Scaffold (TabContent, not a
            // screen-owned Scaffold), so the overlay is layered here around just this tab's content.
            Box(modifier = Modifier.fillMaxSize()) {
                PersonalRecommendationsContent(
                    state = state,
                    getManga = screenModel::getManga,
                    onClickItem = { manga -> navigator.push(MangaScreen(manga.id, true)) },
                    onClickSource = { source ->
                        val ctx = state.searchContexts[source.id]
                        navigator.push(BrowseSourceScreen(source.id, ctx?.textQuery))
                    },
                    onClickTopPicks = {
                        val detail = state.combinedDetailResult
                        if (detail is PersonalRecommendationResult.Success && detail.result.isNotEmpty()) {
                            val isPartial = state.total > 0 && state.progress < state.total
                            navigator.push(TopPicksScreen(ArrayList(detail.result.map { it.manga.id }), isPartial))
                        }
                    },
                    // KMK --> v0.7.5: long-press source row to export
                    onLongClickSource = { source ->
                        pendingExportSource = source
                        pendingExportIsTopPicks = false
                        exportLauncher.launch("kmk_${source.name.lowercase().replace(Regex("[^a-z0-9]"), "_")}.json")
                    },
                    // KMK <--
                    // KMK v0.8.16: For You long-press selection mode
                    selectedManga = selectedManga,
                    onLongClickManga = { manga ->
                        val key = MangaIdentityKey(manga.source, manga.url)
                        selectedManga = ForYouSelectionPolicy.longPress(selectedManga, key, manga)
                    },
                    onToggleSelectManga = { manga ->
                        val key = MangaIdentityKey(manga.source, manga.url)
                        selectedManga = ForYouSelectionPolicy.toggle(selectedManga, key, manga)
                    },
                    onCloseSelection = { selectedManga = ForYouSelectionPolicy.clear() },
                    // KMK v0.8.17-fix1: rateSelected/markSelectedNotInterested/clearSelectedRatings are
                    // now suspend functions returning a BulkTasteActionOutcome instead of fire-and-forget
                    // -- awaited here so a real success/failure toast can be shown and errors are never
                    // silently swallowed. Selection is cleared once the action is accepted (before the
                    // suspend call returns), matching the app's existing bulk-action convention.
                    onRateSelected = { rating ->
                        val targets = selectedManga.values.toList()
                        val single = targets.singleOrNull()
                        selectedManga = ForYouSelectionPolicy.clear()
                        scope.launch {
                            val outcome = screenModel.rateSelected(targets, rating)
                            showBulkActionFeedback(context, bulkTasteActionRatingType(rating), outcome)
                            if (single != null && outcome.successCount > 0) {
                                pendingRateOtherVersions = single.id to rating
                            }
                        }
                    },
                    onNotInterestedSelected = {
                        val targets = selectedManga.values.toList()
                        selectedManga = ForYouSelectionPolicy.clear()
                        scope.launch {
                            val outcome = screenModel.markSelectedNotInterested(targets)
                            showBulkActionFeedback(context, BulkTasteActionType.NOT_INTERESTED, outcome)
                        }
                    },
                    onClearRatingSelected = {
                        val targets = selectedManga.values.toList()
                        selectedManga = ForYouSelectionPolicy.clear()
                        scope.launch {
                            val outcome = screenModel.clearSelectedRatings(targets)
                            showBulkActionFeedback(context, BulkTasteActionType.CLEAR_RATING, outcome)
                        }
                    },
                    onFindBestVersionSelected = {
                        val target = selectedManga.values.singleOrNull()
                        selectedManga = ForYouSelectionPolicy.clear()
                        if (target != null) navigator.push(BestVersionCompareScreen(target.id))
                    },
                    onOpenSelected = {
                        val target = selectedManga.values.singleOrNull()
                        selectedManga = ForYouSelectionPolicy.clear()
                        if (target != null) navigator.push(MangaScreen(target.id, true))
                    },
                    // KMK --> v0.7.25: retry after offline
                    onRetry = screenModel::refresh,
                    // KMK <--
                    contentPadding = contentPadding,
                )
                // KMK --> v0.8.19: this panel now jumps to Recommendation Settings sections
                // (its originally-stated purpose) instead of switching between For You/Loved/
                // Liked/Disliked, which the top tabs already cover.
                exh.recs.settings.RecommendationSettingsQuickAccessPanel(
                    current = null,
                    onNavigate = { destination ->
                        navigator.push(destination.toScreen())
                    },
                )
                // KMK <--
            }
        },
    )
}

@Composable
private fun PersonalRecommendationsContent(
    state: BrowsePersonalRecommendationsScreenModel.State,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickItem: (Manga) -> Unit,
    onClickSource: (Source) -> Unit,
    onClickTopPicks: () -> Unit,
    // KMK --> v0.7.5: export source row on long press
    onLongClickSource: ((Source) -> Unit)? = null,
    // KMK <--
    // KMK --> v0.7.25: retry callback for offline state
    onRetry: () -> Unit = {},
    // KMK <--
    // KMK v0.8.16: For You long-press selection mode
    selectedManga: Map<MangaIdentityKey, Manga> = emptyMap(),
    onLongClickManga: (Manga) -> Unit = {},
    onToggleSelectManga: (Manga) -> Unit = {},
    onCloseSelection: () -> Unit = {},
    onRateSelected: (MangaRating) -> Unit = {},
    onNotInterestedSelected: () -> Unit = {},
    // KMK v0.8.17-fix1: Clear Rating for the current selection
    onClearRatingSelected: () -> Unit = {},
    onFindBestVersionSelected: () -> Unit = {},
    onOpenSelected: () -> Unit = {},
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
) {
    val selectionMode = ForYouSelectionPolicy.isSelectionMode(selectedManga)
    val selectionList = selectedManga.values.toList()
    // KMK --> v0.8.19: evaluation mode source-name obfuscation
    val evaluationModeEnabled = rememberEvaluationModeEnabled()
    // KMK <--
    // KMK v0.8.16: while in selection mode, tapping a card toggles selection instead of opening it;
    // long-press always enters/adds to selection. Normal tap opens the manga when not selecting.
    val effectiveOnClick: (Manga) -> Unit = { manga ->
        if (selectionMode) onToggleSelectManga(manga) else onClickItem(manga)
    }
    val effectiveOnLongClick: (Manga) -> Unit = { manga -> onLongClickManga(manga) }
    when {
        // KMK --> v0.7.25: show offline error before loading spinner
        state.isOffline -> {
            EmptyScreen(
                message = stringResource(KMR.strings.rec_for_you_offline),
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                actions = persistentListOf(
                    EmptyScreenAction(
                        stringRes = MR.strings.action_retry,
                        icon = Icons.Outlined.Refresh,
                        onClick = onRetry,
                    ),
                ),
            )
        }
        // KMK <--
        state.isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        state.profileIsEmpty -> {
            Box(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(KMR.strings.taste_recommendations_empty))
            }
        }
        else -> {
            val dedupedMap = state.dedupedItems()
            val visibleOrderedSources = state.sourceOrder.filter { source ->
                val result = dedupedMap[source]
                result != null && (result !is PersonalRecommendationResult.Success || !result.isEmpty)
            }
            val allDone = state.total > 0 && state.progress == state.total
            val combinedResult = state.combinedResult
            val hasCombined = combinedResult is PersonalRecommendationResult.Success &&
                !(combinedResult as PersonalRecommendationResult.Success).isEmpty
            val hasNoResults = allDone && !hasCombined &&
                visibleOrderedSources.none { source ->
                    val r = dedupedMap[source]
                    r is PersonalRecommendationResult.Success && !r.isEmpty
                }

            if (hasNoResults) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(KMR.strings.taste_recommendations_empty))
                }
            } else {
                // KMK <--
                Column(modifier = Modifier.fillMaxSize()) {
                    // KMK v0.8.16: selection banner -- count + close, never crowds the normal top bar
                    if (selectionMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = onCloseSelection) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(KMR.strings.rec_for_you_selection_close),
                                )
                            }
                            Text(
                                text = stringResource(KMR.strings.rec_for_you_selected_count, selectionList.size),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                    // KMK --> v0.7.29: pull-to-refresh support
                    var isRefreshing by remember { mutableStateOf(false) }
                    LaunchedEffect(state.isLoading) {
                        if (!state.isLoading) isRefreshing = false
                    }
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            isRefreshing = true
                            onRetry()
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) {
                        // KMK <--
                        LazyColumn(contentPadding = contentPadding) {
                            // Top Picks row — appears first, derived from all source results
                            if (hasCombined) {
                                item(key = "top_picks") {
                                    val combined = combinedResult as PersonalRecommendationResult.Success
                                    val subtitle = combined.reason?.let {
                                        stringResource(KMR.strings.rec_top_picks_matched, it)
                                    } ?: stringResource(KMR.strings.rec_top_picks_subtitle)
                                    GlobalSearchResultItem(
                                        title = stringResource(KMR.strings.rec_top_picks_title),
                                        subtitle = subtitle,
                                        onClick = onClickTopPicks,
                                    ) {
                                        GlobalSearchCardRow(
                                            titles = combined.result.map { it.manga },
                                            getManga = getManga,
                                            onClick = effectiveOnClick,
                                            onLongClick = effectiveOnLongClick,
                                            selection = selectionList,
                                        )
                                    }
                                }
                            }
                            // Per-source rows in priority order
                            visibleOrderedSources.forEach { source ->
                                item(key = source.id) {
                                    val result = dedupedMap[source] ?: return@item
                                    val reason = (result as? PersonalRecommendationResult.Success)?.reason
                                    GlobalSearchResultItem(
                                        // KMK -->
                                        title = if (evaluationModeEnabled) {
                                            EvaluationModeFormatter.sourceLabel(source.id)
                                        } else {
                                            source.name
                                        },
                                        // KMK <--
                                        subtitle = if (reason != null) {
                                            stringResource(KMR.strings.taste_matched_tags, reason)
                                        } else {
                                            source.lang.uppercase()
                                        },
                                        onClick = { onClickSource(source) },
                                        // KMK --> v0.7.5: long-press to export source row
                                        onLongClick = onLongClickSource?.let { handler -> { handler(source) } },
                                        // KMK <--
                                    ) {
                                        when (result) {
                                            PersonalRecommendationResult.Loading -> GlobalSearchLoadingResultItem()
                                            is PersonalRecommendationResult.Success -> GlobalSearchCardRow(
                                                titles = result.result.map { it.manga },
                                                getManga = getManga,
                                                onClick = effectiveOnClick,
                                                onLongClick = effectiveOnLongClick,
                                                selection = selectionList,
                                            )
                                            is PersonalRecommendationResult.Error -> {
                                                GlobalSearchErrorResultItem(
                                                    message = with(LocalContext.current) {
                                                        result.throwable.formattedMessage
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // KMK --> v0.7.29
                    }
                    // KMK <--
                    // KMK v0.8.16: selection bottom action bar -- real actions only, never
                    // rendered unless something is actually selected.
                    if (selectionMode) {
                        ForYouSelectionBottomBar(
                            selectedCount = selectionList.size,
                            onRateLove = { onRateSelected(MangaRating.LOVE) },
                            onRateLike = { onRateSelected(MangaRating.LIKE) },
                            onRateDislike = { onRateSelected(MangaRating.DISLIKE) },
                            onNotInterested = onNotInterestedSelected,
                            onClearRating = onClearRatingSelected,
                            onFindBestVersion = onFindBestVersionSelected,
                            onOpen = onOpenSelected,
                            isSingleSelection = ForYouSelectionPolicy.isSingleSelection(selectedManga),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ForYouSelectionBottomBar(
    selectedCount: Int,
    onRateLove: () -> Unit,
    onRateLike: () -> Unit,
    onRateDislike: () -> Unit,
    onNotInterested: () -> Unit,
    // KMK v0.8.17-fix1: Clear Rating direct/overflow action
    onClearRating: () -> Unit,
    onFindBestVersion: () -> Unit,
    onOpen: () -> Unit,
    isSingleSelection: Boolean,
) {
    // KMK v0.8.16-fix1: width-aware layout -- ADB tablet audit found all six text buttons fit
    // landscape but are too crowded for phone portrait (UI_AUDIT_NOTES.md). Every action stays
    // available; compact width only moves the less-common ones into a "More" overflow menu.
    BottomAppBar {
        androidx.compose.foundation.layout.BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.padding.small),
        ) {
            val layout = ForYouSelectionActionLayoutPolicy.layoutFor(maxWidth.value.toInt())
            val overflow = ForYouSelectionActionLayoutPolicy.overflowActions(layout, isSingleSelection)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ForYouActionButton(
                    icon = Icons.Outlined.Favorite,
                    label = stringResource(KMR.strings.taste_love),
                    onClick = onRateLove,
                    enabled = selectedCount > 0,
                )
                ForYouActionButton(
                    icon = Icons.Outlined.ThumbUp,
                    label = stringResource(KMR.strings.taste_like),
                    onClick = onRateLike,
                    enabled = selectedCount > 0,
                )
                ForYouActionButton(
                    icon = Icons.Outlined.ThumbDown,
                    label = stringResource(KMR.strings.taste_dislike),
                    onClick = onRateDislike,
                    enabled = selectedCount > 0,
                )
                if (!overflow.notInterested) {
                    ForYouActionButton(
                        icon = Icons.Outlined.VisibilityOff,
                        label = stringResource(KMR.strings.rec_mark_seen),
                        onClick = onNotInterested,
                        enabled = selectedCount > 0,
                    )
                }
                if (!overflow.clearRating) {
                    ForYouActionButton(
                        icon = Icons.Outlined.DeleteOutline,
                        label = stringResource(KMR.strings.rec_for_you_selection_clear_rating),
                        onClick = onClearRating,
                        enabled = selectedCount > 0,
                    )
                }
                if (isSingleSelection && !overflow.findBestVersion) {
                    ForYouActionButton(
                        icon = Icons.AutoMirrored.Outlined.CompareArrows,
                        label = stringResource(KMR.strings.rec_for_you_selection_find_best_version),
                        onClick = onFindBestVersion,
                        enabled = true,
                    )
                }
                if (isSingleSelection && !overflow.open) {
                    ForYouActionButton(
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                        label = stringResource(KMR.strings.rec_for_you_selection_open),
                        onClick = onOpen,
                        enabled = true,
                    )
                }
                if (overflow.notInterested || overflow.clearRating || overflow.findBestVersion || overflow.open) {
                    var showMore by remember { mutableStateOf(false) }
                    Box {
                        ForYouActionButton(
                            icon = Icons.Outlined.MoreVert,
                            label = stringResource(KMR.strings.rec_for_you_selection_more),
                            onClick = { showMore = true },
                            enabled = selectedCount > 0,
                        )
                        DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                            if (overflow.notInterested) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(KMR.strings.rec_mark_seen)) },
                                    leadingIcon = { Icon(Icons.Outlined.VisibilityOff, contentDescription = null) },
                                    onClick = {
                                        showMore = false
                                        onNotInterested()
                                    },
                                )
                            }
                            if (overflow.clearRating) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(KMR.strings.rec_for_you_selection_clear_rating)) },
                                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                                    onClick = {
                                        showMore = false
                                        onClearRating()
                                    },
                                )
                            }
                            if (overflow.findBestVersion) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(KMR.strings.rec_for_you_selection_find_best_version)) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.CompareArrows, contentDescription = null) },
                                    onClick = {
                                        showMore = false
                                        onFindBestVersion()
                                    },
                                )
                            }
                            if (overflow.open) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(KMR.strings.rec_for_you_selection_open)) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null) },
                                    onClick = {
                                        showMore = false
                                        onOpen()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// KMK v0.8.17-fix1: shared direct-action button for the For You selection bottom bar -- fixes the
// plan's alignment complaint by giving every direct action the same icon size, spacing, label style,
// minimum touch height, and icon-above-label layout (rather than mixed raw `TextButton` Row content
// whose icon/text baselines drift depending on label length). A `Column` layout keeps every button's
// height identical regardless of whether its label wraps.
@Composable
private fun ForYouActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = MaterialTheme.padding.extraSmall),
        modifier = Modifier
            .defaultMinSize(minHeight = 56.dp)
            .widthIn(min = 56.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// KMK v0.8.17-fix1: shows one short toast summarizing a bulk For You action's outcome. Failures are
// never silently swallowed -- a partial or total failure gets distinct wording from a clean success.
// KMK v0.8.19: message text now comes from the shared bulkTasteActionMessage() (BulkTasteActionFeedback.kt),
// naming the specific action (Love/Like/Dislike/Not Interested/Clear Rating) with correct
// singular/plural wording, instead of the previous generic "N manga updated" text -- the same
// builder Loved/Liked/Disliked use, so the wording matches across screens for the same action.
// KMK v0.8.19 code-review follow-up: still no Undo here (unlike Loved/Liked/Disliked), and this is a
// real, acknowledged gap in the Undo Journal's safety-net coverage -- not merely a documentation note.
// The blocker is structural: this Tab renders inside `BrowseTab`'s own shared `Scaffold` (see the
// `RecommendationCollectionQuickAccessPanel` overlay comment further up this file), which has no
// `SnackbarHostState` and is shared across every Browse tab, not just For You. Wiring Undo here
// requires either (a) adding a `SnackbarHost` to that shared Scaffold -- a change that affects every
// Browse tab's layout and needs its own dedicated verification pass, not a drive-by edit alongside
// unrelated fixes -- or (b) giving this Tab its own local Snackbar surface layered above its content,
// mirroring the `showFabMenu`-style overlay pattern already used near line 262. Both are real,
// scoped implementation work, tracked as a required next step in
// `docs/recommendations/NEXT_WORK.md`, not a permanently-deferred nice-to-have. Until it lands, the
// Evaluation Mode Undo Journal (`EvaluationModeActionHistoryScreen`) still records these actions and
// they remain undo-able there even though this Tab's own inline feedback has no Undo action.
private fun showBulkActionFeedback(context: android.content.Context, action: BulkTasteActionType, outcome: BulkTasteOutcome) {
    val message = bulkTasteActionMessage(context, action, outcome) ?: return
    context.toast(message)
}
// KMK <--

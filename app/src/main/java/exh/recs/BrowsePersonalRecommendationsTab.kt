package exh.recs

// KMK -->
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import exh.recs.loved.LovedMangaScreen
import exh.recs.loved.RatedMangaScreen
import exh.recs.settings.RecommendationSettingsIndexScreen
import exh.recs.share.RecommendationBundleExporter
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction

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

    return TabContent(
        titleRes = KMR.strings.taste_recommendations_tab,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.action_webview_refresh),
                icon = Icons.Outlined.Refresh,
                onClick = screenModel::refresh,
            ),
            // KMK --> v0.7.0: Loved Manga view
            AppBar.Action(
                title = stringResource(KMR.strings.loved_manga_title),
                icon = Icons.Outlined.Favorite,
                onClick = { navigator.push(LovedMangaScreen()) },
            ),
            // KMK <--
            // KMK --> v0.7.35: Liked / Disliked manga entry points
            AppBar.Action(
                title = stringResource(KMR.strings.liked_manga_title),
                icon = Icons.Outlined.ThumbUp,
                onClick = { navigator.push(RatedMangaScreen(MangaRating.LIKE.value)) },
            ),
            AppBar.Action(
                title = stringResource(KMR.strings.disliked_manga_title),
                icon = Icons.Outlined.ThumbDown,
                onClick = { navigator.push(RatedMangaScreen(MangaRating.DISLIKE.value)) },
            ),
            // KMK <--
            // KMK --> v0.7.5: Export Top Picks
            AppBar.Action(
                title = stringResource(KMR.strings.rec_bundle_export_top_picks),
                icon = Icons.Outlined.Share,
                onClick = {
                    pendingExportIsTopPicks = true
                    exportLauncher.launch("kmk_top_picks.json")
                },
            ),
            // KMK <--
            AppBar.Action(
                title = stringResource(KMR.strings.taste_settings_title),
                icon = Icons.Outlined.Settings,
                // KMK v0.8.8: entry point is now the concise settings index, not the single big screen directly.
                onClick = { navigator.push(RecommendationSettingsIndexScreen) },
            ),
        ),
        content = { contentPadding, _ ->
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
                // KMK --> v0.7.25: retry after offline
                onRetry = screenModel::refresh,
                // KMK <--
                contentPadding = contentPadding,
            )
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
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
) {
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
                    modifier = Modifier.fillMaxSize(),
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
                                        onClick = onClickItem,
                                        onLongClick = onClickItem,
                                        selection = emptyList(),
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
                                    title = source.name,
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
                                            onClick = onClickItem,
                                            onLongClick = onClickItem,
                                            selection = emptyList(),
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
            }
        }
    }
}
// KMK <--

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.toast
import exh.recs.KmkRecsReleaseNotes
import exh.recs.links.LinkGroupManagementScreen
import exh.recs.share.RecommendationBundleExporter
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

class LovedMangaScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val screenModel = rememberScreenModel { LovedMangaScreenModel() }
        val state by screenModel.state.collectAsState()

        // KMK --> v0.7.5: export Loved Manga as JSON bundle
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
        // KMK <--

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.loved_manga_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                    // KMK --> v0.7.5: export action
                    actions = {
                        AppBarActions(
                            persistentListOf(
                                // KMK --> v0.7.30: link group management
                                AppBar.Action(
                                    title = stringResource(KMR.strings.link_group_management_title),
                                    icon = Icons.Outlined.Link,
                                    onClick = { navigator.push(LinkGroupManagementScreen()) },
                                ),
                                // KMK <--
                                AppBar.Action(
                                    title = stringResource(KMR.strings.rec_bundle_export_loved_manga),
                                    icon = Icons.Outlined.Share,
                                    onClick = {
                                        val s = screenModel.state.value
                                        if (s is LovedMangaScreenModel.State.Success && s.displayItems.isNotEmpty()) {
                                            exportLauncher.launch("kmk_loved_manga.json")
                                        } else {
                                            scope.launch { withUIContext { context.toast(KMR.strings.rec_bundle_export_empty) } }
                                        }
                                    },
                                ),
                            ),
                        )
                    },
                    // KMK <--
                )
            },
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
                        text = stringResource(KMR.strings.loved_manga_empty),
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
                        text = s.error.message ?: stringResource(KMR.strings.loved_manga_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(MaterialTheme.padding.medium),
                    )
                }

                is LovedMangaScreenModel.State.Success -> {
                    val items = s.displayItems
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(96.dp + MaterialTheme.padding.small),
                        contentPadding = contentPadding,
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            GroupDuplicatesToggleRow(
                                checked = s.groupDuplicates,
                                onToggle = screenModel::toggleGroupDuplicates,
                            )
                        }
                        // KMK --> v0.7.14: sort mode chips
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LoveSortRow(
                                current = s.sortMode,
                                onSelect = screenModel::setSortMode,
                            )
                        }
                        // KMK <--
                        // KMK --> v0.7.15: feedback when grouping is on but no duplicates were found
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
                        // KMK <--
                        items(items, key = { "${it.taste.source}|${it.taste.url}" }) { item ->
                            Box {
                                MangaItem(
                                    title = item.manga?.title ?: item.taste.title,
                                    cover = item.manga?.asMangaCover() ?: fallbackCover(item),
                                    isFavorite = item.manga?.favorite ?: false,
                                    onClick = { navigator.push(MangaScreen(item.taste.mangaId, true)) },
                                    onLongClick = { navigator.push(MangaScreen(item.taste.mangaId, true)) },
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
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupDuplicatesToggleRow(
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

// KMK --> v0.7.14: sort mode chip row
@Composable
private fun LoveSortRow(
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
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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
// KMK <--

private fun fallbackCover(item: LovedDisplayItem): MangaCover = MangaCover(
    mangaId = item.taste.mangaId,
    sourceId = item.taste.source,
    isMangaFavorite = false,
    ogUrl = null,
    lastModified = 0L,
)
// KMK <--

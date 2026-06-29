package exh.recs

// KMK -->
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.components.MangaItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.toast
import exh.recs.share.RecommendationBundleExporter
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TopPicksScreen(
    private val mangaIds: ArrayList<Long>,
    private val isPartial: Boolean = false,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val screenModel = rememberScreenModel { TopPicksScreenModel(mangaIds) }
        val state by screenModel.state.collectAsState()

        // KMK --> v0.7.5: export Top Picks as JSON bundle
        val exportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val mangas = screenModel.state.value.mangas
            scope.launch {
                val exporter = RecommendationBundleExporter()
                val bundle = exporter.buildTopPicksFromMangaBundle(mangas, KmkRecsReleaseNotes.VERSION_NAME)
                exporter.writeToUri(context, uri, bundle)
                    .onSuccess { withUIContext { context.toast(KMR.strings.rec_bundle_export_success) } }
                    .onFailure { withUIContext { context.toast(KMR.strings.rec_bundle_export_failure) } }
            }
        }
        // KMK <--

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.rec_top_picks_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                    // KMK --> v0.7.5: export action
                    actions = {
                        AppBarActions(
                            persistentListOf(
                                AppBar.Action(
                                    title = stringResource(KMR.strings.rec_bundle_export_top_picks),
                                    icon = Icons.Outlined.Share,
                                    onClick = {
                                        if (state.mangas.isNotEmpty()) {
                                            exportLauncher.launch("kmk_top_picks.json")
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
            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                state.mangas.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(KMR.strings.rec_top_picks_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp + MaterialTheme.padding.small),
                    contentPadding = contentPadding,
                ) {
                    if (isPartial) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = stringResource(KMR.strings.rec_top_picks_partial),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = MaterialTheme.padding.medium,
                                        vertical = MaterialTheme.padding.small,
                                    ),
                            )
                        }
                    }
                    items(state.mangas, key = { it.id }) { manga ->
                        MangaItem(
                            title = manga.title,
                            cover = manga.asMangaCover(),
                            isFavorite = manga.favorite,
                            onClick = { navigator.push(MangaScreen(manga.id, true)) },
                            onLongClick = { navigator.push(MangaScreen(manga.id, true)) },
                        )
                    }
                }
            }
        }
    }
}

private class TopPicksScreenModel(
    mangaIds: List<Long>,
    private val getManga: GetManga = Injekt.get(),
) : StateScreenModel<TopPicksScreenModel.State>(State()) {

    init {
        screenModelScope.launch {
            val mangas = mangaIds.mapNotNull { getManga.await(it) }
            mutableState.value = State(mangas = mangas, isLoading = false)
        }
    }

    @Immutable
    data class State(
        val mangas: List<Manga> = emptyList(),
        val isLoading: Boolean = true,
    )
}
// KMK <--

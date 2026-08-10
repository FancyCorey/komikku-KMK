package exh.recs.matching

// KMK -->
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.fastFilter
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.components.GlobalSearchCardRow
import eu.kanade.presentation.browse.components.GlobalSearchErrorResultItem
import eu.kanade.presentation.browse.components.GlobalSearchLoadingResultItem
import eu.kanade.presentation.browse.components.GlobalSearchResultItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.formattedMessage
import exh.util.EvaluationModeFormatter
import exh.util.rememberEvaluationModeEnabled
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

// v0.7.1: constructor stores only serializable primitives to prevent BadParcelableException
class CrossExtensionMatchScreen(
    private val originMangaId: Long,
    private val modeKey: String,
    private val ratingValue: Int? = null,
) : Screen() {

    companion object {
        fun fromMode(originMangaId: Long, mode: CrossExtensionMatchMode): CrossExtensionMatchScreen {
            val args = CrossExtensionMatchRouteMode.fromMode(mode)
            return CrossExtensionMatchScreen(originMangaId, args.modeKey, args.ratingValue)
        }
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val mode = remember(modeKey, ratingValue) {
            CrossExtensionMatchRouteMode.toMode(modeKey, ratingValue)
        }

        if (mode == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(KMR.strings.rec_match_mode_invalid))
            }
            return
        }

        val screenModel = rememberScreenModel { CrossExtensionMatchScreenModel(originMangaId, mode) }
        val state by screenModel.state.collectAsState()

        val rating = (mode as? CrossExtensionMatchMode.Rating)?.rating

        val screenTitle = when (mode) {
            // KMK --> v0.6.20: MarkSeen title
            CrossExtensionMatchMode.MarkSeen -> stringResource(KMR.strings.rec_match_title_seen)
            // KMK <--
            // KMK --> v0.7.0: Favorite title
            CrossExtensionMatchMode.Favorite -> stringResource(KMR.strings.rec_match_title_favorite)
            // KMK <--
            is CrossExtensionMatchMode.Rating -> when (rating) {
                MangaRating.LOVE -> stringResource(KMR.strings.rec_match_title_love)
                MangaRating.LIKE -> stringResource(KMR.strings.rec_match_title_like)
                MangaRating.DISLIKE -> stringResource(KMR.strings.rec_match_title_dislike)
                null -> stringResource(KMR.strings.rec_match_title_love)
            }
        }

        val confirmLabel = when {
            // KMK --> v0.6.20: MarkSeen confirm label
            state.isApplying && mode == CrossExtensionMatchMode.MarkSeen -> stringResource(KMR.strings.rec_match_applying_seen)
            mode == CrossExtensionMatchMode.MarkSeen -> stringResource(KMR.strings.rec_match_apply_seen, state.selectedKeys.size)
            // KMK <--
            // KMK --> v0.7.0: Favorite confirm label
            state.isApplying && mode == CrossExtensionMatchMode.Favorite -> stringResource(KMR.strings.rec_match_applying_favorite)
            mode == CrossExtensionMatchMode.Favorite -> stringResource(KMR.strings.rec_match_apply_favorite, state.selectedKeys.size)
            // KMK <--
            state.isApplying -> stringResource(KMR.strings.rec_match_applying)
            else -> when (rating) {
                MangaRating.LOVE -> stringResource(KMR.strings.rec_match_apply_love, state.selectedKeys.size)
                MangaRating.LIKE -> stringResource(KMR.strings.rec_match_apply_like, state.selectedKeys.size)
                MangaRating.DISLIKE -> stringResource(KMR.strings.rec_match_apply_dislike, state.selectedKeys.size)
                null -> stringResource(KMR.strings.rec_match_apply_love, state.selectedKeys.size)
            }
        }

        val selectionSubtitle = stringResource(
            KMR.strings.rec_match_selection_count,
            state.selectedKeys.size,
            state.totalCandidates,
        )

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = screenTitle,
                    subtitle = selectionSubtitle,
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(MaterialTheme.padding.medium),
                ) {
                    Button(
                        onClick = { screenModel.applyRating { navigator.pop() } },
                        enabled = !state.isApplying && state.selectedKeys.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(confirmLabel)
                    }
                }
            },
        ) { contentPadding ->
            if (state.total == 0) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(modifier = Modifier.padding(contentPadding)) {
                    if (state.progress < state.total) {
                        LinearProgressIndicator(
                            progress = { state.progress.toFloat() / state.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    val allManga = state.items.values
                        .filterIsInstance<MatchItemResult.Success>()
                        .flatMap { it.result }
                    val selectedMangaList = allManga.fastFilter {
                        MangaIdentityKey(it.source, it.url) in state.selectedKeys
                    }
                    LazyColumn {
                        items(
                            items = state.items.entries.toList(),
                            key = { (source, _) -> source.id },
                        ) { (source, result) ->
                            GlobalSearchResultItem(
                                // KMK -->
                                title = if (rememberEvaluationModeEnabled()) {
                                    EvaluationModeFormatter.sourceLabel(source.id)
                                } else {
                                    source.name
                                },
                                // KMK <--
                                subtitle = source.lang.uppercase(),
                                onClick = {},
                            ) {
                                when (result) {
                                    MatchItemResult.Loading -> GlobalSearchLoadingResultItem()
                                    // KMK v0.8.12: routed through the shared formattedMessage
                                    // classifier (same one For You rows use) instead of a raw
                                    // localizedMessage/javaClass.simpleName fallback, which leaked
                                    // developer-facing wrapper class names (e.g.
                                    // "RecoverableSourceRuntimeException: ...") into this row.
                                    is MatchItemResult.Error -> GlobalSearchErrorResultItem(
                                        message = with(LocalContext.current) {
                                            result.throwable.formattedMessage
                                        },
                                    )
                                    is MatchItemResult.Success -> GlobalSearchCardRow(
                                        titles = result.result,
                                        getManga = screenModel::getManga,
                                        onClick = { manga ->
                                            screenModel.toggleSelection(MangaIdentityKey(manga.source, manga.url))
                                        },
                                        onLongClick = { manga ->
                                            screenModel.toggleSelection(MangaIdentityKey(manga.source, manga.url))
                                        },
                                        selection = selectedMangaList,
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
// KMK <--

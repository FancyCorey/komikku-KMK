package exh.recs

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifSourcesLoaded
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.browse.components.BulkFavoriteDialogs
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import exh.recs.RecommendsScreen.Args.CrossSourceGroupSeed
import exh.recs.RecommendsScreen.Args.MergedSourceMangas
import exh.recs.RecommendsScreen.Args.SingleSourceManga
import exh.recs.batch.RankedSearchResults
import exh.recs.components.RecommendsScreen
import exh.recs.sources.CrossExtensionGenreSearchSource
import exh.recs.sources.RECOMMENDS_SOURCE
import exh.recs.sources.StaticResultPagingSource
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.Serializable

class RecommendsScreen(private val args: Args) : Screen() {

    sealed interface Args : Serializable {
        data class SingleSourceManga(val mangaId: Long, val sourceId: Long) : Args
        data class MergedSourceMangas(val mergedResults: List<RankedSearchResults>) : Args
        // KMK --> v0.7.43: Loved/Liked group-seeded recommendations. Only primitive identity is
        // passed through the route (Voyager Screen must stay Serializable/Parcelable-safe) — the
        // screen model rebuilds the full GroupRecommendationSeed via GroupRecommendationSeedBuilder.
        data class CrossSourceGroupSeed(val sourceId: Long, val url: String, val primaryTitle: String) : Args
        // KMK <--
    }

    @Composable
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { RecommendsScreenModel(args) }
        val state by screenModel.state.collectAsState()

        // KMK -->
        val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
        val crossExtensionEnabled by sourcePreferences.recommendationCrossExtensionSearch().collectAsState()

        val bulkFavoriteScreenModel = rememberScreenModel { BulkFavoriteScreenModel() }
        val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()

        val haptic = LocalHapticFeedback.current

        BackHandler(enabled = bulkFavoriteState.selectionMode) {
            bulkFavoriteScreenModel.backHandler()
        }
        // KMK <--

        val onClickItem = { manga: Manga ->
            navigator.push(
                when (manga.source) {
                    RECOMMENDS_SOURCE -> SourcesScreen(SourcesScreen.SmartSearchConfig(manga.ogTitle))
                    else -> MangaScreen(manga.id, true)
                },
            )
        }

        val onLongClickItem = { manga: Manga ->
            when (manga.source) {
                RECOMMENDS_SOURCE -> WebViewActivity.newIntent(context, manga.url, title = manga.title).let(context::startActivity)
                else -> {
                    // KMK -->
                    // Add to favorite
                    bulkFavoriteScreenModel.addRemoveManga(
                        manga,
                        haptic,
                    )
                    // KMK <--
                }
            }
        }

        RecommendsScreen(
            title = if (args is SingleSourceManga || args is CrossSourceGroupSeed) {
                stringResource(SYMR.strings.similar, state.title.orEmpty())
            } else {
                stringResource(SYMR.strings.rec_common_recommendations)
            },
            state = state,
            navigateUp = navigator::pop,
            getManga = @Composable { manga: Manga -> screenModel.getManga(manga) },
            onClickSource = { pagingSource ->
                // KMK -->
                // CrossExtensionGenreSearchSource instances all share the same class name, so
                // they cannot be identified via BrowseRecommendsScreenModel's name-lookup.
                // Navigate directly to BrowseSourceScreen with genre filters pre-applied instead.
                if (pagingSource is CrossExtensionGenreSearchSource) {
                    navigator.push(
                        BrowseSourceScreen(
                            sourceId = pagingSource.associatedSourceId!!,
                            listingQuery = pagingSource.cachedTextQuery.ifBlank { null },
                            filtersJson = pagingSource.cachedFiltersJson,
                        ),
                    )
                } else {
                    // KMK <--
                    // Pass class name of paging source as screens need to be serializable
                    val browseArgs: BrowseRecommendsScreen.Args? = when (args) {
                        is SingleSourceManga ->
                            BrowseRecommendsScreen.Args.SingleSourceManga(
                                args.mangaId,
                                args.sourceId,
                                pagingSource::class.qualifiedName!!,
                            )
                        is MergedSourceMangas ->
                            BrowseRecommendsScreen.Args.MergedSourceMangas(
                                (pagingSource as StaticResultPagingSource).data,
                            )
                        // KMK --> v0.7.43: group seed drill-down reuses the primary manga
                        // (trackers already only ever use the primary manga as their seed).
                        is CrossSourceGroupSeed ->
                            state.primaryMangaId?.let { primaryMangaId ->
                                BrowseRecommendsScreen.Args.SingleSourceManga(
                                    primaryMangaId,
                                    args.sourceId,
                                    pagingSource::class.qualifiedName!!,
                                )
                            }
                        // KMK <--
                    }
                    if (browseArgs != null) {
                        navigator.push(
                            BrowseRecommendsScreen(browseArgs, pagingSource.associatedSourceId == null),
                        )
                    }
                    // KMK -->
                }
                // KMK <--
            },
            onClickItem = { onClickItem(it) },
            onLongClickItem = { onLongClickItem(it) },
            // KMK -->
            actions = {
                // Icon button to toggle cross-extension genre search on/off.
                // Tapping replaces the screen with a fresh instance that reads the updated
                // preference at init time, enabling or disabling the cross-extension sources.
                IconButton(
                    onClick = {
                        sourcePreferences.recommendationCrossExtensionSearch().set(!crossExtensionEnabled)
                        navigator.replace(RecommendsScreen(args))
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.TravelExplore,
                        contentDescription = stringResource(KMR.strings.action_toggle_extension_search),
                        tint = if (crossExtensionEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Unspecified
                        },
                    )
                }
            },
            // KMK <--
        )

        // KMK -->
        BulkFavoriteDialogs(
            bulkFavoriteScreenModel = bulkFavoriteScreenModel,
            dialog = bulkFavoriteState.dialog,
        )
        // KMK <--
    }
}

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.util.fastFilter
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.components.GlobalSearchCardRow
import eu.kanade.presentation.browse.components.GlobalSearchErrorResultItem
import eu.kanade.presentation.browse.components.GlobalSearchLoadingResultItem
import eu.kanade.presentation.browse.components.GlobalSearchResultItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.formattedMessage
import exh.recs.settings.RecommendationDiagnosticsSettingsScreen
import exh.util.EvaluationModeFormatter
import exh.util.rememberEvaluationModeEnabled
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import java.util.Locale

// v0.7.1: constructor stores only serializable primitives to prevent BadParcelableException
class CrossExtensionMatchScreen(
    private val originMangaId: Long,
    private val modeKey: String,
    private val ratingValue: Int? = null,
    private val localStatus: Int? = null,
) : Screen() {

    companion object {
        fun fromMode(originMangaId: Long, mode: CrossExtensionMatchMode): CrossExtensionMatchScreen {
            val args = CrossExtensionMatchRouteMode.fromMode(mode)
            return CrossExtensionMatchScreen(originMangaId, args.modeKey, args.ratingValue, args.localStatus)
        }
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val mode = remember(modeKey, ratingValue, localStatus) {
            CrossExtensionMatchRouteMode.toMode(modeKey, ratingValue, localStatus)
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
            // KMK --> v0.7.0: Favorite title
            CrossExtensionMatchMode.Favorite -> stringResource(KMR.strings.rec_match_title_favorite)
            is CrossExtensionMatchMode.LocalTracking -> stringResource(KMR.strings.local_tracking_other_versions)
            // KMK <--
            is CrossExtensionMatchMode.Rating -> when (rating) {
                MangaRating.LOVE -> stringResource(KMR.strings.rec_match_title_love)
                MangaRating.LIKE -> stringResource(KMR.strings.rec_match_title_like)
                MangaRating.DISLIKE -> stringResource(KMR.strings.rec_match_title_dislike)
                // KMK v0.8.21-fix3: R1 correction -- Not Interested is Rating(NOT_INTERESTED) now,
                // reached through this branch like every other value, not a separate MarkSeen mode.
                MangaRating.NOT_INTERESTED -> stringResource(KMR.strings.rec_match_title_seen)
                null -> stringResource(KMR.strings.rec_match_title_love)
            }
        }

        val confirmLabel = when {
            // KMK --> v0.7.0: Favorite confirm label
            state.isApplying && mode == CrossExtensionMatchMode.Favorite -> stringResource(KMR.strings.rec_match_applying_favorite)
            mode == CrossExtensionMatchMode.Favorite -> pluralStringResource(KMR.plurals.rec_match_apply_favorite, count = state.selectedKeys.size, state.selectedKeys.size)
            mode is CrossExtensionMatchMode.LocalTracking -> pluralStringResource(KMR.plurals.local_tracking_apply_other_versions, count = state.selectedKeys.size, state.selectedKeys.size)
            // KMK <--
            state.isApplying -> stringResource(KMR.strings.rec_match_applying)
            else -> when (rating) {
                MangaRating.LOVE -> pluralStringResource(KMR.plurals.rec_match_apply_love, count = state.selectedKeys.size, state.selectedKeys.size)
                MangaRating.LIKE -> pluralStringResource(KMR.plurals.rec_match_apply_like, count = state.selectedKeys.size, state.selectedKeys.size)
                MangaRating.DISLIKE -> pluralStringResource(KMR.plurals.rec_match_apply_dislike, count = state.selectedKeys.size, state.selectedKeys.size)
                MangaRating.NOT_INTERESTED -> pluralStringResource(KMR.plurals.rec_match_apply_seen, count = state.selectedKeys.size, state.selectedKeys.size)
                null -> pluralStringResource(KMR.plurals.rec_match_apply_love, count = state.selectedKeys.size, state.selectedKeys.size)
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
                    actions = {
                        AppBarActions(
                            persistentListOf(
                                AppBar.Action(
                                    title = stringResource(KMR.strings.best_version_settings_action),
                                    icon = Icons.Outlined.Settings,
                                    onClick = {
                                        navigator.push(
                                            RecommendationDiagnosticsSettingsScreen(
                                                anchor = "same_manga_preselect",
                                            ),
                                        )
                                    },
                                ),
                            ),
                        )
                    },
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
                    state.identityFeedback?.let { feedback ->
                        Text(
                            text = stringResource(
                                when (feedback) {
                                    CrossSourceIdentityMutationResult.APPLIED -> KMR.strings.identity_review_applied
                                    CrossSourceIdentityMutationResult.UNCHANGED -> KMR.strings.identity_review_unchanged
                                    CrossSourceIdentityMutationResult.CONFLICT -> KMR.strings.identity_review_conflict
                                    CrossSourceIdentityMutationResult.FAILED -> KMR.strings.identity_review_failed
                                },
                            ),
                            color = if (feedback == CrossSourceIdentityMutationResult.FAILED || feedback == CrossSourceIdentityMutationResult.CONFLICT) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = MaterialTheme.padding.small),
                        )
                    }
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
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = screenModel::updateSearchQuery,
                        label = { Text(stringResource(KMR.strings.rec_match_search_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { screenModel.searchCurrentQuery() }),
                        trailingIcon = {
                            IconButton(onClick = screenModel::searchCurrentQuery) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = stringResource(KMR.strings.rec_match_search_action),
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MaterialTheme.padding.medium),
                    )
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
                                subtitle = source.lang.uppercase(Locale.ROOT),
                                onClick = { screenModel.searchSource(source) },
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
                                        isSelected = { manga ->
                                            MangaIdentityKey(manga.source, manga.url) in state.selectedKeys
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
}
// KMK <--

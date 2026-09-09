package eu.kanade.presentation.browse

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import eu.kanade.presentation.browse.components.BrowseSourceComfortableGrid
import eu.kanade.presentation.browse.components.BrowseSourceCompactGrid
import eu.kanade.presentation.browse.components.BrowseSourceEHentaiList
import eu.kanade.presentation.browse.components.BrowseSourceList
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.source.DebugBrowseFixtureSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseDeterministicFixtureException
import exh.metadata.metadata.RaisedSearchMetadata
import exh.source.isEhBasedSource
import exh.util.rememberEvaluationModeEnabled
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.StateFlow
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.model.StubSource
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.source.local.LocalSource

@Composable
fun BrowseSourceContent(
    source: Source?,
    sourceId: Long? = source?.id,
    isDeterministicFixtureSource: Boolean = false,
    mangaList: LazyPagingItems<StateFlow</* SY --> */Pair<Manga, RaisedSearchMetadata?>/* SY <-- */>>,
    columns: GridCells,
    // SY -->
    ehentaiBrowseDisplayMode: Boolean,
    // SY <--
    displayMode: LibraryDisplayMode,
    snackbarHostState: SnackbarHostState,
    contentPadding: PaddingValues,
    // SY -->
    onWebViewClick: (() -> Unit)?,
    onHelpClick: (() -> Unit)?,
    onLocalSourceHelpClick: (() -> Unit)?,
    // SY <--
    onMangaClick: (Manga) -> Unit,
    onMangaLongClick: (Manga) -> Unit,
    // KMK -->
    selection: List<Manga>,
    // KMK <--
) {
    val context = LocalContext.current

    val emptyStatePolicy = resolveBrowseEmptyStatePolicy(
        sourceId = sourceId,
        sourceName = source?.name,
        isDeterministicFixtureSource = isDeterministicFixtureSource,
        isLocalSource = source is LocalSource,
        localSourceHelpAvailable = onLocalSourceHelpClick != null,
        refresh = mangaList.loadState.refresh,
        append = mangaList.loadState.append,
    )
    val errorState = emptyStatePolicy.error

    val getErrorMessage: (LoadState.Error) -> String = { state ->
        if (isDeterministicBrowseFixtureError(state.error)) {
            context.stringResource(KMR.strings.rec_error_network)
        } else {
            with(context) { state.error.formattedMessage }
        }
    }

    LaunchedEffect(errorState) {
        if (mangaList.itemCount > 0 && errorState != null && errorState is LoadState.Error) {
            val result = snackbarHostState.showSnackbar(
                message = getErrorMessage(errorState),
                actionLabel = context.stringResource(MR.strings.action_retry),
                duration = SnackbarDuration.Indefinite,
            )
            when (result) {
                SnackbarResult.Dismissed -> snackbarHostState.currentSnackbarData?.dismiss()
                SnackbarResult.ActionPerformed -> mangaList.retry()
            }
        }
    }

    if (mangaList.itemCount == 0 && mangaList.loadState.refresh is LoadState.Loading) {
        LoadingScreen(Modifier.padding(contentPadding))
        return
    }

    if (mangaList.itemCount == 0) {
        EmptyScreen(
            modifier = Modifier.padding(contentPadding),
            message = when (emptyStatePolicy.message) {
                BrowseEmptyStateMessage.ERROR -> getErrorMessage(requireNotNull(errorState))
                BrowseEmptyStateMessage.SOURCE_UNAVAILABLE ->
                    context.stringResource(KMR.strings.rec_error_network)
                BrowseEmptyStateMessage.NO_RESULTS -> stringResource(MR.strings.no_results_found)
            },
            actions = if (!emptyStatePolicy.showRetry) {
                persistentListOf(
                    EmptyScreenAction(
                        stringRes = MR.strings.local_source_help_guide,
                        icon = Icons.AutoMirrored.Outlined.HelpOutline,
                        onClick = requireNotNull(onLocalSourceHelpClick),
                    ),
                )
            } else {
                listOfNotNull(
                    EmptyScreenAction(
                        stringRes = MR.strings.action_retry,
                        icon = Icons.Outlined.Refresh,
                        onClick = mangaList::refresh,
                    ),
                    // SY -->
                    if (onWebViewClick != null) {
                        EmptyScreenAction(
                            MR.strings.action_open_in_web_view,
                            icon = Icons.Outlined.Public,
                            onClick = onWebViewClick,
                        )
                    } else {
                        null
                    },
                    if (onHelpClick != null) {
                        EmptyScreenAction(
                            MR.strings.label_help,
                            icon = Icons.AutoMirrored.Outlined.HelpOutline,
                            onClick = onHelpClick,
                        )
                    } else {
                        null
                    },
                    // SY <--
                ).toImmutableList()
            },
        )

        return
    }

    // SY -->
    if (source?.isEhBasedSource() == true && ehentaiBrowseDisplayMode) {
        BrowseSourceEHentaiList(
            mangaList = mangaList,
            contentPadding = contentPadding,
            onMangaClick = onMangaClick,
            onMangaLongClick = onMangaLongClick,
            // KMK -->
            selection = selection,
            // KMK <--
        )
        return
    }
    // SY <--

    when (displayMode) {
        LibraryDisplayMode.ComfortableGrid -> {
            BrowseSourceComfortableGrid(
                mangaList = mangaList,
                columns = columns,
                contentPadding = contentPadding,
                onMangaClick = onMangaClick,
                onMangaLongClick = onMangaLongClick,
                // KMK -->
                selection = selection,
                // KMK <--
            )
        }
        // KMK -->
        LibraryDisplayMode.ComfortableGridPanorama -> {
            BrowseSourceComfortableGrid(
                mangaList = mangaList,
                columns = columns,
                contentPadding = contentPadding,
                onMangaClick = onMangaClick,
                onMangaLongClick = onMangaLongClick,
                // KMK -->
                selection = selection,
                usePanoramaCover = true,
                // KMK <--
            )
        }
        // KMK <--
        LibraryDisplayMode.List -> {
            BrowseSourceList(
                mangaList = mangaList,
                contentPadding = contentPadding,
                onMangaClick = onMangaClick,
                onMangaLongClick = onMangaLongClick,
                // KMK -->
                selection = selection,
                // KMK <--
            )
        }
        LibraryDisplayMode.CompactGrid, LibraryDisplayMode.CoverOnlyGrid -> {
            BrowseSourceCompactGrid(
                mangaList = mangaList,
                columns = columns,
                contentPadding = contentPadding,
                onMangaClick = onMangaClick,
                onMangaLongClick = onMangaLongClick,
                // KMK -->
                selection = selection,
                // KMK <--
            )
        }
    }
}

internal fun isDeterministicBrowseFixtureError(error: Throwable): Boolean =
    error is BrowseDeterministicFixtureException

internal enum class BrowseEmptyStateMessage {
    ERROR,
    SOURCE_UNAVAILABLE,
    NO_RESULTS,
}

internal data class BrowseEmptyStatePolicy(
    val message: BrowseEmptyStateMessage,
    val error: LoadState.Error?,
    val showRetry: Boolean,
)

internal fun resolveBrowseEmptyStatePolicy(
    sourceId: Long?,
    sourceName: String?,
    isDeterministicFixtureSource: Boolean = false,
    isLocalSource: Boolean,
    localSourceHelpAvailable: Boolean,
    refresh: LoadState,
    append: LoadState,
): BrowseEmptyStatePolicy {
    val error = refresh as? LoadState.Error ?: append as? LoadState.Error
    val isFixture = isDeterministicFixtureSource
    val message = when {
        error != null && isDeterministicBrowseFixtureError(error.error) ->
            BrowseEmptyStateMessage.SOURCE_UNAVAILABLE
        error != null -> BrowseEmptyStateMessage.ERROR
        isFixture -> BrowseEmptyStateMessage.SOURCE_UNAVAILABLE
        else -> BrowseEmptyStateMessage.NO_RESULTS
    }
    return BrowseEmptyStatePolicy(
        message = message,
        error = error,
        showRetry = !(isLocalSource && localSourceHelpAvailable),
    )
}

internal fun isDeterministicBrowseFixtureSource(
    source: Source?,
    sourceId: Long? = source?.id,
): Boolean =
    sourceId == DebugBrowseFixtureSource.ID ||
        source?.id == DebugBrowseFixtureSource.ID ||
        source?.name == DebugBrowseFixtureSource().name

@Composable
internal fun MissingSourceScreen(
    source: StubSource,
    navigateUp: () -> Unit,
) {
    val evaluationModeEnabled = rememberEvaluationModeEnabled()
    val title = BrowseSourceTitlePolicy.resolve(evaluationModeEnabled, source.id) { source.name }

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = title,
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        EmptyScreen(
            message = stringResource(MR.strings.source_not_installed, title),
            modifier = Modifier.padding(paddingValues),
        )
    }
}

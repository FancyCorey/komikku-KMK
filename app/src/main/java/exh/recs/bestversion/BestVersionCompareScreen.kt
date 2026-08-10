package exh.recs.bestversion

// KMK --> v0.7.8
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.KmkEmptyStateArtwork
import eu.kanade.presentation.components.KmkEmptyStateIllustration
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import exh.recs.RecommendationErrorKind
import exh.recs.matching.MangaIdentityKey
import exh.recs.matching.SameMangaCandidateResult
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK --> v0.7.46: maps a RecommendationErrorClassifier storage key to a KMR string. Every exception
// caught in BestVersionCompareScreenModel is now routed through RecommendationErrorClassifier first
// (never raw `.message`), so the only non-key value `message` can hold here is one deliberate,
// already-curated, non-exception-derived literal ("Could not load origin manga." in
// confirmMigration()) — passed through verbatim since it isn't raw exception text.
@Composable
private fun recommendationErrorText(key: String): String {
    val kind = RecommendationErrorKind.fromStorageKey(key) ?: return key
    return stringResource(
        when (kind) {
            RecommendationErrorKind.Network -> KMR.strings.rec_error_network
            RecommendationErrorKind.Timeout -> KMR.strings.rec_error_timeout
            RecommendationErrorKind.Cancelled -> KMR.strings.rec_error_cancelled
            RecommendationErrorKind.FileAccess -> KMR.strings.rec_error_file_access
            RecommendationErrorKind.ExtensionIncompatible -> KMR.strings.rec_error_extension_incompatible
            RecommendationErrorKind.Internal -> KMR.strings.rec_error_internal
        },
    )
}
// KMK <--

// KMK --> v0.7.9
// KMK v0.8.16-fix1: carries sourceId so the fullscreen dialog can build a source-aware PagePreview
// instead of loading the raw imageUrl string directly.
private data class FullscreenPreviewPage(
    val imageUrl: String,
    val pageIndex: Int,
    val mangaTitle: String,
    val sourceId: Long,
)
// KMK <--

class BestVersionCompareScreen(
    private val originMangaId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { BestVersionCompareScreenModel(originMangaId) }
        val state by screenModel.state.collectAsState()
        // KMK --> v0.7.9: local UI state for fullscreen page preview — does not affect model state
        // KMK --> v0.7.33: stored as 3 primitives so rememberSaveable survives rotation
        var fullscreenPageUrl by rememberSaveable { mutableStateOf<String?>(null) }
        var fullscreenPageIndex by rememberSaveable { mutableStateOf(-1) }
        var fullscreenPageTitle by rememberSaveable { mutableStateOf("") }
        // KMK v0.8.16-fix1: source id, so the fullscreen dialog can build a source-aware PagePreview.
        var fullscreenPageSourceId by rememberSaveable { mutableStateOf(0L) }
        val fullscreenPage: FullscreenPreviewPage? = fullscreenPageUrl?.let {
            FullscreenPreviewPage(it, fullscreenPageIndex, fullscreenPageTitle, fullscreenPageSourceId)
        }
        // KMK <--
        // KMK v0.8.16: full-screen, read-only candidate comparison preview -- stored as two
        // primitives (MangaIdentityKey isn't Parcelable/Serializable) so rememberSaveable survives
        // rotation without holding a non-Parcelable Manga/key object directly.
        var fullscreenCandidateSource by rememberSaveable { mutableStateOf<Long?>(null) }
        var fullscreenCandidateUrl by rememberSaveable { mutableStateOf<String?>(null) }
        val fullscreenCandidateKey: MangaIdentityKey? = fullscreenCandidateSource?.let { src ->
            fullscreenCandidateUrl?.let { url -> MangaIdentityKey(src, url) }
        }
        // KMK <--

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.best_version_screen_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                when (val step = state.step) {
                    BestVersionStep.LoadingOrigin -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(KMR.strings.best_version_loading_origin))
                            }
                        }
                    }

                    BestVersionStep.SearchingCandidates -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(MaterialTheme.padding.medium),
                        ) {
                            Text(
                                text = stringResource(KMR.strings.best_version_searching),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { if (state.searchTotal > 0) state.searchProgress.toFloat() / state.searchTotal else 0f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    BestVersionStep.ConfirmCandidates -> {
                        ConfirmCandidatesContent(
                            state = state,
                            sourceName = screenModel::sourceName,
                            onToggle = screenModel::toggleSelection,
                            onConfirm = screenModel::confirmCandidates,
                        )
                    }

                    BestVersionStep.LoadingChapters -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(KMR.strings.best_version_loading_chapters))
                            }
                        }
                    }

                    BestVersionStep.SelectChapter -> {
                        SelectChapterContent(
                            state = state,
                            sourceName = screenModel::sourceName,
                            onStartPreview = screenModel::startPreview,
                        )
                    }

                    BestVersionStep.LoadingPreview -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(KMR.strings.best_version_loading_preview))
                            }
                        }
                    }

                    BestVersionStep.ComparePreview -> {
                        ComparePreviewContent(
                            state = state,
                            sourceName = screenModel::sourceName,
                            onSelectBest = screenModel::selectBestVersion,
                            // KMK --> v0.7.9
                            onOpenPagePreview = { p ->
                                fullscreenPageUrl = p.imageUrl
                                fullscreenPageIndex = p.pageIndex
                                fullscreenPageTitle = p.mangaTitle
                                fullscreenPageSourceId = p.sourceId
                            },
                            // KMK <--
                            // KMK v0.8.16
                            onOpenFullscreenCandidate = { key ->
                                fullscreenCandidateSource = key.source
                                fullscreenCandidateUrl = key.url
                            },
                            // KMK v0.8.17-fix1: retry exactly one failed/timed-out candidate
                            onRetryCandidate = screenModel::retryCandidate,
                        )
                    }

                    BestVersionStep.PreparingMigration -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(KMR.strings.best_version_preparing_migration))
                            }
                        }
                    }

                    BestVersionStep.Done -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(
                                        // KMK v0.8.18: honest wording -- keepCurrentVersion() never
                                        // migrated/copied anything, so Done must not claim it did.
                                        if (state.keptCurrentVersion) {
                                            KMR.strings.best_version_kept_current_complete
                                        } else {
                                            KMR.strings.best_version_migration_complete
                                        },
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        // KMK v0.8.16: Done should open the manga the user migrated/
                                        // copied to, not bounce back to the origin -- falls back to
                                        // pop() if the target id could not be resolved.
                                        when (
                                            val destination = BestVersionMigrationCompletionPolicy.resolve(
                                                state.completedTargetMangaId,
                                            )
                                        ) {
                                            is BestVersionMigrationCompletionPolicy.Destination.TargetManga ->
                                                navigator.replace(
                                                    eu.kanade.tachiyomi.ui.manga.MangaScreen(
                                                        destination.mangaId,
                                                        true,
                                                    ),
                                                )
                                            BestVersionMigrationCompletionPolicy.Destination.Fallback ->
                                                navigator.pop()
                                        }
                                    },
                                ) {
                                    Text(stringResource(KMR.strings.best_version_done_action))
                                }
                            }
                        }
                    }

                    is BestVersionStep.Error -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(MaterialTheme.padding.medium),
                            ) {
                                Text(
                                    text = recommendationErrorText(step.message),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = { navigator.pop() }) {
                                    Text(stringResource(KMR.strings.best_version_error_back_action))
                                }
                            }
                        }
                    }
                }

                // KMK --> v0.7.9: guard dialog — only render when selected key resolves to a real candidate
                val selectedBestKey = state.selectedBestKey
                val migrationTarget = selectedBestKey?.let { key ->
                    state.selectedCandidates.find { it.source == key.source && it.url == key.url }
                }
                if (selectedBestKey != null && migrationTarget == null && state.step == BestVersionStep.ComparePreview) {
                    LaunchedEffect(selectedBestKey) { screenModel.dismissMigrationDialog() }
                }
                if (migrationTarget != null && state.step == BestVersionStep.ComparePreview) {
                    MigrationConfirmDialog(
                        targetTitle = migrationTarget.title,
                        onDismiss = screenModel::dismissMigrationDialog,
                        onMigrate = { screenModel.confirmMigration(replace = true) },
                        onCopy = { screenModel.confirmMigration(replace = false) },
                    )
                }
                // KMK <--
            }
        }

        // KMK --> v0.7.9: fullscreen page preview overlay
        fullscreenPage?.let { page ->
            FullscreenPagePreviewDialog(
                page = page,
                onDismiss = { fullscreenPageUrl = null },
            )
        }
        // KMK <--
        // KMK --> v0.8.16: fullscreen, read-only candidate comparison preview
        fullscreenCandidateKey?.let { key ->
            // KMK v0.8.18: includes origin -- fullscreen compare must also work from the origin row.
            val candidateManga = state.compareCandidates.find { it.source == key.source && it.url == key.url }
            val previewState = state.candidatePreviews[key]
            if (candidateManga != null && previewState is CandidatePreviewState.Loaded) {
                FullscreenCandidatePreviewDialog(
                    mangaTitle = candidateManga.title,
                    sourceName = screenModel.sourceName(candidateManga.source),
                    pages = previewState.pages,
                    onDismiss = {
                        fullscreenCandidateSource = null
                        fullscreenCandidateUrl = null
                    },
                )
            } else {
                LaunchedEffect(key) {
                    fullscreenCandidateSource = null
                    fullscreenCandidateUrl = null
                }
            }
        }
        // KMK <--
    }
}

@Composable
private fun ConfirmCandidatesContent(
    state: BestVersionCompareScreenModel.State,
    sourceName: (Long) -> String,
    onToggle: (MangaIdentityKey) -> Unit,
    onConfirm: () -> Unit,
) {
    val allCandidates: List<Manga> = state.candidates.values
        .filterIsInstance<SameMangaCandidateResult.Success>()
        .flatMap { it.results }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(KMR.strings.best_version_confirm_candidates_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
        )
        Text(
            text = stringResource(KMR.strings.best_version_confirm_candidates_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            // KMK v0.8.18: origin/current manga always shown first as the fixed comparison
            // baseline -- always included, never toggled off (see
            // BestVersionCompareScreenModel.State.compareCandidates and toggleSelection()'s origin
            // guard).
            state.originManga?.let { origin ->
                item(key = "origin_${origin.source}|${origin.url}") {
                    CandidateRow(
                        manga = origin,
                        sourceName = stringResource(KMR.strings.best_version_current_version_label),
                        selected = true,
                        onClick = {},
                    )
                }
            }
            if (allCandidates.isEmpty()) {
                // Candidate search has completed before this step is shown, so an empty list is a
                // genuine unavailable result rather than a loading placeholder.
                item(key = "no_candidates") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(MaterialTheme.padding.medium),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        KmkEmptyStateIllustration(
                            artwork = KmkEmptyStateArtwork.FIND_BEST_VERSION_UNAVAILABLE,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(72.dp),
                        )
                        Text(
                            text = stringResource(KMR.strings.best_version_no_candidates),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = MaterialTheme.padding.small),
                        )
                    }
                }
            } else {
                items(allCandidates, key = { "${it.source}|${it.url}" }) { manga ->
                    val key = MangaIdentityKey(manga.source, manga.url)
                    val selected = key in state.selectedKeys
                    CandidateRow(
                        manga = manga,
                        sourceName = sourceName(manga.source),
                        selected = selected,
                        onClick = { onToggle(key) },
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(MaterialTheme.padding.medium),
        ) {
            // KMK v0.8.18: the origin is always part of the comparison set once loaded, so the
            // confirm action stays enabled even with zero real candidates selected/found -- the user
            // can still proceed straight to confirming they're already on the best version.
            Button(
                onClick = onConfirm,
                enabled = state.originManga != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(KMR.strings.best_version_confirm_action))
            }
        }
    }
}

@Composable
private fun CandidateRow(
    manga: Manga,
    sourceName: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.extraSmall)
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.padding.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            AsyncImage(
                model = manga.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = manga.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // KMK v0.8.16: show which extension this candidate belongs to
                Text(
                    text = stringResource(KMR.strings.best_version_candidate_source, sourceName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SelectChapterContent(
    state: BestVersionCompareScreenModel.State,
    sourceName: (Long) -> String,
    onStartPreview: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(KMR.strings.best_version_select_chapter_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            // KMK v0.8.18: origin/current manga included as the comparison baseline -- see
            // BestVersionCompareScreenModel.State.compareCandidates.
            items(state.compareCandidates, key = { "${it.source}|${it.url}" }) { manga ->
                val key = MangaIdentityKey(manga.source, manga.url)
                val isOrigin = key == state.originKey
                val chapterState = state.candidateChapters[key]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.extraSmall),
                ) {
                    Column(modifier = Modifier.padding(MaterialTheme.padding.small)) {
                        Text(
                            text = manga.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // KMK v0.8.16: show which extension this candidate belongs to
                        // KMK v0.8.18: origin gets a distinct "Current version" label instead of the
                        // normal source label, so it clearly reads as the baseline, not another find.
                        Text(
                            text = if (isOrigin) {
                                stringResource(KMR.strings.best_version_current_version_label)
                            } else {
                                stringResource(KMR.strings.best_version_candidate_source, sourceName(manga.source))
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOrigin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        when (chapterState) {
                            is CandidateChapterState.Available ->
                                Text(
                                    text = stringResource(KMR.strings.best_version_candidate_chapter, chapterState.chapter.name),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            CandidateChapterState.Unavailable ->
                                Text(
                                    text = stringResource(KMR.strings.best_version_chapter_unavailable),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            is CandidateChapterState.ChapterError ->
                                Text(
                                    text = recommendationErrorText(chapterState.message),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            CandidateChapterState.Loading, null ->
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(MaterialTheme.padding.medium),
        ) {
            Button(
                onClick = onStartPreview,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(KMR.strings.best_version_start_preview_action))
            }
        }
    }
}

@Composable
private fun ComparePreviewContent(
    state: BestVersionCompareScreenModel.State,
    sourceName: (Long) -> String,
    onSelectBest: (MangaIdentityKey) -> Unit,
    onOpenPagePreview: (FullscreenPreviewPage) -> Unit, // KMK --> v0.7.9 // KMK <--
    onOpenFullscreenCandidate: (MangaIdentityKey) -> Unit, // KMK v0.8.16
    onRetryCandidate: (MangaIdentityKey) -> Unit, // KMK v0.8.17-fix1
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // KMK v0.8.18: origin/current manga included as the comparison baseline -- see
        // BestVersionCompareScreenModel.State.compareCandidates.
        items(state.compareCandidates, key = { "${it.source}|${it.url}" }) { manga ->
            val key = MangaIdentityKey(manga.source, manga.url)
            val isOrigin = key == state.originKey
            val previewState = state.candidatePreviews[key]
            // KMK --> v0.7.33: per-thumbnail ContentScale.Fit toggle state (keyed per candidate)
            val fitModes = remember { mutableStateMapOf<Int, Boolean>() }
            // KMK <--
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.extraSmall),
            ) {
                Column(modifier = Modifier.padding(MaterialTheme.padding.small)) {
                    Text(
                        text = manga.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // KMK v0.8.16: show which extension this candidate belongs to
                    // KMK v0.8.18: origin gets a distinct "Current version" label.
                    Text(
                        text = if (isOrigin) {
                            stringResource(KMR.strings.best_version_current_version_label)
                        } else {
                            stringResource(KMR.strings.best_version_candidate_source, sourceName(manga.source))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOrigin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when (previewState) {
                        // KMK v0.8.18: a chapter that was already unavailable before preview even
                        // started -- never sent to page-list fetching, never a spinner, never an
                        // error (nothing failed; nothing was attempted). No Retry action either --
                        // see retryCandidate()'s matching guard.
                        CandidatePreviewState.Skipped -> {
                            Text(
                                text = stringResource(KMR.strings.best_version_preview_skipped_unavailable),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        is CandidatePreviewState.Loaded -> {
                            val loaded = previewState.pages.size
                            val total = state.sampleSize
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                            ) {
                                // KMK v0.8.16-fix1: reworded from "N/N pages loaded" -- that wording
                                // implied the preview images themselves had successfully loaded, when
                                // it only meant preview page URLs were sampled/resolved (the actual ADB
                                // audit bug: Comix showed "5/5 pages loaded" with five broken-image
                                // placeholders). This count is still "preview pages prepared for
                                // display", not a claim about image-decode success -- each thumbnail
                                // below now shows its own loading/error state via SubcomposeAsyncImage.
                                Text(
                                    text = stringResource(KMR.strings.best_version_pages_loaded, loaded, total),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                // KMK v0.8.16: open a full-screen, read-only comparison view for
                                // this candidate instead of only single-thumbnail zoom.
                                if (previewState.pages.isNotEmpty()) {
                                    TextButton(onClick = { onOpenFullscreenCandidate(key) }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Fullscreen,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(KMR.strings.best_version_open_fullscreen_compare),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(previewState.pages, key = { it.index }) { page ->
                                    // KMK --> v0.7.33: per-thumbnail Fit/Crop toggle
                                    val fitMode = fitModes.getOrDefault(page.index, false)
                                    Box(
                                        modifier = Modifier
                                            .height(180.dp)
                                            .aspectRatio(0.7f),
                                    ) {
                                        // KMK <--
                                        // KMK v0.8.16-fix1: source-aware page preview loading --
                                        // SubcomposeAsyncImage(model = page.preview) routes through
                                        // PagePreviewFetcher (source-runtime boundary, page-preview
                                        // cache, source headers), not a raw URL string. Explicit
                                        // loading/error content replaces the previous silent
                                        // broken-image placeholder.
                                        SubcomposeAsyncImage(
                                            model = page.preview,
                                            contentDescription = stringResource(
                                                KMR.strings.best_version_preview_page_content_description,
                                                page.index + 1,
                                                manga.title,
                                            ),
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(MaterialTheme.shapes.small)
                                                // KMK --> v0.7.9: tap to open fullscreen preview
                                                .clickable {
                                                    onOpenPagePreview(
                                                        FullscreenPreviewPage(
                                                            imageUrl = page.preview.imageUrl,
                                                            pageIndex = page.index,
                                                            mangaTitle = manga.title,
                                                            sourceId = page.preview.source,
                                                        ),
                                                    )
                                                },
                                            // KMK <--
                                            loading = {
                                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                                }
                                            },
                                            error = {
                                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = stringResource(KMR.strings.best_version_preview_page_failed),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                        modifier = Modifier.padding(4.dp),
                                                    )
                                                }
                                            },
                                            success = {
                                                Box(Modifier.fillMaxSize()) {
                                                    this@SubcomposeAsyncImage.SubcomposeAsyncImageContent()
                                                }
                                            },
                                            // KMK --> v0.7.33: toggle between Crop and Fit
                                            contentScale = if (fitMode) ContentScale.Fit else ContentScale.Crop,
                                            // KMK <--
                                        )
                                        // KMK --> v0.7.33: Fit/Crop icon toggle overlay
                                        IconButton(
                                            onClick = { fitModes[page.index] = !fitMode },
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .size(28.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.AspectRatio,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.85f),
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                        // KMK <--
                                        // KMK --> v0.7.33
                                    }
                                    // KMK <--
                                }
                            }
                        }
                        is CandidatePreviewState.PreviewError -> {
                            // KMK v0.8.17-fix1: candidate-level retry -- restarts only this
                            // candidate's preview load, not the whole comparison workflow, and
                            // preserves every other candidate's already-loaded state and any
                            // selected-best choice.
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                            ) {
                                Text(
                                    text = recommendationErrorText(previewState.message),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { onRetryCandidate(key) }) {
                                    Text(stringResource(KMR.strings.best_version_retry))
                                }
                            }
                        }
                        CandidatePreviewState.Loading, null -> {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // KMK v0.8.18: selecting origin reads as "Keep current version" -- it routes to
                    // BestVersionCompareScreenModel.keepCurrentVersion() (safe finalize, no
                    // migrate/copy dialog), never the normal migrate/copy confirmation flow.
                    OutlinedButton(
                        onClick = { onSelectBest(key) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                if (isOrigin) KMR.strings.best_version_keep_current else KMR.strings.best_version_select_as_best,
                            ),
                        )
                    }
                }
            }
        }
        item(key = "bottom_spacer") {
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}

@Composable
private fun MigrationConfirmDialog(
    targetTitle: String,
    onDismiss: () -> Unit,
    onMigrate: () -> Unit,
    onCopy: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(KMR.strings.best_version_migrate_title)) },
        text = {
            // KMK Undo Expansion Phase 4: honest preflight wording -- migration is not automatically
            // reversible (see MigrateMangaUseCase's non-transactional flag sequence and enhanced-
            // tracker remote writes, documented in the private Undo coverage audit). No "Undo
            // migration" affordance is offered anywhere in this feature.
            Column {
                Text(targetTitle)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(KMR.strings.best_version_migrate_not_undoable),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onMigrate) {
                Text(stringResource(KMR.strings.best_version_migrate_action))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(tachiyomi.i18n.MR.strings.action_cancel))
                }
                TextButton(onClick = onCopy) {
                    Text(stringResource(KMR.strings.best_version_copy_action))
                }
            }
        },
    )
}

// KMK --> v0.7.9: fullscreen zoomable page preview
@Composable
private fun FullscreenPagePreviewDialog(
    page: FullscreenPreviewPage,
    onDismiss: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            // KMK v0.8.16-fix1: source-aware -- see the thumbnail row's matching comment above.
            SubcomposeAsyncImage(
                model = eu.kanade.domain.manga.model.PagePreview(page.pageIndex, page.imageUrl, page.sourceId),
                contentDescription = stringResource(
                    KMR.strings.best_version_preview_page_content_description,
                    page.pageIndex + 1,
                    page.mangaTitle,
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                        }
                    }
                    // KMK --> v0.7.33: tap to close when not zoomed in
                    .pointerInput("tap") {
                        detectTapGestures {
                            if (scale <= 1f) onDismiss()
                        }
                    }
                    // KMK <--
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                },
                error = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(KMR.strings.best_version_preview_page_failed),
                            color = Color.White,
                        )
                    }
                },
                success = { SubcomposeAsyncImageContent() },
                contentScale = ContentScale.Fit,
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(KMR.strings.best_version_close_preview),
                    tint = Color.White,
                )
            }
        }
    }
}
// KMK <--

// KMK --> v0.8.16: full-screen, read-only comparison view for one candidate's whole sampled page
// set (not only a single tapped thumbnail). Explicitly read-only: no chapter navigation, no
// mark-read, no page saving, no source browsing -- just a vertical scroll of the same sampled pages
// already loaded for comparison, with source/title context visible at the top.
@Composable
private fun FullscreenCandidatePreviewDialog(
    mangaTitle: String,
    sourceName: String,
    pages: List<SampledPage>,
    onDismiss: () -> Unit,
) {
    // KMK v0.8.18: reader-informed display -- reads only the user's own reading-mode/side-padding
    // preference values (no reader lifecycle, no navigation, no reader Composables). See
    // BestVersionReaderPreviewPolicy's doc comment for exactly what is and isn't reused.
    val readerPreferences = remember { Injekt.get<ReaderPreferences>() }
    val readerDisplay = remember {
        BestVersionReaderPreviewPolicy.resolve(
            defaultReadingModeValue = readerPreferences.defaultReadingMode().get(),
            webtoonSidePaddingPercent = readerPreferences.webtoonSidePadding().get(),
        )
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MaterialTheme.padding.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mangaTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(KMR.strings.best_version_candidate_source, sourceName),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(KMR.strings.best_version_close_preview),
                            tint = Color.White,
                        )
                    }
                }
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(pages, key = { it.index }) { page ->
                        // KMK v0.8.16-fix1: source-aware -- see the thumbnail row's matching comment.
                        // KMK v0.8.18: reader-informed side padding (only applied when the user's own
                        // default reading mode is webtoon-style) -- see BestVersionReaderPreviewPolicy.
                        SubcomposeAsyncImage(
                            model = page.preview,
                            contentDescription = stringResource(
                                KMR.strings.best_version_preview_page_content_description,
                                page.index + 1,
                                mangaTitle,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp)
                                .padding(horizontal = readerDisplay.sidePaddingDp.dp, vertical = 0.dp)
                                .padding(bottom = 2.dp),
                            loading = {
                                Box(
                                    Modifier.fillMaxWidth().height(120.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(color = Color.White)
                                }
                            },
                            error = {
                                Box(
                                    Modifier.fillMaxWidth().height(120.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(KMR.strings.best_version_preview_page_failed),
                                        color = Color.White,
                                    )
                                }
                            },
                            success = { SubcomposeAsyncImageContent() },
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
        }
    }
}
// KMK <--

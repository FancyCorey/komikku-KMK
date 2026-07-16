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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Close
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
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import exh.recs.RecommendationErrorKind
import exh.recs.matching.MangaIdentityKey
import exh.recs.matching.SameMangaCandidateResult
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

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
            RecommendationErrorKind.Internal -> KMR.strings.rec_error_internal
        },
    )
}
// KMK <--

// KMK --> v0.7.9
private data class FullscreenPreviewPage(
    val imageUrl: String,
    val pageIndex: Int,
    val mangaTitle: String,
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
        val fullscreenPage: FullscreenPreviewPage? = fullscreenPageUrl?.let {
            FullscreenPreviewPage(it, fullscreenPageIndex, fullscreenPageTitle)
        }
        // KMK <--
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
                            onSelectBest = screenModel::selectBestVersion,
                            // KMK --> v0.7.9
                            onOpenPagePreview = { p ->
                                fullscreenPageUrl = p.imageUrl
                                fullscreenPageIndex = p.pageIndex
                                fullscreenPageTitle = p.mangaTitle
                            },
                            // KMK <--
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
                                    text = stringResource(KMR.strings.best_version_migration_complete),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = { navigator.pop() }) {
                                    Text("Done")
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
                                    Text("Back")
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
    }
}

@Composable
private fun ConfirmCandidatesContent(
    state: BestVersionCompareScreenModel.State,
    onToggle: (MangaIdentityKey) -> Unit,
    onConfirm: () -> Unit,
) {
    val allCandidates: List<Manga> = state.candidates.values
        .filterIsInstance<SameMangaCandidateResult.Success>()
        .flatMap { it.results }

    Column(modifier = Modifier.fillMaxSize()) {
        if (allCandidates.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(KMR.strings.best_version_no_candidates))
            }
        } else {
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
                items(allCandidates, key = { "${it.source}|${it.url}" }) { manga ->
                    val key = MangaIdentityKey(manga.source, manga.url)
                    val selected = key in state.selectedKeys
                    CandidateRow(
                        manga = manga,
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
            Button(
                onClick = onConfirm,
                enabled = allCandidates.isNotEmpty() && state.selectedKeys.isNotEmpty(),
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
            items(state.selectedCandidates, key = { "${it.source}|${it.url}" }) { manga ->
                val key = MangaIdentityKey(manga.source, manga.url)
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
    onSelectBest: (MangaIdentityKey) -> Unit,
    onOpenPagePreview: (FullscreenPreviewPage) -> Unit, // KMK --> v0.7.9 // KMK <--
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(state.selectedCandidates, key = { "${it.source}|${it.url}" }) { manga ->
            val key = MangaIdentityKey(manga.source, manga.url)
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
                    when (previewState) {
                        is CandidatePreviewState.Loaded -> {
                            val loaded = previewState.pages.size
                            val total = state.sampleSize
                            Text(
                                text = stringResource(KMR.strings.best_version_pages_loaded, loaded, total),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
                                        AsyncImage(
                                            model = page.imageUrl,
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
                                                            imageUrl = page.imageUrl,
                                                            pageIndex = page.index,
                                                            mangaTitle = manga.title,
                                                        ),
                                                    )
                                                },
                                            // KMK <--
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
                            Text(
                                text = recommendationErrorText(previewState.message),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        CandidatePreviewState.Loading, null -> {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onSelectBest(key) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(KMR.strings.best_version_select_as_best))
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
        text = { Text(targetTitle) },
        confirmButton = {
            TextButton(onClick = onMigrate) {
                Text(stringResource(KMR.strings.best_version_migrate_action))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
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
            AsyncImage(
                model = page.imageUrl,
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

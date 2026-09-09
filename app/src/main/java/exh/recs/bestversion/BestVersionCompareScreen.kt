package exh.recs.bestversion

// KMK --> v0.7.8
import android.widget.Toast
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.KmkEmptyStateArtwork
import eu.kanade.presentation.components.KmkEmptyStateIllustration
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import exh.recs.RecommendationErrorKind
import exh.recs.bestversion.fixture.BestVersionPairedFixtureRuntime
import exh.recs.matching.MangaIdentityKey
import exh.recs.matching.SameMangaCandidateResult
import exh.recs.recommendationErrorMessageRes
import exh.recs.settings.RecommendationDiagnosticsSettingsScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.domain.chapter.interactor.GetChapterByUrlAndMangaId
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.selectedBackground
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK --> v0.8.20-fix5: Best Version state carries a closed typed error reason. Rendering is
// exhaustive and resource-backed, so a new failure cannot accidentally become verbatim UI text.
@Composable
private fun bestVersionErrorText(reason: BestVersionErrorReason): String {
    return stringResource(
        when (reason) {
            BestVersionErrorReason.OriginMissing -> KMR.strings.best_version_error_origin_missing
            BestVersionErrorReason.SourceUnavailable -> KMR.strings.best_version_error_source_unavailable
            // KMK v0.8.21-fix2: reuses the shared recommendationErrorMessageRes() mapping instead of
            // duplicating it -- this local copy previously had to be updated by hand every time
            // RecommendationErrorKind gained a case (it was missed for RateLimit/Authentication until
            // this pass), which is exactly the kind of drift a single shared mapping avoids.
            is BestVersionErrorReason.Recommendation -> recommendationErrorMessageRes(reason.kind)
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
    internal val originMangaId: Long,
    internal val fixtureOperationId: String? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = androidx.compose.ui.platform.LocalContext.current
        val readerScope = rememberCoroutineScope()
        val getChapterByUrlAndMangaId = remember { Injekt.get<GetChapterByUrlAndMangaId>() }
        val updateMangaFromRemote = remember { Injekt.get<UpdateMangaFromRemote>() }
        val fixtureRouteResolver = remember(fixtureOperationId) {
            fixtureOperationId?.let { BestVersionPairedFixtureRuntime().routeResolver }
        }
        val screenModel = rememberScreenModel {
            BestVersionCompareScreenModel(
                originMangaId = originMangaId,
                fixtureOperationId = fixtureOperationId,
                fixtureRouteResolver = fixtureRouteResolver,
            )
        }
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
                    actions = {
                        AppBarActions(
                            persistentListOf(
                                AppBar.Action(
                                    title = stringResource(KMR.strings.best_version_settings_action),
                                    icon = Icons.Outlined.Settings,
                                    onClick = {
                                        navigator.push(
                                            RecommendationDiagnosticsSettingsScreen(
                                                anchor = "same_manga_results_per_source",
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
                            onOpenReader = { manga, chapter ->
                                readerScope.launch {
                                    val localChapter = getChapterByUrlAndMangaId.await(chapter.url, manga.id)
                                        ?: updateMangaFromRemote(manga = manga, fetchChapters = true)
                                            .getOrNull()
                                            ?.newChapters
                                            ?.find { it.url == chapter.url }
                                    if (localChapter != null) {
                                        context.startActivity(
                                            ReaderActivity.newIntent(context, manga.id, localChapter.id),
                                        )
                                    } else {
                                        Toast.makeText(
                                            context,
                                            context.getString(KMR.strings.best_version_reader_chapter_unavailable.resourceId),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            },
                            onSelectManualChapter = screenModel::selectManualChapter,
                            onRetryFailedChapterLoads = screenModel::retryFailedChapterLoads,
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
                            // Opening asks
                            // the screen model to fetch the candidate's full, unsampled page list
                            // (see BestVersionCompareScreenModel.openFullscreenCandidate) instead of
                            // reusing the bounded BestVersionPageSampler thumbnail strip.
                            onOpenFullscreenCandidate = { key ->
                                fullscreenCandidateSource = key.source
                                fullscreenCandidateUrl = key.url
                                screenModel.openFullscreenCandidate(key)
                            },
                            // One candidate Retry action retries the current failed candidate set.
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
                                    text = bestVersionErrorText(step.reason),
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
        // Fullscreen, read-only candidate
        // comparison preview -- now backed by the screen model's own FullscreenPreviewState (the
        // full, unsampled page list) instead of reusing candidatePreviews' bounded
        // BestVersionPageSampler thumbnail strip.
        fullscreenCandidateKey?.let { key ->
            // KMK v0.8.18: includes origin -- fullscreen compare must also work from the origin row.
            val candidateManga = state.compareCandidates.find { it.source == key.source && it.url == key.url }
            val fullscreenState = state.fullscreenPreview
            if (candidateManga != null && fullscreenState != null) {
                FullscreenCandidatePreviewDialog(
                    mangaTitle = candidateManga.title,
                    sourceName = screenModel.sourceName(candidateManga.source),
                    previewState = fullscreenState,
                    onRequestPage = screenModel::requestFullscreenPageImage,
                    onRetryPage = screenModel::retryFullscreenPageImage,
                    onRetryCandidate = screenModel::retryFullscreenCandidate,
                    onDismiss = {
                        fullscreenCandidateSource = null
                        fullscreenCandidateUrl = null
                        screenModel.closeFullscreenCandidate()
                    },
                )
            } else {
                LaunchedEffect(key) {
                    fullscreenCandidateSource = null
                    fullscreenCandidateUrl = null
                    screenModel.closeFullscreenCandidate()
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

// KMK R2-AUG-05-ACTUAL-BEST-VERSION-FAILURE-PATH: the literal checkmark glyph decoration
// previously rendered here read as a misleading "Best Version" marker (see the contract's checkmark
// defect) and was never accessible -- a plain Text glyph carries no selected semantics. Replaced with
// this codebase's own established selection convention (Modifier.selectedBackground, the same tint
// CommonMangaItem/library rows use for a selected state) plus real accessible selected semantics --
// no new decorative corner badge/icon was added in its place.
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
            .semantics {
                role = Role.Checkbox
                this.selected = selected
            }
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier
                .selectedBackground(selected)
                .padding(MaterialTheme.padding.small),
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
        }
    }
}

@Composable
private fun SelectChapterContent(
    state: BestVersionCompareScreenModel.State,
    sourceName: (Long) -> String,
    onStartPreview: () -> Unit,
    onOpenReader: (Manga, eu.kanade.tachiyomi.source.model.SChapter) -> Unit,
    onSelectManualChapter: (MangaIdentityKey, eu.kanade.tachiyomi.source.model.SChapter) -> Unit,
    onRetryFailedChapterLoads: () -> Unit,
) {
    // KMK R2-AUG-05: which candidate's bounded manual chapter picker dialog is open, if any.
    var manualPickerKey by remember { mutableStateOf<MangaIdentityKey?>(null) }

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
                val candidateChapterList = state.candidateChapterLists[key].orEmpty()
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
                            is CandidateChapterState.Available -> {
                                // KMK R2-AUG-05-ACTUAL-BEST-VERSION-FAILURE-PATH: exact, nearest
                                // (disclosed non-exact), and manually-picked chapters render with
                                // distinct wording -- a Nearest match is never worded as if it were
                                // the same chapter as the origin.
                                val disclosureText = when (val disclosure = chapterState.matchDisclosure) {
                                    ChapterMatchDisclosure.Exact ->
                                        stringResource(KMR.strings.best_version_candidate_chapter, chapterState.chapter.name)
                                    is ChapterMatchDisclosure.Nearest ->
                                        stringResource(
                                            KMR.strings.best_version_chapter_match_nearest,
                                            formatChapterNumber(disclosure.originChapterNumber),
                                            formatChapterNumber(disclosure.candidateChapterNumber),
                                        )
                                    ChapterMatchDisclosure.Manual ->
                                        stringResource(KMR.strings.best_version_chapter_match_manual, chapterState.chapter.name)
                                }
                                Text(
                                    text = disclosureText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (chapterState.matchDisclosure is ChapterMatchDisclosure.Nearest) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                // KMK R2-AUG-05: bounded manual chapter override -- only offered for
                                // real candidates with a fetched chapter list of their own (never the
                                // origin, and never when there is nothing to pick from).
                                if (!isOrigin && candidateChapterList.size > 1) {
                                    TextButton(onClick = { manualPickerKey = key }) {
                                        Text(stringResource(KMR.strings.best_version_choose_chapter_action))
                                    }
                                }
                                TextButton(onClick = { onOpenReader(manga, chapterState.chapter) }) {
                                    Text(stringResource(KMR.strings.best_version_open_reader_action))
                                }
                            }
                            CandidateChapterState.Unavailable ->
                                Text(
                                    text = stringResource(KMR.strings.best_version_chapter_unavailable),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            is CandidateChapterState.ChapterError -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                                ) {
                                    Text(
                                        text = bestVersionErrorText(chapterState.reason),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = onRetryFailedChapterLoads) {
                                        Text(stringResource(KMR.strings.best_version_retry))
                                    }
                                }
                            }
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

    // KMK R2-AUG-05: bounded chapter picker -- scoped to exactly the candidate's own fetched chapter
    // list (state.candidateChapterLists[key]), never any other candidate's/source's chapters.
    manualPickerKey?.let { key ->
        val manga = state.compareCandidates.find { MangaIdentityKey(it.source, it.url) == key }
        val chapters = state.candidateChapterLists[key].orEmpty()
        if (manga != null && chapters.isNotEmpty()) {
            ManualChapterPickerDialog(
                mangaTitle = manga.title,
                chapters = chapters,
                onSelect = { chapter ->
                    onSelectManualChapter(key, chapter)
                    manualPickerKey = null
                },
                onDismiss = { manualPickerKey = null },
            )
        } else {
            LaunchedEffect(key) { manualPickerKey = null }
        }
    }
}

// KMK R2-AUG-05: trims a trailing ".0" for a whole chapter number (e.g. "145" not "145.0") while
// keeping fractional chapter numbers (e.g. "12.5") intact -- purely a display concern.
private fun formatChapterNumber(number: Double): String {
    return if (number == number.toLong().toDouble()) number.toLong().toString() else number.toString()
}

// KMK R2-AUG-05-ACTUAL-BEST-VERSION-FAILURE-PATH: bounded manual chapter picker -- lists only the
// selected candidate's own already-fetched chapters (never fetches or displays another candidate's
// or source's chapter list), so a user stuck with a disclosed Nearest (non-exact) auto-match can pick
// a different chapter from that same candidate instead.
@Composable
private fun ManualChapterPickerDialog(
    mangaTitle: String,
    chapters: List<eu.kanade.tachiyomi.source.model.SChapter>,
    onSelect: (eu.kanade.tachiyomi.source.model.SChapter) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
            Column(modifier = Modifier.padding(MaterialTheme.padding.medium)) {
                Text(
                    text = stringResource(KMR.strings.best_version_choose_chapter_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = mangaTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(
                        chapters.sortedByDescending { it.chapter_number },
                        key = { it.url },
                    ) { chapter ->
                        Text(
                            text = chapter.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(chapter) }
                                .padding(vertical = MaterialTheme.padding.small),
                        )
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(tachiyomi.i18n.MR.strings.action_cancel))
                }
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
    // Thumbnail decoding happens after the source preview state is Loaded. Keep this small,
    // presentation-only retry ledger separate from the source/model state so a failed image can be
    // retried without refetching candidates or disturbing thumbnails that already succeeded.
    val failedPreviewPages = remember { mutableStateMapOf<MangaIdentityKey, Set<Int>>() }
    val previewRetryGenerations = remember { mutableStateMapOf<Pair<MangaIdentityKey, Int>, Int>() }
    fun retryFailedPreviewPages() {
        BestVersionRetryScopePolicy.thumbnailPageKeys(failedPreviewPages.toMap()).forEach { retryKey ->
            previewRetryGenerations[retryKey] = (previewRetryGenerations[retryKey] ?: 0) + 1
        }
    }
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
                            val failedPages = failedPreviewPages[key].orEmpty()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                            ) {
                                // This count describes sampled page references, not successful image decodes.
                                // Calling them "pages loaded" would be misleading when source-specific image
                                // requests later fail and the row contains broken placeholders. It therefore
                                // remains "preview pages prepared for
                                // display", not a claim about image-decode success -- each thumbnail
                                // below now shows its own loading/error state via SubcomposeAsyncImage.
                                Text(
                                    text = pluralStringResource(KMR.plurals.best_version_pages_loaded, count = total, loaded, total),
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
                                    val retryGeneration = previewRetryGenerations[key to page.index] ?: 0
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
                                        androidx.compose.runtime.key(retryGeneration) {
                                            SubcomposeAsyncImage(
                                                model = page.preview,
                                                contentDescription = stringResource(
                                                    KMR.strings.best_version_preview_page_content_description,
                                                    page.index + 1,
                                                    manga.title,
                                                ),
                                                onSuccess = {
                                                    failedPreviewPages[key] = failedPreviewPages[key].orEmpty() - page.index
                                                },
                                                onError = {
                                                    failedPreviewPages[key] = failedPreviewPages[key].orEmpty() + page.index
                                                },
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
                                        }
                                        // KMK --> v0.7.33: Fit/Crop icon toggle overlay
                                        IconButton(
                                            onClick = { fitModes[page.index] = !fitMode },
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.AspectRatio,
                                                contentDescription = stringResource(
                                                    if (fitMode) {
                                                        KMR.strings.best_version_preview_crop_page
                                                    } else {
                                                        KMR.strings.best_version_preview_fit_page
                                                    },
                                                ),
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
                            val failedSourcePages = previewState.failedPageIndexes
                            if (failedPages.isNotEmpty() || failedSourcePages.isNotEmpty()) {
                                if (failedSourcePages.isNotEmpty()) {
                                    Text(
                                        text = stringResource(KMR.strings.best_version_preview_page_failed),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        if (failedSourcePages.isNotEmpty()) {
                                            onRetryCandidate(key)
                                        } else {
                                            retryFailedPreviewPages()
                                        }
                                    },
                                    modifier = Modifier.align(Alignment.End),
                                ) {
                                    Text(stringResource(KMR.strings.best_version_retry))
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
                                    text = bestVersionErrorText(previewState.reason),
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

// A reader-quality, read-only chapter preview displays one page at a time in paged mode.
// Swipe navigation uses
// HorizontalPager, pinch zoom per page, reader-consistent Fit content scale), or a continuous vertical
// scroll in webtoon mode, honoring the user's own actual default reading-mode preference exactly like
// the real reader would, via BestVersionReaderPreviewPolicy (still only reading that preference's
// *value*, never the reader's Composables/lifecycle/navigation/preloading -- see that object's doc
// comment for the exact boundary). Operates on [previewState]'s FULL, unsampled page list (see
// FullscreenPreviewState's doc comment for why this is no longer BestVersionPageSampler's bounded
// thumbnail sample) -- each page's image is requested lazily via [onRequestPage] only once the pager
// actually shows (or is about to show) that page, never eagerly for the whole chapter. Each page has
// its own loading/retry/error state sourced from the screen model's own per-page resolution (backed by
// a real SourceRuntime.run(..., ImageUrl) call on request/retry, not a Compose-only generation bump --
// see BestVersionCompareScreenModel.resolveFullscreenPageImage). Structurally read-only: this dialog
// only ever renders state the screen model fetched via SourceRuntime.run(..., PageList/ImageUrl); it
// has no reference to DownloadManager, UpdateChapter, any tracker, the Alternate Source Reading
// Bridge, or Chapter Line Continuity, and triggers no navigation beyond paging within this already-
// fetched chapter (never a chapter transition).
@Composable
private fun FullscreenCandidatePreviewDialog(
    mangaTitle: String,
    sourceName: String,
    previewState: FullscreenPreviewState,
    onRequestPage: (Int) -> Unit,
    onRetryPage: (Int) -> Unit,
    onRetryCandidate: () -> Unit,
    onDismiss: () -> Unit,
) {
    // KMK v0.8.18 / R2-AUG-05: reader-informed display -- reads only the user's own reading-mode/
    // side-padding preference values (no reader lifecycle, no navigation, no reader Composables). See
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
            when (previewState) {
                FullscreenPreviewState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
                is FullscreenPreviewState.Error -> {
                    // Show an actionable terminal reason instead
                    // of an indefinite spinner or a silently empty dialog when the full page-list
                    // fetch itself fails (the confirmed "traces why previews can fail for sources
                    // whose manga/chapters remain readable elsewhere" requirement -- the real cause is
                    // now surfaced here via the same typed classification every other Best Version
                    // failure uses, not swallowed).
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = bestVersionErrorText(previewState.reason),
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(horizontal = MaterialTheme.padding.medium),
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onRetryCandidate) {
                            Text(stringResource(KMR.strings.best_version_retry))
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }
                is FullscreenPreviewState.Loaded -> {
                    val pages = previewState.pages
                    if (pages.isEmpty()) {
                        // KMK R2-AUG-05: explicit empty state -- matches the real reader's convention
                        // of never silently rendering nothing. The screen model already treats an
                        // empty fetched page list as FullscreenPreviewState.Error, so this branch is a
                        // defensive fallback, not the primary empty path.
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = stringResource(KMR.strings.best_version_preview_skipped_unavailable),
                                color = Color.White,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                            Spacer(Modifier.weight(1f))
                        }
                    } else if (readerDisplay.webtoonStyle) {
                        // KMK R2-AUG-05: webtoon-style users get the reader-consistent continuous
                        // vertical scroll behavior instead of a page-by-page pager -- this IS the
                        // reader-honored behavior for that reading mode, not a fallback/simplification.
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(pages, key = { index, _ -> index }) { index, _ ->
                                // KMK: LazyColumn only composes items near the visible viewport, so
                                // this naturally stays lazy across a long chapter -- no eager
                                // whole-chapter resolution.
                                LaunchedEffect(index) { onRequestPage(index) }
                                PreviewPageContent(
                                    pageIndex = index,
                                    mangaTitle = mangaTitle,
                                    imageState = previewState.pageImages[index],
                                    onRetry = { onRetryPage(index) },
                                    // Fullscreen inspection must keep the same pinch/pan affordance
                                    // in continuous/webtoon mode as in the paged reader-shaped mode.
                                    zoomable = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 120.dp)
                                        .padding(horizontal = readerDisplay.sidePaddingDp.dp)
                                        .padding(bottom = 2.dp),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }
                    } else {
                        // KMK R2-AUG-05: one page at a time, swipe navigation, pinch zoom -- the
                        // paged-mode reader-quality preview.
                        val pagerState = rememberPagerState(pageCount = { pages.size })
                        // Request the current page's image
                        // lazily as the pager reaches it, plus a single page ahead for smoother
                        // swiping -- a small, bounded, read-only analogue of the real reader's
                        // next-page preloading intent, never the reader's actual preload queue/worker
                        // infrastructure.
                        LaunchedEffect(pagerState.currentPage, pages.size) {
                            onRequestPage(pagerState.currentPage)
                            val next = pagerState.currentPage + 1
                            if (next < pages.size) onRequestPage(next)
                        }
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                        ) { pageIndex ->
                            PreviewPageContent(
                                pageIndex = pageIndex,
                                mangaTitle = mangaTitle,
                                imageState = previewState.pageImages[pageIndex],
                                onRetry = { onRetryPage(pageIndex) },
                                zoomable = true,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                        Text(
                            text = stringResource(
                                KMR.strings.best_version_preview_page_indicator,
                                pagerState.currentPage + 1,
                                pages.size,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 24.dp),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
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
        }
    }
}

// Shared per-page content for the candidate
// preview pager -- explicit loading/retry/error states sourced from the screen model's own
// [PreviewPageImageState] (a real SourceRuntime.run(..., ImageUrl) result, not a Compose-only
// generation counter), plus optional pinch zoom for paged mode. A null [imageState] (page not
// requested yet) renders the same loading spinner as an explicit Loading state. Once resolved, the
// image itself still routes through Coil's SubcomposeAsyncImage/PagePreviewFetcher for the actual
// network fetch, disk cache, and source-header handling -- Coil's own error/retry affordance is wired
// to the same [onRetry] callback, so a transient image-byte failure and a URL-resolution failure both
// recover through one genuine re-resolution path.
@Composable
private fun PreviewPageContent(
    pageIndex: Int,
    mangaTitle: String,
    imageState: PreviewPageImageState?,
    onRetry: () -> Unit,
    zoomable: Boolean,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    var scale by remember(pageIndex) { mutableFloatStateOf(1f) }
    var offset by remember(pageIndex) { mutableStateOf(Offset.Zero) }
    val zoomModifier = if (zoomable) {
        Modifier.pointerInput(pageIndex) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 5f)
                offset = if (scale > 1f) offset + pan else Offset.Zero
            }
        }.graphicsLayer {
            scaleX = scale
            scaleY = scale
            translationX = offset.x
            translationY = offset.y
        }
    } else {
        Modifier
    }
    when (imageState) {
        null, PreviewPageImageState.Loading -> {
            Box(modifier.then(zoomModifier), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }
        is PreviewPageImageState.Failed -> {
            Box(modifier.then(zoomModifier), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = bestVersionErrorText(imageState.reason),
                        color = Color.White,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onRetry) {
                        Text(stringResource(KMR.strings.best_version_retry))
                    }
                }
            }
        }
        is PreviewPageImageState.Resolved -> {
            SubcomposeAsyncImage(
                model = imageState.preview.preview,
                contentDescription = stringResource(
                    KMR.strings.best_version_preview_page_content_description,
                    pageIndex + 1,
                    mangaTitle,
                ),
                modifier = modifier.then(zoomModifier),
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                },
                error = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(KMR.strings.best_version_preview_page_failed),
                                color = Color.White,
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = onRetry) {
                                Text(stringResource(KMR.strings.best_version_retry))
                            }
                        }
                    }
                },
                success = { SubcomposeAsyncImageContent() },
                contentScale = contentScale,
            )
        }
    }
}
// KMK <--

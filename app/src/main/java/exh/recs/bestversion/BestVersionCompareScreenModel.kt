package exh.recs.bestversion

// KMK --> v0.7.8
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceRuntime
import eu.kanade.tachiyomi.source.SourceRuntimeOperation
import eu.kanade.tachiyomi.source.online.HttpSource
import exh.recs.RecommendationErrorClassifier
import exh.recs.RecommendationErrorKind
import exh.recs.matching.MangaIdentityKey
import exh.recs.matching.SameMangaCandidateResult
import exh.recs.matching.SameMangaCandidateSearcher
import exh.recs.matching.SameMangaMatchSettings
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import mihon.domain.migration.usecases.MigrateMangaUseCase
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.taste.interactor.UpsertMangaSourceQualitySignal
import tachiyomi.domain.taste.model.MangaSourceQualitySignal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.Executors

sealed interface BestVersionStep {
    data object LoadingOrigin : BestVersionStep
    data object SearchingCandidates : BestVersionStep
    data object ConfirmCandidates : BestVersionStep
    data object LoadingChapters : BestVersionStep
    data object SelectChapter : BestVersionStep
    data object LoadingPreview : BestVersionStep
    data object ComparePreview : BestVersionStep
    data object PreparingMigration : BestVersionStep
    data class Error(val message: String) : BestVersionStep
    data object Done : BestVersionStep
}

sealed interface CandidateChapterState {
    data object Loading : CandidateChapterState
    data class Available(val chapter: eu.kanade.tachiyomi.source.model.SChapter, val totalChapters: Int) : CandidateChapterState
    data object Unavailable : CandidateChapterState
    data class ChapterError(val message: String) : CandidateChapterState
}

sealed interface CandidatePreviewState {
    data object Loading : CandidatePreviewState
    data class Loaded(val pages: List<SampledPage>) : CandidatePreviewState
    data class PreviewError(val message: String) : CandidatePreviewState
    // KMK v0.8.18: a candidate whose chapter was already CandidateChapterState.Unavailable before
    // startPreview() ran must never be sent into page-list/image-url fetching at all -- it is not a
    // "failure" (nothing was attempted), it is a deliberately skipped row. Kept distinct from
    // PreviewError so the UI can render "Chapter unavailable, preview skipped" instead of a
    // network/source failure message, and so it is never eligible for Retry (there is nothing to
    // retry -- the chapter itself is unavailable, not the preview fetch).
    data object Skipped : CandidatePreviewState
}

// KMK v0.8.16-fix1: carries a source-aware eu.kanade.domain.manga.model.PagePreview instead of a raw
// URL string -- the ADB UI audit (UI_AUDIT_NOTES.md) found the previous raw-URL AsyncImage path could
// report "N/N pages loaded" while every thumbnail rendered as a broken placeholder, because sources
// that require source-specific preview fetching/headers/cache behavior need PagePreviewFetcher's
// source-runtime boundary (SourceRuntime.run(..., SourceRuntimeOperation.PreviewImage)), which raw URL
// loading bypasses entirely. `preview.index`/`preview.imageUrl` are still available directly off
// [PagePreview] for any code that only needs the primitives.
data class SampledPage(val index: Int, val preview: eu.kanade.domain.manga.model.PagePreview)

// KMK Confirmed Blocker Remediation Corrective Completion Plan V3 2026-07-29 Phase B: pairs a
// dispatcher with an explicit close action, so [BestVersionCompareScreenModel.onDispose] never has to
// guess ownership from the dispatcher's runtime type. `close` defaults to a no-op: any dispatcher a
// caller (test or otherwise) supplies from outside is never closed unless that caller explicitly opts
// in by passing its own `close` action along with it.
data class DispatcherHandle(
    val dispatcher: CoroutineDispatcher,
    val close: () -> Unit = {},
)

// KMK V3 Phase B: the only handle in this file that legitimately owns its executor -- production
// default for [BestVersionCompareScreenModel]'s `dispatcherHandle` parameter. Identical concurrency
// limit to the pre-V3 hardcoded dispatcher; the only behavioral change is that closing it is now driven
// by this handle's own `close` action rather than by `is ExecutorCoroutineDispatcher` type-checking.
fun ownedFixedThreadPoolDispatcherHandle(threads: Int = 5): DispatcherHandle {
    val executor = Executors.newFixedThreadPool(threads)
    return DispatcherHandle(dispatcher = executor.asCoroutineDispatcher(), close = executor::shutdown)
}

class BestVersionCompareScreenModel(
    val originMangaId: Long,
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val getMangaInteractor: GetManga = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val migrateMangaUseCase: MigrateMangaUseCase = Injekt.get(),
    private val upsertQualitySignal: UpsertMangaSourceQualitySignal = Injekt.get(),
    // KMK Confirmed Blocker Remediation Corrective Completion Plan V2 2026-07-29: this used to be a
    // hardcoded property initializer, which eagerly spun up 5 real OS threads the moment this class
    // was constructed -- before init{} even ran -- with no way for a test to substitute a deterministic
    // dispatcher and, worse, no lifecycle hook ever closed the pool, leaking 5 threads every time this
    // screen was opened and disposed. Now injectable, defaulting to the exact same fixed-thread-pool
    // dispatcher in production (identical concurrency limit, identical SourceRuntime call sites).
    // KMK Confirmed Blocker Remediation Corrective Completion Plan V3 2026-07-29 Phase B: ownership of
    // the dispatcher's lifecycle is now an explicit contract (DispatcherHandle) instead of being
    // inferred from `dispatcher is ExecutorCoroutineDispatcher`. Inferring ownership from runtime type
    // was wrong: any externally-supplied executor-backed dispatcher (e.g. a caller's own pool passed in
    // deliberately without transferring ownership) was being closed out from under its real owner
    // purely because of its type, not because this instance actually created it. The default here is
    // the only case that legitimately owns its executor and wires a real close action; every other
    // handle (a TestDispatcher, or an externally-supplied dispatcher) must default `close` to a no-op.
    private val dispatcherHandle: DispatcherHandle = ownedFixedThreadPoolDispatcherHandle(),
) : StateScreenModel<BestVersionCompareScreenModel.State>(State()) {

    // KMK V3 Phase B: kept as a plain val (not a computed property) so every existing SourceRuntime.run
    // call site below is unchanged -- only construction and disposal of the underlying dispatcher
    // changed, not how it's consumed mid-flight.
    private val coroutineDispatcher: CoroutineDispatcher = dispatcherHandle.dispatcher

    private val searcher = SameMangaCandidateSearcher(sourcePreferences, sourceManager, networkToLocalManga)
    private var searchJob: Job? = null
    private var originManga: Manga? = null
    private var disposed = false

    // KMK Confirmed Blocker Remediation Corrective Completion Plan V3 2026-07-29 Phase A: exposed
    // (package-private, not just testable via side effects) so a test can await the coroutine
    // confirmMigration() launches and assert directly on its own terminal Job state -- the only way to
    // actually prove a CancellationException propagates out of that coroutine instead of being silently
    // turned into a BestVersionStep.Error, rather than merely inferring propagation from the absence of
    // a receipt.
    internal var migrationJob: Job? = null
        private set

    data class State(
        val step: BestVersionStep = BestVersionStep.LoadingOrigin,
        val originManga: Manga? = null,
        val candidates: PersistentMap<Source, SameMangaCandidateResult> = persistentMapOf(),
        val selectedKeys: Set<MangaIdentityKey> = emptySet(),
        val manuallyDeselectedKeys: Set<MangaIdentityKey> = emptySet(),
        val selectedChapterNumber: Double? = null,
        val originChapters: List<Chapter> = emptyList(),
        val candidateChapters: PersistentMap<MangaIdentityKey, CandidateChapterState> = persistentMapOf(),
        val candidatePreviews: PersistentMap<MangaIdentityKey, CandidatePreviewState> = persistentMapOf(),
        val sampleSize: Int = 5,
        val avoidFirstPages: Boolean = true,
        val selectedBestKey: MangaIdentityKey? = null,
        val isMigrating: Boolean = false,
        val migrationComplete: Boolean = false,
        // KMK v0.8.16: local manga id of the migration/copy target, resolved once confirmMigration()
        // succeeds. Candidates are already localized (real local ids) by SameMangaCandidateSearcher
        // before this screen ever sees them, so target.id is already the correct id to store here --
        // no second networkToLocalManga call is needed. Null means Done must fall back to pop().
        val completedTargetMangaId: Long? = null,
        // KMK v0.8.18: true when Done was reached via keepCurrentVersion() (selecting the origin as
        // best) rather than an actual migrateMangaUseCase migrate/copy -- lets the Done screen show
        // truthful "kept current version" wording instead of "migration complete".
        val keptCurrentVersion: Boolean = false,
    ) {
        val searchProgress: Int get() = candidates.count { it.value !is SameMangaCandidateResult.Loading }
        val searchTotal: Int get() = candidates.size

        // KMK v0.8.18: identity key for the origin/current manga, used to recognize origin rows
        // across selection, chapter-loading, and preview without repeating the source+url comparison.
        val originKey: MangaIdentityKey?
            get() = originManga?.let { MangaIdentityKey(it.source, it.url) }

        // KMK v0.8.18: real migration-eligible candidates only (never includes origin) -- this is
        // deliberately unchanged from before v0.8.18 so the existing migrate/copy confirmation dialog
        // logic in BestVersionCompareScreen.kt (which looks a selected key up in this list) can never
        // resolve the origin as a migration target. Selecting origin routes through
        // BestVersionCompareScreenModel.keepCurrentVersion() instead, never through this list.
        val selectedCandidates: List<Manga>
            get() = candidates.values
                .filterIsInstance<SameMangaCandidateResult.Success>()
                .flatMap { it.results }
                .filter { MangaIdentityKey(it.source, it.url) in selectedKeys }

        // KMK v0.8.18: the full comparison set shown in chapter selection/preview/compare -- origin
        // always first (fixed baseline, not subject to toggleSelection()), followed by every selected
        // real candidate. Chapter-loading and preview both iterate this list instead of
        // [selectedCandidates] so origin is visible and previewable alongside real candidates.
        val compareCandidates: List<Manga>
            get() = listOfNotNull(originManga) + selectedCandidates
    }

    init {
        screenModelScope.launch {
            val manga = getMangaInteractor.await(originMangaId)
            if (manga == null) {
                mutableState.update {
                    it.copy(step = BestVersionStep.Error("Could not load origin manga."))
                }
                return@launch
            }
            originManga = manga
            mutableState.update { it.copy(originManga = manga) }
            startSearch(manga)
        }
    }

    // KMK Confirmed Blocker Remediation Corrective Completion Plan V2 2026-07-29: closes the
    // fixed-thread-pool dispatcher this instance created for itself, so it no longer leaks 5 real OS
    // threads every time this screen is disposed.
    // KMK V3 Phase B: closing now runs `dispatcherHandle.close`, the explicit action supplied at
    // construction time, instead of inferring ownership from the dispatcher's runtime type. The
    // production default wires a real close action for the executor it created; any handle built from
    // an externally-supplied dispatcher (test or otherwise) defaults `close` to a no-op, so this call
    // can never reach into and shut down a dispatcher this instance doesn't own. Guarded by `disposed`
    // so a second onDispose() call (defensive; Voyager should only call this once) can never invoke the
    // close action twice.
    override fun onDispose() {
        super.onDispose()
        searchJob?.cancel()
        if (!disposed) {
            disposed = true
            dispatcherHandle.close()
        }
    }

    private fun startSearch(manga: Manga) {
        val settings = resolveSettings()
        val sources = searcher.getMatchingSources()
        mutableState.update {
            it.copy(
                step = BestVersionStep.SearchingCandidates,
                candidates = sources.associateWith<Source, SameMangaCandidateResult> {
                    SameMangaCandidateResult.Loading
                }.toPersistentMap(),
                sampleSize = settings.previewSampleSize,
                avoidFirstPages = settings.avoidFirstPages,
            )
        }
        val queries = exh.recs.matching.CrossExtensionMatchQueryPlanner.buildQueries(manga)
        searchJob = ioCoroutineScope.launch {
            searcher.search(
                queries = queries,
                settings = settings,
                originManga = manga,
                sources = sources,
            ) { result ->
                updateCandidate(result.source, result.result, settings.preselectResults)
            }
            if (isActive) {
                mutableState.update {
                    it.copy(step = BestVersionStep.ConfirmCandidates)
                }
            }
        }
    }

    private fun updateCandidate(source: Source, result: SameMangaCandidateResult, preselect: Boolean) {
        val origin = originManga
        mutableState.update { current ->
            val newCandidates = current.candidates.mutate { it[source] = result }
            val newSelected = if (result is SameMangaCandidateResult.Success && preselect) {
                val newKeys = result.results.mapNotNull { manga ->
                    val key = MangaIdentityKey(manga.source, manga.url)
                    if (origin != null && manga.source == origin.source && manga.url == origin.url) return@mapNotNull null
                    if (key in current.manuallyDeselectedKeys) return@mapNotNull null
                    key
                }.toSet()
                current.selectedKeys + newKeys
            } else {
                current.selectedKeys
            }
            current.copy(candidates = newCandidates, selectedKeys = newSelected)
        }
    }

    fun toggleSelection(key: MangaIdentityKey) {
        val origin = originManga
        if (origin != null && key.source == origin.source && key.url == origin.url) return
        mutableState.update { current ->
            if (key in current.selectedKeys) {
                current.copy(
                    selectedKeys = current.selectedKeys - key,
                    manuallyDeselectedKeys = current.manuallyDeselectedKeys + key,
                )
            } else {
                current.copy(
                    selectedKeys = current.selectedKeys + key,
                    manuallyDeselectedKeys = current.manuallyDeselectedKeys - key,
                )
            }
        }
    }

    // KMK v0.8.18: proceeds using the full comparison set (origin + selected real candidates)
    // instead of just the real candidates -- origin must always reach chapter/preview even when zero
    // real candidates are selected (e.g. no matches found, or the user deselected every real
    // candidate and just wants to confirm they're already on the best version).
    fun confirmCandidates() {
        val compare = state.value.compareCandidates
        if (compare.isEmpty()) return
        loadChapters(compare)
    }

    private fun loadChapters(candidates: List<Manga>) {
        val origin = originManga ?: return
        mutableState.update { it.copy(step = BestVersionStep.LoadingChapters) }
        ioCoroutineScope.launch {
            // Load origin chapters to determine default chapter
            val originChapters = runCatching { getChaptersByMangaId.await(origin.id) }.getOrElse { emptyList() }
            val defaultChapter = BestVersionChapterMatcher.selectDefaultChapter(originChapters)
            val targetChapterNumber = defaultChapter?.chapterNumber ?: originChapters.maxOfOrNull { it.chapterNumber } ?: -1.0

            // KMK v0.8.18: origin's own chapter state is derived directly from the already-fetched
            // local `originChapters`/`defaultChapter` above -- never searched through extensions, and
            // never routed through the per-candidate SourceRuntime/getMangaUpdate fetch below (that
            // path is for real, externally-matched candidates only).
            val originKey = MangaIdentityKey(origin.source, origin.url)
            val originChapterState: CandidateChapterState = if (defaultChapter != null) {
                CandidateChapterState.Available(defaultChapter.toSChapter(), originChapters.size)
            } else {
                CandidateChapterState.Unavailable
            }

            val nonOriginCandidates = candidates.filterNot { it.source == origin.source && it.url == origin.url }

            // For each selected candidate, fetch chapter list from source
            val chapterResults = nonOriginCandidates.map { manga ->
                async {
                    val key = MangaIdentityKey(manga.source, manga.url)
                    val source = sourceManager.get(manga.source)
                    if (source == null) {
                        key to CandidateChapterState.ChapterError("Source not available")
                    } else {
                        // KMK v0.8.10-fix4: routed through SourceRuntime instead of a local
                        // catch(Exception)/catch(Error) pair -- one shared boundary classifies
                        // both and records a recoverable extension LinkageError in
                        // SourceRuntimeFailureRegistry; still always rethrows
                        // CancellationException and any genuinely fatal Error.
                        val sManga = manga.toSManga()
                        SourceRuntime.run(source, SourceRuntimeOperation.MangaUpdate, coroutineDispatcher) {
                            getMangaUpdate(
                                manga = sManga,
                                chapters = emptyList(),
                                fetchDetails = false,
                                fetchChapters = true,
                            ).chapters
                        }.fold(
                            onSuccess = { chapters ->
                                val match = if (targetChapterNumber >= 0) {
                                    BestVersionChapterMatcher.findMatch(targetChapterNumber, chapters)
                                } else {
                                    chapters.maxByOrNull { it.chapter_number }
                                }
                                key to if (match != null) {
                                    CandidateChapterState.Available(match, chapters.size)
                                } else {
                                    CandidateChapterState.Unavailable
                                }
                            },
                            onFailure = { throwable ->
                                // KMK v0.7.46: stable key, not raw exception text — see RecommendationErrorClassifier.
                                key to CandidateChapterState.ChapterError(RecommendationErrorClassifier.classifyToStorageKey(throwable))
                            },
                        )
                    }
                }
            }.awaitAll()

            if (isActive) {
                val chapterMap = (chapterResults + (originKey to originChapterState)).toMap().toPersistentMap()
                mutableState.update {
                    it.copy(
                        originChapters = originChapters,
                        selectedChapterNumber = targetChapterNumber.takeIf { it >= 0 },
                        candidateChapters = chapterMap,
                        step = BestVersionStep.SelectChapter,
                    )
                }
            }
        }
    }

    // KMK v0.8.18: iterates the full comparison set (origin + selected real candidates), not just
    // real candidates -- origin must reach preview too. A candidate whose chapter is already
    // CandidateChapterState.Unavailable is never sent into page-list fetching at all: it is marked
    // CandidatePreviewState.Skipped immediately, so it can never show up as a null/`Loading` row in
    // ComparePreviewContent (that was the exact bug -- an unavailable-chapter candidate simply had no
    // entry in candidatePreviews, and the UI's `CandidatePreviewState.Loading, null -> spinner` branch
    // rendered it as an indefinite spinner instead of a terminal unavailable state). If every
    // candidate is unavailable (zero previewable), preview is not started at all -- the screen still
    // moves straight to ComparePreview so the all-Skipped state can render its own clear message,
    // without ever touching the network.
    fun startPreview() {
        val compare = state.value.compareCandidates
        val chapterMap = state.value.candidateChapters
        val sampleSize = state.value.sampleSize
        val avoidFirstPages = state.value.avoidFirstPages

        val (previewable, unavailable) = BestVersionPreviewFetchPolicy.partition(compare, chapterMap)
        if (compare.isEmpty()) return

        val skippedPreviews = unavailable.associate { manga ->
            MangaIdentityKey(manga.source, manga.url) to (CandidatePreviewState.Skipped as CandidatePreviewState)
        }

        if (previewable.isEmpty()) {
            // KMK v0.8.18: nothing to fetch -- go straight to ComparePreview with every candidate
            // already marked Skipped, instead of starting a network round-trip for zero candidates.
            mutableState.update {
                it.copy(step = BestVersionStep.ComparePreview, candidatePreviews = skippedPreviews.toPersistentMap())
            }
            return
        }

        mutableState.update {
            val previews = (
                previewable.associate { manga ->
                    MangaIdentityKey(manga.source, manga.url) to (CandidatePreviewState.Loading as CandidatePreviewState)
                } + skippedPreviews
                ).toPersistentMap()
            it.copy(step = BestVersionStep.LoadingPreview, candidatePreviews = previews)
        }

        ioCoroutineScope.launch {
            previewable.map { manga ->
                async {
                    val result = previewOneCandidate(manga, chapterMap, sampleSize, avoidFirstPages) ?: return@async
                    val key = MangaIdentityKey(manga.source, manga.url)
                    mutableState.update { current ->
                        current.copy(candidatePreviews = current.candidatePreviews.mutate { it[key] = result })
                    }
                }
                // KMK v0.8.17-fix1: even though each result already lands in `candidatePreviews`
                // independently as it finishes (updated inside the async block above, not after
                // `awaitAll()`), `awaitAll()` itself previously had no bound -- one candidate that
                // hung on page-list/image-url resolution (no network timeout, no cancellation) meant
                // `step` never flipped to ComparePreview, leaving the *whole screen* stuck showing a
                // spinner even for candidates that had already finished. `previewOneCandidate` now
                // wraps its own work in a per-candidate timeout, so `awaitAll()` is bounded by that
                // timeout regardless of how many candidates are running concurrently.
            }.awaitAll()
            if (isActive) {
                mutableState.update { it.copy(step = BestVersionStep.ComparePreview) }
            }
        }
    }

    // KMK v0.8.17-fix1: retries exactly one failed/timed-out candidate -- reuses the exact same
    // per-candidate logic `startPreview()` uses, so retry behavior can never drift from the initial
    // load. Does not touch any other candidate's state or restart the whole workflow.
    fun retryCandidate(key: MangaIdentityKey) {
        val manga = state.value.compareCandidates.find { MangaIdentityKey(it.source, it.url) == key } ?: return
        val chapterMap = state.value.candidateChapters
        // KMK v0.8.18: a Skipped (unavailable-chapter) candidate has nothing to retry -- the chapter
        // itself is unavailable, not the preview fetch, so retrying would just re-derive the same
        // Skipped state via previewOneCandidate's own `?: return null` guard. Guard here explicitly
        // so a stray Retry affordance can never fire a pointless network round-trip.
        if (chapterMap[key] !is CandidateChapterState.Available) return
        val sampleSize = state.value.sampleSize
        val avoidFirstPages = state.value.avoidFirstPages
        mutableState.update { current ->
            current.copy(candidatePreviews = current.candidatePreviews.mutate { it[key] = CandidatePreviewState.Loading })
        }
        ioCoroutineScope.launch {
            val result = previewOneCandidate(manga, chapterMap, sampleSize, avoidFirstPages)
                ?: CandidatePreviewState.PreviewError(RecommendationErrorKind.Internal.storageKey)
            mutableState.update { current ->
                current.copy(candidatePreviews = current.candidatePreviews.mutate { it[key] = result })
            }
        }
    }

    // KMK v0.8.17-fix1: shared by startPreview() and retryCandidate() -- returns null only when this
    // candidate has no available chapter or resolvable source at all (a state that should never occur
    // for an already-previewable candidate, kept only as a defensive no-op rather than a crash).
    private suspend fun previewOneCandidate(
        manga: Manga,
        chapterMap: PersistentMap<MangaIdentityKey, CandidateChapterState>,
        sampleSize: Int,
        avoidFirstPages: Boolean,
    ): CandidatePreviewState? {
        val key = MangaIdentityKey(manga.source, manga.url)
        val candidateChapter = (chapterMap[key] as? CandidateChapterState.Available)?.chapter ?: return null
        val source = sourceManager.get(manga.source) as? HttpSource ?: return null
        // KMK v0.8.17-fix1: bounded per-candidate timeout -- a slow/hanging source can no longer keep
        // this candidate's row stuck in Loading indefinitely or block the whole screen from reaching
        // ComparePreview (see startPreview()'s doc comment on the awaitAll() gating this replaces the
        // risk of). Times out to the same RecommendationErrorKind.Timeout classification other KMK
        // recommendation flows already use for a slow network operation.
        return withTimeoutOrNull(PREVIEW_CANDIDATE_TIMEOUT_MS) {
            // KMK v0.8.10-fix4: routed through SourceRuntime instead of a local
            // catch(Exception)/catch(Error) pair -- one shared boundary classifies both
            // and records a recoverable extension LinkageError in
            // SourceRuntimeFailureRegistry; still always rethrows CancellationException
            // and any genuinely fatal Error.
            SourceRuntime.run(source, SourceRuntimeOperation.PageList, coroutineDispatcher) {
                getPageList(candidateChapter)
            }.fold(
                onSuccess = { pages ->
                    val indexes = BestVersionPageSampler.sample(pages.size, sampleSize, avoidFirstPages)
                    val sampledPages = indexes.mapNotNull { idx ->
                        val page = pages.getOrNull(idx) ?: return@mapNotNull null
                        val imageUrl = page.imageUrl
                            ?: SourceRuntime.run(source, SourceRuntimeOperation.ImageUrl, coroutineDispatcher) {
                                (this as HttpSource).getImageUrl(page)
                            }.getOrNull()
                            ?: return@mapNotNull null
                        // KMK v0.8.16-fix1: source-aware display model -- see SampledPage's doc
                        // comment. manga.source (not source.id) matches PagePreviewFetcher
                        // .Factory's sourceManager.get(data.source) lookup contract.
                        SampledPage(idx, eu.kanade.domain.manga.model.PagePreview(idx, imageUrl, manga.source))
                    }
                    // KMK v0.8.16-fix1: a candidate whose pages could not resolve into a single
                    // usable sampled page must not report a misleading "loaded" success row
                    // with an empty thumbnail strip -- show the same PreviewError state a real
                    // failure would (see BestVersionPreviewOutcomePolicy's doc comment).
                    if (BestVersionPreviewOutcomePolicy.hasUsablePreview(sampledPages.size)) {
                        CandidatePreviewState.Loaded(sampledPages)
                    } else {
                        CandidatePreviewState.PreviewError(RecommendationErrorClassifier.classifyToStorageKey(IllegalStateException("No preview pages available")))
                    }
                },
                onFailure = { throwable ->
                    // KMK v0.7.46: stable key, not raw exception text — see RecommendationErrorClassifier.
                    CandidatePreviewState.PreviewError(RecommendationErrorClassifier.classifyToStorageKey(throwable))
                },
            )
        } ?: CandidatePreviewState.PreviewError(RecommendationErrorKind.Timeout.storageKey)
    }

    companion object {
        // KMK v0.8.17-fix1: bounded per-candidate preview timeout (page-list fetch + per-page image-
        // URL resolution). 25s balances slow-but-real sources against not leaving the screen stuck on
        // a single hung candidate; matches the ballpark of other KMK source-operation timeouts.
        private const val PREVIEW_CANDIDATE_TIMEOUT_MS = 25_000L
    }

    // KMK v0.8.16: candidate rows previously only showed title, making it unclear which extension
    // each row/preview actually belongs to during comparison. Resolved from the model since
    // sourceManager is already injected here (avoids a second injection in the composable).
    // KMK --> v0.8.19: evaluation mode source-name obfuscation. Not a @Composable context, so it
    // reads the preference directly instead of via rememberEvaluationModeEnabled().
    fun sourceName(sourceId: Long): String =
        BestVersionSourceLabelPolicy.resolve(
            evaluationModeEnabled = sourcePreferences.evaluationMode().get(),
            sourceId = sourceId,
            rawName = { sourceManager.getOrStub(sourceId).name },
        )
    // KMK <--

    // KMK v0.8.18: routes to keepCurrentVersion() when the selected key is the origin/current manga
    // -- selecting origin as "best" is a safe no-op finalize, never a self-migration. Only a real,
    // non-origin candidate ever sets selectedBestKey and opens the migrate/copy confirmation dialog.
    fun selectBestVersion(key: MangaIdentityKey) {
        if (key == state.value.originKey) {
            keepCurrentVersion()
            return
        }
        mutableState.update { it.copy(selectedBestKey = key) }
    }

    // KMK v0.8.18: selecting the origin as best finalizes immediately -- there is nothing to migrate
    // or copy, so this never calls migrateMangaUseCase and never opens MigrationConfirmDialog. Done's
    // "Open" target resolves to the origin manga itself (already the correct id), and
    // `keptCurrentVersion` lets the Done screen show honest "kept current version" wording instead of
    // "migration complete".
    fun keepCurrentVersion() {
        val origin = originManga ?: return
        mutableState.update {
            it.copy(
                step = BestVersionStep.Done,
                selectedBestKey = null,
                migrationComplete = true,
                keptCurrentVersion = true,
                completedTargetMangaId = origin.id,
            )
        }
    }

    // KMK --> v0.7.9: dedicated dismiss — clears fake sentinel, preserves all preview state
    fun dismissMigrationDialog() {
        mutableState.update { it.copy(selectedBestKey = null, isMigrating = false) }
    }
    // KMK <--

    fun confirmMigration(replace: Boolean) {
        // KMK --> v0.7.9: defensive guard — clear selected key if origin or target cannot be resolved
        val origin = originManga ?: run {
            mutableState.update { it.copy(step = BestVersionStep.Error("Could not load origin manga."), selectedBestKey = null) }
            return
        }
        val key = state.value.selectedBestKey ?: return
        val target = state.value.selectedCandidates.find { it.source == key.source && it.url == key.url }
        if (target == null) {
            dismissMigrationDialog()
            return
        }
        // KMK <--
        mutableState.update { it.copy(step = BestVersionStep.PreparingMigration, isMigrating = true) }
        migrationJob = screenModelScope.launch {
            try {
                // KMK Confirmed Blocker Remediation Phase 4: migrateMangaUseCase now returns a
                // truthful mihon.domain.migration.usecases.MigrationOutcome instead of Unit -- a
                // non-fatal exception partway through the migration previously vanished silently,
                // and this call site unconditionally recorded MIGRATION_COMPLETED and navigated to
                // Done regardless. Only Success reaches that path now; PartialFailure/NotStarted
                // surface a truthful error instead, matching the plan's explicit requirement to
                // "never record a successful migration... before the underlying operation returns
                // success."
                when (val outcome = migrateMangaUseCase(current = origin, target = target, replace = replace)) {
                    is mihon.domain.migration.usecases.MigrationOutcome.Success -> {
                        saveQualitySignal(origin, target)
                        // KMK v0.8.20-fix1: migration has no safe inverse (see
                        // best_version_migrate_not_undoable -- it may delete downloaded chapters and
                        // can update an external tracker this device cannot roll back), so it is
                        // recorded as a non-undoable, visibility-only Action History event rather
                        // than a fake Undo. Only recorded after the migration write above actually
                        // succeeded. See exh.util.NonUndoableEventJournal's doc for the exact scope.
                        if (sourcePreferences.evaluationMode().get()) {
                            exh.util.NonUndoableEventJournal.record(
                                exh.util.NonUndoableEvent(
                                    id = exh.util.NonUndoableEvent.newId(),
                                    timestamp = System.currentTimeMillis(),
                                    eventType = exh.util.NonUndoableEventType.MIGRATION_COMPLETED,
                                ),
                            )
                        }
                        mutableState.update {
                            it.copy(
                                step = BestVersionStep.Done,
                                isMigrating = false,
                                migrationComplete = true,
                                completedTargetMangaId = target.id,
                            )
                        }
                    }
                    is mihon.domain.migration.usecases.MigrationOutcome.PartialFailure -> {
                        mutableState.update {
                            it.copy(
                                step = BestVersionStep.Error(RecommendationErrorClassifier.classifyToStorageKey(outcome.cause)),
                                isMigrating = false,
                            )
                        }
                    }
                    is mihon.domain.migration.usecases.MigrationOutcome.NotStarted -> {
                        mutableState.update {
                            it.copy(
                                step = BestVersionStep.Error(
                                    outcome.cause?.let { c -> RecommendationErrorClassifier.classifyToStorageKey(c) }
                                        ?: RecommendationErrorKind.Internal.storageKey,
                                ),
                                isMigrating = false,
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                // KMK Confirmed Blocker Remediation Corrective Completion Plan V3 2026-07-29 Phase A:
                // CancellationException is a subtype of Exception -- the broad catch below previously
                // swallowed it and converted a real coroutine cancellation into a fake
                // BestVersionStep.Error, breaking structured concurrency (the caller/parent scope never
                // observed the cancellation). Must always be rethrown before the broad catch runs.
                throw e
            } catch (e: Exception) {
                mutableState.update {
                    it.copy(
                        // KMK v0.7.46: stable key, not raw exception text — see RecommendationErrorClassifier.
                        step = BestVersionStep.Error(RecommendationErrorClassifier.classifyToStorageKey(e)),
                        isMigrating = false,
                    )
                }
            }
        }
    }

    private suspend fun saveQualitySignal(origin: Manga, target: Manga) {
        val source = sourceManager.get(target.source)
        val signal = MangaSourceQualitySignal(
            id = 0,
            originSourceId = origin.source,
            originUrl = origin.url,
            originTitle = origin.title,
            selectedSourceId = target.source,
            selectedUrl = target.url,
            selectedTitle = target.title,
            selectedSourceName = source?.name ?: "",
            comparedCandidatesJson = "[]",
            chapterNumber = state.value.selectedChapterNumber,
            chapterName = "",
            sampleSize = state.value.sampleSize,
            sampledPagesJson = "[]",
            selectedAt = System.currentTimeMillis(),
            qualitySignalVersion = 1,
        )
        runCatching { upsertQualitySignal.insert(signal) }
    }

    private fun resolveSettings(): SameMangaMatchSettings {
        return SameMangaMatchSettings(
            resultsPerSource = SameMangaMatchSettings.clampResultCap(
                sourcePreferences.sameMangaMatchResultsPerSource().get(),
            ),
            preselectResults = sourcePreferences.sameMangaMatchPreselectResults().get(),
            previewSampleSize = SameMangaMatchSettings.clampSampleSize(
                sourcePreferences.bestVersionPreviewSampleSize().get(),
            ),
            avoidFirstPages = sourcePreferences.bestVersionAvoidFirstPages().get(),
        )
    }
}
// KMK <--

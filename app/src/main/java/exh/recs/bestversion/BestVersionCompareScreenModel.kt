package exh.recs.bestversion

// KMK --> v0.7.8
import android.app.Application
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceRuntime
import eu.kanade.tachiyomi.source.SourceRuntimeFailureRegistry
import eu.kanade.tachiyomi.source.SourceRuntimeOperation
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.system.DeviceUtil
import exh.recs.RecommendationErrorClassifier
import exh.recs.RecommendationErrorKind
import exh.recs.bestversion.fixture.BestVersionPairedFixtureRouteResolverContract
import exh.recs.matching.MangaIdentityKey
import exh.recs.matching.SameMangaCandidateResult
import exh.recs.matching.SameMangaMatchSettings
import exh.recs.matching.SameMangaPreselectionMode
import exh.recs.matching.SameMangaPreselectionPolicy
import exh.source.isEhBasedSource
import exh.util.DispatcherHandle
import exh.util.ownedFixedThreadPoolDispatcherHandle
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import mihon.domain.migration.models.MigrationFlag
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

sealed interface BestVersionStep {
    data object LoadingOrigin : BestVersionStep
    data object SearchingCandidates : BestVersionStep
    data object ConfirmCandidates : BestVersionStep
    data object LoadingChapters : BestVersionStep
    data object SelectChapter : BestVersionStep
    data object LoadingPreview : BestVersionStep
    data object ComparePreview : BestVersionStep
    data object PreparingMigration : BestVersionStep
    data class Error(val reason: BestVersionErrorReason) : BestVersionStep
    data object Done : BestVersionStep
}

sealed interface BestVersionErrorReason {
    data object OriginMissing : BestVersionErrorReason
    data object SourceUnavailable : BestVersionErrorReason
    data class Recommendation(val kind: RecommendationErrorKind) : BestVersionErrorReason
}

// KMK R2-AUG-05-ACTUAL-BEST-VERSION-FAILURE-PATH: discloses whether an Available candidate
// chapter is the real (+/-0.01) match, a disclosed non-exact nearest-readable-chapter fallback (see
// BestVersionChapterMatcher.ChapterMatchResult.Nearest), or a chapter the user picked by hand via
// the bounded manual chapter picker. A Nearest match must never be presented to the user as if it
// were the same chapter as the origin -- the UI renders each case with different wording.
sealed interface ChapterMatchDisclosure {
    data object Exact : ChapterMatchDisclosure
    data class Nearest(val originChapterNumber: Double, val candidateChapterNumber: Double) : ChapterMatchDisclosure
    data object Manual : ChapterMatchDisclosure
}

sealed interface CandidateChapterState {
    data object Loading : CandidateChapterState
    data class Available(
        val chapter: SChapter,
        val totalChapters: Int,
        val matchDisclosure: ChapterMatchDisclosure = ChapterMatchDisclosure.Exact,
    ) : CandidateChapterState
    data object Unavailable : CandidateChapterState
    data class ChapterError(val reason: BestVersionErrorReason) : CandidateChapterState
}

sealed interface CandidatePreviewState {
    data object Loading : CandidatePreviewState
    data class Loaded(
        val pages: List<SampledPage>,
        // A page can be sampled successfully while its source-specific image URL lookup fails.
        // Keep those indexes so the row remains recoverable through candidate retry instead of
        // silently shrinking the preview and losing the failure context.
        val failedPageIndexes: Set<Int> = emptySet(),
    ) : CandidatePreviewState
    data class PreviewError(val reason: BestVersionErrorReason) : CandidatePreviewState
    // KMK v0.8.18: a candidate whose chapter was already CandidateChapterState.Unavailable before
    // startPreview() ran must never be sent into page-list/image-url fetching at all -- it is not a
    // "failure" (nothing was attempted), it is a deliberately skipped row. Kept distinct from
    // PreviewError so the UI can render "Chapter unavailable, preview skipped" instead of a
    // network/source failure message, and so it is never eligible for Retry (there is nothing to
    // retry -- the chapter itself is unavailable, not the preview fetch).
    data object Skipped : CandidatePreviewState
}

// Carries a source-aware eu.kanade.domain.manga.model.PagePreview instead of a raw URL string. A raw
// AsyncImage path can report sampled pages while every thumbnail fails, because sources that require
// source-specific preview fetching, headers, or cache behavior need PagePreviewFetcher's
// source-runtime boundary (SourceRuntime.run(..., SourceRuntimeOperation.PreviewImage)), which raw URL
// loading bypasses entirely. `preview.index`/`preview.imageUrl` are still available directly off
// [PagePreview] for any code that only needs the primitives.
data class SampledPage(val index: Int, val preview: eu.kanade.domain.manga.model.PagePreview)

/**
 * State for the reader-quality fullscreen candidate preview (the swipe/zoom pager opened via
 * "Open fullscreen compare"), kept deliberately separate from [CandidatePreviewState]'s bounded
 * [BestVersionPageSampler] thumbnail strip. That strip remains an intentional at-a-glance summary
 * (a handful of sampled thumbnails inline in the candidate row) and is unaffected by this type --
 * the confirmed gap this closes is specific to the fullscreen dialog, which previously rendered
 * only that same bounded sample instead of a genuine chapter-quality preview.
 *
 * [Loaded.pages] is the FULL raw page list for the candidate's matched chapter (fetched once via
 * `SourceRuntime.run(..., PageList)`), never truncated or sampled. Each page's image is resolved
 * lazily on demand (see [BestVersionCompareScreenModel.requestFullscreenPageImage]) as the pager
 * actually needs it, tracked per-index in [Loaded.pageImages] -- never eagerly for the whole
 * chapter, matching the real reader's own lazy-URL-resolution contract.
 */
sealed interface FullscreenPreviewState {
    data object Loading : FullscreenPreviewState
    data class Loaded(
        val key: MangaIdentityKey,
        val pages: List<Page>,
        val pageImages: PersistentMap<Int, PreviewPageImageState> = persistentMapOf(),
    ) : FullscreenPreviewState
    data class Error(val reason: BestVersionErrorReason) : FullscreenPreviewState
}

/** Per-page lazy image-resolution state within a [FullscreenPreviewState.Loaded] preview. */
sealed interface PreviewPageImageState {
    data object Loading : PreviewPageImageState
    data class Resolved(val preview: SampledPage) : PreviewPageImageState
    data class Failed(val reason: BestVersionErrorReason) : PreviewPageImageState
}
// KMK <--

// KMK Confirmed Blocker Remediation Corrective Completion Plan V3 2026-07-29 Phase B: pairs a
// dispatcher with an explicit close action, so [BestVersionCompareScreenModel.onDispose] never has to
// guess ownership from the dispatcher's runtime type. `close` defaults to a no-op: any dispatcher a
// caller (test or otherwise) supplies from outside is never closed unless that caller explicitly opts
// in by passing its own `close` action along with it.
// KMK V3 Phase B: the only handle in this file that legitimately owns its executor -- production
// default for [BestVersionCompareScreenModel]'s `dispatcherHandle` parameter. Identical concurrency
// limit to the pre-V3 hardcoded dispatcher; the only behavioral change is that closing it is now driven
// by this handle's own `close` action rather than by `is ExecutorCoroutineDispatcher` type-checking.
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
    private val isLowRamDevice: Boolean = DeviceUtil.isLowRamDevice(Injekt.get<Application>()),
    private val dispatcherHandle: DispatcherHandle = ownedFixedThreadPoolDispatcherHandle(),
    private val candidateSearchGateway: BestVersionCandidateSearchGateway =
        SameMangaBestVersionCandidateSearchGateway(
            sourcePreferences = sourcePreferences,
            sourceManager = sourceManager,
            networkToLocalManga = networkToLocalManga,
            coroutineDispatcher = dispatcherHandle.dispatcher.limitedParallelism(
                exh.recs.RecommendationEffectiveResourcePolicy.sourceConcurrency(isLowRamDevice),
            ),
        ),
    private val fixtureOperationId: String? = null,
    private val fixtureRouteResolver: BestVersionPairedFixtureRouteResolverContract? = null,
    private val identityController: exh.recs.matching.CrossSourceIdentityDecisionController =
        exh.recs.matching.CrossSourceIdentityDecisionController(),
) : StateScreenModel<BestVersionCompareScreenModel.State>(State()) {

    // KMK V3 Phase B: kept as a plain val (not a computed property) so every existing SourceRuntime.run
    // call site below is unchanged -- only construction and disposal of the underlying dispatcher
    // changed, not how it's consumed mid-flight.
    private val coroutineDispatcher: CoroutineDispatcher = dispatcherHandle.dispatcher.limitedParallelism(
        exh.recs.RecommendationEffectiveResourcePolicy.sourceConcurrency(isLowRamDevice),
    )

    private var searchJob: Job? = null
    private var originManga: Manga? = null
    private var disposed = false
    private var activeCandidateSearchGateway = candidateSearchGateway
    private var migrationPresetFlags: Set<MigrationFlag>? = null
    private var isCurrentMigrationTarget: suspend (Manga) -> Boolean = { true }
    private val previewLoadCoordinator = BestVersionPreviewLoadCoordinator(
        exh.recs.RecommendationEffectiveResourcePolicy.previewConcurrency(isLowRamDevice),
    )

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
        // KMK R2-AUG-05: raw fetched chapter list per non-origin candidate, kept so the UI can
        // offer a bounded manual chapter picker scoped to that candidate's own chapters instead of
        // being stuck with the auto-matched (possibly Nearest, non-exact) chapter. Never populated
        // for the origin (its chapter is resolved locally, not via a fetched SChapter list).
        val candidateChapterLists: PersistentMap<MangaIdentityKey, List<SChapter>> = persistentMapOf(),
        val candidatePreviews: PersistentMap<MangaIdentityKey, CandidatePreviewState> = persistentMapOf(),
        // Per-candidate retry generations prevent an older preview request from overwriting a
        // newer retry result after both requests overlap.
        val candidatePreviewGenerations: PersistentMap<MangaIdentityKey, Int> = persistentMapOf(),
        // The reader-quality fullscreen preview has its own
        // state -- only one candidate's fullscreen preview can be open at a time (matches the
        // existing UI, which tracks a single fullscreenCandidateKey). Null when the dialog is
        // closed. See [FullscreenPreviewState]'s doc comment for why this is separate from
        // [candidatePreviews].
        val fullscreenPreview: FullscreenPreviewState? = null,
        // Retains the candidate identity while its full page list is loading or has failed, so the
        // fullscreen error state can retry the same chapter without guessing from UI state.
        val fullscreenCandidateKey: MangaIdentityKey? = null,
        // Per-page retry generation counters for the fullscreen preview, keyed by page index within
        // whichever candidate is currently open. Reset whenever a different candidate's fullscreen
        // preview opens. Same capture-before-async-op / compare-and-set-on-write shape as
        // SourceRuntimeFailureRegistry.clearIfUnchanged (R2 fix6) -- see
        // resolveFullscreenPageImage's doc comment for the exact contract this enforces.
        val fullscreenPageGenerations: PersistentMap<Int, Int> = persistentMapOf(),
        // KMK C2 (HR-2026-08-26-RATED-COLLECTIONS-AND-BEST-VERSION-CORRECTIONS, E4.2): monotonic
        // generation bumped on every openFullscreenCandidate/closeFullscreenCandidate call.
        // FullscreenPreviewState.Loading carries no candidate identity by itself, so two overlapping
        // opens for different candidates previously had no way to tell which in-flight fetch was
        // still "current" -- an older candidate's response could win the race and populate a newer
        // candidate's dialog. See openFullscreenCandidate's doc comment for the exact guard this
        // enables.
        val fullscreenOpenGeneration: Int = 0,
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
                    it.copy(step = BestVersionStep.Error(BestVersionErrorReason.OriginMissing))
                }
                return@launch
            }
            originManga = manga
            mutableState.update { it.copy(originManga = manga) }
            if (fixtureOperationId != null) {
                val binding = fixtureRouteResolver?.resolve(fixtureOperationId, manga)
                if (binding == null) {
                    mutableState.update {
                        it.copy(step = BestVersionStep.Error(BestVersionErrorReason.SourceUnavailable))
                    }
                    return@launch
                }
                activeCandidateSearchGateway = binding.candidateSearchGateway
                migrationPresetFlags = binding.migrationPresetFlags
                isCurrentMigrationTarget = binding.isCurrentTarget
            }
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
        migrationJob?.cancel()
        if (!disposed) {
            disposed = true
            dispatcherHandle.close()
        }
    }

    private fun startSearch(manga: Manga) {
        val settings = resolveSettings()
        val sources = activeCandidateSearchGateway.getMatchingSources()
        mutableState.update {
            it.copy(
                step = BestVersionStep.SearchingCandidates,
                candidates = sources.associateWith<Source, SameMangaCandidateResult> {
                    SameMangaCandidateResult.Loading
                }.toPersistentMap(),
                candidatePreviews = persistentMapOf(),
                candidatePreviewGenerations = persistentMapOf(),
                sampleSize = settings.previewSampleSize,
                avoidFirstPages = settings.avoidFirstPages,
            )
        }
        val queries = exh.recs.matching.CrossExtensionMatchQueryPlanner.buildQueries(manga)
        searchJob = screenModelScope.launch(coroutineDispatcher) {
            try {
                activeCandidateSearchGateway.search(
                    queries = queries,
                    settings = settings,
                    originManga = manga,
                    sources = sources,
                ) { result ->
                    if (isActive && !disposed) {
                        updateCandidate(result.source, result.result, settings.preselectionMode)
                    }
                }
                if (isActive) {
                    mutableState.update {
                        it.copy(step = BestVersionStep.ConfirmCandidates)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isActive && !disposed) {
                    mutableState.update {
                        it.copy(
                            step = BestVersionStep.Error(
                                BestVersionErrorReason.Recommendation(
                                    RecommendationErrorClassifier.classify(e),
                                ),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun updateCandidate(source: Source, result: SameMangaCandidateResult, preselectionMode: SameMangaPreselectionMode) {
        val origin = originManga
        mutableState.update { current ->
            val newCandidates = current.candidates.mutate { it[source] = result }
            val newSelected = if (result is SameMangaCandidateResult.Success) {
                val newKeys = result.results.mapNotNull { manga ->
                    val key = MangaIdentityKey(manga.source, manga.url)
                    if (origin != null && manga.source == origin.source && manga.url == origin.url) return@mapNotNull null
                    if (key in current.manuallyDeselectedKeys) return@mapNotNull null
                    if (origin != null && SameMangaPreselectionPolicy.shouldSelect(preselectionMode, origin, manga)) key else null
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

    /**
     * Re-fetches only candidates whose chapter request currently failed. Successful, unavailable,
     * and manually selected rows remain untouched so a single Retry cannot discard usable work.
     */
    fun retryFailedChapterLoads() {
        val current = state.value
        val retryableKeys = current.candidateChapters
            .filterValues { it is CandidateChapterState.ChapterError }
            .keys
        if (retryableKeys.isEmpty()) return
        val candidates = current.compareCandidates.filter { MangaIdentityKey(it.source, it.url) in retryableKeys }
        val targetChapterNumber = current.selectedChapterNumber
        mutableState.update {
            it.copy(
                candidateChapters = it.candidateChapters.mutate { chapters ->
                    retryableKeys.forEach { key -> chapters[key] = CandidateChapterState.Loading }
                },
            )
        }
        screenModelScope.launch(coroutineDispatcher) {
            val results = candidates.map { manga ->
                async { loadCandidateChapter(manga, targetChapterNumber, bypassSuppression = true) }
            }.awaitAll()
            if (isActive) {
                mutableState.update { currentState ->
                    currentState.copy(
                        candidateChapters = currentState.candidateChapters.mutate { chapters ->
                            results.forEach { (key, chapterState, _) -> chapters[key] = chapterState }
                        },
                        candidateChapterLists = currentState.candidateChapterLists.mutate { chapterLists ->
                            results.forEach { (key, _, chapters) ->
                                if (chapters != null) chapterLists[key] = chapters
                            }
                        },
                    )
                }
            }
        }
    }

    private fun loadChapters(candidates: List<Manga>) {
        val origin = originManga ?: return
        mutableState.update { it.copy(step = BestVersionStep.LoadingChapters) }
        screenModelScope.launch(coroutineDispatcher) {
            // Load origin chapters to determine default chapter
            val originChapters = try {
                getChaptersByMangaId.await(origin.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            val defaultChapter = BestVersionChapterMatcher.selectDefaultChapter(originChapters)
            // Preserve a finite negative chapter number (the normal special/prologue sentinel).
            // Null means the origin has no chapter to match; it must remain distinct from -1.0.
            val targetChapterNumber = defaultChapter?.chapterNumber

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
            // KMK R2-AUG-05: each async result also carries the candidate's raw fetched chapter list
            // (null when the fetch itself failed/had no source) so a successful fetch's full chapter
            // list survives into State.candidateChapterLists for the bounded manual chapter picker,
            // regardless of whether BestVersionChapterMatcher found an Exact, Nearest, or no match.
            val chapterResults = nonOriginCandidates.map { manga ->
                async { loadCandidateChapter(manga, targetChapterNumber) }
            }.awaitAll()

            if (isActive) {
                val chapterMap = (
                    chapterResults.map { (key, state, _) -> key to state } + (originKey to originChapterState)
                    ).toMap().toPersistentMap()
                val chapterLists = chapterResults
                    .mapNotNull { (key, _, chapters) -> chapters?.let { key to it } }
                    .toMap()
                    .toPersistentMap()
                mutableState.update {
                    it.copy(
                        originChapters = originChapters,
                        selectedChapterNumber = targetChapterNumber,
                        candidateChapters = chapterMap,
                        candidateChapterLists = chapterLists,
                        step = BestVersionStep.SelectChapter,
                    )
                }
            }
        }
    }

    private suspend fun loadCandidateChapter(
        manga: Manga,
        targetChapterNumber: Double?,
        bypassSuppression: Boolean = false,
    ): Triple<MangaIdentityKey, CandidateChapterState, List<SChapter>?> {
        val key = MangaIdentityKey(manga.source, manga.url)
        val source = sourceManager.get(manga.source)
        if (source == null) {
            return Triple(key, CandidateChapterState.ChapterError(BestVersionErrorReason.SourceUnavailable), null)
        }
        // KMK v0.8.10-fix4: use the shared SourceRuntime boundary for source exceptions.
        val sManga = manga.toSManga()
        return try {
            withTimeout(CHAPTER_LIST_TIMEOUT_MS) {
                SourceRuntime.run(
                    source,
                    SourceRuntimeOperation.MangaUpdate,
                    coroutineDispatcher,
                    bypassSuppression = bypassSuppression,
                ) {
                    getMangaUpdate(
                        manga = sManga,
                        chapters = emptyList(),
                        fetchDetails = false,
                        fetchChapters = true,
                    ).chapters
                }.fold(
                    onSuccess = { chapters ->
                        val matchResult = if (targetChapterNumber != null) {
                            BestVersionChapterMatcher.findMatch(targetChapterNumber, chapters, manga.title)
                        } else {
                            BestVersionChapterMatcher.selectLatestCandidate(chapters, manga.title)
                                ?.let { BestVersionChapterMatcher.ChapterMatchResult.Exact(it) }
                        }
                        val state: CandidateChapterState = if (matchResult != null) {
                            CandidateChapterState.Available(
                                chapter = matchResult.chapter,
                                totalChapters = chapters.size,
                                matchDisclosure = when (matchResult) {
                                    is BestVersionChapterMatcher.ChapterMatchResult.Exact -> ChapterMatchDisclosure.Exact
                                    is BestVersionChapterMatcher.ChapterMatchResult.Nearest -> ChapterMatchDisclosure.Nearest(
                                        originChapterNumber = matchResult.originChapterNumber,
                                        candidateChapterNumber = matchResult.candidateChapterNumber,
                                    )
                                },
                            )
                        } else {
                            CandidateChapterState.Unavailable
                        }
                        Triple(key, state, chapters)
                    },
                    onFailure = { throwable ->
                        Triple(
                            key,
                            CandidateChapterState.ChapterError(
                                BestVersionErrorReason.Recommendation(RecommendationErrorClassifier.classify(throwable)),
                            ),
                            null,
                        )
                    },
                )
            }
        } catch (e: TimeoutCancellationException) {
            if (!currentCoroutineContext().isActive) throw e
            Triple(
                key,
                CandidateChapterState.ChapterError(
                    BestVersionErrorReason.Recommendation(RecommendationErrorKind.Timeout),
                ),
                null,
            )
        }
    }

    // KMK R2-AUG-05-ACTUAL-BEST-VERSION-FAILURE-PATH: bounded manual chapter override -- lets the
    // user pick a different chapter from THIS candidate's own already-fetched chapter list instead of
    // being stuck with the auto-matched (possibly disclosed-as-Nearest) chapter. [chapter] must come
    // from `state.value.candidateChapterLists[key]` (the candidate's own fetched list) -- this never
    // substitutes another source's/candidate's pages, it only changes which of THIS candidate's own
    // chapters is used for chapter-loading/preview. A no-op for the origin (which has no fetched
    // chapter list here) or a key with no fetched chapter list at all.
    fun selectManualChapter(key: MangaIdentityKey, chapter: SChapter) {
        val chapters = state.value.candidateChapterLists[key] ?: return
        if (chapter !in chapters) return
        val mangaTitle = state.value.compareCandidates
            .firstOrNull { it.source == key.source && it.url == key.url }
            ?.title
            .orEmpty()
        val normalizedChapter = BestVersionChapterMatcher.normalizeChapter(chapter, mangaTitle)
        mutableState.update { current ->
            current.copy(
                candidateChapters = current.candidateChapters.mutate {
                    it[key] = CandidateChapterState.Available(
                        chapter = normalizedChapter,
                        totalChapters = chapters.size,
                        matchDisclosure = ChapterMatchDisclosure.Manual,
                    )
                },
            )
        }
    }

    // Opens the reader-quality fullscreen preview for
    // [key] -- fetches that candidate's FULL page list (no BestVersionPageSampler truncation) via
    // the same SourceRuntime.run(..., PageList) boundary every other Best Version page-list fetch
    // uses. Image URLs are resolved lazily per-page afterward (see requestFullscreenPageImage), not
    // here -- this only needs the page count/identity, not every image.
    // KMK C2 (E4.2): bumps State.fullscreenOpenGeneration before launching and captures it in
    // [nextGeneration]. The completion handler only writes if that generation is still current --
    // this is request/candidate ownership, not the previous (and insufficient) check of "is the
    // state still exactly Loading," which could not distinguish an older overlapping open (for a
    // different key) from the newer one the user actually wants to see. Without this, tapping a
    // second candidate's fullscreen preview before the first one's page-list fetch resolved could
    // let the FIRST (now stale) response populate the dialog the user opened SECOND.
    fun openFullscreenCandidate(key: MangaIdentityKey) {
        val chapterState = state.value.candidateChapters[key] as? CandidateChapterState.Available ?: return
        val manga = state.value.compareCandidates.find { it.source == key.source && it.url == key.url } ?: return
        val source = sourceManager.get(manga.source) as? HttpSource
        val nextGeneration = state.value.fullscreenOpenGeneration + 1
        if (source == null) {
            mutableState.update {
                it.copy(
                    fullscreenPreview = FullscreenPreviewState.Error(BestVersionErrorReason.SourceUnavailable),
                    fullscreenCandidateKey = key,
                    fullscreenPageGenerations = persistentMapOf(),
                    fullscreenOpenGeneration = nextGeneration,
                )
            }
            return
        }
        mutableState.update {
            it.copy(
                fullscreenPreview = FullscreenPreviewState.Loading,
                fullscreenCandidateKey = key,
                fullscreenPageGenerations = persistentMapOf(),
                fullscreenOpenGeneration = nextGeneration,
            )
        }
        screenModelScope.launch(coroutineDispatcher) {
            val result = SourceRuntime.run(source, SourceRuntimeOperation.PageList, coroutineDispatcher) {
                getPageList(chapterState.chapter)
            }
            if (!isActive || disposed) return@launch
            mutableState.update { current ->
                // A newer openFullscreenCandidate/closeFullscreenCandidate call may have already
                // superseded this one while the fetch was in flight -- never clobber a newer request
                // with a stale result. Keyed on the generation captured before this coroutine ever
                // started, not on the current preview's runtime type.
                if (current.fullscreenOpenGeneration != nextGeneration) return@update current
                result.fold(
                    onSuccess = { pages ->
                        current.copy(
                            fullscreenPreview = if (pages.isEmpty()) {
                                FullscreenPreviewState.Error(
                                    BestVersionErrorReason.Recommendation(RecommendationErrorKind.Internal),
                                )
                            } else {
                                FullscreenPreviewState.Loaded(key = key, pages = pages)
                            },
                        )
                    },
                    onFailure = { throwable ->
                        current.copy(
                            fullscreenPreview = FullscreenPreviewState.Error(
                                BestVersionErrorReason.Recommendation(RecommendationErrorClassifier.classify(throwable)),
                            ),
                        )
                    },
                )
            }
        }
    }

    fun closeFullscreenCandidate() {
        mutableState.update {
            it.copy(
                fullscreenPreview = null,
                fullscreenCandidateKey = null,
                fullscreenPageGenerations = persistentMapOf(),
                // KMK C2 (E4.2): a still-in-flight open must never repopulate the dialog after it has
                // been explicitly closed.
                fullscreenOpenGeneration = it.fullscreenOpenGeneration + 1,
            )
        }
    }

    /** Retries the full page-list request for the candidate currently showing a fullscreen error. */
    fun retryFullscreenCandidate() {
        val current = state.value
        if (current.fullscreenPreview !is FullscreenPreviewState.Error) return
        current.fullscreenCandidateKey?.let(::openFullscreenCandidate)
    }

    // Lazily resolves one page's image only if it has
    // never been requested before -- a Failed page waits for an explicit retryFullscreenPageImage
    // call rather than silently re-attempting on every recomposition. This is what the fullscreen
    // pager calls as the user swipes to a page it hasn't shown yet.
    fun requestFullscreenPageImage(pageIndex: Int) {
        val loaded = state.value.fullscreenPreview as? FullscreenPreviewState.Loaded ?: return
        if (loaded.pageImages.containsKey(pageIndex)) return
        resolveFullscreenPageImage(pageIndex, forceRetry = false)
    }

    // A fullscreen Retry affordance is visible on an individual failed page, but its action is a
    // same-screen recovery action: retry every page currently exposing Retry, while preserving
    // already-resolved pages. The clicked index is retained in the callback shape for UI stability.
    fun retryFullscreenPageImage(pageIndex: Int) {
        var retryIndexes = emptyList<Int>()
        var retryKey: MangaIdentityKey? = null
        mutableState.update { current ->
            val currentLoaded = current.fullscreenPreview as? FullscreenPreviewState.Loaded
                ?: return@update current
            retryIndexes = BestVersionRetryScopePolicy.fullscreenPageIndexes(currentLoaded.pageImages)
            retryKey = currentLoaded.key
            if (retryIndexes.isEmpty()) return@update current
            current.copy(
                fullscreenPreview = currentLoaded.copy(
                    pageImages = currentLoaded.pageImages.mutate { images ->
                        retryIndexes.forEach { index -> images[index] = PreviewPageImageState.Loading }
                    },
                ),
            )
        }
        if (retryIndexes.isEmpty()) return
        val activeRetryKey = retryKey ?: return
        state.value.compareCandidates.find { it.source == activeRetryKey.source && it.url == activeRetryKey.url }
            ?.let { manga -> SourceRuntimeFailureRegistry.clear(manga.source) }
        retryIndexes.forEach { index -> resolveFullscreenPageImage(index, forceRetry = true) }
    }

    // Shared by requestFullscreenPageImage and
    // retryFullscreenPageImage. Bumps [State.fullscreenPageGenerations] for [pageIndex] before
    // launching so a stale in-flight resolution can never overwrite a newer retry's result -- the
    // async block re-checks its own captured generation is still current before writing (the exact
    // capture-before-async-op / compare-and-set-on-write shape SourceRuntimeFailureRegistry
    // .clearIfUnchanged uses for the R2 concurrent-bypass race fix, reused here at the per-page
    // level instead of the per-source registry level).
    //
    // Deliberately never mutates the shared Page objects held in State.fullscreenPreview.pages
    // (Page.imageUrl is a mutable var, but writing through it here would mean this "immutable"
    // State secretly carries mutable shared references) -- every resolved image URL lives only in
    // pageImages, keyed by page index, never written back onto the Page instance itself.
    private fun resolveFullscreenPageImage(pageIndex: Int, forceRetry: Boolean) {
        val loaded = state.value.fullscreenPreview as? FullscreenPreviewState.Loaded ?: return
        val page = loaded.pages.getOrNull(pageIndex) ?: return
        val key = loaded.key
        val manga = state.value.compareCandidates.find { it.source == key.source && it.url == key.url } ?: return
        val source = sourceManager.get(manga.source) as? HttpSource ?: return
        // KMK C2 (E4.1): needed only for a forced non-EH retry's page-list refetch below.
        val chapter = (state.value.candidateChapters[key] as? CandidateChapterState.Available)?.chapter

        // KMK: reuses BestVersionPreviewRetryPolicy's existing generation-bump/isCurrent contract
        // (previously only consulted by the Compose layer's own local retry map) as the screen
        // model's own stale-result guard, instead of a second hand-rolled counter.
        val nextGeneration = BestVersionPreviewRetryPolicy.requestRetry(state.value.fullscreenPageGenerations, pageIndex)[pageIndex]!!
        mutableState.update { current ->
            val currentLoaded = current.fullscreenPreview as? FullscreenPreviewState.Loaded ?: return@update current
            if (currentLoaded.key != key) return@update current
            current.copy(
                fullscreenPreview = currentLoaded.copy(
                    pageImages = currentLoaded.pageImages.put(pageIndex, PreviewPageImageState.Loading),
                ),
                fullscreenPageGenerations = BestVersionPreviewRetryPolicy
                    .requestRetry(current.fullscreenPageGenerations, pageIndex)
                    .toPersistentMap(),
            )
        }

        screenModelScope.launch(coroutineDispatcher) {
            // A forced retry on a
            // non-EH source previously fell straight through to the "already provided" branch below
            // and reused the exact same (possibly expired) direct URL forever -- only EH sources ever
            // got a genuinely fresh URL, via getImageUrl(). But HttpSource.getImageUrl's own contract
            // is "only called if Page.imageUrl is null" (see its KDoc); calling it against an
            // already-populated URL is undefined for the many sources that never override it because
            // their page-list response already is the final image URL. Re-fetching the chapter's page
            // list -- something every source already supports -- is the source-agnostic way to obtain
            // a genuinely fresh candidate URL for THIS retry without violating that contract: the
            // resulting page may carry a new URL (if the source regenerates one per fetch) or the
            // same one (if it's stable), and either way this never substitutes another candidate's or
            // another source's page.
            val retryPage = if (forceRetry && !source.isEhBasedSource() && chapter != null) {
                SourceRuntime.run(source, SourceRuntimeOperation.PageList, coroutineDispatcher) {
                    getPageList(chapter)
                }.getOrNull()?.getOrNull(pageIndex) ?: page
            } else {
                page
            }
            // KMK: mirrors HttpPageLoader.internalLoadPage's own "skip resolution if the source
            // already provided imageUrl in the page-list response" check -- many sources never
            // implement getImageUrl() at all because they don't need a second call, and calling it
            // anyway can throw. A forced EH-source retry is the one documented exception
            // (HttpPageLoader.retryPage nulls the cached URL first for EH sources specifically,
            // since theirs can expire/rotate) -- mirrored here via isEhBasedSource() instead of
            // mutating page.imageUrl. For a forced non-EH retry, [retryPage] above already carries
            // whatever URL the fresh page-list fetch just returned, so this check naturally uses that
            // fresh value instead of falling back to getImageUrl (which would violate its own
            // null-only contract).
            val alreadyProvided = retryPage.imageUrl
            val skipResolve = !alreadyProvided.isNullOrEmpty() && !(forceRetry && source.isEhBasedSource())
            val resolution = if (skipResolve) {
                Result.success(alreadyProvided)
            } else {
                SourceRuntime.run(source, SourceRuntimeOperation.ImageUrl, coroutineDispatcher) {
                    (this as HttpSource).getImageUrl(retryPage)
                }
            }
            val result = resolution.fold(
                onSuccess = { imageUrl ->
                    val outcome = BestVersionImageOutcomePolicy.classifyResolvedUrl(imageUrl)
                    if (outcome == BestVersionImageOutcome.Usable) {
                        PreviewPageImageState.Resolved(
                            SampledPage(
                                pageIndex,
                                eu.kanade.domain.manga.model.PagePreview(pageIndex, imageUrl, manga.source),
                            ),
                        )
                    } else {
                        PreviewPageImageState.Failed(BestVersionImageOutcomePolicy.toErrorReason(outcome))
                    }
                },
                onFailure = { throwable ->
                    PreviewPageImageState.Failed(
                        BestVersionErrorReason.Recommendation(RecommendationErrorClassifier.classify(throwable)),
                    )
                },
            )

            if (!isActive || disposed) return@launch
            mutableState.update { current ->
                val currentLoaded = current.fullscreenPreview as? FullscreenPreviewState.Loaded ?: return@update current
                if (currentLoaded.key != key) return@update current
                if (!BestVersionPreviewRetryPolicy.isCurrent(current.fullscreenPageGenerations, pageIndex, nextGeneration)) {
                    return@update current
                }
                current.copy(
                    fullscreenPreview = currentLoaded.copy(
                        pageImages = currentLoaded.pageImages.put(pageIndex, result),
                    ),
                )
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
            val generations = previewable.associate { manga ->
                MangaIdentityKey(manga.source, manga.url) to 0
            }.toPersistentMap()
            it.copy(
                step = BestVersionStep.LoadingPreview,
                candidatePreviews = previews,
                candidatePreviewGenerations = generations,
            )
        }

        val previewGenerationSnapshot = state.value.candidatePreviewGenerations
        screenModelScope.launch(coroutineDispatcher) {
            previewable.map { manga ->
                val key = MangaIdentityKey(manga.source, manga.url)
                val generation = previewGenerationSnapshot[key] ?: 0
                async {
                    val result = previewLoadCoordinator.runBounded {
                        BestVersionPreviewTerminalStatePolicy.resolve(
                            previewOneCandidate(manga, chapterMap, sampleSize, avoidFirstPages),
                        )
                    }
                    if (isActive && !disposed) {
                        mutableState.update { current ->
                            if (current.candidatePreviewGenerations[key] != generation) return@update current
                            current.copy(candidatePreviews = current.candidatePreviews.mutate { it[key] = result })
                        }
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

    // A candidate Retry affordance is rendered on each failed row, but the action retries the
    // complete current retryable candidate set. Loaded and Skipped rows remain untouched.
    @Suppress("UNUSED_PARAMETER")
    fun retryCandidate(key: MangaIdentityKey) {
        var retryKeys = emptyList<MangaIdentityKey>()
        var snapshot: State? = null
        mutableState.update { current ->
            retryKeys = BestVersionRetryScopePolicy.candidateKeys(current.candidatePreviews, current.candidateChapters)
            snapshot = current
            if (retryKeys.isEmpty()) return@update current
            current.copy(
                candidatePreviews = current.candidatePreviews.mutate { previews ->
                    retryKeys.forEach { retryKey -> previews[retryKey] = CandidatePreviewState.Loading }
                },
                candidatePreviewGenerations = current.candidatePreviewGenerations.mutate { generations ->
                    retryKeys.forEach { retryKey -> generations[retryKey] = (generations[retryKey] ?: 0) + 1 }
                },
            )
        }
        if (retryKeys.isEmpty()) return
        val currentSnapshot = snapshot ?: return
        val sampleSize = currentSnapshot.sampleSize
        val avoidFirstPages = currentSnapshot.avoidFirstPages
        retryKeys.forEach { retryKey ->
            val manga = currentSnapshot.compareCandidates.find { MangaIdentityKey(it.source, it.url) == retryKey } ?: return@forEach
            val generation = state.value.candidatePreviewGenerations[retryKey] ?: return@forEach
            screenModelScope.launch(coroutineDispatcher) {
                val result = previewLoadCoordinator.runBounded {
                    BestVersionPreviewTerminalStatePolicy.resolve(
                        previewOneCandidate(
                            manga,
                            currentSnapshot.candidateChapters,
                            sampleSize,
                            avoidFirstPages,
                            bypassSuppression = true,
                        ),
                    )
                }
                if (isActive && !disposed) {
                    mutableState.update { current ->
                        if (current.candidatePreviewGenerations[retryKey] != generation) return@update current
                        current.copy(candidatePreviews = current.candidatePreviews.mutate { it[retryKey] = result })
                    }
                }
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
        bypassSuppression: Boolean = false,
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
            SourceRuntime.run(
                source,
                SourceRuntimeOperation.PageList,
                coroutineDispatcher,
                bypassSuppression = bypassSuppression,
            ) {
                getPageList(candidateChapter)
            }.fold(
                onSuccess = { pages ->
                    val indexes = BestVersionPageSampler.sample(pages.size, sampleSize, avoidFirstPages)
                    var firstImageFailure: BestVersionImageOutcome? = null
                    val failedPageIndexes = mutableSetOf<Int>()
                    val sampledPages = indexes.mapNotNull { idx ->
                        val page = pages.getOrNull(idx) ?: run {
                            failedPageIndexes += idx
                            return@mapNotNull null
                        }
                        val fallbackImageUrl = if (
                            BestVersionImageOutcomePolicy.classifyResolvedUrl(page.imageUrl) == BestVersionImageOutcome.Usable
                        ) {
                            null
                        } else {
                            SourceRuntime.run(
                                source,
                                SourceRuntimeOperation.ImageUrl,
                                coroutineDispatcher,
                                bypassSuppression = bypassSuppression,
                            ) {
                                (this as HttpSource).getImageUrl(page)
                            }.getOrNull()
                        }
                        val imageUrl = BestVersionImageOutcomePolicy.selectUsableUrl(page.imageUrl, fallbackImageUrl)
                            ?: run {
                                firstImageFailure = firstImageFailure ?: BestVersionImageOutcomePolicy
                                    .classifyResolvedUrl(fallbackImageUrl ?: page.imageUrl)
                                failedPageIndexes += idx
                                return@mapNotNull null
                            }
                        val imageOutcome = BestVersionImageOutcomePolicy.classifyResolvedUrl(imageUrl)
                        if (imageOutcome != BestVersionImageOutcome.Usable) {
                            firstImageFailure = firstImageFailure ?: imageOutcome
                            failedPageIndexes += idx
                            return@mapNotNull null
                        }
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
                        CandidatePreviewState.Loaded(sampledPages, failedPageIndexes)
                    } else {
                        CandidatePreviewState.PreviewError(
                            BestVersionImageOutcomePolicy.toErrorReason(
                                firstImageFailure ?: BestVersionImageOutcome.SourceAbsent,
                            ),
                        )
                    }
                },
                onFailure = { throwable ->
                    // KMK v0.8.20-fix5: typed classification, never raw exception text.
                    val errorKind = RecommendationErrorClassifier.classify(throwable)
                    CandidatePreviewState.PreviewError(
                        BestVersionErrorReason.Recommendation(errorKind),
                    )
                },
            )
        } ?: CandidatePreviewState.PreviewError(
            BestVersionErrorReason.Recommendation(RecommendationErrorKind.Timeout),
        )
    }

    companion object {
        // A source extension must not keep the comparison route loading indefinitely while
        // paginating a chapter list. The candidate remains retryable and other candidates can
        // still reach the selection step.
        private const val CHAPTER_LIST_TIMEOUT_MS = 20_000L
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
        if (state.value.isMigrating || migrationJob?.isActive == true) return
        // KMK --> v0.7.9: defensive guard — clear selected key if origin or target cannot be resolved
        val origin = originManga ?: run {
            mutableState.update {
                it.copy(
                    step = BestVersionStep.Error(BestVersionErrorReason.OriginMissing),
                    selectedBestKey = null,
                )
            }
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
                if (!isCurrentMigrationTarget(target)) {
                    mutableState.update {
                        it.copy(
                            step = BestVersionStep.Error(BestVersionErrorReason.SourceUnavailable),
                            selectedBestKey = null,
                            isMigrating = false,
                        )
                    }
                    return@launch
                }
                val identityResult = identityController.mutate(
                    tachiyomi.domain.taste.model.CrossSourceIdentityDecisionPolicy.canonicalPair(
                        tachiyomi.domain.taste.model.CrossSourceRecordKey(origin.source, origin.url),
                        tachiyomi.domain.taste.model.CrossSourceRecordKey(target.source, target.url),
                    ),
                    exh.recs.matching.CrossSourceIdentityMutation.CONFIRM,
                )
                if (identityResult == exh.recs.matching.CrossSourceIdentityMutationResult.CONFLICT ||
                    identityResult == exh.recs.matching.CrossSourceIdentityMutationResult.FAILED
                ) {
                    mutableState.update {
                        it.copy(
                            step = BestVersionStep.Error(
                                BestVersionErrorReason.Recommendation(RecommendationErrorKind.Internal),
                            ),
                            selectedBestKey = null,
                            isMigrating = false,
                        )
                    }
                    return@launch
                }
                // KMK Confirmed Blocker Remediation Phase 4: migrateMangaUseCase now returns a
                // truthful mihon.domain.migration.usecases.MigrationOutcome instead of Unit -- a
                // non-fatal exception partway through the migration previously vanished silently,
                // and this call site unconditionally recorded MIGRATION_COMPLETED and navigated to
                // Done regardless. Only Success reaches that path now; PartialFailure/NotStarted
                // surface a truthful error instead, matching the plan's explicit requirement to
                // "never record a successful migration... before the underlying operation returns
                // success."
                when (
                    val outcome = migrateMangaUseCase(
                        current = origin,
                        target = target,
                        replace = replace,
                        presetFlags = migrationPresetFlags,
                    )
                ) {
                    is mihon.domain.migration.usecases.MigrationOutcome.Success -> {
                        // KMK v0.8.20-fix1: migration has no safe local-database inverse (see
                        // best_version_migrate_not_undoable -- it may delete downloaded chapters and
                        // can update an external tracker this device cannot roll back), so it is
                        // recorded as a non-undoable, visibility-only Action History event rather
                        // than a fake Undo. Only recorded after the migration write above actually
                        // succeeded. See exh.util.NonUndoableEventJournal's doc for the exact scope.
                        // KMK Universal Action History Recovery Plan 2026-07-31: a typed
                        // MigrationReceipt is recorded alongside the event (same shared-id
                        // correlation pattern as PackageOperationReceipt) so ActionHistoryRegistry
                        // can offer a real "Migrate back" compensating action -- a fresh reverse
                        // migration through the same use case, never a database rollback. See
                        // MigrationReceipt's own doc for exactly what this can and cannot recover.
                        val sharedId = exh.util.NonUndoableEvent.newId()
                        exh.util.NonUndoableEventJournal.record(
                            exh.util.NonUndoableEvent(
                                id = sharedId,
                                timestamp = System.currentTimeMillis(),
                                eventType = exh.util.NonUndoableEventType.MIGRATION_COMPLETED,
                            ),
                        )
                        exh.util.MigrationReceiptJournal.record(
                            exh.util.MigrationReceipt(
                                id = sharedId,
                                timestamp = System.currentTimeMillis(),
                                originMangaId = origin.id,
                                originSourceId = origin.source,
                                targetMangaId = target.id,
                                targetSourceId = target.source,
                                replace = replace,
                            ),
                        )
                        mutableState.update {
                            it.copy(
                                step = BestVersionStep.Done,
                                isMigrating = false,
                                migrationComplete = true,
                                completedTargetMangaId = target.id,
                            )
                        }
                        // The migration has already completed at this point. The optional quality
                        // signal is deliberately best-effort and runs only after the truthful event,
                        // receipt, and Done state have been committed; cancellation or a storage
                        // failure here must not erase visibility of an irreversible completed action.
                        saveQualitySignal(origin, target)
                    }
                    is mihon.domain.migration.usecases.MigrationOutcome.PartialFailure -> {
                        mutableState.update {
                            it.copy(
                                step = BestVersionStep.Error(
                                    BestVersionErrorReason.Recommendation(
                                        RecommendationErrorClassifier.classify(outcome.cause),
                                    ),
                                ),
                                isMigrating = false,
                            )
                        }
                    }
                    is mihon.domain.migration.usecases.MigrationOutcome.NotStarted -> {
                        mutableState.update {
                            it.copy(
                                step = BestVersionStep.Error(
                                    BestVersionErrorReason.Recommendation(
                                        outcome.cause?.let(RecommendationErrorClassifier::classify)
                                            ?: RecommendationErrorKind.Internal,
                                    ),
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
                        // KMK v0.8.20-fix5: typed classification, never raw exception text.
                        step = BestVersionStep.Error(
                            BestVersionErrorReason.Recommendation(RecommendationErrorClassifier.classify(e)),
                        ),
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
        try {
            upsertQualitySignal.insert(signal)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
        }
    }

    private fun resolveSettings(): SameMangaMatchSettings {
        return SameMangaMatchSettings(
            resultsPerSource = SameMangaMatchSettings.clampResultCap(
                sourcePreferences.sameMangaMatchResultsPerSource().get(),
            ),
            preselectResults = SameMangaPreselectionMode.resolve(
                sourcePreferences.sameMangaMatchPreselectionMode().get(),
                sourcePreferences.sameMangaMatchPreselectResults().get(),
            ) != SameMangaPreselectionMode.NONE,
            previewSampleSize = SameMangaMatchSettings.clampSampleSize(
                sourcePreferences.bestVersionPreviewSampleSize().get(),
            ),
            avoidFirstPages = sourcePreferences.bestVersionAvoidFirstPages().get(),
            preselectionMode = SameMangaPreselectionMode.resolve(
                sourcePreferences.sameMangaMatchPreselectionMode().get(),
                sourcePreferences.sameMangaMatchPreselectResults().get(),
            ),
        )
    }
}
// KMK <--

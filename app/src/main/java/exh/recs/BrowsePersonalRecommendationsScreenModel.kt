package exh.recs

// KMK -->
import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceRuntime
import eu.kanade.tachiyomi.source.SourceRuntimeOperation
import eu.kanade.tachiyomi.source.getOrThrowSourceRuntimeException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.rethrowIfFatal
import eu.kanade.tachiyomi.util.export.SafArtifactOutcome
import eu.kanade.tachiyomi.util.export.SafExportCoordinator
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.isOnline
import eu.kanade.tachiyomi.util.system.toast
import exh.recs.RecommendationCandidateEnricher.Companion.needsEnrichment
import exh.recs.matching.ConfirmedGroupLocalTrackingPropagator
import exh.recs.matching.ConfirmedMangaGroupTargets
import exh.recs.memory.RecommendationCandidateMemoryEntry
import exh.recs.memory.RecommendationCandidateMemoryRanker
import exh.recs.memory.RecommendationCandidateMemoryStore
import exh.recs.memory.RecommendationDiscoveryPlanner
import exh.recs.memory.RecommendationDiscoveryProgressStore
import exh.recs.memory.RecommendationRetryClassifier
import exh.recs.settings.RecommendationForYouPreviewManga
import exh.recs.settings.RecommendationForYouPreviewRow
import exh.recs.settings.RecommendationForYouPreviewRowType
import exh.recs.settings.RecommendationForYouPreviewSnapshot
import exh.recs.settings.RecommendationForYouPreviewSnapshotStore
import exh.recs.share.RecommendationBundleExporter
import exh.recs.sources.GenreFilterMapper
import exh.util.EvaluationModeFormatter
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.taste.interactor.ClearRecommendationCache
import tachiyomi.domain.taste.interactor.GetChapterCountsByMangaIds
import tachiyomi.domain.taste.interactor.GetDisabledRecommendationSources
import tachiyomi.domain.taste.interactor.GetKnownRecommendationMangaIds
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.interactor.GetRecommendationCache
import tachiyomi.domain.taste.interactor.GetRecommendationCandidateMemory
import tachiyomi.domain.taste.interactor.GetRecommendationDiscoveryProgress
import tachiyomi.domain.taste.interactor.GetTagAliases
import tachiyomi.domain.taste.interactor.GetTasteProfile
import tachiyomi.domain.taste.interactor.PruneRecommendationCandidateMemory
import tachiyomi.domain.taste.interactor.UpsertRecommendationCache
import tachiyomi.domain.taste.interactor.UpsertRecommendationCandidateMemory
import tachiyomi.domain.taste.interactor.UpsertRecommendationDiscoveryProgress
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaTaste
import tachiyomi.domain.taste.model.RatedMangaVisibility
import tachiyomi.domain.taste.model.RecommendationCacheEntry
import tachiyomi.domain.taste.model.RecommendationDiscoveryProgress
import tachiyomi.domain.taste.model.TasteProfile
import tachiyomi.domain.taste.model.normalizeTag
import tachiyomi.domain.tracker.repository.LocalTrackerRepository
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

data class PersonalRecommendation(
    val manga: Manga,
    val score: Double,
    val matchedGroups: List<String>,
    /**
     * Which discovery lane produced this candidate. Carried all the way through the merge so the
     * personalized-majority invariant can be asserted on the **final displayed list**, not merely on
     * the input quota arithmetic. Defaults to [RecommendationDiscoveryLane.PERSONALIZED] so every
     * pre-existing construction site keeps its exact previous meaning.
     */
    val lane: RecommendationDiscoveryLane = RecommendationDiscoveryLane.PERSONALIZED,
    // KMK <--
)

sealed interface PersonalRecommendationResult {
    data object Loading : PersonalRecommendationResult
    data class Error(val throwable: Throwable) : PersonalRecommendationResult
    data class Success(val result: List<PersonalRecommendation>) : PersonalRecommendationResult {
        val isEmpty: Boolean get() = result.isEmpty()
        val reason: String? get() = result
            .flatMap { it.matchedGroups }
            .distinct()
            .take(4)
            .joinToString(", ")
            .ifBlank { null }
    }
}

/** Returns the source rows that currently expose the inline Retry action. */
internal fun retryableRecommendationSourceIds(
    items: Map<Long, PersonalRecommendationResult>,
): Set<Long> = items.asSequence()
    .filter { (_, result) -> result is PersonalRecommendationResult.Error }
    .map { (sourceId, _) -> sourceId }
    .toSet()

internal fun <T> selectRecommendationCandidates(
    sources: List<T>,
    retrySourceIds: Set<Long>?,
    maxAttempts: Int,
    sourceId: (T) -> Long,
): List<T> = if (retrySourceIds == null) {
    sources.take(maxAttempts)
} else {
    sources
        .filter { sourceId(it) in retrySourceIds }
        .distinctBy(sourceId)
}

data class RecommendationSearchContext(
    val textQuery: String,
    val topTags: List<String>,
)

/** Stable identity for a source manga — survives local manga_id changes across devices/paths. */
internal data class MangaTasteKey(val source: Long, val url: String)

/**
 * Returns true if the manga should be hidden from For You given the current visibility setting.
 * Favorite filtering is kept separate and handled at the call site.
 */
internal fun shouldHideForYou(
    manga: Manga,
    tasteByKey: Map<MangaTasteKey, MangaTaste>,
    visibility: RatedMangaVisibility,
): Boolean {
    val taste = tasteByKey[MangaTasteKey(manga.source, manga.url)] ?: return false
    return when (visibility) {
        RatedMangaVisibility.HIDE_ALL_RATED -> true
        RatedMangaVisibility.HIDE_DISLIKED_ONLY -> taste.rating == MangaRating.DISLIKE.value
        RatedMangaVisibility.SHOW_ALL_RATED -> false
    }
}

// KMK --> v0.7.41 follow-up: shared pure filter seam for live page-one and extra-page discovery
/**
 * Filters [candidates] down to those [CandidateVisibility.VISIBLE] under the shared
 * [RecommendationCandidateVisibilityPolicy]. Used by both the live page-one path and the extra-page
 * discovery path so a known/rated/seen/favorited/below-min-chapter candidate is excluded before
 * scoring, progress recording, or candidate-memory storage — not only at final merge time.
 */
internal fun filterVisibleCandidates(
    candidates: List<Manga>,
    tasteByKey: Map<MangaTasteKey, MangaTaste>,
    visibility: RatedMangaVisibility,
    seenKeys: Set<SeenMangaKey>,
    knownIds: Set<Long>,
    minChapterCount: Int,
    chapterCounts: Map<Long, Long>,
): List<Manga> = candidates.filter { manga ->
    RecommendationCandidateVisibilityPolicy.evaluate(
        manga = manga,
        tasteByKey = tasteByKey,
        visibility = visibility,
        seenKeys = seenKeys,
        knownIds = knownIds,
        minChapterCount = minChapterCount,
        chapterCounts = chapterCounts,
    ) == CandidateVisibility.VISIBLE
}

/** Reconciles retry-created Source instances without duplicating a source-id in the UI order. */
internal fun reconcileSourceOrder(
    current: PersistentList<Source>,
    incoming: List<Source>,
): PersistentList<Source> {
    val replacements = incoming.associateBy { it.id }
    val seen = mutableSetOf<Long>()
    val result = buildList {
        current.forEach { source ->
            val replacement = replacements[source.id] ?: source
            if (seen.add(replacement.id)) add(replacement)
        }
        incoming.forEach { source ->
            if (seen.add(source.id)) add(source)
        }
    }
    return result.toPersistentList()
}
// KMK <--

private fun normalizedTitleKey(title: String): String =
    title.lowercase(Locale.ROOT)
        .replace(Regex("""\([^)]*\)|\[[^\]]*\]"""), "")
        .replace(Regex("[^a-z0-9]"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

class BrowsePersonalRecommendationsScreenModel(
    // KMK --> v0.7.25: connectivity check before starting For You queries
    private val context: Context = Injekt.get<Application>(),
    private val isOnline: () -> Boolean = { context.isOnline() },
    private val clock: () -> Long = System::currentTimeMillis,
    private val searchDispatcher: kotlinx.coroutines.CoroutineDispatcher =
        kotlinx.coroutines.Dispatchers.IO.limitedParallelism(5),
    private val isLowRamDevice: Boolean = DeviceUtil.isLowRamDevice(context),
    // Tests can construct the real
    // ScreenModel without starting the Android/network load before collaborators are ready.
    private val autoLoad: Boolean = true,
    // Keep debug-only fixture eligibility injectable for host tests that run under a non-debug
    // variant. Production callers retain the real build flag by default.
    private val isDebugBuild: Boolean = BuildConfig.DEBUG,
    // KMK <--
    private val getTasteProfile: GetTasteProfile = Injekt.get(),
    private val getTagAliases: GetTagAliases = Injekt.get(),
    private val getDisabledSources: GetDisabledRecommendationSources = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val getMangaInteractor: GetManga = Injekt.get(),
    private val getMangaTaste: GetMangaTaste = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val getRecommendationCache: GetRecommendationCache = Injekt.get(),
    private val upsertRecommendationCache: UpsertRecommendationCache = Injekt.get(),
    private val clearRecommendationCache: ClearRecommendationCache = Injekt.get(),
    private val getKnownMangaIds: GetKnownRecommendationMangaIds = Injekt.get(),
    // KMK --> v0.7.26: chapter count lookup for minimum-chapter filter
    private val getChapterCounts: GetChapterCountsByMangaIds = Injekt.get(),
    // KMK <--
    // KMK --> v0.7.38: For You candidate discovery memory
    private val getMemory: GetRecommendationCandidateMemory = Injekt.get(),
    private val upsertMemory: UpsertRecommendationCandidateMemory = Injekt.get(),
    private val pruneMemory: PruneRecommendationCandidateMemory = Injekt.get(),
    // KMK <--
    // KMK --> v0.7.39: For You rolling discovery progress
    private val getDiscoveryProgress: GetRecommendationDiscoveryProgress = Injekt.get(),
    private val upsertDiscoveryProgress: UpsertRecommendationDiscoveryProgress = Injekt.get(),
    // KMK <--
    // KMK --> v0.8.16: For You long-press selection bulk actions
    private val setMangaTasteBatch: tachiyomi.domain.taste.interactor.SetMangaTasteBatch = Injekt.get(),
    // KMK v0.8.17-fix1: Clear Rating for For You selection, same interactor manga detail already uses.
    private val clearMangaTaste: tachiyomi.domain.taste.interactor.ClearMangaTaste = Injekt.get(),
    private val localTrackerRepository: LocalTrackerRepository = Injekt.get(),
    private val confirmedMangaGroupTargets: ConfirmedMangaGroupTargets = Injekt.get(),
    private val confirmedGroupLocalTrackingPropagator: ConfirmedGroupLocalTrackingPropagator =
        ConfirmedGroupLocalTrackingPropagator(localTrackerRepository),
    // KMK <--
    // batched tracker lookup so a tracked title is
    // genuinely exempt from the exposure penalty. Fails open (see resolveTrackedExposureKeys).
    private val getTracks: tachiyomi.domain.track.interactor.GetTracks = Injekt.get(),
    // Local-only For You exposure
    // history (soft display reordering only -- see RecommendationExposureRepository KDoc)
    private val getRecommendationExposure: tachiyomi.domain.taste.interactor.GetRecommendationExposure = Injekt.get(),
    private val recordRecommendationExposure: tachiyomi.domain.taste.interactor.RecordRecommendationExposure = Injekt.get(),
    private val pruneRecommendationExposure: tachiyomi.domain.taste.interactor.PruneRecommendationExposure = Injekt.get(),
) : StateScreenModel<BrowsePersonalRecommendationsScreenModel.State>(State()) {
    // Keep all source fetches in the refresh under one device-aware aggregate limit. The caller's
    // dispatcher remains injectable for host tests; only its effective parallelism changes.
    private val effectiveSearchDispatcher = searchDispatcher.limitedParallelism(
        RecommendationEffectiveResourcePolicy.sourceConcurrency(isLowRamDevice),
    )

    // All downstream source work, including SourceRuntime and enrichment, must share the effective
    // device-aware limit. Passing the raw constructor dispatcher here would bypass the low-RAM cap
    // whenever a nested owner switches context for its own I/O.
    private val coroutineDispatcher = effectiveSearchDispatcher

    // screenModelScope-owned, not Composable-`remember`-owned.
    val exportCoordinator = SafExportCoordinator()

    // [snapshot],
    // [targetSource], and [isTopPicks] are all captured by the caller (personalRecommendationsTab) at
    // the moment the export is requested, before the picker opens.
    fun exportRecommendationBundle(
        context: Context,
        operationId: String,
        uri: android.net.Uri,
        snapshot: State?,
        targetSource: Source?,
        isTopPicks: Boolean,
    ): Boolean {
        if (!exportCoordinator.registerUri(operationId, uri)) return false
        screenModelScope.launch {
            val outcome = exportCoordinator.performWrite(operationId) {
                if (snapshot == null) {
                    SafArtifactOutcome.PARTIAL_OR_EMPTY
                } else {
                    val exporter = RecommendationBundleExporter()
                    val bundle = when {
                        isTopPicks -> {
                            val detail = snapshot.combinedDetailResult as? PersonalRecommendationResult.Success
                            if (detail == null || detail.result.isEmpty()) {
                                null
                            } else {
                                exporter.buildTopPicksBundle(detail.result, KmkRecsReleaseNotes.VERSION_NAME)
                            }
                        }
                        targetSource != null -> {
                            val result = snapshot.items[targetSource] as? PersonalRecommendationResult.Success
                            if (result == null || result.result.isEmpty()) {
                                null
                            } else {
                                exporter.buildSourceRowBundle(
                                    targetSource.name,
                                    targetSource.lang,
                                    result.result,
                                    KmkRecsReleaseNotes.VERSION_NAME,
                                )
                            }
                        }
                        else -> null
                    }
                    if (bundle == null) {
                        SafArtifactOutcome.PARTIAL_OR_EMPTY
                    } else {
                        exporter.writeToUri(context, uri, bundle).fold(
                            onSuccess = { SafArtifactOutcome.SUCCESS },
                            onFailure = { SafArtifactOutcome.FAILED },
                        )
                    }
                }
            }
            withUIContext {
                when (outcome) {
                    SafArtifactOutcome.SUCCESS -> context.toast(KMR.strings.rec_bundle_export_success)
                    SafArtifactOutcome.FAILED -> context.toast(KMR.strings.rec_bundle_export_failure)
                    SafArtifactOutcome.PARTIAL_OR_EMPTY -> context.toast(KMR.strings.rec_bundle_export_empty)
                    SafArtifactOutcome.CANCELLED -> Unit
                    SafArtifactOutcome.IN_PROGRESS -> Unit
                    SafArtifactOutcome.UNRESOLVED -> Unit
                }
            }
        }
        return true
    }

    private var searchJob: Job? = null
    private var loadJob: Job? = null
    private val loadLaunchLock = Any()
    private val enricher = RecommendationCandidateEnricher(networkToLocalManga, coroutineDispatcher)
    // KMK --> v0.7.38: candidate discovery memory helpers
    private val memoryStore = RecommendationCandidateMemoryStore(getMemory, upsertMemory, pruneMemory)
    // KMK <--
    // KMK --> v0.7.39: rolling discovery progress helpers
    private val progressStore = RecommendationDiscoveryProgressStore(getDiscoveryProgress, upsertDiscoveryProgress)
    // KMK <--

    // Top Picks accumulator — guarded by accumulatorLock for concurrent source updates
    private val accumulatorLock = Any()
    private val combinedAccumulator = CombinedPicksAccumulator()

    /**
     * Rejects late results from a superseded refresh before they can mutate the shared accumulator
     * or the rendered source map. Cancellation is cooperative, so an HTTP/enrichment completion can
     * still reach [updateItem] after a newer refresh has started.
     */
    private val activeRefreshGeneration = java.util.concurrent.atomic.AtomicLong(-1L)

    @Volatile private var currentBoostedSourceIds: Set<Long> = emptySet()

    /** Refresh-owned budget state; never shared by overlapping or superseded refreshes. */
    private data class LatestExplorationBudgetState(
        val limit: Int,
        val attemptsUsed: java.util.concurrent.atomic.AtomicInteger =
            java.util.concurrent.atomic.AtomicInteger(0),
    )
    // KMK <--

    /**
     * One wall-clock reading per refresh, used by every exposure-reranking comparison this refresh
     * makes -- deterministic ordering requires a single `now`, never a fresh read per source/card.
     */
    @Volatile private var currentRefreshTimestamp: Long = 0L

    /** Guards [recordVisibleExposure] against firing twice for the same [State.resultGeneration]. */
    @Volatile private var lastExposureRecordedGeneration: Long = -1L
    // KMK <--

    /** Outcome returned by [searchSource] — carries result, status, and strategy for the caller. */
    private data class SourceSearchOutcome(
        val source: Source,
        val result: PersonalRecommendationResult,
        val successfulStrategy: RecommendationQueryStrategyType?,
        val status: RecommendationSourceRunStatus,
        val isUseful: Boolean,
    )

    init {
        if (autoLoad) {
            requestLoad(forceRefresh = false)
        }
    }

    // KMK v0.8.21-fix2: AUG-14 slice 3 UI -- saved For You focus modes. Deliberately UI-adjacent
    // state (a plain preference), not part of the main [State]: reactive to the preference's own
    // changes() so a save/rename/delete from anywhere updates every observer immediately, exactly
    // like every other Preference-backed reactive value in this codebase.
    val savedFocusModes: kotlinx.coroutines.flow.StateFlow<List<SavedFocusMode>> =
        sourcePreferences.savedFocusModes().changes()
            .map { SavedFocusModeStore.parse(it) }
            .stateIn(
                screenModelScope,
                SharingStarted.Eagerly,
                SavedFocusModeStore.parse(sourcePreferences.savedFocusModes().get()),
            )

    /** Applied focus survives screen/process recreation without becoming taste or saved-mode data. */
    internal val activeFocusCriteria: kotlinx.coroutines.flow.StateFlow<RecommendationFocusPolicy.FocusCriteria> =
        sourcePreferences.activeForYouFocus().changes()
            .map(ActiveFocusStore::parse)
            .stateIn(
                screenModelScope,
                SharingStarted.Eagerly,
                ActiveFocusStore.parse(sourcePreferences.activeForYouFocus().get()),
            )

    internal fun setActiveFocus(criteria: RecommendationFocusPolicy.FocusCriteria) {
        sourcePreferences.activeForYouFocus().set(ActiveFocusStore.serialize(criteria))
        // Applying or clearing focus is a new discovery request, not only a presentation filter.
        // Reuse the existing refresh owner so every eligible source gets a fresh bounded search.
        refresh()
    }

    /** No-ops on a blank [name], per [SavedFocusModeStore.create]'s own contract. */
    fun saveFocusMode(name: String, includeGroups: Set<String>, excludeGroups: Set<String> = emptySet(), matchAll: Boolean = true) {
        val updated = SavedFocusModeStore.create(
            current = savedFocusModes.value,
            name = name,
            includeGroups = includeGroups,
            excludeGroups = excludeGroups,
            matchAll = matchAll,
            now = clock(),
            newId = { UUID.randomUUID().toString() },
        )
        sourcePreferences.savedFocusModes().set(SavedFocusModeStore.serialize(updated))
    }

    // KMK v0.8.21-fix4: R4/AUG-14 completion -- rename/edit/delete/reorder were already fully
    // implemented and tested at the SavedFocusModeStore level (slice 3); only the screen-model
    // and UI wiring were the deferred remainder. Each simply delegates to the store's pure
    // function and writes the result back through the same reactive preference every other
    // mutation here already uses -- no new persistence mechanism.
    /** No-ops on a blank [newName], per [SavedFocusModeStore.rename]'s own contract. */
    fun renameFocusMode(id: String, newName: String) {
        val updated = SavedFocusModeStore.rename(savedFocusModes.value, id, newName, now = clock())
        sourcePreferences.savedFocusModes().set(SavedFocusModeStore.serialize(updated))
    }

    fun updateFocusModeCriteria(id: String, includeGroups: Set<String>, excludeGroups: Set<String>, matchAll: Boolean) {
        val updated = SavedFocusModeStore.updateCriteria(savedFocusModes.value, id, includeGroups, excludeGroups, matchAll, now = clock())
        sourcePreferences.savedFocusModes().set(SavedFocusModeStore.serialize(updated))
    }

    fun deleteFocusMode(id: String) {
        val updated = SavedFocusModeStore.delete(savedFocusModes.value, id)
        sourcePreferences.savedFocusModes().set(SavedFocusModeStore.serialize(updated))
    }

    fun reorderFocusModes(orderedIds: List<String>) {
        val updated = SavedFocusModeStore.reorder(savedFocusModes.value, orderedIds)
        sourcePreferences.savedFocusModes().set(SavedFocusModeStore.serialize(updated))
    }

    fun refresh() {
        requestLoad(forceRefresh = true)
    }

    /**
     * Re-runs only the source rows currently showing an error. A full refresh remains the fallback
     * for offline/empty states and the pull-to-refresh gesture, while an inline Retry action must
     * not discard successful source results or issue duplicate requests for them.
     */
    fun retryFailedSourcesOrRefresh() {
        val failedSourceIds = retryableRecommendationSourceIds(
            mutableState.value.items.mapKeys { (source, _) -> source.id },
        )
        requestLoad(
            forceRefresh = true,
            retrySourceIds = failedSourceIds.takeIf { it.isNotEmpty() },
        )
    }

    /** Serializes refresh requests so a superseded load cannot cancel a newer search job. */
    private fun requestLoad(forceRefresh: Boolean, retrySourceIds: Set<Long>? = null) {
        synchronized(loadLaunchLock) {
            loadJob?.cancel()
            searchJob?.cancel()
            // Reserve the generation before launching work. A cancelled older load can otherwise
            // reach its late state reset after this request and steal the active-generation slot.
            val refreshGeneration = mutableState.updateAndGet {
                it.copy(resultGeneration = it.resultGeneration + 1)
            }.resultGeneration
            activeRefreshGeneration.updateAndGet { currentGeneration ->
                RecommendationRefreshGenerationPolicy.activate(currentGeneration, refreshGeneration)
            }
            loadJob = screenModelScope.launch {
                load(
                    forceRefresh = forceRefresh,
                    retrySourceIds = retrySourceIds,
                    refreshGeneration = refreshGeneration,
                )
            }
        }
    }

    /**
     * Records that the current [State.resultGeneration]'s cards are actually visible on screen.
     *
     * Called from the Tab's own `LaunchedEffect`, keyed on `(resultGeneration, allSourcesFinished)`
     * -- i.e. only once composition has actually rendered a loaded, non-empty, fully-settled result
     * for this generation, never for loading/empty/error/partial states, and never merely because
     * Compose recomposed. [lastExposureRecordedGeneration] is a second, defense-in-depth guard inside
     * the ScreenModel itself against the same generation being recorded twice even if the Tab's key
     * were ever wrong.
     *
     * A stale call (an old generation's coroutine still in flight when a new refresh starts) is not
     * possible here because the Tab cancels and restarts its effect on generation change; this
     * function is fire-and-forget from the Tab's perspective and launches its own
     * [screenModelScope]-owned write, so a slow write can never block composition.
     */
    fun recordVisibleExposure() {
        val state = mutableState.value
        val generation = state.resultGeneration
        val shouldRecord = RecommendationExposureCapturePolicy.shouldRecord(
            isLoading = state.isLoading,
            hasItems = state.items.isNotEmpty(),
            total = state.total,
            progress = state.progress,
            generation = generation,
            lastRecordedGeneration = lastExposureRecordedGeneration,
        )
        if (!shouldRecord) return
        lastExposureRecordedGeneration = generation

        val dedupedMap = state.dedupedItems()
        val keys = mutableListOf<Pair<Long, String>>()
        val mangaIds = mutableMapOf<Pair<Long, String>, Long?>()
        dedupedMap.forEach { (source, result) ->
            if (result is PersonalRecommendationResult.Success) {
                result.result.forEach { rec ->
                    val key = source.id to rec.manga.url
                    keys += key
                    mangaIds[key] = rec.manga.id.takeIf { it != 0L }
                }
            }
        }
        if (keys.isEmpty()) return

        screenModelScope.launch {
            try {
                recordRecommendationExposure.await(keys, mangaIds, clock())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failed exposure write must never change or block the visible result -- it has
                // already been shown by the time this runs.
            }
        }
    }
    // KMK <--

    private suspend fun load(
        forceRefresh: Boolean,
        retrySourceIds: Set<Long>? = null,
        refreshGeneration: Long,
    ) {
        if (activeRefreshGeneration.get() != refreshGeneration) return
        val retryOnly = retrySourceIds?.takeIf { it.isNotEmpty() }
        val fixtureMode = ForYouDebugFixture.resolveMode(
            isDebugBuild = isDebugBuild || BuildConfig.KMK_BENCHMARK_FIXTURE,
            preferenceValue = sourcePreferences.forYouFixtureMode().get(),
        )
        // KMK --> v0.7.25: reset offline state when starting a new load
        mutableState.update {
            if (it.resultGeneration != refreshGeneration) return@update it
            it.copy(
                isLoading = retryOnly == null,
                profileIsEmpty = false,
                isOffline = false,
            )
        }

        if (!ForYouDebugFixture.usesSyntheticInputs(fixtureMode) && !isOnline()) {
            mutableState.update {
                if (it.resultGeneration != refreshGeneration) {
                    it
                } else {
                    State(isLoading = false, isOffline = true, resultGeneration = refreshGeneration)
                }
            }
            return
        }
        // KMK <--

        clearRecommendationCache.awaitExpired()
        // Prune exposure rows older
        // than the configured window plus its grace period on every refresh -- ordering history only,
        // never touches ratings/library/manga/taste. A failed prune must never block the refresh.
        try {
            val windowDays = RecommendationExposurePolicy.validateWindowDays(
                sourcePreferences.recommendationExposureWindowDays().get(),
            )
            pruneRecommendationExposure.await(RecommendationExposurePolicy.pruneBefore(clock(), windowDays))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
        }

        val profile = ForYouDebugFixture.profile(fixtureMode) { getTasteProfile.await() }
        if (profile.isEmpty()) {
            if (retryOnly == null) {
                mutableState.update {
                    if (it.resultGeneration != refreshGeneration) {
                        it
                    } else {
                        it.copy(isLoading = false, profileIsEmpty = true)
                    }
                }
            } else {
                mutableState.update {
                    if (it.resultGeneration != refreshGeneration) it else it.copy(isLoading = false)
                }
            }
            return
        }

        val aliasMap = getTagAliases.awaitAliasMap()
        val groupToAliases = getTagAliases.awaitGroupToAliasesMap()
        val disabledSourceIds = getDisabledSources.await().toSet()
        // KMK -->
        val dislikedRaw = sourcePreferences.dislikedRecommendationSourceKeys().get()
        val dislikedInstalledIds = exh.recs.sourceprefs.RecommendationSourcePreferenceStore
            .installedSourceIds(exh.recs.sourceprefs.RecommendationSourcePreferenceStore.parse(dislikedRaw))
        // KMK v0.8.1-fix4: source/library-quality dislikes also exclude installed sources from For You —
        // a source marked poor/too-explicit as a whole should not keep feeding recommendation rows.
        val qualityDislikedRaw = sourcePreferences.dislikedSourceQualityKeys().get()
        val qualityDislikedInstalledIds = exh.recs.sourceprefs.RecommendationSourcePreferenceStore
            .installedSourceIds(exh.recs.sourceprefs.RecommendationSourcePreferenceStore.parse(qualityDislikedRaw))
        val effectiveDisabledIds = disabledSourceIds + dislikedInstalledIds + qualityDislikedInstalledIds
        // KMK <--

        val topTags = topSearchTags(profile)
        if (topTags.isEmpty()) {
            if (retryOnly == null) {
                mutableState.update {
                    if (it.resultGeneration != refreshGeneration) {
                        it
                    } else {
                        it.copy(isLoading = false, profileIsEmpty = true)
                    }
                }
            } else {
                mutableState.update {
                    if (it.resultGeneration != refreshGeneration) it else it.copy(isLoading = false)
                }
            }
            return
        }

        val aliasCandidates = buildAliasCandidates(topTags, groupToAliases)
        // Reuse the same aliasMap/groupToAliases
        // already fetched above (no second GetTagAliases call) to build the Focus feature's own
        // alias map + known-group universe -- see buildFocusAliasMap/buildFocusKnownGroups.
        val focusAliasMap = buildFocusAliasMap(aliasMap)
        // KMK C3 (HR-2026-08-26-FOCUS-CRITERION-CATALOG-COMPLETENESS, E4.3): third union input --
        // genre-like labels extracted from eligible sources' own FilterList, accumulated across this
        // process's lifetime (not just this load) via SourceGenreCatalogCache. See its own doc for
        // why this makes the catalog a real source-filter-style contract instead of only loaded-
        // result genres plus the alias/synonym systems.
        val focusKnownGroups = buildFocusKnownGroups(
            groupToAliases,
            exh.recs.sources.SourceGenreCatalogCache.snapshot(),
            focusAliasMap,
        )

        val recommendationLanguages = sourcePreferences.recommendationSourceLanguages().get()
        val storedOrder = RecommendationSourceOrdering.parse(sourcePreferences.recommendationSourceOrder().get())
        // KMK --> v0.7.40: use shared selector (language filter + ordering + disabled exclusion)
        val orderedEnabledSources = RecommendationSourceSelector.select(
            sources = ForYouDebugFixture.sources(fixtureMode, sourceManager.getVisibleSources()),
            languages = if (ForYouDebugFixture.usesSyntheticInputs(fixtureMode)) setOf("en") else recommendationLanguages,
            storedOrder = if (ForYouDebugFixture.usesSyntheticInputs(fixtureMode)) {
                ForYouDebugFixture.sourceIds(fixtureMode).toList()
            } else {
                storedOrder
            },
            effectiveDisabledIds = if (ForYouDebugFixture.usesSyntheticInputs(fixtureMode)) emptySet() else effectiveDisabledIds,
        )
        // KMK <--

        // Boosted sources are fixed: always the top BOOSTED_SOURCE_COUNT eligible sources
        val boostedSourceIds = orderedEnabledSources.take(BOOSTED_SOURCE_COUNT).map { it.id }.toSet()

        val retryCombined: Pair<PersonalRecommendationResult?, PersonalRecommendationResult?>?
        synchronized(accumulatorLock) {
            combinedAccumulator.clear()
            currentBoostedSourceIds = boostedSourceIds
            if (retryOnly != null) {
                mutableState.value.items.forEach { (source, result) ->
                    if (source.id !in retryOnly && result is PersonalRecommendationResult.Success && result.result.isNotEmpty()) {
                        combinedAccumulator.add(result.result, source.id)
                    }
                }
            }
            val rowRanked = combinedAccumulator.rank(boostedSourceIds, TOP_PICKS_ROW_CAP)
            val detailRanked = combinedAccumulator.rank(boostedSourceIds, TOP_PICKS_DETAIL_CAP)
            retryCombined = if (retryOnly == null) {
                null
            } else {
                (rowRanked.takeIf { it.isNotEmpty() }?.let { PersonalRecommendationResult.Success(it) }) to
                    (detailRanked.takeIf { it.isNotEmpty() }?.let { PersonalRecommendationResult.Success(it) })
            }
        }

        val strategyMap = RecommendationQueryPlanner.parseStrategies(
            sourcePreferences.recommendationSourceStrategies().get(),
        ).toMutableMap()
        // KMK v0.8.13: last-run statuses, used by RecommendationStrategyRecoveryPolicy to decide
        // whether a persisted strategy hint is still trustworthy for this refresh.
        val lastStatusMap = RecommendationSourceRunStatusStore.parse(
            sourcePreferences.recommendationLastSourceRunStatuses().get(),
        )

        val hideKnownManga = sourcePreferences.recommendationHideKnownManga().get()
        // KMK v0.8.21-fix3: R1 correction -- Not Interested exclusion now reads MangaTaste (the
        // single rating-family source of truth) instead of the legacy seenRecommendationMangaKeys
        // preference. seenKeys keeps its existing Set<SeenMangaKey> shape (a plain (source, url)
        // value tuple, not tied to the preference's own persistence) so every downstream consumer
        // of this variable is unaffected -- only where it's computed FROM changes.
        val allTastes = getMangaTaste.awaitAll()
        val seenKeys = allTastes
            .asSequence()
            .filter { it.rating == tachiyomi.domain.taste.model.MangaRating.NOT_INTERESTED.value }
            .map { SeenMangaKey(it.source, it.url) }
            .toSet()
        val seenMangaCount = seenKeys.size
        // KMK --> v0.7.43: Not Interested (internal storage still "seen") becomes a mild negative
        // signal — similar candidates are slightly deprioritized, much weaker than Dislike. Query
        // tag selection above (topTags) intentionally still uses the raw, unadjusted profile; only
        // scoring/ranking uses scoringProfile.
        // Synthetic debug fixtures must remain deterministic and independent of the preserved
        // user's real Not Interested history. Production refreshes retain the live adjustment;
        // otherwise a user's accumulated genre penalties can make the fixture's intentionally
        // positive candidates disappear before the mixed-result device gate is observable.
        val scoringProfile = if (ForYouDebugFixture.usesSyntheticInputs(fixtureMode)) {
            profile
        } else {
            buildNotInterestedAdjustedProfile(profile, seenKeys, aliasMap)
        }
        // KMK <--
        // KMK --> v0.7.26: minimum locally-known chapter count filter (0 = off)
        // resolved through the shared
        // supported-value policy before it reaches searchSource(), the visibility policy, the
        // memory merge, and profileFingerprint() below -- so every consumer of this refresh uses
        // one identical threshold and an unsupported persisted value cannot silently filter at a
        // threshold the picker never offered.
        val minChapterCount = RecommendationMinChapterCountPolicy.resolve(
            sourcePreferences.recommendationMinChapterCount().get(),
        )
        // KMK <--
        // KMK v0.8.2: visible-card budget per row; raw value, validated by ForYouResultBudgetPolicy
        val resultBudget = sourcePreferences.recommendationResultBudget().get()
        // KMK <--
        val fingerprint = profileFingerprint(topTags, profile, aliasMap, effectiveDisabledIds, storedOrder, recommendationLanguages, hideKnownManga, seenMangaCount, minChapterCount, resultBudget)
        val queryKey = topTags.sorted().joinToString(",")

        val tasteByKey = allTastes.associate { MangaTasteKey(it.source, it.url) to it }
        val visibility = sourcePreferences.recommendationRatedMangaVisibility().get()
        val fallbackQuery = topTags.take(3).joinToString(" ")

        // Reset UI state — sources will be added in batches
        //
        // KMK F2-03 atomicity correction (2026-08-27): an independent review correctly found that
        // capturing refreshGeneration via a SEPARATE `mutableState.value.resultGeneration` read
        // AFTER this update() call was not atomic with the increment itself -- two overlapping
        // refresh() invocations could both finish their own update() (each correctly incrementing
        // resultGeneration by exactly 1 from whatever it currently was) and then both read the SAME
        // final `.value` afterward if a second update() from the other invocation landed between the
        // first invocation's update() and its OWN subsequent `.value` read. That would hand both
        // refreshes the identical generation number, defeating the entire point of a per-refresh
        // generation ("capture a unique immutable generation for each load invocation"). `update {}`'s
        // own increment (`it.resultGeneration + 1`) is internally atomic (a compareAndSet retry loop),
        // but the OLD code's separate `.value` read after it was not part of that same atomic step.
        // `updateAndGet {}` returns the POST-update state from the SAME atomic operation that performed
        // the update, so the generation captured below is guaranteed to be the exact one THIS
        // invocation's update produced, even under real concurrent overlapping refresh() calls.
        mutableState.update {
            if (it.resultGeneration != refreshGeneration) return@update it
            if (retryOnly == null) {
                it.copy(
                    items = persistentMapOf(),
                    sourceOrder = persistentListOf(),
                    combinedResult = null,
                    combinedDetailResult = null,
                    sourceStatuses = persistentMapOf(),
                    searchContexts = persistentMapOf(),
                    // Keep the full-refresh loading state active until the asynchronous source
                    // batches below have reached their terminal boundary. Clearing it here lets
                    // the renderer present an empty/non-loading surface while the first batch is
                    // still in flight.
                    isLoading = true,
                    profileIsEmpty = false,
                    // A new generation invalidates the Tab's exposure-recording effect for the
                    // previous refresh.
                    resultGeneration = refreshGeneration,
                    focusAliasMap = focusAliasMap,
                    focusKnownGroups = focusKnownGroups,
                )
            } else {
                it.copy(
                    items = it.items.mutate { map ->
                        map.entries
                            .filter { (source, result) -> source.id in retryOnly && result is PersonalRecommendationResult.Error }
                            .forEach { (source, _) -> map[source] = PersonalRecommendationResult.Loading }
                    },
                    combinedResult = retryCombined?.first,
                    combinedDetailResult = retryCombined?.second,
                    sourceStatuses = it.sourceStatuses.mutate { map -> retryOnly.forEach(map::remove) },
                    isLoading = false,
                    profileIsEmpty = false,
                    resultGeneration = refreshGeneration,
                    focusAliasMap = focusAliasMap,
                    focusKnownGroups = focusKnownGroups,
                )
            }
        }
        // KMK F2-03 corrective slice B: reuses the request-reserved resultGeneration (rather than
        // inventing a second, parallel counter) as SourceGenreCatalogCache's own per-source staleness
        // generation -- it is already the exact "monotonically increasing, fixed for this whole
        // refresh's lifetime, captured before any per-source async work starts" value the cache needs.
        // See SourceGenreCatalogCache.record's own doc.
        // KMK F2-03 corrective slice B (2026-08-27, tombstone-generation follow-up correction
        // 2026-08-28, atomicity correction 2026-08-27, production-caller-boundary correction
        // 2026-08-27): explicit pruning owner for "a source is removed, disabled, or no longer
        // eligible" (see SourceGenreCatalogCache.pruneIneligibleSources's own doc for why this is
        // scoped to installed-and-not-disabled, independent of the language filter). Passes
        // refreshGeneration -- captured above, immutable for this whole refresh -- so pruning also
        // raises this source's staleness barrier to at least this refresh's generation.
        //
        // This call site deliberately passes ONLY this refresh's own current eligible source ids --
        // it does NOT need to compute or track which sources have disappeared since an earlier
        // refresh. A THIRD independent review found that an earlier version of this fix (passing an
        // extra "known ineligible" set computed HERE from sourceManager.getVisibleSources()) still
        // could not protect a source that was eligible in an OLDER refresh, never recorded anything,
        // and was UNINSTALLED before this refresh -- such a source is invisible to
        // getVisibleSources() entirely, so no caller-side computation at this call site could ever
        // reconstruct it. SourceGenreCatalogCache.everEligibleSourceIds now owns that history
        // instead (see its own doc): the cache remembers every source id ANY past call here ever
        // marked eligible, so a source this call's own installedAndEnabledSourceIds omits but a
        // PAST call's didn't is still discoverable as "known, now ineligible" -- without this call
        // site needing to know anything about the past. Skipped entirely under a synthetic debug
        // fixture run (ForYouDebugFixture) so a developer's fixture session can never prune the real
        // catalog (and, as importantly, never pollutes everEligibleSourceIds with fixture-only ids).
        if (!ForYouDebugFixture.usesSyntheticInputs(fixtureMode)) {
            val installedAndEnabledSourceIds = sourceManager.getVisibleSources()
                .asSequence()
                .map { it.id }
                .filterNot { it in effectiveDisabledIds }
                .toSet()
            exh.recs.sources.SourceGenreCatalogCache.pruneIneligibleSources(
                eligibleSourceIds = installedAndEnabledSourceIds,
                generation = refreshGeneration,
            )
        }

        currentCoroutineContext().ensureActive()
        val loadIsActive = currentCoroutineContext().isActive
        synchronized(loadLaunchLock) {
            if (!loadIsActive) return
            searchJob?.cancel()
            searchJob = screenModelScope.launch(effectiveSearchDispatcher) {
                val allStatuses = mutableMapOf<Long, RecommendationSourceRunStatus>()
                var usefulCount = 0

                val candidateSources = selectRecommendationCandidates(
                    sources = orderedEnabledSources,
                    retrySourceIds = retryOnly,
                    maxAttempts = MAX_SOURCE_ATTEMPTS,
                    sourceId = Source::id,
                )

                // Resolve a private Latest budget for this refresh. It is passed to every source
                // search instead of living on the ScreenModel, so a cancelled older refresh cannot
                // reset or consume the newer refresh's counter.
                val latestBudgetState = LatestExplorationBudgetState(
                    limit = RecommendationLatestBudgetPolicy.resolveAttempts(
                        enabled = sourcePreferences.recommendationLatestExplorationEnabled().get(),
                        configuredPercent = sourcePreferences.recommendationLatestExplorationPercent().get(),
                        attemptedSourceCount = candidateSources.size,
                    ),
                )
                currentRefreshTimestamp = clock()

                for (batch in candidateSources.chunked(SOURCE_BATCH_SIZE)) {
                    if (!isActive) break

                    // Register batch in UI as Loading before searching
                    val initialContexts = batch.associate { it.id to RecommendationSearchContext(fallbackQuery, topTags) }
                    mutableState.update { state ->
                        val batchIds = batch.map { it.id }.toSet()
                        state.copy(
                            items = state.items.mutate { map ->
                                map.keys.filter { it.id in batchIds }.forEach { map.remove(it) }
                                batch.forEach { src -> map[src] = PersonalRecommendationResult.Loading }
                            },
                            sourceOrder = reconcileSourceOrder(state.sourceOrder, batch),
                            searchContexts = state.searchContexts.mutate { map -> initialContexts.forEach { (id, ctx) -> map[id] = ctx } },
                        )
                    }

                    val outcomes = batch.map { source ->
                        async {
                            searchSource(
                                source, queryKey, fingerprint, topTags, scoringProfile, aliasMap, aliasCandidates,
                                tasteByKey, visibility, hideKnownManga, seenKeys, forceRefresh,
                                source.id in boostedSourceIds,
                                lastStrategy = strategyMap[source.id],
                                // KMK v0.8.13
                                lastRunStatus = lastStatusMap[source.id],
                                // KMK --> v0.7.26
                                minChapterCount = minChapterCount,
                                // KMK <--
                                // KMK v0.8.2
                                resultBudget = resultBudget,
                                latestBudgetState = latestBudgetState,
                                // KMK <--
                                refreshGeneration = refreshGeneration,
                                // An inline Retry is an explicit user-requested attempt and must bypass
                                // automatic suppression for the selected failed sources only.
                                bypassSourceFailureSuppression = retryOnly != null,
                            )
                        }
                    }.awaitAll()

                    for (outcome in outcomes) {
                        if (outcome.successfulStrategy != null) {
                            synchronized(strategyMap) { strategyMap[outcome.source.id] = outcome.successfulStrategy }
                        } else if (RecommendationStrategyRecoveryPolicy.shouldForgetAfterRun(outcome.status)) {
                            // KMK v0.8.13: a failed/no-result run disproves the persisted strategy hint
                            // for this source -- remove only this source's entry, never the whole map.
                            synchronized(strategyMap) { strategyMap.remove(outcome.source.id) }
                        }
                        allStatuses[outcome.source.id] = outcome.status
                        if (outcome.isUseful) usefulCount++
                        updateItem(refreshGeneration, outcome.source, outcome.result, outcome.status)
                    }

                    if (usefulCount >= MAX_VISIBLE_SOURCE_ROWS) break
                }

                // Adjust statuses to reflect post-dedupe visibility (Shown → HiddenByDuplicateHandling
                // when all of a source's cards were hidden by cross-source display dedupe).
                if (isActive) {
                    val dedupedMap = mutableState.value.dedupedItems()
                    val sourceHasVisible = mutableState.value.items.keys.associate { src ->
                        val result = dedupedMap[src]
                        src.id to (result is PersonalRecommendationResult.Success && !result.isEmpty)
                    }
                    val adjusted = adjustStatusesForDedupe(allStatuses, sourceHasVisible)
                    allStatuses.clear()
                    allStatuses.putAll(adjusted)
                    mutableState.update { state ->
                        state.copy(sourceStatuses = adjusted.toPersistentMap())
                    }

                    // KMK v0.8.14-fix1: persist a read-only preview snapshot for Recommendation Settings'
                    // "For You sources" screen once this refresh has at least one visible row -- see
                    // RecommendationForYouPreviewSnapshotStore. Built from already-finalized display state
                    // (dedupedMap/combinedResult), not raw fetch data, so the preview matches exactly what
                    // this run's real For You page shows.
                    persistForYouPreviewSnapshot(dedupedMap)

                    // KMK F2-03 (KFC-V0.8.21-FIX2-CORRECTIVE-RECHECK-AND-RELEASE-PROGRAM) correction:
                    // the EARLIER focusKnownGroups written into State (below, before searchJob was even
                    // launched) snapshotted SourceGenreCatalogCache before this refresh's own per-source
                    // getFilterList() calls (inside searchSource(), one per batch above) had recorded
                    // anything into it -- on a truly fresh process, that snapshot is empty, so a newly
                    // discovered source's genre filters could never appear in the focus picker until an
                    // UNRELATED later refresh happened to re-snapshot a since-populated cache. This
                    // second, later recomputation -- now that every batch's searchSource() calls have
                    // completed and recorded their filter lists -- makes newly discovered criteria
                    // available within the SAME logically requested refresh instead of a hidden second
                    // one. Guarded by isActive (this whole block already is) so a cancelled refresh never
                    // writes a stale/inconsistent focusKnownGroups.
                    val refreshedFocusKnownGroups = buildFocusKnownGroups(
                        groupToAliases,
                        exh.recs.sources.SourceGenreCatalogCache.snapshot(),
                        focusAliasMap,
                    )
                    if (refreshedFocusKnownGroups != focusKnownGroups) {
                        mutableState.update { it.copy(focusKnownGroups = refreshedFocusKnownGroups) }
                    }
                }

                // A superseded refresh may observe cancellation at the batch boundary rather
                // than while awaiting a source. Its partial strategy/status/fit data must not
                // overwrite the newer refresh's durable state.
                if (!isActive) return@launch

                // Persist strategy map and source run statuses
                sourcePreferences.recommendationSourceStrategies().set(
                    RecommendationQueryPlanner.serializeStrategies(strategyMap),
                )
                sourcePreferences.recommendationLastSourceRunStatuses().set(
                    RecommendationSourceRunStatusStore.serialize(allStatuses.values),
                )
                // KMK --> v0.7.19: merge this run into rolling source fit stats
                val currentFitStats = SourceFitStatsStore.parse(sourcePreferences.recommendationSourceFitStats().get())
                // KMK --> v0.7.32: D2 — collect source IDs that ended up in the final Top Picks row
                val topPicksContributors = (mutableState.value.combinedResult as? PersonalRecommendationResult.Success)
                    ?.result?.map { it.manga.source }?.toSet() ?: emptySet()
                // KMK <--
                val updatedFitStats = SourceFitStatsStore.mergeRun(
                    currentFitStats,
                    allStatuses.values,
                    // KMK --> v0.7.32: D2
                    topPicksContributors,
                    // KMK <--
                )
                sourcePreferences.recommendationSourceFitStats().set(SourceFitStatsStore.serialize(updatedFitStats.values))
                // KMK <--

                mutableState.update { state ->
                    if (state.resultGeneration == refreshGeneration) {
                        state.copy(isLoading = false)
                    } else {
                        state
                    }
                }
            }
        }
    }

    /**
     * Searches a single source using the query planner. Returns a [SourceSearchOutcome] describing
     * the result, diagnostic status, and the successful query strategy (if any).
     */
    private suspend fun searchSource(
        source: Source,
        queryKey: String,
        fingerprint: String,
        topTags: List<String>,
        profile: TasteProfile,
        aliasMap: Map<String, String>,
        aliasCandidates: Map<String, List<String>>,
        tasteByKey: Map<MangaTasteKey, MangaTaste>,
        visibility: RatedMangaVisibility,
        hideKnownManga: Boolean,
        // KMK --> v0.6.20: always filter seen manga
        seenKeys: Set<SeenMangaKey>,
        // KMK <--
        forceRefresh: Boolean,
        isBoosted: Boolean,
        lastStrategy: RecommendationQueryStrategyType?,
        // KMK v0.8.13: this source's most recently recorded run status, used to decide whether
        // [lastStrategy] is still trustworthy -- see RecommendationStrategyRecoveryPolicy.
        lastRunStatus: RecommendationSourceRunStatus? = null,
        // KMK --> v0.7.26: minimum locally-known chapter count (0 = off)
        minChapterCount: Int = 0,
        // KMK <--
        // KMK v0.8.2: user-configured visible-card budget (raw preference value; validated inside
        // ForYouResultBudgetPolicy.resolve() below, never trusted directly).
        resultBudget: Int = ForYouResultBudgetPolicy.DEFAULT,
        latestBudgetState: LatestExplorationBudgetState = LatestExplorationBudgetState(0),
        // KMK F2-03 corrective slice B: this refresh()'s own resultGeneration, threaded through so
        // SourceGenreCatalogCache.record() can reject a stale, superseded refresh's late-arriving
        // result -- see that call site's own comment and SourceGenreCatalogCache.record's doc.
        refreshGeneration: Long = 0,
        bypassSourceFailureSuppression: Boolean = false,
    ): SourceSearchOutcome {
        // KMK --> v0.7.38: load remembered candidates before cache check so they are available for merge
        val remembered = try {
            memoryStore.loadForSourceQuery(source.id, queryKey)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        // KMK <--
        // KMK --> v0.7.40: load full progress records so the planner can classify retryable failures
        val progressRecords = try {
            progressStore.progressRecords(source.id, queryKey)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        // KMK <--

        if (!forceRefresh) {
            val cached = loadFromCache(source.id, queryKey, fingerprint, tasteByKey, visibility, hideKnownManga, seenKeys, minChapterCount)
            if (cached != null) {
                // KMK --> v0.7.38: merge cached page-1 results with remembered candidates from all pages
                val displayLimit = ForYouResultBudgetPolicy.resolve(resultBudget, isBoosted) // KMK v0.8.2
                val resolvedMemory = resolveMemoryEntries(remembered)
                val knownIdsForFilter = if (hideKnownManga && (cached.isNotEmpty() || resolvedMemory.isNotEmpty())) {
                    val allMangaIds = (cached.map { it.manga.id } + resolvedMemory.map { it.first.id }).distinct()
                    try {
                        getKnownMangaIds.await(allMangaIds)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        emptySet()
                    }
                } else {
                    emptySet()
                }
                // KMK --> v0.7.41: batch chapter counts across cached + memory so memory candidates
                // are min-chapter filtered by the same shared policy as cached candidates.
                val chapterCountsForFilter = if (minChapterCount > 0 && (cached.isNotEmpty() || resolvedMemory.isNotEmpty())) {
                    val allMangaIds = (cached.map { it.manga.id } + resolvedMemory.map { it.first.id }).distinct()
                    try {
                        getChapterCounts.await(allMangaIds)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logcat(LogPriority.WARN) { "Cached-merge chapter-count lookup failed, skipping min-chapter filter" }
                        emptyMap()
                    }
                } else {
                    emptyMap()
                }
                // KMK <--
                // KMK --> v0.7.40 Deliverable A: always merge so cache and memory compete for slots
                val merged = RecommendationCandidateMemoryRanker.merge(
                    resolvedMemory, cached, profile, aliasMap,
                    tasteByKey, visibility, seenKeys, knownIdsForFilter, displayLimit,
                    // KMK --> v0.7.41: apply min-chapter policy in the merge too
                    minChapterCount, chapterCountsForFilter,
                    // KMK <--
                )
                // KMK <--
                val status = RecommendationSourceRunStatus(
                    sourceId = source.id,
                    status = if (merged.isNotEmpty()) RecommendationSourceStatus.Shown else RecommendationSourceStatus.NoMatches,
                    visibleCount = merged.size,
                    evaluatedCount = progressRecords.sumOf { it.rawCount }.coerceAtLeast(0),
                )
                return SourceSearchOutcome(
                    source = source,
                    result = PersonalRecommendationResult.Success(merged),
                    successfulStrategy = null,
                    status = status,
                    isUseful = merged.isNotEmpty(),
                )
            }
        }

        val displayLimit = ForYouResultBudgetPolicy.resolve(resultBudget, isBoosted) // KMK v0.8.2
        // KMK --> v0.7.34: enrichment cap is user-configurable; boosted sources always get 2×
        val normalEnrichCap = sourcePreferences.recommendationEnrichmentCap().get().coerceIn(1, 20)
        val enrichLimit = if (isBoosted) normalEnrichCap * 2 else normalEnrichCap
        // KMK <--
        val rawCap = displayLimit * RAW_CANDIDATE_MULTIPLIER
        val minUseful = if (isBoosted) {
            RecommendationQueryPlanner.MIN_USEFUL_RESULTS_BOOSTED
        } else {
            RecommendationQueryPlanner.MIN_USEFUL_RESULTS_NORMAL
        }

        // KMK v0.8.13: a persisted strategy is only a hint -- resolve it through the recovery policy
        // first so a stale terminal TEXT_ONLY_TOP_TAGS lock or a strategy already disproved by a
        // recent failed run does not skip the strict-to-lenient chain.
        val effectiveStrategy = resolveEffectiveStrategy(lastStrategy, progressRecords, remembered.size, lastRunStatus)
        val plans = RecommendationQueryPlanner.buildPlans(topTags, effectiveStrategy)
        // KMK v0.8.10-fix2: widened from Exception? to Throwable? -- this loop previously only
        // caught Exception, so a broken/incompatible extension's LinkageError (e.g. the confirmed
        // NoClassDefFoundError: okhttp3.zstd.Zstd from the Asura Scans extension constructing its
        // HTTP client) was never caught here at all and crashed the whole For You load. See the new
        // catch(Error) branch below and RecommendationErrorClassifier.isRecoverableSourceFailure.
        val personalized = runPersonalizedPlans(
            source = source,
            queryKey = queryKey,
            fingerprint = fingerprint,
            topTags = topTags,
            profile = profile,
            aliasMap = aliasMap,
            aliasCandidates = aliasCandidates,
            tasteByKey = tasteByKey,
            visibility = visibility,
            hideKnownManga = hideKnownManga,
            seenKeys = seenKeys,
            forceRefresh = forceRefresh,
            isBoosted = isBoosted,
            lastStrategy = lastStrategy,
            lastRunStatus = lastRunStatus,
            minChapterCount = minChapterCount,
            resultBudget = resultBudget,
            latestBudgetState = latestBudgetState,
            refreshGeneration = refreshGeneration,
            bypassSourceFailureSuppression = bypassSourceFailureSuppression,
            remembered = remembered,
            progressRecords = progressRecords,
            displayLimit = displayLimit,
            enrichLimit = enrichLimit,
            rawCap = rawCap,
            minUseful = minUseful,
            plans = plans,
        )
        if (personalized.outcome != null) return personalized.outcome
        var hadRawResults = personalized.hadRawResults
        var lastError = personalized.lastError

        // Bounded Latest exploration lane.
        // Bounded Latest exploration lane.
        // Deliberately placed AFTER the full personalized strict-to-lenient chain and BEFORE the
        // existing Popular fallback, so:
        //   - personalized relevance remains the dominant lane and is never pre-empted;
        //   - Latest gets its chance before Popular, which is the whole point of the lane (Popular is
        //     a historical-prominence signal and is exactly what over-exposes familiar titles);
        //   - the existing Popular fallback contract is untouched for every source where Latest is
        //     unsupported, empty, failing, or out of budget.
        // Its failure can never alter `lastError` (which belongs to the personalized chain) and can
        // never suppress the Popular fallback below.
        val latest = tryLatestCatalogueLane(
            source, queryKey, fingerprint, topTags, profile, aliasMap, tasteByKey, visibility,
            seenKeys, hideKnownManga, minChapterCount, enrichLimit, displayLimit, rawCap,
            remembered, hadRawResults, lastError, latestBudgetState,
        )
        if (latest.outcome != null) return latest.outcome
        hadRawResults = latest.hadRawResults

        // KMK v0.8.13: extracted -- see tryCatalogueFallback's KDoc for the affected-source false
        // no-match investigation this addresses (unchanged from v0.8.12 other than the extraction
        // itself and the v0.8.13 QUERY_STRATEGY diagnostics recording added in Phase D).
        val fallback = tryCatalogueFallback(
            source, queryKey, fingerprint, topTags, profile, aliasMap, tasteByKey, visibility,
            seenKeys, hideKnownManga, minChapterCount, enrichLimit, displayLimit, rawCap,
            remembered, hadRawResults, lastError,
        )
        if (fallback.outcome != null) return fallback.outcome
        hadRawResults = fallback.hadRawResults

        return finalEmptyOutcome(source, hadRawResults, lastError)
    }

    private data class PersonalizedPlanSearchOutcome(
        val outcome: SourceSearchOutcome?,
        val hadRawResults: Boolean,
        val lastError: Throwable?,
    )

    /**
     * Runs the personalized query plan chain separately from the source orchestration method.
     *
     * Keeping the plan loop behind this owner-local seam prevents the orchestration method from
     * exceeding Android's compiler instruction limit while preserving the existing per-source
     * cancellation and recoverable-error boundaries.
     */
    private data class PersonalizedPlanAttemptResult(
        val outcome: SourceSearchOutcome?,
        val hadRawResults: Boolean,
        val lastError: Throwable?,
        val shouldContinue: Boolean,
    )

    private suspend fun runPersonalizedPlans(
        source: Source,
        queryKey: String,
        fingerprint: String,
        topTags: List<String>,
        profile: TasteProfile,
        aliasMap: Map<String, String>,
        aliasCandidates: Map<String, List<String>>,
        tasteByKey: Map<MangaTasteKey, MangaTaste>,
        visibility: RatedMangaVisibility,
        hideKnownManga: Boolean,
        seenKeys: Set<SeenMangaKey>,
        forceRefresh: Boolean,
        isBoosted: Boolean,
        lastStrategy: RecommendationQueryStrategyType?,
        lastRunStatus: RecommendationSourceRunStatus?,
        minChapterCount: Int,
        resultBudget: Int,
        latestBudgetState: LatestExplorationBudgetState,
        refreshGeneration: Long,
        bypassSourceFailureSuppression: Boolean,
        remembered: List<RecommendationCandidateMemoryEntry>,
        progressRecords: List<RecommendationDiscoveryProgress>,
        displayLimit: Int,
        enrichLimit: Int,
        rawCap: Int,
        minUseful: Int,
        plans: List<RecommendationQueryPlan>,
    ): PersonalizedPlanSearchOutcome {
        var lastError: Throwable? = null
        var hadRawResults = false

        for ((index, plan) in plans.withIndex()) {
            if (!currentCoroutineContext().isActive) {
                return PersonalizedPlanSearchOutcome(
                    outcome = SourceSearchOutcome(
                        source,
                        PersonalRecommendationResult.Success(emptyList()),
                        null,
                        RecommendationSourceRunStatus(source.id, RecommendationSourceStatus.NoMatches),
                        false,
                    ),
                    hadRawResults = hadRawResults,
                    lastError = lastError,
                )
            }
            val attempt = executePersonalizedPlan(
                source = source,
                queryKey = queryKey,
                fingerprint = fingerprint,
                topTags = topTags,
                profile = profile,
                aliasMap = aliasMap,
                aliasCandidates = aliasCandidates,
                tasteByKey = tasteByKey,
                visibility = visibility,
                hideKnownManga = hideKnownManga,
                seenKeys = seenKeys,
                forceRefresh = forceRefresh,
                isBoosted = isBoosted,
                lastStrategy = lastStrategy,
                lastRunStatus = lastRunStatus,
                minChapterCount = minChapterCount,
                resultBudget = resultBudget,
                latestBudgetState = latestBudgetState,
                refreshGeneration = refreshGeneration,
                bypassSourceFailureSuppression = bypassSourceFailureSuppression,
                remembered = remembered,
                progressRecords = progressRecords,
                displayLimit = displayLimit,
                enrichLimit = enrichLimit,
                rawCap = rawCap,
                minUseful = minUseful,
                plan = plan,
                isLastPlan = index == plans.lastIndex,
            )
            hadRawResults = hadRawResults || attempt.hadRawResults
            lastError = attempt.lastError
            if (attempt.outcome != null) {
                return PersonalizedPlanSearchOutcome(
                    outcome = attempt.outcome,
                    hadRawResults = hadRawResults,
                    lastError = lastError,
                )
            }
            if (attempt.shouldContinue) continue
        }

        return PersonalizedPlanSearchOutcome(
            outcome = null,
            hadRawResults = hadRawResults,
            lastError = lastError,
        )
    }

    private suspend fun executePersonalizedPlan(
        source: Source,
        queryKey: String,
        fingerprint: String,
        topTags: List<String>,
        profile: TasteProfile,
        aliasMap: Map<String, String>,
        aliasCandidates: Map<String, List<String>>,
        tasteByKey: Map<MangaTasteKey, MangaTaste>,
        visibility: RatedMangaVisibility,
        hideKnownManga: Boolean,
        seenKeys: Set<SeenMangaKey>,
        forceRefresh: Boolean,
        isBoosted: Boolean,
        lastStrategy: RecommendationQueryStrategyType?,
        lastRunStatus: RecommendationSourceRunStatus?,
        minChapterCount: Int,
        resultBudget: Int,
        latestBudgetState: LatestExplorationBudgetState,
        refreshGeneration: Long,
        bypassSourceFailureSuppression: Boolean,
        remembered: List<RecommendationCandidateMemoryEntry>,
        progressRecords: List<RecommendationDiscoveryProgress>,
        displayLimit: Int,
        enrichLimit: Int,
        rawCap: Int,
        minUseful: Int,
        plan: RecommendationQueryPlan,
        isLastPlan: Boolean,
    ): PersonalizedPlanAttemptResult {
        var lastError: Throwable? = null
        var hadRawResults = false
        if (!currentCoroutineContext().isActive) {
            return PersonalizedPlanAttemptResult(
                outcome = SourceSearchOutcome(
                    source,
                    PersonalRecommendationResult.Success(emptyList()),
                    null,
                    RecommendationSourceRunStatus(source.id, RecommendationSourceStatus.NoMatches),
                    false,
                ),
                hadRawResults = hadRawResults,
                lastError = lastError,
                shouldContinue = false,
            )
        }
        try {
            // KMK v0.8.10-fix4: routed through SourceRuntime instead of a local
            // try/catch(Exception) -- a recoverable failure (ordinary Exception or an extension
            // LinkageError such as the confirmed Asura Scans NoClassDefFoundError) now also
            // gets recorded in SourceRuntimeFailureRegistry, not just silently swallowed.
            val filterListResult = SourceRuntime.run(
                source,
                SourceRuntimeOperation.FilterList,
                coroutineDispatcher,
                bypassSuppression = bypassSourceFailureSuppression,
            ) {
                getFilterList()
            }
            // KMK C3 (E4.3): free byproduct of the FilterList fetch this call site already makes
            // for search-query purposes -- records this source's genre-like filter labels into
            // the process-lifetime focus criterion catalog cache, no extra network call. See
            // SourceGenreCatalogCache's own doc for why this is "not limited to currently loaded
            // recommendation results."
            //
            // KMK F2-03 corrective slice B: passes the raw Result (not an already-collapsed
            // FilterList) so record() can distinguish a genuinely successful empty extraction
            // (prunes stale labels) from a fetch failure (retains the last known-good labels) --
            // see SourceGenreCatalogCache.record's own doc. refreshGeneration is this whole
            // refresh()'s own resultGeneration, captured once before any per-source search began
            // (see refresh()'s own comment on refreshGeneration), so a stale, superseded refresh's
            // late-arriving result can never overwrite a newer refresh's fresher one for the same
            // source.
            exh.recs.sources.SourceGenreCatalogCache.record(source.id, refreshGeneration, filterListResult)
            val filterList = filterListResult.getOrElse { FilterList() }
            val searchParams = GenreFilterMapper.buildSearch(
                filterList,
                plan.tags,
                aliasCandidates,
                plan.forceTextOnly,
                // KMK --> v0.7.0: Phase 7 — push blocked tags as exclusion filters
                blockedGenres = profile.blockedGroups.toList(),
                // KMK <--
            )

            if (currentCoroutineContext().isActive) {
                val ctx = RecommendationSearchContext(searchParams.textQuery, plan.tags)
                mutableState.update { s ->
                    s.copy(searchContexts = s.searchContexts.mutate { it[source.id] = ctx })
                }
            }

            // KMK v0.8.10-fix7: routed through SourceRuntime instead of a raw call inside
            // withContext -- getOrThrowSourceRuntimeException() converts a recoverable failure
            // (which may be a raw LinkageError, an Error not an Exception) into
            // RecoverableSourceRuntimeException so the existing outer catch(Exception) below
            // records it as lastError through the primary containment path, not the defensive
            // catch(Error) below. A genuinely fatal error or CancellationException still
            // propagates directly out of SourceRuntime.run() itself, before ever reaching this
            // line.
            val page = SourceRuntime.run(
                source,
                SourceRuntimeOperation.Search,
                coroutineDispatcher,
                bypassSuppression = bypassSourceFailureSuppression,
            ) {
                getSearchManga(1, searchParams.textQuery, searchParams.filters)
            }.getOrThrowSourceRuntimeException()

            val rawSMangas = RecommendationRawCandidatePolicy.distinctWithinLimit(page.mangas, rawCap) { it.url }
            if (rawSMangas.isNotEmpty()) hadRawResults = true

            // KMK v0.8.13: extracted -- identical raw-manga-to-scored-recommendations pipeline
            // used by both this plan loop and the catalogue fallback (see processRawCandidates).
            val processed = processRawCandidates(
                source, rawSMangas, tasteByKey, visibility, seenKeys, minChapterCount,
                hideKnownManga, enrichLimit, displayLimit, profile, aliasMap,
            )
            val localized = processed.localized
            val knownIds = processed.knownIds
            val scoredCount = processed.scoredCount
            val recommendations = processed.recommendations

            if (recommendations.size < minUseful && !isLastPlan) {
                lastError = null
                return PersonalizedPlanAttemptResult(
                    outcome = null,
                    hadRawResults = hadRawResults,
                    lastError = null,
                    shouldContinue = true,
                )
            }

            val reason = recommendations.flatMap { it.matchedGroups }
                .distinct().take(MAX_REASON_TAGS).joinToString(", ").ifBlank { null }
            saveToCache(source.id, queryKey, fingerprint, recommendations.map { it.manga }, recommendations.map { it.score }, reason)

            // KMK --> v0.7.38: upsert page-1 results to memory + try additional page
            // memoryStore.upsertBatch already rethrows CancellationException and swallows
            // ordinary exceptions internally (RecommendationCandidateMemoryStore); this
            // try/catch only guards against a future change to that internal contract.
            try {
                memoryStore.upsertBatch(
                    sourceId = source.id,
                    querySignature = queryKey,
                    queryTags = topTags,
                    queryStrategy = plan.type.name,
                    page = 1,
                    profileFingerprint = fingerprint,
                    recommendations = recommendations,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
            // KMK --> v0.7.39: record page-1 progress so the planner can advance to page 2
            var currentProgressRecords = progressRecords
            try {
                progressStore.recordProgress(
                    sourceId = source.id,
                    querySignature = queryKey,
                    queryTags = topTags,
                    queryStrategy = plan.type.name,
                    page = 1,
                    profileFingerprint = fingerprint,
                    rawCount = rawSMangas.size,
                    localizedCount = localized.size,
                    scoredCount = scoredCount,
                    visibleCount = recommendations.size,
                    filteredCount = rawSMangas.size - localized.size,
                    status = when {
                        recommendations.isNotEmpty() -> RecommendationDiscoveryProgress.STATUS_SUCCESS
                        rawSMangas.isNotEmpty() -> RecommendationDiscoveryProgress.STATUS_FILTERED
                        else -> RecommendationDiscoveryProgress.STATUS_EMPTY
                    },
                )
                // Keep the planner's local snapshot in step with the page-1 write. Without
                // this, a fresh query has an empty pre-refresh snapshot and cannot schedule
                // its first additional page until a later refresh, regardless of the user's
                // configured discovery effort.
                currentProgressRecords = currentProgressRecords
                    .filterNot { it.page == 1 }
                    .plus(
                        RecommendationDiscoveryProgress(
                            sourceId = source.id,
                            querySignature = queryKey,
                            queryTagsJson = topTags.joinToString(","),
                            queryStrategy = plan.type.name,
                            page = 1,
                            evaluatedAt = clock(),
                            rawCount = rawSMangas.size,
                            localizedCount = localized.size,
                            scoredCount = scoredCount,
                            visibleCount = recommendations.size,
                            filteredCount = rawSMangas.size - localized.size,
                            status = when {
                                recommendations.isNotEmpty() -> RecommendationDiscoveryProgress.STATUS_SUCCESS
                                rawSMangas.isNotEmpty() -> RecommendationDiscoveryProgress.STATUS_FILTERED
                                else -> RecommendationDiscoveryProgress.STATUS_EMPTY
                            },
                            errorMessage = null,
                            profileFingerprint = fingerprint,
                        ),
                    )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
            // KMK <--

            // KMK v0.8.13: do not probe additional pages for a query that already proved it
            // returns nothing raw on page 1 -- see RecommendationAdditionalPagePolicy's KDoc for
            // the confirmed source TEXT_ONLY_TOP_TAGS page-crawl evidence this prevents.
            // KMK --> EC-04 2026-09-01: configurable discovery-effort policy. Probes up to
            // discoveryEffortLevel.additionalPagesPerRefresh pages THIS refresh --
            // DiscoveryEffortLevel.STANDARD's value of 1 reproduces the exact pre-existing
            // single-probe behavior byte-for-byte (same single discoverAdditionalPage call,
            // same single upsertBatch call), so existing users see no behavior change unless
            // they explicitly change the setting. Each iteration after the first re-derives
            // progressRecords from progressStore so the loop reacts to the REAL outcome the
            // previous iteration's discoverAdditionalPage just wrote (success/empty/error/
            // backoff/cap) via that function's own nextPageToProbe() call -- no separate
            // early-stop check is needed here: a page with nothing left to probe simply returns
            // (emptyList(), 0), which this loop treats as "stop early." discoverAdditionalPage
            // itself is completely unchanged.
            val discoveryEffortLevel = exh.recs.memory.DiscoveryEffortLevel.resolve(
                sourcePreferences.recommendationDiscoveryEffortLevel().get(),
            )
            var remainingCandidateBudget = exh.recs.memory.RecommendationDiscoveryCandidateBudgetPolicy.resolve(
                sourcePreferences.recommendationDiscoveryCandidateBudget().get(),
            )
            val additionalPageResults = mutableListOf<PersonalRecommendation>()
            var lastAdditionalPage = 0
            if (
                discoveryEffortLevel.additionalPagesPerRefresh > 0 &&
                remainingCandidateBudget > 0 &&
                RecommendationAdditionalPagePolicy.shouldDiscoverAdditionalPage(
                    planType = plan.type,
                    pageOneRawCount = rawSMangas.size,
                    pageOneVisibleCount = recommendations.size,
                    lastError = null,
                )
            ) {
                val plannedPages = RecommendationDiscoveryPlanner.planAdditionalPages(
                    progressRecords = currentProgressRecords,
                    maxAdditionalPages = discoveryEffortLevel.additionalPagesPerRefresh,
                    nowMs = clock(),
                )
                for ((iteration, plannedPage) in plannedPages.withIndex()) {
                    if (!currentCoroutineContext().isActive) break
                    // The planner assumes each earlier probe succeeds. Reconcile that plan
                    // against the persisted frontier before each probe so a retryable,
                    // blocked, or exhausted result never causes a later page to be probed.
                    val nextPage = RecommendationDiscoveryPlanner.nextPageToProbe(
                        currentProgressRecords,
                        clock(),
                    ) ?: break
                    if (nextPage != plannedPage) break
                    val (pageResults, probedPage, consumedCandidateCount) = discoverAdditionalPage(
                        source = source,
                        progressRecords = currentProgressRecords,
                        queryKey = queryKey,
                        topTags = topTags,
                        fingerprint = fingerprint,
                        queryStrategy = plan.type.name,
                        searchParams = searchParams,
                        tasteByKey = tasteByKey,
                        visibility = visibility,
                        seenKeys = seenKeys,
                        profile = profile,
                        aliasMap = aliasMap,
                        minChapterCount = minChapterCount,
                        // KMK --> v0.7.41 follow-up
                        hideKnownManga = hideKnownManga,
                        // KMK <--
                        candidateBudget = remainingCandidateBudget,
                    )
                    if (probedPage == 0) break
                    lastAdditionalPage = probedPage
                    remainingCandidateBudget -= consumedCandidateCount
                    if (pageResults.isNotEmpty()) {
                        additionalPageResults += pageResults
                        // memoryStore.upsertBatch already rethrows CancellationException and
                        // swallows ordinary exceptions internally; this try/catch only guards
                        // against a future change to that internal contract.
                        try {
                            memoryStore.upsertBatch(
                                sourceId = source.id,
                                querySignature = queryKey,
                                queryTags = topTags,
                                queryStrategy = plan.type.name,
                                page = probedPage,
                                profileFingerprint = fingerprint,
                                recommendations = pageResults,
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                        }
                    }
                    // The candidate budget is an aggregate per-refresh bound. Do not continue
                    // probing planned pages after the bound is exhausted; a zero budget would
                    // otherwise still create a page probe with no candidates to process. The
                    // check follows the memory upsert so the final allowed page is retained.
                    if (remainingCandidateBudget <= 0) break
                    if (iteration + 1 < plannedPages.size) {
                        currentProgressRecords = try {
                            progressStore.progressRecords(source.id, queryKey)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            currentProgressRecords
                        }
                    }
                }
            }
            val additionalResults = additionalPageResults.toList() to lastAdditionalPage
            // KMK <--
            try {
                memoryStore.pruneIfNeeded(source.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }

            // KMK v0.8.13: extracted -- identical remembered+fresh merge pipeline used by both
            // this plan loop and the catalogue fallback (see mergeFreshAndRememberedCandidates).
            // additionalResults.first is already known-filtered inside discoverAdditionalPage
            // (same hide-known context as page one), so knownIds (page-1) is sufficient for the
            // merge; no redundant DB round-trip is needed.
            val allNew = recommendations + additionalResults.first
            // Bounded additive
            // Latest augmentation -- see tryAdditiveLatestAugmentation's KDoc for why this is the
            // structural fix for "Latest does not run when personalized results already exist".
            // Only ever reached from this success path (mutually exclusive with the
            // hadRawResults == false rescue path further below), and shares the same per-refresh
            // budget pool, so total Latest source-touches per refresh stay bounded either way.
            val latestAdditive = tryAdditiveLatestAugmentation(
                source, queryKey, fingerprint, topTags, profile, aliasMap, tasteByKey, visibility,
                seenKeys, hideKnownManga, minChapterCount, enrichLimit, displayLimit, rawCap,
                latestBudgetState = latestBudgetState,
                excludeMangaIds = allNew.mapNotNull { it.manga.id.takeIf { id -> id != 0L } }.toSet(),
            )
            // Bounded soft
            // reordering by local exposure history -- see ExposureRerankContext/
            // RecommendationDisplayReranker. Batched once per source (not once per card); a
            // failed lookup fails open to no reordering (ExposureRerankContext.NONE), never to
            // hiding results. "Interacted" is derived live from favorite + existing taste data
            // already loaded for this refresh -- not a stored copy -- so it is always current.
            val candidatePool = allNew + latestAdditive
            val exposureContext = buildExposureRerankContext(source.id, candidatePool, tasteByKey)
            val mergedRecommendations = mergeFreshAndRememberedCandidates(
                remembered, candidatePool, profile, aliasMap, tasteByKey, visibility, seenKeys,
                knownIds, displayLimit, minChapterCount, exposureContext,
            )

            val status = RecommendationSourceRunStatus(
                sourceId = source.id,
                status = if (mergedRecommendations.isNotEmpty()) {
                    RecommendationSourceStatus.Shown
                } else if (hadRawResults) {
                    RecommendationSourceStatus.FilteredOut
                } else {
                    RecommendationSourceStatus.NoMatches
                },
                visibleCount = mergedRecommendations.size,
                evaluatedCount = progressStore.progressRecords(source.id, queryKey)
                    .sumOf { it.rawCount }
                    .coerceAtLeast(0),
            )
            return PersonalizedPlanAttemptResult(
                outcome = SourceSearchOutcome(
                    source = source,
                    result = PersonalRecommendationResult.Success(mergedRecommendations),
                    // KMK --> v0.7.44 Phase D.3: only persist this strategy as "successful" when it
                    // actually produced a visible result. Previously this was set unconditionally
                    // once the last plan in the chain was reached, so a source could get "locked"
                    // onto a strategy that produced zero results just because it was tried last.
                    successfulStrategy = plan.type.takeIf { mergedRecommendations.isNotEmpty() },
                    // KMK <--
                    status = status,
                    isUseful = mergedRecommendations.isNotEmpty(),
                ),
                hadRawResults = hadRawResults,
                lastError = lastError,
                shouldContinue = false,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // KMK v0.8.10-fix7: both source calls above now use
            // getOrThrowSourceRuntimeException(), so a recoverable extension-linkage failure
            // (e.g. NoClassDefFoundError) reaches this primary catch as
            // RecoverableSourceRuntimeException (an Exception), not as a raw Error. This is the
            // main containment path now, not the defensive catch(Error) below.
            lastError = e
        } catch (e: Error) {
            // KMK v0.8.10-fix7: defensive-only guard for any remaining path that has not been
            // migrated to getOrThrowSourceRuntimeException() -- SourceRuntime.run() itself
            // already rethrows CancellationException and any genuinely fatal Error directly
            // from *inside* its run()/runBlockingSourceCall() blocks (they never reach this
            // catch clause from there). KMK v0.8.12-fix1 correction: that guarantee does not
            // cover this whole try block -- enrichment, scoring, and memory/DB lookups between
            // the SourceRuntime calls run outside SourceRuntime's boundary but still inside this
            // try, so an unconditional `catch (e: Error)` here previously swallowed every Error
            // subtype unconditionally, including OutOfMemoryError/StackOverflowError/ThreadDeath.
            // rethrowIfFatal() rethrows anything that is not a recoverable per-source failure
            // (i.e. everything except LinkageError and its subtypes) before this branch ever
            // records it as a per-source lastError.
            rethrowIfFatal(e)
            lastError = e
        }

        return PersonalizedPlanAttemptResult(
            outcome = null,
            hadRawResults = hadRawResults,
            lastError = lastError,
            shouldContinue = false,
        )
    }
    /**
     * Bounded Latest-catalogue exploration probe for one source.
     *
     * Mirrors [tryCatalogueFallback]'s shape exactly -- same `CatalogueFallbackOutcome` contract,
     * same reuse of [processRawCandidates]/[mergeFreshAndRememberedCandidates], so **every** existing
     * filter still applies unchanged: blocked/adult tags, language, minimum chapters, known/rated/
     * Not Interested filtering, metadata confidence, enrichment, the positive-taste-evidence gate,
     * cross-source dedup, and per-source runtime isolation. Latest provenance never lets a candidate
     * bypass an exclusion rule.
     *
     * Differences from the Popular fallback, all deliberate:
     * - it runs even when the personalized attempts *did* return raw results, because its purpose is
     *   controlled exploration rather than rescuing a false no-match (it still only *returns* an
     *   outcome when the personalized chain produced nothing usable, so it can never displace a
     *   personalized result set);
     * - it is gated by [RecommendationCatalogueLanePolicy] on capability, eligibility, and the
     *   refresh's shared [RecommendationLatestBudgetPolicy] budget;
     * - it records itself under [RecommendationDiscoveryLane.LATEST_CATALOGUE], never under the
     *   Popular sentinel, so the two lanes stay distinguishable in memory and diagnostics forever.
     *
     * Never throws for a per-source problem: cancellation and fatal errors propagate (via
     * [SourceRuntime] and [rethrowIfFatal]); everything else becomes a bounded outcome.
     */
    private suspend fun tryLatestCatalogueLane(
        source: Source,
        queryKey: String,
        fingerprint: String,
        topTags: List<String>,
        profile: TasteProfile,
        aliasMap: Map<String, String>,
        tasteByKey: Map<MangaTasteKey, MangaTaste>,
        visibility: RatedMangaVisibility,
        seenKeys: Set<SeenMangaKey>,
        hideKnownManga: Boolean,
        minChapterCount: Int,
        enrichLimit: Int,
        displayLimit: Int,
        rawCap: Int,
        remembered: List<RecommendationCandidateMemoryEntry>,
        hadRawResults: Boolean,
        lastError: Throwable?,
        latestBudgetState: LatestExplorationBudgetState,
    ): CatalogueFallbackOutcome {
        // Only offer Latest when the personalized chain produced nothing usable and did not itself
        // fail -- identical gating to the Popular fallback, so a source that already has personalized
        // results keeps them and a malfunctioning source is not poked again.
        if (!RecommendationCatalogueFallbackPolicy.shouldAttempt(hadRawResults, lastError != null) ||
            !currentCoroutineContext().isActive
        ) {
            return CatalogueFallbackOutcome(null, hadRawResults)
        }

        val budget = latestBudgetState.limit
        val eligible = RecommendationCatalogueLanePolicy.shouldAttempt(
            supportsLatest = source.supportsLatest,
            sourceIsEligible = true,
            latestAttemptsUsed = latestBudgetState.attemptsUsed.get(),
            latestBudget = budget,
        )
        if (!eligible) return CatalogueFallbackOutcome(null, hadRawResults)
        // Claim the budget slot before the call so concurrent per-source coroutines in the same batch
        // cannot collectively overshoot it.
        if (latestBudgetState.attemptsUsed.getAndIncrement() >= budget) return CatalogueFallbackOutcome(null, hadRawResults)

        var rawFound = hadRawResults
        try {
            val pageResult = SourceRuntime.run(source, SourceRuntimeOperation.Latest, coroutineDispatcher) {
                getLatestUpdates(RecommendationCatalogueLanePolicy.LATEST_PAGE)
            }
            // A recoverable per-source failure (including UnsupportedOperationException from a source
            // that declared supportsLatest but never implemented it) is a bounded outcome, never an
            // error for this refresh -- the Popular fallback below still gets its turn.
            val page = pageResult.getOrElse { return CatalogueFallbackOutcome(null, rawFound) }

            val rawSMangas = RecommendationRawCandidatePolicy.distinctWithinLimit(
                page.mangas.filter { RecommendationCatalogueLanePolicy.isUsableEntry(it.url, it.title) },
                rawCap,
            ) { it.url }
            if (rawSMangas.isEmpty()) return CatalogueFallbackOutcome(null, rawFound)
            rawFound = true

            val processed = processRawCandidates(
                source, rawSMangas, tasteByKey, visibility, seenKeys, minChapterCount,
                hideKnownManga, enrichLimit, displayLimit, profile, aliasMap,
            )
            if (processed.recommendations.isEmpty()) return CatalogueFallbackOutcome(null, rawFound)

            val mergedRecommendations = mergeFreshAndRememberedCandidates(
                remembered, processed.recommendations, profile, aliasMap, tasteByKey,
                visibility, seenKeys, processed.knownIds, displayLimit, minChapterCount,
            )
            if (mergedRecommendations.isEmpty()) return CatalogueFallbackOutcome(null, rawFound)

            val reason = mergedRecommendations.flatMap { it.matchedGroups }
                .distinct().take(MAX_REASON_TAGS).joinToString(", ").ifBlank { null }
            saveToCache(source.id, queryKey, fingerprint, mergedRecommendations.map { it.manga }, mergedRecommendations.map { it.score }, reason)
            try {
                memoryStore.upsertBatch(
                    sourceId = source.id,
                    querySignature = queryKey,
                    queryTags = topTags,
                    queryStrategy = RecommendationDiscoveryLane.LATEST_CATALOGUE.storageKey,
                    page = RecommendationCatalogueLanePolicy.LATEST_PAGE,
                    profileFingerprint = fingerprint,
                    recommendations = mergedRecommendations,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
            try {
                progressStore.recordProgress(
                    sourceId = source.id,
                    querySignature = queryKey,
                    queryTags = topTags,
                    queryStrategy = RecommendationDiscoveryLane.LATEST_CATALOGUE.storageKey,
                    page = RecommendationCatalogueLanePolicy.LATEST_PAGE,
                    profileFingerprint = fingerprint,
                    rawCount = rawSMangas.size,
                    localizedCount = processed.localized.size,
                    scoredCount = processed.scoredCount,
                    visibleCount = mergedRecommendations.size,
                    filteredCount = rawSMangas.size - processed.localized.size,
                    status = RecommendationDiscoveryProgress.STATUS_SUCCESS,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
            return CatalogueFallbackOutcome(
                SourceSearchOutcome(
                    source = source,
                    result = PersonalRecommendationResult.Success(mergedRecommendations),
                    // Never persisted as a "successful strategy" -- Latest is a bounded exploration
                    // probe, not one of RecommendationQueryStrategyType's tag-search strategies, so it
                    // must never be locked in as one (same rule the Popular fallback follows).
                    successfulStrategy = null,
                    status = RecommendationSourceRunStatus(
                        sourceId = source.id,
                        status = RecommendationSourceStatus.Shown,
                        visibleCount = mergedRecommendations.size,
                    ),
                    isUseful = true,
                ),
                rawFound,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return CatalogueFallbackOutcome(null, rawFound)
        } catch (e: Error) {
            // Same contract as the Popular fallback: only a recoverable per-source failure is
            // swallowed; a fatal VM error thrown anywhere in the enrichment/scoring/merge pipeline
            // propagates rather than masquerading as "Latest found nothing".
            rethrowIfFatal(e)
            return CatalogueFallbackOutcome(null, rawFound)
        }
    }
    // KMK <--

    /**
     * Bounded, additive Latest-catalogue augmentation for a source that **already** produced usable
     * personalized results in this refresh.
     *
     * ## Why this exists (structural fix, not a new feature)
     *
     * [tryLatestCatalogueLane] (added in the prior pass) is gated by
     * `RecommendationCatalogueFallbackPolicy.shouldAttempt(hadRawResults, ...)`, which requires
     * `hadRawResults == false` -- i.e. it only fires when the personalized chain found *nothing*.
     * Because the personalized plan loop in `searchSource()` returns directly on success, that gate
     * meant Latest never ran at all for the common case where personalized search actually worked.
     * This function is the missing "Latest is additive even when personalized already succeeded"
     * path: it is called from inside the personalized loop's own success branch, immediately before
     * the final merge, so a small bounded slice of Latest candidates gets a chance to compete for
     * display alongside the personalized ones on every successful refresh, not only on a fallback.
     *
     * ## Bounds
     *
     * - **Per-source candidate count:** [RecommendationLatestBudgetPolicy.resolveAdditiveSlotsPerSource]
     *   caps how many Latest candidates are even added to the merge input pool -- this bounds Latest's
     *   maximum possible contribution to the row regardless of how any individual candidate scores,
     *   which is what keeps personalized results the majority (proven exhaustively in
     *   `RecommendationLatestBudgetPolicyTest`).
     * - **Per-refresh source-touch count:** shares the same [LatestExplorationBudgetState] pool
     *   [tryLatestCatalogueLane] uses. Only one of the two functions can ever run for a given
     *   source in a given refresh (they sit on mutually exclusive control-flow branches: this one only
     *   when personalized succeeded, the other only when it did not), so the refresh-owned budget is
     *   never double-spent per source and the refresh-wide ceiling still holds.
     *
     * ## Filter/provenance parity
     *
     * Reuses [processRawCandidates] exactly like every other lane -- blocked/adult tags, language,
     * minimum chapters, known/rated/Not Interested filtering, and the positive-taste-evidence gate all
     * apply unchanged. [excludeMangaIds] performs cross-lane dedup so a manga already present in the
     * personalized result set cannot also be counted as a Latest slot. A non-empty result is recorded
     * under its own [RecommendationDiscoveryLane.LATEST_CATALOGUE]-tagged
     * `memoryStore.upsertBatch`/`progressStore.recordProgress` call, kept separate from the
     * personalized batch's `plan.type.name`-tagged upsert, so provenance stays distinct even though
     * the two lanes' candidates are merged together only for *display*.
     *
     * Never throws for a per-source problem and never returns a partial/inconsistent state:
     * cancellation and fatal errors propagate; every other failure yields an empty list, which is a
     * no-op for the caller's merge (fails open to personalized-only results).
     */
    private suspend fun tryAdditiveLatestAugmentation(
        source: Source,
        queryKey: String,
        fingerprint: String,
        topTags: List<String>,
        profile: TasteProfile,
        aliasMap: Map<String, String>,
        tasteByKey: Map<MangaTasteKey, MangaTaste>,
        visibility: RatedMangaVisibility,
        seenKeys: Set<SeenMangaKey>,
        hideKnownManga: Boolean,
        minChapterCount: Int,
        enrichLimit: Int,
        displayLimit: Int,
        rawCap: Int,
        latestBudgetState: LatestExplorationBudgetState,
        excludeMangaIds: Set<Long>,
    ): List<PersonalRecommendation> {
        if (!currentCoroutineContext().isActive) return emptyList()

        val quota = RecommendationLatestBudgetPolicy.resolveAdditiveSlotsPerSource(
            enabled = sourcePreferences.recommendationLatestExplorationEnabled().get(),
            displayLimit = displayLimit,
            configuredPercent = sourcePreferences.recommendationLatestExplorationPercent().get(),
        )
        if (quota <= 0) return emptyList()

        val budget = latestBudgetState.limit
        val eligible = RecommendationCatalogueLanePolicy.shouldAttempt(
            supportsLatest = source.supportsLatest,
            sourceIsEligible = true,
            latestAttemptsUsed = latestBudgetState.attemptsUsed.get(),
            latestBudget = budget,
        )
        if (!eligible) return emptyList()
        // Claim the shared budget slot before the call, same contract as tryLatestCatalogueLane.
        if (latestBudgetState.attemptsUsed.getAndIncrement() >= budget) return emptyList()

        return try {
            val pageResult = SourceRuntime.run(source, SourceRuntimeOperation.Latest, coroutineDispatcher) {
                getLatestUpdates(RecommendationCatalogueLanePolicy.LATEST_PAGE)
            }
            val page = pageResult.getOrElse { return emptyList() }

            val rawSMangas = RecommendationRawCandidatePolicy.distinctWithinLimit(
                page.mangas.filter { RecommendationCatalogueLanePolicy.isUsableEntry(it.url, it.title) },
                rawCap,
            ) { it.url }
            if (rawSMangas.isEmpty()) return emptyList()

            val processed = processRawCandidates(
                source, rawSMangas, tasteByKey, visibility, seenKeys, minChapterCount,
                hideKnownManga, enrichLimit, displayLimit, profile, aliasMap,
            )
            // Cross-lane dedup: a manga already present in the personalized result set for this
            // source must never also be counted as (or displace a slot meant for) a Latest candidate.
            val additive = processed.recommendations
                .filter { it.manga.id == 0L || it.manga.id !in excludeMangaIds }
                .take(quota)
                // Tag the lane so the shared merge can
                // enforce the personalized-majority invariant on the realised final list.
                .map { it.copy(lane = RecommendationDiscoveryLane.LATEST_CATALOGUE) }
            if (additive.isEmpty()) return emptyList()

            try {
                memoryStore.upsertBatch(
                    sourceId = source.id,
                    querySignature = queryKey,
                    queryTags = topTags,
                    queryStrategy = RecommendationDiscoveryLane.LATEST_CATALOGUE.storageKey,
                    page = RecommendationCatalogueLanePolicy.LATEST_PAGE,
                    profileFingerprint = fingerprint,
                    recommendations = additive,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
            try {
                progressStore.recordProgress(
                    sourceId = source.id,
                    querySignature = queryKey,
                    queryTags = topTags,
                    queryStrategy = RecommendationDiscoveryLane.LATEST_CATALOGUE.storageKey,
                    page = RecommendationCatalogueLanePolicy.LATEST_PAGE,
                    profileFingerprint = fingerprint,
                    rawCount = rawSMangas.size,
                    localizedCount = processed.localized.size,
                    scoredCount = processed.scoredCount,
                    visibleCount = additive.size,
                    filteredCount = rawSMangas.size - processed.localized.size,
                    status = RecommendationDiscoveryProgress.STATUS_SUCCESS,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }

            additive
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        } catch (e: Error) {
            // Same contract as tryLatestCatalogueLane/tryCatalogueFallback: only a recoverable
            // per-source failure is swallowed here; a fatal VM error propagates.
            rethrowIfFatal(e)
            emptyList()
        }
    }
    // KMK <--

    // KMK v0.8.13 -->
    /** Result of [processRawCandidates] -- the raw-manga-to-scored-recommendations pipeline. */
    private data class ProcessedRawCandidates(
        val localized: List<Manga>,
        val knownIds: Set<Long>,
        val scoredCount: Int,
        val recommendations: List<PersonalRecommendation>,
    )

    /**
     * Shared raw-manga-to-scored-recommendations pipeline: localize, batch known-manga/chapter-count
     * lookups, apply the shared visibility policy, enrich, and rank with the strict positive-taste-
     * evidence gate (default of [PersonalRecommendationScorer.rankCandidates]). Extracted from
     * `searchSource()`'s plan loop and catalogue fallback -- both previously duplicated this exact
     * sequence, contributing to `searchSource()` exceeding Android's compiler instruction limit on a
     * live device. No behavior changed by this extraction; the two call sites now also both log a
     * warning on a failed batched lookup (previously only the plan-loop copy did).
     */
    private suspend fun processRawCandidates(
        source: Source,
        rawSMangas: List<SManga>,
        tasteByKey: Map<MangaTasteKey, MangaTaste>,
        visibility: RatedMangaVisibility,
        seenKeys: Set<SeenMangaKey>,
        minChapterCount: Int,
        hideKnownManga: Boolean,
        enrichLimit: Int,
        displayLimit: Int,
        profile: TasteProfile,
        aliasMap: Map<String, String>,
    ): ProcessedRawCandidates {
        val smangaByUrl = rawSMangas.associateBy { it.url }
        val raw = rawSMangas.map { it.toDomainManga(source.id) }
        val rawLocalized = networkToLocalManga(raw)
        val chapterCounts = if (minChapterCount > 0 && rawLocalized.isNotEmpty()) {
            try {
                getChapterCounts.await(rawLocalized.map { it.id })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN) { "Chapter count lookup failed, skipping min-chapter filter" }
                emptyMap()
            }
        } else {
            emptyMap()
        }
        val knownIds = if (hideKnownManga && rawLocalized.isNotEmpty()) {
            try {
                getKnownMangaIds.await(rawLocalized.map { it.id })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN) { "Known-manga filter failed, keeping all candidates" }
                emptySet()
            }
        } else {
            emptySet()
        }
        val saved = filterVisibleCandidates(
            rawLocalized,
            tasteByKey,
            visibility,
            seenKeys,
            knownIds,
            minChapterCount,
            chapterCounts,
        )
        val enriched = enricher.enrich(source, saved, smangaByUrl, enrichLimit)
        val scored = PersonalRecommendationScorer.rankCandidates(enriched, profile, aliasMap, displayLimit)
        val recommendations = scored.map { sc -> PersonalRecommendation(sc.manga, sc.score, sc.matchedGroups) }
        return ProcessedRawCandidates(saved, knownIds, scored.size, recommendations)
    }

    /**
     * Shared remembered-plus-fresh candidate merge: batches chapter counts across both sets and
     * delegates to [RecommendationCandidateMemoryRanker.merge]. Extracted from `searchSource()`'s
     * plan loop and catalogue fallback -- both previously duplicated this exact sequence. No behavior
     * changed by this extraction; both call sites now also log a warning on a failed batched
     * chapter-count lookup (previously only the plan-loop copy did).
     */
    private suspend fun mergeFreshAndRememberedCandidates(
        remembered: List<RecommendationCandidateMemoryEntry>,
        newResults: List<PersonalRecommendation>,
        profile: TasteProfile,
        aliasMap: Map<String, String>,
        tasteByKey: Map<MangaTasteKey, MangaTaste>,
        visibility: RatedMangaVisibility,
        seenKeys: Set<SeenMangaKey>,
        knownIds: Set<Long>,
        displayLimit: Int,
        minChapterCount: Int,
        // Optional exposure-aware
        // reordering, forwarded to RecommendationCandidateMemoryRanker.merge. Defaults to a no-op.
        exposureContext: ExposureRerankContext = ExposureRerankContext.NONE,
    ): List<PersonalRecommendation> {
        val resolvedMemory = resolveMemoryEntries(remembered)
        val chapterCountsForMerge = if (minChapterCount > 0 && (newResults.isNotEmpty() || resolvedMemory.isNotEmpty())) {
            val allMangaIds = (newResults.map { it.manga.id } + resolvedMemory.map { it.first.id }).distinct()
            try {
                getChapterCounts.await(allMangaIds)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN) { "Live-merge chapter-count lookup failed, skipping min-chapter filter" }
                emptyMap()
            }
        } else {
            emptyMap()
        }
        // Always merge so extra-page/fallback candidates compete for display even when memory is empty.
        return RecommendationCandidateMemoryRanker.merge(
            resolvedMemory, newResults, profile, aliasMap,
            tasteByKey, visibility, seenKeys, knownIds, displayLimit,
            minChapterCount, chapterCountsForMerge,
            if (exposureContext.enabled) exposureContext.exposureByKey else emptyMap(),
            exposureContext.interactions,
            exposureContext.now,
            exposureContext.windowDays,
        )
    }

    /** Bundles the optional exposure-reranking inputs for [mergeFreshAndRememberedCandidates]. */
    private data class ExposureRerankContext(
        val exposureByKey: Map<RecommendationDisplayReranker.ExposureKey, RecommendationDisplayReranker.ExposureSummary>,
        val interactions: RecommendationDisplayReranker.InteractionSignals,
        val now: Long,
        val windowDays: Int,
        val enabled: Boolean,
    ) {
        companion object {
            val NONE = ExposureRerankContext(
                emptyMap(),
                RecommendationDisplayReranker.InteractionSignals.NONE,
                0L,
                RecommendationExposurePolicy.DEFAULT_WINDOW_DAYS,
                false,
            )
        }
    }

    /**
     * Loads exposure history for [candidates]' `(sourceId, url)` keys and builds the reranking
     * context.
     *
     * ## The three positive-interaction exemptions
     *
     * - **Library**: [Manga.favorite], already loaded for this refresh.
     * - **Rated**: a taste row present in [tasteByKey], already loaded for this refresh.
     * - **Tracked**: a real batched [GetTracks] lookup over the candidates' local manga ids.
     *
     * All three are live signals rather than a stored copy of interaction, so none can go stale
     * independently of the rest of the app.
     *
     * ## Fail-open behavior
     *
     * the tracker check used to be omitted entirely
     * (hardcoded `isTracked = false`), which silently risked penalising a title the user actively
     * tracks. It is now a real lookup. [GetTracks.await] already swallows its own failures and
     * returns an empty map, and this call is additionally wrapped: on **any** failure the tracked set
     * is left empty. An empty tracked set only ever means "no tracker exemption was applied", which
     * can at worst leave a small ordering penalty in place -- it can never hide, exclude, or
     * negatively rate a candidate. Cancellation always propagates.
     */
    private suspend fun buildExposureRerankContext(
        sourceId: Long,
        candidates: List<PersonalRecommendation>,
        tasteByKey: Map<MangaTasteKey, MangaTaste>,
    ): ExposureRerankContext {
        if (candidates.isEmpty()) return ExposureRerankContext.NONE
        val keys = candidates.map { sourceId to it.manga.url }.distinct()
        val records = try {
            getRecommendationExposure.awaitBySourceUrls(keys)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        if (records.isEmpty()) return ExposureRerankContext.NONE

        val exposureByKey = records.associate {
            RecommendationDisplayReranker.ExposureKey(it.sourceId, it.url) to
                RecommendationDisplayReranker.ExposureSummary(it.lastExposedAt, it.exposureCount, null)
        }

        val libraryKeys = mutableSetOf<RecommendationDisplayReranker.ExposureKey>()
        val ratedKeys = mutableSetOf<RecommendationDisplayReranker.ExposureKey>()
        candidates.forEach { rec ->
            val key = RecommendationDisplayReranker.ExposureKey(sourceId, rec.manga.url)
            if (rec.manga.favorite) libraryKeys += key
            if (tasteByKey.containsKey(MangaTasteKey(rec.manga.source, rec.manga.url))) ratedKeys += key
        }

        val trackedState = resolveTrackedExposureKeys(sourceId, candidates)

        val windowDays = RecommendationExposurePolicy.validateWindowDays(
            sourcePreferences.recommendationExposureWindowDays().get(),
        )
        val enabled = sourcePreferences.recommendationExposureWindowEnabled().get()
        return ExposureRerankContext(
            exposureByKey,
            RecommendationDisplayReranker.InteractionSignals(libraryKeys, ratedKeys, trackedState),
            currentRefreshTimestamp,
            windowDays,
            enabled,
        )
    }

    /**
     * One batched tracker lookup for this source's candidates, returning a **tri-state**.
     *
     * this previously returned a bare `Set`, collapsing
     * "the lookup failed" into "nothing is tracked". The reranker read that empty set as a positive
     * fact and could therefore demote a title the user actively tracks. It now returns
     * [RecommendationDisplayReranker.TrackedState.Unknown] on any failure, which the reranker treats
     * as "no candidate may be penalised".
     *
     * - Every candidate has an unresolved local id -> `Known(emptySet())`: nothing *can* be tracked,
     *   which is a fact, not an unknown.
     * - Lookup returns data -> `Known(<tracked keys>)`.
     * - Lookup fails -> [RecommendationDisplayReranker.TrackedState.Unknown].
     *
     * Cancellation always propagates. The failure log is a fixed string: no url, title, manga id,
     * tracker identifier, account detail, or exception object is recorded.
     */
    private suspend fun resolveTrackedExposureKeys(
        sourceId: Long,
        candidates: List<PersonalRecommendation>,
    ): RecommendationDisplayReranker.TrackedState {
        val idToUrl = candidates
            .filter { it.manga.id != 0L }
            .associate { it.manga.id to it.manga.url }
        // No candidate is resolvable locally, so none can carry a track row. This is a known-empty
        // result, not an unavailable one.
        if (idToUrl.isEmpty()) return RecommendationDisplayReranker.TrackedState.Known(emptySet())

        val tracksByMangaId = try {
            getTracks.awaitOrNull(idToUrl.keys.toList())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (tracksByMangaId == null) {
            logcat(LogPriority.WARN) { "Exposure tracker lookup unavailable; exposure reordering skipped" }
            return RecommendationDisplayReranker.TrackedState.Unknown
        }

        val trackedKeys = tracksByMangaId
            .asSequence()
            .filter { it.value.isNotEmpty() }
            .mapNotNull { entry -> idToUrl[entry.key] }
            .map { RecommendationDisplayReranker.ExposureKey(sourceId, it) }
            .toSet()
        return RecommendationDisplayReranker.TrackedState.Known(trackedKeys)
    }
    // KMK <--

    /** Result of [tryCatalogueFallback]. */
    private data class CatalogueFallbackOutcome(
        val outcome: SourceSearchOutcome?,
        val hadRawResults: Boolean,
    )

    /**
     * Bounded catalogue fallback -- see [RecommendationCatalogueFallbackPolicy]'s KDoc for the
     * the false no-match investigation for an affected source (v0.8.12). Every tag-based search
     * attempt in `searchSource()`'s plan loop found zero raw results and no source error occurred, so
     * this gives the source's plain Popular catalogue one bounded, single-page chance before
     * reporting NoMatches -- reusing exactly the same visibility/enrichment/scoring/merge pipeline as
     * an ordinary successful attempt, so every existing filter (blocked/adult/known/rated/seen/
     * min-chapter/dedup) still applies unchanged. Extracted unchanged from `searchSource()` in
     * v0.8.13 (Phase G) other than reusing [processRawCandidates]/[mergeFreshAndRememberedCandidates].
     *
     * Returns [CatalogueFallbackOutcome.outcome] (non-null) only on a genuine success -- callers must
     * `return` it directly. On any other path (nothing found, filtered to nothing, or a recoverable
     * failure), returns null and the caller must use [CatalogueFallbackOutcome.hadRawResults] (which
     * may have become true even though outcome is null, exactly as the un-extracted code did) to
     * compute the final NoMatches/FilteredOut status.
     */
    private suspend fun tryCatalogueFallback(
        source: Source,
        queryKey: String,
        fingerprint: String,
        topTags: List<String>,
        profile: TasteProfile,
        aliasMap: Map<String, String>,
        tasteByKey: Map<MangaTasteKey, MangaTaste>,
        visibility: RatedMangaVisibility,
        seenKeys: Set<SeenMangaKey>,
        hideKnownManga: Boolean,
        minChapterCount: Int,
        enrichLimit: Int,
        displayLimit: Int,
        rawCap: Int,
        remembered: List<RecommendationCandidateMemoryEntry>,
        hadRawResults: Boolean,
        lastError: Throwable?,
    ): CatalogueFallbackOutcome {
        if (!RecommendationCatalogueFallbackPolicy.shouldAttempt(hadRawResults, lastError != null) ||
            !currentCoroutineContext().isActive
        ) {
            return CatalogueFallbackOutcome(null, hadRawResults)
        }
        var rawFound = hadRawResults
        try {
            val page = SourceRuntime.run(source, SourceRuntimeOperation.Popular, coroutineDispatcher) {
                getPopularManga(1)
            }.getOrThrowSourceRuntimeException()
            val rawSMangas = RecommendationRawCandidatePolicy.distinctWithinLimit(page.mangas, rawCap) { it.url }
            if (rawSMangas.isEmpty()) return CatalogueFallbackOutcome(null, rawFound)
            rawFound = true

            val processed = processRawCandidates(
                source, rawSMangas, tasteByKey, visibility, seenKeys, minChapterCount,
                hideKnownManga, enrichLimit, displayLimit, profile, aliasMap,
            )
            if (processed.recommendations.isEmpty()) return CatalogueFallbackOutcome(null, rawFound)

            val mergedRecommendations = mergeFreshAndRememberedCandidates(
                remembered, processed.recommendations, profile, aliasMap, tasteByKey,
                visibility, seenKeys, processed.knownIds, displayLimit, minChapterCount,
            )
            if (mergedRecommendations.isEmpty()) return CatalogueFallbackOutcome(null, rawFound)

            val reason = mergedRecommendations.flatMap { it.matchedGroups }
                .distinct().take(MAX_REASON_TAGS).joinToString(", ").ifBlank { null }
            saveToCache(source.id, queryKey, fingerprint, mergedRecommendations.map { it.manga }, mergedRecommendations.map { it.score }, reason)
            // KMK v0.8.13: record the fallback in candidate memory/discovery progress under
            // RecommendationCatalogueFallbackPolicy.QUERY_STRATEGY, the same way an ordinary
            // successful plan records itself under plan.type.name -- so a future refresh's
            // remembered-candidate/progress evidence and diagnostics can tell a fallback result apart
            // from a real tag/text search strategy. Never written to
            // recommendationSourceStrategies() (successfulStrategy stays null below), so this cannot
            // be mistaken for a persisted RecommendationQueryStrategyType hint.
            try {
                memoryStore.upsertBatch(
                    sourceId = source.id,
                    querySignature = queryKey,
                    queryTags = topTags,
                    queryStrategy = RecommendationCatalogueFallbackPolicy.QUERY_STRATEGY,
                    page = 1,
                    profileFingerprint = fingerprint,
                    recommendations = mergedRecommendations,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
            try {
                progressStore.recordProgress(
                    sourceId = source.id,
                    querySignature = queryKey,
                    queryTags = topTags,
                    queryStrategy = RecommendationCatalogueFallbackPolicy.QUERY_STRATEGY,
                    page = 1,
                    profileFingerprint = fingerprint,
                    rawCount = rawSMangas.size,
                    localizedCount = processed.localized.size,
                    scoredCount = processed.scoredCount,
                    visibleCount = mergedRecommendations.size,
                    filteredCount = rawSMangas.size - processed.localized.size,
                    status = RecommendationDiscoveryProgress.STATUS_SUCCESS,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
            val status = RecommendationSourceRunStatus(
                sourceId = source.id,
                status = RecommendationSourceStatus.Shown,
                visibleCount = mergedRecommendations.size,
            )
            return CatalogueFallbackOutcome(
                SourceSearchOutcome(
                    source = source,
                    result = PersonalRecommendationResult.Success(mergedRecommendations),
                    // Not persisted as a "successful strategy" -- this is a bounded catalogue probe,
                    // not one of RecommendationQueryStrategyType's tag-search strategies, so it must
                    // never be locked in as one.
                    successfulStrategy = null,
                    status = status,
                    isUseful = true,
                ),
                rawFound,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A failed fallback probe is not itself an error worth reporting -- the search attempts
            // above already established there's no source error; fall through to the normal
            // NoMatches/FilteredOut status exactly as if the fallback had not run.
            return CatalogueFallbackOutcome(null, rawFound)
        } catch (e: Error) {
            // KMK v0.8.12-fix1: same defect as the main attempt loop's catch(Error) -- this
            // previously swallowed every Error subtype unconditionally, so a fatal VM error thrown
            // anywhere in the fallback's enrichment/scoring/merge pipeline (not just the
            // SourceRuntime.run() call) would silently vanish instead of propagating. A fatal error
            // must never be treated as "the fallback simply found nothing." Only a recoverable
            // per-source failure (LinkageError and its subtypes) is swallowed here; everything else
            // is rethrown.
            rethrowIfFatal(e)
            return CatalogueFallbackOutcome(null, rawFound)
        }
    }

    /** Resolves a persisted strategy hint through [RecommendationStrategyRecoveryPolicy]. */
    private fun resolveEffectiveStrategy(
        lastStrategy: RecommendationQueryStrategyType?,
        progressRecords: List<RecommendationDiscoveryProgress>,
        rememberedCount: Int,
        lastRunStatus: RecommendationSourceRunStatus?,
    ): RecommendationQueryStrategyType? = RecommendationStrategyRecoveryPolicy.effectiveStartingStrategy(
        lastStrategy,
        progressRecords,
        rememberedCount,
        lastRunStatus,
    )

    /** Terminal [SourceSearchOutcome] once every attempt (including the catalogue fallback) found nothing. */
    private fun finalEmptyOutcome(
        source: Source,
        hadRawResults: Boolean,
        lastError: Throwable?,
    ): SourceSearchOutcome {
        val finalStatus = when {
            lastError != null -> RecommendationSourceRunStatus(source.id, RecommendationSourceStatus.Error)
            hadRawResults -> RecommendationSourceRunStatus(source.id, RecommendationSourceStatus.FilteredOut)
            else -> RecommendationSourceRunStatus(source.id, RecommendationSourceStatus.NoMatches)
        }
        val finalResult = if (lastError != null) {
            PersonalRecommendationResult.Error(lastError)
        } else {
            PersonalRecommendationResult.Success(emptyList())
        }
        return SourceSearchOutcome(source, finalResult, null, finalStatus, false)
    }
    // KMK <--

    // --- Cache helpers ---

    private suspend fun loadFromCache(
        sourceId: Long,
        queryKey: String,
        fingerprint: String,
        tasteByKey: Map<MangaTasteKey, MangaTaste>,
        visibility: RatedMangaVisibility,
        hideKnownManga: Boolean,
        // KMK --> v0.6.20
        seenKeys: Set<SeenMangaKey> = emptySet(),
        // KMK <--
        // KMK --> v0.7.41: min-chapter context so cached results obey the same policy as live results
        minChapterCount: Int = 0,
        // KMK <--
    ): List<PersonalRecommendation>? {
        val cacheKey = cacheKey(sourceId, queryKey)
        val entry = getRecommendationCache.await(cacheKey) ?: return null

        val now = clock()
        if (entry.expiresAt < now) return null
        if (entry.profileFingerprint != fingerprint) return null

        val ids = entry.resultMangaIds.split(",").mapNotNull { it.trim().toLongOrNull() }
        if (ids.isEmpty()) return emptyList()

        val scores = entry.resultScores?.split(",").orEmpty().mapNotNull { it.trim().toDoubleOrNull() }
        val cachedGroups = entry.resultReasons?.split(", ").orEmpty().filter { it.isNotBlank() }

        // Resolve every cached manga first so known-id and chapter-count lookups can be batched.
        val resolved = ids.mapIndexedNotNull { index, id ->
            val manga = getMangaInteractor.await(id) ?: return@mapIndexedNotNull null
            manga to scores.getOrElse(index) { 0.0 }
        }
        if (resolved.isEmpty()) return emptyList()

        val knownIds = if (hideKnownManga) {
            try {
                getKnownMangaIds.await(resolved.map { it.first.id })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN) { "Top Picks cached known-manga filter failed, keeping cached candidates" }
                emptySet()
            }
        } else {
            emptySet()
        }

        // KMK --> v0.7.41: batched chapter counts only when the minimum is active; fail open on error
        val chapterCounts = if (minChapterCount > 0) {
            try {
                getChapterCounts.await(resolved.map { it.first.id })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN) { "Cached chapter-count lookup failed, skipping min-chapter filter" }
                emptyMap()
            }
        } else {
            emptyMap()
        }
        // KMK <--

        // Single shared visibility contract for cached candidates (favorite/rated/seen/known/min-chapter).
        return resolved.mapNotNull { (manga, score) ->
            val visible = RecommendationCandidateVisibilityPolicy.evaluate(
                manga = manga,
                tasteByKey = tasteByKey,
                visibility = visibility,
                seenKeys = seenKeys,
                knownIds = knownIds,
                minChapterCount = minChapterCount,
                chapterCounts = chapterCounts,
            ) == CandidateVisibility.VISIBLE
            if (!visible) null else PersonalRecommendation(manga, score, cachedGroups)
        }
    }

    private suspend fun saveToCache(
        sourceId: Long,
        queryKey: String,
        fingerprint: String,
        result: List<Manga>,
        scores: List<Double>,
        reason: String?,
    ) {
        val now = clock()
        val entry = RecommendationCacheEntry(
            cacheKey = cacheKey(sourceId, queryKey),
            sourceId = sourceId,
            profileFingerprint = fingerprint,
            queryKey = queryKey,
            resultMangaIds = result.joinToString(",") { it.id.toString() },
            resultScores = scores.joinToString(","),
            resultReasons = reason,
            createdAt = now,
            expiresAt = now + CACHE_TTL_MS,
        )
        upsertRecommendationCache.await(entry)
    }

    private fun cacheKey(sourceId: Long, queryKey: String): String =
        "personal_v3:$sourceId:$queryKey"

    private fun profileFingerprint(
        topTags: List<String>,
        profile: TasteProfile,
        aliasMap: Map<String, String>,
        disabledSourceIds: Set<Long>,
        storedOrder: List<Long>,
        languages: Set<String>,
        hideKnownManga: Boolean,
        // KMK --> v0.6.20: invalidate cache when seen set changes
        seenMangaCount: Int = 0,
        // KMK <--
        // KMK --> v0.7.26: invalidate cache when min chapter filter changes
        minChapterCount: Int = 0,
        // KMK <--
        // KMK --> v0.8.2: invalidate cache when the visible-card budget changes — a cache saved
        // under a smaller budget stores fewer rows than a larger budget needs, so it must never be
        // treated as complete/reusable for a later, larger setting.
        resultBudget: Int = ForYouResultBudgetPolicy.DEFAULT,
        // KMK <--
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun update(s: String) = digest.update(s.toByteArray())
        update("personal_v3")
        RecommendationSourceFilter.normalizeLanguages(languages).sorted().forEach { update("lang:$it") }
        topTags.sorted().forEach { update(it) }
        profile.explicitTagPreferences.entries.sortedBy { it.key }.forEach { update("${it.key}=${it.value}") }
        profile.learnedTagWeights.entries.sortedBy { it.key }.forEach { update("${it.key}=${it.value}") }
        profile.blockedGroups.sorted().forEach { update(it) }
        profile.sourceAffinity.entries.sortedBy { it.key }.forEach { update("${it.key}=${it.value}") }
        aliasMap.entries.sortedBy { it.key }.forEach { update("${it.key}=${it.value}") }
        disabledSourceIds.sorted().forEach { update(it.toString()) }
        storedOrder.forEach { update(it.toString()) }
        update("hideKnown:$hideKnownManga")
        // KMK --> v0.6.20
        update("seenCount:$seenMangaCount")
        // KMK <--
        // KMK --> v0.7.26
        update("minChapter:$minChapterCount")
        // KMK <--
        // KMK --> v0.8.2
        update("resultBudget:${ForYouResultBudgetPolicy.validate(resultBudget)}")
        // KMK <--
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // --- Profile helpers ---

    private fun topSearchTags(profile: TasteProfile): List<String> {
        val scores = mutableMapOf<String, Double>()
        profile.explicitTagPreferences.filterValues { it > 0 }
            .forEach { (tag, _) -> scores.merge(tag, 3.0, Double::plus) }
        profile.learnedTagWeights.filterValues { it > 0 }
            .forEach { (tag, w) -> scores.merge(tag, w, Double::plus) }
        return scores.entries.sortedByDescending { it.value }
            .take(MAX_SEARCH_TAGS)
            .map { it.key }
    }

    // KMK --> v0.7.43: Not Interested mild-negative signal
    /**
     * Returns [profile] with a small negative adjustment applied to [TasteProfile.learnedTagWeights]
     * for genre groups seen on manga the user marked Not Interested (`MangaRating.NOT_INTERESTED`).
     *
     * This is intentionally much weaker than a Dislike rating (-2.0 per genre occurrence, uncapped
     * per-source contribution): Not Interested contributes [NOT_INTERESTED_WEIGHT] per genre
     * occurrence and only affects in-memory scoring for this load — it never writes to the ratings
     * table, so it does not count toward the reassessment rating threshold or taste-profile
     * confidence. Only manga with an existing local DB row (already seen/localized once before)
     * contribute; this never triggers a network lookup. Bounded by
     * [NOT_INTERESTED_LOOKUP_CAP] to keep the cost predictable for large seen sets.
     */
    private suspend fun buildNotInterestedAdjustedProfile(
        profile: TasteProfile,
        seenKeys: Set<SeenMangaKey>,
        aliasMap: Map<String, String>,
    ): TasteProfile {
        if (seenKeys.isEmpty()) return profile

        val penalty = mutableMapOf<String, Double>()
        for (key in seenKeys.take(NOT_INTERESTED_LOOKUP_CAP)) {
            val manga = try {
                getMangaInteractor.await(key.url, key.sourceId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } ?: continue
            val genres = manga.genre ?: continue
            for (genre in genres) {
                val normalized = genre.normalizeTag()
                val group = aliasMap[normalized] ?: normalized
                penalty[group] = (penalty[group] ?: 0.0) + NOT_INTERESTED_WEIGHT
            }
        }
        if (penalty.isEmpty()) return profile

        val adjustedWeights = profile.learnedTagWeights.toMutableMap()
        for ((group, delta) in penalty) {
            adjustedWeights[group] = ((adjustedWeights[group] ?: 0.0) + delta).coerceIn(-10.0, 10.0)
        }
        return profile.copy(learnedTagWeights = adjustedWeights)
    }
    // KMK <--

    private fun buildAliasCandidates(
        topTags: List<String>,
        groupToAliases: Map<String, List<String>>,
    ): Map<String, List<String>> = topTags.associate { tag ->
        val userAliases = groupToAliases[tag].orEmpty()
        val builtIn = GenreFilterMapper.BUILT_IN_SYNONYMS[tag].orEmpty()
        tag to (userAliases + builtIn).distinct()
    }

    // KMK --> v0.7.38: candidate memory helpers

    /** Resolve remembered memory entries to local Manga objects. Unresolvable entries are skipped. */
    private suspend fun resolveMemoryEntries(
        entries: List<RecommendationCandidateMemoryEntry>,
    ): List<Pair<Manga, RecommendationCandidateMemoryEntry>> =
        entries.mapNotNull { entry ->
            val manga = try {
                if (entry.mangaId != null) {
                    getMangaInteractor.await(entry.mangaId)
                } else {
                    getMangaInteractor.await(entry.url, entry.sourceId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (manga != null) manga to entry else null
        }

    /**
     * Probe the next unevaluated page for [source] using [searchParams] from the page-1 plan.
     * Returns (results, probed-page-number). Results may be empty if the page was empty/filtered.
     * Records progress in the discovery progress table regardless of outcome.
     * CancellationException is always rethrown.
     *
     * v0.7.39: uses full progress records from the progress table (not candidate memory
     * knownPages), so empty/filtered/error pages are tracked and not retried.
     * v0.7.41 follow-up: applies the same hide-known context ([hideKnownManga]) as live page-one,
     * cache, memory, and group recommendations, so localizedCount/filteredCount/scoredCount/
     * visibleCount/progressStatus/returned recommendations/candidate memory are all derived from
     * the fully filtered result — a known-only page is never recorded as successful/visible.
     */
    private suspend fun discoverAdditionalPage(
        source: Source,
        // KMK --> v0.7.40: full progress records replace Set<Int> so planner can classify failures
        progressRecords: List<RecommendationDiscoveryProgress>,
        // KMK <--
        queryKey: String,
        topTags: List<String>,
        fingerprint: String,
        queryStrategy: String?,
        searchParams: GenreFilterMapper.SearchParams,
        tasteByKey: Map<MangaTasteKey, MangaTaste>,
        visibility: RatedMangaVisibility,
        seenKeys: Set<SeenMangaKey>,
        profile: TasteProfile,
        aliasMap: Map<String, String>,
        minChapterCount: Int,
        // KMK --> v0.7.41 follow-up: hide-known context so extra-page candidates use the same
        // shared visibility policy as live page-one, cache, memory, and group recommendations
        hideKnownManga: Boolean,
        candidateBudget: Int,
        // KMK <--
    ): Triple<List<PersonalRecommendation>, Int, Int> {
        // KMK --> v0.7.40: planner now classifies retryable vs permanent failures
        val nowMs = clock()
        val nextPage = RecommendationDiscoveryPlanner.nextPageToProbe(progressRecords, nowMs)
            ?: return Triple(emptyList(), 0, 0)
        // KMK <--
        if (!currentCoroutineContext().isActive) return Triple(emptyList(), nextPage, 0)

        // KMK --> v0.7.40: carry forward attempt_count for the page being probed (for retry tracking)
        val existingRecord = progressRecords.find { it.page == nextPage }
        val baseAttemptCount = existingRecord?.attemptCount ?: 0
        // KMK <--

        var rawCount = 0
        var localizedCount = 0
        var scoredCount = 0
        var visibleCount = 0
        var filteredCount = 0
        var progressStatus = RecommendationDiscoveryProgress.STATUS_ERROR
        var errorMessage: String? = null
        // KMK --> v0.7.40: retry metadata defaults
        var failureKind: String? = null
        var nextRetryAt: Long? = null
        // KMK <--

        // KMK --> v0.7.40: bounded timeout on additional-page search only (not page-1)
        // v0.8.20: converted from runCatching to explicit try/catch -- the previous
        // runCatching { ... }.onFailure { if (e is CancellationException) throw e ... } looked safe,
        // but the two NESTED runCatching calls below (chapterCounts/knownIds) swallowed
        // CancellationException internally before it could ever reach this outer rethrow. Every
        // suspend boundary in this block now rethrows cancellation explicitly.
        val result = try {
            run innerRun@{
                // KMK v0.8.10-fix7: routed through SourceRuntime instead of a raw call inside
                // withContext -- registers a recoverable failure (including an extension
                // LinkageError) in SourceRuntimeFailureRegistry. getOrThrowSourceRuntimeException()
                // converts a recoverable failure into RecoverableSourceRuntimeException (an
                // Exception) rather than rethrowing a raw Error. A genuinely fatal error or
                // CancellationException still propagates directly out of SourceRuntime.run() itself.
                val pageResult = withTimeoutOrNull(ADDITIONAL_PAGE_TIMEOUT_MS) {
                    SourceRuntime.run(source, SourceRuntimeOperation.Search, coroutineDispatcher) {
                        getSearchManga(nextPage, searchParams.textQuery, searchParams.filters)
                    }.getOrThrowSourceRuntimeException()
                } ?: run {
                    // Explicit local probe timeout — a transient, retryable failure.
                    progressStatus = RecommendationDiscoveryProgress.STATUS_ERROR
                    failureKind = RecommendationDiscoveryProgress.FAILURE_KIND_RETRYABLE
                    errorMessage = "Additional-page search timed out after ${ADDITIONAL_PAGE_TIMEOUT_MS}ms"
                    return@innerRun Triple(emptyList(), nextPage, 0)
                }
                // KMK <--
                val rawSMangas = RecommendationRawCandidatePolicy.distinctWithinLimit(
                    pageResult.mangas,
                    candidateBudget.coerceAtMost(RecommendationDiscoveryPlanner.MAX_NEW_CANDIDATES_PER_DISCOVERY_PAGE),
                ) { it.url }

                rawCount = rawSMangas.size
                if (rawSMangas.isEmpty()) {
                    progressStatus = RecommendationDiscoveryProgress.STATUS_EMPTY
                    return@innerRun Triple(emptyList(), nextPage, 0)
                }

                val raw = rawSMangas.map { it.toDomainManga(source.id) }
                // KMK --> v0.7.40 Deliverable D: use shared visibility policy in extra-page path
                val allLocalized = networkToLocalManga(raw)
                val chapterCounts = if (minChapterCount > 0 && allLocalized.isNotEmpty()) {
                    try {
                        getChapterCounts.await(allLocalized.map { it.id })
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        emptyMap()
                    }
                } else {
                    emptyMap()
                }
                // KMK --> v0.7.41 follow-up: batch-load known IDs for extra-page candidates so this
                // page uses the same hide-known context as live page-one, cache, memory, and group
                // recommendations. Fail open (log + emptySet()) rather than crash or blank the row.
                val knownIds = if (hideKnownManga && allLocalized.isNotEmpty()) {
                    try {
                        getKnownMangaIds.await(allLocalized.map { it.id })
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logcat(LogPriority.WARN) { "Additional-page known-manga filter failed, keeping all candidates" }
                        emptySet()
                    }
                } else {
                    emptySet()
                }
                // KMK <--
                val localized = filterVisibleCandidates(
                    allLocalized,
                    tasteByKey,
                    visibility,
                    seenKeys,
                    knownIds,
                    minChapterCount,
                    chapterCounts,
                )
                // KMK <--

                localizedCount = localized.size
                filteredCount = rawCount - localizedCount

                val scored = PersonalRecommendationScorer.rankCandidates(
                    localized,
                    profile,
                    aliasMap,
                    rawSMangas.size,
                )
                scoredCount = scored.size

                val results = scored.map { PersonalRecommendation(it.manga, it.score, it.matchedGroups) }
                visibleCount = results.size
                progressStatus = when {
                    results.isNotEmpty() -> RecommendationDiscoveryProgress.STATUS_SUCCESS
                    rawCount > 0 -> RecommendationDiscoveryProgress.STATUS_FILTERED
                    else -> RecommendationDiscoveryProgress.STATUS_EMPTY
                }
                Triple(results, nextPage, rawSMangas.size)
            }
        } catch (e: CancellationException) {
            // Cancellation is never recorded as a failed retry — rethrow before any progress write.
            throw e
        } catch (e: Exception) {
            // KMK --> v0.7.40: classify failure kind for retry policy
            progressStatus = RecommendationDiscoveryProgress.STATUS_ERROR
            errorMessage = RecommendationErrorClassifier.classifyToStorageKey(e)
            failureKind = RecommendationRetryClassifier.classify(e)
            // KMK <--
            Triple(emptyList(), nextPage, 0)
        }

        // KMK --> v0.7.41: compute the terminal retry state after the probe resolves.
        // A retryable failure that just used its final allowed attempt persists as STATUS_EXHAUSTED
        // (terminal, retained diagnostic, nextRetryAt = null). Earlier retryable failures schedule a
        // backoff; permanent failures never retry.
        var attemptCount = baseAttemptCount
        if (progressStatus == RecommendationDiscoveryProgress.STATUS_ERROR) {
            attemptCount = (baseAttemptCount + 1).coerceAtMost(RecommendationRetryClassifier.MAX_ATTEMPTS)
            if (failureKind == RecommendationDiscoveryProgress.FAILURE_KIND_RETRYABLE) {
                if (attemptCount >= RecommendationRetryClassifier.MAX_ATTEMPTS) {
                    progressStatus = RecommendationDiscoveryProgress.STATUS_EXHAUSTED
                    nextRetryAt = null
                } else {
                    nextRetryAt = RecommendationRetryClassifier.nextRetryAt(baseAttemptCount, nowMs)
                }
            } else {
                // Permanent failure — no retry scheduled.
                nextRetryAt = null
            }
        }

        // Record progress with the resolved retry metadata.
        try {
            progressStore.recordProgress(
                sourceId = source.id,
                querySignature = queryKey,
                queryTags = topTags,
                queryStrategy = queryStrategy,
                page = nextPage,
                profileFingerprint = fingerprint,
                rawCount = rawCount,
                localizedCount = localizedCount,
                scoredCount = scoredCount,
                visibleCount = visibleCount,
                filteredCount = filteredCount,
                status = progressStatus,
                errorMessage = errorMessage,
                attemptCount = attemptCount,
                nextRetryAt = nextRetryAt,
                failureKind = failureKind,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
        }
        // KMK <--

        return result
    }

    // KMK <--

    @Composable
    fun getManga(initialManga: Manga): androidx.compose.runtime.State<Manga> {
        return produceState(initialValue = initialManga) {
            getMangaInteractor.subscribe(initialManga.url, initialManga.source)
                .filterNotNull()
                .collect { value = it }
        }
    }

    // KMK v0.8.16 -->
    /**
     * Bulk rate: reuses the same exclusive-rating write path as manga detail/Rated Manga screens.
     *
     * KMK v0.8.17-fix1: now suspends and returns a [BulkTasteActionOutcome] instead of firing-and-
     * forgetting inside `screenModelScope.launch` with a swallowing `runCatching` -- the caller (the
     * Tab) awaits this in its own coroutine scope and shows real success/failure feedback.
     * `SetMangaTasteBatch.await` writes every manga in one suspend call, so a failure is currently
     * all-or-nothing for this batch (not per-item) -- reported honestly as such below.
     */
    suspend fun rateSelected(manga: List<Manga>, rating: tachiyomi.domain.taste.model.MangaRating): BulkTasteOutcome {
        if (manga.isEmpty()) return BulkTasteOutcome(0, 0, 0)
        return try {
            // KMK v0.8.19: builds pre-write entries for the local Action History. Built before the
            // write so it captures the *previous* state, but only
            // committed to the journal after the write succeeds -- otherwise a failed write would leave
            // a stale entry that never actually happened.
            val journalActionType = when (rating) {
                tachiyomi.domain.taste.model.MangaRating.LOVE -> exh.util.EvaluationJournalActionType.RATE_LOVE
                tachiyomi.domain.taste.model.MangaRating.LIKE -> exh.util.EvaluationJournalActionType.RATE_LIKE
                tachiyomi.domain.taste.model.MangaRating.DISLIKE -> exh.util.EvaluationJournalActionType.RATE_DISLIKE
                // Bulk-rate selection never offers Not Interested as a rating choice (it has its own
                // bulk action elsewhere) -- kept exhaustive since MangaRating is the shared enum.
                tachiyomi.domain.taste.model.MangaRating.NOT_INTERESTED -> exh.util.EvaluationJournalActionType.NOT_INTERESTED
            }
            val journalEntries = exh.util.EvaluationModeJournalRecorder.buildRatingChange(getMangaTaste, manga, rating.value, journalActionType)
            // KMK v0.8.21-fix4: R1 correction -- this used to also remove/restore keys in the
            // legacy seenRecommendationMangaKeys preference around the setMangaTasteBatch write,
            // coordinating a rollback across two stores for a preference nothing reads live
            // anymore (Not Interested exclusion is MangaTaste-derived everywhere now, see
            // RecommendsScreenModel and this class's own `seenKeys` above). MangaTaste is the sole
            // rating-family authority; the legacy preference is migration/old-backup-restore-only.
            try {
                setMangaTasteBatch.await(manga, rating)
                exh.util.EvaluationModeJournalRecorder.commit(journalEntries)
                propagateConfirmedLocalTracking(manga)
                BulkTasteOutcome.success(manga.size)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BulkTasteOutcome.failed(manga.size)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BulkTasteOutcome.failed(manga.size)
        }
    }

    /**
     * Marks the selection Not Interested -- writes `MangaTaste.rating = NOT_INTERESTED` through
     * the same [setMangaTasteBatch] every other bulk rating uses, per [rateSelected].
     *
     * KMK v0.8.21-fix3: R1 correction -- this previously wrote ONLY the legacy
     * seenRecommendationMangaKeys preference and never touched MangaTaste, a dangling writer the
     * corrected architecture cannot allow.
     */
    suspend fun markSelectedNotInterested(manga: List<Manga>): BulkTasteOutcome {
        if (manga.isEmpty()) return BulkTasteOutcome(0, 0, 0)
        return try {
            val journalEntries = exh.util.EvaluationModeJournalRecorder.buildRatingChange(
                getMangaTaste,
                manga,
                tachiyomi.domain.taste.model.MangaRating.NOT_INTERESTED.value,
                exh.util.EvaluationJournalActionType.NOT_INTERESTED,
            )
            setMangaTasteBatch.await(manga, tachiyomi.domain.taste.model.MangaRating.NOT_INTERESTED)
            exh.util.EvaluationModeJournalRecorder.commit(journalEntries)
            propagateConfirmedLocalTracking(manga)
            BulkTasteOutcome.success(manga.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BulkTasteOutcome.failed(manga.size)
        }
    }

    // KMK v0.8.17-fix1: Clear Rating for For You selection -- reuses the same `ClearMangaTaste`
    // interactor manga detail's `MangaInfoHeader`/`MangaScreenModel.clearMangaTaste()` already use.
    // Deletes by (source, url) since selected recommendation candidates aren't guaranteed to carry a
    // persisted mangaId. Per-item try/catch so one failure doesn't abort the rest of the batch, and
    // cancellation is never swallowed (KMK v0.8.19: previously used runCatching, which also caught
    // CancellationException).
    suspend fun clearSelectedRatings(manga: List<Manga>): BulkTasteOutcome {
        if (manga.isEmpty()) return BulkTasteOutcome(0, 0, 0)
        // KMK v0.8.19: build pre-write entries for the local Action History journal. Each entry is
        // committed individually below, only for the manga whose clear write actually succeeded --
        // a per-item failure must not leave a stale journal entry.
        val journalEntries = exh.util.EvaluationModeJournalRecorder.buildRatingChange(
            getMangaTaste,
            manga,
            null,
            exh.util.EvaluationJournalActionType.CLEAR_RATING,
        )
        var successCount = 0
        var failureCount = 0
        manga.forEachIndexed { index, m ->
            try {
                clearMangaTaste.await(m.source, m.url)
                successCount++
                journalEntries.getOrNull(index)?.let { exh.util.EvaluationModeJournalRecorder.commit(listOf(it)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failureCount++
            }
        }
        return BulkTasteOutcome(requestedCount = manga.size, successCount = successCount, failureCount = failureCount)
    }

    private suspend fun propagateConfirmedLocalTracking(manga: List<Manga>) {
        if (!sourcePreferences.confirmedTrackedVersionLocalTrackingPropagationEnabled().get()) return
        manga.distinctBy { it.source to it.url }.forEach { origin ->
            confirmedGroupLocalTrackingPropagator.propagateIfAnyTracked(
                confirmedMangaGroupTargets.await(origin),
            )
        }
    }
    // KMK <--

    private fun updateItem(
        refreshGeneration: Long,
        source: Source,
        result: PersonalRecommendationResult,
        status: RecommendationSourceRunStatus? = null,
    ) {
        if (!RecommendationRefreshGenerationPolicy.shouldAccept(
                candidateGeneration = refreshGeneration,
                activeGeneration = activeRefreshGeneration.get(),
            )
        ) {
            return
        }

        val (rowResult, detailResult) = synchronized(accumulatorLock) {
            if (!RecommendationRefreshGenerationPolicy.shouldAccept(
                    candidateGeneration = refreshGeneration,
                    activeGeneration = activeRefreshGeneration.get(),
                )
            ) {
                return
            }
            if (result is PersonalRecommendationResult.Success && result.result.isNotEmpty()) {
                combinedAccumulator.add(result.result, source.id)
            }
            val rowRanked = combinedAccumulator.rank(currentBoostedSourceIds, TOP_PICKS_ROW_CAP)
            val detailRanked = combinedAccumulator.rank(currentBoostedSourceIds, TOP_PICKS_DETAIL_CAP)
            Pair(
                if (rowRanked.isEmpty()) null else PersonalRecommendationResult.Success(rowRanked),
                if (detailRanked.isEmpty()) null else PersonalRecommendationResult.Success(detailRanked),
            )
        }
        mutableState.update { state ->
            if (!RecommendationRefreshGenerationPolicy.shouldAccept(
                    candidateGeneration = refreshGeneration,
                    activeGeneration = activeRefreshGeneration.get(),
                )
            ) {
                return@update state
            }
            state.copy(
                items = state.items.mutate { it[source] = result },
                combinedResult = rowResult,
                combinedDetailResult = detailResult,
                sourceStatuses = if (status != null) {
                    state.sourceStatuses.mutate { it[source.id] = status }
                } else {
                    state.sourceStatuses
                },
            )
        }
    }

    // KMK v0.8.14-fix1 -->
    /**
     * Builds and persists a read-only preview snapshot for Recommendation Settings' "For You sources"
     * screen -- Top Picks first (if visible), then each visible source row in priority order, each
     * capped defensively. Only persists when at least one row has visible manga, so a run that ended
     * with nothing to show never overwrites a previous good snapshot with an empty one. Never touches a
     * source, the network, or install/update logic -- this runs after the real fetch already completed.
     */
    private fun persistForYouPreviewSnapshot(dedupedMap: PersistentMap<Source, PersonalRecommendationResult>) {
        val rows = mutableListOf<RecommendationForYouPreviewRow>()

        (mutableState.value.combinedResult as? PersonalRecommendationResult.Success)?.let { topPicks ->
            if (topPicks.result.isNotEmpty()) {
                rows += RecommendationForYouPreviewRow(
                    rowKey = "top_picks",
                    rowType = RecommendationForYouPreviewRowType.TOP_PICKS,
                    title = "",
                    subtitle = "",
                    mangas = topPicks.result.map { it.toPreviewManga() },
                )
            }
        }

        for (source in mutableState.value.sourceOrder) {
            val result = dedupedMap[source] as? PersonalRecommendationResult.Success ?: continue
            if (result.isEmpty) continue
            rows += RecommendationForYouPreviewRow(
                rowKey = source.id.toString(),
                rowType = RecommendationForYouPreviewRowType.SOURCE,
                // KMK --> v0.8.19: evaluation mode source-name obfuscation
                title = if (sourcePreferences.evaluationMode().get()) {
                    EvaluationModeFormatter.sourceLabel(source.id)
                } else {
                    source.name
                },
                // KMK <--
                subtitle = "",
                mangas = result.result.map { it.toPreviewManga() },
            )
        }

        if (rows.isEmpty()) return

        sourcePreferences.recommendationForYouPreviewSnapshot().set(
            RecommendationForYouPreviewSnapshotStore.serialize(
                RecommendationForYouPreviewSnapshot(capturedAt = clock(), rows = rows),
            ),
        )
    }

    private fun PersonalRecommendation.toPreviewManga() = RecommendationForYouPreviewManga(
        mangaId = manga.id,
        sourceId = manga.source,
        url = manga.url,
        title = manga.title,
        thumbnailUrl = manga.thumbnailUrl,
    )
    // KMK <--

    @Immutable
    data class State(
        val items: PersistentMap<Source, PersonalRecommendationResult> = persistentMapOf(),
        val searchContexts: PersistentMap<Long, RecommendationSearchContext> = persistentMapOf(),
        // KMK -->
        /** Sources in priority order — used to render For You rows in the correct sequence. */
        val sourceOrder: PersistentList<Source> = persistentListOf(),
        /** Top Picks row — up to [TOP_PICKS_ROW_CAP] results derived from all fetched source results. */
        val combinedResult: PersonalRecommendationResult? = null,
        /** Full Top Picks detail — up to [TOP_PICKS_DETAIL_CAP] results for the drill-down screen. */
        val combinedDetailResult: PersonalRecommendationResult? = null,
        /** Per-source last-run statuses — updated as each source completes. */
        val sourceStatuses: PersistentMap<Long, RecommendationSourceRunStatus> = persistentMapOf(),
        // KMK <--
        val isLoading: Boolean = true,
        val profileIsEmpty: Boolean = false,
        // KMK --> v0.7.25: true when device has no internet at load time
        val isOffline: Boolean = false,
        // KMK <--
        // bumped exactly once per
        // load()/refresh() call, at the same point `items` is reset to empty. The Tab keys its
        // exposure-recording LaunchedEffect on this value (plus "every source finished") so a stale
        // in-flight recording coroutine from a superseded refresh is cancelled, and recomposition
        // alone (no generation change) never re-records.
        val resultGeneration: Long = 0,
        // Shared focus-criteria alias map (see
        // buildFocusAliasMap) and the known-group universe (see buildFocusKnownGroups), both
        // computed once per refresh() from GetTagAliases -- not limited to whatever genres happen
        // to be in the currently-loaded result set. Threaded into every production
        // RecommendationFocusPresentationPolicy.apply call so alias-aware focus filtering is
        // actually exercised in Top Picks and every source lane, not just in isolated policy tests.
        val focusAliasMap: Map<String, String> = emptyMap(),
        val focusKnownGroups: Set<String> = emptySet(),
    ) {
        val progress: Int = items.count { it.value !is PersonalRecommendationResult.Loading }
        val total: Int = items.size

        fun dedupedItems(): PersistentMap<Source, PersonalRecommendationResult> {
            val winner = mutableMapOf<String, PersonalRecommendation>()
            items.values.forEach { result ->
                if (result is PersonalRecommendationResult.Success) {
                    result.result.forEach { rec ->
                        val key = normalizedTitleKey(rec.manga.title)
                        val current = winner[key]
                        if (current == null || isBetterRec(rec, current)) winner[key] = rec
                    }
                }
            }
            return items.mapValues { (_, result) ->
                if (result is PersonalRecommendationResult.Success) {
                    PersonalRecommendationResult.Success(
                        result.result.filter { rec ->
                            winner[normalizedTitleKey(rec.manga.title)] === rec
                        },
                    )
                } else {
                    result
                }
            }.toPersistentMap()
        }
    }

    companion object {
        private const val MAX_VISIBLE_SOURCE_ROWS = 20
        private const val MAX_SOURCE_ATTEMPTS = 40
        private const val BOOSTED_SOURCE_COUNT = 3
        private const val SOURCE_BATCH_SIZE = 5
        // KMK v0.8.2: replaced by ForYouResultBudgetPolicy.resolve(configuredValue, isBoosted) —
        // the visible-card budget per row is now user-configurable (SourcePreferences
        // .recommendationResultBudget()), with the boosted floor (formerly BOOSTED_RESULTS_PER_SOURCE
        // = 20) preserved as ForYouResultBudgetPolicy.BOOSTED_MINIMUM.
        private const val NORMAL_ENRICHMENT_LIMIT = 5
        private const val BOOSTED_ENRICHMENT_LIMIT = 10
        private const val RAW_CANDIDATE_MULTIPLIER = 3
        private const val MAX_SEARCH_TAGS = 5
        private const val MAX_REASON_TAGS = 4
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
        // KMK --> v0.7.43: Not Interested mild-negative signal — much weaker than Dislike (-2.0)
        private const val NOT_INTERESTED_WEIGHT = -0.3
        private const val NOT_INTERESTED_LOOKUP_CAP = 150
        // KMK <--
        // KMK -->
        /** Maximum recommendations shown in the inline Top Picks row. */
        private const val TOP_PICKS_ROW_CAP = 20
        /** Maximum recommendations shown in the Top Picks drill-down screen. */
        private const val TOP_PICKS_DETAIL_CAP = 50
        // KMK --> v0.7.40: bounded timeout for additional-page discovery only
        private const val ADDITIONAL_PAGE_TIMEOUT_MS = 20_000L
        // KMK <--

        /** Deterministic tie-break: higher score > more genres > lower manga id. */
        private fun isBetterRec(a: PersonalRecommendation, b: PersonalRecommendation): Boolean {
            if (a.score != b.score) return a.score > b.score
            val aGenres = a.manga.genre?.size ?: 0
            val bGenres = b.manga.genre?.size ?: 0
            if (aGenres != bGenres) return aGenres > bGenres
            return a.manga.id < b.manga.id
        }
    }
}
// KMK <--

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
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceRuntime
import eu.kanade.tachiyomi.source.SourceRuntimeOperation
import eu.kanade.tachiyomi.source.getOrThrowSourceRuntimeException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.rethrowIfFatal
import eu.kanade.tachiyomi.util.export.SafArtifactOutcome
import eu.kanade.tachiyomi.util.export.SafExportCoordinator
import eu.kanade.tachiyomi.util.system.isOnline
import eu.kanade.tachiyomi.util.system.toast
import exh.recs.RecommendationCandidateEnricher.Companion.needsEnrichment
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
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
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.security.MessageDigest

data class PersonalRecommendation(
    val manga: Manga,
    val score: Double,
    val matchedGroups: List<String>,
    // KMK -->
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
// KMK <--

private fun normalizedTitleKey(title: String): String =
    title.lowercase()
        .replace(Regex("""\([^)]*\)|\[[^\]]*\]"""), "")
        .replace(Regex("[^a-z0-9]"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

class BrowsePersonalRecommendationsScreenModel(
    // KMK --> v0.7.25: connectivity check before starting For You queries
    private val context: Context = Injekt.get<Application>(),
    // KMK: tests can construct the real
    // ScreenModel without starting the Android/network load before collaborators are ready.
    private val autoLoad: Boolean = true,
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
    // KMK <--
    // KMK: batched tracker lookup so a tracked title is
    // genuinely exempt from the exposure penalty. Fails open (see resolveTrackedExposureKeys).
    private val getTracks: tachiyomi.domain.track.interactor.GetTracks = Injekt.get(),
    // KMK: local-only For You exposure
    // history (soft display reordering only -- see RecommendationExposureRepository KDoc)
    private val getRecommendationExposure: tachiyomi.domain.taste.interactor.GetRecommendationExposure = Injekt.get(),
    private val recordRecommendationExposure: tachiyomi.domain.taste.interactor.RecordRecommendationExposure = Injekt.get(),
    private val pruneRecommendationExposure: tachiyomi.domain.taste.interactor.PruneRecommendationExposure = Injekt.get(),
) : StateScreenModel<BrowsePersonalRecommendationsScreenModel.State>(State()) {

    private val coroutineDispatcher = kotlinx.coroutines.Dispatchers.IO.limitedParallelism(5)

    // KMK:
    // screenModelScope-owned, not Composable-`remember`-owned.
    val exportCoordinator = SafExportCoordinator()

    // KMK: [snapshot],
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

    @Volatile private var currentBoostedSourceIds: Set<Long> = emptySet()

    // KMK
    /**
     * Latest-lane attempt budget for the *current* refresh, resolved once in [load] from
     * [RecommendationLatestBudgetPolicy] and consumed by [tryLatestCatalogueLane].
     *
     * [latestAttemptsUsed] is an [AtomicInteger] because sources within a batch are searched
     * concurrently (`batch.map { async { searchSource(...) } }`), so several coroutines can reach the
     * lane at once; a plain `Int` would let them collectively overshoot the budget. Both are reset at
     * the start of every refresh so a budget is per-refresh, never cumulative across refreshes.
     */
    @Volatile private var latestExplorationBudget: Int = 0
    private val latestAttemptsUsed = java.util.concurrent.atomic.AtomicInteger(0)
    // KMK <--

    // KMK -->
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
            screenModelScope.launch { load(forceRefresh = false) }
        }
    }

    fun refresh() {
        screenModelScope.launch { load(forceRefresh = true) }
    }

    // KMK -->
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
                recordRecommendationExposure.await(keys, mangaIds, System.currentTimeMillis())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failed exposure write must never change or block the visible result -- it has
                // already been shown by the time this runs.
            }
        }
    }
    // KMK <--

    private suspend fun load(forceRefresh: Boolean) {
        // KMK --> v0.7.25: reset offline state when starting a new load
        mutableState.update { it.copy(isLoading = true, profileIsEmpty = false, isOffline = false) }

        if (!context.isOnline()) {
            mutableState.update { State(isLoading = false, isOffline = true) }
            return
        }
        // KMK <--

        clearRecommendationCache.awaitExpired()
        // KMK: prune exposure rows older
        // than the configured window plus its grace period on every refresh -- ordering history only,
        // never touches ratings/library/manga/taste. A failed prune must never block the refresh.
        try {
            val windowDays = RecommendationExposurePolicy.validateWindowDays(
                sourcePreferences.recommendationExposureWindowDays().get(),
            )
            pruneRecommendationExposure.await(RecommendationExposurePolicy.pruneBefore(System.currentTimeMillis(), windowDays))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
        }

        val profile = getTasteProfile.await()
        if (profile.isEmpty()) {
            mutableState.update { State(isLoading = false, profileIsEmpty = true) }
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
            mutableState.update { State(isLoading = false, profileIsEmpty = true) }
            return
        }

        val aliasCandidates = buildAliasCandidates(topTags, groupToAliases)

        val recommendationLanguages = sourcePreferences.recommendationSourceLanguages().get()
        val storedOrder = RecommendationSourceOrdering.parse(sourcePreferences.recommendationSourceOrder().get())
        // KMK --> v0.7.40: use shared selector (language filter + ordering + disabled exclusion)
        val orderedEnabledSources = RecommendationSourceSelector.select(
            sources = sourceManager.getVisibleSources(),
            languages = recommendationLanguages,
            storedOrder = storedOrder,
            effectiveDisabledIds = effectiveDisabledIds,
        )
        // KMK <--

        // Boosted sources are fixed: always the top BOOSTED_SOURCE_COUNT eligible sources
        val boostedSourceIds = orderedEnabledSources.take(BOOSTED_SOURCE_COUNT).map { it.id }.toSet()

        synchronized(accumulatorLock) {
            combinedAccumulator.clear()
            currentBoostedSourceIds = boostedSourceIds
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
        // KMK --> v0.6.20: seen manga keys always filter from For You regardless of hideKnownManga
        val seenKeys = SeenRecommendationMangaStore.parse(
            sourcePreferences.seenRecommendationMangaKeys().get(),
        )
        val seenMangaCount = seenKeys.size
        // KMK <--
        // KMK --> v0.7.43: Not Interested (internal storage still "seen") becomes a mild negative
        // signal — similar candidates are slightly deprioritized, much weaker than Dislike. Query
        // tag selection above (topTags) intentionally still uses the raw, unadjusted profile; only
        // scoring/ranking uses scoringProfile.
        val scoringProfile = buildNotInterestedAdjustedProfile(profile, seenKeys, aliasMap)
        // KMK <--
        // KMK --> v0.7.26: minimum locally-known chapter count filter (0 = off)
        // KMK: resolved through the shared
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

        val allTastes = getMangaTaste.awaitAll()
        val tasteByKey = allTastes.associate { MangaTasteKey(it.source, it.url) to it }
        val visibility = sourcePreferences.recommendationRatedMangaVisibility().get()
        val fallbackQuery = topTags.take(3).joinToString(" ")

        // Reset UI state — sources will be added in batches
        mutableState.update {
            it.copy(
                items = persistentMapOf(),
                sourceOrder = persistentListOf(),
                combinedResult = null,
                combinedDetailResult = null,
                sourceStatuses = persistentMapOf(),
                searchContexts = persistentMapOf(),
                isLoading = false,
                profileIsEmpty = false,
                // KMK: a new generation
                // invalidates the Tab's exposure-recording effect for the previous refresh.
                resultGeneration = it.resultGeneration + 1,
            )
        }

        searchJob?.cancel()
        searchJob = ioCoroutineScope.launch {
            val allStatuses = mutableMapOf<Long, RecommendationSourceRunStatus>()
            var usefulCount = 0

            val candidateSources = orderedEnabledSources.take(MAX_SOURCE_ATTEMPTS)

            // KMK: resolve this refresh's bounded
            // Latest exploration budget from the number of sources actually about to be attempted,
            // and reset the counter so budgets never accumulate across refreshes. A user-configured
            // 0% resolves to 0 attempts, which disables the lane entirely without any other change.
            latestExplorationBudget = RecommendationLatestBudgetPolicy.resolveAttempts(
                configuredPercent = sourcePreferences.recommendationLatestExplorationPercent().get(),
                attemptedSourceCount = candidateSources.size,
            )
            latestAttemptsUsed.set(0)
            currentRefreshTimestamp = System.currentTimeMillis()

            for (batch in candidateSources.chunked(SOURCE_BATCH_SIZE)) {
                if (!isActive) break

                // Register batch in UI as Loading before searching
                val initialContexts = batch.associate { it.id to RecommendationSearchContext(fallbackQuery, topTags) }
                mutableState.update { state ->
                    state.copy(
                        items = state.items.mutate { map -> batch.forEach { src -> map[src] = PersonalRecommendationResult.Loading } },
                        sourceOrder = state.sourceOrder.addAll(batch),
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
                            // KMK <--
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
                    updateItem(outcome.source, outcome.result, outcome.status)
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
            }

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
        var lastError: Throwable? = null
        var hadRawResults = false

        for (plan in plans) {
            if (!currentCoroutineContext().isActive) {
                return SourceSearchOutcome(
                    source,
                    PersonalRecommendationResult.Success(emptyList()),
                    null,
                    RecommendationSourceRunStatus(source.id, RecommendationSourceStatus.NoMatches),
                    false,
                )
            }
            try {
                // KMK v0.8.10-fix4: routed through SourceRuntime instead of a local
                // try/catch(Exception) -- a recoverable failure (ordinary Exception or an extension
                // LinkageError such as the confirmed Asura Scans NoClassDefFoundError) now also
                // gets recorded in SourceRuntimeFailureRegistry, not just silently swallowed.
                val filterList = SourceRuntime.run(source, SourceRuntimeOperation.FilterList, coroutineDispatcher) {
                    getFilterList()
                }.getOrElse { FilterList() }
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
                val page = SourceRuntime.run(source, SourceRuntimeOperation.Search, coroutineDispatcher) {
                    getSearchManga(1, searchParams.textQuery, searchParams.filters)
                }.getOrThrowSourceRuntimeException()

                val rawSMangas = page.mangas.take(rawCap).distinctBy { it.url }
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

                if (recommendations.size < minUseful && plan != plans.last()) {
                    lastError = null
                    continue
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
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                }
                // KMK <--

                // KMK v0.8.13: do not probe additional pages for a query that already proved it
                // returns nothing raw on page 1 -- see RecommendationAdditionalPagePolicy's KDoc for
                // the confirmed source TEXT_ONLY_TOP_TAGS page-crawl evidence this prevents.
                val additionalResults = if (
                    RecommendationAdditionalPagePolicy.shouldDiscoverAdditionalPage(
                        planType = plan.type,
                        pageOneRawCount = rawSMangas.size,
                        pageOneVisibleCount = recommendations.size,
                        lastError = null,
                    )
                ) {
                    discoverAdditionalPage(
                        source = source,
                        progressRecords = progressRecords,
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
                    )
                } else {
                    emptyList<PersonalRecommendation>() to 0
                }
                if (additionalResults.first.isNotEmpty()) {
                    // memoryStore.upsertBatch already rethrows CancellationException and swallows
                    // ordinary exceptions internally; this try/catch only guards against a future
                    // change to that internal contract.
                    try {
                        memoryStore.upsertBatch(
                            sourceId = source.id,
                            querySignature = queryKey,
                            queryTags = topTags,
                            queryStrategy = plan.type.name,
                            page = additionalResults.second,
                            profileFingerprint = fingerprint,
                            recommendations = additionalResults.first,
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                    }
                }
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
                // KMK: bounded additive
                // Latest augmentation -- see tryAdditiveLatestAugmentation's KDoc for why this is the
                // structural fix for "Latest does not run when personalized results already exist".
                // Only ever reached from this success path (mutually exclusive with the
                // hadRawResults == false rescue path further below), and shares the same per-refresh
                // budget pool, so total Latest source-touches per refresh stay bounded either way.
                val latestAdditive = tryAdditiveLatestAugmentation(
                    source, queryKey, fingerprint, topTags, profile, aliasMap, tasteByKey, visibility,
                    seenKeys, hideKnownManga, minChapterCount, enrichLimit, displayLimit, rawCap,
                    excludeMangaIds = allNew.mapNotNull { it.manga.id.takeIf { id -> id != 0L } }.toSet(),
                )
                // KMK: bounded soft
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
                )
                return SourceSearchOutcome(
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
        }

        // KMK: bounded Latest exploration lane.
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
            remembered, hadRawResults, lastError,
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

    // KMK
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
    ): CatalogueFallbackOutcome {
        // Only offer Latest when the personalized chain produced nothing usable and did not itself
        // fail -- identical gating to the Popular fallback, so a source that already has personalized
        // results keeps them and a malfunctioning source is not poked again.
        if (!RecommendationCatalogueFallbackPolicy.shouldAttempt(hadRawResults, lastError != null) ||
            !currentCoroutineContext().isActive
        ) {
            return CatalogueFallbackOutcome(null, hadRawResults)
        }

        val budget = latestExplorationBudget
        val eligible = RecommendationCatalogueLanePolicy.shouldAttempt(
            supportsLatest = source.supportsLatest,
            sourceIsEligible = true,
            latestAttemptsUsed = latestAttemptsUsed.get(),
            latestBudget = budget,
        )
        if (!eligible) return CatalogueFallbackOutcome(null, hadRawResults)
        // Claim the budget slot before the call so concurrent per-source coroutines in the same batch
        // cannot collectively overshoot it.
        if (latestAttemptsUsed.getAndIncrement() >= budget) return CatalogueFallbackOutcome(null, hadRawResults)

        var rawFound = hadRawResults
        try {
            val pageResult = SourceRuntime.run(source, SourceRuntimeOperation.Latest, coroutineDispatcher) {
                getLatestUpdates(RecommendationCatalogueLanePolicy.LATEST_PAGE)
            }
            // A recoverable per-source failure (including UnsupportedOperationException from a source
            // that declared supportsLatest but never implemented it) is a bounded outcome, never an
            // error for this refresh -- the Popular fallback below still gets its turn.
            val page = pageResult.getOrElse { return CatalogueFallbackOutcome(null, rawFound) }

            val rawSMangas = page.mangas
                .filter { RecommendationCatalogueLanePolicy.isUsableEntry(it.url, it.title) }
                .take(rawCap)
                .distinctBy { it.url }
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

    // KMK -->
    /**
     * Bounded, additive Latest-catalogue augmentation for a source that **already** produced usable
     * personalized results in this refresh.
     *
     * ## Why this exists (structural fix, not a new feature)
     *
     * [tryLatestCatalogueLane] (added in the earlier implementation) is gated by
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
     * - **Per-refresh source-touch count:** shares the same [latestAttemptsUsed]/[latestExplorationBudget]
     *   pool [tryLatestCatalogueLane] uses. Only one of the two functions can ever run for a given
     *   source in a given refresh (they sit on mutually exclusive control-flow branches: this one only
     *   when personalized succeeded, the other only when it did not), so the shared budget is never
     *   double-spent per source and the refresh-wide ceiling still holds.
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
        excludeMangaIds: Set<Long>,
    ): List<PersonalRecommendation> {
        if (!currentCoroutineContext().isActive) return emptyList()

        val quota = RecommendationLatestBudgetPolicy.resolveAdditiveSlotsPerSource(
            displayLimit,
            sourcePreferences.recommendationLatestExplorationPercent().get(),
        )
        if (quota <= 0) return emptyList()

        val budget = latestExplorationBudget
        val eligible = RecommendationCatalogueLanePolicy.shouldAttempt(
            supportsLatest = source.supportsLatest,
            sourceIsEligible = true,
            latestAttemptsUsed = latestAttemptsUsed.get(),
            latestBudget = budget,
        )
        if (!eligible) return emptyList()
        // Claim the shared budget slot before the call, same contract as tryLatestCatalogueLane.
        if (latestAttemptsUsed.getAndIncrement() >= budget) return emptyList()

        return try {
            val pageResult = SourceRuntime.run(source, SourceRuntimeOperation.Latest, coroutineDispatcher) {
                getLatestUpdates(RecommendationCatalogueLanePolicy.LATEST_PAGE)
            }
            val page = pageResult.getOrElse { return emptyList() }

            val rawSMangas = page.mangas
                .filter { RecommendationCatalogueLanePolicy.isUsableEntry(it.url, it.title) }
                .take(rawCap)
                .distinctBy { it.url }
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
                // KMK: tag the lane so the shared merge can
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
        // KMK: optional exposure-aware
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
            exposureContext.exposureByKey, exposureContext.interactions, exposureContext.now, exposureContext.windowDays,
        )
    }

    // KMK -->
    /** Bundles the optional exposure-reranking inputs for [mergeFreshAndRememberedCandidates]. */
    private data class ExposureRerankContext(
        val exposureByKey: Map<RecommendationDisplayReranker.ExposureKey, RecommendationDisplayReranker.ExposureSummary>,
        val interactions: RecommendationDisplayReranker.InteractionSignals,
        val now: Long,
        val windowDays: Int,
    ) {
        companion object {
            val NONE = ExposureRerankContext(
                emptyMap(),
                RecommendationDisplayReranker.InteractionSignals.NONE,
                0L,
                RecommendationExposurePolicy.DEFAULT_WINDOW_DAYS,
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
     * KMK: the tracker check used to be omitted entirely
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
        return ExposureRerankContext(
            exposureByKey,
            RecommendationDisplayReranker.InteractionSignals(libraryKeys, ratedKeys, trackedState),
            currentRefreshTimestamp,
            windowDays,
        )
    }

    /**
     * One batched tracker lookup for this source's candidates, returning a **tri-state**.
     *
     * KMK: this previously returned a bare `Set`, collapsing
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
            val rawSMangas = page.mangas.take(rawCap).distinctBy { it.url }
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

        val now = System.currentTimeMillis()
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
        val now = System.currentTimeMillis()
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
     * for genre groups seen on manga the user marked Not Interested (internal storage: "seen").
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
        // KMK <--
    ): Pair<List<PersonalRecommendation>, Int> {
        // KMK --> v0.7.40: planner now classifies retryable vs permanent failures
        val nowMs = System.currentTimeMillis()
        val nextPage = RecommendationDiscoveryPlanner.nextPageToProbe(progressRecords, nowMs)
            ?: return emptyList<PersonalRecommendation>() to 0
        // KMK <--
        if (!currentCoroutineContext().isActive) return emptyList<PersonalRecommendation>() to nextPage

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
                    return@innerRun emptyList<PersonalRecommendation>() to nextPage
                }
                // KMK <--
                val rawSMangas = pageResult.mangas
                    .take(RecommendationDiscoveryPlanner.MAX_NEW_CANDIDATES_PER_DISCOVERY_PAGE)
                    .distinctBy { it.url }

                rawCount = rawSMangas.size
                if (rawSMangas.isEmpty()) {
                    progressStatus = RecommendationDiscoveryProgress.STATUS_EMPTY
                    return@innerRun emptyList<PersonalRecommendation>() to nextPage
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
                    RecommendationDiscoveryPlanner.MAX_NEW_CANDIDATES_PER_DISCOVERY_PAGE,
                )
                scoredCount = scored.size

                val results = scored.map { PersonalRecommendation(it.manga, it.score, it.matchedGroups) }
                visibleCount = results.size
                progressStatus = when {
                    results.isNotEmpty() -> RecommendationDiscoveryProgress.STATUS_SUCCESS
                    rawCount > 0 -> RecommendationDiscoveryProgress.STATUS_FILTERED
                    else -> RecommendationDiscoveryProgress.STATUS_EMPTY
                }
                results to nextPage
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
            emptyList<PersonalRecommendation>() to nextPage
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
            // KMK v0.8.19: builds pre-write journal entries for Evaluation Mode's Undo Journal (no-op
            // when disabled). Built before the write so it captures the *previous* state, but only
            // committed to the journal after the write succeeds -- otherwise a failed write would leave
            // a stale entry that never actually happened.
            val journalActionType = when (rating) {
                tachiyomi.domain.taste.model.MangaRating.LOVE -> exh.util.EvaluationJournalActionType.RATE_LOVE
                tachiyomi.domain.taste.model.MangaRating.LIKE -> exh.util.EvaluationJournalActionType.RATE_LIKE
                tachiyomi.domain.taste.model.MangaRating.DISLIKE -> exh.util.EvaluationJournalActionType.RATE_DISLIKE
            }
            val journalEntries = exh.util.EvaluationModeJournalRecorder.buildRatingChange(sourcePreferences, getMangaTaste, manga, rating.value, journalActionType)
            setMangaTasteBatch.await(manga, rating)
            exh.util.EvaluationModeJournalRecorder.commit(journalEntries)
            BulkTasteOutcome.success(manga.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BulkTasteOutcome.failed(manga.size)
        }
    }

    /**
     * Mild negative signal -- same "not interested" store used by manga detail/Rated Manga screens.
     * KMK v0.8.17-fix1: suspends and reports outcome, same reasoning as [rateSelected].
     */
    suspend fun markSelectedNotInterested(manga: List<Manga>): BulkTasteOutcome {
        if (manga.isEmpty()) return BulkTasteOutcome(0, 0, 0)
        return try {
            // KMK v0.8.19: build pre-write journal entries for Evaluation Mode's Undo Journal (no-op if
            // disabled), commit only after the write below succeeds.
            val journalEntries = exh.util.EvaluationModeJournalRecorder.buildNotInterested(sourcePreferences, getMangaTaste, manga)
            val raw = sourcePreferences.seenRecommendationMangaKeys().get()
            var seen = SeenRecommendationMangaStore.parse(raw)
            manga.forEach { m -> seen = SeenRecommendationMangaStore.add(seen, SeenMangaKey(m.source, m.url)) }
            sourcePreferences.seenRecommendationMangaKeys().set(SeenRecommendationMangaStore.serialize(seen))
            exh.util.EvaluationModeJournalRecorder.commit(journalEntries)
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
        // KMK v0.8.19: build pre-write journal entries for Evaluation Mode's Undo Journal (no-op if
        // disabled). Each entry is committed individually below, only for the manga whose clear write
        // actually succeeded -- a per-item failure must not leave a stale journal entry.
        val journalEntries = exh.util.EvaluationModeJournalRecorder.buildRatingChange(
            sourcePreferences,
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
    // KMK <--

    private fun updateItem(
        source: Source,
        result: PersonalRecommendationResult,
        status: RecommendationSourceRunStatus? = null,
    ) {
        val (rowResult, detailResult) = synchronized(accumulatorLock) {
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
                RecommendationForYouPreviewSnapshot(capturedAt = System.currentTimeMillis(), rows = rows),
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
        // KMK: bumped exactly once per
        // load()/refresh() call, at the same point `items` is reset to empty. The Tab keys its
        // exposure-recording LaunchedEffect on this value (plus "every source finished") so a stale
        // in-flight recording coroutine from a superseded refresh is cancelled, and recomposition
        // alone (no generation change) never re-records.
        val resultGeneration: Long = 0,
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

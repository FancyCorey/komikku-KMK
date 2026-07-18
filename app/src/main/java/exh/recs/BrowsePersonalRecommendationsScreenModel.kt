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
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.system.isOnline
import exh.recs.RecommendationCandidateEnricher.Companion.needsEnrichment
import exh.recs.memory.RecommendationCandidateMemoryEntry
import exh.recs.memory.RecommendationCandidateMemoryRanker
import exh.recs.memory.RecommendationCandidateMemoryStore
import exh.recs.memory.RecommendationDiscoveryPlanner
import exh.recs.memory.RecommendationDiscoveryProgressStore
import exh.recs.memory.RecommendationRetryClassifier
import exh.recs.sources.GenreFilterMapper
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
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.security.MessageDigest

data class PersonalRecommendation(
    val manga: Manga,
    val score: Double,
    val matchedGroups: List<String>,
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
) : StateScreenModel<BrowsePersonalRecommendationsScreenModel.State>(State()) {

    private val coroutineDispatcher = kotlinx.coroutines.Dispatchers.IO.limitedParallelism(5)
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

    /** Outcome returned by [searchSource] — carries result, status, and strategy for the caller. */
    private data class SourceSearchOutcome(
        val source: Source,
        val result: PersonalRecommendationResult,
        val successfulStrategy: RecommendationQueryStrategyType?,
        val status: RecommendationSourceRunStatus,
        val isUseful: Boolean,
    )

    init {
        screenModelScope.launch { load(forceRefresh = false) }
    }

    fun refresh() {
        screenModelScope.launch { load(forceRefresh = true) }
    }

    private suspend fun load(forceRefresh: Boolean) {
        // KMK --> v0.7.25: reset offline state when starting a new load
        mutableState.update { it.copy(isLoading = true, profileIsEmpty = false, isOffline = false) }

        if (!context.isOnline()) {
            mutableState.update { State(isLoading = false, isOffline = true) }
            return
        }
        // KMK <--

        clearRecommendationCache.awaitExpired()

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
        val minChapterCount = sourcePreferences.recommendationMinChapterCount().get()
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
            )
        }

        searchJob?.cancel()
        searchJob = ioCoroutineScope.launch {
            val allStatuses = mutableMapOf<Long, RecommendationSourceRunStatus>()
            var usefulCount = 0

            val candidateSources = orderedEnabledSources.take(MAX_SOURCE_ATTEMPTS)
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
        // KMK --> v0.7.26: minimum locally-known chapter count (0 = off)
        minChapterCount: Int = 0,
        // KMK <--
        // KMK v0.8.2: user-configured visible-card budget (raw preference value; validated inside
        // ForYouResultBudgetPolicy.resolve() below, never trusted directly).
        resultBudget: Int = ForYouResultBudgetPolicy.DEFAULT,
    ): SourceSearchOutcome {
        // KMK --> v0.7.38: load remembered candidates before cache check so they are available for merge
        val remembered = runCatching { memoryStore.loadForSourceQuery(source.id, queryKey) }.getOrDefault(emptyList())
        // KMK <--
        // KMK --> v0.7.40: load full progress records so the planner can classify retryable failures
        val progressRecords = runCatching { progressStore.progressRecords(source.id, queryKey) }.getOrDefault(emptyList())
        // KMK <--

        if (!forceRefresh) {
            val cached = loadFromCache(source.id, queryKey, fingerprint, tasteByKey, visibility, hideKnownManga, seenKeys, minChapterCount)
            if (cached != null) {
                // KMK --> v0.7.38: merge cached page-1 results with remembered candidates from all pages
                val displayLimit = ForYouResultBudgetPolicy.resolve(resultBudget, isBoosted) // KMK v0.8.2
                val resolvedMemory = resolveMemoryEntries(remembered)
                val knownIdsForFilter = if (hideKnownManga && (cached.isNotEmpty() || resolvedMemory.isNotEmpty())) {
                    val allMangaIds = (cached.map { it.manga.id } + resolvedMemory.map { it.first.id }).distinct()
                    runCatching { getKnownMangaIds.await(allMangaIds) }.getOrElse { emptySet() }
                } else {
                    emptySet()
                }
                // KMK --> v0.7.41: batch chapter counts across cached + memory so memory candidates
                // are min-chapter filtered by the same shared policy as cached candidates.
                val chapterCountsForFilter = if (minChapterCount > 0 && (cached.isNotEmpty() || resolvedMemory.isNotEmpty())) {
                    val allMangaIds = (cached.map { it.manga.id } + resolvedMemory.map { it.first.id }).distinct()
                    runCatching { getChapterCounts.await(allMangaIds) }.getOrElse { error ->
                        logcat(LogPriority.WARN, error) { "Cached-merge chapter-count lookup failed, skipping min-chapter filter" }
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

        val plans = RecommendationQueryPlanner.buildPlans(topTags, lastStrategy)
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

                // KMK v0.8.10-fix4: routed through SourceRuntime instead of a raw call inside
                // withContext -- .getOrThrow() rethrows a recoverable failure as a normal exception
                // for the existing outer catch(Exception)/catch(Error) below to record as lastError;
                // a genuinely fatal error or CancellationException already propagated directly out
                // of SourceRuntime.run() itself, before ever reaching this line.
                val page = SourceRuntime.run(source, SourceRuntimeOperation.Search, coroutineDispatcher) {
                    getSearchManga(1, searchParams.textQuery, searchParams.filters)
                }.getOrThrow()

                val rawSMangas = page.mangas.take(rawCap).distinctBy { it.url }
                if (rawSMangas.isNotEmpty()) hadRawResults = true

                val smangaByUrl = rawSMangas.associateBy { it.url }
                val raw = rawSMangas.map { it.toDomainManga(source.id) }
                // KMK --> v0.7.40 Deliverable D: use shared visibility policy in live path
                val rawLocalized = networkToLocalManga(raw)
                val chapterCounts = if (minChapterCount > 0 && rawLocalized.isNotEmpty()) {
                    runCatching {
                        getChapterCounts.await(rawLocalized.map { it.id })
                    }.getOrElse { error ->
                        logcat(LogPriority.WARN, error) { "Chapter count lookup failed, skipping min-chapter filter" }
                        emptyMap()
                    }
                } else {
                    emptyMap()
                }
                val knownIds = if (hideKnownManga && rawLocalized.isNotEmpty()) {
                    runCatching {
                        getKnownMangaIds.await(rawLocalized.map { it.id })
                    }.getOrElse { error ->
                        logcat(LogPriority.WARN, error) { "Top Picks known-manga filter failed, keeping all candidates" }
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
                val localized = saved // alias for enrichment below
                // KMK <--

                val enriched = enricher.enrich(source, saved, smangaByUrl, enrichLimit)

                val scored = PersonalRecommendationScorer.rankCandidates(
                    enriched,
                    profile,
                    aliasMap,
                    displayLimit,
                )
                val recommendations = scored.map { sc ->
                    PersonalRecommendation(sc.manga, sc.score, sc.matchedGroups)
                }

                if (recommendations.size < minUseful && plan != plans.last()) {
                    lastError = null
                    continue
                }

                val reason = recommendations.flatMap { it.matchedGroups }
                    .distinct().take(MAX_REASON_TAGS).joinToString(", ").ifBlank { null }
                saveToCache(source.id, queryKey, fingerprint, recommendations.map { it.manga }, recommendations.map { it.score }, reason)

                // KMK --> v0.7.38: upsert page-1 results to memory + try additional page
                runCatching {
                    memoryStore.upsertBatch(
                        sourceId = source.id,
                        querySignature = queryKey,
                        queryTags = topTags,
                        queryStrategy = plan.type.name,
                        page = 1,
                        profileFingerprint = fingerprint,
                        recommendations = recommendations,
                    )
                }
                // KMK --> v0.7.39: record page-1 progress so the planner can advance to page 2
                runCatching {
                    progressStore.recordProgress(
                        sourceId = source.id,
                        querySignature = queryKey,
                        queryTags = topTags,
                        queryStrategy = plan.type.name,
                        page = 1,
                        profileFingerprint = fingerprint,
                        rawCount = rawSMangas.size,
                        localizedCount = localized.size,
                        scoredCount = scored.size,
                        visibleCount = recommendations.size,
                        filteredCount = rawSMangas.size - localized.size,
                        status = when {
                            recommendations.isNotEmpty() -> RecommendationDiscoveryProgress.STATUS_SUCCESS
                            rawSMangas.isNotEmpty() -> RecommendationDiscoveryProgress.STATUS_FILTERED
                            else -> RecommendationDiscoveryProgress.STATUS_EMPTY
                        },
                    )
                }
                // KMK <--

                val additionalResults = discoverAdditionalPage(
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
                if (additionalResults.first.isNotEmpty()) {
                    runCatching {
                        memoryStore.upsertBatch(
                            sourceId = source.id,
                            querySignature = queryKey,
                            queryTags = topTags,
                            queryStrategy = plan.type.name,
                            page = additionalResults.second,
                            profileFingerprint = fingerprint,
                            recommendations = additionalResults.first,
                        )
                    }
                }
                runCatching { memoryStore.pruneIfNeeded(source.id) }

                val resolvedMemory = resolveMemoryEntries(remembered)
                val allNew = recommendations + additionalResults.first
                // KMK --> v0.7.41 follow-up: additionalResults.first is now already known-filtered
                // inside discoverAdditionalPage (it uses the same hide-known context as page one), so
                // a second known-id lookup over those candidates here would always be a no-op. knownIds
                // (page-1) is sufficient for the merge; no redundant DB round-trip is needed.
                val allKnownIds = knownIds
                // KMK <--
                // KMK --> v0.7.41: batch chapter counts across memory + all new candidates so remembered
                // memory candidates obey the same min-chapter policy as freshly discovered ones.
                val chapterCountsForMerge = if (minChapterCount > 0 && (allNew.isNotEmpty() || resolvedMemory.isNotEmpty())) {
                    val allMangaIds = (allNew.map { it.manga.id } + resolvedMemory.map { it.first.id }).distinct()
                    runCatching { getChapterCounts.await(allMangaIds) }.getOrElse { error ->
                        logcat(LogPriority.WARN, error) { "Live-merge chapter-count lookup failed, skipping min-chapter filter" }
                        emptyMap()
                    }
                } else {
                    emptyMap()
                }
                // KMK <--
                // Always merge so extra-page candidates compete for display even when memory is empty.
                val mergedRecommendations = RecommendationCandidateMemoryRanker.merge(
                    resolvedMemory, allNew, profile, aliasMap,
                    tasteByKey, visibility, seenKeys, allKnownIds, displayLimit,
                    // KMK --> v0.7.41: apply min-chapter policy in the merge too
                    minChapterCount, chapterCountsForMerge,
                    // KMK <--
                )
                // KMK <--

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
            } catch (e: Exception) {
                lastError = e
            } catch (e: Error) {
                // KMK v0.8.10-fix4: this Error catch is now purely defensive bookkeeping, not the
                // structural boundary -- both source calls above go through SourceRuntime.run(),
                // which already rethrows CancellationException and any genuinely fatal Error
                // directly (they never reach this catch clause at all); only a recoverable
                // extension-linkage failure can still surface here, via .getOrThrow() on the search
                // call. Recorded the same way as an ordinary Exception.
                lastError = e
            }
        }

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
            runCatching {
                getKnownMangaIds.await(resolved.map { it.first.id })
            }.getOrElse { error ->
                logcat(LogPriority.WARN, error) { "Top Picks cached known-manga filter failed, keeping cached candidates" }
                emptySet()
            }
        } else {
            emptySet()
        }

        // KMK --> v0.7.41: batched chapter counts only when the minimum is active; fail open on error
        val chapterCounts = if (minChapterCount > 0) {
            runCatching {
                getChapterCounts.await(resolved.map { it.first.id })
            }.getOrElse { error ->
                logcat(LogPriority.WARN, error) { "Cached chapter-count lookup failed, skipping min-chapter filter" }
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
            val manga = runCatching { getMangaInteractor.await(key.url, key.sourceId) }.getOrNull() ?: continue
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
            val manga = if (entry.mangaId != null) {
                runCatching { getMangaInteractor.await(entry.mangaId) }.getOrNull()
            } else {
                runCatching { getMangaInteractor.await(entry.url, entry.sourceId) }.getOrNull()
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
        val result = runCatching {
            // KMK v0.8.10-fix4: routed through SourceRuntime instead of a raw call inside
            // withContext -- registers a recoverable failure (including an extension LinkageError)
            // in SourceRuntimeFailureRegistry instead of only relying on this function's outer
            // runCatching, which does not record anything. .getOrThrow() rethrows a recoverable
            // failure for the existing timeout/runCatching handling below to treat exactly as
            // before; a genuinely fatal error already propagated directly out of
            // SourceRuntime.run() itself.
            val pageResult = withTimeoutOrNull(ADDITIONAL_PAGE_TIMEOUT_MS) {
                SourceRuntime.run(source, SourceRuntimeOperation.Search, coroutineDispatcher) {
                    getSearchManga(nextPage, searchParams.textQuery, searchParams.filters)
                }.getOrThrow()
            } ?: run {
                // Explicit local probe timeout — a transient, retryable failure.
                progressStatus = RecommendationDiscoveryProgress.STATUS_ERROR
                failureKind = RecommendationDiscoveryProgress.FAILURE_KIND_RETRYABLE
                errorMessage = "Additional-page search timed out after ${ADDITIONAL_PAGE_TIMEOUT_MS}ms"
                return@runCatching emptyList<PersonalRecommendation>() to nextPage
            }
            // KMK <--
            val rawSMangas = pageResult.mangas
                .take(RecommendationDiscoveryPlanner.MAX_NEW_CANDIDATES_PER_DISCOVERY_PAGE)
                .distinctBy { it.url }

            rawCount = rawSMangas.size
            if (rawSMangas.isEmpty()) {
                progressStatus = RecommendationDiscoveryProgress.STATUS_EMPTY
                return@runCatching emptyList<PersonalRecommendation>() to nextPage
            }

            val raw = rawSMangas.map { it.toDomainManga(source.id) }
            // KMK --> v0.7.40 Deliverable D: use shared visibility policy in extra-page path
            val allLocalized = networkToLocalManga(raw)
            val chapterCounts = if (minChapterCount > 0 && allLocalized.isNotEmpty()) {
                runCatching { getChapterCounts.await(allLocalized.map { it.id }) }.getOrDefault(emptyMap())
            } else {
                emptyMap()
            }
            // KMK --> v0.7.41 follow-up: batch-load known IDs for extra-page candidates so this
            // page uses the same hide-known context as live page-one, cache, memory, and group
            // recommendations. Fail open (log + emptySet()) rather than crash or blank the row.
            val knownIds = if (hideKnownManga && allLocalized.isNotEmpty()) {
                runCatching {
                    getKnownMangaIds.await(allLocalized.map { it.id })
                }.getOrElse { error ->
                    logcat(LogPriority.WARN, error) { "Additional-page known-manga filter failed, keeping all candidates" }
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
        }.onFailure { e ->
            // Cancellation is never recorded as a failed retry — rethrow before any progress write.
            if (e is CancellationException) throw e
            // KMK --> v0.7.40: classify failure kind for retry policy
            progressStatus = RecommendationDiscoveryProgress.STATUS_ERROR
            errorMessage = e.message?.take(200)
            failureKind = RecommendationRetryClassifier.classify(e)
            // KMK <--
        }.getOrDefault(emptyList<PersonalRecommendation>() to nextPage)

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
        runCatching {
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

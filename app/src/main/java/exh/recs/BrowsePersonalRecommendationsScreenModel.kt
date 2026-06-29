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
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.system.isOnline
import exh.recs.RecommendationCandidateEnricher.Companion.needsEnrichment
import exh.recs.sources.GenreFilterMapper
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.taste.interactor.ClearRecommendationCache
import tachiyomi.domain.taste.interactor.GetDisabledRecommendationSources
import tachiyomi.domain.taste.interactor.GetChapterCountsByMangaIds
import tachiyomi.domain.taste.interactor.GetKnownRecommendationMangaIds
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.interactor.GetRecommendationCache
import tachiyomi.domain.taste.interactor.GetTagAliases
import tachiyomi.domain.taste.interactor.GetTasteProfile
import tachiyomi.domain.taste.interactor.UpsertRecommendationCache
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaTaste
import tachiyomi.domain.taste.model.RatedMangaVisibility
import tachiyomi.domain.taste.model.RecommendationCacheEntry
import tachiyomi.domain.taste.model.TasteProfile
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
) : StateScreenModel<BrowsePersonalRecommendationsScreenModel.State>(State()) {

    private val coroutineDispatcher = kotlinx.coroutines.Dispatchers.IO.limitedParallelism(5)
    private var searchJob: Job? = null
    private val enricher = RecommendationCandidateEnricher(networkToLocalManga, coroutineDispatcher)

    // Top Picks accumulator — guarded by accumulatorLock for concurrent source updates
    private val accumulatorLock = Any()
    private val combinedAccumulator = CombinedPicksAccumulator()

    @Volatile private var currentBoostedSourceIds: Set<Long> = emptySet()

    /** Outcome returned by [searchSource] — carries result, status, and strategy for the caller. */
    private data class SourceSearchOutcome(
        val source: CatalogueSource,
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
        val effectiveDisabledIds = disabledSourceIds + dislikedInstalledIds
        // KMK <--

        val topTags = topSearchTags(profile)
        if (topTags.isEmpty()) {
            mutableState.update { State(isLoading = false, profileIsEmpty = true) }
            return
        }

        val aliasCandidates = buildAliasCandidates(topTags, groupToAliases)

        val recommendationLanguages = sourcePreferences.recommendationSourceLanguages().get()
        val languageFilteredSources = RecommendationSourceFilter.filterForRecommendations(
            sources = sourceManager.getVisibleCatalogueSources(),
            languages = recommendationLanguages,
        )
        val storedOrder = RecommendationSourceOrdering.parse(sourcePreferences.recommendationSourceOrder().get())
        val orderedEnabledSources = RecommendationSourceOrdering.apply(
            visibleSources = languageFilteredSources,
            storedOrder = storedOrder,
            disabledSourceIds = effectiveDisabledIds,
        )

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
        // KMK --> v0.7.26: minimum locally-known chapter count filter (0 = off)
        val minChapterCount = sourcePreferences.recommendationMinChapterCount().get()
        // KMK <--
        val fingerprint = profileFingerprint(topTags, profile, aliasMap, effectiveDisabledIds, storedOrder, recommendationLanguages, hideKnownManga, seenMangaCount, minChapterCount)
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
                            source, queryKey, fingerprint, topTags, profile, aliasMap, aliasCandidates,
                            tasteByKey, visibility, hideKnownManga, seenKeys, forceRefresh,
                            source.id in boostedSourceIds,
                            lastStrategy = strategyMap[source.id],
                            // KMK --> v0.7.26
                            minChapterCount = minChapterCount,
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
            val updatedFitStats = SourceFitStatsStore.mergeRun(currentFitStats, allStatuses.values)
            sourcePreferences.recommendationSourceFitStats().set(SourceFitStatsStore.serialize(updatedFitStats.values))
            // KMK <--
        }
    }

    /**
     * Searches a single source using the query planner. Returns a [SourceSearchOutcome] describing
     * the result, diagnostic status, and the successful query strategy (if any).
     */
    private suspend fun searchSource(
        source: CatalogueSource,
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
    ): SourceSearchOutcome {
        if (!forceRefresh) {
            val cached = loadFromCache(source.id, queryKey, fingerprint, tasteByKey, visibility, hideKnownManga, seenKeys)
            if (cached != null) {
                val status = RecommendationSourceRunStatus(
                    sourceId = source.id,
                    status = if (cached.isNotEmpty()) RecommendationSourceStatus.Shown else RecommendationSourceStatus.NoMatches,
                    visibleCount = cached.size,
                )
                return SourceSearchOutcome(
                    source = source,
                    result = PersonalRecommendationResult.Success(cached),
                    successfulStrategy = null,
                    status = status,
                    isUseful = cached.isNotEmpty(),
                )
            }
        }

        val displayLimit = if (isBoosted) BOOSTED_RESULTS_PER_SOURCE else NORMAL_RESULTS_PER_SOURCE
        val enrichLimit = if (isBoosted) BOOSTED_ENRICHMENT_LIMIT else NORMAL_ENRICHMENT_LIMIT
        val rawCap = displayLimit * RAW_CANDIDATE_MULTIPLIER
        val minUseful = if (isBoosted) {
            RecommendationQueryPlanner.MIN_USEFUL_RESULTS_BOOSTED
        } else {
            RecommendationQueryPlanner.MIN_USEFUL_RESULTS_NORMAL
        }

        val plans = RecommendationQueryPlanner.buildPlans(topTags, lastStrategy)
        var lastError: Exception? = null
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
                val filterList = withContext(coroutineDispatcher) {
                    try {
                        source.getFilterList()
                    } catch (_: Exception) {
                        FilterList()
                    }
                }
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

                val page = withContext(coroutineDispatcher) {
                    source.getSearchManga(1, searchParams.textQuery, searchParams.filters)
                }

                val rawSMangas = page.mangas.take(rawCap).distinctBy { it.url }
                if (rawSMangas.isNotEmpty()) hadRawResults = true

                val smangaByUrl = rawSMangas.associateBy { it.url }
                val raw = rawSMangas.map { it.toDomainManga(source.id) }
                val localized = networkToLocalManga(raw)
                    .filterNot { it.favorite || shouldHideForYou(it, tasteByKey, visibility) }
                    // KMK --> v0.6.20: always exclude seen manga regardless of hideKnownManga
                    .filterNot { SeenMangaKey(it.source, it.url) in seenKeys }
                // KMK <--

                // KMK --> v0.7.26: filter manga whose locally-known chapter count is below threshold
                val chapterFiltered = if (minChapterCount > 0 && localized.isNotEmpty()) {
                    val counts = runCatching {
                        getChapterCounts.await(localized.map { it.id })
                    }.getOrElse { error ->
                        logcat(LogPriority.WARN, error) { "Chapter count lookup failed, skipping min-chapter filter" }
                        emptyMap()
                    }
                    localized.filterNot { manga ->
                        val count = counts[manga.id] ?: 0L
                        count > 0L && count < minChapterCount  // known count and below threshold
                    }
                } else {
                    localized
                }
                // KMK <--

                val knownIds = if (hideKnownManga && chapterFiltered.isNotEmpty()) {
                    runCatching {
                        getKnownMangaIds.await(chapterFiltered.map { it.id })
                    }.getOrElse { error ->
                        logcat(LogPriority.WARN, error) { "Top Picks known-manga filter failed, keeping all candidates" }
                        emptySet()
                    }
                } else {
                    emptySet()
                }
                val saved = if (knownIds.isEmpty()) chapterFiltered else chapterFiltered.filterNot { it.id in knownIds }

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

                val status = RecommendationSourceRunStatus(
                    sourceId = source.id,
                    status = if (recommendations.isNotEmpty()) {
                        RecommendationSourceStatus.Shown
                    } else if (hadRawResults) {
                        RecommendationSourceStatus.FilteredOut
                    } else {
                        RecommendationSourceStatus.NoMatches
                    },
                    visibleCount = recommendations.size,
                )
                return SourceSearchOutcome(
                    source = source,
                    result = PersonalRecommendationResult.Success(recommendations),
                    successfulStrategy = plan.type,
                    status = status,
                    isUseful = recommendations.isNotEmpty(),
                )
            } catch (e: Exception) {
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

        val resolved = ids.mapIndexedNotNull { index, id ->
            val manga = getMangaInteractor.await(id) ?: return@mapIndexedNotNull null
            if (manga.favorite || shouldHideForYou(manga, tasteByKey, visibility)) return@mapIndexedNotNull null
            // KMK --> v0.6.20: exclude seen manga
            if (SeenMangaKey(manga.source, manga.url) in seenKeys) return@mapIndexedNotNull null
            // KMK <--
            manga to scores.getOrElse(index) { 0.0 }
        }

        val knownIds = if (hideKnownManga && resolved.isNotEmpty()) {
            runCatching {
                getKnownMangaIds.await(resolved.map { it.first.id })
            }.getOrElse { error ->
                logcat(LogPriority.WARN, error) { "Top Picks cached known-manga filter failed, keeping cached candidates" }
                emptySet()
            }
        } else {
            emptySet()
        }

        return resolved
            .filterNot { (manga, _) -> manga.id in knownIds }
            .map { (manga, score) -> PersonalRecommendation(manga, score, cachedGroups) }
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

    private fun buildAliasCandidates(
        topTags: List<String>,
        groupToAliases: Map<String, List<String>>,
    ): Map<String, List<String>> = topTags.associate { tag ->
        val userAliases = groupToAliases[tag].orEmpty()
        val builtIn = GenreFilterMapper.BUILT_IN_SYNONYMS[tag].orEmpty()
        tag to (userAliases + builtIn).distinct()
    }

    @Composable
    fun getManga(initialManga: Manga): androidx.compose.runtime.State<Manga> {
        return produceState(initialValue = initialManga) {
            getMangaInteractor.subscribe(initialManga.url, initialManga.source)
                .filterNotNull()
                .collect { value = it }
        }
    }

    private fun updateItem(
        source: CatalogueSource,
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
        val items: PersistentMap<CatalogueSource, PersonalRecommendationResult> = persistentMapOf(),
        val searchContexts: PersistentMap<Long, RecommendationSearchContext> = persistentMapOf(),
        // KMK -->
        /** Sources in priority order — used to render For You rows in the correct sequence. */
        val sourceOrder: PersistentList<CatalogueSource> = persistentListOf(),
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

        fun dedupedItems(): PersistentMap<CatalogueSource, PersonalRecommendationResult> {
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
        private const val NORMAL_RESULTS_PER_SOURCE = 10
        private const val BOOSTED_RESULTS_PER_SOURCE = 20
        private const val NORMAL_ENRICHMENT_LIMIT = 5
        private const val BOOSTED_ENRICHMENT_LIMIT = 10
        private const val RAW_CANDIDATE_MULTIPLIER = 3
        private const val MAX_SEARCH_TAGS = 5
        private const val MAX_REASON_TAGS = 4
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
        // KMK -->
        /** Maximum recommendations shown in the inline Top Picks row. */
        private const val TOP_PICKS_ROW_CAP = 20
        /** Maximum recommendations shown in the Top Picks drill-down screen. */
        private const val TOP_PICKS_DETAIL_CAP = 50
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

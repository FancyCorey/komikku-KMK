package exh.recs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.source.isRecoverableSourceRuntimeFailure
import eu.kanade.tachiyomi.source.unwrapSourceRuntimeCause
import exh.recs.group.GroupRecommendationSeed
import exh.recs.group.GroupRecommendationSeedBuilder
import exh.recs.group.GroupSeedRecommendationScorer
import exh.recs.sourceprefs.RecommendationSourcePreferenceStore
import exh.recs.sources.RECOMMENDS_SOURCE
import exh.recs.sources.RecommendationPagingSource
import exh.recs.sources.RecommendationSource
import exh.recs.sources.StaticResultPagingSource
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
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
import tachiyomi.domain.taste.interactor.GetChapterCountsByMangaIds
import tachiyomi.domain.taste.interactor.GetDisabledRecommendationSources
import tachiyomi.domain.taste.interactor.GetKnownRecommendationMangaIds
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.model.MangaTaste
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

open class RecommendsScreenModel(
    private val args: RecommendsScreen.Args,
    private val getManga: GetManga = Injekt.get(),
    protected val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    // KMK --> v0.7.43: group-seeded recommendations (Loved/Liked group, row-based like single-manga)
    private val seedBuilder: GroupRecommendationSeedBuilder = GroupRecommendationSeedBuilder(),
    private val getTagAliases: tachiyomi.domain.taste.interactor.GetTagAliases = Injekt.get(),
    private val sourcePreferences: eu.kanade.domain.source.service.SourcePreferences = Injekt.get(),
    // KMK <--
    // KMK --> v0.7.44: group recommendations now honor the same source-selection and candidate
    // visibility policy as For You (Phase E). Only used when args is CrossSourceGroupSeed.
    private val sourceManager: SourceManager = Injekt.get(),
    private val getDisabledSources: GetDisabledRecommendationSources = Injekt.get(),
    private val getMangaTaste: GetMangaTaste = Injekt.get(),
    private val getKnownMangaIds: GetKnownRecommendationMangaIds = Injekt.get(),
    private val getChapterCounts: GetChapterCountsByMangaIds = Injekt.get(),
    // KMK <--
) : StateScreenModel<RecommendsScreenModel.State>(State()) {

    companion object {
        // KMK --> v0.7.44
        /** Mirrors RecommendationPagingSource.MAX_CROSS_EXTENSION_SOURCES (private there). */
        private const val MAX_CROSS_EXTENSION_SOURCES = 20

        /**
         * Minimum combined RecommendationScorer + GroupSeedRecommendationScorer score for a group
         * candidate to be considered relevant enough to display, when the seed has real tag
         * evidence. Deliberately low — this only removes near-zero-signal noise, not borderline
         * results; sorting still ranks the remaining candidates by score.
         */
        private const val GROUP_RELEVANCE_MIN_SCORE = 0.1
        // KMK <--

        private const val TAG = "RecommendsScreenModel"
    }

    private val coroutineDispatcher = Dispatchers.IO.limitedParallelism(5)

    // KMK v0.8.6: bounded concurrency + timeout for GROUP_PREVIEW row fetch/enrichment only,
    // extracted into GroupPreviewLoadCoordinator (see that class for the full contract/tests).
    // Single-manga and merged-source rows never call into this coordinator.
    private val groupPreviewCoordinator = GroupPreviewLoadCoordinator()

    // KMK v0.8.6: load generation guard, extracted into GenerationGuard (see that class for tests).
    // A future refresh/reload entry point (none exists yet — this screen model currently only loads
    // once in `init{}`) can safely supersede an in-flight load using this same instance.
    private val generationGuard = GenerationGuard()

    private val sortComparator = { map: Map<RecommendationPagingSource, RecommendationItemResult> ->
        compareBy<RecommendationPagingSource>(
            { (map[it] as? RecommendationItemResult.Success)?.isEmpty ?: true },
            { it.name },
            { it.category.resourceId },
        )
    }

    init {
        // KMK v0.8.6: switched from ioCoroutineScope (a process-wide scope not tied to this screen's
        // lifecycle) to screenModelScope (cancelled automatically when this ScreenModel is disposed,
        // e.g. leaving the screen), matching the existing convention in
        // BrowsePersonalRecommendationsScreenModel. This directly satisfies documented behavior:
        // "leaving the screen cancels in-flight preview work."
        val myGeneration = generationGuard.next()
        val loadStartMs = System.currentTimeMillis()
        screenModelScope.launch {
            // KMK -->
            var sourceManga: tachiyomi.domain.manga.model.Manga? = null
            // KMK --> v0.7.43: group seed — null except for CrossSourceGroupSeed
            var groupSeed: GroupRecommendationSeed? = null
            // KMK <--
            // KMK v0.8.6: hoisted out of the CrossSourceGroupSeed branch below so the GROUP_PREVIEW
            // cache-key fingerprint (built once per load, further down) can include the same
            // disabled/disliked/quality-disliked source state and source order that already govern
            // which sources are eligible and how candidates are filtered — a cached result must miss
            // once any of these change. Empty/single-manga path leaves these at their defaults.
            var effectiveDisabledSourceIds: Set<Long> = emptySet()
            var storedSourceOrder: List<Long> = emptyList()
            var dislikedSourceRaw = ""
            var qualityDislikedSourceRaw = ""
            val recommendationSources = when (args) {
                is RecommendsScreen.Args.SingleSourceManga -> {
                    val manga = getManga.await(args.mangaId) ?: return@launch
                    mutableState.update { it.copy(title = manga.title, primaryMangaId = manga.id) }
                    sourceManga = manga

                    RecommendationPagingSource.createSources(
                        manga,
                        RecommendationSource(args.sourceId),
                    )
                }
                is RecommendsScreen.Args.MergedSourceMangas -> {
                    args.mergedResults.map(::StaticResultPagingSource)
                }
                // KMK --> v0.7.43: build the group seed from every confirmed linked version, then
                // reuse the exact same single-manga provider/extension row pipeline as above —
                // the only differences are the seed genre/title lists fed to cross-extension rows,
                // eligible-source selection, candidate visibility, and scoring (below).
                is RecommendsScreen.Args.CrossSourceGroupSeed -> {
                    val manga = getManga.await(args.url, args.sourceId) ?: return@launch
                    val seed = seedBuilder.build(args.sourceId, args.url, args.primaryTitle)
                    groupSeed = seed
                    mutableState.update { it.copy(title = args.primaryTitle, primaryMangaId = manga.id) }
                    sourceManga = manga

                    // KMK --> v0.7.44 Phase E: cross-extension rows honor the same source-selection
                    // policy as For You (recommendation languages, priority order, disabled/disliked
                    // sources) instead of raw visible-source order. Single-manga recommendations are
                    // intentionally unaffected — RecommendationPagingSource.createSources only uses
                    // this when eligibleCrossExtensionSources is passed.
                    val recommendationLanguages = sourcePreferences.recommendationSourceLanguages().get()
                    val disabledSourceIds = getDisabledSources.await().toSet()
                    val dislikedRaw = sourcePreferences.dislikedRecommendationSourceKeys().get()
                    val dislikedInstalledIds = RecommendationSourcePreferenceStore
                        .installedSourceIds(RecommendationSourcePreferenceStore.parse(dislikedRaw))
                    // KMK v0.8.1-fix4: source/library-quality dislikes also exclude installed sources here
                    val qualityDislikedRaw = sourcePreferences.dislikedSourceQualityKeys().get()
                    val qualityDislikedInstalledIds = RecommendationSourcePreferenceStore
                        .installedSourceIds(RecommendationSourcePreferenceStore.parse(qualityDislikedRaw))
                    val effectiveDisabledIds = disabledSourceIds + dislikedInstalledIds + qualityDislikedInstalledIds
                    val storedOrder = RecommendationSourceOrdering.parse(sourcePreferences.recommendationSourceOrder().get())
                    // KMK v0.8.6: hoisted for the GROUP_PREVIEW cache fingerprint — see declaration above.
                    effectiveDisabledSourceIds = effectiveDisabledIds
                    storedSourceOrder = storedOrder
                    dislikedSourceRaw = dislikedRaw
                    qualityDislikedSourceRaw = qualityDislikedRaw
                    val eligibleSources = RecommendationSourceSelector.select(
                        sources = sourceManager.getVisibleSources(),
                        languages = recommendationLanguages,
                        storedOrder = storedOrder,
                        effectiveDisabledIds = effectiveDisabledIds,
                        maxSources = MAX_CROSS_EXTENSION_SOURCES,
                    )
                    // KMK <--

                    RecommendationPagingSource.createSources(
                        manga,
                        RecommendationSource(args.sourceId),
                        groupGenreOverride = seed.tags.takeIf { it.isNotEmpty() },
                        groupTitlesOverride = seed.titles.takeIf { it.isNotEmpty() },
                        eligibleCrossExtensionSources = eligibleSources,
                        // KMK v0.8.6: one shared enrichment semaphore for every cross-extension
                        // source created for this group load, so nested detail requests are bounded
                        // by one total budget, not per source. See GroupPreviewLoadCoordinator.
                        sharedEnrichmentSemaphore = groupPreviewCoordinator.sharedEnrichmentSemaphore,
                    )
                }
                // KMK <--
            }

            // KMK v0.8.6: stale-generation guard — if a newer load has already started (future
            // refresh entry point), this generation must never mutate state.
            if (!generationGuard.isCurrent(myGeneration)) return@launch

            updateItems(
                recommendationSources
                    .associateWith { RecommendationItemResult.Loading }
                    .toPersistentMap(),
            )

            // KMK --> v0.7.43: alias map for group-tag scoring, loaded once for the whole screen
            val aliasMap = if (groupSeed != null) {
                try {
                    getTagAliases.awaitAliasMap()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyMap()
                }
            } else {
                emptyMap()
            }
            // KMK <--
            // KMK --> v0.7.44 Phase E: group recommendations now go through the same shared
            // RecommendationCandidateVisibilityPolicy as For You (favorite/rated/seen/known/
            // min-chapter), not just seed-member/seen exclusion. Context is batch-loaded once for
            // the whole screen here; per-row known-id/chapter-count lookups are batched per row
            // (below) rather than per-candidate. Single-manga Recommendations never applied this
            // policy, so it stays scoped to the group-seed path to keep that behavior unchanged.
            val seenKeys = if (groupSeed != null) {
                SeenRecommendationMangaStore.parse(sourcePreferences.seenRecommendationMangaKeys().get())
            } else {
                emptySet()
            }
            val tasteByKey: Map<MangaTasteKey, MangaTaste> = if (groupSeed != null) {
                try {
                    getMangaTaste.awaitAll()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }.associateBy { MangaTasteKey(it.source, it.url) }
            } else {
                emptyMap()
            }
            val visibility = sourcePreferences.recommendationRatedMangaVisibility().get()
            val hideKnownManga = sourcePreferences.recommendationHideKnownManga().get()
            // KMK: same shared supported-value
            // resolution For You uses, so this screen and For You can never disagree about the
            // active threshold (and so it feeds the visibility fingerprint below as a legitimate value).
            val minChapterCount = RecommendationMinChapterCountPolicy.resolve(
                sourcePreferences.recommendationMinChapterCount().get(),
            )
            // KMK <--

            // KMK v0.8.6: single fingerprint of every recommendation-affecting input that is NOT
            // already part of GroupPreviewCache.Key's seed/source/language/budget fields — reviewer
            // finding: the cache key must invalidate on ALL of these, not just group/source/language/
            // budget. Computed once per load (not per row) since none of it is row-specific. See
            // GroupPreviewVisibilityFingerprint for the exact fields covered and why, and
            // GroupPreviewVisibilityFingerprintTest for the "changed input -> different fingerprint"
            // proof that does not require constructing this screen model.
            val groupPreviewVisibilityFingerprint = GroupPreviewVisibilityFingerprint.build(
                effectiveDisabledSourceIds = effectiveDisabledSourceIds,
                storedSourceOrder = storedSourceOrder,
                dislikedSourceRaw = dislikedSourceRaw,
                qualityDislikedSourceRaw = qualityDislikedSourceRaw,
                seenKeys = seenKeys,
                tasteByKey = tasteByKey,
                visibility = visibility,
                hideKnownManga = hideKnownManga,
                minChapterCount = minChapterCount,
            )

            recommendationSources.map { recSource ->
                async {
                    if (state.value.items[recSource] !is RecommendationItemResult.Loading) {
                        return@async
                    }

                    // KMK v0.8.6: GROUP_PREVIEW rows are bounded to GroupPreviewLoadCoordinator's
                    // concurrent-operation limit and per-row timeout; single-manga/merged rows are
                    // unaffected (isGroupPreview == false skips both, and never touches the cache).
                    val isGroupPreview = groupSeed != null
                    val rowStartMs = System.currentTimeMillis()

                    // KMK v0.8.6: GROUP_PREVIEW cache lookup, keyed per documented behavior Never
                    // consulted for single-manga/merged rows. A hit skips the source call entirely.
                    val cacheKey = if (isGroupPreview) {
                        val seed = requireNotNull(groupSeed)
                        val configuredBudget = GroupPreviewBudgetPolicy.previewCandidateBudget(
                            sourcePreferences.groupPreviewResultBudget().get(),
                        )
                        GroupPreviewCache.Key(
                            groupFingerprint = seed.groupId
                                ?: seed.memberKeys.sortedWith(compareBy({ it.first }, { it.second })).joinToString("|"),
                            sourceId = recSource.associatedSourceId ?: RECOMMENDS_SOURCE,
                            language = sourcePreferences.recommendationSourceLanguages().get().sorted().joinToString(","),
                            normalizedSeed = (seed.tags.sorted() + seed.titles.sorted()).joinToString("|"),
                            visibilityFingerprint = groupPreviewVisibilityFingerprint,
                            previewBudget = configuredBudget,
                        )
                    } else {
                        null
                    }

                    val cachedResult = cacheKey?.let { GroupPreviewCache.get(it) }
                    if (cachedResult != null) {
                        logcat(LogPriority.DEBUG, tag = TAG) {
                            "GROUP_PREVIEW cache HIT count=${cachedResult.size}"
                        }
                        if (isActive && generationGuard.isCurrent(myGeneration)) {
                            updateItem(recSource, RecommendationItemResult.Success(cachedResult))
                        }
                        return@async
                    } else if (cacheKey != null) {
                        logcat(LogPriority.DEBUG, tag = TAG) { "GROUP_PREVIEW cache MISS" }
                    }

                    try {
                        val page = if (isGroupPreview) {
                            groupPreviewCoordinator.runBounded {
                                withContext(coroutineDispatcher) {
                                    recSource.requestNextPage(1)
                                }
                            } ?: run {
                                logcat(LogPriority.WARN, tag = TAG) {
                                    "GROUP_PREVIEW timeout elapsedMs=${System.currentTimeMillis() - rowStartMs}"
                                }
                                if (isActive && generationGuard.isCurrent(myGeneration)) {
                                    updateItem(
                                        recSource,
                                        RecommendationItemResult.Error(
                                            java.util.concurrent.TimeoutException("Timed out after ${GroupPreviewLoadCoordinator.DEFAULT_TIMEOUT_MS}ms"),
                                        ),
                                    )
                                }
                                return@async
                            }
                        } else {
                            withContext(coroutineDispatcher) {
                                recSource.requestNextPage(1)
                            }
                        }

                        val recSourceId = recSource.associatedSourceId
                        val titles = if (recSourceId != null) {
                            // If the recommendation is associated with a source, resolve it
                            page.mangas.map { it.toDomainManga(recSourceId) }
                                .let { networkToLocalManga(it) }
                        } else {
                            // Otherwise, skip this step. The user will be prompted to choose a source via SmartSearch
                            page.mangas.map { it.toDomainManga(RECOMMENDS_SOURCE) }
                        }
                            .distinctBy { it.url }
                            // KMK --> v0.7.44 Phase E: group candidates now go through the full
                            // shared RecommendationCandidateVisibilityPolicy (seed member, favorite,
                            // rated, seen/not-interested, known, min-chapter) instead of only
                            // excluding seed members and exact Seen. known-id/chapter-count lookups
                            // are batched once per row, not per candidate.
                            .let { list ->
                                val seed = groupSeed
                                if (seed != null) {
                                    val knownIds = if (hideKnownManga) {
                                        try {
                                            getKnownMangaIds.await(list.map { it.id })
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            emptySet()
                                        }
                                    } else {
                                        emptySet()
                                    }
                                    val chapterCounts = if (minChapterCount > 0) {
                                        try {
                                            getChapterCounts.await(list.map { it.id })
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            emptyMap()
                                        }
                                    } else {
                                        emptyMap()
                                    }
                                    list.filter { candidate ->
                                        RecommendationCandidateVisibilityPolicy.evaluate(
                                            manga = candidate,
                                            tasteByKey = tasteByKey,
                                            visibility = visibility,
                                            seenKeys = seenKeys,
                                            knownIds = knownIds,
                                            minChapterCount = minChapterCount,
                                            chapterCounts = chapterCounts,
                                            seedMemberKeys = seed.memberKeys,
                                        ) == CandidateVisibility.VISIBLE
                                    }
                                } else {
                                    list
                                }
                            }
                            // KMK <--
                            // KMK -->
                            // Rank by similarity to the source manga (title + tag overlap), plus a
                            // group-tag overlap bonus when this is a group-seeded screen.
                            // Falls back to provider order when sourceManga is unavailable or
                            // when no similarity evidence distinguishes candidates.
                            .let { list ->
                                val ref = sourceManga
                                val seed = groupSeed
                                if (ref != null) {
                                    list.map { candidate ->
                                        candidate to (
                                            RecommendationScorer.score(ref, candidate) +
                                                (seed?.let { GroupSeedRecommendationScorer.score(candidate, it, aliasMap) } ?: 0.0)
                                            )
                                    }
                                        // KMK --> v0.7.44 Phase C.9: for group rows with real tag
                                        // signal, drop candidates with no meaningful relevance
                                        // (title AND tags both weak) instead of only sorting them
                                        // last — a near-zero score is noise, not a ranked result.
                                        // Single-manga rows are unaffected (seed == null).
                                        .let { scored ->
                                            if (seed != null && seed.seedTags.isNotEmpty()) {
                                                scored.filter { (_, score) -> score > GROUP_RELEVANCE_MIN_SCORE }
                                            } else {
                                                scored
                                            }
                                        }
                                        // KMK <--
                                        .sortedByDescending { (_, score) -> score }
                                        .map { (candidate, _) -> candidate }
                                } else {
                                    list
                                }
                            }
                        // KMK <--

                        // KMK v0.8.6: truncate to the configured GROUP_PREVIEW initial budget only —
                        // applied last, after fetch/dedupe/visibility/scoring, per documented behavior
                        // Full paging (opening the source row) is unaffected; single-manga rows never
                        // truncate here (isGroupPreview == false).
                        val budgeted = if (isGroupPreview) {
                            val budget = GroupPreviewBudgetPolicy.previewCandidateBudget(
                                sourcePreferences.groupPreviewResultBudget().get(),
                            )
                            titles.take(budget)
                        } else {
                            titles
                        }

                        // KMK v0.8.6: populate the GROUP_PREVIEW cache with the final, normalized,
                        // already-budgeted result. Never touched for single-manga/merged rows.
                        if (cacheKey != null) {
                            GroupPreviewCache.put(cacheKey, budgeted)
                        }

                        logcat(LogPriority.DEBUG, tag = TAG) {
                            "GROUP_PREVIEW completed rawCount=${titles.size} finalCount=${budgeted.size} elapsedMs=${System.currentTimeMillis() - rowStartMs}"
                        }

                        if (isActive && generationGuard.isCurrent(myGeneration)) {
                            updateItem(recSource, RecommendationItemResult.Success(budgeted))
                        }
                    } catch (e: CancellationException) {
                        // KMK v0.8.6: never render cancellation as a row error; always propagate so
                        // structured concurrency (leaving the screen, a new load) can observe it.
                        logcat(LogPriority.DEBUG, tag = TAG) { "GROUP_PREVIEW cancelled" }
                        throw e
                    } catch (e: Throwable) {
                        // KMK v0.8.6: fatal VM errors (OutOfMemoryError, StackOverflowError, etc.) are
                        // never treated as an ordinary recoverable source error.
                        // KMK v0.8.10-fix2: narrowed from "every Error is fatal" to "every Error
                        // except a recoverable LinkageError (broken/incompatible extension) is fatal"
                        // -- confirmed NoClassDefFoundError/okhttp3.zstd.Zstd root cause this fixes.
                        // KMK v0.8.10-fix4: this call site wraps recSource.requestNextPage(1), a
                        // polymorphic call across several RecommendationPagingSource/PagingSource
                        // implementations (CrossExtensionGenreSearchSource, RecommendationPagingSource
                        // backed by data-module SourcePagingSource, StaticResultPagingSource, etc), not
                        // a single raw Source method -- so it cannot be wrapped in SourceRuntime.run()
                        // itself; each of those implementations already routes its own direct Source
                        // calls through SourceRuntime.run() (app-module) or the shared core:common
                        // classifier (data-module, which cannot depend on app's SourceRuntime object).
                        // This outer catch now calls the same shared core:common classifier functions
                        // directly instead of going through the RecommendationErrorClassifier
                        // indirection, per the behavior contract's instruction to prefer direct SourceRuntime/
                        // classifier usage over delegation at call sites where feasible.
                        if (!e.unwrapSourceRuntimeCause().isRecoverableSourceRuntimeFailure()) throw e
                        logcat(LogPriority.WARN, tag = TAG) {
                            "GROUP_PREVIEW error elapsedMs=${System.currentTimeMillis() - rowStartMs}"
                        }
                        if (isActive && generationGuard.isCurrent(myGeneration)) {
                            updateItem(recSource, RecommendationItemResult.Error(e))
                        }
                    }
                }
            }.awaitAll()

            // KMK v0.8.6: total-elapsed and max-observed-concurrency diagnostics for this load.
            logcat(LogPriority.DEBUG, tag = TAG) {
                "load complete generation=$myGeneration groupPreview=${recommendationSources.size} " +
                    "totalElapsedMs=${System.currentTimeMillis() - loadStartMs} " +
                    "maxObservedConcurrency=${groupPreviewCoordinator.observedMaxConcurrency()}"
            }
        }
    }

    @Composable
    fun getManga(initialManga: Manga): androidx.compose.runtime.State<Manga> {
        return produceState(initialValue = initialManga) {
            getManga.subscribe(initialManga.url, initialManga.source)
                .filterNotNull()
                .collectLatest { manga ->
                    value = manga
                }
        }
    }

    private fun updateItems(items: PersistentMap<RecommendationPagingSource, RecommendationItemResult>) {
        mutableState.update {
            it.copy(
                items = items
                    .toSortedMap(sortComparator(items))
                    .toPersistentMap(),
            )
        }
    }

    private fun updateItem(source: RecommendationPagingSource, result: RecommendationItemResult) {
        val newItems = state.value.items.mutate {
            it[source] = result
        }
        updateItems(newItems)
    }

    @Immutable
    data class State(
        val title: String? = null,
        val items: PersistentMap<RecommendationPagingSource, RecommendationItemResult> = persistentMapOf(),
        // KMK --> v0.7.43: local manga id of the resolved primary manga (single-manga or group primary)
        val primaryMangaId: Long? = null,
        // KMK <--
    ) {
        val progress: Int = items.count { it.value !is RecommendationItemResult.Loading }
        val total: Int = items.size
        val filteredItems = items.filter { (_, result) -> result.isVisible(false) }
            .toImmutableMap()
    }
}

sealed interface RecommendationItemResult {
    data object Loading : RecommendationItemResult

    data class Error(
        val throwable: Throwable,
    ) : RecommendationItemResult

    data class Success(
        val result: List<Manga>,
    ) : RecommendationItemResult {
        val isEmpty: Boolean
            get() = result.isEmpty()
    }

    fun isVisible(onlyShowHasResults: Boolean): Boolean {
        return !onlyShowHasResults || (this is Success && !this.isEmpty)
    }
}

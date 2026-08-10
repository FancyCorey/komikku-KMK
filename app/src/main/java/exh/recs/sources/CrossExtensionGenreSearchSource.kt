package exh.recs.sources

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceRuntime
import eu.kanade.tachiyomi.source.SourceRuntimeFailureRegistry
import eu.kanade.tachiyomi.source.SourceRuntimeOperation
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import exh.recs.RecommendationQueryAttemptPolicy
import exh.recs.RecommendationQueryFailureKind
import exh.recs.RecommendationQueryPlan
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.source.NoResultsException
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.kmk.KMR
import xyz.nulldev.ts.api.http.serializer.FilterSerializer

// KMK -->
/**
 * A [RecommendationPagingSource] that searches a single installed [Source] by the
 * source manga's genres, then scores and sorts the results using [exh.recs.RecommendationScorer].
 *
 * One instance is created per visible catalogue source when the cross-extension search preference
 * is enabled. All instances run in the existing [exh.recs.RecommendsScreenModel] async/awaitAll
 * loop — no structural changes to the screen model are needed.
 *
 * When the user taps the row header on the recommendations screen, they are taken directly to
 * [eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen] with the genre filters
 * pre-applied via [cachedFiltersJson], rather than using the BrowseRecommendsScreen drill-down
 * (which cannot distinguish multiple instances of the same class by name).
 */
internal class CrossExtensionGenreSearchSource(
    manga: Manga,
    private val catalogueSource: Source,
    // KMK --> v0.7.43: when set (group-seeded recommendations), search by the combined/weighted
    // group tag list instead of this single manga's own genres.
    private val genreOverride: List<String>? = null,
    // KMK --> v0.7.44: when set (group-seeded recommendations), used as a bounded title fallback
    // (GroupRecommendationSeed.titles — combined/alternate titles from every linked version) once
    // every tag-based attempt in RecommendationQueryAttemptPolicy's chain has failed. Null (single
    // manga) falls back to manga.ogTitle only, same as before.
    private val titlesOverride: List<String>? = null,
    // KMK <--
    // KMK v0.8.6: when set (group-seeded/GROUP_PREVIEW recommendations only), every detail-request
    // ("enrichment") call made by this instance acquires a permit from this *shared* Semaphore
    // before the call and releases it in `finally`. The instance is shared across every
    // CrossExtensionGenreSearchSource created for the same group-recommendation load (passed down
    // from RecommendsScreenModel/GroupPreviewLoadCoordinator), so the total number of in-flight
    // detail requests is bounded across ALL concurrently active sources, not just per source — a
    // per-instance semaphore would not cap the cross-source total, since up to
    // GroupPreviewLoadCoordinator's own limit of sources can each be enriching MAX_ENRICH_PER_SOURCE
    // candidates at the same time. Null (single-manga path, no group seed) keeps the original
    // unbounded-within-this-source-only enrichment behavior.
    private val sharedEnrichmentSemaphore: Semaphore? = null,
) : RecommendationPagingSource(
    manga,
    RecommendationSource(catalogueSource.id),
) {
    override val name: String get() = catalogueSource.name
    override val category: StringResource get() = KMR.strings.rec_extension_search
    override val associatedSourceId: Long get() = catalogueSource.id

    /**
     * Serialized [FilterList] JSON, populated after [requestNextPage] completes.
     * Used by [exh.recs.RecommendsScreen] to open [eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen]
     * with genre filters pre-applied when the user taps the row header.
     */
    var cachedFiltersJson: String? = null
        private set

    /**
     * Text query fallback for genres that could not be mapped to filters.
     * Also passed to [eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen].
     */
    var cachedTextQuery: String = ""
        private set

    // KMK --> v0.7.44: last classified attempt outcome, for row-level diagnostics/logging. Null
    // until requestNextPage completes at least one attempt.
    var lastFailureKind: RecommendationQueryFailureKind? = null
        private set
    // KMK <--

    private val filterSerializer = FilterSerializer()

    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        // KMK v0.8.10-fix5: batch/global-flow proactive skip (Phase 7) -- only applies when this
        // instance is part of a bounded batch load (sharedEnrichmentSemaphore != null, i.e.
        // GROUP_PREVIEW/group recommendations), never a single-manga user-initiated open. Avoids
        // re-attempting a source that just failed with a recoverable linkage error within this same
        // process lifetime; advisory only -- does not persist or permanently block anything.
        if (sharedEnrichmentSemaphore != null &&
            SourceRuntimeFailureRegistry.isTemporarilyUnavailable(catalogueSource.id)
        ) {
            return MangasPage(emptyList(), false)
        }

        val desiredGenres = genreOverride ?: manga.genre.orEmpty()

        // KMK --> v0.7.44: strict-to-lenient tag attempt chain (shared with For You via
        // RecommendationQueryAttemptPolicy), then a bounded title fallback only if every tag
        // attempt failed to return any raw result.
        val chain = RecommendationQueryAttemptPolicy.buildTagAttemptChain(desiredGenres)
        for (plan in chain) {
            val page = tryAttempt(plan)
            if (page != null) return page
        }
        return runTitleFallback()
        // KMK <--
    }

    // KMK --> v0.7.44: one tag-based attempt; returns the page if useful, null if the caller
    // should try the next, more lenient attempt. Exceptions other than NoResultsException are
    // logged and treated as a failed attempt (not rethrown) so one source's transient error
    // doesn't prevent trying a more lenient query — CancellationException always propagates.
    private suspend fun tryAttempt(plan: RecommendationQueryPlan): MangasPage? {
        // Step 1: get filter list (local call into extension APK — no network in most extensions)
        // KMK v0.8.10-fix4: routed through SourceRuntime instead of a local try/catch(Exception)/
        // catch(Error) pair -- one shared boundary classifies both, and records a recoverable
        // failure (including an extension LinkageError) in SourceRuntimeFailureRegistry.
        val filterList = SourceRuntime.run(catalogueSource, SourceRuntimeOperation.FilterList) {
            getFilterList()
        }.getOrElse {
            logcat(LogPriority.WARN) { "Cross-extension filter loading failed; using empty filters" }
            FilterList()
        }

        // Step 2: map this attempt's tags to filters; unmatched tags become a text query
        val searchParams = GenreFilterMapper.buildSearch(filterList, plan.tags, forceTextOnly = plan.forceTextOnly)
        cachedTextQuery = searchParams.textQuery
        cachedFiltersJson = runCatching {
            filterSerializer.serialize(searchParams.filters).toString()
        }.getOrNull()

        // Step 3: search the extension (one network call)
        // KMK v0.8.10-fix4: routed through SourceRuntime instead of a local try/catch(Exception)/
        // catch(Error) pair. NoResultsException is still distinguished from every other recoverable
        // failure (it must not set exceptionOccurred -- that flag specifically means "the attempt
        // itself broke," not "the source legitimately had nothing").
        var exceptionOccurred = false
        val mangasPage = SourceRuntime.run(catalogueSource, SourceRuntimeOperation.Search) {
            getSearchManga(1, searchParams.textQuery, searchParams.filters)
        }.getOrElse { throwable ->
            if (throwable !is NoResultsException) {
                logcat(LogPriority.WARN) { "Cross-extension search attempt failed" }
                exceptionOccurred = true
            }
            null
        }

        val rawCount = mangasPage?.mangas?.size ?: 0
        if (rawCount == 0) {
            lastFailureKind = RecommendationQueryAttemptPolicy.classify(
                strategy = plan.type,
                rawCount = 0,
                enrichedCount = 0,
                relevantCount = 0,
                exceptionOccurred = exceptionOccurred,
            ).failureKind
            return null
        }

        enrichTopResults(mangasPage!!.mangas)
        val enrichedCount = mangasPage.mangas.count { !it.genre.isNullOrEmpty() }
        // This class only fetches raw candidates — relevance scoring happens downstream in
        // RecommendsScreenModel (RecommendationScorer/GroupSeedRecommendationScorer). A non-empty
        // raw result is treated as "useful" here so the row returns results for scoring instead of
        // silently trying every attempt; RecommendsScreenModel applies the actual relevance filter.
        lastFailureKind = RecommendationQueryFailureKind.NONE
        return MangasPage(mangasPage.mangas, false).also {
            logcat(LogPriority.DEBUG) {
                "Cross-extension search results: strategy=${plan.type}, raw=$rawCount, enriched=$enrichedCount"
            }
        }
    }
    // KMK <--

    // KMK --> v0.7.44: bounded title fallback for grouped recommendations (or single-manga title
    // when no genres were available at all). Tries at most MAX_TITLE_FALLBACKS titles.
    private suspend fun runTitleFallback(): MangasPage {
        val titles = (titlesOverride?.takeIf { it.isNotEmpty() } ?: listOf(manga.ogTitle))
            .distinct()
            .take(MAX_TITLE_FALLBACKS)
        for (title in titles) {
            cachedTextQuery = title
            cachedFiltersJson = null
            // KMK v0.8.10-fix4: routed through SourceRuntime instead of a local try/catch(Exception)/
            // catch(Error) pair -- same reasoning as the tryAttempt() search above.
            val page = SourceRuntime.run(catalogueSource, SourceRuntimeOperation.Search) {
                getSearchManga(1, title, FilterList())
            }.getOrElse { throwable ->
                if (throwable !is NoResultsException) {
                    logcat(LogPriority.WARN) { "Cross-extension title fallback failed" }
                }
                continue
            }
            if (page.mangas.isNotEmpty()) {
                enrichTopResults(page.mangas)
                lastFailureKind = RecommendationQueryFailureKind.NONE
                return MangasPage(page.mangas, false)
            }
        }
        lastFailureKind = RecommendationQueryFailureKind.NO_RAW_RESULTS
        throw NoResultsException()
    }
    // KMK <--

    // KMK --> v0.7.44: extracted unchanged from the old single-attempt requestNextPage — enriches
    // the top results to populate genre data so downstream scoring has something to compare
    // against. Capped at MAX_ENRICH_PER_SOURCE to limit network calls; results mutated in-place.
    private suspend fun enrichTopResults(mangas: List<SManga>) {
        coroutineScope {
            mangas
                .take(MAX_ENRICH_PER_SOURCE)
                .filter { it.genre.isNullOrEmpty() }
                .map { smanga ->
                    async {
                        // KMK v0.8.6: when a shared enrichment semaphore is present (GROUP_PREVIEW
                        // only), acquire a permit before the network call and release it in the
                        // semaphore's own `finally` (via withPermit), so the total number of
                        // in-flight detail requests is bounded across every source active under this
                        // load, not just within this one source's own MAX_ENRICH_PER_SOURCE cap.
                        // KMK v0.8.10-fix4: routed through SourceRuntime instead of runCatching --
                        // the behavior contract's explicit instruction is that runCatching must not be the source
                        // boundary because it does not record the failure registry. SourceRuntime
                        // still rethrows CancellationException and any genuinely fatal Error, exactly
                        // as runCatching should have but does not by default.
                        val enrich: suspend () -> Unit = {
                            SourceRuntime.run(catalogueSource, SourceRuntimeOperation.MangaUpdate) {
                                getMangaUpdate(
                                    manga = smanga,
                                    chapters = emptyList(),
                                    fetchDetails = true,
                                    fetchChapters = false,
                                ).manga
                            }.fold(
                                onSuccess = { details ->
                                    smanga.genre = details.genre
                                    smanga.description = details.description
                                    smanga.status = details.status
                                },
                                onFailure = {
                                    logcat(LogPriority.WARN) { "Cross-extension manga enrichment failed" }
                                },
                            )
                        }
                        if (sharedEnrichmentSemaphore != null) {
                            sharedEnrichmentSemaphore.withPermit { enrich() }
                        } else {
                            enrich()
                        }
                    }
                }.awaitAll()
        }
    }
    // KMK <--

    companion object {
        private const val MAX_ENRICH_PER_SOURCE = 10
        private const val MAX_TITLE_FALLBACKS = 2
    }
}
// KMK <--

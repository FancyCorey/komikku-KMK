package exh.recs.matching

// KMK --> v0.7.8
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceRuntime
import eu.kanade.tachiyomi.source.SourceRuntimeOperation
import eu.kanade.tachiyomi.source.isRecoverableSourceRuntimeFailure
import eu.kanade.tachiyomi.source.unwrapSourceRuntimeCause
import exh.recs.RecommendationErrorClassifier
import exh.recs.RecommendationSourceFilter
import exh.recs.RecommendationSourceOrdering
import exh.recs.isRetryableForDiscovery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.QuerySanitizer.sanitize
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.taste.interactor.GetCrossSourceIdentityDecisions
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionPolicy
import tachiyomi.domain.taste.model.CrossSourceRecordKey
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Shared bounded same-manga candidate search used by
 * [CrossExtensionMatchScreenModel] and [BestVersionCompareScreenModel].
 *
 * Applies recommendation-language filtering, source priority ordering,
 * per-source result cap, origin filtering, and multi-query deduplication.
 * Does NOT affect normal global search.
 */
class SameMangaCandidateSearcher(
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val coroutineDispatcher: CoroutineDispatcher,
    private val getIdentityDecisions: GetCrossSourceIdentityDecisions = Injekt.get(),
) {
    fun getMatchingSources(): List<Source> {
        val recLanguages = RecommendationSourceFilter.normalizeLanguages(
            sourcePreferences.recommendationSourceLanguages().get(),
        )
        val storedOrder = RecommendationSourceOrdering.parse(
            sourcePreferences.recommendationSourceOrder().get(),
        )
        val disabledSourceIds = sourcePreferences.disabledSources().get()
            .mapNotNull { it.toLongOrNull() }.toSet()
        val all = sourceManager.getVisibleSources()
        val filtered = RecommendationSourceFilter.filterForRecommendations(all, recLanguages)
        return RecommendationSourceOrdering.apply(filtered, storedOrder, disabledSourceIds)
    }

    /**
     * Searches [sources] (or all matching sources if null) with [queries] using the configured
     * per-source cap from [SameMangaMatchSettings]. The [originManga] is excluded from results.
     * Returns one [SameMangaSourceResult] per source.
     */
    suspend fun search(
        queries: List<String>,
        settings: SameMangaMatchSettings,
        originManga: Manga,
        sources: List<Source> = getMatchingSources(),
        onResult: suspend (SameMangaSourceResult) -> Unit,
    ) = coroutineScope {
        val cap = SameMangaMatchSettings.clampResultCap(settings.resultsPerSource)

        sources.map { source ->
            async {
                currentCoroutineContext().ensureActive()
                val result = searchOneSource(source, queries, cap, originManga)
                currentCoroutineContext().ensureActive()
                onResult(SameMangaSourceResult(source, result))
            }
        }.awaitAll()
    }

    private suspend fun searchOneSource(
        source: Source,
        queries: List<String>,
        cap: Int,
        originManga: Manga,
    ): SameMangaCandidateResult {
        // KMK v0.8.10-fix4: routed through SourceRuntime instead of a local
        // catch(Exception)/catch(Error) pair -- one shared boundary classifies both,
        // records a recoverable extension LinkageError in SourceRuntimeFailureRegistry,
        // and still always rethrows CancellationException and any genuinely fatal Error.
        // KMK v0.8.21-fix4: extracted to a local fun (rather than inlining the SourceRuntime.run
        // call at both the initial-attempt and retry sites below) so
        // SameMangaSearchOwnershipSourceTest's "exactly one Search-operation call site in this
        // file" source-guard stays satisfied by construction, not by convention.
        suspend fun attempt(query: String, bypassSuppression: Boolean = false) = SourceRuntime.run(
            source,
            SourceRuntimeOperation.Search,
            coroutineDispatcher,
            bypassSuppression = bypassSuppression,
        ) {
            getSearchManga(1, query.sanitize(), getFilterList())
        }
        return try {
            val seen = LinkedHashMap<CrossSourceRecordKey, Manga>()
            val confirmedKeys = mutableSetOf<MangaIdentityKey>()
            // KMK v0.8.10-fix3: widened from Exception? to Throwable? -- see the identical pattern
            // in CrossExtensionMatchScreenModel for the full reasoning.
            var lastError: Throwable? = null
            for (query in queries) {
                currentCoroutineContext().ensureActive()
                if (seen.size >= cap) break
                var searchResult = attempt(query)
                // KMK v0.8.21-fix4: R2/AUG-05 correction -- the reproduced live failure was a
                // transient HTTP 502 during this exact discovery search with no retry of any kind,
                // so a source having one bad moment permanently failed for the whole comparison. A
                // single bounded retry of the *same* query against the *same* source (never another
                // source's request or another source's pages -- see SameMangaCandidateSearcherRetryTest
                // and Plan B's REOPENED fix-boundary constraint) is attempted only for the transient
                // error kinds (Network/Timeout/RateLimit/ServerError) that a second attempt could
                // plausibly succeed at; a terminal failure like Authentication is never retried since
                // repeating the identical request cannot change the outcome. ensureActive() first so
                // a cancelled/superseded search never issues a pointless extra network call.
                searchResult.exceptionOrNull()?.let { throwable ->
                    if (RecommendationErrorClassifier.classify(throwable).isRetryableForDiscovery()) {
                        currentCoroutineContext().ensureActive()
                        // KMK v0.8.21-fix5: R2 correction -- SourceRuntime.run() unconditionally
                        // suppresses a second call to the same source within its 60s failure window
                        // (SourceRuntimeFailureRegistry.SUPPRESSION_WINDOW_MS, enforced
                        // unconditionally since v0.8.10-fix8) -- without lifting it here, this retry
                        // would never actually reach the source at all. Previously this called
                        // SourceRuntimeFailureRegistry.clear(source.id), which is a process-global
                        // side effect: it silently lifted suppression for every other unrelated
                        // concurrent caller of the same source too, and made a second consecutive
                        // failure record as a misleadingly fresh single-failure entry instead of
                        // correctly extending the existing one. bypassSuppression = true authorizes
                        // exactly this one retry attempt without touching the registry at all -- a
                        // second failure now correctly increments the existing entry's count and
                        // preserves its original firstFailureAt, and no other caller's suppression
                        // window is ever affected by this retry decision.
                        searchResult = attempt(query, bypassSuppression = true)
                    }
                }
                searchResult.fold(
                    onSuccess = { page ->
                        val resolved = page.mangas
                            .map { it.toDomainManga(source.id) }
                            .distinctBy { it.url }
                            .let { networkToLocalManga(it) }
                            .filterNot { it.source == originManga.source && it.url == originManga.url }
                        for (manga in resolved) {
                            if (seen.size >= cap) break
                            val pair = CrossSourceIdentityDecisionPolicy.canonicalPair(
                                CrossSourceRecordKey(originManga.source, originManga.url),
                                CrossSourceRecordKey(manga.source, manga.url),
                            )
                            val identityDecision = getIdentityDecisions.await(pair)
                            if (CrossSourceIdentityDecisionPolicy.isCurrentRejection(identityDecision)) {
                                continue
                            }
                            if (CrossSourceIdentityDecisionPolicy.isAuthoritativeConfirmation(identityDecision)) {
                                confirmedKeys += MangaIdentityKey(manga.source, manga.url)
                            }
                            seen.putIfAbsent(CrossSourceRecordKey(manga.source, manga.url), manga)
                        }
                    },
                    onFailure = { throwable -> lastError = throwable },
                )
            }
            when {
                seen.isNotEmpty() -> SameMangaCandidateEvidenceAttacher.attach(
                    origin = originManga,
                    candidates = seen.values.toList(),
                    confirmedKeys = confirmedKeys,
                )
                lastError != null -> SameMangaCandidateResult.Error(lastError)
                else -> SameMangaCandidateResult.Success(emptyList())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SameMangaCandidateResult.Error(e)
        } catch (e: Error) {
            val unwrapped = e.unwrapSourceRuntimeCause()
            if (!unwrapped.isRecoverableSourceRuntimeFailure()) throw e
            SameMangaCandidateResult.Error(unwrapped)
        }
    }
}
// KMK <--

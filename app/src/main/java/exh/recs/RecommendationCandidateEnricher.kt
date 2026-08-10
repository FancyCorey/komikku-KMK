package exh.recs

// KMK -->
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceRuntime
import eu.kanade.tachiyomi.source.SourceRuntimeOperation
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CoroutineDispatcher
import mihon.domain.manga.model.toDomainManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga

/**
 * Fetches full manga details for a bounded set of candidates that have weak metadata.
 *
 * Enrichment is sequential per source so it does not multiply network requests.
 * At most [limit] candidates are enriched. Detail call failures keep the original candidate.
 * Never enriches candidates that were filtered out before scoring.
 */
internal class RecommendationCandidateEnricher(
    private val networkToLocalManga: NetworkToLocalManga,
    private val coroutineDispatcher: CoroutineDispatcher,
) {
    suspend fun enrich(
        source: Source,
        candidates: List<Manga>,
        smangaByUrl: Map<String, SManga>,
        limit: Int,
    ): List<Manga> {
        if (limit <= 0) return candidates

        val enrichedByUrl = mutableMapOf<String, Manga>()
        var enrichCount = 0

        for (manga in candidates) {
            if (enrichCount >= limit) break
            if (!manga.needsEnrichment()) continue
            val smanga = smangaByUrl[manga.url] ?: continue

            // KMK v0.8.10-fix4: routed through SourceRuntime instead of runCatching -- the behavior contract's
            // explicit instruction is that runCatching must not be the source boundary because it
            // does not record the failure registry. SourceRuntime still rethrows
            // CancellationException and any genuinely fatal Error, exactly as runCatching should
            // have but does not by default.
            val enriched = SourceRuntime.run(source, SourceRuntimeOperation.MangaUpdate, coroutineDispatcher) {
                getMangaUpdate(
                    manga = smanga,
                    chapters = emptyList(),
                    fetchDetails = true,
                    fetchChapters = false,
                ).manga
            }.getOrNull()?.let { details ->
                networkToLocalManga(listOf(details.toDomainManga(source.id))).firstOrNull()
            }
            if (enriched != null) {
                enrichedByUrl[manga.url] = enriched
            }
            enrichCount++
        }

        return if (enrichedByUrl.isEmpty()) {
            candidates
        } else {
            candidates.map { enrichedByUrl[it.url] ?: it }
        }
    }

    companion object {
        /** True when the manga has not yet had its details fetched from the source. */
        fun Manga.needsEnrichment(): Boolean = !initialized
    }
}
// KMK <--

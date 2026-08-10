package exh.recs.evaluation

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceRuntime
import eu.kanade.tachiyomi.source.SourceRuntimeOperation
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.withTimeoutOrNull
import mihon.domain.manga.model.toDomainManga
import tachiyomi.domain.manga.model.Manga

// KMK --> v0.7.47
/**
 * Bounded catalogue-sample enrichment for Source Evaluation.
 *
 * Popular/Latest list entries frequently omit genre/tags until the manga detail page is fetched
 * (see `docs/recommendations/KMK.md`). Scoring those
 * list entries directly measures "does the list page expose tags?" instead of "does this source
 * contain manga matching my taste?". This helper calls [Source.getMangaDetails] for a
 * bounded number of samples that lack genre metadata, mirroring the same bounded-enrichment pattern
 * already used by `RecommendationCandidateEnricher` and `SourceRecommendationFitProbe`.
 *
 * This is evidence-only: enriched results are never written to the app manga table (no
 * `NetworkToLocalManga` call) and no chapter lists or page images are fetched.
 *
 * Pure enough to unit test with a fake [Source] — no Android dependencies.
 */
object SourceEvaluationCatalogueEnricher {

    const val CATALOGUE_DETAIL_ENRICH_CAP = 12
    const val CATALOGUE_DETAIL_ENRICH_TIMEOUT_MS = 10_000L

    data class CatalogueProbeSamples(
        val samples: List<Manga>,
        val detailAttempts: Int,
        val detailSuccesses: Int,
    )

    /**
     * Deduplicates [rawItems] by URL (preserving order), then enriches at most [cap] items that
     * lack genre metadata via a sequential, timeout-bounded, cancellation-aware
     * [Source.getMangaDetails] call. Items that already have genre metadata, or that are
     * beyond the cap, are converted to [Manga] directly without a detail call. A failed or timed-out
     * detail call keeps the original list-entry candidate and still counts as an attempt (but not a
     * success).
     */
    suspend fun enrich(
        source: Source,
        rawItems: List<SManga>,
        sourceId: Long,
        cap: Int = CATALOGUE_DETAIL_ENRICH_CAP,
        timeoutMs: Long = CATALOGUE_DETAIL_ENRICH_TIMEOUT_MS,
    ): CatalogueProbeSamples {
        val deduped = rawItems.distinctBy { it.url }
        var attempts = 0
        var successes = 0

        val samples = deduped.map { raw ->
            val hasGenre = !raw.getGenres().isNullOrEmpty()
            if (hasGenre || attempts >= cap) {
                return@map raw.toDomainManga(sourceId)
            }

            attempts++
            // KMK v0.8.10-fix4: routed through SourceRuntime instead of a local
            // catch(Exception)/catch(Error) pair -- one shared boundary classifies both, records a
            // recoverable extension LinkageError in SourceRuntimeFailureRegistry, and still always
            // rethrows CancellationException and any genuinely fatal Error.
            val enriched = withTimeoutOrNull(timeoutMs) {
                SourceRuntime.run(source, SourceRuntimeOperation.MangaUpdate) {
                    getMangaUpdate(
                        manga = raw,
                        chapters = emptyList(),
                        fetchDetails = true,
                        fetchChapters = false,
                    ).manga
                }.getOrNull()
            }

            if (enriched != null) {
                successes++
                enriched.toDomainManga(sourceId)
            } else {
                raw.toDomainManga(sourceId)
            }
        }

        return CatalogueProbeSamples(samples = samples, detailAttempts = attempts, detailSuccesses = successes)
    }
}
// KMK <--

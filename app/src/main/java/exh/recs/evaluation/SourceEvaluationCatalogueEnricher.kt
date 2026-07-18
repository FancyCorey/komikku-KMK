package exh.recs.evaluation

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.isRecoverableSourceRuntimeFailure
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.unwrapSourceRuntimeCause
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import mihon.domain.manga.model.toDomainManga
import tachiyomi.domain.manga.model.Manga

// KMK --> v0.7.47
/**
 * Bounded catalogue-sample enrichment for Source Evaluation.
 *
 * Popular/Latest list entries frequently omit genre/tags until the manga detail page is fetched
 * (see `docs/recommendations/KMK_SOURCE_EVALUATION_COMPLETE_AUDIT_2026_07_12.md`). Scoring those
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
            val enriched = try {
                withTimeoutOrNull(timeoutMs) {
                    source.getMangaUpdate(
                        manga = raw,
                        chapters = emptyList(),
                        fetchDetails = true,
                        fetchChapters = false,
                    ).manga
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } catch (e: Error) {
                // KMK v0.8.10-fix3: a broken/incompletely-packaged extension can throw a
                // LinkageError from getMangaUpdate() -- previously uncaught here, aborting
                // enrichment for the whole batch instead of just skipping this one candidate's
                // detail enrichment (identical fallback to the Exception branch above). Genuinely
                // fatal VM errors still rethrow.
                if (!e.unwrapSourceRuntimeCause().isRecoverableSourceRuntimeFailure()) throw e
                null
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

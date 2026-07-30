package exh.recs

// KMK v0.8.12 -->
/**
 * Pure decision for whether For You should try one bounded catalogue (Popular page 1) probe after
 * every tag-based query attempt for a source produced zero raw results.
 *
 * ## Why this exists
 *
 * Audited as part of the v0.8.12 false no-match report: a source whose text search only
 * matches manga titles (not genres/tags) will legitimately return zero raw results for every attempt
 * in [RecommendationQueryAttemptPolicy]'s strict-to-lenient chain, including the final
 * `TEXT_ONLY_TOP_TAGS` attempt -- which searches for the *literal tag words themselves*
 * (e.g. "Isekai Fantasy Action") as free text. Most source search backends treat a multi-word text
 * query as an AND-of-words title match, so this attempt can legitimately find nothing even though the
 * source's catalogue actually has plenty of matching manga -- the same manga a user would find by
 * manually browsing Popular/Latest. This was a genuine gap, not a misclassification: `hadRawResults`
 * was already false in that case, so the existing code correctly reported `NoMatches` for what the
 * *search* attempts found -- but never gave the *catalogue* a chance.
 *
 * A source that already produced at least one raw result (later filtered by rating/known/seen/min-
 * chapter/dedup) is reported as `FilteredOut`, not `NoMatches` -- that case is unaffected and this
 * fallback is not offered for it, since the existing filtered-out result already proves the search
 * itself works and simply had nothing left to show, which is not the bug being addressed here.
 */
internal object RecommendationCatalogueFallbackPolicy {

    // KMK v0.8.13: named `queryStrategy` value recorded in candidate-memory/discovery-progress rows
    // when a result came from this bounded Popular-page-1 probe rather than a tag/text search
    // attempt -- lets diagnostics and future refreshes tell fallback results apart from a real
    // RecommendationQueryStrategyType. Deliberately NOT one of RecommendationQueryStrategyType's
    // enum values and never written to `recommendationSourceStrategies()` -- both `queryStrategy`
    // fields it is written into are already free-form strings (RecommendationCandidateMemoryEntry,
    // RecommendationDiscoveryProgress), so no serialization/migration changes are needed.
    const val QUERY_STRATEGY = "CATALOGUE_FALLBACK"

    /**
     * @param hadRawResults true if any tag-based search attempt for this source returned at least
     * one raw manga (regardless of whether it was later filtered out).
     * @param hadError true if a search attempt failed with a recoverable source error -- the fallback
     * is skipped in that case since the source itself is already known to be malfunctioning, and a
     * bounded catalogue probe would just risk repeating the same failure.
     */
    fun shouldAttempt(hadRawResults: Boolean, hadError: Boolean): Boolean = !hadRawResults && !hadError
}
// KMK <--

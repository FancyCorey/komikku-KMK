package exh.recs

// KMK
/**
 * Typed provenance for a For You candidate: which discovery lane produced it.
 *
 * ## Why this is typed rather than inferred from a label
 *
 * Before this type, provenance was inferred from the free-form `queryStrategy` string written into
 * `recommendation_candidate_memory` / `recommendation_discovery_progress`: a real
 * [RecommendationQueryStrategyType] name for a tag/text search, or the sentinel
 * [RecommendationCatalogueFallbackPolicy.QUERY_STRATEGY] (`"CATALOGUE_FALLBACK"`) for the bounded
 * Popular probe. Adding a Latest probe under that same sentinel would have made Popular and Latest
 * evidence permanently indistinguishable in memory and diagnostics.
 *
 * ## Serialization compatibility
 *
 * [storageKey] values are written into the same existing free-form `queryStrategy` columns -- no
 * schema or migration change is required, and no existing value changes meaning:
 *
 * - [PERSONALIZED] is never written; a personalized result keeps writing its real
 *   `RecommendationQueryStrategyType.name`, exactly as before.
 * - [POPULAR_CATALOGUE] deliberately reuses the pre-existing `"CATALOGUE_FALLBACK"` constant, so
 *   every row already on disk keeps its current meaning.
 * - [LATEST_CATALOGUE] is a new value that has never been written before, so it cannot collide.
 *
 * [fromStorageKey] returns `null` for anything else -- including every real strategy name and any
 * unknown/legacy value. A missing or unrecognized provenance is **legacy/unknown, never Latest**.
 */
enum class RecommendationDiscoveryLane(val storageKey: String) {

    /** The strict-to-lenient personalized tag/text search chain. Always the dominant lane. */
    PERSONALIZED("PERSONALIZED"),

    /** The bounded Popular-page-1 probe owned by [RecommendationCatalogueFallbackPolicy]. */
    POPULAR_CATALOGUE(RecommendationCatalogueFallbackPolicy.QUERY_STRATEGY),

    /** The bounded Latest-page-1 exploration probe owned by [RecommendationCatalogueLanePolicy]. */
    LATEST_CATALOGUE("LATEST_CATALOGUE"),
    ;

    companion object {
        /**
         * Resolves a stored `queryStrategy` value to a lane, or `null` when the value is a real
         * personalized strategy name, empty, or otherwise unrecognized. Never guesses [LATEST_CATALOGUE].
         */
        fun fromStorageKey(storageKey: String?): RecommendationDiscoveryLane? = when (storageKey) {
            POPULAR_CATALOGUE.storageKey -> POPULAR_CATALOGUE
            LATEST_CATALOGUE.storageKey -> LATEST_CATALOGUE
            else -> null
        }
    }
}
// KMK <--

package exh.recs

// KMK --> v0.7.40: shared source selection policy for For You and group-seeded recommendations
import eu.kanade.tachiyomi.source.Source

/**
 * Composes [RecommendationSourceFilter] and [RecommendationSourceOrdering] into a single
 * source-selection call so that all personalized recommendation workflows use the same
 * language, priority, disabled-source, and disliked-source rules.
 *
 * This is a thin, pure helper — it never reads preferences or calls the source manager directly.
 * Callers are responsible for loading preference values and passing them here.
 */
internal object RecommendationSourceSelector {

    /**
     * Returns the ordered, filtered source list for a personalized recommendation workflow.
     *
     * @param sources All visible catalogue sources (from SourceManager.getVisibleSources()).
     * @param languages User-selected recommendation languages (raw, not normalized).
     * @param storedOrder Parsed source priority order (from RecommendationSourceOrdering.parse).
     * @param effectiveDisabledIds Combined disabled + disliked installed source IDs.
     * @param maxSources Maximum number of sources to return (0 = unlimited).
     */
    fun select(
        sources: List<Source>,
        languages: Set<String>,
        storedOrder: List<Long>,
        effectiveDisabledIds: Set<Long>,
        maxSources: Int = 0,
    ): List<Source> {
        val languageFiltered = RecommendationSourceFilter.filterForRecommendations(
            sources = sources,
            languages = languages,
        )
        val ordered = RecommendationSourceOrdering.apply(
            visibleSources = languageFiltered,
            storedOrder = storedOrder,
            disabledSourceIds = effectiveDisabledIds,
        )
        return if (maxSources > 0) ordered.take(maxSources) else ordered
    }
}
// KMK <--

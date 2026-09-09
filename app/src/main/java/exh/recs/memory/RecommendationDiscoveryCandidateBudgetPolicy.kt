package exh.recs.memory

/**
 * Validates the aggregate number of candidates an additional-page discovery pass may process for
 * one source during one refresh. The source page size remains an implementation detail; this
 * budget is the stable resource unit exposed to users.
 */
object RecommendationDiscoveryCandidateBudgetPolicy {
    const val MIN = 0
    const val MAX = 100
    const val DEFAULT = RecommendationDiscoveryPlanner.MAX_NEW_CANDIDATES_PER_DISCOVERY_PAGE

    fun resolve(value: Int): Int = value.coerceIn(MIN, MAX)
}

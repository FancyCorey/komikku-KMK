package exh.recs

/** Shared acceptance rule for late results from overlapping recommendation refreshes. */
internal object RecommendationRefreshGenerationPolicy {
    fun shouldAccept(candidateGeneration: Long, activeGeneration: Long): Boolean =
        candidateGeneration == activeGeneration

    /** A late cancelled load may claim its generation after a newer load; never regress the barrier. */
    fun activate(currentGeneration: Long, candidateGeneration: Long): Long =
        maxOf(currentGeneration, candidateGeneration)
}

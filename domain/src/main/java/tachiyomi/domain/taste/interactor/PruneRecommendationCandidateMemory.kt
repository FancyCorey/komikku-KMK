package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.repository.RecommendationCandidateMemoryRepository

// KMK --> v0.7.38: For You candidate discovery memory
class PruneRecommendationCandidateMemory(
    private val repository: RecommendationCandidateMemoryRepository,
) {
    /**
     * Ensures the stored candidate count for [sourceId] does not exceed [maxPerSource].
     * Prunes the oldest/lowest-seen entries first.
     */
    suspend fun awaitIfNeeded(sourceId: Long, maxPerSource: Long = MAX_PER_SOURCE) {
        val count = repository.countBySource(sourceId)
        if (count > maxPerSource) {
            repository.pruneOldestBySource(sourceId, count - maxPerSource)
        }
    }

    companion object {
        const val MAX_PER_SOURCE = 500L
    }
}
// KMK <--

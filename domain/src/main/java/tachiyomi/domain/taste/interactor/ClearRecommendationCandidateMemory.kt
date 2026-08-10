package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.repository.RecommendationCandidateMemoryRepository

// KMK --> v0.7.38: For You candidate discovery memory
class ClearRecommendationCandidateMemory(
    private val repository: RecommendationCandidateMemoryRepository,
) {
    /** Clears ALL stored For You discovery candidates. Does not affect ratings, seen manga, or source preferences. */
    suspend fun await() = repository.deleteAll()
}
// KMK <--

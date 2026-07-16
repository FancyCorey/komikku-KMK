package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.model.RecommendationCandidateMemory
import tachiyomi.domain.taste.repository.RecommendationCandidateMemoryRepository

// KMK --> v0.7.38: For You candidate discovery memory
class UpsertRecommendationCandidateMemory(
    private val repository: RecommendationCandidateMemoryRepository,
) {
    suspend fun await(entry: RecommendationCandidateMemory) =
        repository.upsert(entry)
}
// KMK <--

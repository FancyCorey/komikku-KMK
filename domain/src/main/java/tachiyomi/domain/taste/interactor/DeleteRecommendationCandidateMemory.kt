package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.repository.RecommendationCandidateMemoryRepository

// KMK --> v0.7.38: For You candidate discovery memory
class DeleteRecommendationCandidateMemory(
    private val repository: RecommendationCandidateMemoryRepository,
) {
    suspend fun awaitBySourceUrl(sourceId: Long, url: String) =
        repository.deleteBySourceUrl(sourceId, url)

    suspend fun awaitBySource(sourceId: Long) =
        repository.deleteBySource(sourceId)
}
// KMK <--

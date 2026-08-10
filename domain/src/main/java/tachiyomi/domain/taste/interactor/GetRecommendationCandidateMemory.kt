package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.model.RecommendationCandidateMemory
import tachiyomi.domain.taste.repository.RecommendationCandidateMemoryRepository

// KMK --> v0.7.38: For You candidate discovery memory
class GetRecommendationCandidateMemory(
    private val repository: RecommendationCandidateMemoryRepository,
) {
    suspend fun awaitAll(): List<RecommendationCandidateMemory> =
        repository.getAll()

    suspend fun awaitBySource(sourceId: Long): List<RecommendationCandidateMemory> =
        repository.getBySource(sourceId)

    suspend fun awaitBySources(sourceIds: Collection<Long>): List<RecommendationCandidateMemory> =
        repository.getBySources(sourceIds)

    suspend fun awaitBySourceUrl(sourceId: Long, url: String): RecommendationCandidateMemory? =
        repository.getBySourceUrl(sourceId, url)

    suspend fun awaitBySourceQuery(
        sourceId: Long,
        querySignature: String,
    ): List<RecommendationCandidateMemory> =
        repository.getBySourceQuery(sourceId, querySignature)

    suspend fun awaitDistinctPagesBySourceQuery(
        sourceId: Long,
        querySignature: String,
    ): Set<Int> =
        repository.getDistinctPagesBySourceQuery(sourceId, querySignature)

    suspend fun awaitCountBySource(sourceId: Long): Long =
        repository.countBySource(sourceId)
}
// KMK <--

package tachiyomi.domain.taste.repository

import tachiyomi.domain.taste.model.RecommendationCandidateMemory

// KMK --> v0.7.38: For You candidate discovery memory repository
interface RecommendationCandidateMemoryRepository {

    suspend fun getAll(): List<RecommendationCandidateMemory>

    suspend fun getBySource(sourceId: Long): List<RecommendationCandidateMemory>

    suspend fun getBySources(sourceIds: Collection<Long>): List<RecommendationCandidateMemory>

    suspend fun getBySourceUrl(sourceId: Long, url: String): RecommendationCandidateMemory?

    suspend fun getBySourceQuery(sourceId: Long, querySignature: String): List<RecommendationCandidateMemory>

    suspend fun getDistinctPagesBySourceQuery(sourceId: Long, querySignature: String): Set<Int>

    suspend fun countBySource(sourceId: Long): Long

    suspend fun upsert(entry: RecommendationCandidateMemory)

    suspend fun deleteBySourceUrl(sourceId: Long, url: String)

    suspend fun deleteBySource(sourceId: Long)

    suspend fun deleteAll()

    suspend fun pruneOldestBySource(sourceId: Long, pruneCount: Long)
}
// KMK <--

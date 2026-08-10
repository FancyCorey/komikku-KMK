package tachiyomi.domain.taste.repository

import tachiyomi.domain.taste.model.RecommendationDiscoveryProgress

// KMK --> v0.7.39: For You rolling discovery progress repository
interface RecommendationDiscoveryProgressRepository {

    suspend fun getBySourceQuery(
        sourceId: Long,
        querySignature: String,
    ): List<RecommendationDiscoveryProgress>

    suspend fun getEvaluatedPagesBySourceQuery(
        sourceId: Long,
        querySignature: String,
    ): Set<Int>

    suspend fun upsert(entry: RecommendationDiscoveryProgress)

    suspend fun deleteBySourceQuery(sourceId: Long, querySignature: String)

    suspend fun deleteBySource(sourceId: Long)

    suspend fun deleteAll()
}
// KMK <--

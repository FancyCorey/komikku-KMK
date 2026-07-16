package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.model.RecommendationDiscoveryProgress
import tachiyomi.domain.taste.repository.RecommendationDiscoveryProgressRepository

// KMK --> v0.7.39: For You rolling discovery progress
class GetRecommendationDiscoveryProgress(
    private val repository: RecommendationDiscoveryProgressRepository,
) {
    suspend fun awaitEvaluatedPages(sourceId: Long, querySignature: String): Set<Int> =
        repository.getEvaluatedPagesBySourceQuery(sourceId, querySignature)

    suspend fun awaitBySourceQuery(
        sourceId: Long,
        querySignature: String,
    ): List<RecommendationDiscoveryProgress> =
        repository.getBySourceQuery(sourceId, querySignature)
}
// KMK <--

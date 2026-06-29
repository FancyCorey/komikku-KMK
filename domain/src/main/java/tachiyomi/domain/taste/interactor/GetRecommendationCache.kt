package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.model.RecommendationCacheEntry
import tachiyomi.domain.taste.repository.RecommendationCacheRepository

// KMK -->
class GetRecommendationCache(
    private val repository: RecommendationCacheRepository,
) {
    suspend fun await(cacheKey: String): RecommendationCacheEntry? =
        repository.getCache(cacheKey)

    suspend fun awaitForSource(sourceId: Long): List<RecommendationCacheEntry> =
        repository.getCacheForSource(sourceId)
}
// KMK <--

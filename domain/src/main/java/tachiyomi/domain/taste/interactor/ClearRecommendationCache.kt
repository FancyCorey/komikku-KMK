package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.repository.RecommendationCacheRepository

// KMK -->
class ClearRecommendationCache(
    private val repository: RecommendationCacheRepository,
) {
    suspend fun awaitAll() = repository.deleteAllCaches()

    suspend fun awaitForSource(sourceId: Long) = repository.deleteCacheForSource(sourceId)

    suspend fun awaitExpired(now: Long = System.currentTimeMillis()) =
        repository.deleteExpiredCaches(now)
}
// KMK <--

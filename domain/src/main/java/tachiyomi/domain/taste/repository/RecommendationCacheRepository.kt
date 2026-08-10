package tachiyomi.domain.taste.repository

import tachiyomi.domain.taste.model.RecommendationCacheEntry

// KMK -->
interface RecommendationCacheRepository {

    suspend fun getCache(cacheKey: String): RecommendationCacheEntry?

    suspend fun getCacheForSource(sourceId: Long): List<RecommendationCacheEntry>

    suspend fun upsertCache(entry: RecommendationCacheEntry)

    suspend fun deleteCacheByCacheKey(cacheKey: String)

    suspend fun deleteCacheForSource(sourceId: Long)

    suspend fun deleteExpiredCaches(now: Long)

    suspend fun deleteAllCaches()
}
// KMK <--

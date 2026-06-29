package tachiyomi.data.taste

import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.taste.model.RecommendationCacheEntry
import tachiyomi.domain.taste.repository.RecommendationCacheRepository

// KMK -->
class RecommendationCacheRepositoryImpl(
    private val handler: DatabaseHandler,
) : RecommendationCacheRepository {

    override suspend fun getCache(cacheKey: String): RecommendationCacheEntry? {
        return handler.awaitOneOrNull {
            recommendation_cacheQueries.getByCacheKey(cacheKey, recommendationCacheMapper)
        }
    }

    override suspend fun getCacheForSource(sourceId: Long): List<RecommendationCacheEntry> {
        return handler.awaitList {
            recommendation_cacheQueries.getBySourceId(sourceId, recommendationCacheMapper)
        }
    }

    override suspend fun upsertCache(entry: RecommendationCacheEntry) {
        handler.await(inTransaction = true) {
            recommendation_cacheQueries.upsert(
                cacheKey = entry.cacheKey,
                sourceId = entry.sourceId,
                profileFingerprint = entry.profileFingerprint,
                queryKey = entry.queryKey,
                resultMangaIds = entry.resultMangaIds,
                resultScores = entry.resultScores,
                resultReasons = entry.resultReasons,
                createdAt = entry.createdAt,
                expiresAt = entry.expiresAt,
            )
        }
    }

    override suspend fun deleteCacheByCacheKey(cacheKey: String) {
        handler.await { recommendation_cacheQueries.deleteByCacheKey(cacheKey) }
    }

    override suspend fun deleteCacheForSource(sourceId: Long) {
        handler.await { recommendation_cacheQueries.deleteBySourceId(sourceId) }
    }

    override suspend fun deleteExpiredCaches(now: Long) {
        handler.await { recommendation_cacheQueries.deleteExpired(now) }
    }

    override suspend fun deleteAllCaches() {
        handler.await { recommendation_cacheQueries.deleteAll() }
    }
}

private val recommendationCacheMapper = {
        cacheKey: String,
        sourceId: Long,
        profileFingerprint: String,
        queryKey: String,
        resultMangaIds: String,
        resultScores: String?,
        resultReasons: String?,
        createdAt: Long,
        expiresAt: Long,
    ->
    RecommendationCacheEntry(
        cacheKey = cacheKey,
        sourceId = sourceId,
        profileFingerprint = profileFingerprint,
        queryKey = queryKey,
        resultMangaIds = resultMangaIds,
        resultScores = resultScores,
        resultReasons = resultReasons,
        createdAt = createdAt,
        expiresAt = expiresAt,
    )
}
// KMK <--

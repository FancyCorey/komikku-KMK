package tachiyomi.data.taste

import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.taste.model.RecommendationExposure
import tachiyomi.domain.taste.repository.RecommendationExposureRepository

class RecommendationExposureRepositoryImpl(
    private val handler: DatabaseHandler,
) : RecommendationExposureRepository {

    override suspend fun getBySourceUrls(keys: List<Pair<Long, String>>): List<RecommendationExposure> {
        if (keys.isEmpty()) return emptyList()
        return handler.await(inTransaction = true) {
            keys.mapNotNull { (sourceId, url) ->
                recommendation_exposureQueries.getBySourceUrl(sourceId, url, recommendationExposureMapper).executeAsOneOrNull()
            }
        }
    }

    override suspend fun getBySource(sourceId: Long): List<RecommendationExposure> {
        return handler.awaitList {
            recommendation_exposureQueries.getBySource(sourceId, recommendationExposureMapper)
        }
    }

    override suspend fun recordExposureBatch(
        keys: List<Pair<Long, String>>,
        mangaIds: Map<Pair<Long, String>, Long?>,
        timestamp: Long,
    ) {
        if (keys.isEmpty()) return
        handler.await(inTransaction = true) {
            keys.forEach { key ->
                val (sourceId, url) = key
                recommendation_exposureQueries.recordExposure(
                    sourceId = sourceId,
                    url = url,
                    mangaId = mangaIds[key],
                    timestamp = timestamp,
                )
            }
        }
    }

    override suspend fun pruneOlderThan(cutoff: Long) {
        handler.await {
            recommendation_exposureQueries.pruneOlderThan(cutoff)
        }
    }

    override suspend fun deleteBySource(sourceId: Long) {
        handler.await {
            recommendation_exposureQueries.deleteBySource(sourceId)
        }
    }

    override suspend fun deleteAll() {
        handler.await {
            recommendation_exposureQueries.deleteAll()
        }
    }
}

private val recommendationExposureMapper = {
        sourceId: Long,
        url: String,
        mangaId: Long?,
        firstExposedAt: Long,
        lastExposedAt: Long,
        exposureCount: Long,
    ->
    RecommendationExposure(
        sourceId = sourceId,
        url = url,
        mangaId = mangaId,
        firstExposedAt = firstExposedAt,
        lastExposedAt = lastExposedAt,
        exposureCount = exposureCount.toInt(),
    )
}
// KMK <--

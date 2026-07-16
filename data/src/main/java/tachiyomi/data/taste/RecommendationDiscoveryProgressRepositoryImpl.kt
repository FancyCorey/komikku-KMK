package tachiyomi.data.taste

import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.taste.model.RecommendationDiscoveryProgress
import tachiyomi.domain.taste.repository.RecommendationDiscoveryProgressRepository

// KMK --> v0.7.39: For You rolling discovery progress
class RecommendationDiscoveryProgressRepositoryImpl(
    private val handler: DatabaseHandler,
) : RecommendationDiscoveryProgressRepository {

    override suspend fun getBySourceQuery(
        sourceId: Long,
        querySignature: String,
    ): List<RecommendationDiscoveryProgress> {
        return handler.awaitList {
            recommendation_discovery_progressQueries.getBySourceQuery(
                sourceId,
                querySignature,
                recommendationDiscoveryProgressMapper,
            )
        }
    }

    override suspend fun getEvaluatedPagesBySourceQuery(
        sourceId: Long,
        querySignature: String,
    ): Set<Int> {
        return handler.awaitList {
            recommendation_discovery_progressQueries.getEvaluatedPagesBySourceQuery(sourceId, querySignature)
        }.map { it.toInt() }.toSet()
    }

    override suspend fun upsert(entry: RecommendationDiscoveryProgress) {
        handler.await(inTransaction = true) {
            recommendation_discovery_progressQueries.upsert(
                sourceId = entry.sourceId,
                querySignature = entry.querySignature,
                queryTagsJson = entry.queryTagsJson,
                queryStrategy = entry.queryStrategy,
                page = entry.page.toLong(),
                evaluatedAt = entry.evaluatedAt,
                rawCount = entry.rawCount.toLong(),
                localizedCount = entry.localizedCount.toLong(),
                scoredCount = entry.scoredCount.toLong(),
                visibleCount = entry.visibleCount.toLong(),
                filteredCount = entry.filteredCount.toLong(),
                status = entry.status,
                errorMessage = entry.errorMessage,
                profileFingerprint = entry.profileFingerprint,
                // KMK --> v0.7.40
                attemptCount = entry.attemptCount.toLong(),
                nextRetryAt = entry.nextRetryAt,
                failureKind = entry.failureKind,
                // KMK <--
            )
        }
    }

    override suspend fun deleteBySourceQuery(sourceId: Long, querySignature: String) {
        handler.await {
            recommendation_discovery_progressQueries.deleteBySourceQuery(sourceId, querySignature)
        }
    }

    override suspend fun deleteBySource(sourceId: Long) {
        handler.await {
            recommendation_discovery_progressQueries.deleteBySource(sourceId)
        }
    }

    override suspend fun deleteAll() {
        handler.await {
            recommendation_discovery_progressQueries.deleteAll()
        }
    }
}

private val recommendationDiscoveryProgressMapper = {
        sourceId: Long,
        querySignature: String,
        queryTagsJson: String,
        queryStrategy: String?,
        page: Long,
        evaluatedAt: Long,
        rawCount: Long,
        localizedCount: Long,
        scoredCount: Long,
        visibleCount: Long,
        filteredCount: Long,
        status: String,
        errorMessage: String?,
        profileFingerprint: String?,
        // KMK --> v0.7.40
        attemptCount: Long,
        nextRetryAt: Long?,
        failureKind: String?,
    // KMK <--
    ->
    RecommendationDiscoveryProgress(
        sourceId = sourceId,
        querySignature = querySignature,
        queryTagsJson = queryTagsJson,
        queryStrategy = queryStrategy,
        page = page.toInt(),
        evaluatedAt = evaluatedAt,
        rawCount = rawCount.toInt(),
        localizedCount = localizedCount.toInt(),
        scoredCount = scoredCount.toInt(),
        visibleCount = visibleCount.toInt(),
        filteredCount = filteredCount.toInt(),
        status = status,
        errorMessage = errorMessage,
        profileFingerprint = profileFingerprint,
        // KMK --> v0.7.40
        attemptCount = attemptCount.toInt(),
        nextRetryAt = nextRetryAt,
        failureKind = failureKind,
        // KMK <--
    )
}
// KMK <--

package tachiyomi.data.taste

import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.taste.model.RecommendationCandidateMemory
import tachiyomi.domain.taste.repository.RecommendationCandidateMemoryRepository

// KMK --> v0.7.38: For You candidate discovery memory
class RecommendationCandidateMemoryRepositoryImpl(
    private val handler: DatabaseHandler,
) : RecommendationCandidateMemoryRepository {

    override suspend fun getAll(): List<RecommendationCandidateMemory> {
        return handler.awaitList {
            recommendation_candidate_memoryQueries.getAll(recommendationCandidateMemoryMapper)
        }
    }

    override suspend fun getBySource(sourceId: Long): List<RecommendationCandidateMemory> {
        return handler.awaitList {
            recommendation_candidate_memoryQueries.getBySource(sourceId, recommendationCandidateMemoryMapper)
        }
    }

    override suspend fun getBySources(sourceIds: Collection<Long>): List<RecommendationCandidateMemory> {
        return handler.awaitList {
            recommendation_candidate_memoryQueries.getBySources(sourceIds, recommendationCandidateMemoryMapper)
        }
    }

    override suspend fun getBySourceUrl(sourceId: Long, url: String): RecommendationCandidateMemory? {
        return handler.awaitOneOrNull {
            recommendation_candidate_memoryQueries.getBySourceUrl(sourceId, url, recommendationCandidateMemoryMapper)
        }
    }

    override suspend fun getBySourceQuery(
        sourceId: Long,
        querySignature: String,
    ): List<RecommendationCandidateMemory> {
        return handler.awaitList {
            recommendation_candidate_memoryQueries.getBySourceQuery(
                sourceId,
                querySignature,
                recommendationCandidateMemoryMapper,
            )
        }
    }

    override suspend fun getDistinctPagesBySourceQuery(
        sourceId: Long,
        querySignature: String,
    ): Set<Int> {
        return handler.awaitList {
            recommendation_candidate_memoryQueries.getDistinctPagesBySourceQuery(sourceId, querySignature)
        }.map { it.toInt() }.toSet()
    }

    override suspend fun countBySource(sourceId: Long): Long {
        return handler.awaitOne {
            recommendation_candidate_memoryQueries.countBySource(sourceId)
        }
    }

    override suspend fun upsert(entry: RecommendationCandidateMemory) {
        handler.await(inTransaction = true) {
            recommendation_candidate_memoryQueries.upsert(
                sourceId = entry.sourceId,
                url = entry.url,
                mangaId = entry.mangaId,
                title = entry.title,
                thumbnailUrl = entry.thumbnailUrl,
                normalizedTitle = entry.normalizedTitle,
                lastScore = entry.lastScore,
                matchedGroupsJson = entry.matchedGroupsJson,
                resultReasonsJson = entry.resultReasonsJson,
                querySignature = entry.querySignature,
                queryTagsJson = entry.queryTagsJson,
                queryStrategy = entry.queryStrategy,
                page = entry.page.toLong(),
                discoveredAt = entry.discoveredAt,
                lastScoredAt = entry.lastScoredAt,
                lastSeenAt = entry.lastSeenAt,
                profileFingerprint = entry.profileFingerprint,
                filteredReason = entry.filteredReason,
            )
        }
    }

    override suspend fun deleteBySourceUrl(sourceId: Long, url: String) {
        handler.await {
            recommendation_candidate_memoryQueries.deleteBySourceUrl(sourceId, url)
        }
    }

    override suspend fun deleteBySource(sourceId: Long) {
        handler.await {
            recommendation_candidate_memoryQueries.deleteBySource(sourceId)
        }
    }

    override suspend fun deleteAll() {
        handler.await {
            recommendation_candidate_memoryQueries.deleteAll()
        }
    }

    override suspend fun pruneOldestBySource(sourceId: Long, pruneCount: Long) {
        handler.await {
            recommendation_candidate_memoryQueries.pruneOldestBySource(sourceId, pruneCount)
        }
    }
}

private val recommendationCandidateMemoryMapper = {
        sourceId: Long,
        url: String,
        mangaId: Long?,
        title: String,
        thumbnailUrl: String?,
        normalizedTitle: String,
        lastScore: Double,
        matchedGroupsJson: String?,
        resultReasonsJson: String?,
        querySignature: String,
        queryTagsJson: String,
        queryStrategy: String?,
        page: Long,
        discoveredAt: Long,
        lastScoredAt: Long,
        lastSeenAt: Long,
        profileFingerprint: String?,
        filteredReason: String?,
    ->
    RecommendationCandidateMemory(
        sourceId = sourceId,
        url = url,
        mangaId = mangaId,
        title = title,
        thumbnailUrl = thumbnailUrl,
        normalizedTitle = normalizedTitle,
        lastScore = lastScore,
        matchedGroupsJson = matchedGroupsJson,
        resultReasonsJson = resultReasonsJson,
        querySignature = querySignature,
        queryTagsJson = queryTagsJson,
        queryStrategy = queryStrategy,
        page = page.toInt(),
        discoveredAt = discoveredAt,
        lastScoredAt = lastScoredAt,
        lastSeenAt = lastSeenAt,
        profileFingerprint = profileFingerprint,
        filteredReason = filteredReason,
    )
}
// KMK <--

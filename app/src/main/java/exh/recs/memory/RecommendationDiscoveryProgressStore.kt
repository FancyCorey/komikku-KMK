package exh.recs.memory

// KMK --> v0.7.39: For You rolling discovery progress
import kotlinx.coroutines.CancellationException
import tachiyomi.domain.taste.interactor.GetRecommendationDiscoveryProgress
import tachiyomi.domain.taste.interactor.UpsertRecommendationDiscoveryProgress
import tachiyomi.domain.taste.model.RecommendationDiscoveryProgress

/**
 * Wraps the discovery progress repository with safe access and convenience helpers.
 * Used by [BrowsePersonalRecommendationsScreenModel] to track evaluated pages so
 * that empty, filtered, and error pages are not retried on every refresh.
 *
 * v0.7.40: [progressRecords] returns the full typed records so [RecommendationDiscoveryPlanner]
 * can classify retryable vs permanent failures and apply bounded exponential backoff.
 */
class RecommendationDiscoveryProgressStore(
    private val getProgress: GetRecommendationDiscoveryProgress,
    private val upsertProgress: UpsertRecommendationDiscoveryProgress,
) {
    /** Return all progress records for this source + query. */
    suspend fun progressRecords(
        sourceId: Long,
        querySignature: String,
    ): List<RecommendationDiscoveryProgress> =
        try {
            getProgress.awaitBySourceQuery(sourceId, querySignature)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }

    /** Return the set of pages already evaluated for this source + query. */
    suspend fun evaluatedPages(sourceId: Long, querySignature: String): Set<Int> =
        try {
            getProgress.awaitEvaluatedPages(sourceId, querySignature)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptySet()
        }

    /** Record the outcome of probing a page (called for both page 1 and additional pages). */
    suspend fun recordProgress(
        sourceId: Long,
        querySignature: String,
        queryTags: List<String>,
        queryStrategy: String?,
        page: Int,
        profileFingerprint: String,
        rawCount: Int,
        localizedCount: Int,
        scoredCount: Int,
        visibleCount: Int,
        filteredCount: Int,
        status: String,
        errorMessage: String? = null,
        // KMK --> v0.7.40: retry metadata
        attemptCount: Int = 0,
        nextRetryAt: Long? = null,
        failureKind: String? = null,
        // KMK <--
    ) {
        val entry = RecommendationDiscoveryProgress(
            sourceId = sourceId,
            querySignature = querySignature,
            queryTagsJson = queryTags.joinToString(","),
            queryStrategy = queryStrategy,
            page = page,
            evaluatedAt = System.currentTimeMillis(),
            rawCount = rawCount,
            localizedCount = localizedCount,
            scoredCount = scoredCount,
            visibleCount = visibleCount,
            filteredCount = filteredCount,
            status = status,
            errorMessage = errorMessage,
            profileFingerprint = profileFingerprint,
            attemptCount = attemptCount,
            nextRetryAt = nextRetryAt,
            failureKind = failureKind,
        )
        try {
            upsertProgress.await(entry)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Best-effort progress tracking; a failed write here only means this page may be
            // retried on a later pass, which is safe and already handled by the retry/backoff logic.
        }
    }
}
// KMK <--

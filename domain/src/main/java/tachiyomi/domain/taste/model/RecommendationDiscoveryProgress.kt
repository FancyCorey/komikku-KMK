package tachiyomi.domain.taste.model

// KMK --> v0.7.39: For You rolling discovery progress tracker
/**
 * Records whether a specific source/query/page combination has been evaluated.
 * Tracked independently of [RecommendationCandidateMemory] so that empty,
 * filtered, and error pages are not retried on every refresh.
 */
data class RecommendationDiscoveryProgress(
    val sourceId: Long,
    val querySignature: String,
    val queryTagsJson: String,
    val queryStrategy: String?,
    val page: Int,
    val evaluatedAt: Long,
    val rawCount: Int,
    val localizedCount: Int,
    val scoredCount: Int,
    val visibleCount: Int,
    val filteredCount: Int,
    val status: String,
    val errorMessage: String?,
    val profileFingerprint: String?,
    // KMK --> v0.7.40: bounded retry metadata
    val attemptCount: Int = 0,
    val nextRetryAt: Long? = null,
    val failureKind: String? = null,
    // KMK <--
) {
    companion object {
        const val STATUS_SUCCESS = "success"
        const val STATUS_EMPTY = "empty"
        const val STATUS_FILTERED = "filtered"
        const val STATUS_DUPLICATE = "duplicate"
        const val STATUS_ERROR = "error"
        const val STATUS_UNSUPPORTED = "unsupported"
        const val STATUS_EXHAUSTED = "exhausted"

        // KMK --> v0.7.40: failure_kind values (stored with STATUS_ERROR)
        const val FAILURE_KIND_RETRYABLE = "retryable"
        const val FAILURE_KIND_PERMANENT = "permanent"
        // KMK <--
    }
}
// KMK <--

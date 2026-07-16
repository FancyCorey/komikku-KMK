package tachiyomi.domain.taste.model

// KMK --> v0.7.38: For You candidate discovery memory — local-only derived cache
/**
 * Domain model for a single remembered For You candidate.
 *
 * Stored in [recommendation_candidate_memory] (local-only, not in backup/sync).
 * Primary key: (sourceId, url).
 */
data class RecommendationCandidateMemory(
    val sourceId: Long,
    val url: String,
    val mangaId: Long?,
    val title: String,
    val thumbnailUrl: String?,
    val normalizedTitle: String,
    val lastScore: Double,
    val matchedGroupsJson: String?,
    val resultReasonsJson: String?,
    val querySignature: String,
    val queryTagsJson: String,
    val queryStrategy: String?,
    val page: Int,
    val discoveredAt: Long,
    val lastScoredAt: Long,
    val lastSeenAt: Long,
    val profileFingerprint: String?,
    val filteredReason: String?,
)
// KMK <--

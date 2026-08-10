package exh.recs.memory

// KMK --> v0.7.38: For You candidate discovery memory — app-layer representation
/**
 * App-layer representation of a remembered For You candidate.
 *
 * Derived from [tachiyomi.domain.taste.model.RecommendationCandidateMemory] with
 * JSON fields pre-parsed. Bad JSON produces empty lists rather than crashing.
 */
data class RecommendationCandidateMemoryEntry(
    val sourceId: Long,
    val url: String,
    val mangaId: Long?,
    val title: String,
    val normalizedTitle: String,
    val lastScore: Double,
    val matchedGroups: List<String>,
    val resultReasons: List<String>,
    val querySignature: String,
    val queryTags: List<String>,
    val queryStrategy: String?,
    val page: Int,
    val discoveredAt: Long,
    val lastScoredAt: Long,
    val lastSeenAt: Long,
)
// KMK <--

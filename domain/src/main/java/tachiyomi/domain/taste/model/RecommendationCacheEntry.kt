package tachiyomi.domain.taste.model

// KMK -->
data class RecommendationCacheEntry(
    val cacheKey: String,
    val sourceId: Long,
    val profileFingerprint: String,
    val queryKey: String,
    val resultMangaIds: String,
    val resultScores: String?,
    val resultReasons: String?,
    val createdAt: Long,
    val expiresAt: Long,
)
// KMK <--

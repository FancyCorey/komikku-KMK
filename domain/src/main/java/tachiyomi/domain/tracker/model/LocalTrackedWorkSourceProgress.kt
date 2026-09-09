package tachiyomi.domain.tracker.model

/** Progress retained for one concrete source version; inherited rows retain their origin identity. */
data class LocalTrackedWorkSourceProgress(
    val workId: String,
    val source: Long,
    val url: String,
    val chapterNumber: Double?,
    val chapterUrl: String,
    val chapterLabel: String,
    val progressAt: Long,
    val inheritedFromSource: Long? = null,
    val inheritedFromUrl: String? = null,
    val updatedAt: Long,
)

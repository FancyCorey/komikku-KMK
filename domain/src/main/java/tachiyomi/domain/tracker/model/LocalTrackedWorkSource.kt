package tachiyomi.domain.tracker.model

enum class LocalTrackedWorkSourceConfirmation {
    USER_CONFIRMED,
    SUGGESTED,
}

data class LocalTrackedWorkSource(
    val workId: String,
    val source: Long,
    val url: String,
    val title: String,
    val confidence: Int,
    val confirmation: LocalTrackedWorkSourceConfirmation,
    val inheritanceOptedOut: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

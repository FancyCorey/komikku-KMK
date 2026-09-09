package tachiyomi.domain.tracker.model

data class LocalTrackedWork(
    val id: String,
    val title: String,
    val normalizedTitle: String,
    val status: LocalTrackedWorkStatus,
    val lastChapterSource: Long?,
    val lastChapterNumber: Double?,
    val lastChapterUrl: String?,
    val lastChapterLabel: String?,
    val lastProgressAt: Long?,
    val score: Double? = null,
    val startDate: Long? = null,
    val finishDate: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    data class Metadata(
        val chapterNumber: Double?,
        val score: Double?,
        val startDate: Long?,
        val finishDate: Long?,
        val status: LocalTrackedWorkStatus? = null,
        val presentFields: Set<Field> = emptySet(),
    ) {
        enum class Field { CHAPTER, SCORE, START_DATE, FINISH_DATE, STATUS }
    }
}

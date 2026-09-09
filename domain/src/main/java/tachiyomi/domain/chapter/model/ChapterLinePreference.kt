package tachiyomi.domain.chapter.model

/** A user-confirmed, local preference for the chapter line used by one manga/source pair. */
data class ChapterLinePreference(
    val mangaId: Long,
    val sourceId: Long,
    val preferredScanlator: String?,
    val anchorChapterUrl: String,
    val anchorChapterNumber: Double?,
    val confirmedAt: Long,
    val updatedAt: Long,
)

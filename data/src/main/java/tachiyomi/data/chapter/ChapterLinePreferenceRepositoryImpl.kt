package tachiyomi.data.chapter

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.chapter.model.ChapterLinePreference
import tachiyomi.domain.chapter.repository.ChapterLinePreferenceRepository

class ChapterLinePreferenceRepositoryImpl(
    private val handler: DatabaseHandler,
) : ChapterLinePreferenceRepository {
    override fun get(mangaId: Long, sourceId: Long): Flow<ChapterLinePreference?> = handler.subscribeToOneOrNull {
        chapter_line_preferencesQueries.get(mangaId, sourceId, chapterLinePreferenceMapper)
    }

    override suspend fun getOnce(mangaId: Long, sourceId: Long): ChapterLinePreference? = handler.awaitOneOrNull {
        chapter_line_preferencesQueries.get(mangaId, sourceId, chapterLinePreferenceMapper)
    }

    override suspend fun save(preference: ChapterLinePreference) {
        require(preference.mangaId > 0L)
        require(preference.sourceId != 0L)
        require(preference.anchorChapterUrl.isNotBlank())
        require(preference.confirmedAt > 0L && preference.updatedAt >= preference.confirmedAt)
        require(preference.anchorChapterNumber?.isFinite() != false)
        val normalizedScanlator = preference.preferredScanlator
            ?.trim()
            ?.replace(Regex("\\s+"), " ")
            ?.takeIf(String::isNotEmpty)
        handler.await {
            chapter_line_preferencesQueries.upsert(
                mangaId = preference.mangaId,
                sourceId = preference.sourceId,
                preferredScanlator = normalizedScanlator,
                anchorChapterUrl = preference.anchorChapterUrl,
                anchorChapterNumber = preference.anchorChapterNumber,
                confirmedAt = preference.confirmedAt,
                updatedAt = preference.updatedAt,
            )
        }
    }

    override suspend fun clear(mangaId: Long, sourceId: Long) {
        require(mangaId > 0L)
        require(sourceId != 0L)
        handler.await { chapter_line_preferencesQueries.delete(mangaId, sourceId) }
    }
}

private val chapterLinePreferenceMapper = {
        mangaId: Long,
        sourceId: Long,
        preferredScanlator: String?,
        anchorChapterUrl: String,
        anchorChapterNumber: Double?,
        confirmedAt: Long,
        updatedAt: Long,
    ->
    ChapterLinePreference(
        mangaId = mangaId,
        sourceId = sourceId,
        preferredScanlator = preferredScanlator,
        anchorChapterUrl = anchorChapterUrl,
        anchorChapterNumber = anchorChapterNumber,
        confirmedAt = confirmedAt,
        updatedAt = updatedAt,
    )
}

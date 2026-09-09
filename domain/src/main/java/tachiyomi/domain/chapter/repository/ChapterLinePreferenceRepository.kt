package tachiyomi.domain.chapter.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.chapter.model.ChapterLinePreference

interface ChapterLinePreferenceRepository {
    fun get(mangaId: Long, sourceId: Long): Flow<ChapterLinePreference?>

    suspend fun getOnce(mangaId: Long, sourceId: Long): ChapterLinePreference?

    suspend fun save(preference: ChapterLinePreference)

    suspend fun clear(mangaId: Long, sourceId: Long)
}

package tachiyomi.domain.chapter.interactor

import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository

class GetChapterByUrl(
    private val chapterRepository: ChapterRepository,
) {

    suspend fun await(url: String): List<Chapter> {
        return try {
            chapterRepository.getChapterByUrl(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }
}

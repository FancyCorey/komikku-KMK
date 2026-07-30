package tachiyomi.domain.chapter.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository

class UpdateChapter(
    private val chapterRepository: ChapterRepository,
) {

    suspend fun await(chapterUpdate: ChapterUpdate): Boolean {
        try {
            chapterRepository.update(chapterUpdate)
            return true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return false
        }
    }

    suspend fun awaitAll(chapterUpdates: List<ChapterUpdate>): Boolean {
        try {
            chapterRepository.updateAll(chapterUpdates)
            return true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return false
        }
    }
}

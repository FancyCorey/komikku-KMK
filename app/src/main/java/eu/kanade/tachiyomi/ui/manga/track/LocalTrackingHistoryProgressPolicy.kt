package eu.kanade.tachiyomi.ui.manga.track

import tachiyomi.domain.history.model.History

object LocalTrackingHistoryProgressPolicy {

    data class ReadProgress(
        val chapterId: Long,
        val progressAt: Long,
        val firstReadAt: Long,
    )

    fun resolve(history: List<History>): ReadProgress? {
        val readHistory = history.mapNotNull { entry ->
            entry.readAt?.time?.takeIf { it > 0L }?.let { readAt -> entry to readAt }
        }
        if (readHistory.isEmpty()) return null

        return ReadProgress(
            chapterId = readHistory.maxBy { it.second }.first.chapterId,
            progressAt = readHistory.maxOf { it.second },
            firstReadAt = readHistory.minOf { it.second },
        )
    }
}

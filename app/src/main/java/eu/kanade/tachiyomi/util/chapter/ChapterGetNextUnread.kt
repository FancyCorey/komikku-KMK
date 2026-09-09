package eu.kanade.tachiyomi.util.chapter

import eu.kanade.domain.chapter.model.applyFilters
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.manga.ChapterList
import exh.source.isEhBasedManga
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterLinePreference
import tachiyomi.domain.manga.model.Manga

/**
 * Gets next unread chapter with filters and sorting applied
 */
fun List<Chapter>.getNextUnread(
    manga: Manga,
    downloadManager: DownloadManager,
    // SY -->
    mergedManga: Map<Long, Manga>,
    // SY <--
    skipDuplicateChapterNumbers: Boolean = false,
    linePreference: ChapterLinePreference? = null,
): Chapter? {
    return applyFilters(manga, downloadManager/* SY --> */, mergedManga/* SY <-- */).let { chapters ->
        val candidates = if (skipDuplicateChapterNumbers) {
            ChapterLineContinuityPolicy.collapse(chapters.toList(), linePreference)
        } else {
            chapters.toList()
        }
        // SY -->
        if (manga.isEhBasedManga()) {
            return@let if (manga.sortDescending()) {
                candidates.firstOrNull()?.takeUnless { it.read }
            } else {
                candidates.lastOrNull()?.takeUnless { it.read }
            }
        }
        // SY <--
        if (manga.sortDescending()) {
            candidates.findLast { !it.read }
        } else {
            candidates.find { !it.read }
        }
    }
}

/**
 * Gets next unread chapter with filters and sorting applied
 */
fun List<ChapterList.Item>.getNextUnread(
    manga: Manga,
    skipDuplicateChapterNumbers: Boolean = false,
    linePreference: ChapterLinePreference? = null,
): Chapter? {
    return applyFilters(manga).let { chapters ->
        val candidates = if (skipDuplicateChapterNumbers) {
            ChapterLineContinuityPolicy.collapseItems(chapters.toList(), linePreference)
        } else {
            chapters.toList()
        }
        // SY -->
        if (manga.isEhBasedManga()) {
            return@let if (manga.sortDescending()) {
                candidates.firstOrNull()?.takeUnless { it.chapter.read }
            } else {
                candidates.lastOrNull()?.takeUnless { it.chapter.read }
            }
        }
        // SY <--
        if (manga.sortDescending()) {
            candidates.findLast { !it.chapter.read }
        } else {
            candidates.find { !it.chapter.read }
        }
    }?.chapter
}

package exh.util

import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.domain.chapter.model.Chapter

// KMK -->
/**
 * Builds not-yet-committed [ChapterJournalEntry] snapshots for bookmark/read-state changes. Same
 * build-before-write/commit-after-success contract as every other recorder in this package.
 *
 * No-ops (returns an empty list) when Evaluation Mode is disabled, or when the requested batch exceeds
 * [ChapterUndoBoundPolicy.MAX_JOURNALED_CHAPTERS] -- the caller's write still proceeds normally in that
 * case, it is simply not journaled (see the shared 500-chapter bound policy).
 */
object ChapterUndoRecorder {

    fun buildBookmarkEntries(
        sourcePreferences: SourcePreferences,
        previousChapters: List<Chapter>,
        newBookmark: Boolean,
    ): List<ChapterJournalEntry> {
        if (!sourcePreferences.evaluationMode().get() || previousChapters.isEmpty()) return emptyList()
        if (ChapterUndoBoundPolicy.exceedsBound(previousChapters.size)) return emptyList()
        val changed = previousChapters.filter { it.bookmark != newBookmark }
        if (changed.isEmpty()) return emptyList()
        val bulkId = if (changed.size > 1) ChapterJournalEntry.newBulkId() else null
        return changed.map { chapter ->
            ChapterJournalEntry(
                id = ChapterJournalEntry.newId(),
                timestamp = System.currentTimeMillis(),
                actionType = if (newBookmark) ChapterJournalActionType.BOOKMARK else ChapterJournalActionType.UNBOOKMARK,
                chapterId = chapter.id,
                previousRead = null,
                expectedPostRead = null,
                previousLastPageRead = null,
                expectedPostLastPageRead = null,
                previousBookmark = chapter.bookmark,
                expectedPostBookmark = newBookmark,
                isBulk = changed.size > 1,
                bulkOperationId = bulkId,
            )
        }
    }

    fun buildReadEntries(
        sourcePreferences: SourcePreferences,
        previousChapters: List<Chapter>,
        newRead: Boolean,
    ): List<ChapterJournalEntry> {
        if (!sourcePreferences.evaluationMode().get() || previousChapters.isEmpty()) return emptyList()
        if (ChapterUndoBoundPolicy.exceedsBound(previousChapters.size)) return emptyList()
        val changed = previousChapters.filter {
            it.read != newRead || (!newRead && it.lastPageRead > 0)
        }
        if (changed.isEmpty()) return emptyList()
        val bulkId = if (changed.size > 1) ChapterJournalEntry.newBulkId() else null
        return changed.map { chapter ->
            ChapterJournalEntry(
                id = ChapterJournalEntry.newId(),
                timestamp = System.currentTimeMillis(),
                actionType = if (newRead) ChapterJournalActionType.READ else ChapterJournalActionType.UNREAD,
                chapterId = chapter.id,
                previousRead = chapter.read,
                expectedPostRead = newRead,
                previousLastPageRead = chapter.lastPageRead.takeIf { !newRead },
                expectedPostLastPageRead = 0L.takeIf { !newRead },
                previousBookmark = null,
                expectedPostBookmark = null,
                isBulk = changed.size > 1,
                bulkOperationId = bulkId,
            )
        }
    }
}
// KMK <--

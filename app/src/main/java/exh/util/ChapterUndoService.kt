package exh.util

import kotlinx.coroutines.CancellationException
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/** Pure conflict check: compares only the field(s) this entry's action actually changed. */
fun chapterUndoConflicts(
    entry: ChapterJournalEntry,
    currentRead: Boolean,
    currentBookmark: Boolean,
    currentLastPageRead: Long = 0L,
): Boolean {
    val readConflict = entry.expectedPostRead != null && entry.expectedPostRead != currentRead
    val pageConflict = entry.expectedPostLastPageRead != null && entry.expectedPostLastPageRead != currentLastPageRead
    val bookmarkConflict = entry.expectedPostBookmark != null && entry.expectedPostBookmark != currentBookmark
    return readConflict || pageConflict || bookmarkConflict
}

/**
 * Restore logic for [ChapterUndoJournal] entries. Restores the exact read/bookmark fields changed,
 * including the page-progress reset caused by marking a chapter unread. History and tracker state
 * remain outside this local inverse.
 *
 * [UpdateChapter.await] now returns an explicit `Boolean` success signal (previously it swallowed
 * every `Exception` internally with no way for a caller to detect a persistence-layer failure); this
 * service checks that return value and reports [GroupUndoResult.FAILED] without removing the journal
 * entry when the write did not actually apply, so a failed restore stays undoable for a retry. See
 * `ChapterUndoServiceRestoreTest`'s "a persistence failure during restore is reported and remains
 * undoable" test.
 */
class ChapterUndoService(
    private val getChapter: GetChapter = Injekt.get(),
    private val updateChapter: UpdateChapter = Injekt.get(),
) {
    suspend fun undo(entryId: String): GroupUndoResult {
        val entry = ChapterUndoJournal.snapshot().find { it.id == entryId } ?: return GroupUndoResult.FAILED
        if (!entry.reversible) return GroupUndoResult.CONFLICT
        return try {
            val current = getChapter.await(entry.chapterId) ?: return GroupUndoResult.CONFLICT
            if (chapterUndoConflicts(entry, current.read, current.bookmark, current.lastPageRead)) {
                return GroupUndoResult.CONFLICT
            }
            val restored = updateChapter.await(
                ChapterUpdate(
                    id = entry.chapterId,
                    read = entry.previousRead,
                    bookmark = entry.previousBookmark,
                    lastPageRead = entry.previousLastPageRead,
                ),
            )
            if (!restored) return GroupUndoResult.FAILED
            ChapterUndoJournal.removeById(entry.id)
            GroupUndoResult.RESTORED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GroupUndoResult.FAILED
        }
    }
}
// KMK <--

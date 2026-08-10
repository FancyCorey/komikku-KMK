package exh.util

import java.util.UUID

// KMK -->
/**
 * Evaluation Mode Undo Journal for discrete local chapter read/bookmark state changes -- a sibling to
 * [LibraryUndoJournal], same in-memory-only, bounded, typed-inverse design. Never journals
 * `lastPageRead` (continuous reader progress -- too frequent to be a meaningful Undo target) and never
 * attempts to reverse a coupled tracker update; those remain ordinary, un-journaled app behavior.
 */
enum class ChapterJournalActionType { READ, UNREAD, BOOKMARK, UNBOOKMARK }

data class ChapterJournalEntry(
    val id: String,
    val timestamp: Long,
    val actionType: ChapterJournalActionType,
    val chapterId: Long,
    val previousRead: Boolean?,
    val expectedPostRead: Boolean?,
    val previousBookmark: Boolean?,
    val expectedPostBookmark: Boolean?,
    /** Set when marking unread reset the chapter's page progress. */
    val previousLastPageRead: Long? = null,
    val expectedPostLastPageRead: Long? = null,
    val isBulk: Boolean = false,
    val bulkOperationId: String? = null,
    val reversible: Boolean = true,
) {
    companion object {
        fun newId(): String = UUID.randomUUID().toString()
        fun newBulkId(): String = UUID.randomUUID().toString()
    }
}

/**
 * Bounded, thread-safe, in-memory chapter-state journal, evicted bulk-group-aware exactly like
 * [EvaluationModeUndoJournal]/[LibraryUndoJournal].
 */
object ChapterUndoJournal {
    const val MAX_ENTRIES = 20

    private val lock = Any()
    private val entries = ArrayDeque<ChapterJournalEntry>()

    fun record(entry: ChapterJournalEntry) {
        synchronized(lock) {
            entries.addLast(entry)
            while (entries.size > MAX_ENTRIES) {
                val oldestBulkId = entries.first().bulkOperationId
                if (oldestBulkId != null) {
                    entries.removeAll { it.bulkOperationId == oldestBulkId }
                } else {
                    entries.removeFirst()
                }
            }
        }
    }

    fun snapshot(): List<ChapterJournalEntry> = synchronized(lock) { entries.toList().asReversed() }

    fun removeById(id: String) {
        synchronized(lock) { entries.removeAll { it.id == id } }
    }

    fun entriesForBulk(bulkId: String): List<ChapterJournalEntry> =
        synchronized(lock) { entries.filter { it.bulkOperationId == bulkId } }

    fun clear() {
        synchronized(lock) { entries.clear() }
    }

    fun isEmpty(): Boolean = synchronized(lock) { entries.isEmpty() }
}

/**
 * Shared bound for "mark every chapter of this manga" style operations. Above this, the requested
 * read/unread operation still completes normally, but no Undo entry is created. The conservative
 * limit is 500 chapters.
 */
object ChapterUndoBoundPolicy {
    const val MAX_JOURNALED_CHAPTERS = 500

    fun exceedsBound(count: Int): Boolean = count > MAX_JOURNALED_CHAPTERS
}
// KMK <--

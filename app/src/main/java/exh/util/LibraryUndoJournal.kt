package exh.util

import java.util.UUID

// KMK -->
/**
 * Evaluation Mode Undo Journal for local library membership and category-assignment mutations --
 * a sibling to [EvaluationModeUndoJournal] (taste) and [GroupUndoJournal] (cross-source links), with
 * the same in-memory-only, bounded, typed-inverse design.
 *
 * Deliberately narrow: a [LibraryJournalEntry] only ever describes the `favorite`/`dateAdded` row flip
 * or the category-id-set replacement for one manga. It never describes the coupled side effects a
 * favorite/unfavorite can trigger elsewhere (cover-file removal, downloaded-chapter deletion, remote
 * metadata/chapter fetch, enhanced-tracker binding) -- those are real external effects and are never
 * restored by this journal, per the documented scope.
 */
enum class LibraryJournalActionType { FAVORITE, UNFAVORITE, SET_CATEGORIES }

data class LibraryJournalEntry(
    val id: String,
    val timestamp: Long,
    val actionType: LibraryJournalActionType,
    val mangaId: Long,
    /** Null for [LibraryJournalActionType.SET_CATEGORIES] entries (categories are independent of favorite state). */
    val previousFavorite: Boolean?,
    val previousDateAdded: Long?,
    val expectedPostFavorite: Boolean?,
    /** Null for [LibraryJournalActionType.FAVORITE]/[LibraryJournalActionType.UNFAVORITE] entries. */
    val previousCategoryIds: List<Long>?,
    val expectedPostCategoryIds: List<Long>?,
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
 * Bounded, thread-safe, in-memory journal. Same eviction contract as [EvaluationModeUndoJournal]: a
 * bulk operation is evicted as a whole when its oldest member entry would otherwise be evicted alone.
 */
object LibraryUndoJournal {
    const val MAX_ENTRIES = 20

    private val lock = Any()
    private val entries = ArrayDeque<LibraryJournalEntry>()

    fun record(entry: LibraryJournalEntry) {
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

    fun snapshot(): List<LibraryJournalEntry> = synchronized(lock) { entries.toList().asReversed() }

    fun removeById(id: String) {
        synchronized(lock) { entries.removeAll { it.id == id } }
    }

    fun entriesForBulk(bulkId: String): List<LibraryJournalEntry> =
        synchronized(lock) { entries.filter { it.bulkOperationId == bulkId } }

    fun clear() {
        synchronized(lock) { entries.clear() }
    }

    fun isEmpty(): Boolean = synchronized(lock) { entries.isEmpty() }
}
// KMK <--

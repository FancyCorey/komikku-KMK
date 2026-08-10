package exh.util

import java.util.UUID

// KMK v0.8.19 -->
/**
 * Evaluation Mode Action Undo Journal.
 *
 * Bounded, **in-memory only**, typed journal for reversing supported local actions performed while
 * Evaluation Mode is enabled. This file owns the taste-action entry family; the broader Action
 * History screen combines it with sibling typed journals and receipt-backed entries. This is
 * deliberately NOT a database table:
 *
 * - Evaluation Mode's own existing state ([EvaluationModeFormatter]) is already process-lifetime-only
 *   by design (see its doc comment) -- a persistent journal would be the only piece of Evaluation Mode
 *   state that survives a restart, which is inconsistent with how the rest of the feature already
 *   behaves and would need its own migration, backup/sync opt-out, and termination-recovery design to
 *   avoid exactly the corruption risks this feature exists to prevent.
 * - Evaluation Mode is inherently a short test session (install a build, run through a scripted
 *   evidence-capture pass, uninstall/reinstall). A journal that resets on process death matches that
 *   usage pattern and eliminates an entire class of persistence bugs (half-written entries, migration
 *   version skew, backup/restore interaction) by construction -- there is nothing to corrupt.
 * - This is a documented, deliberate choice per the feature's own explicit instruction to "choose
 *   between persistent and in-memory storage only after inspecting the existing architecture."
 *
 * The journal never performs a generic snapshot/rollback. Every entry is a typed inverse operation
 * (previous rating / previous Not Interested state) restored through the exact same
 * [tachiyomi.domain.taste.interactor.SetMangaTaste]/[tachiyomi.domain.taste.interactor.ClearMangaTaste]/
 * `SeenRecommendationMangaStore` calls a normal rating change already uses -- see
 * `EvaluationModeUndoService.kt` for the restore logic itself.
 */
enum class EvaluationJournalActionType {
    RATE_LOVE,
    RATE_LIKE,
    RATE_DISLIKE,
    CLEAR_RATING,
    NOT_INTERESTED,
}

/**
 * One reversible action. [mangaId] is the stable database identity when known (may be absent for a
 * recommendation candidate that was never persisted to the library); [source]/[url] is always present
 * and is the identity every write path (`SetMangaTaste`, `ClearMangaTaste`,
 * `SeenRecommendationMangaStore`) already keys off, so it is what undo actually restores by.
 *
 * Only the fields a supported action can change are ever recorded -- no manga metadata, no source or
 * repository names/URLs, no credentials, no raw logs.
 */
data class EvaluationJournalEntry(
    val id: String,
    val timestamp: Long,
    val actionType: EvaluationJournalActionType,
    val mangaId: Long?,
    val source: Long,
    val url: String,
    val previousRating: Int?,
    val newRating: Int?,
    val previousNotInterested: Boolean,
    val newNotInterested: Boolean,
    val isBulk: Boolean,
    val bulkOperationId: String?,
    val changedFields: Set<String>,
    val schemaVersion: Int = SCHEMA_VERSION,
    val reversible: Boolean = true,
) {
    companion object {
        const val SCHEMA_VERSION = 1
        const val FIELD_RATING = "rating"
        const val FIELD_NOT_INTERESTED = "not_interested"

        fun newId(): String = UUID.randomUUID().toString()
        fun newBulkId(): String = UUID.randomUUID().toString()
    }
}

/**
 * Result of restoring the journaled previous state against the manga's *current* state.
 * [MATCHED] means the current state equaled [EvaluationJournalEntry.newRating]/[newNotInterested]
 * (i.e. nothing changed the manga since this journal entry was recorded), so it was safe to restore.
 * [CONFLICT] means a later change (by this journal or a plain user action) altered the manga after
 * this entry -- the entry is left in place, unrestored, and reported to the user rather than silently
 * overwritten or force-restored.
 */
enum class EvaluationUndoItemResult { RESTORED, CONFLICT, MISSING, FAILED }

data class EvaluationUndoOutcome(
    val requestedCount: Int,
    val restoredCount: Int,
    val conflictCount: Int,
    val missingCount: Int,
    val failedCount: Int,
) {
    val allRestored: Boolean get() = restoredCount == requestedCount && requestedCount > 0
    val partial: Boolean get() = restoredCount in 1 until requestedCount
    val noneRestored: Boolean get() = restoredCount == 0 && requestedCount > 0
}

/**
 * Bounded, thread-safe, in-memory journal. Capacity is fixed at [MAX_ENTRIES] -- chosen as a small
 * round number appropriate for a manual test/evidence-capture session (enough to cover a realistic
 * scripted pass through a handful of screens without unbounded growth). Only the oldest entries are ever evicted --
 * eviction never touches manga, rating, or group data, only this in-memory list.
 *
 * Eviction is operation-aware: if the oldest entry belongs to a bulk operation (shares a
 * [EvaluationJournalEntry.bulkOperationId] with other entries), the *whole* bulk group is evicted
 * together, even if that drops the journal below [MAX_ENTRIES] by more than one. This guarantees a
 * bulk action's Undo group is never left partially evicted -- either the whole group is undo-able or
 * none of it is, never a silently incomplete subset.
 */
object EvaluationModeUndoJournal {
    const val MAX_ENTRIES = 20

    private val lock = Any()
    private val entries = ArrayDeque<EvaluationJournalEntry>()

    /** Adds [entry], evicting the oldest entry (or its whole bulk group) if over [MAX_ENTRIES]. */
    fun record(entry: EvaluationJournalEntry) {
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

    /** Most recent first. */
    fun snapshot(): List<EvaluationJournalEntry> = synchronized(lock) { entries.toList().asReversed() }

    fun removeById(id: String) {
        synchronized(lock) { entries.removeAll { it.id == id } }
    }

    fun removeByBulkId(bulkId: String) {
        synchronized(lock) { entries.removeAll { it.bulkOperationId == bulkId } }
    }

    fun entriesForBulk(bulkId: String): List<EvaluationJournalEntry> =
        synchronized(lock) { entries.filter { it.bulkOperationId == bulkId } }

    fun clear() {
        synchronized(lock) { entries.clear() }
    }

    fun isEmpty(): Boolean = synchronized(lock) { entries.isEmpty() }
}
// KMK <--

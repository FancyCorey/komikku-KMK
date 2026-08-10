package exh.util

import tachiyomi.domain.taste.model.CrossSourceGroupPrimary
import tachiyomi.domain.taste.model.CrossSourceMangaLink
import java.util.UUID

// KMK v0.8.20 -->
/**
 * Evaluation Mode Undo Journal for the cross-source link-group mutations (merge, remove-from-group,
 * ungroup) -- a sibling typed family in the broader Action History, kept separate rather than folded
 * into [EvaluationJournalEntry] because a group
 * mutation snapshots a different, richer shape: a set of complete link rows plus primary-version rows
 * spanning potentially several groups, not a single manga's rating.
 *
 * Same in-memory-only, bounded, typed-inverse design as [EvaluationModeUndoJournal]. No generic
 * snapshot/rollback: every field below is a concrete, named
 * link/primary row, never an opaque blob.
 */
data class RatedLinkKey(val source: Long, val url: String)

enum class GroupJournalActionType { MERGE, REMOVE_FROM_GROUP, UNGROUP, SET_PRIMARY }

/** Immutable snapshot of a `manga_cross_source_link` row, safe to re-upsert verbatim during restore. */
data class GroupLinkSnapshot(
    val source: Long,
    val url: String,
    val groupId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toLink() = CrossSourceMangaLink(source = source, url = url, groupId = groupId, title = title, createdAt = createdAt, updatedAt = updatedAt)

    companion object {
        fun from(link: CrossSourceMangaLink) = GroupLinkSnapshot(link.source, link.url, link.groupId, link.title, link.createdAt, link.updatedAt)
    }
}

/** Immutable snapshot of a `manga_cross_source_group_primary` row. */
data class GroupPrimarySnapshot(val groupId: String, val source: Long, val url: String, val updatedAt: Long) {
    fun toPrimary() = CrossSourceGroupPrimary(groupId = groupId, source = source, url = url, updatedAt = updatedAt)

    companion object {
        fun from(primary: CrossSourceGroupPrimary) = GroupPrimarySnapshot(primary.groupId, primary.source, primary.url, primary.updatedAt)
    }
}

/**
 * One reversible group mutation. [touchedKeys] is every `(source, url)` whose link row this action
 * changed; [previousLinks] holds that key's pre-action row (`null` = the key had no link row at all
 * before this action, e.g. a newly-grouped item). [expectedPostLinks] is what each key's row looks
 * like immediately after this action succeeded (`null` = the action deleted the row) -- used purely
 * for conflict detection, never written back directly. [touchedGroupIds]/[previousPrimaries]/
 * [expectedPostPrimaries] are the same idea for the `manga_cross_source_group_primary` table.
 *
 * A single entry always describes one whole operation (one merge, one remove-selection batch, one
 * ungroup) -- unlike [EvaluationJournalEntry]'s one-entry-per-manga model, there is no separate
 * "bulk" flag here because the entry itself is already the indivisible unit. This means eviction can
 * never split an operation: evicting the oldest entry always evicts the operation whole.
 */
data class GroupJournalEntry(
    val id: String,
    val timestamp: Long,
    val actionType: GroupJournalActionType,
    val touchedKeys: Set<RatedLinkKey>,
    val previousLinks: Map<RatedLinkKey, GroupLinkSnapshot?>,
    val expectedPostLinks: Map<RatedLinkKey, GroupLinkSnapshot?>,
    val touchedGroupIds: Set<String>,
    val previousPrimaries: Map<String, GroupPrimarySnapshot?>,
    val expectedPostPrimaries: Map<String, GroupPrimarySnapshot?>,
    val reversible: Boolean = true,
) {
    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}

enum class GroupUndoResult { RESTORED, CONFLICT, FAILED }

data class GroupUndoOutcome(val result: GroupUndoResult, val actionType: GroupJournalActionType? = null)

/**
 * Bounded, thread-safe, in-memory journal for [GroupJournalEntry]. Capacity is intentionally smaller
 * than [EvaluationModeUndoJournal.MAX_ENTRIES] (10 vs 20) because a group entry is a whole operation
 * (potentially many rows), not a single manga action -- 10 recent group operations is already a
 * generous manual-test-session budget.
 */
object GroupUndoJournal {
    const val MAX_ENTRIES = 10

    private val lock = Any()
    private val entries = ArrayDeque<GroupJournalEntry>()

    fun record(entry: GroupJournalEntry) {
        synchronized(lock) {
            entries.addLast(entry)
            while (entries.size > MAX_ENTRIES) {
                entries.removeFirst()
            }
        }
    }

    /** Most recent first. */
    fun snapshot(): List<GroupJournalEntry> = synchronized(lock) { entries.toList().asReversed() }

    fun removeById(id: String) {
        synchronized(lock) { entries.removeAll { it.id == id } }
    }

    fun clear() {
        synchronized(lock) { entries.clear() }
    }

    fun isEmpty(): Boolean = synchronized(lock) { entries.isEmpty() }
}
// KMK <--

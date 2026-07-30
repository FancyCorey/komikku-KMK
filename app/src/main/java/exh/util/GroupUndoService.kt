package exh.util

import kotlinx.coroutines.CancellationException
import tachiyomi.domain.taste.interactor.GetCrossSourceGroupPrimary
import tachiyomi.domain.taste.interactor.GetCrossSourceMangaLinks
import tachiyomi.domain.taste.interactor.RestoreCrossSourceGroupState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK v0.8.20 -->
/**
 * Pure conflict check for a single touched link key: true when the link row currently in the
 * database no longer matches what this entry's own action produced (i.e. something else re-grouped,
 * merged, or ungrouped this manga after the journal entry was recorded). Only `groupId` is compared --
 * `title`/timestamps do not change grouping semantics and comparing them would produce false conflicts
 * on no-op writes.
 */
fun groupUndoLinkConflicts(expected: GroupLinkSnapshot?, current: tachiyomi.domain.taste.model.CrossSourceMangaLink?): Boolean =
    expected?.groupId != current?.groupId

/** Pure conflict check for a single touched group's primary-version row: compares `(source, url)` only. */
fun groupUndoPrimaryConflicts(expected: GroupPrimarySnapshot?, current: tachiyomi.domain.taste.model.CrossSourceGroupPrimary?): Boolean =
    expected?.source != current?.source || expected?.url != current?.url

/**
 * Restore logic for [GroupUndoJournal] entries (merge / remove-from-group / ungroup).
 *
 * Restore contract, per entry:
 * 1. Re-read the *current* link row for every [GroupJournalEntry.touchedKeys] and the current primary
 *    row for every [GroupJournalEntry.touchedGroupIds].
 * 2. If any current row no longer matches the entry's own recorded post-action state
 *    ([GroupJournalEntry.expectedPostLinks]/[expectedPostPrimaries]) -- something else changed the
 *    grouping after this entry was recorded -- the whole restore is refused as a conflict. **Never
 *    partially restored, never force-restored.**
 * 3. Otherwise, every previous link/primary row is written back in one atomic transaction
 *    ([RestoreCrossSourceGroupState]) -- rows that existed before are re-upserted verbatim, rows that
 *    did not exist before (this action created them) are deleted, and the same for primaries. Either
 *    the whole transaction commits or none of it does.
 * 4. The entry is removed from the journal only after the restore transaction succeeds.
 */
class GroupUndoService(
    private val getCrossSourceMangaLinks: GetCrossSourceMangaLinks = Injekt.get(),
    private val getCrossSourceGroupPrimary: GetCrossSourceGroupPrimary = Injekt.get(),
    private val restoreCrossSourceGroupState: RestoreCrossSourceGroupState = Injekt.get(),
) {
    suspend fun undo(entryId: String): GroupUndoOutcome {
        val entry = GroupUndoJournal.snapshot().find { it.id == entryId } ?: return GroupUndoOutcome(GroupUndoResult.FAILED)
        if (!entry.reversible) return GroupUndoOutcome(GroupUndoResult.CONFLICT, entry.actionType)
        return try {
            val hasConflict = entry.touchedKeys.any { key ->
                val current = getCrossSourceMangaLinks.awaitBySourceUrl(key.source, key.url)
                groupUndoLinkConflicts(entry.expectedPostLinks[key], current)
            } || entry.touchedGroupIds.any { groupId ->
                val current = getCrossSourceGroupPrimary.awaitByGroupId(groupId)
                groupUndoPrimaryConflicts(entry.expectedPostPrimaries[groupId], current)
            }
            if (hasConflict) {
                return GroupUndoOutcome(GroupUndoResult.CONFLICT, entry.actionType)
            }

            val linkUpserts = entry.previousLinks.values.filterNotNull().map { it.toLink() }
            val linkDeletes = entry.touchedKeys.filter { entry.previousLinks[it] == null }.map { it.source to it.url }
            val primaryUpserts = entry.previousPrimaries.values.filterNotNull().map { it.toPrimary() }
            val primaryDeletes = entry.touchedGroupIds.filter { entry.previousPrimaries[it] == null }

            restoreCrossSourceGroupState.await(linkUpserts, linkDeletes, primaryUpserts, primaryDeletes)
            GroupUndoJournal.removeById(entry.id)
            GroupUndoOutcome(GroupUndoResult.RESTORED, entry.actionType)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GroupUndoOutcome(GroupUndoResult.FAILED, entry.actionType)
        }
    }
}
// KMK <--

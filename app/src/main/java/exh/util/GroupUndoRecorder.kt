package exh.util

import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.domain.taste.model.CrossSourceGroupPrimary
import tachiyomi.domain.taste.model.CrossSourceMangaLink

// KMK v0.8.20 -->
/**
 * Builds not-yet-committed [GroupJournalEntry] snapshots for the three group mutations (merge,
 * remove-from-group, ungroup). Mirrors [EvaluationModeJournalRecorder]'s build-before/commit-after
 * contract: every `build*` function here only reads pre-action state and returns an entry -- callers
 * must call [GroupUndoJournal.record] themselves, and only after their write actually succeeds, so a
 * failed mutation can never leave a stale journal entry describing a change that never happened.
 *
 * No-ops entirely (returns `null`, no journal entry, no extra state capture) when Evaluation Mode is
 * disabled -- normal users get no group-undo journal behavior and no extra reads.
 */
object GroupUndoRecorder {

    /**
     * @param plan the already-computed merge plan (see `RatedGroupMergePlanner`); [plan]'s `writes`
     * are exactly the post-action expected state for every touched key.
     * @param existingGroupMembers the same pre-merge `groupId -> members` map the caller already
     * fetched to build [plan] -- reused here as the source of pre-action link snapshots so no extra
     * database read is needed.
     */
    fun buildMergeEntry(
        sourcePreferences: SourcePreferences,
        plan: exh.recs.loved.RatedGroupMergePlanner.MergePlan,
        existingGroupMembers: Map<String, List<CrossSourceMangaLink>>,
    ): GroupJournalEntry? {
        if (!sourcePreferences.evaluationMode().get() || plan.writes.isEmpty()) return null
        val previousByKey: Map<RatedLinkKey, CrossSourceMangaLink> = existingGroupMembers.values
            .flatten()
            .associateBy { RatedLinkKey(it.source, it.url) }

        val touchedKeys = plan.writes.map { RatedLinkKey(it.source, it.url) }.toSet()
        val previousLinks = touchedKeys.associateWith { key -> previousByKey[key]?.let { GroupLinkSnapshot.from(it) } }
        val expectedPostLinks = plan.writes.associate { RatedLinkKey(it.source, it.url) to GroupLinkSnapshot.from(it) }

        return GroupJournalEntry(
            id = GroupJournalEntry.newId(),
            timestamp = System.currentTimeMillis(),
            actionType = GroupJournalActionType.MERGE,
            touchedKeys = touchedKeys,
            previousLinks = previousLinks,
            expectedPostLinks = expectedPostLinks,
            // KMK v0.8.20: mergeSelectedIntoGroup() never touches primary-version rows, so there is
            // nothing to snapshot/restore there for a merge.
            touchedGroupIds = emptySet(),
            previousPrimaries = emptyMap(),
            expectedPostPrimaries = emptyMap(),
        )
    }

    /** @param removals each removed key paired with its full pre-removal link row (read before the delete). */
    fun buildRemoveFromGroupEntry(
        sourcePreferences: SourcePreferences,
        removals: List<Pair<RatedLinkKey, CrossSourceMangaLink>>,
    ): GroupJournalEntry? {
        if (!sourcePreferences.evaluationMode().get() || removals.isEmpty()) return null
        val touchedKeys = removals.map { it.first }.toSet()
        val previousLinks = removals.associate { (key, link) -> key to GroupLinkSnapshot.from(link) }
        val expectedPostLinks = touchedKeys.associateWith { null as GroupLinkSnapshot? }
        return GroupJournalEntry(
            id = GroupJournalEntry.newId(),
            timestamp = System.currentTimeMillis(),
            actionType = GroupJournalActionType.REMOVE_FROM_GROUP,
            touchedKeys = touchedKeys,
            previousLinks = previousLinks,
            expectedPostLinks = expectedPostLinks,
            touchedGroupIds = emptySet(),
            previousPrimaries = emptyMap(),
            expectedPostPrimaries = emptyMap(),
        )
    }

    /** @param previousLinks every link row the group had, read before the ungroup delete. */
    fun buildUngroupEntry(
        sourcePreferences: SourcePreferences,
        groupId: String,
        previousLinks: List<CrossSourceMangaLink>,
        previousPrimary: CrossSourceGroupPrimary?,
    ): GroupJournalEntry? {
        if (!sourcePreferences.evaluationMode().get() || previousLinks.isEmpty()) return null
        val touchedKeys = previousLinks.map { RatedLinkKey(it.source, it.url) }.toSet()
        val previousLinkSnapshots = previousLinks.associate { RatedLinkKey(it.source, it.url) to GroupLinkSnapshot.from(it) }
        val expectedPostLinks = touchedKeys.associateWith { null as GroupLinkSnapshot? }
        return GroupJournalEntry(
            id = GroupJournalEntry.newId(),
            timestamp = System.currentTimeMillis(),
            actionType = GroupJournalActionType.UNGROUP,
            touchedKeys = touchedKeys,
            previousLinks = previousLinkSnapshots,
            expectedPostLinks = expectedPostLinks,
            touchedGroupIds = setOf(groupId),
            previousPrimaries = mapOf(groupId to previousPrimary?.let { GroupPrimarySnapshot.from(it) }),
            expectedPostPrimaries = mapOf(groupId to null),
        )
    }

    /** Builds the narrow inverse for changing only a group's selected primary version. */
    fun buildSetPrimaryEntry(
        sourcePreferences: SourcePreferences,
        groupId: String,
        previousPrimary: CrossSourceGroupPrimary?,
        newPrimary: RatedLinkKey,
    ): GroupJournalEntry? {
        if (!sourcePreferences.evaluationMode().get()) return null
        val expectedPrimary = GroupPrimarySnapshot(
            groupId = groupId,
            source = newPrimary.source,
            url = newPrimary.url,
            updatedAt = previousPrimary?.updatedAt ?: 0L,
        )
        val previousSnapshot = previousPrimary?.let { GroupPrimarySnapshot.from(it) }
        if (previousSnapshot?.source == expectedPrimary.source && previousSnapshot.url == expectedPrimary.url) return null
        return GroupJournalEntry(
            id = GroupJournalEntry.newId(),
            timestamp = System.currentTimeMillis(),
            actionType = GroupJournalActionType.SET_PRIMARY,
            touchedKeys = emptySet(),
            previousLinks = emptyMap(),
            expectedPostLinks = emptyMap(),
            touchedGroupIds = setOf(groupId),
            previousPrimaries = mapOf(groupId to previousSnapshot),
            expectedPostPrimaries = mapOf(groupId to expectedPrimary),
        )
    }
}
// KMK <--

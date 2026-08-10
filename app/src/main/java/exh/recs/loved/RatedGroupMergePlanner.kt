package exh.recs.loved

import tachiyomi.domain.taste.model.CrossSourceMangaLink

// KMK --> v0.8.0
/**
 * Pure planner for "Merge Selected Into Group". No Android/DB dependencies — fully unit-testable.
 *
 * Never merges by title. Title is only used as fallback display text for a brand-new link row when
 * no existing link/title is available; it is never read to decide which entries belong together.
 * Grouping intent comes exclusively from the caller's manual selection.
 */
object RatedGroupMergePlanner {

    data class SelectedEntry(
        val key: RatedMangaKey,
        val title: String,
        val existingGroupId: String?,
    )

    data class MergePlan(
        val targetGroupId: String,
        val isNewGroup: Boolean,
        /** Other groups being folded into [targetGroupId] (never includes [targetGroupId] itself). */
        val mergedGroupIds: Set<String>,
        val writes: List<CrossSourceMangaLink>,
    )

    /**
     * @param selected the user's manual multi-selection. Requires at least 2 entries — returns null
     * otherwise (callers should gate the UI action on selection size before invoking this).
     * @param existingGroupMembers groupId -> all current member links of that group, for every
     * distinct [SelectedEntry.existingGroupId] present in [selected]. Used to fully fold every member
     * of a merged-away group into the target, not just the selected subset of it (plan step 5).
     */
    fun plan(
        selected: List<SelectedEntry>,
        existingGroupMembers: Map<String, List<CrossSourceMangaLink>>,
        now: Long,
        newGroupIdProvider: () -> String,
    ): MergePlan? {
        if (selected.size < 2) return null

        val distinctGroups = selected.mapNotNull { it.existingGroupId }.distinct().sorted()
        val isNewGroup = distinctGroups.isEmpty()
        val targetGroupId = distinctGroups.firstOrNull() ?: newGroupIdProvider()
        val mergedGroupIds = distinctGroups.drop(1).toSet()

        val writes = mutableListOf<CrossSourceMangaLink>()
        val written = mutableSetOf<RatedMangaKey>()

        // Fold every member of every merged-away group into the target — a genuine merge, not just
        // rewriting the selected subset.
        for (groupId in mergedGroupIds) {
            existingGroupMembers[groupId].orEmpty().forEach { link ->
                val key = RatedMangaKey(link.source, link.url)
                writes += link.copy(groupId = targetGroupId, updatedAt = now)
                written += key
            }
        }

        // Ensure every selected entry ends up linked into the target group.
        for (entry in selected) {
            if (entry.key in written) continue
            val existingLink = entry.existingGroupId
                ?.let { existingGroupMembers[it] }
                ?.find { it.source == entry.key.source && it.url == entry.key.url }
            writes += CrossSourceMangaLink(
                source = entry.key.source,
                url = entry.key.url,
                groupId = targetGroupId,
                title = existingLink?.title ?: entry.title,
                createdAt = existingLink?.createdAt ?: now,
                updatedAt = now,
            )
            written += entry.key
        }

        return MergePlan(
            targetGroupId = targetGroupId,
            isNewGroup = isNewGroup,
            mergedGroupIds = mergedGroupIds,
            writes = writes,
        )
    }
}
// KMK <--

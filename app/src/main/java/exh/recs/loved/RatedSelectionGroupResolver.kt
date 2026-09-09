package exh.recs.loved

// KMK v0.8.7 -->
/**
 * Pure resolver for "which single confirmed group does the current selection belong to, if any" —
 * used to decide whether the bulk-selection bottom bar's "Group" button should merge the selection
 * (2+ items, any groups) versus expand it to a single existing group (documented behavior "Select All
 * In Group"), and whether "Select All In Group" should be offered in the bottom bar's More menu at
 * all. Extracted out of [exh.recs.loved.RatedMangaScreen] so the group-conflict rule — no single
 * answer when the selection spans zero or multiple confirmed groups — is directly unit testable.
 */
object RatedSelectionGroupResolver {

    /**
     * @return the one confirmed group every selected item belongs to, or null when the selection is
     * empty, contains at least one ungrouped item, or spans more than one confirmed group (a
     * conflict — there is no single unambiguous group to act on).
     */
    fun resolveSingleGroup(items: List<LovedDisplayItem>, selectedKeys: Set<RatedMangaKey>): String? {
        if (selectedKeys.isEmpty()) return null
        val selectedItems = items.filter { it.key in selectedKeys }
        if (selectedItems.size != selectedKeys.size) {
            // Some selected key had no matching item (stale selection) -- treat as a conflict rather
            // than guessing which group was intended.
            return null
        }
        val groupIds = selectedItems.map { it.confirmedGroupId }
        if (groupIds.any { it == null }) return null
        return groupIds.filterNotNull().distinct().singleOrNull()
    }
}
// KMK <--

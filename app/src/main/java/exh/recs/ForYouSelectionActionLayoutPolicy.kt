package exh.recs

// KMK v0.8.16-fix1 -->
/**
 * Pure decision for how [BrowsePersonalRecommendationsTab]'s selection bottom bar lays out its
 * actions at a given width. Six always-visible text buttons (Love/Like/Dislike/Not interested/Find
 * best version/Open) fit wide layouts but are too crowded for phone portrait. This does not remove
 * any action: on wide
 * layouts every action stays directly visible; on compact layouts the less-common actions move into
 * a "More" overflow, never disappearing.
 */
object ForYouSelectionActionLayoutPolicy {

    /** Below this width, use the compact layout (primary actions + More overflow). */
    const val COMPACT_WIDTH_THRESHOLD_DP = 720

    enum class Layout { WIDE, COMPACT }

    fun layoutFor(maxWidthDp: Int): Layout =
        if (maxWidthDp >= COMPACT_WIDTH_THRESHOLD_DP) Layout.WIDE else Layout.COMPACT

    data class OverflowActions(
        val notInterested: Boolean,
        val findBestVersion: Boolean,
        val open: Boolean,
        // KMK v0.8.17-fix1: Clear Rating, same overflow treatment as Not interested (always
        // available regardless of selection count, unlike Find best version/Open).
        val clearRating: Boolean,
    )

    /**
     * On [Layout.COMPACT], Love/Like/Dislike stay directly visible; Not interested and Clear Rating
     * always move to More, and Find best version/Open move to More only when they would be shown at
     * all (i.e. only with exactly one item selected -- unchanged single-selection-only eligibility).
     */
    fun overflowActions(layout: Layout, isSingleSelection: Boolean): OverflowActions =
        when (layout) {
            Layout.WIDE -> OverflowActions(notInterested = false, findBestVersion = false, open = false, clearRating = false)
            Layout.COMPACT -> OverflowActions(
                notInterested = true,
                findBestVersion = isSingleSelection,
                open = isSingleSelection,
                clearRating = true,
            )
        }
}
// KMK <--

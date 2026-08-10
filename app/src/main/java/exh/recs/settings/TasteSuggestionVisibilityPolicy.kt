package exh.recs.settings

// KMK v0.8.11 -->
/**
 * Pure progressive-reveal policy for one group of items (Taste Suggestions' Preferred/Blocked
 * groups, and -- as of v0.8.12 -- [TagPreferenceGroup]'s stored tag preference groups), so all
 * capped/expandable groups behave the same way -- independently per group, so expanding Preferred
 * never affects Blocked and vice versa.
 *
 * KMK v0.8.12: replaced the boolean `expanded` flag with an integer visible-count. The previous
 * `Show more` action revealed *every* remaining item at once despite its "(%d)" label implying a
 * bounded reveal -- this now genuinely reveals [REVEAL_STEP] more at a time, with a separate
 * `Show all` action for revealing everything in one tap, and `Show fewer` collapsing back to
 * [DEFAULT_VISIBLE].
 */
object TasteSuggestionVisibilityPolicy {
    const val DEFAULT_VISIBLE = 10
    const val REVEAL_STEP = 10

    /** Items currently shown for [visibleCount] against a group of [items]. */
    fun <T> visible(items: List<T>, visibleCount: Int): List<T> =
        if (visibleCount >= items.size) items else items.take(visibleCount)

    /** The next visible count after tapping "Show N more" -- capped at the group's total size. */
    fun nextVisibleCount(totalCount: Int, currentVisibleCount: Int): Int =
        (currentVisibleCount + REVEAL_STEP).coerceAtMost(totalCount)

    /** True when a "Show N more" action should be offered (more remain, but not all are shown yet). */
    fun canShowMore(totalCount: Int, currentVisibleCount: Int): Boolean =
        currentVisibleCount < totalCount

    /**
     * True when a distinct "Show all" action should be offered alongside "Show more" -- i.e. more
     * than one more reveal step would otherwise be needed to reach the end.
     */
    fun canShowAll(totalCount: Int, currentVisibleCount: Int): Boolean =
        totalCount - currentVisibleCount > REVEAL_STEP

    /** True when a "Show fewer" (collapse) action should be offered. */
    fun canShowFewer(currentVisibleCount: Int): Boolean = currentVisibleCount > DEFAULT_VISIBLE
}
// KMK <--

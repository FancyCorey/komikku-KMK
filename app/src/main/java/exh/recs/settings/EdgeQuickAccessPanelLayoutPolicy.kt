package exh.recs.settings

/**
 * Pure layout policy for [EdgeQuickAccessPanel], extracted so the panel's sizing decisions are
 * testable without rendering Compose or reading a screenshot.
 *
 * ## Defects this policy exists to fix
 *
 * The previous panel hardcoded `fillMaxHeight(0.7f)` + a top-packed, unscrollable `Column` of
 * fixed-`96.dp` / `10.sp` items behind a `28.dp`-wide handle. That produced, in order of severity:
 *
 * 1. **Clipped, unreachable destinations.** On a short screen (landscape, small phone, split screen)
 *    0.7x height can be smaller than the five items need, and nothing scrolled.
 * 2. **Dead empty space and poor balance.** On a tall screen the fixed fraction reserved far more
 *    height than the content used, and `Column`'s default `Top` arrangement pushed everything up,
 *    leaving a large empty region -- the reported "poor centering / weak balance".
 * 3. **Cramped, unreadable labels.** `10.sp` is below the platform's smallest standard label size
 *    and the fixed `96.dp` width ignored screen width entirely.
 * 4. **Undersized hit targets.** The handle was 28dp wide.
 *
 * ## The 28dp handle decision is preserved deliberately
 *
 * The handle's *drawn* width stays [HANDLE_VISUAL_WIDTH_DP] because that narrowness is an explicit
 * Android edge-back-gesture safety decision carried forward from v0.8.18 -- widening the drawn
 * handle would push it further into the system gesture inset. The *touch* target is raised to
 * [MIN_TOUCH_TARGET_DP] via the platform's minimum-interactive-size mechanism instead, which expands
 * the interactive region without changing the painted geometry. No blind swipe coordinates are
 * introduced.
 */
object EdgeQuickAccessPanelLayoutPolicy {

    /** Platform minimum interactive dimension. */
    const val MIN_TOUCH_TARGET_DP = 48

    /** Painted width of the edge handle. Deliberately narrow -- see the class KDoc. */
    const val HANDLE_VISUAL_WIDTH_DP = 28

    /** Painted height of the edge handle. */
    const val HANDLE_VISUAL_HEIGHT_DP = 64

    /** Item width on a narrow (phone) container. */
    const val ITEM_WIDTH_COMPACT_DP = 104

    /** Item width once the container is wide enough to afford it (tablet/landscape). */
    const val ITEM_WIDTH_EXPANDED_DP = 128

    /** Container width at or above which [ITEM_WIDTH_EXPANDED_DP] is used. */
    const val EXPANDED_WIDTH_BREAKPOINT_DP = 600

    /** Minimum height of one destination item, so every row is comfortably tappable. */
    const val ITEM_MIN_HEIGHT_DP = 64

    /**
     * Fraction of container height the panel may occupy before it starts scrolling. The panel now
     * *wraps* its content and only grows to this bound, instead of always claiming it.
     */
    const val MAX_HEIGHT_FRACTION = 0.9f

    /** Resolves the destination item width for a container of [containerWidthDp]. */
    fun itemWidthDp(containerWidthDp: Int): Int =
        if (containerWidthDp >= EXPANDED_WIDTH_BREAKPOINT_DP) ITEM_WIDTH_EXPANDED_DP else ITEM_WIDTH_COMPACT_DP

    /**
     * Estimated height the panel's content needs for [destinationCount] items, including the column's
     * own vertical padding and inter-item spacing. Used to decide whether the panel must scroll.
     */
    fun estimatedContentHeightDp(destinationCount: Int, itemSpacingDp: Int, verticalPaddingDp: Int): Int {
        if (destinationCount <= 0) return 0
        return (destinationCount * ITEM_MIN_HEIGHT_DP) +
            ((destinationCount - 1) * itemSpacingDp) +
            (verticalPaddingDp * 2)
    }

    /**
     * True when the content cannot fit inside [MAX_HEIGHT_FRACTION] of the container and must
     * therefore scroll rather than clip. This is the invariant that makes every destination
     * reachable at any screen size.
     */
    fun requiresScroll(
        destinationCount: Int,
        containerHeightDp: Int,
        itemSpacingDp: Int,
        verticalPaddingDp: Int,
    ): Boolean {
        if (containerHeightDp <= 0) return false
        val available = (containerHeightDp * MAX_HEIGHT_FRACTION).toInt()
        return estimatedContentHeightDp(destinationCount, itemSpacingDp, verticalPaddingDp) > available
    }

    /**
     * True when the drawn handle is smaller than the platform minimum and therefore needs its touch
     * target expanded rather than its painted size changed.
     */
    fun handleNeedsExpandedTouchTarget(): Boolean =
        HANDLE_VISUAL_WIDTH_DP < MIN_TOUCH_TARGET_DP
}
// KMK <--

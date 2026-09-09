package exh.recs.loved

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// KMK HR-2026-08-26-SMALL-PHONE-RATED-COLLECTION-LAYOUT -->
/**
 * Pure width-capping math for [RatedMangaCollectionContent]'s per-item version-count badge
 * (top-end corner of a rated-manga grid cell). Extracted from the Composable so the "never let the
 * badge claim the corner menu button's own space" invariant is directly testable without Compose.
 *
 * The version-count badge (a plural string like "6 versions") and the per-item overflow menu
 * trigger (a `MoreVert` icon button) are both absolutely positioned over the same
 * `GridCells.Adaptive(96.dp + padding)` grid cell -- the badge at top-end, the menu button at
 * top-start. `GridCells.Adaptive`'s cell width settles near its ~96-120dp floor on most phone
 * widths (adding columns, not making cells much wider, as the screen grows) -- so the badge's
 * natural (unbounded) width can reach or exceed the corner menu button's footprint whenever the
 * plural string is long (a high count, or a locale whose plural word is long) or the system font
 * scale is increased (an explicit accessibility validation point for this gate), independent of
 * whether the device itself is a "small phone." Capping the badge's max width relative to the
 * actual measured cell width, reserving the menu button's own footprint, prevents that overlap
 * structurally at any width/locale/font-scale rather than only for today's specific data.
 */
internal object RatedMangaVersionBadgeLayoutPolicy {

    /**
     * Footprint reserved for the top-start overflow menu trigger: a 28.dp [androidx.compose.material3
     * .IconButton] inside a [androidx.compose.material3.Surface] with 2.dp padding on each side
     * (32.dp total), plus a small clearance gap so the badge never touches it edge-to-edge.
     */
    val MENU_RESERVED_WIDTH: Dp = 40.dp

    /** Never let the cap collapse to something too small to render a truncated count legibly. */
    val MIN_BADGE_WIDTH: Dp = 24.dp

    /**
     * The maximum width the version-count badge may occupy within a cell of [cellWidth], leaving
     * [menuReservedWidth] clear for the corner menu trigger. Never returns less than
     * [MIN_BADGE_WIDTH] -- an extremely narrow cell still gets a renderable (if tightly truncated)
     * badge rather than a nonsensical zero/negative width.
     */
    fun maxBadgeWidth(cellWidth: Dp, menuReservedWidth: Dp = MENU_RESERVED_WIDTH): Dp {
        val available = cellWidth - menuReservedWidth
        return if (available < MIN_BADGE_WIDTH) MIN_BADGE_WIDTH else available
    }
}
// KMK <--

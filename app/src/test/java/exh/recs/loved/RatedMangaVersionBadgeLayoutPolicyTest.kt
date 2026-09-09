package exh.recs.loved

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * HR-2026-08-26-SMALL-PHONE-RATED-COLLECTION-LAYOUT: proves
 * [RatedMangaVersionBadgeLayoutPolicy.maxBadgeWidth] never returns a width that would let the
 * version-count badge claim the corner overflow-menu button's own reserved space, at any cell
 * width -- the exact structural guarantee [RatedMangaScreen]'s per-item badge/menu overlay relies
 * on instead of a fixed dp guess that would only happen to work for today's data/locale/font scale.
 */
class RatedMangaVersionBadgeLayoutPolicyTest {

    @Test
    fun `a typical 3-column phone cell leaves the badge narrower than the cell minus the menu footprint`() {
        val cellWidth = 120.dp
        val result = RatedMangaVersionBadgeLayoutPolicy.maxBadgeWidth(cellWidth)

        assertEquals(80.dp, result)
        assertTrue(result + RatedMangaVersionBadgeLayoutPolicy.MENU_RESERVED_WIDTH <= cellWidth)
    }

    @Test
    fun `a wide tablet cell still reserves exactly the menu footprint, not a fixed fraction`() {
        val cellWidth = 400.dp
        val result = RatedMangaVersionBadgeLayoutPolicy.maxBadgeWidth(cellWidth)

        assertEquals(360.dp, result)
    }

    @Test
    fun `an extremely narrow cell (below the adaptive floor, or an extreme font-scale squeeze) floors at MIN_BADGE_WIDTH instead of going negative`() {
        val cellWidth = 30.dp
        val result = RatedMangaVersionBadgeLayoutPolicy.maxBadgeWidth(cellWidth)

        assertEquals(RatedMangaVersionBadgeLayoutPolicy.MIN_BADGE_WIDTH, result)
        assertTrue(result > 0.dp, "the cap must never collapse to zero or negative width")
    }

    @Test
    fun `a custom menu-reserved width is honored instead of the default`() {
        val result = RatedMangaVersionBadgeLayoutPolicy.maxBadgeWidth(cellWidth = 100.dp, menuReservedWidth = 20.dp)

        assertEquals(80.dp, result)
    }

    @Test
    fun `the result is monotonically non-decreasing as the cell widens`() {
        val widths = listOf(24.dp, 60.dp, 96.dp, 120.dp, 200.dp, 500.dp)
        val results = widths.map { RatedMangaVersionBadgeLayoutPolicy.maxBadgeWidth(it) }

        for (i in 1 until results.size) {
            assertTrue(
                results[i] >= results[i - 1],
                "maxBadgeWidth must never shrink as the cell widens: ${results[i - 1]} -> ${results[i]}",
            )
        }
    }
}

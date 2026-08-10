package exh.recs.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK v0.8.11 -->
// KMK v0.8.12: rewritten for the integer visible-count API -- see TasteSuggestionVisibilityPolicy's
// class doc for why the boolean `expanded` flag was replaced.
class TasteSuggestionVisibilityPolicyTest {

    @Test
    fun `35 items reveals 10, then 20, then 30, then all 35 via show more`() {
        val total = 35
        var visibleCount = TasteSuggestionVisibilityPolicy.DEFAULT_VISIBLE
        assertEquals(10, visibleCount)

        visibleCount = TasteSuggestionVisibilityPolicy.nextVisibleCount(total, visibleCount)
        assertEquals(20, visibleCount)

        visibleCount = TasteSuggestionVisibilityPolicy.nextVisibleCount(total, visibleCount)
        assertEquals(30, visibleCount)

        visibleCount = TasteSuggestionVisibilityPolicy.nextVisibleCount(total, visibleCount)
        assertEquals(35, visibleCount, "the final step is capped at the group's total size, not 40")
    }

    @Test
    fun `show all from 10 reveals every item`() {
        val items = (1..35).toList()
        val visible = TasteSuggestionVisibilityPolicy.visible(items, visibleCount = items.size)
        assertEquals(items, visible)
    }

    @Test
    fun `show fewer resets to 10`() {
        assertTrue(TasteSuggestionVisibilityPolicy.canShowFewer(currentVisibleCount = 30))
        assertFalse(TasteSuggestionVisibilityPolicy.canShowFewer(currentVisibleCount = TasteSuggestionVisibilityPolicy.DEFAULT_VISIBLE))
    }

    @Test
    fun `preferred and blocked groups are independent - a small group is never truncated by a large one`() {
        val small = listOf("a", "b", "c")
        val visible = TasteSuggestionVisibilityPolicy.visible(small, visibleCount = TasteSuggestionVisibilityPolicy.DEFAULT_VISIBLE)
        assertEquals(small, visible)
    }

    @Test
    fun `exactly 10 has no show-more or show-all controls`() {
        assertFalse(TasteSuggestionVisibilityPolicy.canShowMore(totalCount = 10, currentVisibleCount = 10))
        assertFalse(TasteSuggestionVisibilityPolicy.canShowAll(totalCount = 10, currentVisibleCount = 10))
    }

    @Test
    fun `an empty group stays empty`() {
        val empty = emptyList<Int>()
        assertEquals(emptyList<Int>(), TasteSuggestionVisibilityPolicy.visible(empty, visibleCount = TasteSuggestionVisibilityPolicy.DEFAULT_VISIBLE))
        assertFalse(TasteSuggestionVisibilityPolicy.canShowMore(totalCount = 0, currentVisibleCount = 0))
    }

    @Test
    fun `show all is offered only when more than one reveal step remains`() {
        // 20 items, 10 visible: exactly one more step (10) reaches the end -- show all would be
        // redundant with show more here.
        assertFalse(TasteSuggestionVisibilityPolicy.canShowAll(totalCount = 20, currentVisibleCount = 10))
        // 21 items, 10 visible: one more step only reaches 20, leaving 1 more -- show all is useful.
        assertTrue(TasteSuggestionVisibilityPolicy.canShowAll(totalCount = 21, currentVisibleCount = 10))
    }

    @Test
    fun `show more remains offered up to the last item`() {
        assertTrue(TasteSuggestionVisibilityPolicy.canShowMore(totalCount = 11, currentVisibleCount = 10))
    }
}
// KMK <--

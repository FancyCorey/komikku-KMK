package exh.recs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK v0.8.16-fix1 -->
class ForYouSelectionActionLayoutPolicyTest {

    @Test
    fun `width at or above the threshold uses the wide layout`() {
        assertEquals(
            ForYouSelectionActionLayoutPolicy.Layout.WIDE,
            ForYouSelectionActionLayoutPolicy.layoutFor(ForYouSelectionActionLayoutPolicy.COMPACT_WIDTH_THRESHOLD_DP),
        )
        assertEquals(ForYouSelectionActionLayoutPolicy.Layout.WIDE, ForYouSelectionActionLayoutPolicy.layoutFor(1000))
    }

    @Test
    fun `width below the threshold uses the compact layout`() {
        assertEquals(
            ForYouSelectionActionLayoutPolicy.Layout.COMPACT,
            ForYouSelectionActionLayoutPolicy.layoutFor(ForYouSelectionActionLayoutPolicy.COMPACT_WIDTH_THRESHOLD_DP - 1),
        )
        assertEquals(ForYouSelectionActionLayoutPolicy.Layout.COMPACT, ForYouSelectionActionLayoutPolicy.layoutFor(360))
    }

    @Test
    fun `wide layout never sends anything to overflow`() {
        val overflow = ForYouSelectionActionLayoutPolicy.overflowActions(
            ForYouSelectionActionLayoutPolicy.Layout.WIDE,
            isSingleSelection = true,
        )
        assertFalse(overflow.notInterested)
        assertFalse(overflow.findBestVersion)
        assertFalse(overflow.open)
        assertFalse(overflow.clearRating)
    }

    @Test
    fun `compact layout with single selection sends not-interested, find-best-version, open, and clear-rating to overflow`() {
        val overflow = ForYouSelectionActionLayoutPolicy.overflowActions(
            ForYouSelectionActionLayoutPolicy.Layout.COMPACT,
            isSingleSelection = true,
        )
        assertTrue(overflow.notInterested)
        assertTrue(overflow.findBestVersion)
        assertTrue(overflow.open)
        assertTrue(overflow.clearRating)
    }

    @Test
    fun `compact layout with multi selection still sends not-interested and clear-rating to overflow but not single-only actions`() {
        val overflow = ForYouSelectionActionLayoutPolicy.overflowActions(
            ForYouSelectionActionLayoutPolicy.Layout.COMPACT,
            isSingleSelection = false,
        )
        assertTrue(overflow.notInterested)
        assertFalse(overflow.findBestVersion)
        assertFalse(overflow.open)
        assertTrue(overflow.clearRating)
    }
}
// KMK <--

package exh.recs.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK
/**
 * Pure layout-policy tests for the repaired [EdgeQuickAccessPanel]. Each audited defect from the
 * documented layout defects has a corresponding assertion here, so the repair is proven
 * without needing a device screenshot.
 */
class EdgeQuickAccessPanelLayoutPolicyTest {

    /** The panel always shows exactly the five Recommendation Settings destinations. */
    private val destinationCount = RecommendationSettingsQuickAccessDestination.entries.size

    @Test
    fun `all five destinations are preserved`() {
        assertEquals(5, destinationCount)
        assertEquals(
            listOf(
                RecommendationSettingsQuickAccessDestination.ForYouSources,
                RecommendationSettingsQuickAccessDestination.TasteAndFilters,
                RecommendationSettingsQuickAccessDestination.SourceEvaluation,
                RecommendationSettingsQuickAccessDestination.SourcesToTry,
                RecommendationSettingsQuickAccessDestination.ManagementAndDiagnostics,
            ),
            RecommendationSettingsQuickAccessDestination.entries.toList(),
        )
    }

    // --- Hit targets ---

    @Test
    fun `every destination item meets the platform minimum touch height`() {
        assertTrue(
            EdgeQuickAccessPanelLayoutPolicy.ITEM_MIN_HEIGHT_DP >= EdgeQuickAccessPanelLayoutPolicy.MIN_TOUCH_TARGET_DP,
            "item min height ${EdgeQuickAccessPanelLayoutPolicy.ITEM_MIN_HEIGHT_DP}dp is below the 48dp minimum",
        )
    }

    @Test
    fun `every destination item is at least as wide as the platform minimum at any screen size`() {
        listOf(320, 360, 411, 600, 800, 1280).forEach { width ->
            assertTrue(
                EdgeQuickAccessPanelLayoutPolicy.itemWidthDp(width) >= EdgeQuickAccessPanelLayoutPolicy.MIN_TOUCH_TARGET_DP,
                "item width at ${width}dp is below the 48dp minimum",
            )
        }
    }

    @Test
    fun `the narrow drawn handle still needs an expanded touch target`() {
        // The 28dp painted width is a deliberate Android edge-back-gesture safety decision and must
        // stay; this asserts the code knows it has to compensate rather than silently shipping a
        // sub-minimum tap area.
        assertEquals(28, EdgeQuickAccessPanelLayoutPolicy.HANDLE_VISUAL_WIDTH_DP)
        assertTrue(EdgeQuickAccessPanelLayoutPolicy.handleNeedsExpandedTouchTarget())
    }

    @Test
    fun `the handle drawn height already exceeds the minimum touch target`() {
        assertTrue(
            EdgeQuickAccessPanelLayoutPolicy.HANDLE_VISUAL_HEIGHT_DP >= EdgeQuickAccessPanelLayoutPolicy.MIN_TOUCH_TARGET_DP,
        )
    }

    // --- Responsive width (label wrapping pressure) ---

    @Test
    fun `item width is responsive rather than a single hardcoded value`() {
        val compact = EdgeQuickAccessPanelLayoutPolicy.itemWidthDp(360)
        val expanded = EdgeQuickAccessPanelLayoutPolicy.itemWidthDp(800)
        assertTrue(expanded > compact, "a wide container should afford a wider item")
        assertEquals(EdgeQuickAccessPanelLayoutPolicy.ITEM_WIDTH_COMPACT_DP, compact)
        assertEquals(EdgeQuickAccessPanelLayoutPolicy.ITEM_WIDTH_EXPANDED_DP, expanded)
    }

    @Test
    fun `the compact item is wider than the previous cramped 96dp value`() {
        // The longest destination title ("Management and Diagnostics") previously had to be squeezed
        // into 96dp at 10sp with a 3-line cap; the repair widens the item and drops the cap.
        assertTrue(EdgeQuickAccessPanelLayoutPolicy.ITEM_WIDTH_COMPACT_DP > 96)
    }

    @Test
    fun `the expanded breakpoint matches the conventional tablet width boundary`() {
        assertEquals(600, EdgeQuickAccessPanelLayoutPolicy.EXPANDED_WIDTH_BREAKPOINT_DP)
        assertEquals(
            EdgeQuickAccessPanelLayoutPolicy.ITEM_WIDTH_EXPANDED_DP,
            EdgeQuickAccessPanelLayoutPolicy.itemWidthDp(600),
        )
        assertEquals(
            EdgeQuickAccessPanelLayoutPolicy.ITEM_WIDTH_COMPACT_DP,
            EdgeQuickAccessPanelLayoutPolicy.itemWidthDp(599),
        )
    }

    // --- Empty balance / clipping ---

    @Test
    fun `content height estimate accounts for spacing and padding, not just items`() {
        val bare = destinationCount * EdgeQuickAccessPanelLayoutPolicy.ITEM_MIN_HEIGHT_DP
        val estimated = EdgeQuickAccessPanelLayoutPolicy.estimatedContentHeightDp(destinationCount, itemSpacingDp = 4, verticalPaddingDp = 8)
        assertTrue(estimated > bare)
    }

    @Test
    fun `an empty destination list has no content height`() {
        assertEquals(0, EdgeQuickAccessPanelLayoutPolicy.estimatedContentHeightDp(0, 4, 8))
    }

    @Test
    fun `a tall phone fits all five destinations without needing to scroll`() {
        // 0.9 x 800dp = 720dp available; five 64dp items plus spacing/padding is far less. Combined
        // with wrapContentHeight this is what removes the old dead space instead of reserving a
        // fixed 70% of the screen.
        assertFalse(
            EdgeQuickAccessPanelLayoutPolicy.requiresScroll(destinationCount, containerHeightDp = 800, itemSpacingDp = 4, verticalPaddingDp = 8),
        )
    }

    @Test
    fun `a short landscape container scrolls instead of clipping destinations`() {
        // This is the latent defect the old fixed-fraction, unscrollable Column had: on a short
        // container the lower destinations were simply unreachable.
        assertTrue(
            EdgeQuickAccessPanelLayoutPolicy.requiresScroll(destinationCount, containerHeightDp = 320, itemSpacingDp = 4, verticalPaddingDp = 8),
        )
    }

    @Test
    fun `the panel never claims more than the bounded height fraction`() {
        assertTrue(EdgeQuickAccessPanelLayoutPolicy.MAX_HEIGHT_FRACTION <= 1.0f)
        assertTrue(
            EdgeQuickAccessPanelLayoutPolicy.MAX_HEIGHT_FRACTION > 0.7f,
            "the bound should exceed the old fixed 0.7 so tall content is not clipped before scrolling",
        )
    }

    @Test
    fun `a zero-height container never reports a scroll requirement`() {
        assertFalse(EdgeQuickAccessPanelLayoutPolicy.requiresScroll(destinationCount, 0, 4, 8))
        assertFalse(EdgeQuickAccessPanelLayoutPolicy.requiresScroll(destinationCount, -100, 4, 8))
    }
}
// KMK <--

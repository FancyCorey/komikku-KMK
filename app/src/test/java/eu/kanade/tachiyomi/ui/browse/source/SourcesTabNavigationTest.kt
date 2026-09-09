package eu.kanade.tachiyomi.ui.browse.source

import eu.kanade.tachiyomi.source.DebugBrowseFixtureSource
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreenModel.Listing
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SourcesTabNavigationTest {
    @Test
    fun `debug Browse fixture uses Browse owner even with new source navigation`() {
        assertFalse(
            shouldUseSourceFeedNavigation(
                sourceId = DebugBrowseFixtureSource.ID,
                listing = Listing.Popular,
                useNewSourceNavigation = true,
            ),
        )
    }

    @Test
    fun `ordinary popular source keeps new SourceFeed navigation`() {
        assertTrue(
            shouldUseSourceFeedNavigation(
                sourceId = 42L,
                listing = Listing.Popular,
                useNewSourceNavigation = true,
            ),
        )
    }

    @Test
    fun `disabled new navigation keeps Browse route for ordinary popular source`() {
        assertFalse(
            shouldUseSourceFeedNavigation(
                sourceId = 42L,
                listing = Listing.Popular,
                useNewSourceNavigation = false,
            ),
        )
    }

    @Test
    fun `non-popular listing keeps Browse route`() {
        assertFalse(
            shouldUseSourceFeedNavigation(
                sourceId = 42L,
                listing = Listing.Latest,
                useNewSourceNavigation = true,
            ),
        )
    }
}

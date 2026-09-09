package exh.recs.matching

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.tracker.model.LocalTrackedWorkStatus

// KMK -->
class CrossExtensionMatchRouteModeTest {

    @Test
    fun `Rating LOVE route args round trip`() {
        val mode = CrossExtensionMatchMode.Rating(MangaRating.LOVE)
        val args = CrossExtensionMatchRouteMode.fromMode(mode)
        val restored = CrossExtensionMatchRouteMode.toMode(args.modeKey, args.ratingValue)
        assertEquals(mode, restored)
    }

    @Test
    fun `Rating LIKE route args round trip`() {
        val mode = CrossExtensionMatchMode.Rating(MangaRating.LIKE)
        val args = CrossExtensionMatchRouteMode.fromMode(mode)
        val restored = CrossExtensionMatchRouteMode.toMode(args.modeKey, args.ratingValue)
        assertEquals(mode, restored)
    }

    @Test
    fun `Rating DISLIKE route args round trip`() {
        val mode = CrossExtensionMatchMode.Rating(MangaRating.DISLIKE)
        val args = CrossExtensionMatchRouteMode.fromMode(mode)
        val restored = CrossExtensionMatchRouteMode.toMode(args.modeKey, args.ratingValue)
        assertEquals(mode, restored)
    }

    @Test
    fun `Rating NOT_INTERESTED route args round trip`() {
        // KMK v0.8.21-fix3: R1 correction -- Not Interested is Rating(NOT_INTERESTED) now, encoded
        // and decoded through the same RATING path as every other value.
        val mode = CrossExtensionMatchMode.Rating(MangaRating.NOT_INTERESTED)
        val args = CrossExtensionMatchRouteMode.fromMode(mode)
        val restored = CrossExtensionMatchRouteMode.toMode(args.modeKey, args.ratingValue)
        assertEquals(mode, restored)
    }

    @Test
    fun `the legacy mark_seen route string still decodes to Rating(NOT_INTERESTED) for old serialized routes`() {
        // Read-compatibility only: fromMode() never encodes this anymore (confirmed by the test
        // above using the RATING path), but a route string serialized by a build before this
        // correction must still resolve to something sensible rather than crash/null out.
        val restored = CrossExtensionMatchRouteMode.toMode(CrossExtensionMatchRouteMode.MARK_SEEN, null)
        assertEquals(CrossExtensionMatchMode.Rating(MangaRating.NOT_INTERESTED), restored)
    }

    @Test
    fun `Favorite route args round trip`() {
        val mode = CrossExtensionMatchMode.Favorite
        val args = CrossExtensionMatchRouteMode.fromMode(mode)
        val restored = CrossExtensionMatchRouteMode.toMode(args.modeKey, args.ratingValue)
        assertEquals(mode, restored)
    }

    @Test
    fun `Local tracking route args round trip`() {
        val mode = CrossExtensionMatchMode.LocalTracking(LocalTrackedWorkStatus.COMPLETED)
        val args = CrossExtensionMatchRouteMode.fromMode(mode)
        val restored = CrossExtensionMatchRouteMode.toMode(args.modeKey, args.ratingValue, args.localStatus)
        assertEquals(mode, restored)
    }

    @Test
    fun `Local tracking route with invalid status is rejected safely`() {
        val result = CrossExtensionMatchRouteMode.toMode(
            CrossExtensionMatchRouteMode.LOCAL_TRACKING,
            null,
            LocalTrackedWorkStatus.entries.size,
        )
        assertNull(result)
    }

    @Test
    fun `unknown mode key is rejected safely`() {
        val result = CrossExtensionMatchRouteMode.toMode("unknown_mode", null)
        assertNull(result)
    }

    @Test
    fun `rating mode without rating value is rejected safely`() {
        val result = CrossExtensionMatchRouteMode.toMode(CrossExtensionMatchRouteMode.RATING, null)
        assertNull(result)
    }

    @Test
    fun `invalid rating value is rejected safely`() {
        val result = CrossExtensionMatchRouteMode.toMode(CrossExtensionMatchRouteMode.RATING, 999)
        assertNull(result)
    }
}
// KMK <--

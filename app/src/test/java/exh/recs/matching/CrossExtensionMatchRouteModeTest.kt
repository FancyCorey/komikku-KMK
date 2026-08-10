package exh.recs.matching

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.MangaRating

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
    fun `MarkSeen route args round trip`() {
        val mode = CrossExtensionMatchMode.MarkSeen
        val args = CrossExtensionMatchRouteMode.fromMode(mode)
        val restored = CrossExtensionMatchRouteMode.toMode(args.modeKey, args.ratingValue)
        assertEquals(mode, restored)
    }

    @Test
    fun `Favorite route args round trip`() {
        val mode = CrossExtensionMatchMode.Favorite
        val args = CrossExtensionMatchRouteMode.fromMode(mode)
        val restored = CrossExtensionMatchRouteMode.toMode(args.modeKey, args.ratingValue)
        assertEquals(mode, restored)
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

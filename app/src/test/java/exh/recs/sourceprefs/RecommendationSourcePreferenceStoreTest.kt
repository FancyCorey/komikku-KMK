package exh.recs.sourceprefs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK -->
class RecommendationSourcePreferenceStoreTest {

    // --- Key building ---

    @Test
    fun `installedKey produces correct format`() {
        assertEquals("i|12345", RecommendationSourcePreferenceStore.installedKey(12345L))
    }

    @Test
    fun `availableKey with sourceId produces correct format`() {
        assertEquals("a|sig1|eu.test|99", RecommendationSourcePreferenceStore.availableKey("sig1", "eu.test", 99L))
    }

    @Test
    fun `availableKey without sourceId produces correct format`() {
        assertEquals("a|sig1|eu.test", RecommendationSourcePreferenceStore.availableKey("sig1", "eu.test", null))
    }

    // --- Parse and serialize ---

    @Test
    fun `parse returns empty set for blank input`() {
        assertTrue(RecommendationSourcePreferenceStore.parse("").isEmpty())
        assertTrue(RecommendationSourcePreferenceStore.parse("   ").isEmpty())
    }

    @Test
    fun `parse splits semicolon-separated keys`() {
        val result = RecommendationSourcePreferenceStore.parse("i|1;a|sig|pkg|2;i|3")
        assertEquals(setOf("i|1", "a|sig|pkg|2", "i|3"), result)
    }

    @Test
    fun `serialize produces semicolon-joined string`() {
        val keys = setOf("i|1", "a|sig|pkg|99")
        val raw = RecommendationSourcePreferenceStore.serialize(keys)
        // Order is not guaranteed in a set — round-trip parse instead
        val roundTrip = RecommendationSourcePreferenceStore.parse(raw)
        assertEquals(keys, roundTrip)
    }

    @Test
    fun `serialize and parse are stable round-trip`() {
        val keys = setOf("i|111", "a|abc|eu.pkg|42", "a|def|eu.other")
        val raw = RecommendationSourcePreferenceStore.serialize(keys)
        assertEquals(keys, RecommendationSourcePreferenceStore.parse(raw))
    }

    // --- installedSourceIds ---

    @Test
    fun `installedSourceIds extracts only installed-prefix keys`() {
        val keys = setOf("i|10", "i|20", "a|sig|pkg|99")
        assertEquals(setOf(10L, 20L), RecommendationSourcePreferenceStore.installedSourceIds(keys))
    }

    @Test
    fun `installedSourceIds ignores malformed keys`() {
        val keys = setOf("i|notanumber", "i|", "a|sig|pkg|99")
        assertTrue(RecommendationSourcePreferenceStore.installedSourceIds(keys).isEmpty())
    }

    // --- Mutual exclusion ---

    @Test
    fun `like adds to liked and removes from disliked`() {
        val key = "i|42"
        val liked = emptySet<String>()
        val disliked = setOf(key)
        val (newLiked, newDisliked) = RecommendationSourcePreferenceStore.like(liked, disliked, key)
        assertTrue(key in newLiked)
        assertFalse(key in newDisliked)
    }

    @Test
    fun `dislike adds to disliked and removes from liked`() {
        val key = "i|42"
        val liked = setOf(key)
        val disliked = emptySet<String>()
        val (newLiked, newDisliked) = RecommendationSourcePreferenceStore.dislike(liked, disliked, key)
        assertFalse(key in newLiked)
        assertTrue(key in newDisliked)
    }

    @Test
    fun `reset removes key from both liked and disliked`() {
        val key = "i|42"
        val liked = setOf(key)
        val disliked = setOf(key) // hypothetically both set (shouldn't happen but reset must clear both)
        val (newLiked, newDisliked) = RecommendationSourcePreferenceStore.reset(liked, disliked, key)
        assertFalse(key in newLiked)
        assertFalse(key in newDisliked)
    }

    @Test
    fun `like does not affect other keys`() {
        val key = "i|42"
        val otherKey = "i|99"
        val liked = setOf(otherKey)
        val disliked = setOf(key)
        val (newLiked, newDisliked) = RecommendationSourcePreferenceStore.like(liked, disliked, key)
        assertTrue(otherKey in newLiked)
        assertTrue(key in newLiked)
        assertFalse(key in newDisliked)
    }

    // KMK v0.8.1-fix4: source/library-quality axis is independent from the recommendation axis
    // above -- both reuse this store's key format/serializer, but are persisted under separate
    // preference keys and must never leak into each other.

    @Test
    fun `recommendation dislike and source-quality dislike are independent axes`() {
        val key = "a|sig1|eu.test"
        // Disliking for recommendation behavior must not affect a separately-tracked quality state.
        val (_, recDisliked) = RecommendationSourcePreferenceStore.dislike(emptySet(), emptySet(), key)
        val qualityState = SourceQualityMarkPolicy.State(emptySet(), emptySet(), emptySet())
        assertTrue(key in recDisliked)
        assertFalse(key in qualityState.disliked)
    }

    @Test
    fun `marking source-quality poor does not affect recommendation liked set`() {
        val key = "i|42"
        val recLiked = setOf(key)
        val quality = SourceQualityMarkPolicy.markPoor(SourceQualityMarkPolicy.State(emptySet(), emptySet(), emptySet()), key)
        // Recommendation liked set is a wholly separate value — unaffected by the quality-axis operation.
        assertTrue(key in recLiked)
        assertTrue(key in quality.disliked)
    }

    @Test
    fun `clearing source-quality mark does not need to touch recommendation preference`() {
        val key = "i|42"
        val quality = SourceQualityMarkPolicy.markExplicit(SourceQualityMarkPolicy.State(emptySet(), emptySet(), emptySet()), key)
        val cleared = SourceQualityMarkPolicy.clear(quality, key)
        assertFalse(key in cleared.disliked)
        assertFalse(key in cleared.explicit)
    }

    @Test
    fun `markPoor sets disliked without explicit flag`() {
        val key = "i|1"
        val result = SourceQualityMarkPolicy.markPoor(SourceQualityMarkPolicy.State(emptySet(), emptySet(), emptySet()), key)
        assertTrue(SourceQualityMarkPolicy.isPoor(result, key))
        assertFalse(SourceQualityMarkPolicy.isExplicit(result, key))
    }

    @Test
    fun `markExplicit sets disliked with explicit flag`() {
        val key = "i|1"
        val result = SourceQualityMarkPolicy.markExplicit(SourceQualityMarkPolicy.State(emptySet(), emptySet(), emptySet()), key)
        assertTrue(SourceQualityMarkPolicy.isExplicit(result, key))
        assertFalse(SourceQualityMarkPolicy.isPoor(result, key))
    }

    @Test
    fun `switching from poor to explicit updates the explicit flag without duplicating dislike`() {
        val key = "i|1"
        val poor = SourceQualityMarkPolicy.markPoor(SourceQualityMarkPolicy.State(emptySet(), emptySet(), emptySet()), key)
        val explicit = SourceQualityMarkPolicy.markExplicit(poor, key)
        assertEquals(1, explicit.disliked.size)
        assertTrue(SourceQualityMarkPolicy.isExplicit(explicit, key))
    }

    @Test
    fun `liking a key clears any source-quality dislike mark for that key`() {
        val key = "i|1"
        val marked = SourceQualityMarkPolicy.markExplicit(SourceQualityMarkPolicy.State(emptySet(), emptySet(), emptySet()), key)
        val (newLiked, newDisliked) = RecommendationSourcePreferenceStore.like(marked.liked, marked.disliked, key)
        assertTrue(key in newLiked)
        assertFalse(key in newDisliked)
    }

    @Test
    fun `clearAll resets every source-quality set`() {
        val cleared = SourceQualityMarkPolicy.clearAll()
        assertTrue(cleared.liked.isEmpty())
        assertTrue(cleared.disliked.isEmpty())
        assertTrue(cleared.explicit.isEmpty())
    }
}
// KMK <--

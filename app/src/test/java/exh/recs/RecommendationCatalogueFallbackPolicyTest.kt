package exh.recs

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK v0.8.12 -->
class RecommendationCatalogueFallbackPolicyTest {

    @Test
    fun `a true no-match with no raw results and no error attempts the catalogue fallback`() {
        assertTrue(RecommendationCatalogueFallbackPolicy.shouldAttempt(hadRawResults = false, hadError = false))
    }

    @Test
    fun `a source that already produced raw results is FilteredOut, not offered the fallback`() {
        assertFalse(RecommendationCatalogueFallbackPolicy.shouldAttempt(hadRawResults = true, hadError = false))
    }

    @Test
    fun `a source with a recoverable search error is not retried through the fallback`() {
        assertFalse(RecommendationCatalogueFallbackPolicy.shouldAttempt(hadRawResults = false, hadError = true))
    }

    @Test
    fun `a source with both raw results and an error is not offered the fallback`() {
        assertFalse(RecommendationCatalogueFallbackPolicy.shouldAttempt(hadRawResults = true, hadError = true))
    }

    // KMK v0.8.13 -->
    @Test
    fun `QUERY_STRATEGY is not a valid RecommendationQueryStrategyType name`() {
        assertTrue(RecommendationQueryStrategyType.entries.none { it.name == RecommendationCatalogueFallbackPolicy.QUERY_STRATEGY })
    }

    @Test
    fun `a fallback success recorded under QUERY_STRATEGY is never parsed back as a persisted strategy`() {
        // Proves fallback success cannot be silently promoted into a persisted
        // RecommendationQueryStrategyType hint even if a future edit accidentally wrote
        // QUERY_STRATEGY into recommendationSourceStrategies() -- parseStrategies() already drops
        // any entry whose value does not match a real enum constant.
        val serialized = "42=${RecommendationCatalogueFallbackPolicy.QUERY_STRATEGY}"
        val parsed = RecommendationQueryPlanner.parseStrategies(serialized)
        assertNull(parsed[42L])
        assertTrue(parsed.isEmpty())
    }
    // KMK <--
}
// KMK <--

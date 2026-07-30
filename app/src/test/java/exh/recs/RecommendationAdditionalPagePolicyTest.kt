package exh.recs

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK v0.8.13 -->
class RecommendationAdditionalPagePolicyTest {

    @Test
    fun `text-only zero raw returns false`() {
        val result = RecommendationAdditionalPagePolicy.shouldDiscoverAdditionalPage(
            planType = RecommendationQueryStrategyType.TEXT_ONLY_TOP_TAGS,
            pageOneRawCount = 0,
            pageOneVisibleCount = 0,
            lastError = null,
        )
        assertFalse(result)
    }

    @Test
    fun `strict zero raw returns false`() {
        val result = RecommendationAdditionalPagePolicy.shouldDiscoverAdditionalPage(
            planType = RecommendationQueryStrategyType.TOP_TAGS_FILTER,
            pageOneRawCount = 0,
            pageOneVisibleCount = 0,
            lastError = null,
        )
        assertFalse(result)
    }

    @Test
    fun `strict raw but zero visible returns true if no error`() {
        val result = RecommendationAdditionalPagePolicy.shouldDiscoverAdditionalPage(
            planType = RecommendationQueryStrategyType.TOP_TAGS_FILTER,
            pageOneRawCount = 10,
            pageOneVisibleCount = 0,
            lastError = null,
        )
        assertTrue(result)
    }

    @Test
    fun `any error returns false`() {
        val result = RecommendationAdditionalPagePolicy.shouldDiscoverAdditionalPage(
            planType = RecommendationQueryStrategyType.TOP_TAGS_FILTER,
            pageOneRawCount = 10,
            pageOneVisibleCount = 5,
            lastError = IllegalStateException("boom"),
        )
        assertFalse(result)
    }

    @Test
    fun `text-only with raw results and no error still returns true`() {
        // Zero-raw is the specific bug being fixed -- a text-only query that DID find raw results
        // should still be allowed to advance, same as any other strategy.
        val result = RecommendationAdditionalPagePolicy.shouldDiscoverAdditionalPage(
            planType = RecommendationQueryStrategyType.TEXT_ONLY_TOP_TAGS,
            pageOneRawCount = 3,
            pageOneVisibleCount = 1,
            lastError = null,
        )
        assertTrue(result)
    }
}
// KMK <--

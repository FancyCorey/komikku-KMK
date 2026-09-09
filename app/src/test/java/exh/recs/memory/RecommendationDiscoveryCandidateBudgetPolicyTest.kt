package exh.recs.memory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RecommendationDiscoveryCandidateBudgetPolicyTest {
    @Test
    fun `budget is bounded to supported range`() {
        assertEquals(RecommendationDiscoveryCandidateBudgetPolicy.MIN, RecommendationDiscoveryCandidateBudgetPolicy.resolve(-1))
        assertEquals(RecommendationDiscoveryCandidateBudgetPolicy.MAX, RecommendationDiscoveryCandidateBudgetPolicy.resolve(101))
    }

    @Test
    fun `default preserves the existing single-page candidate work`() {
        assertEquals(
            RecommendationDiscoveryPlanner.MAX_NEW_CANDIDATES_PER_DISCOVERY_PAGE,
            RecommendationDiscoveryCandidateBudgetPolicy.DEFAULT,
        )
    }
}

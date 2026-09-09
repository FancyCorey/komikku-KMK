package exh.recs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RecommendationEnrichmentCapPolicyTest {

    @Test
    fun `range keeps existing default and accepts exact values`() {
        assertEquals(5, RecommendationEnrichmentCapPolicy.DEFAULT)
        assertEquals(1, RecommendationEnrichmentCapPolicy.resolve(1))
        assertEquals(17, RecommendationEnrichmentCapPolicy.resolve(17))
        assertEquals(20, RecommendationEnrichmentCapPolicy.resolve(20))
    }

    @Test
    fun `malformed values fall back to default`() {
        listOf(0, -1, 21, Int.MIN_VALUE, Int.MAX_VALUE).forEach { value ->
            assertEquals(RecommendationEnrichmentCapPolicy.DEFAULT, RecommendationEnrichmentCapPolicy.resolve(value))
        }
    }
}

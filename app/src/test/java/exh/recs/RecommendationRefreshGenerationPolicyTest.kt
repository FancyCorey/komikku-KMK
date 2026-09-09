package exh.recs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecommendationRefreshGenerationPolicyTest {
    @Test
    fun `current refresh accepts its own result`() {
        assertTrue(RecommendationRefreshGenerationPolicy.shouldAccept(7L, 7L))
    }

    @Test
    fun `late result from an older refresh is rejected`() {
        assertFalse(RecommendationRefreshGenerationPolicy.shouldAccept(6L, 7L))
    }

    @Test
    fun `a result cannot be accepted before a refresh claims its generation`() {
        assertFalse(RecommendationRefreshGenerationPolicy.shouldAccept(0L, -1L))
    }

    @Test
    fun `late activation from an older refresh cannot regress the active barrier`() {
        assertEquals(7L, RecommendationRefreshGenerationPolicy.activate(7L, 6L))
        assertEquals(8L, RecommendationRefreshGenerationPolicy.activate(7L, 8L))
    }
}

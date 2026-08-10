package exh.recs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// KMK v0.8.6 -->
class RecommendationLoadContextTest {

    @Test
    fun `exposes exactly the three documented contexts`() {
        assertEquals(
            listOf(RecommendationLoadContext.FOR_YOU, RecommendationLoadContext.GROUP_PREVIEW, RecommendationLoadContext.FULL_SOURCE),
            RecommendationLoadContext.entries.toList(),
        )
    }
}
// KMK <--

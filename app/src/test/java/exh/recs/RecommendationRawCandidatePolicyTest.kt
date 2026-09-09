package exh.recs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RecommendationRawCandidatePolicyTest {
    @Test
    fun `deduplicates before applying the candidate limit`() {
        val items = listOf("duplicate", "duplicate", "unique", "later")

        assertEquals(
            listOf("duplicate", "unique"),
            RecommendationRawCandidatePolicy.distinctWithinLimit(items, limit = 2) { it },
        )
    }

    @Test
    fun `non-positive limits perform no candidate work`() {
        assertEquals(
            emptyList<String>(),
            RecommendationRawCandidatePolicy.distinctWithinLimit(listOf("one"), limit = 0) { it },
        )
    }
}

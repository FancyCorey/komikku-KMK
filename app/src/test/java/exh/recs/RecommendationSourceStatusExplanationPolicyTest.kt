package exh.recs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RecommendationSourceStatusExplanationPolicyTest {

    @Test
    fun `every status resolves to its own dedicated explanation string`() {
        val resolved = RecommendationSourceStatus.entries.associateWith {
            RecommendationSourceStatusExplanationPolicy.explanationFor(it)
        }
        assertEquals(RecommendationSourceStatus.entries.size, resolved.values.toSet().size)
    }

    @Test
    fun `NoMatches and FilteredOut resolve to distinct explanations`() {
        val noMatches = RecommendationSourceStatusExplanationPolicy.explanationFor(RecommendationSourceStatus.NoMatches)
        val filtered = RecommendationSourceStatusExplanationPolicy.explanationFor(RecommendationSourceStatus.FilteredOut)
        assert(noMatches != filtered)
    }
}

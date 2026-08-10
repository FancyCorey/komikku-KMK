package exh.recs.evaluation

import exh.recs.evaluation.SourceRecommendationFitScorer.Outcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK -->
class SourceRecommendationFitScorerTest {

    @Test
    fun `error-only outcome scores 0`() {
        val score = SourceRecommendationFitScorer.score(Outcome(errorCount = 3, visibleCandidateCount = 0))
        assertEquals(0.0, score)
    }

    @Test
    fun `zero visible candidates with no errors scores 0`() {
        val score = SourceRecommendationFitScorer.score(Outcome())
        assertEquals(0.0, score)
    }

    @Test
    fun `strong outcome with many visible candidates scores above 0_7`() {
        val score = SourceRecommendationFitScorer.score(
            Outcome(
                visibleCandidateCount = 12,
                matchedGroupCount = 4,
                topPicksContribution = 5,
                avgCandidateScore = 0.85,
            ),
        )
        assertTrue(score > 0.7, "Expected score > 0.7 but was $score")
    }

    @Test
    fun `single visible candidate scores above 0 and below 0_5`() {
        val score = SourceRecommendationFitScorer.score(Outcome(visibleCandidateCount = 1))
        assertTrue(score > 0.0 && score < 0.5, "Expected 0 < score < 0.5 but was $score")
    }

    @Test
    fun `many no-matches reduces score`() {
        val baseline = SourceRecommendationFitScorer.score(Outcome(visibleCandidateCount = 5))
        val withNoMatches = SourceRecommendationFitScorer.score(Outcome(visibleCandidateCount = 5, noMatchesCount = 5))
        assertTrue(withNoMatches < baseline, "No-matches penalty should reduce score")
    }

    @Test
    fun `blocked tag candidates reduce score`() {
        val baseline = SourceRecommendationFitScorer.score(Outcome(visibleCandidateCount = 10))
        val withBlocked = SourceRecommendationFitScorer.score(Outcome(visibleCandidateCount = 10, blockedTagCandidateCount = 8))
        assertTrue(withBlocked < baseline, "Blocked-tag candidates should reduce score")
    }

    @Test
    fun `matched groups boost score`() {
        val baseline = SourceRecommendationFitScorer.score(Outcome(visibleCandidateCount = 5))
        val withGroups = SourceRecommendationFitScorer.score(Outcome(visibleCandidateCount = 5, matchedGroupCount = 3))
        assertTrue(withGroups > baseline, "Matched groups should boost score")
    }

    @Test
    fun `top picks contribution boosts score`() {
        val baseline = SourceRecommendationFitScorer.score(Outcome(visibleCandidateCount = 5))
        val withTopPicks = SourceRecommendationFitScorer.score(Outcome(visibleCandidateCount = 5, topPicksContribution = 3))
        assertTrue(withTopPicks > baseline, "Top Picks contribution should boost score")
    }

    @Test
    fun `score is clamped to 0_0 minimum`() {
        val score = SourceRecommendationFitScorer.score(
            Outcome(
                noMatchesCount = 100,
                filteredOutCount = 100,
                blockedTagCandidateCount = 100,
                errorCount = 100,
                visibleCandidateCount = 1,
            ),
        )
        assertTrue(score >= 0.0, "Score must be >= 0.0 but was $score")
    }

    @Test
    fun `score is clamped to 1_0 maximum`() {
        val score = SourceRecommendationFitScorer.score(
            Outcome(
                visibleCandidateCount = 100,
                matchedGroupCount = 100,
                topPicksContribution = 100,
                avgCandidateScore = 1.0,
            ),
        )
        assertTrue(score <= 1.0, "Score must be <= 1.0 but was $score")
    }
}
// KMK <--

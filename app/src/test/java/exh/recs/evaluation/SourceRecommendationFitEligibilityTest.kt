package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationVerdict

// KMK -->
class SourceRecommendationFitEligibilityTest {

    private fun makeEval(
        verdict: SourceEvaluationVerdict,
        sampleCount: Int = 5,
    ): SourceEvaluation = SourceEvaluation(
        evaluationKey = "sig|pkg|123",
        sourceId = 123L,
        extensionPkgName = "eu.kanade.test",
        signatureHash = "sig",
        extensionName = "Test Extension",
        sourceName = "Test Source",
        lang = "en",
        baseUrl = "https://example.com",
        repoName = "test-repo",
        sourceCount = 1,
        isNsfw = false,
        evaluationVersion = 1,
        evaluatedAt = 1_000_000L,
        expiresAt = null,
        sampleCount = sampleCount,
        popularCount = 2,
        latestCount = 2,
        searchCount = 1,
        searchSuccessCount = 1,
        likedTitleMatchCount = 0,
        preferredTagMatchCount = 2,
        blockedTagMatchCount = 0,
        explicitSignalCount = 0,
        ecchiSignalCount = 0,
        errorCount = 0,
        qualityScore = 0.7,
        recommendationFitScore = 0.75,
        searchReliabilityScore = 1.0,
        explicitScore = 0.0,
        ecchiScore = 0.0,
        verdict = verdict,
        sampledTitlesJson = null,
        sampledTagsJson = null,
        errorMessage = null,
    )

    @Test
    fun `STRONG_FIT with sufficient samples is ELIGIBLE`() {
        val result = SourceRecommendationFitEligibility.check(makeEval(SourceEvaluationVerdict.STRONG_FIT))
        assertEquals(SourceRecommendationFitEligibility.EligibilityResult.ELIGIBLE, result)
    }

    @Test
    fun `WORTH_TRYING with sufficient samples is ELIGIBLE`() {
        val result = SourceRecommendationFitEligibility.check(makeEval(SourceEvaluationVerdict.WORTH_TRYING))
        assertEquals(SourceRecommendationFitEligibility.EligibilityResult.ELIGIBLE, result)
    }

    @Test
    fun `REJECTED verdict is INELIGIBLE_VERDICT`() {
        val result = SourceRecommendationFitEligibility.check(makeEval(SourceEvaluationVerdict.REJECTED))
        assertEquals(SourceRecommendationFitEligibility.EligibilityResult.INELIGIBLE_VERDICT, result)
    }

    @Test
    fun `ERROR verdict is INELIGIBLE_VERDICT`() {
        val result = SourceRecommendationFitEligibility.check(makeEval(SourceEvaluationVerdict.ERROR))
        assertEquals(SourceRecommendationFitEligibility.EligibilityResult.INELIGIBLE_VERDICT, result)
    }

    @Test
    fun `WEAK verdict is INELIGIBLE_VERDICT`() {
        val result = SourceRecommendationFitEligibility.check(makeEval(SourceEvaluationVerdict.WEAK))
        assertEquals(SourceRecommendationFitEligibility.EligibilityResult.INELIGIBLE_VERDICT, result)
    }

    @Test
    fun `NEUTRAL verdict is INELIGIBLE_VERDICT`() {
        val result = SourceRecommendationFitEligibility.check(makeEval(SourceEvaluationVerdict.NEUTRAL))
        assertEquals(SourceRecommendationFitEligibility.EligibilityResult.INELIGIBLE_VERDICT, result)
    }

    @Test
    fun `EXPLICIT_HEAVY verdict is INELIGIBLE_VERDICT`() {
        val result = SourceRecommendationFitEligibility.check(makeEval(SourceEvaluationVerdict.EXPLICIT_HEAVY))
        assertEquals(SourceRecommendationFitEligibility.EligibilityResult.INELIGIBLE_VERDICT, result)
    }

    @Test
    fun `STRONG_FIT below minimum sample count is INSUFFICIENT_EVIDENCE`() {
        val result = SourceRecommendationFitEligibility.check(
            makeEval(SourceEvaluationVerdict.STRONG_FIT, sampleCount = SourceRecommendationFitEligibility.MIN_SAMPLE_COUNT - 1),
        )
        assertEquals(SourceRecommendationFitEligibility.EligibilityResult.INSUFFICIENT_EVIDENCE, result)
    }

    @Test
    fun `WORTH_TRYING with zero samples is INSUFFICIENT_EVIDENCE`() {
        val result = SourceRecommendationFitEligibility.check(
            makeEval(SourceEvaluationVerdict.WORTH_TRYING, sampleCount = 0),
        )
        assertEquals(SourceRecommendationFitEligibility.EligibilityResult.INSUFFICIENT_EVIDENCE, result)
    }

    @Test
    fun `STRONG_FIT at exactly minimum sample count is ELIGIBLE`() {
        val result = SourceRecommendationFitEligibility.check(
            makeEval(SourceEvaluationVerdict.STRONG_FIT, sampleCount = SourceRecommendationFitEligibility.MIN_SAMPLE_COUNT),
        )
        assertEquals(SourceRecommendationFitEligibility.EligibilityResult.ELIGIBLE, result)
    }
}
// KMK <--

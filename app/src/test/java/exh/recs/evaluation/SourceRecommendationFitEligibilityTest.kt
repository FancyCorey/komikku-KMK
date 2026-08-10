package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationKeys
import tachiyomi.domain.taste.model.SourceEvaluationMetadataConfidence
import tachiyomi.domain.taste.model.SourceEvaluationVerdict

// KMK -->
class SourceRecommendationFitEligibilityTest {

    private fun makeEval(
        verdict: SourceEvaluationVerdict,
        sampleCount: Int = 5,
        // KMK --> v0.7.42: HIGH default = "confidently evaluated" for tests not about confidence itself
        confidence: SourceEvaluationMetadataConfidence = SourceEvaluationMetadataConfidence.HIGH,
        // KMK <--
        // KMK --> v0.7.47: current version default so pre-existing tests keep evaluating "current row"
        // behavior; staleness is covered by its own dedicated tests below.
        evaluationVersion: Int = SourceEvaluationKeys.CURRENT_VERSION,
        // KMK <--
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
        evaluationVersion = evaluationVersion,
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
        catalogueMetadataConfidence = confidence,
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

    // ---- v0.7.42 decision D1: confidence-based fail-open admission ----

    @Test
    fun `WEAK verdict with LOW confidence is ELIGIBLE — inconclusive catalogue evidence fails open`() {
        val result = SourceRecommendationFitEligibility.check(
            makeEval(SourceEvaluationVerdict.WEAK, confidence = SourceEvaluationMetadataConfidence.LOW),
        )
        assertEquals(SourceRecommendationFitEligibility.EligibilityResult.ELIGIBLE, result)
    }

    @Test
    fun `WEAK verdict with UNKNOWN confidence is ELIGIBLE — inconclusive catalogue evidence fails open`() {
        val result = SourceRecommendationFitEligibility.check(
            makeEval(SourceEvaluationVerdict.WEAK, confidence = SourceEvaluationMetadataConfidence.UNKNOWN),
        )
        assertEquals(SourceRecommendationFitEligibility.EligibilityResult.ELIGIBLE, result)
    }

    @Test
    fun `WEAK verdict with MODERATE confidence stays INELIGIBLE_VERDICT — evidence was conclusive enough`() {
        val result = SourceRecommendationFitEligibility.check(
            makeEval(SourceEvaluationVerdict.WEAK, confidence = SourceEvaluationMetadataConfidence.MODERATE),
        )
        assertEquals(SourceRecommendationFitEligibility.EligibilityResult.INELIGIBLE_VERDICT, result)
    }

    @Test
    fun `NEUTRAL verdict with LOW confidence is ELIGIBLE`() {
        val result = SourceRecommendationFitEligibility.check(
            makeEval(SourceEvaluationVerdict.NEUTRAL, confidence = SourceEvaluationMetadataConfidence.LOW),
        )
        assertEquals(SourceRecommendationFitEligibility.EligibilityResult.ELIGIBLE, result)
    }

    @Test
    fun `WEAK verdict with LOW confidence but zero samples is INSUFFICIENT_EVIDENCE`() {
        val result = SourceRecommendationFitEligibility.check(
            makeEval(SourceEvaluationVerdict.WEAK, sampleCount = 0, confidence = SourceEvaluationMetadataConfidence.LOW),
        )
        assertEquals(SourceRecommendationFitEligibility.EligibilityResult.INSUFFICIENT_EVIDENCE, result)
    }

    @Test
    fun `EXPLICIT_HEAVY verdict with LOW confidence still stays INELIGIBLE_VERDICT — safety exclusion is unconditional`() {
        val result = SourceRecommendationFitEligibility.check(
            makeEval(SourceEvaluationVerdict.EXPLICIT_HEAVY, confidence = SourceEvaluationMetadataConfidence.LOW),
        )
        assertEquals(SourceRecommendationFitEligibility.EligibilityResult.INELIGIBLE_VERDICT, result)
    }

    @Test
    fun `ECCHI_HEAVY verdict with UNKNOWN confidence still stays INELIGIBLE_VERDICT — safety exclusion is unconditional`() {
        val result = SourceRecommendationFitEligibility.check(
            makeEval(SourceEvaluationVerdict.ECCHI_HEAVY, confidence = SourceEvaluationMetadataConfidence.UNKNOWN),
        )
        assertEquals(SourceRecommendationFitEligibility.EligibilityResult.INELIGIBLE_VERDICT, result)
    }

    @Test
    fun `ERROR verdict with LOW confidence still stays INELIGIBLE_VERDICT — infrastructure exclusion is unconditional`() {
        val result = SourceRecommendationFitEligibility.check(
            makeEval(SourceEvaluationVerdict.ERROR, confidence = SourceEvaluationMetadataConfidence.LOW),
        )
        assertEquals(SourceRecommendationFitEligibility.EligibilityResult.INELIGIBLE_VERDICT, result)
    }

    // ---- v0.7.47: stale rows must not feed the search-compatibility queue as current evidence ----

    @Test
    fun `STRONG_FIT row scored under an older version is STALE_EVALUATION, not ELIGIBLE`() {
        val result = SourceRecommendationFitEligibility.check(
            makeEval(SourceEvaluationVerdict.STRONG_FIT, evaluationVersion = SourceEvaluationKeys.CURRENT_VERSION - 1),
        )
        assertEquals(SourceRecommendationFitEligibility.EligibilityResult.STALE_EVALUATION, result)
    }

    @Test
    fun `isProbeEligible is false for a stale row regardless of its stored verdict`() {
        val stale = makeEval(SourceEvaluationVerdict.STRONG_FIT, evaluationVersion = SourceEvaluationKeys.CURRENT_VERSION - 1)
        assertEquals(false, SourceRecommendationFitEligibility.isProbeEligible(stale))
    }

    @Test
    fun `staleness check runs before the verdict and confidence checks`() {
        // EXPLICIT_HEAVY would normally be an unconditional INELIGIBLE_VERDICT — but a stale row's
        // verdict isn't trustworthy at all, so STALE_EVALUATION must win first.
        val result = SourceRecommendationFitEligibility.check(
            makeEval(SourceEvaluationVerdict.EXPLICIT_HEAVY, evaluationVersion = 1),
        )
        assertEquals(SourceRecommendationFitEligibility.EligibilityResult.STALE_EVALUATION, result)
    }
}
// KMK <--

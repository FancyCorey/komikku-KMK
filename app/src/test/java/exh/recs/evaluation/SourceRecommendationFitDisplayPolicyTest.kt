package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.RecommendationQualityVerdict
import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationKeys
import tachiyomi.domain.taste.model.SourceEvaluationMetadataConfidence
import tachiyomi.domain.taste.model.SourceEvaluationVerdict
import tachiyomi.domain.taste.model.SourceRecommendationFit

// KMK -->
/**
 * Tests for [SourceRecommendationFitDisplayPolicy.resolve] and [SourceRecommendationFitDisplayPolicy.compatibilityRank].
 *
 * This is the single shared display policy for v0.7.42-fix2: queue buckets, diagnostics, row
 * labels, and the For You compatibility sort all resolve through this one function so they can
 * never disagree. Pure JVM test — no Android, Compose, network, or database dependency.
 */
class SourceRecommendationFitDisplayPolicyTest {

    private val now = 10_000_000L

    private fun eval(
        verdict: SourceEvaluationVerdict,
        sampleCount: Int = 5,
        confidence: SourceEvaluationMetadataConfidence = SourceEvaluationMetadataConfidence.HIGH,
    ): SourceEvaluation = SourceEvaluation(
        evaluationKey = "key",
        sourceId = 1L,
        extensionPkgName = "pkg",
        signatureHash = "sig",
        extensionName = "Ext",
        sourceName = "Source",
        lang = "en",
        baseUrl = null,
        repoName = null,
        sourceCount = 1,
        isNsfw = false,
        evaluationVersion = SourceEvaluationKeys.CURRENT_VERSION,
        evaluatedAt = 0L,
        expiresAt = null,
        sampleCount = sampleCount,
        popularCount = 2,
        latestCount = 2,
        searchCount = 0,
        searchSuccessCount = 0,
        likedTitleMatchCount = 0,
        preferredTagMatchCount = 2,
        blockedTagMatchCount = 0,
        explicitSignalCount = 0,
        ecchiSignalCount = 0,
        errorCount = 0,
        qualityScore = 0.7,
        recommendationFitScore = 0.75,
        searchReliabilityScore = 0.0,
        explicitScore = 0.0,
        ecchiScore = 0.0,
        verdict = verdict,
        sampledTitlesJson = null,
        sampledTagsJson = null,
        errorMessage = null,
        catalogueMetadataConfidence = confidence,
    )

    private fun fit(
        verdict: RecommendationQualityVerdict = RecommendationQualityVerdict.GOOD,
        evaluationVersion: Int = SourceRecommendationFit.CURRENT_VERSION,
        expiresAt: Long? = null,
        errorMessage: String? = null,
    ): SourceRecommendationFit = SourceRecommendationFit(
        fitKey = "key::rec_fit",
        evaluationKey = "key",
        sourceId = 1L,
        extensionPkgName = "pkg",
        signatureHash = "sig",
        extensionName = "Ext",
        sourceName = "Source",
        lang = "en",
        evaluatedAt = 0L,
        queryCount = 1,
        querySuccessCount = 1,
        rawResultCount = 5,
        visibleCandidateCount = 2,
        filteredOutCount = 1,
        blockedTagCandidateCount = 0,
        matchedGroupCount = 1,
        topPicksContribution = 0,
        noMatchesCount = 0,
        errorCount = 0,
        avgCandidateScore = 0.5,
        recommendationQualityScore = 0.5,
        verdict = verdict,
        reasonsJson = "[]",
        errorMessage = errorMessage,
        evaluationVersion = evaluationVersion,
        expiresAt = expiresAt,
    )

    private fun resolve(
        evaluation: SourceEvaluation,
        fit: SourceRecommendationFit? = null,
    ) = SourceRecommendationFitDisplayPolicy.resolve(evaluation, fit, now)

    // ---- NOT_CHECKED: eligible, no fit ----

    @Test
    fun `STRONG_FIT with no fit is NOT_CHECKED`() {
        assertEquals(CompatibilityDisplayState.NOT_CHECKED, resolve(eval(SourceEvaluationVerdict.STRONG_FIT)))
    }

    @Test
    fun `WORTH_TRYING with no fit is NOT_CHECKED`() {
        assertEquals(CompatibilityDisplayState.NOT_CHECKED, resolve(eval(SourceEvaluationVerdict.WORTH_TRYING)))
    }

    @Test
    fun `WEAK with LOW confidence (fail-open) and no fit is NOT_CHECKED`() {
        val e = eval(SourceEvaluationVerdict.WEAK, confidence = SourceEvaluationMetadataConfidence.LOW)
        assertEquals(CompatibilityDisplayState.NOT_CHECKED, resolve(e))
    }

    @Test
    fun `NEUTRAL with UNKNOWN confidence (fail-open) and no fit is NOT_CHECKED`() {
        val e = eval(SourceEvaluationVerdict.NEUTRAL, confidence = SourceEvaluationMetadataConfidence.UNKNOWN)
        assertEquals(CompatibilityDisplayState.NOT_CHECKED, resolve(e))
    }

    // ---- INELIGIBLE: excluded regardless of confidence or a persisted fit ----

    @Test
    fun `EXPLICIT_HEAVY is INELIGIBLE regardless of confidence`() {
        val e = eval(SourceEvaluationVerdict.EXPLICIT_HEAVY, confidence = SourceEvaluationMetadataConfidence.LOW)
        assertEquals(CompatibilityDisplayState.INELIGIBLE, resolve(e))
    }

    @Test
    fun `ECCHI_HEAVY is INELIGIBLE regardless of confidence`() {
        val e = eval(SourceEvaluationVerdict.ECCHI_HEAVY, confidence = SourceEvaluationMetadataConfidence.UNKNOWN)
        assertEquals(CompatibilityDisplayState.INELIGIBLE, resolve(e))
    }

    @Test
    fun `ERROR verdict is INELIGIBLE regardless of confidence`() {
        val e = eval(SourceEvaluationVerdict.ERROR, confidence = SourceEvaluationMetadataConfidence.LOW)
        assertEquals(CompatibilityDisplayState.INELIGIBLE, resolve(e))
    }

    @Test
    fun `REJECTED is INELIGIBLE regardless of confidence`() {
        val e = eval(SourceEvaluationVerdict.REJECTED, confidence = SourceEvaluationMetadataConfidence.UNKNOWN)
        assertEquals(CompatibilityDisplayState.INELIGIBLE, resolve(e))
    }

    @Test
    fun `EXPLICIT_HEAVY stays INELIGIBLE even with a persisted current fit`() {
        val e = eval(SourceEvaluationVerdict.EXPLICIT_HEAVY)
        assertEquals(CompatibilityDisplayState.INELIGIBLE, resolve(e, fit(RecommendationQualityVerdict.GREAT)))
    }

    @Test
    fun `WEAK with MODERATE confidence is INELIGIBLE — evidence was conclusive`() {
        val e = eval(SourceEvaluationVerdict.WEAK, confidence = SourceEvaluationMetadataConfidence.MODERATE)
        assertEquals(CompatibilityDisplayState.INELIGIBLE, resolve(e))
    }

    // ---- Missing / staleness (version, exact-expiry, future-expiry) ----

    @Test
    fun `eligible with no fit at all is NOT_CHECKED`() {
        val e = eval(SourceEvaluationVerdict.STRONG_FIT)
        assertEquals(CompatibilityDisplayState.NOT_CHECKED, resolve(e, null))
    }

    @Test
    fun `eligible with older-version fit is OUTDATED`() {
        val e = eval(SourceEvaluationVerdict.STRONG_FIT)
        val staleFit = fit(evaluationVersion = SourceRecommendationFit.CURRENT_VERSION - 1)
        assertEquals(CompatibilityDisplayState.OUTDATED, resolve(e, staleFit))
    }

    @Test
    fun `eligible with fit expiring exactly at now is OUTDATED — boundary is exclusive`() {
        val e = eval(SourceEvaluationVerdict.STRONG_FIT)
        val boundaryFit = fit(expiresAt = now)
        assertEquals(CompatibilityDisplayState.OUTDATED, resolve(e, boundaryFit))
    }

    @Test
    fun `eligible with fit expiring in the past is OUTDATED`() {
        val e = eval(SourceEvaluationVerdict.STRONG_FIT)
        val expiredFit = fit(expiresAt = now - 1)
        assertEquals(CompatibilityDisplayState.OUTDATED, resolve(e, expiredFit))
    }

    @Test
    fun `eligible with fit expiring in the future is current, not OUTDATED`() {
        val e = eval(SourceEvaluationVerdict.STRONG_FIT)
        val freshFit = fit(expiresAt = now + 1)
        assertEquals(CompatibilityDisplayState.GOOD, resolve(e, freshFit))
    }

    @Test
    fun `eligible with current fit and no expiry is current`() {
        val e = eval(SourceEvaluationVerdict.STRONG_FIT)
        assertEquals(CompatibilityDisplayState.GOOD, resolve(e, fit()))
    }

    // ---- Every current fit verdict maps correctly ----

    @Test
    fun `current GREAT fit maps to GREAT`() {
        val e = eval(SourceEvaluationVerdict.STRONG_FIT)
        assertEquals(CompatibilityDisplayState.GREAT, resolve(e, fit(RecommendationQualityVerdict.GREAT)))
    }

    @Test
    fun `current GOOD fit maps to GOOD`() {
        val e = eval(SourceEvaluationVerdict.STRONG_FIT)
        assertEquals(CompatibilityDisplayState.GOOD, resolve(e, fit(RecommendationQualityVerdict.GOOD)))
    }

    @Test
    fun `current MIXED fit maps to MIXED`() {
        val e = eval(SourceEvaluationVerdict.STRONG_FIT)
        assertEquals(CompatibilityDisplayState.MIXED, resolve(e, fit(RecommendationQualityVerdict.MIXED)))
    }

    @Test
    fun `current WEAK fit maps to WEAK`() {
        val e = eval(SourceEvaluationVerdict.STRONG_FIT)
        assertEquals(CompatibilityDisplayState.WEAK, resolve(e, fit(RecommendationQualityVerdict.WEAK)))
    }

    @Test
    fun `current NO_MATCHES fit maps to NO_MATCHES`() {
        val e = eval(SourceEvaluationVerdict.STRONG_FIT)
        assertEquals(CompatibilityDisplayState.NO_MATCHES, resolve(e, fit(RecommendationQualityVerdict.NO_MATCHES)))
    }

    @Test
    fun `current TOO_LITTLE_EVIDENCE fit maps to NO_MATCHES`() {
        val e = eval(SourceEvaluationVerdict.STRONG_FIT)
        assertEquals(CompatibilityDisplayState.NO_MATCHES, resolve(e, fit(RecommendationQualityVerdict.TOO_LITTLE_EVIDENCE)))
    }

    @Test
    fun `current ERROR fit maps to ERROR`() {
        val e = eval(SourceEvaluationVerdict.STRONG_FIT)
        assertEquals(CompatibilityDisplayState.ERROR, resolve(e, fit(RecommendationQualityVerdict.ERROR)))
    }

    // ---- A stale error must never display as a current error ----

    @Test
    fun `a stale ERROR fit maps to OUTDATED, not ERROR`() {
        val e = eval(SourceEvaluationVerdict.STRONG_FIT)
        val staleErrorFit = fit(RecommendationQualityVerdict.ERROR, evaluationVersion = SourceRecommendationFit.CURRENT_VERSION - 1)
        assertEquals(CompatibilityDisplayState.OUTDATED, resolve(e, staleErrorFit))
    }

    @Test
    fun `an expired ERROR fit maps to OUTDATED, not ERROR`() {
        val e = eval(SourceEvaluationVerdict.STRONG_FIT)
        val expiredErrorFit = fit(RecommendationQualityVerdict.ERROR, expiresAt = now - 1)
        assertEquals(CompatibilityDisplayState.OUTDATED, resolve(e, expiredErrorFit))
    }

    // ---- compatibilityRank ----

    @Test
    fun `compatibilityRank orders current positive, weak, no-matches, error, outdated, not-checked, ineligible`() {
        val ranks = listOf(
            CompatibilityDisplayState.GREAT,
            CompatibilityDisplayState.GOOD,
            CompatibilityDisplayState.MIXED,
            CompatibilityDisplayState.WEAK,
            CompatibilityDisplayState.NO_MATCHES,
            CompatibilityDisplayState.ERROR,
            CompatibilityDisplayState.OUTDATED,
            CompatibilityDisplayState.NOT_CHECKED,
            CompatibilityDisplayState.INELIGIBLE,
        ).map { SourceRecommendationFitDisplayPolicy.compatibilityRank(it) }
        // Monotonically non-decreasing in this exact declared order
        for (i in 0 until ranks.size - 1) {
            assertEquals(true, ranks[i] <= ranks[i + 1]) { "Rank not monotonic at index $i: $ranks" }
        }
        // GREAT/GOOD/MIXED share the same rank (tier 0)
        assertEquals(
            SourceRecommendationFitDisplayPolicy.compatibilityRank(CompatibilityDisplayState.GREAT),
            SourceRecommendationFitDisplayPolicy.compatibilityRank(CompatibilityDisplayState.MIXED),
        )
        // OUTDATED sorts strictly after every current outcome
        assertEquals(
            true,
            SourceRecommendationFitDisplayPolicy.compatibilityRank(CompatibilityDisplayState.OUTDATED) >
                SourceRecommendationFitDisplayPolicy.compatibilityRank(CompatibilityDisplayState.ERROR),
        )
        // NOT_CHECKED sorts strictly after OUTDATED, INELIGIBLE sorts strictly last
        assertEquals(
            true,
            SourceRecommendationFitDisplayPolicy.compatibilityRank(CompatibilityDisplayState.NOT_CHECKED) >
                SourceRecommendationFitDisplayPolicy.compatibilityRank(CompatibilityDisplayState.OUTDATED),
        )
        assertEquals(
            true,
            SourceRecommendationFitDisplayPolicy.compatibilityRank(CompatibilityDisplayState.INELIGIBLE) >
                SourceRecommendationFitDisplayPolicy.compatibilityRank(CompatibilityDisplayState.NOT_CHECKED),
        )
    }

    @Test
    fun `isCurrentOutcome is true only for GREAT GOOD MIXED WEAK NO_MATCHES ERROR`() {
        val expectedCurrent = setOf(
            CompatibilityDisplayState.GREAT,
            CompatibilityDisplayState.GOOD,
            CompatibilityDisplayState.MIXED,
            CompatibilityDisplayState.WEAK,
            CompatibilityDisplayState.NO_MATCHES,
            CompatibilityDisplayState.ERROR,
        )
        for (state in CompatibilityDisplayState.entries) {
            assertEquals(state in expectedCurrent, state.isCurrentOutcome) { "Unexpected isCurrentOutcome for $state" }
        }
    }
}
// KMK <--

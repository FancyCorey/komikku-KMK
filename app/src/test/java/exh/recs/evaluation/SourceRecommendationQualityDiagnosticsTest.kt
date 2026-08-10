package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.RecommendationQualityVerdict
import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationKeys
import tachiyomi.domain.taste.model.SourceEvaluationMetadataConfidence
import tachiyomi.domain.taste.model.SourceEvaluationVerdict
import tachiyomi.domain.taste.model.SourceRecommendationFit

// KMK -->
/**
 * Tests for [SourceRecommendationQualityDiagnostics.compute].
 *
 * v0.7.42-fix1: diagnostics now delegate eligibility/staleness to the same
 * [SourceRecommendationFitEligibility] contract [SourceRecommendationQualityQueue] uses, instead of
 * a separately hardcoded STRONG_FIT/WORTH_TRYING filter.
 *
 * v0.7.42-fix2: routed through [SourceRecommendationFitDisplayPolicy.resolve] (same call path as the
 * queue and row labels). `Summary` still intentionally folds missing+outdated into `notCheckedCount`
 * — the queue is the source of truth for the missing/outdated split used by the action UI.
 */
class SourceRecommendationQualityDiagnosticsTest {

    private val now = 10_000_000L

    private fun makeEval(
        key: String,
        verdict: SourceEvaluationVerdict,
        // KMK --> v0.7.42-fix1: HIGH default = "confidently evaluated" for tests not about confidence itself
        confidence: SourceEvaluationMetadataConfidence = SourceEvaluationMetadataConfidence.HIGH,
        // KMK <--
    ) = SourceEvaluation(
        evaluationKey = key,
        sourceId = key.hashCode().toLong(),
        extensionPkgName = "eu.kanade.tachiyomi.extension.$key",
        signatureHash = "abc123",
        extensionName = key,
        sourceName = key,
        lang = "en",
        baseUrl = null,
        repoName = null,
        sourceCount = 1,
        isNsfw = false,
        evaluationVersion = SourceEvaluationKeys.CURRENT_VERSION,
        evaluatedAt = 0L,
        expiresAt = null,
        sampleCount = 10,
        popularCount = 10,
        latestCount = 0,
        searchCount = 2,
        searchSuccessCount = 2,
        likedTitleMatchCount = 1,
        preferredTagMatchCount = 3,
        blockedTagMatchCount = 0,
        explicitSignalCount = 0,
        ecchiSignalCount = 0,
        errorCount = 0,
        qualityScore = 0.8,
        recommendationFitScore = 0.7,
        searchReliabilityScore = 0.9,
        explicitScore = 0.0,
        ecchiScore = 0.0,
        verdict = verdict,
        sampledTitlesJson = null,
        sampledTagsJson = null,
        errorMessage = null,
        catalogueMetadataConfidence = confidence,
    )

    private fun makeFit(
        evalKey: String,
        verdict: RecommendationQualityVerdict,
        errorMessage: String? = null,
        evaluationVersion: Int = SourceRecommendationFit.CURRENT_VERSION,
        expiresAt: Long? = null,
    ) = SourceRecommendationFit(
        fitKey = SourceRecommendationFit.fitKeyFor(evalKey),
        evaluationKey = evalKey,
        sourceId = evalKey.hashCode().toLong(),
        extensionPkgName = "eu.kanade.tachiyomi.extension.$evalKey",
        signatureHash = "abc123",
        extensionName = evalKey,
        sourceName = evalKey,
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

    private fun compute(
        evaluations: List<SourceEvaluation>,
        fits: Map<String, SourceRecommendationFit> = emptyMap(),
    ) = SourceRecommendationQualityDiagnostics.compute(evaluations, fits, now)

    @Test
    fun `compute returns empty summary when no evaluations`() {
        val summary = compute(emptyList())
        assertFalse(summary.hasAnyResults)
        assertEquals(0, summary.checkedCount)
        assertEquals(0, summary.notCheckedCount)
        assertEquals(0, summary.totalPromisingCount)
    }

    @Test
    fun `non-promising evaluations are not counted`() {
        val evals = listOf(
            makeEval("e1", SourceEvaluationVerdict.POOR_SEARCH),
            makeEval("e2", SourceEvaluationVerdict.REJECTED),
        )
        val summary = compute(evals)
        assertEquals(0, summary.totalPromisingCount)
        assertFalse(summary.hasAnyResults)
    }

    @Test
    fun `promising evaluations with no fit count as not checked`() {
        val evals = listOf(
            makeEval("e1", SourceEvaluationVerdict.STRONG_FIT),
            makeEval("e2", SourceEvaluationVerdict.WORTH_TRYING),
        )
        val summary = compute(evals)
        assertEquals(2, summary.totalPromisingCount)
        assertEquals(0, summary.checkedCount)
        assertEquals(2, summary.notCheckedCount)
        assertFalse(summary.hasAnyResults)
    }

    @Test
    fun `good verdict increments goodCount`() {
        val evals = listOf(makeEval("e1", SourceEvaluationVerdict.STRONG_FIT))
        val fits = mapOf("e1" to makeFit("e1", RecommendationQualityVerdict.GREAT))
        val summary = compute(evals, fits)
        assertEquals(1, summary.checkedCount)
        assertEquals(1, summary.goodCount)
        assertEquals(0, summary.weakCount)
        assertEquals(0, summary.noResultsCount)
        assertEquals(0, summary.searchErrorCount)
        assertEquals(0, summary.installLoadIssueCount)
    }

    @Test
    fun `WEAK verdict increments weakCount`() {
        val evals = listOf(makeEval("e1", SourceEvaluationVerdict.WORTH_TRYING))
        val fits = mapOf("e1" to makeFit("e1", RecommendationQualityVerdict.WEAK))
        val summary = compute(evals, fits)
        assertEquals(1, summary.weakCount)
        assertEquals(0, summary.goodCount)
    }

    @Test
    fun `NO_MATCHES verdict increments noResultsCount`() {
        val evals = listOf(makeEval("e1", SourceEvaluationVerdict.STRONG_FIT))
        val fits = mapOf("e1" to makeFit("e1", RecommendationQualityVerdict.NO_MATCHES))
        val summary = compute(evals, fits)
        assertEquals(1, summary.noResultsCount)
        assertEquals(0, summary.searchErrorCount)
    }

    @Test
    fun `TOO_LITTLE_EVIDENCE verdict increments noResultsCount`() {
        val evals = listOf(makeEval("e1", SourceEvaluationVerdict.STRONG_FIT))
        val fits = mapOf("e1" to makeFit("e1", RecommendationQualityVerdict.TOO_LITTLE_EVIDENCE))
        val summary = compute(evals, fits)
        assertEquals(1, summary.noResultsCount)
    }

    @Test
    fun `ERROR with install issue increments installLoadIssueCount`() {
        val evals = listOf(makeEval("e1", SourceEvaluationVerdict.STRONG_FIT))
        val fits = mapOf(
            "e1" to makeFit("e1", RecommendationQualityVerdict.ERROR, "Extension not found in available sources"),
        )
        val summary = compute(evals, fits)
        assertEquals(1, summary.installLoadIssueCount)
        assertEquals(0, summary.searchErrorCount)
    }

    @Test
    fun `ERROR with search error increments searchErrorCount`() {
        val evals = listOf(makeEval("e1", SourceEvaluationVerdict.STRONG_FIT))
        val fits = mapOf(
            "e1" to makeFit("e1", RecommendationQualityVerdict.ERROR, "Plan TOP_TAGS_FILTER: error — UnknownHostException"),
        )
        val summary = compute(evals, fits)
        assertEquals(1, summary.searchErrorCount)
        assertEquals(0, summary.installLoadIssueCount)
    }

    @Test
    fun `mixed evaluations are categorized correctly`() {
        val evals = listOf(
            makeEval("e1", SourceEvaluationVerdict.STRONG_FIT),
            makeEval("e2", SourceEvaluationVerdict.STRONG_FIT),
            makeEval("e3", SourceEvaluationVerdict.WORTH_TRYING),
            makeEval("e4", SourceEvaluationVerdict.WORTH_TRYING),
            makeEval("e5", SourceEvaluationVerdict.WORTH_TRYING),
            makeEval("epoor", SourceEvaluationVerdict.POOR_SEARCH),
        )
        val fits = mapOf(
            "e1" to makeFit("e1", RecommendationQualityVerdict.GREAT),
            "e2" to makeFit("e2", RecommendationQualityVerdict.WEAK),
            "e3" to makeFit("e3", RecommendationQualityVerdict.NO_MATCHES),
            "e4" to makeFit("e4", RecommendationQualityVerdict.ERROR, "Extension not found in available sources"),
            // e5 has no fit → notChecked
        )
        val summary = compute(evals, fits)
        assertEquals(5, summary.totalPromisingCount)
        assertEquals(4, summary.checkedCount)
        assertEquals(1, summary.notCheckedCount)
        assertEquals(1, summary.goodCount)
        assertEquals(1, summary.weakCount)
        assertEquals(1, summary.noResultsCount)
        assertEquals(1, summary.installLoadIssueCount)
        assertEquals(0, summary.searchErrorCount)
        assertTrue(summary.hasAnyResults)
    }

    @Test
    fun `GOOD and MIXED verdicts increment goodCount`() {
        val evals = listOf(
            makeEval("e1", SourceEvaluationVerdict.STRONG_FIT),
            makeEval("e2", SourceEvaluationVerdict.STRONG_FIT),
        )
        val fits = mapOf(
            "e1" to makeFit("e1", RecommendationQualityVerdict.GOOD),
            "e2" to makeFit("e2", RecommendationQualityVerdict.MIXED),
        )
        val summary = compute(evals, fits)
        assertEquals(2, summary.goodCount)
        assertEquals(0, summary.weakCount)
    }

    // --- v0.7.42-fix1: shares eligibility with the queue (F3) ---

    @Test
    fun `low-confidence WEAK row without fit counts as promising and not checked, matching the queue`() {
        val evals = listOf(makeEval("e1", SourceEvaluationVerdict.WEAK, confidence = SourceEvaluationMetadataConfidence.LOW))
        val summary = compute(evals)
        assertEquals(1, summary.totalPromisingCount)
        assertEquals(0, summary.checkedCount)
        assertEquals(1, summary.notCheckedCount)

        val queueResult = SourceRecommendationQualityQueue.compute(evals, emptyMap(), now)
        assertEquals(1, queueResult.missingCount)
    }

    @Test
    fun `confidently WEAK row (MODERATE confidence) is excluded, matching the queue`() {
        val evals = listOf(makeEval("e1", SourceEvaluationVerdict.WEAK, confidence = SourceEvaluationMetadataConfidence.MODERATE))
        val summary = compute(evals)
        assertEquals(0, summary.totalPromisingCount)

        val queueResult = SourceRecommendationQualityQueue.compute(evals, emptyMap(), now)
        assertEquals(1, queueResult.ineligible.size)
    }

    @Test
    fun `stale fit (older evaluationVersion) counts as not checked, not as a scored outcome`() {
        val evals = listOf(makeEval("e1", SourceEvaluationVerdict.STRONG_FIT))
        val fits = mapOf(
            "e1" to makeFit("e1", RecommendationQualityVerdict.GREAT, evaluationVersion = SourceRecommendationFit.CURRENT_VERSION - 1),
        )
        val summary = compute(evals, fits)
        assertEquals(0, summary.checkedCount)
        assertEquals(1, summary.notCheckedCount)
        assertEquals(0, summary.goodCount)
        assertFalse(summary.hasAnyResults)
    }

    @Test
    fun `expired fit counts as not checked, not as a scored outcome`() {
        val evals = listOf(makeEval("e1", SourceEvaluationVerdict.STRONG_FIT))
        val fits = mapOf(
            "e1" to makeFit("e1", RecommendationQualityVerdict.WEAK, expiresAt = now - 1),
        )
        val summary = compute(evals, fits)
        assertEquals(0, summary.checkedCount)
        assertEquals(1, summary.notCheckedCount)
        assertEquals(0, summary.weakCount)
    }

    @Test
    fun `EXPLICIT_HEAVY row is excluded from diagnostics regardless of confidence`() {
        val evals = listOf(makeEval("e1", SourceEvaluationVerdict.EXPLICIT_HEAVY, confidence = SourceEvaluationMetadataConfidence.LOW))
        val summary = compute(evals)
        assertEquals(0, summary.totalPromisingCount)
    }

    // --- v0.7.42-fix2: diagnostics notCheckedCount agrees with the queue's missing+outdated split ---

    @Test
    fun `notCheckedCount equals queue missingCount plus outdatedCount for a mix of missing and outdated rows`() {
        val missing = makeEval("missing", SourceEvaluationVerdict.STRONG_FIT)
        val outdated = makeEval("outdated", SourceEvaluationVerdict.STRONG_FIT)
        val checked = makeEval("checked", SourceEvaluationVerdict.STRONG_FIT)
        val evals = listOf(missing, outdated, checked)
        val fits = mapOf(
            "outdated" to makeFit("outdated", RecommendationQualityVerdict.GOOD, evaluationVersion = SourceRecommendationFit.CURRENT_VERSION - 1),
            "checked" to makeFit("checked", RecommendationQualityVerdict.GOOD),
        )

        val summary = compute(evals, fits)
        val queueResult = SourceRecommendationQualityQueue.compute(evals, fits, now)

        assertEquals(queueResult.missingCount + queueResult.outdatedCount, summary.notCheckedCount)
        assertEquals(queueResult.checkedPromising.size, summary.checkedCount)
    }

    @Test
    fun `an outdated ERROR fit is not checked, not counted as installLoadIssue or searchError`() {
        val evals = listOf(makeEval("e1", SourceEvaluationVerdict.STRONG_FIT))
        val fits = mapOf(
            "e1" to makeFit(
                "e1",
                RecommendationQualityVerdict.ERROR,
                errorMessage = "Extension not found in available sources",
                evaluationVersion = SourceRecommendationFit.CURRENT_VERSION - 1,
            ),
        )
        val summary = compute(evals, fits)
        assertEquals(0, summary.checkedCount)
        assertEquals(1, summary.notCheckedCount)
        assertEquals(0, summary.installLoadIssueCount)
        assertEquals(0, summary.searchErrorCount)
    }
}
// KMK <--

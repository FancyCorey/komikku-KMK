package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.RecommendationQualityVerdict
import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationVerdict
import tachiyomi.domain.taste.model.SourceRecommendationFit

// KMK -->
class SourceRecommendationQualityDiagnosticsTest {

    private fun makeEval(key: String, verdict: SourceEvaluationVerdict) = SourceEvaluation(
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
        evaluationVersion = 1,
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
    )

    private fun makeFit(
        evalKey: String,
        verdict: RecommendationQualityVerdict,
        errorMessage: String? = null,
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
    )

    @Test
    fun `compute returns empty summary when no evaluations`() {
        val summary = SourceRecommendationQualityDiagnostics.compute(emptyList(), emptyMap())
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
        val summary = SourceRecommendationQualityDiagnostics.compute(evals, emptyMap())
        assertEquals(0, summary.totalPromisingCount)
        assertFalse(summary.hasAnyResults)
    }

    @Test
    fun `promising evaluations with no fit count as not checked`() {
        val evals = listOf(
            makeEval("e1", SourceEvaluationVerdict.STRONG_FIT),
            makeEval("e2", SourceEvaluationVerdict.WORTH_TRYING),
        )
        val summary = SourceRecommendationQualityDiagnostics.compute(evals, emptyMap())
        assertEquals(2, summary.totalPromisingCount)
        assertEquals(0, summary.checkedCount)
        assertEquals(2, summary.notCheckedCount)
        assertFalse(summary.hasAnyResults)
    }

    @Test
    fun `good verdict increments goodCount`() {
        val evals = listOf(makeEval("e1", SourceEvaluationVerdict.STRONG_FIT))
        val fits = mapOf("e1" to makeFit("e1", RecommendationQualityVerdict.GREAT))
        val summary = SourceRecommendationQualityDiagnostics.compute(evals, fits)
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
        val summary = SourceRecommendationQualityDiagnostics.compute(evals, fits)
        assertEquals(1, summary.weakCount)
        assertEquals(0, summary.goodCount)
    }

    @Test
    fun `NO_MATCHES verdict increments noResultsCount`() {
        val evals = listOf(makeEval("e1", SourceEvaluationVerdict.STRONG_FIT))
        val fits = mapOf("e1" to makeFit("e1", RecommendationQualityVerdict.NO_MATCHES))
        val summary = SourceRecommendationQualityDiagnostics.compute(evals, fits)
        assertEquals(1, summary.noResultsCount)
        assertEquals(0, summary.searchErrorCount)
    }

    @Test
    fun `TOO_LITTLE_EVIDENCE verdict increments noResultsCount`() {
        val evals = listOf(makeEval("e1", SourceEvaluationVerdict.STRONG_FIT))
        val fits = mapOf("e1" to makeFit("e1", RecommendationQualityVerdict.TOO_LITTLE_EVIDENCE))
        val summary = SourceRecommendationQualityDiagnostics.compute(evals, fits)
        assertEquals(1, summary.noResultsCount)
    }

    @Test
    fun `ERROR with install issue increments installLoadIssueCount`() {
        val evals = listOf(makeEval("e1", SourceEvaluationVerdict.STRONG_FIT))
        val fits = mapOf(
            "e1" to makeFit("e1", RecommendationQualityVerdict.ERROR, "Extension not found in available sources"),
        )
        val summary = SourceRecommendationQualityDiagnostics.compute(evals, fits)
        assertEquals(1, summary.installLoadIssueCount)
        assertEquals(0, summary.searchErrorCount)
    }

    @Test
    fun `ERROR with search error increments searchErrorCount`() {
        val evals = listOf(makeEval("e1", SourceEvaluationVerdict.STRONG_FIT))
        val fits = mapOf(
            "e1" to makeFit("e1", RecommendationQualityVerdict.ERROR, "Plan TOP_TAGS_FILTER: error — UnknownHostException"),
        )
        val summary = SourceRecommendationQualityDiagnostics.compute(evals, fits)
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
        val summary = SourceRecommendationQualityDiagnostics.compute(evals, fits)
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
        val summary = SourceRecommendationQualityDiagnostics.compute(evals, fits)
        assertEquals(2, summary.goodCount)
        assertEquals(0, summary.weakCount)
    }
}
// KMK <--

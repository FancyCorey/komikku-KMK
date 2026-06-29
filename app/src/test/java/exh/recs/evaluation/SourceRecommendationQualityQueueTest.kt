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
class SourceRecommendationQualityQueueTest {

    private fun makeEval(key: String, verdict: SourceEvaluationVerdict): SourceEvaluation =
        SourceEvaluation(
            evaluationKey = key,
            sourceId = 1L,
            extensionPkgName = "pkg.$key",
            signatureHash = "sig$key",
            extensionName = "Ext $key",
            sourceName = "Source $key",
            lang = "en",
            baseUrl = "https://example.com",
            repoName = "test-repo",
            sourceCount = 1,
            isNsfw = false,
            evaluationVersion = 1,
            evaluatedAt = 1_000_000L,
            expiresAt = null,
            sampleCount = 5,
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

    private fun makeFit(evaluationKey: String): SourceRecommendationFit =
        SourceRecommendationFit(
            fitKey = SourceRecommendationFit.fitKeyFor(evaluationKey),
            evaluationKey = evaluationKey,
            sourceId = 1L,
            extensionPkgName = "pkg.$evaluationKey",
            signatureHash = "sig$evaluationKey",
            extensionName = "Ext $evaluationKey",
            sourceName = "Source $evaluationKey",
            lang = "en",
            evaluatedAt = System.currentTimeMillis(),
            queryCount = 2,
            querySuccessCount = 2,
            rawResultCount = 10,
            visibleCandidateCount = 5,
            filteredOutCount = 2,
            blockedTagCandidateCount = 0,
            matchedGroupCount = 2,
            topPicksContribution = 1,
            noMatchesCount = 0,
            errorCount = 0,
            avgCandidateScore = 0.7,
            recommendationQualityScore = 0.65,
            verdict = RecommendationQualityVerdict.GOOD,
            reasonsJson = "[]",
            errorMessage = null,
        )

    // --- SourceRecommendationQualityQueue.compute() tests ---

    @Test
    fun `promising sources without fit are counted as missing`() {
        val strongFit = makeEval("strong", SourceEvaluationVerdict.STRONG_FIT)
        val worthTrying = makeEval("worth", SourceEvaluationVerdict.WORTH_TRYING)
        val result = SourceRecommendationQualityQueue.compute(
            evaluations = listOf(strongFit, worthTrying),
            fitsByEvalKey = emptyMap(),
        )
        assertEquals(2, result.missingCount)
        assertTrue(result.checkedPromising.isEmpty())
    }

    @Test
    fun `promising sources with fit are not counted as missing`() {
        val strongFit = makeEval("strong", SourceEvaluationVerdict.STRONG_FIT)
        val fit = makeFit("strong")
        val result = SourceRecommendationQualityQueue.compute(
            evaluations = listOf(strongFit),
            fitsByEvalKey = mapOf("strong" to fit),
        )
        assertEquals(0, result.missingCount)
        assertEquals(1, result.checkedPromising.size)
    }

    @Test
    fun `non-promising sources are excluded from promising buckets`() {
        val weak = makeEval("weak", SourceEvaluationVerdict.WEAK)
        val rejected = makeEval("rejected", SourceEvaluationVerdict.REJECTED)
        val error = makeEval("error", SourceEvaluationVerdict.ERROR)
        val explicit = makeEval("explicit", SourceEvaluationVerdict.EXPLICIT_HEAVY)
        val result = SourceRecommendationQualityQueue.compute(
            evaluations = listOf(weak, rejected, error, explicit),
            fitsByEvalKey = emptyMap(),
        )
        assertEquals(0, result.missingCount)
        assertTrue(result.checkedPromising.isEmpty())
        assertEquals(4, result.ineligible.size)
    }

    @Test
    fun `action targets only missing promising rows`() {
        val strongMissing = makeEval("s1", SourceEvaluationVerdict.STRONG_FIT)
        val worthChecked = makeEval("s2", SourceEvaluationVerdict.WORTH_TRYING)
        val fitForS2 = makeFit("s2")
        val result = SourceRecommendationQualityQueue.compute(
            evaluations = listOf(strongMissing, worthChecked),
            fitsByEvalKey = mapOf("s2" to fitForS2),
        )
        // reCheckAll=false: only missing
        assertEquals(listOf(strongMissing), result.missingPromising)
        assertEquals(listOf(worthChecked), result.checkedPromising)
    }

    @Test
    fun `re-check-all mode targets all promising including already checked`() {
        val s1 = makeEval("s1", SourceEvaluationVerdict.STRONG_FIT)
        val s2 = makeEval("s2", SourceEvaluationVerdict.WORTH_TRYING)
        val fitForS2 = makeFit("s2")
        val result = SourceRecommendationQualityQueue.compute(
            evaluations = listOf(s1, s2),
            fitsByEvalKey = mapOf("s2" to fitForS2),
        )
        // reCheckAll targets: missingPromising + checkedPromising
        val reCheckAllTargets = result.missingPromising + result.checkedPromising
        assertEquals(2, reCheckAllTargets.size)
        assertTrue(reCheckAllTargets.any { it.evaluationKey == "s1" })
        assertTrue(reCheckAllTargets.any { it.evaluationKey == "s2" })
    }

    @Test
    fun `totalPromising is sum of missing and checked`() {
        val s1 = makeEval("s1", SourceEvaluationVerdict.STRONG_FIT)
        val s2 = makeEval("s2", SourceEvaluationVerdict.WORTH_TRYING)
        val s3 = makeEval("s3", SourceEvaluationVerdict.STRONG_FIT)
        val fitForS3 = makeFit("s3")
        val result = SourceRecommendationQualityQueue.compute(
            evaluations = listOf(s1, s2, s3),
            fitsByEvalKey = mapOf("s3" to fitForS3),
        )
        assertEquals(2, result.missingCount)
        assertEquals(1, result.checkedPromising.size)
        assertEquals(3, result.totalPromising)
    }

    @Test
    fun `empty evaluations returns empty result`() {
        val result = SourceRecommendationQualityQueue.compute(
            evaluations = emptyList(),
            fitsByEvalKey = emptyMap(),
        )
        assertEquals(0, result.missingCount)
        assertTrue(result.checkedPromising.isEmpty())
        assertTrue(result.ineligible.isEmpty())
    }

    @Test
    fun `mixed evaluations partitioned correctly`() {
        val evals = listOf(
            makeEval("a", SourceEvaluationVerdict.STRONG_FIT),
            makeEval("b", SourceEvaluationVerdict.WORTH_TRYING),
            makeEval("c", SourceEvaluationVerdict.WEAK),
            makeEval("d", SourceEvaluationVerdict.REJECTED),
        )
        val fits = mapOf("b" to makeFit("b"))
        val result = SourceRecommendationQualityQueue.compute(evals, fits)
        assertEquals(1, result.missingCount) // a
        assertEquals(1, result.checkedPromising.size) // b
        assertEquals(2, result.ineligible.size) // c, d
        assertEquals("a", result.missingPromising.first().evaluationKey)
        assertEquals("b", result.checkedPromising.first().evaluationKey)
    }
}
// KMK <--

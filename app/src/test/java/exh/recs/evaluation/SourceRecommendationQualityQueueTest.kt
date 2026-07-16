package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
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
 * Tests for [SourceRecommendationQualityQueue.compute].
 *
 * v0.7.42-fix1: the queue delegates eligibility/staleness to [SourceRecommendationFitEligibility]
 * instead of a hardcoded STRONG_FIT/WORTH_TRYING set.
 *
 * v0.7.42-fix2: the old two-way missing/checked split is now three-way — missing (no fit),
 * outdated (stale fit), checked (current fit) — resolved via [SourceRecommendationFitDisplayPolicy]
 * so the queue, diagnostics, and row labels agree. `missingCount` (and `missingPromising`) now means
 * "no fit at all"; a stale fit is `outdatedPromising`, never `missingPromising`.
 */
class SourceRecommendationQualityQueueTest {

    private val now = 10_000_000L

    private fun makeEval(
        key: String,
        verdict: SourceEvaluationVerdict,
        sampleCount: Int = 5,
        // KMK --> v0.7.42-fix1: HIGH default = "confidently evaluated" for tests not about confidence itself
        confidence: SourceEvaluationMetadataConfidence = SourceEvaluationMetadataConfidence.HIGH,
        // KMK <--
    ): SourceEvaluation =
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
            evaluationVersion = SourceEvaluationKeys.CURRENT_VERSION,
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

    private fun makeFit(
        evaluationKey: String,
        evaluationVersion: Int = SourceRecommendationFit.CURRENT_VERSION,
        expiresAt: Long? = null,
    ): SourceRecommendationFit =
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
            evaluationVersion = evaluationVersion,
            expiresAt = expiresAt,
        )

    private fun compute(
        evaluations: List<SourceEvaluation>,
        fitsByEvalKey: Map<String, SourceRecommendationFit> = emptyMap(),
    ) = SourceRecommendationQualityQueue.compute(evaluations, fitsByEvalKey, now)

    // --- SourceRecommendationQualityQueue.compute() tests ---

    @Test
    fun `promising sources without fit are counted as missing`() {
        val strongFit = makeEval("strong", SourceEvaluationVerdict.STRONG_FIT)
        val worthTrying = makeEval("worth", SourceEvaluationVerdict.WORTH_TRYING)
        val result = compute(listOf(strongFit, worthTrying))
        assertEquals(2, result.missingCount)
        assertTrue(result.checkedPromising.isEmpty())
        assertTrue(result.outdatedPromising.isEmpty())
    }

    @Test
    fun `promising sources with fit are not counted as missing`() {
        val strongFit = makeEval("strong", SourceEvaluationVerdict.STRONG_FIT)
        val fit = makeFit("strong")
        val result = compute(listOf(strongFit), mapOf("strong" to fit))
        assertEquals(0, result.missingCount)
        assertEquals(1, result.checkedPromising.size)
    }

    @Test
    fun `non-promising sources with confidently-unfavorable verdicts are excluded from promising buckets`() {
        val weak = makeEval("weak", SourceEvaluationVerdict.WEAK)
        val rejected = makeEval("rejected", SourceEvaluationVerdict.REJECTED)
        val error = makeEval("error", SourceEvaluationVerdict.ERROR)
        val explicit = makeEval("explicit", SourceEvaluationVerdict.EXPLICIT_HEAVY)
        val result = compute(listOf(weak, rejected, error, explicit))
        assertEquals(0, result.missingCount)
        assertTrue(result.checkedPromising.isEmpty())
        assertTrue(result.outdatedPromising.isEmpty())
        assertEquals(4, result.ineligible.size)
    }

    @Test
    fun `action targets only missing promising rows`() {
        val strongMissing = makeEval("s1", SourceEvaluationVerdict.STRONG_FIT)
        val worthChecked = makeEval("s2", SourceEvaluationVerdict.WORTH_TRYING)
        val fitForS2 = makeFit("s2")
        val result = compute(listOf(strongMissing, worthChecked), mapOf("s2" to fitForS2))
        // reCheckAll=false: only missing
        assertEquals(listOf(strongMissing), result.missingPromising)
        assertEquals(listOf(worthChecked), result.checkedPromising)
    }

    @Test
    fun `re-check-all mode targets all promising including already checked and outdated`() {
        val s1 = makeEval("s1", SourceEvaluationVerdict.STRONG_FIT) // missing
        val s2 = makeEval("s2", SourceEvaluationVerdict.WORTH_TRYING) // checked
        val s3 = makeEval("s3", SourceEvaluationVerdict.STRONG_FIT) // outdated
        val fitForS2 = makeFit("s2")
        val staleFitForS3 = makeFit("s3", evaluationVersion = SourceRecommendationFit.CURRENT_VERSION - 1)
        val result = compute(listOf(s1, s2, s3), mapOf("s2" to fitForS2, "s3" to staleFitForS3))
        // reCheckAll targets: missingPromising + outdatedPromising + checkedPromising
        val reCheckAllTargets = result.missingPromising + result.outdatedPromising + result.checkedPromising
        assertEquals(3, reCheckAllTargets.size)
        assertTrue(reCheckAllTargets.any { it.evaluationKey == "s1" })
        assertTrue(reCheckAllTargets.any { it.evaluationKey == "s2" })
        assertTrue(reCheckAllTargets.any { it.evaluationKey == "s3" })
    }

    @Test
    fun `totalPromising is sum of missing, outdated, and checked`() {
        val s1 = makeEval("s1", SourceEvaluationVerdict.STRONG_FIT) // missing
        val s2 = makeEval("s2", SourceEvaluationVerdict.WORTH_TRYING) // checked
        val s3 = makeEval("s3", SourceEvaluationVerdict.STRONG_FIT) // outdated
        val fitForS2 = makeFit("s2")
        val staleFitForS3 = makeFit("s3", evaluationVersion = SourceRecommendationFit.CURRENT_VERSION - 1)
        val result = compute(listOf(s1, s2, s3), mapOf("s2" to fitForS2, "s3" to staleFitForS3))
        assertEquals(1, result.missingCount)
        assertEquals(1, result.outdatedCount)
        assertEquals(1, result.checkedPromising.size)
        assertEquals(3, result.totalPromising)
    }

    @Test
    fun `empty evaluations returns empty result`() {
        val result = compute(emptyList())
        assertEquals(0, result.missingCount)
        assertTrue(result.checkedPromising.isEmpty())
        assertTrue(result.outdatedPromising.isEmpty())
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
        val result = compute(evals, fits)
        assertEquals(1, result.missingCount) // a
        assertEquals(1, result.checkedPromising.size) // b
        assertEquals(2, result.ineligible.size) // c, d
        assertEquals("a", result.missingPromising.first().evaluationKey)
        assertEquals("b", result.checkedPromising.first().evaluationKey)
    }

    // --- v0.7.42-fix1: confidence-based fail-open eligibility (F1) ---

    @Test
    fun `WEAK verdict with LOW confidence and enough samples is missing when no fit`() {
        val eval = makeEval("weak-low", SourceEvaluationVerdict.WEAK, confidence = SourceEvaluationMetadataConfidence.LOW)
        val result = compute(listOf(eval))
        assertEquals(1, result.missingCount)
        assertTrue(result.ineligible.isEmpty())
    }

    @Test
    fun `NEUTRAL verdict with UNKNOWN confidence and enough samples is missing when no fit`() {
        val eval = makeEval("neutral-unknown", SourceEvaluationVerdict.NEUTRAL, confidence = SourceEvaluationMetadataConfidence.UNKNOWN)
        val result = compute(listOf(eval))
        assertEquals(1, result.missingCount)
        assertTrue(result.ineligible.isEmpty())
    }

    @Test
    fun `WEAK verdict with MODERATE confidence is ineligible — evidence was conclusive`() {
        val eval = makeEval("weak-moderate", SourceEvaluationVerdict.WEAK, confidence = SourceEvaluationMetadataConfidence.MODERATE)
        val result = compute(listOf(eval))
        assertEquals(0, result.missingCount)
        assertEquals(1, result.ineligible.size)
    }

    @Test
    fun `EXPLICIT_HEAVY verdict with UNKNOWN confidence stays ineligible — safety exclusion is unconditional`() {
        val eval = makeEval("explicit-unknown", SourceEvaluationVerdict.EXPLICIT_HEAVY, confidence = SourceEvaluationMetadataConfidence.UNKNOWN)
        val result = compute(listOf(eval))
        assertEquals(0, result.missingCount)
        assertEquals(1, result.ineligible.size)
    }

    // --- v0.7.42-fix2: missing vs. outdated are distinct buckets (F2 extended) ---

    @Test
    fun `fit with older evaluationVersion than current is outdated, not missing`() {
        val eval = makeEval("stale-version", SourceEvaluationVerdict.STRONG_FIT)
        val staleFit = makeFit("stale-version", evaluationVersion = SourceRecommendationFit.CURRENT_VERSION - 1)
        val result = compute(listOf(eval), mapOf("stale-version" to staleFit))
        assertEquals(0, result.missingCount)
        assertEquals(1, result.outdatedCount)
        assertTrue(result.checkedPromising.isEmpty())
    }

    @Test
    fun `expired fit is outdated, not missing`() {
        val eval = makeEval("expired", SourceEvaluationVerdict.STRONG_FIT)
        val expiredFit = makeFit("expired", expiresAt = now - 1)
        val result = compute(listOf(eval), mapOf("expired" to expiredFit))
        assertEquals(0, result.missingCount)
        assertEquals(1, result.outdatedCount)
        assertTrue(result.checkedPromising.isEmpty())
    }

    @Test
    fun `fit expiring exactly at now is outdated — boundary is exclusive`() {
        val eval = makeEval("boundary", SourceEvaluationVerdict.STRONG_FIT)
        val boundaryFit = makeFit("boundary", expiresAt = now)
        val result = compute(listOf(eval), mapOf("boundary" to boundaryFit))
        assertEquals(0, result.missingCount)
        assertEquals(1, result.outdatedCount)
    }

    @Test
    fun `fit not yet expired is treated as checked`() {
        val eval = makeEval("future", SourceEvaluationVerdict.STRONG_FIT)
        val freshFit = makeFit("future", expiresAt = now + 1)
        val result = compute(listOf(eval), mapOf("future" to freshFit))
        assertEquals(0, result.missingCount)
        assertEquals(0, result.outdatedCount)
        assertEquals(1, result.checkedPromising.size)
    }

    @Test
    fun `current fit with current version and no expiry is treated as checked`() {
        val eval = makeEval("current", SourceEvaluationVerdict.STRONG_FIT)
        val currentFit = makeFit("current")
        val result = compute(listOf(eval), mapOf("current" to currentFit))
        assertEquals(0, result.missingCount)
        assertEquals(0, result.outdatedCount)
        assertEquals(1, result.checkedPromising.size)
    }

    // --- v0.7.42-fix2: four buckets are complete and mutually exclusive ---

    @Test
    fun `every input evaluation lands in exactly one bucket`() {
        val missing = makeEval("missing", SourceEvaluationVerdict.STRONG_FIT)
        val outdated = makeEval("outdated", SourceEvaluationVerdict.STRONG_FIT)
        val checked = makeEval("checked", SourceEvaluationVerdict.STRONG_FIT)
        val ineligible = makeEval("ineligible", SourceEvaluationVerdict.EXPLICIT_HEAVY)
        val fits = mapOf(
            "outdated" to makeFit("outdated", evaluationVersion = SourceRecommendationFit.CURRENT_VERSION - 1),
            "checked" to makeFit("checked"),
        )
        val evals = listOf(missing, outdated, checked, ineligible)
        val result = compute(evals, fits)

        val allBucketed = result.missingPromising + result.outdatedPromising + result.checkedPromising + result.ineligible
        assertEquals(evals.size, allBucketed.size) { "Buckets are not collectively exhaustive: $allBucketed" }
        assertEquals(evals.toSet(), allBucketed.toSet())

        // Mutually exclusive: no evaluationKey appears in more than one bucket.
        val keysPerBucket = listOf(result.missingPromising, result.outdatedPromising, result.checkedPromising, result.ineligible)
            .map { bucket -> bucket.map { it.evaluationKey }.toSet() }
        for (i in keysPerBucket.indices) {
            for (j in keysPerBucket.indices) {
                if (i != j) {
                    assertTrue(keysPerBucket[i].intersect(keysPerBucket[j]).isEmpty()) {
                        "Buckets $i and $j overlap: ${keysPerBucket[i]} vs ${keysPerBucket[j]}"
                    }
                }
            }
        }

        assertEquals(listOf("missing"), result.missingPromising.map { it.evaluationKey })
        assertEquals(listOf("outdated"), result.outdatedPromising.map { it.evaluationKey })
        assertEquals(listOf("checked"), result.checkedPromising.map { it.evaluationKey })
        assertEquals(listOf("ineligible"), result.ineligible.map { it.evaluationKey })
    }

    @Test
    fun `needsCheckPromising combines missing and outdated only`() {
        val missing = makeEval("missing", SourceEvaluationVerdict.STRONG_FIT)
        val outdated = makeEval("outdated", SourceEvaluationVerdict.STRONG_FIT)
        val checked = makeEval("checked", SourceEvaluationVerdict.STRONG_FIT)
        val fits = mapOf(
            "outdated" to makeFit("outdated", evaluationVersion = SourceRecommendationFit.CURRENT_VERSION - 1),
            "checked" to makeFit("checked"),
        )
        val result = compute(listOf(missing, outdated, checked), fits)
        assertEquals(setOf("missing", "outdated"), result.needsCheckPromising.map { it.evaluationKey }.toSet())
    }

    @Test
    fun `outdated-only target excludes current and missing rows`() {
        val missing = makeEval("missing", SourceEvaluationVerdict.STRONG_FIT)
        val outdated = makeEval("outdated", SourceEvaluationVerdict.STRONG_FIT)
        val checked = makeEval("checked", SourceEvaluationVerdict.STRONG_FIT)
        val fits = mapOf(
            "outdated" to makeFit("outdated", evaluationVersion = SourceRecommendationFit.CURRENT_VERSION - 1),
            "checked" to makeFit("checked"),
        )
        val result = compute(listOf(missing, outdated, checked), fits)
        // Simulates recheckOutdatedRecommendationQuality()'s target selection.
        val outdatedOnlyTargets = result.outdatedPromising
        assertEquals(listOf("outdated"), outdatedOnlyTargets.map { it.evaluationKey })
        assertTrue(outdatedOnlyTargets.none { it.evaluationKey == "missing" })
        assertTrue(outdatedOnlyTargets.none { it.evaluationKey == "checked" })
    }
}
// KMK <--

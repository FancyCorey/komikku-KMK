package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationVerdict

// KMK -->
class SourceEvaluationResultListTest {

    // --- helpers ---

    private fun evaluation(
        key: String,
        verdict: SourceEvaluationVerdict = SourceEvaluationVerdict.NEUTRAL,
        sourceName: String = "Test Source",
        extensionName: String = "Test Extension",
        recommendationFitScore: Double = 0.5,
        qualityScore: Double = 0.5,
        searchReliabilityScore: Double = 0.5,
        explicitScore: Double = 0.0,
        ecchiScore: Double = 0.0,
        evaluatedAt: Long = 0L,
        pkgName: String = "com.test",
        signatureHash: String = "abc",
        sourceId: Long? = null,
    ): SourceEvaluation = SourceEvaluation(
        evaluationKey = key,
        sourceId = sourceId,
        extensionPkgName = pkgName,
        signatureHash = signatureHash,
        extensionName = extensionName,
        sourceName = sourceName,
        lang = "en",
        baseUrl = null,
        repoName = null,
        sourceCount = 1,
        isNsfw = false,
        evaluationVersion = 1,
        evaluatedAt = evaluatedAt,
        expiresAt = null,
        sampleCount = 0,
        popularCount = 0,
        latestCount = 0,
        searchCount = 0,
        searchSuccessCount = 0,
        likedTitleMatchCount = 0,
        preferredTagMatchCount = 0,
        blockedTagMatchCount = 0,
        explicitSignalCount = 0,
        ecchiSignalCount = 0,
        errorCount = 0,
        qualityScore = qualityScore,
        recommendationFitScore = recommendationFitScore,
        searchReliabilityScore = searchReliabilityScore,
        explicitScore = explicitScore,
        ecchiScore = ecchiScore,
        verdict = verdict,
        sampledTitlesJson = null,
        sampledTagsJson = null,
        errorMessage = null,
    )

    // --- sanitize ---

    @Test
    fun `blank evaluationKey row is removed`() {
        val evals = listOf(
            evaluation("valid-key"),
            evaluation(""),
            evaluation("  "),
            evaluation("another-valid"),
        )
        val result = SourceEvaluationResultList.sanitize(evals)
        assertEquals(2, result.size)
        assertTrue(result.all { it.evaluationKey.isNotBlank() })
    }

    @Test
    fun `duplicate evaluationKey rows are deduped keeping first occurrence`() {
        val evals = listOf(
            evaluation("key-a", sourceName = "First"),
            evaluation("key-a", sourceName = "Duplicate"),
            evaluation("key-b", sourceName = "Unique"),
        )
        val result = SourceEvaluationResultList.sanitize(evals)
        assertEquals(2, result.size)
        assertEquals("First", result.first { it.evaluationKey == "key-a" }.sourceName)
    }

    @Test
    fun `sanitize on empty list returns empty`() {
        assertEquals(emptyList<SourceEvaluation>(), SourceEvaluationResultList.sanitize(emptyList()))
    }

    // --- sort: BEST_FIT ---

    @Test
    fun `best-fit sort uses verdict rank before score`() {
        val evals = listOf(
            evaluation("e1", verdict = SourceEvaluationVerdict.ERROR, recommendationFitScore = 0.99),
            evaluation("e2", verdict = SourceEvaluationVerdict.STRONG_FIT, recommendationFitScore = 0.1),
            evaluation("e3", verdict = SourceEvaluationVerdict.WORTH_TRYING, recommendationFitScore = 0.5),
        )
        val sorted = SourceEvaluationResultList.sort(evals, SourceEvaluationResultList.SortMode.BEST_FIT)
        assertEquals("e2", sorted[0].evaluationKey) // STRONG_FIT first
        assertEquals("e3", sorted[1].evaluationKey) // WORTH_TRYING second
        assertEquals("e1", sorted[2].evaluationKey) // ERROR last despite high score
    }

    @Test
    fun `best-fit sort uses recommendationFitScore as tie-breaker within same verdict`() {
        val evals = listOf(
            evaluation("low", verdict = SourceEvaluationVerdict.WORTH_TRYING, recommendationFitScore = 0.6),
            evaluation("high", verdict = SourceEvaluationVerdict.WORTH_TRYING, recommendationFitScore = 0.9),
            evaluation("mid", verdict = SourceEvaluationVerdict.WORTH_TRYING, recommendationFitScore = 0.75),
        )
        val sorted = SourceEvaluationResultList.sort(evals, SourceEvaluationResultList.SortMode.BEST_FIT)
        assertEquals("high", sorted[0].evaluationKey)
        assertEquals("mid", sorted[1].evaluationKey)
        assertEquals("low", sorted[2].evaluationKey)
    }

    @Test
    fun `best-fit verdict rank all verdicts ordered correctly`() {
        val allVerdicts = listOf(
            SourceEvaluationVerdict.ERROR,
            SourceEvaluationVerdict.REJECTED,
            SourceEvaluationVerdict.EXPLICIT_HEAVY,
            SourceEvaluationVerdict.ECCHI_HEAVY,
            SourceEvaluationVerdict.POOR_SEARCH,
            SourceEvaluationVerdict.WEAK,
            SourceEvaluationVerdict.NEEDS_MANUAL_REVIEW,
            SourceEvaluationVerdict.NEUTRAL,
            SourceEvaluationVerdict.WORTH_TRYING,
            SourceEvaluationVerdict.STRONG_FIT,
        )
        val ranks = allVerdicts.map { SourceEvaluationResultList.verdictRank(it) }
        // All ranks are distinct
        assertEquals(ranks.size, ranks.toSet().size)
        // STRONG_FIT has the lowest rank
        assertEquals(0, SourceEvaluationResultList.verdictRank(SourceEvaluationVerdict.STRONG_FIT))
        // ERROR has the highest rank
        assertEquals(9, SourceEvaluationResultList.verdictRank(SourceEvaluationVerdict.ERROR))
        // Ranks should be non-negative
        assertTrue(ranks.all { it >= 0 })
    }

    // --- sort: NEWEST ---

    @Test
    fun `newest sort orders by evaluatedAt descending`() {
        val evals = listOf(
            evaluation("old", evaluatedAt = 100L),
            evaluation("newest", evaluatedAt = 300L),
            evaluation("mid", evaluatedAt = 200L),
        )
        val sorted = SourceEvaluationResultList.sort(evals, SourceEvaluationResultList.SortMode.NEWEST)
        assertEquals("newest", sorted[0].evaluationKey)
        assertEquals("mid", sorted[1].evaluationKey)
        assertEquals("old", sorted[2].evaluationKey)
    }

    // --- sort: SOURCE_NAME ---

    @Test
    fun `source-name sort is case-insensitive`() {
        val evals = listOf(
            evaluation("c", sourceName = "Zebra Source"),
            evaluation("a", sourceName = "alpha source"),
            evaluation("b", sourceName = "Beta Source"),
        )
        val sorted = SourceEvaluationResultList.sort(evals, SourceEvaluationResultList.SortMode.SOURCE_NAME)
        assertEquals("a", sorted[0].evaluationKey)
        assertEquals("b", sorted[1].evaluationKey)
        assertEquals("c", sorted[2].evaluationKey)
    }

    // --- sort: EXTENSION_NAME ---

    @Test
    fun `extension-name sort is case-insensitive`() {
        val evals = listOf(
            evaluation("3", extensionName = "zoo Ext"),
            evaluation("1", extensionName = "Alpha Ext"),
            evaluation("2", extensionName = "beta ext"),
        )
        val sorted = SourceEvaluationResultList.sort(evals, SourceEvaluationResultList.SortMode.EXTENSION_NAME)
        assertEquals("1", sorted[0].evaluationKey)
        assertEquals("2", sorted[1].evaluationKey)
        assertEquals("3", sorted[2].evaluationKey)
    }

    // --- sort: EXPLICIT_RISK ---

    @Test
    fun `explicit-risk sort places high explicit scores first`() {
        val evals = listOf(
            evaluation("low", explicitScore = 0.1),
            evaluation("high", explicitScore = 0.9),
            evaluation("mid", explicitScore = 0.5),
        )
        val sorted = SourceEvaluationResultList.sort(evals, SourceEvaluationResultList.SortMode.EXPLICIT_RISK)
        assertEquals("high", sorted[0].evaluationKey)
        assertEquals("mid", sorted[1].evaluationKey)
        assertEquals("low", sorted[2].evaluationKey)
    }

    // --- stableUiKey ---

    @Test
    fun `stableUiKey never returns blank for valid key`() {
        val eval = evaluation("sig|com.test|42")
        val key = SourceEvaluationResultList.stableUiKey(eval, 0)
        assertTrue(key.isNotBlank())
        assertTrue(key.contains("sig|com.test|42"))
    }

    @Test
    fun `stableUiKey uses fallback for blank evaluationKey`() {
        val eval = evaluation("", pkgName = "com.fallback", signatureHash = "def", sourceId = 99L)
        val key = SourceEvaluationResultList.stableUiKey(eval, 5)
        assertTrue(key.isNotBlank())
        assertTrue(key.contains("com.fallback"))
        assertTrue(key.contains("def"))
        assertTrue(key.contains("99"))
        assertTrue(key.contains("5"))
    }

    @Test
    fun `stableUiKey is not null or empty`() {
        listOf(
            evaluation("valid"),
            evaluation(""),
        ).forEachIndexed { index, eval ->
            val key = SourceEvaluationResultList.stableUiKey(eval, index)
            assertNotNull(key)
            assertTrue(key.isNotEmpty(), "stableUiKey should never be empty for index=$index")
        }
    }
}
// KMK <--

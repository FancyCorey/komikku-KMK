package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.RecommendationQualityVerdict
import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationKeys
import tachiyomi.domain.taste.model.SourceEvaluationVerdict
import tachiyomi.domain.taste.model.SourceRecommendationFit

// KMK -->
class SourceEvaluationResultListTest {

    private val now = 10_000_000L

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
        evaluationVersion: Int = SourceEvaluationKeys.CURRENT_VERSION,
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
        evaluationVersion = evaluationVersion,
        evaluatedAt = evaluatedAt,
        expiresAt = null,
        sampleCount = 5,
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

    private fun fit(
        evaluationKey: String,
        verdict: RecommendationQualityVerdict = RecommendationQualityVerdict.GOOD,
        recommendationQualityScore: Double = 0.5,
        evaluationVersion: Int = SourceRecommendationFit.CURRENT_VERSION,
        expiresAt: Long? = null,
    ): SourceRecommendationFit = SourceRecommendationFit(
        fitKey = SourceRecommendationFit.fitKeyFor(evaluationKey),
        evaluationKey = evaluationKey,
        sourceId = null,
        extensionPkgName = "com.test",
        signatureHash = "abc",
        extensionName = "Test Extension",
        sourceName = "Test Source",
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
        recommendationQualityScore = recommendationQualityScore,
        verdict = verdict,
        reasonsJson = "[]",
        errorMessage = null,
        evaluationVersion = evaluationVersion,
        expiresAt = expiresAt,
    )

    private fun sort(
        evals: List<SourceEvaluation>,
        mode: SourceEvaluationResultList.SortMode,
        fits: Map<String, SourceRecommendationFit> = emptyMap(),
    ) = SourceEvaluationResultList.sort(evals, fits, mode, now)

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
        val sorted = sort(evals, SourceEvaluationResultList.SortMode.BEST_FIT)
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
        val sorted = sort(evals, SourceEvaluationResultList.SortMode.BEST_FIT)
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

    // KMK --> v0.7.47: stale (outdated scoring-version) rows must never outrank current rows.
    @Test
    fun `current rows sort above outdated rows regardless of stored verdict`() {
        val evals = listOf(
            evaluation(
                "stale-strong",
                verdict = SourceEvaluationVerdict.STRONG_FIT,
                recommendationFitScore = 0.95,
                evaluationVersion = SourceEvaluationKeys.CURRENT_VERSION - 1,
            ),
            evaluation(
                "current-worth",
                verdict = SourceEvaluationVerdict.WORTH_TRYING,
                recommendationFitScore = 0.5,
                evaluationVersion = SourceEvaluationKeys.CURRENT_VERSION,
            ),
        )
        val sorted = sort(evals, SourceEvaluationResultList.SortMode.BEST_FIT)
        assertEquals("current-worth", sorted[0].evaluationKey)
        assertEquals("stale-strong", sorted[1].evaluationKey)
    }

    @Test
    fun `outdated strong_fit does not outrank current worth_trying`() {
        val stale = evaluation(
            "old-strong",
            verdict = SourceEvaluationVerdict.STRONG_FIT,
            recommendationFitScore = 1.0,
            evaluationVersion = 1,
        )
        val current = evaluation(
            "new-worth",
            verdict = SourceEvaluationVerdict.WORTH_TRYING,
            recommendationFitScore = 0.5,
            evaluationVersion = SourceEvaluationKeys.CURRENT_VERSION,
        )
        val sorted = sort(listOf(stale, current), SourceEvaluationResultList.SortMode.BEST_FIT)
        assertEquals("new-worth", sorted.first().evaluationKey)
    }

    @Test
    fun `metadata-sparse NEEDS_MANUAL_REVIEW rows are not sorted or treated as errors`() {
        val sparse = evaluation(
            "sparse",
            verdict = SourceEvaluationVerdict.NEEDS_MANUAL_REVIEW,
            evaluationVersion = SourceEvaluationKeys.CURRENT_VERSION,
        )
        val error = evaluation(
            "err",
            verdict = SourceEvaluationVerdict.ERROR,
            evaluationVersion = SourceEvaluationKeys.CURRENT_VERSION,
        )
        val sorted = sort(listOf(error, sparse), SourceEvaluationResultList.SortMode.BEST_FIT)
        // NEEDS_MANUAL_REVIEW must rank above ERROR — it is inconclusive evidence, not a failure.
        assertEquals("sparse", sorted[0].evaluationKey)
        assertEquals("err", sorted[1].evaluationKey)
    }
    // KMK <--

    @Test
    fun `best-fit sort never reads searchReliabilityScore — rows differing only by it stay a deterministic tie`() {
        val evals = listOf(
            evaluation("a", verdict = SourceEvaluationVerdict.WORTH_TRYING, recommendationFitScore = 0.5, qualityScore = 0.5, searchReliabilityScore = 0.9, evaluatedAt = 100L, sourceName = "Alpha"),
            evaluation("b", verdict = SourceEvaluationVerdict.WORTH_TRYING, recommendationFitScore = 0.5, qualityScore = 0.5, searchReliabilityScore = 0.1, evaluatedAt = 100L, sourceName = "Beta"),
        )
        val sorted = sort(evals, SourceEvaluationResultList.SortMode.BEST_FIT)
        // Equal verdict/fit/quality/evaluatedAt: falls through to case-insensitive source name, not
        // searchReliabilityScore (which would have put "a" first since it's higher).
        assertEquals("a", sorted[0].evaluationKey) // "Alpha" < "Beta"
        assertEquals("b", sorted[1].evaluationKey)
    }

    @Test
    fun `best-fit uses current compatibility as a tie-breaker only after equal catalogue evidence`() {
        val evals = listOf(
            evaluation("weakerCompat", verdict = SourceEvaluationVerdict.WORTH_TRYING, recommendationFitScore = 0.5, qualityScore = 0.5),
            evaluation("strongerCompat", verdict = SourceEvaluationVerdict.WORTH_TRYING, recommendationFitScore = 0.5, qualityScore = 0.5),
        )
        val fits = mapOf(
            "weakerCompat" to fit("weakerCompat", RecommendationQualityVerdict.WEAK),
            "strongerCompat" to fit("strongerCompat", RecommendationQualityVerdict.GREAT),
        )
        val sorted = sort(evals, SourceEvaluationResultList.SortMode.BEST_FIT, fits)
        assertEquals("strongerCompat", sorted[0].evaluationKey)
        assertEquals("weakerCompat", sorted[1].evaluationKey)
    }

    @Test
    fun `best-fit does not let compatibility override unequal catalogue evidence`() {
        // Lower recommendationFitScore but GREAT compatibility must still lose to higher catalogue fit.
        val evals = listOf(
            evaluation("betterCatalogue", verdict = SourceEvaluationVerdict.WORTH_TRYING, recommendationFitScore = 0.9, qualityScore = 0.5),
            evaluation("worseCatalogueBetterCompat", verdict = SourceEvaluationVerdict.WORTH_TRYING, recommendationFitScore = 0.1, qualityScore = 0.5),
        )
        val fits = mapOf(
            "worseCatalogueBetterCompat" to fit("worseCatalogueBetterCompat", RecommendationQualityVerdict.GREAT),
        )
        val sorted = sort(evals, SourceEvaluationResultList.SortMode.BEST_FIT, fits)
        assertEquals("betterCatalogue", sorted[0].evaluationKey)
    }

    @Test
    fun `best-fit treats outdated and not-checked as neutral ties, not as a compatibility loss`() {
        val evals = listOf(
            evaluation("outdated", verdict = SourceEvaluationVerdict.WORTH_TRYING, recommendationFitScore = 0.5, qualityScore = 0.5, evaluatedAt = 200L, sourceName = "Alpha"),
            evaluation("notChecked", verdict = SourceEvaluationVerdict.WORTH_TRYING, recommendationFitScore = 0.5, qualityScore = 0.5, evaluatedAt = 100L, sourceName = "Beta"),
        )
        val fits = mapOf(
            "outdated" to fit("outdated", RecommendationQualityVerdict.GREAT, evaluationVersion = SourceRecommendationFit.CURRENT_VERSION - 1),
        )
        val sorted = sort(evals, SourceEvaluationResultList.SortMode.BEST_FIT, fits)
        // Compatibility tie-break is neutral (0) for both since neither is a current outcome, so the
        // next tie-breaker (newest evaluatedAt) decides: "outdated" (200L) before "notChecked" (100L).
        assertEquals("outdated", sorted[0].evaluationKey)
        assertEquals("notChecked", sorted[1].evaluationKey)
    }

    // --- sort: NEWEST ---

    @Test
    fun `newest sort orders by evaluatedAt descending`() {
        val evals = listOf(
            evaluation("old", evaluatedAt = 100L),
            evaluation("newest", evaluatedAt = 300L),
            evaluation("mid", evaluatedAt = 200L),
        )
        val sorted = sort(evals, SourceEvaluationResultList.SortMode.NEWEST)
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
        val sorted = sort(evals, SourceEvaluationResultList.SortMode.SOURCE_NAME)
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
        val sorted = sort(evals, SourceEvaluationResultList.SortMode.EXTENSION_NAME)
        assertEquals("1", sorted[0].evaluationKey)
        assertEquals("2", sorted[1].evaluationKey)
        assertEquals("3", sorted[2].evaluationKey)
    }

    // --- sort: FOR_YOU_COMPATIBILITY ---

    @Test
    fun `for-you-compatibility sort orders current positive, weak, no-matches, error, outdated, missing, ineligible`() {
        val great = evaluation("great", verdict = SourceEvaluationVerdict.STRONG_FIT)
        val weak = evaluation("weak", verdict = SourceEvaluationVerdict.STRONG_FIT)
        val noMatches = evaluation("noMatches", verdict = SourceEvaluationVerdict.STRONG_FIT)
        val error = evaluation("error", verdict = SourceEvaluationVerdict.STRONG_FIT)
        val outdated = evaluation("outdated", verdict = SourceEvaluationVerdict.STRONG_FIT)
        val notChecked = evaluation("notChecked", verdict = SourceEvaluationVerdict.STRONG_FIT)
        val ineligible = evaluation("ineligible", verdict = SourceEvaluationVerdict.EXPLICIT_HEAVY)

        val evals = listOf(ineligible, notChecked, outdated, error, noMatches, weak, great)
        val fits = mapOf(
            "great" to fit("great", RecommendationQualityVerdict.GREAT),
            "weak" to fit("weak", RecommendationQualityVerdict.WEAK),
            "noMatches" to fit("noMatches", RecommendationQualityVerdict.NO_MATCHES),
            "error" to fit("error", RecommendationQualityVerdict.ERROR),
            "outdated" to fit("outdated", RecommendationQualityVerdict.GOOD, evaluationVersion = SourceRecommendationFit.CURRENT_VERSION - 1),
        )

        val sorted = sort(evals, SourceEvaluationResultList.SortMode.FOR_YOU_COMPATIBILITY, fits)
        assertEquals(
            listOf("great", "weak", "noMatches", "error", "outdated", "notChecked", "ineligible"),
            sorted.map { it.evaluationKey },
        )
    }

    @Test
    fun `for-you-compatibility sort ranks current compatibility above stale compatibility`() {
        val current = evaluation("current", verdict = SourceEvaluationVerdict.STRONG_FIT)
        val stale = evaluation("stale", verdict = SourceEvaluationVerdict.STRONG_FIT)
        val fits = mapOf(
            "current" to fit("current", RecommendationQualityVerdict.WEAK), // WEAK is still current-outcome
            "stale" to fit("stale", RecommendationQualityVerdict.GREAT, evaluationVersion = SourceRecommendationFit.CURRENT_VERSION - 1),
        )
        val sorted = sort(listOf(stale, current), SourceEvaluationResultList.SortMode.FOR_YOU_COMPATIBILITY, fits)
        // Even a WEAK *current* result outranks a stale GREAT result — staleness is never treated as
        // "as good as weak"; it is its own lower bucket regardless of the old verdict it once had.
        assertEquals("current", sorted[0].evaluationKey)
        assertEquals("stale", sorted[1].evaluationKey)
    }

    @Test
    fun `for-you-compatibility sort never treats stale or missing as equal to weak`() {
        val weak = evaluation("weak", verdict = SourceEvaluationVerdict.STRONG_FIT)
        val outdated = evaluation("outdated", verdict = SourceEvaluationVerdict.STRONG_FIT)
        val notChecked = evaluation("notChecked", verdict = SourceEvaluationVerdict.STRONG_FIT)
        val fits = mapOf(
            "weak" to fit("weak", RecommendationQualityVerdict.WEAK),
            "outdated" to fit("outdated", RecommendationQualityVerdict.WEAK, evaluationVersion = SourceRecommendationFit.CURRENT_VERSION - 1),
        )
        val sorted = sort(listOf(notChecked, outdated, weak), SourceEvaluationResultList.SortMode.FOR_YOU_COMPATIBILITY, fits)
        assertEquals(listOf("weak", "outdated", "notChecked"), sorted.map { it.evaluationKey })
    }

    @Test
    fun `for-you-compatibility sort orders current positive outcomes by recommendationQualityScore descending`() {
        val evals = listOf(
            evaluation("low", verdict = SourceEvaluationVerdict.STRONG_FIT),
            evaluation("high", verdict = SourceEvaluationVerdict.STRONG_FIT),
            evaluation("mid", verdict = SourceEvaluationVerdict.STRONG_FIT),
        )
        val fits = mapOf(
            "low" to fit("low", RecommendationQualityVerdict.GOOD, recommendationQualityScore = 0.2),
            "high" to fit("high", RecommendationQualityVerdict.GOOD, recommendationQualityScore = 0.9),
            "mid" to fit("mid", RecommendationQualityVerdict.GOOD, recommendationQualityScore = 0.55),
        )
        val sorted = sort(evals, SourceEvaluationResultList.SortMode.FOR_YOU_COMPATIBILITY, fits)
        assertEquals(listOf("high", "mid", "low"), sorted.map { it.evaluationKey })
    }

    // --- sort: EXPLICIT_RISK ---

    @Test
    fun `explicit-risk sort places high explicit scores first`() {
        val evals = listOf(
            evaluation("low", explicitScore = 0.1),
            evaluation("high", explicitScore = 0.9),
            evaluation("mid", explicitScore = 0.5),
        )
        val sorted = sort(evals, SourceEvaluationResultList.SortMode.EXPLICIT_RISK)
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

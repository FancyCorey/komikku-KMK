package exh.recs.evaluation

import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationVerdict
import tachiyomi.domain.taste.model.SourceRecommendationFit

// KMK -->
/**
 * Pure helper for sanitizing, sorting, and generating stable UI keys for Source Evaluation results.
 * Android-free so it can be unit-tested without instrumentation.
 */
object SourceEvaluationResultList {

    enum class SortMode {
        BEST_FIT,
        NEWEST,
        SOURCE_NAME,
        EXTENSION_NAME,
        // KMK --> v0.7.42-fix2: replaces SEARCH_RELIABILITY, which sorted a field
        // (SourceEvaluation.searchReliabilityScore) that v0.7.42 intentionally always writes as 0.0.
        // Sorts by the real For You search-compatibility state instead — see
        // SourceRecommendationFitDisplayPolicy.
        FOR_YOU_COMPATIBILITY,
        // KMK <--
        EXPLICIT_RISK,
    }

    /**
     * Drop rows with a blank evaluationKey and dedupe by evaluationKey.
     * Returns an empty list instead of throwing if an unexpected error occurs.
     */
    fun sanitize(evaluations: List<SourceEvaluation>): List<SourceEvaluation> {
        return try {
            evaluations
                .filter { it.evaluationKey.isNotBlank() }
                .distinctBy { it.evaluationKey }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Sort [evaluations] by [mode].
     * Results are stable (equal elements maintain their relative order).
     *
     * KMK --> v0.7.42-fix2: gained [fitsByEvalKey] and [now] so BEST_FIT's compatibility tie-break
     * and FOR_YOU_COMPATIBILITY can resolve each row's [CompatibilityDisplayState] via
     * [SourceRecommendationFitDisplayPolicy]. Capture [now] once per call (e.g. once per composition
     * or action) — do not let comparators re-read the clock mid-sort.
     * KMK <--
     */
    fun sort(
        evaluations: List<SourceEvaluation>,
        fitsByEvalKey: Map<String, SourceRecommendationFit>,
        mode: SortMode,
        now: Long = System.currentTimeMillis(),
    ): List<SourceEvaluation> = when (mode) {
        SortMode.BEST_FIT -> evaluations.sortedWith(bestFitComparator(fitsByEvalKey, now))
        SortMode.NEWEST -> evaluations.sortedByDescending { it.evaluatedAt }
        SortMode.SOURCE_NAME -> evaluations.sortedWith(sourceNameComparator)
        SortMode.EXTENSION_NAME -> evaluations.sortedWith(extensionNameComparator)
        SortMode.FOR_YOU_COMPATIBILITY -> evaluations.sortedWith(forYouCompatibilityComparator(fitsByEvalKey, now))
        SortMode.EXPLICIT_RISK -> evaluations.sortedWith(explicitRiskComparator)
    }

    /**
     * Returns a stable, non-blank LazyColumn key for [evaluation].
     * Uses evaluationKey when non-blank; falls back to a composite string for malformed rows.
     */
    fun stableUiKey(evaluation: SourceEvaluation, index: Int): String {
        val key = evaluation.evaluationKey
        return if (key.isNotBlank()) {
            "source-evaluation-$key"
        } else {
            val src = evaluation.sourceId ?: "no-source"
            "source-evaluation-fallback-${evaluation.extensionPkgName}-${evaluation.signatureHash}-$src-$index"
        }
    }

    // --- Comparators ---

    // KMK --> v0.7.42-fix2: catalogue-first, with For You compatibility as a true tie-breaker only.
    // Compatibility only distinguishes rows when BOTH are current outcomes (isCurrentOutcome) — an
    // outdated/not-checked/ineligible row never wins or loses this step; it falls through neutrally
    // to the newest-evaluation/name tie-breakers, exactly like two equal current rows would.
    // Never reads searchReliabilityScore.
    private fun bestFitComparator(
        fitsByEvalKey: Map<String, SourceRecommendationFit>,
        now: Long,
    ): Comparator<SourceEvaluation> = Comparator { a, b ->
        // KMK --> v0.7.47: a stale (outdated-version/expired) row must never outrank a current row,
        // regardless of its own stored verdict — see SourceEvaluationDisplayPolicy.
        val verdict = effectiveVerdictRank(a, now).compareTo(effectiveVerdictRank(b, now))
        if (verdict != 0) return@Comparator verdict
        // KMK <--

        val fit = b.recommendationFitScore.compareTo(a.recommendationFitScore)
        if (fit != 0) return@Comparator fit

        val qual = b.qualityScore.compareTo(a.qualityScore)
        if (qual != 0) return@Comparator qual

        val stateA = SourceRecommendationFitDisplayPolicy.resolve(a, fitsByEvalKey[a.evaluationKey], now)
        val stateB = SourceRecommendationFitDisplayPolicy.resolve(b, fitsByEvalKey[b.evaluationKey], now)
        val compat = if (stateA.isCurrentOutcome && stateB.isCurrentOutcome) {
            SourceRecommendationFitDisplayPolicy.compatibilityRank(stateA)
                .compareTo(SourceRecommendationFitDisplayPolicy.compatibilityRank(stateB))
        } else {
            0
        }
        if (compat != 0) return@Comparator compat

        val time = b.evaluatedAt.compareTo(a.evaluatedAt)
        if (time != 0) return@Comparator time

        val src = String.CASE_INSENSITIVE_ORDER.compare(a.sourceName, b.sourceName)
        if (src != 0) return@Comparator src

        String.CASE_INSENSITIVE_ORDER.compare(a.extensionName, b.extensionName)
    }
    // KMK <--

    private val sourceNameComparator: Comparator<SourceEvaluation> = Comparator { a, b ->
        val src = String.CASE_INSENSITIVE_ORDER.compare(a.sourceName, b.sourceName)
        if (src != 0) src else String.CASE_INSENSITIVE_ORDER.compare(a.extensionName, b.extensionName)
    }

    private val extensionNameComparator: Comparator<SourceEvaluation> = Comparator { a, b ->
        val ext = String.CASE_INSENSITIVE_ORDER.compare(a.extensionName, b.extensionName)
        if (ext != 0) ext else String.CASE_INSENSITIVE_ORDER.compare(a.sourceName, b.sourceName)
    }

    // KMK --> v0.7.42-fix2: replaces searchReliabilityComparator. Orders by
    // SourceRecommendationFitDisplayPolicy's seven-bucket rank (current positive/weak/no-matches/
    // error, then outdated, then not-checked, then ineligible last); within the current-positive
    // bucket, higher recommendationQualityScore sorts first. Stale and unprobed rows are never
    // treated as "weak" — they occupy their own distinct buckets below every current outcome.
    private fun forYouCompatibilityComparator(
        fitsByEvalKey: Map<String, SourceRecommendationFit>,
        now: Long,
    ): Comparator<SourceEvaluation> = Comparator { a, b ->
        val fitA = fitsByEvalKey[a.evaluationKey]
        val fitB = fitsByEvalKey[b.evaluationKey]
        val stateA = SourceRecommendationFitDisplayPolicy.resolve(a, fitA, now)
        val stateB = SourceRecommendationFitDisplayPolicy.resolve(b, fitB, now)

        val rank = SourceRecommendationFitDisplayPolicy.compatibilityRank(stateA)
            .compareTo(SourceRecommendationFitDisplayPolicy.compatibilityRank(stateB))
        if (rank != 0) return@Comparator rank

        // Same bucket: current fit score (desc) where a current fit exists, then newest, then name.
        if (stateA.isCurrentOutcome && stateB.isCurrentOutcome) {
            val score = (fitB?.recommendationQualityScore ?: 0.0).compareTo(fitA?.recommendationQualityScore ?: 0.0)
            if (score != 0) return@Comparator score
        }

        val time = b.evaluatedAt.compareTo(a.evaluatedAt)
        if (time != 0) return@Comparator time

        val src = String.CASE_INSENSITIVE_ORDER.compare(a.sourceName, b.sourceName)
        if (src != 0) return@Comparator src

        String.CASE_INSENSITIVE_ORDER.compare(a.extensionName, b.extensionName)
    }
    // KMK <--

    private val explicitRiskComparator: Comparator<SourceEvaluation> = Comparator { a, b ->
        val exp = b.explicitScore.compareTo(a.explicitScore)
        if (exp != 0) return@Comparator exp

        val ecchi = b.ecchiScore.compareTo(a.ecchiScore)
        if (ecchi != 0) return@Comparator ecchi

        String.CASE_INSENSITIVE_ORDER.compare(a.sourceName, b.sourceName)
    }

    /**
     * Verdict rank for BEST_FIT sort: lower = shown first.
     * Strong fit at top, errors at bottom.
     */
    internal fun verdictRank(verdict: SourceEvaluationVerdict): Int = when (verdict) {
        SourceEvaluationVerdict.STRONG_FIT -> 0
        SourceEvaluationVerdict.WORTH_TRYING -> 1
        SourceEvaluationVerdict.NEUTRAL -> 2
        SourceEvaluationVerdict.NEEDS_MANUAL_REVIEW -> 3
        SourceEvaluationVerdict.WEAK -> 4
        SourceEvaluationVerdict.POOR_SEARCH -> 5
        SourceEvaluationVerdict.ECCHI_HEAVY -> 6
        SourceEvaluationVerdict.EXPLICIT_HEAVY -> 7
        SourceEvaluationVerdict.REJECTED -> 8
        SourceEvaluationVerdict.ERROR -> 9
    }

    // KMK --> v0.7.47: stale (outdated-version/expired) rows are ranked strictly below every current
    // row, regardless of their own stored verdict — an old STRONG_FIT must never outrank a current
    // WORTH_TRYING. See SourceEvaluationDisplayPolicy.isStaleForRanking.
    private fun effectiveVerdictRank(evaluation: SourceEvaluation, now: Long): Int {
        val base = verdictRank(evaluation.verdict)
        return if (SourceEvaluationDisplayPolicy.isStaleForRanking(evaluation, now)) base + 100 else base
    }
    // KMK <--
}
// KMK <--

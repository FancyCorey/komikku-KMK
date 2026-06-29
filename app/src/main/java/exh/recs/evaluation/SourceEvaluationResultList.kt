package exh.recs.evaluation

import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationVerdict

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
        SEARCH_RELIABILITY,
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
     */
    fun sort(
        evaluations: List<SourceEvaluation>,
        mode: SortMode,
    ): List<SourceEvaluation> = when (mode) {
        SortMode.BEST_FIT -> evaluations.sortedWith(bestFitComparator)
        SortMode.NEWEST -> evaluations.sortedByDescending { it.evaluatedAt }
        SortMode.SOURCE_NAME -> evaluations.sortedWith(sourceNameComparator)
        SortMode.EXTENSION_NAME -> evaluations.sortedWith(extensionNameComparator)
        SortMode.SEARCH_RELIABILITY -> evaluations.sortedWith(searchReliabilityComparator)
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

    private val bestFitComparator: Comparator<SourceEvaluation> = Comparator { a, b ->
        val verdict = verdictRank(a.verdict).compareTo(verdictRank(b.verdict))
        if (verdict != 0) return@Comparator verdict

        val fit = b.recommendationFitScore.compareTo(a.recommendationFitScore)
        if (fit != 0) return@Comparator fit

        val qual = b.qualityScore.compareTo(a.qualityScore)
        if (qual != 0) return@Comparator qual

        val search = b.searchReliabilityScore.compareTo(a.searchReliabilityScore)
        if (search != 0) return@Comparator search

        val time = b.evaluatedAt.compareTo(a.evaluatedAt)
        if (time != 0) return@Comparator time

        val src = String.CASE_INSENSITIVE_ORDER.compare(a.sourceName, b.sourceName)
        if (src != 0) return@Comparator src

        String.CASE_INSENSITIVE_ORDER.compare(a.extensionName, b.extensionName)
    }

    private val sourceNameComparator: Comparator<SourceEvaluation> = Comparator { a, b ->
        val src = String.CASE_INSENSITIVE_ORDER.compare(a.sourceName, b.sourceName)
        if (src != 0) src else String.CASE_INSENSITIVE_ORDER.compare(a.extensionName, b.extensionName)
    }

    private val extensionNameComparator: Comparator<SourceEvaluation> = Comparator { a, b ->
        val ext = String.CASE_INSENSITIVE_ORDER.compare(a.extensionName, b.extensionName)
        if (ext != 0) ext else String.CASE_INSENSITIVE_ORDER.compare(a.sourceName, b.sourceName)
    }

    private val searchReliabilityComparator: Comparator<SourceEvaluation> = Comparator { a, b ->
        val rel = b.searchReliabilityScore.compareTo(a.searchReliabilityScore)
        if (rel != 0) return@Comparator rel

        val fit = b.recommendationFitScore.compareTo(a.recommendationFitScore)
        if (fit != 0) return@Comparator fit

        String.CASE_INSENSITIVE_ORDER.compare(a.sourceName, b.sourceName)
    }

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
}
// KMK <--

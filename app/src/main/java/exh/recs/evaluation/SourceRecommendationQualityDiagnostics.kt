package exh.recs.evaluation

import tachiyomi.domain.taste.model.RecommendationQualityVerdict
import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationVerdict
import tachiyomi.domain.taste.model.SourceRecommendationFit

// KMK -->

/**
 * Pure helper that computes a compact diagnostics summary for the Recommendation Quality section.
 *
 * The summary helps the user understand whether "everything is broken" or whether sources simply
 * lack useful recommendation/search behavior. It distinguishes:
 * - Install/load failures (extension not found, install failed, source not resolved)
 * - Search errors (search endpoint threw or timed out)
 * - No results (search succeeded but returned no candidates)
 * - Weak (search returned results but scoring was low, often due to missing metadata)
 * - Good recommenders (GREAT, GOOD, or MIXED)
 * - Not yet checked
 */
object SourceRecommendationQualityDiagnostics {

    data class Summary(
        val checkedCount: Int,
        val notCheckedCount: Int,
        val installLoadIssueCount: Int,
        val searchErrorCount: Int,
        val noResultsCount: Int,
        val weakCount: Int,
        val goodCount: Int,
    ) {
        val totalPromisingCount: Int get() = checkedCount + notCheckedCount
        val hasAnyResults: Boolean get() = checkedCount > 0
    }

    fun compute(
        evaluations: List<SourceEvaluation>,
        fitsByEvalKey: Map<String, SourceRecommendationFit>,
    ): Summary {
        val promising = evaluations.filter {
            it.verdict == SourceEvaluationVerdict.STRONG_FIT ||
                it.verdict == SourceEvaluationVerdict.WORTH_TRYING
        }
        val checkedCount = promising.count { fitsByEvalKey.containsKey(it.evaluationKey) }
        val notCheckedCount = promising.size - checkedCount

        var installLoadIssue = 0
        var searchError = 0
        var noResults = 0
        var weak = 0
        var good = 0

        for (eval in promising) {
            val fit = fitsByEvalKey[eval.evaluationKey] ?: continue
            when (fit.verdict) {
                RecommendationQualityVerdict.ERROR -> {
                    val kind = SourceRecommendationFitFailureClassifier.classify(fit.errorMessage)
                    if (SourceRecommendationFitFailureClassifier.isInstallOrLoadIssue(kind)) {
                        installLoadIssue++
                    } else {
                        searchError++
                    }
                }
                RecommendationQualityVerdict.NO_MATCHES,
                RecommendationQualityVerdict.TOO_LITTLE_EVIDENCE,
                -> noResults++
                RecommendationQualityVerdict.WEAK -> weak++
                RecommendationQualityVerdict.MIXED,
                RecommendationQualityVerdict.GOOD,
                RecommendationQualityVerdict.GREAT,
                -> good++
            }
        }

        return Summary(checkedCount, notCheckedCount, installLoadIssue, searchError, noResults, weak, good)
    }
}
// KMK <--

package exh.recs.evaluation

import tachiyomi.domain.taste.model.RecommendationQualityVerdict
import tachiyomi.domain.taste.model.SourceEvaluation
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
 *
 * KMK --> v0.7.42-fix1: eligibility and staleness now come from [SourceRecommendationFitEligibility]
 * — the same shared contract [SourceRecommendationQualityQueue] uses — instead of a separately
 * hardcoded STRONG_FIT/WORTH_TRYING filter, so this summary can never disagree with the queue or the
 * automatic evaluation path. A stale (older-version or expired) fit no longer contributes to the
 * Good/Weak/Error/No-results buckets; it counts toward [Summary.notCheckedCount] instead.
 * KMK <--
 *
 * KMK --> v0.7.42-fix2: routed through [SourceRecommendationFitDisplayPolicy.resolve] (the same
 * classification the queue, row labels, and sort use) instead of calling
 * [SourceRecommendationFitEligibility] directly, so every consumer shares one call path.
 * [Summary.notCheckedCount] intentionally still covers both NOT_CHECKED and OUTDATED — this summary
 * does not need a separate outdated count; the queue exposes that split for the action UI.
 * KMK <--
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
        // KMK --> v0.7.42-fix1: injectable for stale-fit tests
        now: Long = System.currentTimeMillis(),
        // KMK <--
    ): Summary {
        val eligible = evaluations.filter { SourceRecommendationFitEligibility.isProbeEligible(it) }

        var installLoadIssue = 0
        var searchError = 0
        var noResults = 0
        var weak = 0
        var good = 0
        var checkedCount = 0

        for (eval in eligible) {
            val fit = fitsByEvalKey[eval.evaluationKey]
            when (SourceRecommendationFitDisplayPolicy.resolve(eval, fit, now)) {
                CompatibilityDisplayState.NOT_CHECKED, CompatibilityDisplayState.OUTDATED -> Unit
                CompatibilityDisplayState.ERROR -> {
                    checkedCount++
                    val kind = SourceRecommendationFitFailureClassifier.classify(fit?.errorMessage)
                    if (SourceRecommendationFitFailureClassifier.isInstallOrLoadIssue(kind)) {
                        installLoadIssue++
                    } else {
                        searchError++
                    }
                }
                CompatibilityDisplayState.NO_MATCHES -> {
                    checkedCount++
                    noResults++
                }
                CompatibilityDisplayState.WEAK -> {
                    checkedCount++
                    weak++
                }
                CompatibilityDisplayState.GREAT, CompatibilityDisplayState.GOOD, CompatibilityDisplayState.MIXED -> {
                    checkedCount++
                    good++
                }
                CompatibilityDisplayState.INELIGIBLE -> Unit // unreachable: eligible list already excludes this
            }
        }
        val notCheckedCount = eligible.size - checkedCount

        return Summary(checkedCount, notCheckedCount, installLoadIssue, searchError, noResults, weak, good)
    }
}
// KMK <--

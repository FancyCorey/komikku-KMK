package exh.recs.evaluation

import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationVerdict

// KMK -->
/**
 * Pure stateless gate: checks whether a SourceEvaluation record is eligible for
 * a bounded recommendation-fit probe run.
 *
 * Only STRONG_FIT and WORTH_TRYING verdicts are eligible — probing rejected, error, or
 * weak sources would be wasteful and misleading. Evidence must also meet a minimum
 * sample count so the probe has meaningful source output to reason about.
 */
object SourceRecommendationFitEligibility {

    enum class EligibilityResult {
        /** Source is a strong or worth-trying candidate — run the probe. */
        ELIGIBLE,
        /** Verdict is not in the approved set (REJECTED, ERROR, WEAK, etc.) — skip. */
        INELIGIBLE_VERDICT,
        /** Verdict is approved but sample count is below the minimum — skip. */
        INSUFFICIENT_EVIDENCE,
    }

    private val ELIGIBLE_VERDICTS = setOf(
        SourceEvaluationVerdict.STRONG_FIT,
        SourceEvaluationVerdict.WORTH_TRYING,
    )

    /** Minimum number of sampled titles required before running a rec-fit probe. */
    const val MIN_SAMPLE_COUNT = 3

    fun check(evaluation: SourceEvaluation): EligibilityResult = when {
        evaluation.verdict !in ELIGIBLE_VERDICTS -> EligibilityResult.INELIGIBLE_VERDICT
        evaluation.sampleCount < MIN_SAMPLE_COUNT -> EligibilityResult.INSUFFICIENT_EVIDENCE
        else -> EligibilityResult.ELIGIBLE
    }
}
// KMK <--

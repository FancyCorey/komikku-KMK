package exh.recs.evaluation

import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationKeys
import tachiyomi.domain.taste.model.SourceEvaluationMetadataConfidence
import tachiyomi.domain.taste.model.SourceEvaluationVerdict
import tachiyomi.domain.taste.model.SourceRecommendationFit

// KMK -->
/**
 * Pure stateless gate: checks whether a SourceEvaluation record is eligible for
 * a bounded recommendation-fit probe run (measuring For You search compatibility).
 *
 * As of v0.7.42 (decision D1), the catalogue-fit verdict (STRONG_FIT/WORTH_TRYING) is no longer
 * the sole admission path. A source whose catalogue (Popular/Latest) samples had low or unknown
 * metadata confidence is also eligible — catalogue metadata is frequently sparse (the same issue
 * v0.7.13 fixed on the search side), so a WEAK catalogue verdict driven by missing tags must not
 * silently exclude a source that could still be a good search-compatibility fit. Explicit/ecchi/
 * error/rejected verdicts are excluded unconditionally regardless of confidence — those are safety
 * or infrastructure exclusions unrelated to metadata sparsity.
 */
object SourceRecommendationFitEligibility {

    enum class EligibilityResult {
        /** Source is a strong/worth-trying candidate, or catalogue evidence was inconclusive — run the probe. */
        ELIGIBLE,
        /** Verdict is explicit/ecchi/error/rejected, or clearly (high/moderate-confidence) not a fit — skip. */
        INELIGIBLE_VERDICT,
        /** Admission path was open but sample count is below the minimum — skip. */
        INSUFFICIENT_EVIDENCE,
        // KMK --> v0.7.47
        /**
         * Row was scored under an older [SourceEvaluationKeys.CURRENT_VERSION] — its verdict and
         * confidence are not trustworthy current evidence. Must be reassessed before it can feed the
         * search-compatibility queue, unless the caller is an explicit "check compatibility anyway"
         * user action.
         */
        STALE_EVALUATION,
        // KMK <--
    }

    private val STRONG_VERDICTS = setOf(
        SourceEvaluationVerdict.STRONG_FIT,
        SourceEvaluationVerdict.WORTH_TRYING,
    )

    /** Verdicts excluded unconditionally, regardless of catalogue metadata confidence. */
    private val BLOCKED_VERDICTS = setOf(
        SourceEvaluationVerdict.EXPLICIT_HEAVY,
        SourceEvaluationVerdict.ECCHI_HEAVY,
        SourceEvaluationVerdict.ERROR,
        SourceEvaluationVerdict.REJECTED,
    )

    /** Catalogue evidence too sparse to trust the verdict on its own — fail open toward probing. */
    private val INCONCLUSIVE_CONFIDENCE = setOf(
        SourceEvaluationMetadataConfidence.LOW,
        SourceEvaluationMetadataConfidence.UNKNOWN,
    )

    /** Minimum number of catalogue samples required before running a rec-fit probe. */
    const val MIN_SAMPLE_COUNT = 3

    /** Minimum catalogue samples required for the confidence-based fail-open admission path. */
    const val MIN_SAMPLE_COUNT_FAIL_OPEN = 1

    fun check(evaluation: SourceEvaluation): EligibilityResult = when {
        // KMK --> v0.7.47: a stale (outdated-version) row's verdict/confidence were computed under
        // different scoring rules and must not be treated as eligible current evidence.
        evaluation.evaluationVersion < SourceEvaluationKeys.CURRENT_VERSION -> EligibilityResult.STALE_EVALUATION
        // KMK <--
        evaluation.verdict in BLOCKED_VERDICTS -> EligibilityResult.INELIGIBLE_VERDICT
        evaluation.verdict in STRONG_VERDICTS ->
            if (evaluation.sampleCount >= MIN_SAMPLE_COUNT) EligibilityResult.ELIGIBLE else EligibilityResult.INSUFFICIENT_EVIDENCE
        evaluation.catalogueMetadataConfidence in INCONCLUSIVE_CONFIDENCE ->
            if (evaluation.sampleCount >= MIN_SAMPLE_COUNT_FAIL_OPEN) EligibilityResult.ELIGIBLE else EligibilityResult.INSUFFICIENT_EVIDENCE
        else -> EligibilityResult.INELIGIBLE_VERDICT
    }

    // KMK --> v0.7.42-fix1: shared contract so every manual/diagnostic consumer of eligibility and
    // fit-staleness agrees with the automatic v0.7.42 evaluation path. Do not duplicate this logic
    // elsewhere — SourceRecommendationQualityQueue and SourceRecommendationQualityDiagnostics both
    // call these two functions instead of re-deriving their own "promising" or "checked" rules.
    /** Convenience: true only when [check] returns [EligibilityResult.ELIGIBLE]. */
    fun isProbeEligible(evaluation: SourceEvaluation): Boolean =
        check(evaluation) == EligibilityResult.ELIGIBLE

    /**
     * True when [fit] exists and was computed under the current fit-scoring version and has not
     * expired. A missing, older-version, or expired fit must be treated as needing re-evaluation —
     * never silently shown as "checked" — since scoring semantics can change between versions.
     */
    fun isFitCurrent(fit: SourceRecommendationFit?, now: Long = System.currentTimeMillis()): Boolean {
        if (fit == null || fit.evaluationVersion < SourceRecommendationFit.CURRENT_VERSION) return false
        val expiresAt = fit.expiresAt
        return expiresAt == null || expiresAt > now
    }
    // KMK <--
}
// KMK <--

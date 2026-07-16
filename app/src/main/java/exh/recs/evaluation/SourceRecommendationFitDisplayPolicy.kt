package exh.recs.evaluation

import tachiyomi.domain.taste.model.RecommendationQualityVerdict
import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceRecommendationFit

// KMK --> v0.7.42-fix2
/**
 * One truthful display state for a source's For You search-compatibility measurement, resolved
 * from the two existing evidence layers ([SourceEvaluation] catalogue fit + [SourceRecommendationFit]
 * search-compatibility probe result) via [SourceRecommendationFitEligibility]. No third score is
 * introduced — this only classifies what is already stored.
 */
enum class CompatibilityDisplayState {
    /** Not eligible for a compatibility probe — never show or offer a compatibility action/label. */
    INELIGIBLE,
    /** Eligible, but no [SourceRecommendationFit] has ever been recorded. */
    NOT_CHECKED,
    /** Eligible, a fit exists, but it is older-version or expired — must not be shown as current. */
    OUTDATED,
    GREAT,
    GOOD,
    MIXED,
    WEAK,
    /** Current fit verdict is NO_MATCHES or TOO_LITTLE_EVIDENCE — probe ran but found nothing useful. */
    NO_MATCHES,
    /** Current fit verdict is ERROR — probe ran but failed. A stale error is OUTDATED, not this. */
    ERROR,
    ;

    /** True for any state backed by a current fit result (GREAT/GOOD/MIXED/WEAK/NO_MATCHES/ERROR). */
    val isCurrentOutcome: Boolean
        get() = this != INELIGIBLE && this != NOT_CHECKED && this != OUTDATED
}

/**
 * Pure, Android-free, stateless policy that resolves a source's [CompatibilityDisplayState] and its
 * canonical display/sort rank.
 *
 * This is the single source of truth for eligibility + staleness display. It reuses
 * [SourceRecommendationFitEligibility.isProbeEligible] and
 * [SourceRecommendationFitEligibility.isFitCurrent] rather than re-deriving verdict, confidence,
 * version, or expiry logic — [SourceRecommendationQualityQueue], [SourceRecommendationQualityDiagnostics],
 * row labels (`SourceEvaluationScreen`), and [SourceEvaluationResultList]'s For You compatibility sort
 * must all resolve state through this object so they can never disagree.
 */
object SourceRecommendationFitDisplayPolicy {

    /**
     * Resolves the truthful display state for [evaluation] given its (possibly absent or stale)
     * [fit]. Never accesses DB, Compose, Android, or network APIs.
     */
    fun resolve(
        evaluation: SourceEvaluation,
        fit: SourceRecommendationFit?,
        now: Long = System.currentTimeMillis(),
    ): CompatibilityDisplayState {
        if (!SourceRecommendationFitEligibility.isProbeEligible(evaluation)) return CompatibilityDisplayState.INELIGIBLE
        if (fit == null) return CompatibilityDisplayState.NOT_CHECKED
        if (!SourceRecommendationFitEligibility.isFitCurrent(fit, now)) return CompatibilityDisplayState.OUTDATED
        return when (fit.verdict) {
            RecommendationQualityVerdict.GREAT -> CompatibilityDisplayState.GREAT
            RecommendationQualityVerdict.GOOD -> CompatibilityDisplayState.GOOD
            RecommendationQualityVerdict.MIXED -> CompatibilityDisplayState.MIXED
            RecommendationQualityVerdict.WEAK -> CompatibilityDisplayState.WEAK
            RecommendationQualityVerdict.NO_MATCHES,
            RecommendationQualityVerdict.TOO_LITTLE_EVIDENCE,
            -> CompatibilityDisplayState.NO_MATCHES
            RecommendationQualityVerdict.ERROR -> CompatibilityDisplayState.ERROR
        }
    }

    /**
     * Canonical For You compatibility sort-group rank: lower sorts first. Ties within the same rank
     * (e.g. GREAT/GOOD/MIXED, or two OUTDATED rows) are broken by the caller using
     * [SourceRecommendationFit.recommendationQualityScore], evaluation recency, and name — this
     * function only encodes the seven-bucket ordering the plan defines, not those tie-breakers.
     */
    fun compatibilityRank(state: CompatibilityDisplayState): Int = when (state) {
        CompatibilityDisplayState.GREAT,
        CompatibilityDisplayState.GOOD,
        CompatibilityDisplayState.MIXED,
        -> 0
        CompatibilityDisplayState.WEAK -> 1
        CompatibilityDisplayState.NO_MATCHES -> 2
        CompatibilityDisplayState.ERROR -> 3
        CompatibilityDisplayState.OUTDATED -> 4
        CompatibilityDisplayState.NOT_CHECKED -> 5
        CompatibilityDisplayState.INELIGIBLE -> 6
    }
}
// KMK <--

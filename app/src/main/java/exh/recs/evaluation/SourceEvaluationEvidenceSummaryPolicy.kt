package exh.recs.evaluation

import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationVerdict

// KMK --> v0.8.1-fix1
/**
 * Raw evidence counters for the Source Evaluation row's expandable "Details" section — the v0.7.47
 * enrichment/split-evidence fields, computed but barely surfaced until this pass. No Android/DB
 * dependencies; formatting into KMR strings happens in the composable layer.
 */
internal data class SourceEvaluationEvidenceCounts(
    val detailEnrichmentAttemptCount: Int,
    val detailEnrichmentSuccessCount: Int,
    val metadataCandidateCount: Int,
    val sampleCount: Int,
    val positiveCandidateCount: Int,
    val negativeCandidateCount: Int,
    val blockedCandidateCount: Int,
    val adultSignalCandidateCount: Int,
    val isManualReview: Boolean,
) {
    val detailEnrichmentFailedCount: Int get() = (detailEnrichmentAttemptCount - detailEnrichmentSuccessCount).coerceAtLeast(0)
}

/**
 * Decides whether an evidence-details toggle should be offered for a row at all. Pure — no Compose
 * dependency — so it's directly unit-testable.
 */
internal object SourceEvaluationEvidenceSummaryPolicy {

    /**
     * Returns the evidence counts to show, or `null` if the row should not offer a details toggle:
     * - stale rows (OUTDATED_VERSION/EXPIRED) — their counters were computed under different scoring
     *   rules and must not be presented as current evidence (the row already shows "Outdated —
     *   reassess needed" instead);
     * - ERROR rows — no catalogue samples were collected, so there is no evidence to show;
     * - zero-sample rows — nothing to summarize.
     */
    fun evidenceFor(
        evaluation: SourceEvaluation,
        displayState: SourceEvaluationDisplayPolicy.SourceEvaluationDisplayState,
    ): SourceEvaluationEvidenceCounts? {
        if (displayState == SourceEvaluationDisplayPolicy.SourceEvaluationDisplayState.OUTDATED_VERSION ||
            displayState == SourceEvaluationDisplayPolicy.SourceEvaluationDisplayState.EXPIRED
        ) {
            return null
        }
        if (evaluation.verdict == SourceEvaluationVerdict.ERROR) return null
        if (evaluation.sampleCount <= 0) return null

        return SourceEvaluationEvidenceCounts(
            detailEnrichmentAttemptCount = evaluation.detailEnrichmentAttemptCount,
            detailEnrichmentSuccessCount = evaluation.detailEnrichmentSuccessCount,
            metadataCandidateCount = evaluation.metadataCandidateCount,
            sampleCount = evaluation.sampleCount,
            positiveCandidateCount = evaluation.positiveCandidateCount,
            negativeCandidateCount = evaluation.negativeCandidateCount,
            blockedCandidateCount = evaluation.blockedCandidateCount,
            adultSignalCandidateCount = evaluation.adultSignalCandidateCount,
            isManualReview = evaluation.verdict == SourceEvaluationVerdict.NEEDS_MANUAL_REVIEW,
        )
    }
}
// KMK <--

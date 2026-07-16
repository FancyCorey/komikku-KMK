package exh.recs.evaluation

import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceRecommendationFit

// KMK -->
/**
 * Pure stateless helper that partitions source evaluations into recommendation-quality
 * queue buckets:
 *
 * - [QueueResult.missingPromising]: eligible sources with **no** [SourceRecommendationFit] at all —
 *   candidates for a normal check.
 * - [QueueResult.outdatedPromising]: eligible sources with a fit that exists but is stale
 *   (older-version or expired) — candidates for a targeted recheck, distinct from missing.
 * - [QueueResult.checkedPromising]: eligible sources with a current fit.
 * - [QueueResult.ineligible]: sources [SourceRecommendationFitEligibility.check] excludes — this
 *   includes EXPLICIT_HEAVY/ECCHI_HEAVY/ERROR/REJECTED verdicts and sources with insufficient
 *   evidence, not only WEAK/NEUTRAL catalogue verdicts.
 *
 * The four buckets are mutually exclusive and collectively cover every input evaluation.
 *
 * KMK --> v0.7.42-fix1: "promising" is a legacy name kept for compatibility with existing callers.
 * As of v0.7.42 it means "eligible for a For You search-compatibility probe under
 * [SourceRecommendationFitEligibility]" — this includes WEAK/NEUTRAL catalogue verdicts whose
 * catalogue metadata confidence is LOW/UNKNOWN (inconclusive evidence), not only
 * STRONG_FIT/WORTH_TRYING. Both this queue and [SourceRecommendationQualityDiagnostics] delegate to
 * the same [SourceRecommendationFitEligibility] functions so they never disagree.
 * KMK <--
 *
 * KMK --> v0.7.42-fix2: split the old two-way missing/checked split into three: missing (no fit),
 * outdated (stale fit), checked (current fit) — via [SourceRecommendationFitDisplayPolicy] so the
 * queue, diagnostics, row labels, and sort agree on exactly the same classification.
 * KMK <--
 *
 * No DB access is performed. Inputs come from ScreenModel state.
 */
object SourceRecommendationQualityQueue {

    data class QueueResult(
        val missingPromising: List<SourceEvaluation>,
        val outdatedPromising: List<SourceEvaluation>,
        val checkedPromising: List<SourceEvaluation>,
        val ineligible: List<SourceEvaluation>,
    ) {
        val missingCount: Int get() = missingPromising.size
        val outdatedCount: Int get() = outdatedPromising.size
        /** Missing or outdated — either needs a check/recheck to have a current result. */
        val needsCheckPromising: List<SourceEvaluation> get() = missingPromising + outdatedPromising
        val totalPromising: Int get() = missingPromising.size + outdatedPromising.size + checkedPromising.size
    }

    fun compute(
        evaluations: List<SourceEvaluation>,
        fitsByEvalKey: Map<String, SourceRecommendationFit>,
        // KMK --> v0.7.42-fix1: injectable for stale-fit tests
        now: Long = System.currentTimeMillis(),
        // KMK <--
    ): QueueResult {
        val (eligible, ineligible) = evaluations.partition { SourceRecommendationFitEligibility.isProbeEligible(it) }
        val missing = mutableListOf<SourceEvaluation>()
        val outdated = mutableListOf<SourceEvaluation>()
        val checked = mutableListOf<SourceEvaluation>()
        for (evaluation in eligible) {
            when (SourceRecommendationFitDisplayPolicy.resolve(evaluation, fitsByEvalKey[evaluation.evaluationKey], now)) {
                CompatibilityDisplayState.NOT_CHECKED -> missing += evaluation
                CompatibilityDisplayState.OUTDATED -> outdated += evaluation
                else -> checked += evaluation
            }
        }
        return QueueResult(
            missingPromising = missing,
            outdatedPromising = outdated,
            checkedPromising = checked,
            ineligible = ineligible,
        )
    }
}
// KMK <--

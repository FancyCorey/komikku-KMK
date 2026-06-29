package exh.recs.evaluation

import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationVerdict
import tachiyomi.domain.taste.model.SourceRecommendationFit

// KMK -->
/**
 * Pure stateless helper that partitions source evaluations into recommendation-quality
 * queue buckets:
 *
 * - [QueueResult.missingPromising]: promising sources (STRONG_FIT/WORTH_TRYING) without a
 *   rec-quality result yet — candidates for the next evaluation.
 * - [QueueResult.checkedPromising]: promising sources that already have a rec-quality result.
 * - [QueueResult.ineligible]: non-promising sources (WEAK, REJECTED, ERROR, etc.).
 *
 * No DB access is performed. Inputs come from ScreenModel state.
 */
object SourceRecommendationQualityQueue {

    data class QueueResult(
        val missingPromising: List<SourceEvaluation>,
        val checkedPromising: List<SourceEvaluation>,
        val ineligible: List<SourceEvaluation>,
    ) {
        val missingCount: Int get() = missingPromising.size
        val totalPromising: Int get() = missingPromising.size + checkedPromising.size
    }

    private val PROMISING_VERDICTS = setOf(
        SourceEvaluationVerdict.STRONG_FIT,
        SourceEvaluationVerdict.WORTH_TRYING,
    )

    fun compute(
        evaluations: List<SourceEvaluation>,
        fitsByEvalKey: Map<String, SourceRecommendationFit>,
    ): QueueResult {
        val promising = evaluations.filter { it.verdict in PROMISING_VERDICTS }
        val ineligible = evaluations.filter { it.verdict !in PROMISING_VERDICTS }
        val missing = promising.filter { fitsByEvalKey[it.evaluationKey] == null }
        val checked = promising.filter { fitsByEvalKey[it.evaluationKey] != null }
        return QueueResult(
            missingPromising = missing,
            checkedPromising = checked,
            ineligible = ineligible,
        )
    }
}
// KMK <--

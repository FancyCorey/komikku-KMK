package exh.recs.evaluation

import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationKeys
import tachiyomi.domain.taste.model.SourceEvaluationMetadataConfidence

// KMK --> v0.7.47
/**
 * Pure, Android-free policy for how a [SourceEvaluation] row should be treated for display and
 * sorting: as current trustworthy evidence, or as stale/inconclusive evidence that must not be
 * shown or sorted as if it were a confident current verdict.
 *
 * See `docs/recommendations/KMK_SOURCE_EVALUATION_TAG_ENRICHMENT_AND_SCORING_FIX_PLAN.md`
 * ("Stale Row Handling").
 */
internal object SourceEvaluationDisplayPolicy {

    enum class SourceEvaluationDisplayState {
        /** Current scoring version, not expired, and metadata confidence is usable. */
        CURRENT,
        /** Row was computed under an older [SourceEvaluationKeys.CURRENT_VERSION] — reassess needed. */
        OUTDATED_VERSION,
        /** Row has an explicit expiry timestamp that has passed. */
        EXPIRED,
        /** Current row, but catalogue metadata confidence is LOW/UNKNOWN with samples present. */
        METADATA_SPARSE,
        /** Row's own verdict is ERROR. */
        ERROR,
    }

    /** True only when the row is current scoring version, not expired, and not an error record. */
    fun isCurrent(evaluation: SourceEvaluation, now: Long = System.currentTimeMillis()): Boolean =
        state(evaluation, now) == SourceEvaluationDisplayState.CURRENT

    fun state(evaluation: SourceEvaluation, now: Long = System.currentTimeMillis()): SourceEvaluationDisplayState {
        val expiresAt = evaluation.expiresAt
        return when {
            evaluation.evaluationVersion < SourceEvaluationKeys.CURRENT_VERSION ->
                SourceEvaluationDisplayState.OUTDATED_VERSION
            expiresAt != null && expiresAt <= now -> SourceEvaluationDisplayState.EXPIRED
            evaluation.verdict == tachiyomi.domain.taste.model.SourceEvaluationVerdict.ERROR ->
                SourceEvaluationDisplayState.ERROR
            evaluation.sampleCount > 0 &&
                evaluation.catalogueMetadataConfidence in setOf(
                    SourceEvaluationMetadataConfidence.LOW,
                    SourceEvaluationMetadataConfidence.UNKNOWN,
                ) -> SourceEvaluationDisplayState.METADATA_SPARSE
            else -> SourceEvaluationDisplayState.CURRENT
        }
    }

    /** True when the row must not be sorted/displayed as a current strong/worth-trying verdict. */
    fun isStaleForRanking(evaluation: SourceEvaluation, now: Long = System.currentTimeMillis()): Boolean =
        when (state(evaluation, now)) {
            SourceEvaluationDisplayState.OUTDATED_VERSION, SourceEvaluationDisplayState.EXPIRED -> true
            else -> false
        }
}
// KMK <--

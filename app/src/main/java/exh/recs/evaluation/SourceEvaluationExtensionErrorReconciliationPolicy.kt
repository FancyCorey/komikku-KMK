package exh.recs.evaluation

// KMK v0.8.16 -->
/**
 * Pure decision for [SourceEvaluationRunner.recordExtensionError]'s delete-before-upsert step
 * (v0.8.15/v0.8.15-fix1): a package/signature's stale per-source rows must be deleted before the
 * one current extension-level error row is upserted, so old stale rows can never survive beside it.
 *
 * If that delete fails, the candidate must NOT be treated as durably handled -- it must not advance
 * the stale cursor or satisfy stale-reassessment completion, and the run must surface an honest,
 * non-fatal count instead of a silent retry-forever loop. Extracted as its own pure object (rather
 * than only inline `if`/`else` in the suspend function) because [SourceEvaluationRunner] has no
 * fake/mock harness for [tachiyomi.domain.taste.interactor.DeleteSourceEvaluation]/
 * [tachiyomi.domain.taste.interactor.UpsertSourceEvaluation] in this test suite (consistent with the
 * v0.8.15-fix1 precedent) -- this policy is what stays directly, cheaply testable even without one.
 */
object SourceEvaluationExtensionErrorReconciliationPolicy {

    data class Outcome(
        val countsAsDurablyHandled: Boolean,
        val reconciliationFailedCountDelta: Int,
    )

    fun resolve(deleteSucceeded: Boolean): Outcome =
        if (deleteSucceeded) {
            Outcome(countsAsDurablyHandled = true, reconciliationFailedCountDelta = 0)
        } else {
            Outcome(countsAsDurablyHandled = false, reconciliationFailedCountDelta = 1)
        }
}
// KMK <--

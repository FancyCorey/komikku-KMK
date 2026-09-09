package exh.recs.evaluation

/**
 * Pure gate for the visibility-only Action History receipt emitted by destructive Source
 * Evaluation management actions. The deleted diagnostic/quarantine state has no safe generic
 * inverse, so this is deliberately a non-undoable event rather than a fabricated restore entry.
 */
internal object SourceEvaluationHistoryPolicy {
    fun shouldRecordDataClearedEvent(
        operationSucceeded: Boolean,
    ): Boolean = operationSucceeded
}

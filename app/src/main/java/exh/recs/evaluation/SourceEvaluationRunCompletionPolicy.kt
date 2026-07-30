package exh.recs.evaluation

// KMK v0.8.15 -->
/**
 * Pure end-of-run status decision for [SourceEvaluationRunner].
 *
 * Root-cause fix for the live-device "Reassess outdated (25)" / "Evaluation completed" / no DB change
 * bug: the runner used to mark a batch `Completed` whenever it reached the end of its candidate loop,
 * regardless of whether any candidate actually produced a durable database write. Combined with two
 * other bugs (a fire-and-forget error-record write, and an extension-level error key that could not
 * replace stale per-source rows -- both fixed directly in [SourceEvaluationRunner]), a stale
 * reassessment batch could report success after doing nothing durable at all.
 *
 * [resolveStatus] is the extracted decision so it's directly unit-testable without mocking the
 * runner's Android/Injekt dependencies (extension manager, source runtime, work manager).
 */
object SourceEvaluationRunCompletionPolicy {
    /**
     * @param candidatesCount total candidates this batch was handed.
     * @param durablyHandledCount candidates that durably wrote a source_evaluation row before the
     * batch reached its end (see [SourceEvaluationRunner.completedCandidateKeys]).
     */
    fun resolveStatus(candidatesCount: Int, durablyHandledCount: Int): SourceEvaluationQueueState.Status =
        if (candidatesCount > 0 && durablyHandledCount == 0) {
            SourceEvaluationQueueState.Status.NoActionableWork
        } else {
            SourceEvaluationQueueState.Status.Completed
        }
}
// KMK <--

package exh.recs.evaluation

// KMK v0.8.10 -->
/**
 * Pure decision for whether a finished [SourceEvaluationQueueState] should be auto-cleared when the
 * user leaves the Source Evaluation screen.
 *
 * [SourceEvaluationQueueState] is held in the process-scoped [SourceEvaluationJobState] singleton so
 * a running evaluation survives navigating away and back (see [SourceEvaluationJobState]'s doc). That
 * is correct and must not change. The bug this policy fixes is narrower: once the run reaches a
 * terminal status (Completed/Cancelled/Failed/ConnectivityLost), that same persistence means the
 * "Evaluation completed" summary card keeps showing on every future visit to the screen -- for the
 * rest of the process lifetime, or until the user notices and manually taps Reset -- which reads as
 * stale/wrong the next time they open the screen for an unrelated reason.
 *
 * The fix: clear only a *terminal* state when the screen is left (Composable dispose), never a
 * Running/Cancelling one -- an in-progress background job must keep reporting progress exactly as
 * before across navigation.
 */
object SourceEvaluationCompletionLifecyclePolicy {
    /** True when [status] is a finished state whose summary card should not survive leaving the screen. */
    fun shouldClearOnLeave(status: SourceEvaluationQueueState.Status): Boolean = when (status) {
        SourceEvaluationQueueState.Status.Completed,
        SourceEvaluationQueueState.Status.Cancelled,
        SourceEvaluationQueueState.Status.Failed,
        SourceEvaluationQueueState.Status.ConnectivityLost,
        -> true
        SourceEvaluationQueueState.Status.Idle,
        SourceEvaluationQueueState.Status.Running,
        SourceEvaluationQueueState.Status.Cancelling,
        -> false
    }
}
// KMK <--

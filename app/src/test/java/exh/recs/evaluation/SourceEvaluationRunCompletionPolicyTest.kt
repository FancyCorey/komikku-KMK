package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// KMK v0.8.15 -->
class SourceEvaluationRunCompletionPolicyTest {

    @Test
    fun `all candidates durably handled resolves to Completed`() {
        assertEquals(
            SourceEvaluationQueueState.Status.Completed,
            SourceEvaluationRunCompletionPolicy.resolveStatus(candidatesCount = 25, durablyHandledCount = 25),
        )
    }

    @Test
    fun `some candidates durably handled still resolves to Completed -- partial progress is real progress`() {
        assertEquals(
            SourceEvaluationQueueState.Status.Completed,
            SourceEvaluationRunCompletionPolicy.resolveStatus(candidatesCount = 25, durablyHandledCount = 3),
        )
    }

    @Test
    fun `zero candidates handed to the batch resolves to Completed -- there was nothing to do, not a failure`() {
        assertEquals(
            SourceEvaluationQueueState.Status.Completed,
            SourceEvaluationRunCompletionPolicy.resolveStatus(candidatesCount = 0, durablyHandledCount = 0),
        )
    }

    @Test
    fun `ROOT CAUSE - candidates present but zero durably handled resolves to NoActionableWork, not Completed`() {
        // This is exactly the live-device bug: Reassess outdated (25) tapped, worker returns success
        // in under a second, UI shows "Evaluation completed", but the database shows zero changes and
        // the button still reads Reassess outdated (25). The fix is this branch: a non-empty batch
        // that durably wrote nothing must never resolve to the generic Completed status.
        assertEquals(
            SourceEvaluationQueueState.Status.NoActionableWork,
            SourceEvaluationRunCompletionPolicy.resolveStatus(candidatesCount = 25, durablyHandledCount = 0),
        )
    }
}
// KMK <--

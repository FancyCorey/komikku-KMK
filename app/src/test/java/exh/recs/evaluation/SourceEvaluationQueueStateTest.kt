package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK v0.8.15 -->
/**
 * Coverage for [SourceEvaluationQueueState.Status.NoActionableWork] -- the fix for the live-device
 * "Reassess outdated (25)" / "Evaluation completed" / no DB change bug. See
 * [SourceEvaluationRunner]'s end-of-run status decision (candidates present but zero durably handled
 * -> NoActionableWork, never the generic Completed) and this status's doc comment.
 */
class SourceEvaluationQueueStateTest {

    @Test
    fun `NoActionableWork is idle -- the options section must re-appear like every other terminal state`() {
        assertTrue(SourceEvaluationQueueState(status = SourceEvaluationQueueState.Status.NoActionableWork).isIdle)
    }

    @Test
    fun `NoActionableWork is terminal -- a background job waiting on isTerminal must not hang forever`() {
        assertTrue(SourceEvaluationQueueState(status = SourceEvaluationQueueState.Status.NoActionableWork).isTerminal)
    }

    @Test
    fun `NoActionableWork is not Running`() {
        assertFalse(SourceEvaluationQueueState(status = SourceEvaluationQueueState.Status.NoActionableWork).isRunning)
    }

    @Test
    fun `Completed and NoActionableWork are distinct statuses -- the UI must be able to tell them apart`() {
        val completed = SourceEvaluationQueueState(status = SourceEvaluationQueueState.Status.Completed)
        val noWork = SourceEvaluationQueueState(status = SourceEvaluationQueueState.Status.NoActionableWork)
        assertFalse(completed.status == noWork.status)
    }
}
// KMK <--

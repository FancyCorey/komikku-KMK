package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK v0.8.10 -->
class SourceEvaluationCompletionLifecyclePolicyTest {

    @Test
    fun `a Completed status is cleared on leaving the screen`() {
        assertTrue(SourceEvaluationCompletionLifecyclePolicy.shouldClearOnLeave(SourceEvaluationQueueState.Status.Completed))
    }

    @Test
    fun `a Cancelled status is cleared on leaving the screen`() {
        assertTrue(SourceEvaluationCompletionLifecyclePolicy.shouldClearOnLeave(SourceEvaluationQueueState.Status.Cancelled))
    }

    @Test
    fun `a Failed status is cleared on leaving the screen`() {
        assertTrue(SourceEvaluationCompletionLifecyclePolicy.shouldClearOnLeave(SourceEvaluationQueueState.Status.Failed))
    }

    @Test
    fun `a ConnectivityLost status is cleared on leaving the screen`() {
        assertTrue(SourceEvaluationCompletionLifecyclePolicy.shouldClearOnLeave(SourceEvaluationQueueState.Status.ConnectivityLost))
    }

    // KMK v0.8.15 -->
    @Test
    fun `a NoActionableWork status is cleared on leaving the screen -- same lifecycle as the other terminal states`() {
        assertTrue(SourceEvaluationCompletionLifecyclePolicy.shouldClearOnLeave(SourceEvaluationQueueState.Status.NoActionableWork))
    }
    // KMK <--

    @Test
    fun `a Running status is never cleared on leaving the screen -- the background job must keep reporting progress`() {
        assertFalse(SourceEvaluationCompletionLifecyclePolicy.shouldClearOnLeave(SourceEvaluationQueueState.Status.Running))
    }

    @Test
    fun `a Cancelling status is never cleared on leaving the screen`() {
        assertFalse(SourceEvaluationCompletionLifecyclePolicy.shouldClearOnLeave(SourceEvaluationQueueState.Status.Cancelling))
    }

    @Test
    fun `an Idle status is never cleared -- there is nothing to clear`() {
        assertFalse(SourceEvaluationCompletionLifecyclePolicy.shouldClearOnLeave(SourceEvaluationQueueState.Status.Idle))
    }

    @Test
    fun `every Status value is classified -- no status silently falls through as clearable or not`() {
        // Regression guard: if a new Status is ever added without updating the policy, the compiler's
        // exhaustive `when` inside shouldClearOnLeave already enforces this, but pinning the full
        // count here means this test itself changes (and gets reviewed) rather than silently no-op'ing.
        val allStatuses = SourceEvaluationQueueState.Status.entries
        assertTrue(allStatuses.size == 8, "Expected 8 Status values; update this test's status coverage if that count changes.")
    }
}
// KMK <--

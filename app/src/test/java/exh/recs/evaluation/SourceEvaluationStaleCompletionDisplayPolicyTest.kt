package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK v0.8.12 -->
class SourceEvaluationStaleCompletionDisplayPolicyTest {

    @Test
    fun `shows completion after a stale run exhausted the actionable pool`() {
        val result = SourceEvaluationStaleCompletionDisplayPolicy.shouldShowCompletion(
            staleCandidatesEmpty = true,
            continuationCursorStaleIsSet = true,
            isRunning = false,
        )
        assertTrue(result)
    }

    @Test
    fun `does not show completion merely because no run has ever happened`() {
        // No outdated rows were ever workable and no reassessment run occurred -- this must not be
        // presented as "complete", since nothing was actually reassessed.
        val result = SourceEvaluationStaleCompletionDisplayPolicy.shouldShowCompletion(
            staleCandidatesEmpty = true,
            continuationCursorStaleIsSet = false,
            isRunning = false,
        )
        assertFalse(result)
    }

    @Test
    fun `does not show completion while an evaluation is running`() {
        val result = SourceEvaluationStaleCompletionDisplayPolicy.shouldShowCompletion(
            staleCandidatesEmpty = true,
            continuationCursorStaleIsSet = true,
            isRunning = true,
        )
        assertFalse(result)
    }

    @Test
    fun `does not show completion while actionable stale candidates remain`() {
        val result = SourceEvaluationStaleCompletionDisplayPolicy.shouldShowCompletion(
            staleCandidatesEmpty = false,
            continuationCursorStaleIsSet = true,
            isRunning = false,
        )
        assertFalse(result)
    }

    // KMK v0.8.13-fix1 -->
    @Test
    fun `no actionable stale and cursor null yields Hidden, not completed`() {
        val result = SourceEvaluationStaleCompletionDisplayPolicy.evaluate(
            staleCandidatesEmpty = true,
            continuationCursorStaleIsSet = false,
            isRunning = false,
            hasUnreachableOutdated = true,
        )
        assertEquals(SourceEvaluationStaleCompletionDisplayPolicy.DisplayState.Hidden, result)
    }

    @Test
    fun `no actionable stale, cursor set, unreachable rows remain yields CompletedWithUnreachableRemaining, not a misleading all-done message`() {
        val result = SourceEvaluationStaleCompletionDisplayPolicy.evaluate(
            staleCandidatesEmpty = true,
            continuationCursorStaleIsSet = true,
            isRunning = false,
            hasUnreachableOutdated = true,
        )
        assertEquals(SourceEvaluationStaleCompletionDisplayPolicy.DisplayState.CompletedWithUnreachableRemaining, result)
    }

    @Test
    fun `actionable stale exists yields Hidden -- no completion state while work remains`() {
        val result = SourceEvaluationStaleCompletionDisplayPolicy.evaluate(
            staleCandidatesEmpty = false,
            continuationCursorStaleIsSet = true,
            isRunning = false,
            hasUnreachableOutdated = true,
        )
        assertEquals(SourceEvaluationStaleCompletionDisplayPolicy.DisplayState.Hidden, result)
    }

    @Test
    fun `everything reassessed with nothing excluded yields CompletedAllActionable`() {
        val result = SourceEvaluationStaleCompletionDisplayPolicy.evaluate(
            staleCandidatesEmpty = true,
            continuationCursorStaleIsSet = true,
            isRunning = false,
            hasUnreachableOutdated = false,
        )
        assertEquals(SourceEvaluationStaleCompletionDisplayPolicy.DisplayState.CompletedAllActionable, result)
    }

    @Test
    fun `running an evaluation always yields Hidden regardless of unreachable rows`() {
        val result = SourceEvaluationStaleCompletionDisplayPolicy.evaluate(
            staleCandidatesEmpty = true,
            continuationCursorStaleIsSet = true,
            isRunning = true,
            hasUnreachableOutdated = true,
        )
        assertEquals(SourceEvaluationStaleCompletionDisplayPolicy.DisplayState.Hidden, result)
    }
    // KMK <--
}
// KMK <--

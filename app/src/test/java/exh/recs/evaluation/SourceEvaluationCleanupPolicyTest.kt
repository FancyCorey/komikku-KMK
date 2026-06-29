package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// KMK -->
class SourceEvaluationCleanupPolicyTest {

    // cleanupDecision: install failed / not installed after evaluation -> NotNeeded
    @Test
    fun `returns NotNeeded when extension was not installed after evaluation`() {
        val result = SourceEvaluationCleanupPolicy.cleanupDecision(
            preExistingInstalled = false,
            installedAfterEvaluation = false,
            isShared = false,
        )
        assertEquals(SourceEvaluationCleanupPolicy.CleanupDecision.NotNeeded, result)
    }

    // cleanupDecision: pre-existing -> SkipPreExisting
    @Test
    fun `returns SkipPreExisting when extension was already installed before evaluation`() {
        val result = SourceEvaluationCleanupPolicy.cleanupDecision(
            preExistingInstalled = true,
            installedAfterEvaluation = true,
            isShared = true,
        )
        assertEquals(SourceEvaluationCleanupPolicy.CleanupDecision.SkipPreExisting, result)
    }

    // cleanupDecision: pre-existing private -> still SkipPreExisting
    @Test
    fun `returns SkipPreExisting when pre-existing even if isShared is false`() {
        val result = SourceEvaluationCleanupPolicy.cleanupDecision(
            preExistingInstalled = true,
            installedAfterEvaluation = true,
            isShared = false,
        )
        assertEquals(SourceEvaluationCleanupPolicy.CleanupDecision.SkipPreExisting, result)
    }

    // cleanupDecision: private installed by evaluation -> RemovePrivateSilently
    @Test
    fun `returns RemovePrivateSilently when evaluation installed a private extension`() {
        val result = SourceEvaluationCleanupPolicy.cleanupDecision(
            preExistingInstalled = false,
            installedAfterEvaluation = true,
            isShared = false,
        )
        assertEquals(SourceEvaluationCleanupPolicy.CleanupDecision.RemovePrivateSilently, result)
    }

    // cleanupDecision: shared installed by evaluation -> PromptRequired
    @Test
    fun `returns PromptRequired when evaluation installed a system-shared extension`() {
        val result = SourceEvaluationCleanupPolicy.cleanupDecision(
            preExistingInstalled = false,
            installedAfterEvaluation = true,
            isShared = true,
        )
        assertEquals(SourceEvaluationCleanupPolicy.CleanupDecision.PromptRequired, result)
    }

    // NotNeeded takes priority over SkipPreExisting (not installed = nothing to skip)
    @Test
    fun `returns NotNeeded even when preExistingInstalled is true but extension not present after evaluation`() {
        val result = SourceEvaluationCleanupPolicy.cleanupDecision(
            preExistingInstalled = true,
            installedAfterEvaluation = false,
            isShared = false,
        )
        assertEquals(SourceEvaluationCleanupPolicy.CleanupDecision.NotNeeded, result)
    }
}
// KMK <--

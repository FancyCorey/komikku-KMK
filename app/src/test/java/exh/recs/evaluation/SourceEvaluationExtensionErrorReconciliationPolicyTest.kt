package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK v0.8.16 -->
// Direct regression coverage for the recordExtensionError() delete-before-upsert contract (plan
// item: "add the narrowest feasible test/verified helper for package-row delete + extension-level
// error upsert behavior"). Full SourceEvaluationRunner-level testing remains too heavy: this test
// suite has no fake/mock harness for DeleteSourceEvaluation/UpsertSourceEvaluation, so the decision
// itself was extracted into this pure policy (see SourceEvaluationExtensionErrorReconciliationPolicy
// doc comment) rather than tested indirectly through the runner.
class SourceEvaluationExtensionErrorReconciliationPolicyTest {

    @Test
    fun `successful package-row delete counts the candidate as durably handled`() {
        val outcome = SourceEvaluationExtensionErrorReconciliationPolicy.resolve(deleteSucceeded = true)
        assertTrue(outcome.countsAsDurablyHandled)
        assertEquals(0, outcome.reconciliationFailedCountDelta)
    }

    @Test
    fun `failed package-row delete does not count the candidate as durably handled`() {
        val outcome = SourceEvaluationExtensionErrorReconciliationPolicy.resolve(deleteSucceeded = false)
        assertFalse(outcome.countsAsDurablyHandled)
        assertEquals(1, outcome.reconciliationFailedCountDelta)
    }
}
// KMK <--

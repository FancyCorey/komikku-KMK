package exh.recs.evaluation

import eu.kanade.tachiyomi.extension.model.Extension
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK -->
// Regression coverage for the StaticFieldLeak fix (KMK Security and Degraded-Environment
// Hardening, follow-up pass). SourceEvaluationJobState and SourceRecommendationQualityJobState
// are process-scoped `object` singletons (effectively static). They previously held a reference
// to the whole running `SourceEvaluationRunner`/`SourceRecommendationQualityRunner`, each of which
// retains a `Context` field -- Android Lint's StaticFieldLeak flagged both.
//
// The fix is structural, not a suppression: SourceEvaluationJobState.activeRunner (type
// SourceEvaluationRunner?) was replaced with lastCompletedCandidateKeys (type Set<String>), and
// SourceRecommendationQualityJobState.activeRunner was removed outright (confirmed unused by any
// caller). Neither singleton can reference a Context anymore -- this is provable by the type
// signatures alone (a `Set<String>` cannot transitively hold an Android `Context`), which is what
// these tests assert together with the singletons' reset()/lifecycle contract.
class SourceEvaluationJobStateLeakSafetyTest {

    @AfterEach
    fun resetSingletons() {
        SourceEvaluationJobState.reset()
        SourceRecommendationQualityJobState.reset()
    }

    @Test
    fun `SourceEvaluationJobState no longer exposes a field capable of retaining a Context`() {
        // Compile-time proof by construction: lastCompletedCandidateKeys is declared as
        // Set<String>, and Kotlin's type system makes it impossible for a String-only collection
        // to transitively reference a Context. If this field's type is ever widened back to hold
        // a runner/Context, this test still compiles (it only checks value semantics) -- the real
        // guarantee is the type declaration itself; this test pins the *value* contract so a
        // regression in the surrounding read/write logic is still caught.
        SourceEvaluationJobState.lastCompletedCandidateKeys = setOf("a|b", "c|d")
        assertEquals(setOf("a|b", "c|d"), SourceEvaluationJobState.lastCompletedCandidateKeys)
    }

    @Test
    fun `reset clears lastCompletedCandidateKeys back to empty`() {
        SourceEvaluationJobState.lastCompletedCandidateKeys = setOf("leftover-key")
        SourceEvaluationJobState.reset()
        assertTrue(SourceEvaluationJobState.lastCompletedCandidateKeys.isEmpty())
    }

    @Test
    fun `reset clears every pending and active field, not just some of them`() {
        SourceEvaluationJobState.pendingCandidates = emptyList()
        SourceEvaluationJobState.pendingOptions = null
        SourceEvaluationJobState.lastCompletedCandidateKeys = setOf("x")
        SourceEvaluationJobState.pendingCursorFingerprint = "fingerprint"
        SourceEvaluationJobState.pendingAllCandidates = emptyList()
        SourceEvaluationJobState.pendingIsStaleRun = true
        SourceEvaluationJobState.activeQueueState.value = SourceEvaluationQueueState()

        SourceEvaluationJobState.reset()

        assertNull(SourceEvaluationJobState.pendingCandidates)
        assertNull(SourceEvaluationJobState.pendingOptions)
        assertTrue(SourceEvaluationJobState.lastCompletedCandidateKeys.isEmpty())
        assertNull(SourceEvaluationJobState.pendingCursorFingerprint)
        assertNull(SourceEvaluationJobState.pendingAllCandidates)
        assertEquals(false, SourceEvaluationJobState.pendingIsStaleRun)
        assertNull(SourceEvaluationJobState.activeQueueState.value)
    }

    @Test
    fun `older generation cannot overwrite a newer run or its completion keys`() {
        val first = candidate("first")
        val second = candidate("second")
        val firstGeneration = SourceEvaluationJobState.beginRun(
            candidates = listOf(first),
            options = SourceEvaluationOptions(batchSize = 1),
            continuationMetadata = metadata("first", listOf(first)),
        )
        val secondGeneration = SourceEvaluationJobState.beginRun(
            candidates = listOf(second),
            options = SourceEvaluationOptions(batchSize = 1),
            continuationMetadata = metadata("second", listOf(second)),
        )

        assertFalse(
            SourceEvaluationJobState.publish(
                firstGeneration,
                SourceEvaluationQueueState(status = SourceEvaluationQueueState.Status.Completed),
            ),
        )
        assertFalse(SourceEvaluationJobState.finish(firstGeneration, setOf("first-key")))
        assertEquals(SourceEvaluationQueueState.Status.Running, SourceEvaluationJobState.activeQueueState.value?.status)
        assertEquals(secondGeneration, SourceEvaluationJobState.pendingRun()?.generation)
        assertTrue(SourceEvaluationJobState.lastCompletedCandidateKeys.isEmpty())

        assertTrue(
            SourceEvaluationJobState.publish(
                secondGeneration,
                SourceEvaluationQueueState(status = SourceEvaluationQueueState.Status.Completed),
            ),
        )
        assertTrue(SourceEvaluationJobState.finish(secondGeneration, setOf("second-key")))
        assertEquals(setOf("second-key"), SourceEvaluationJobState.lastCompletedCandidateKeys)
        assertNull(SourceEvaluationJobState.pendingRun())
    }

    @Test
    fun `cancel invalidates the active generation and rejects late worker updates`() {
        val candidate = candidate("cancel")
        val generation = SourceEvaluationJobState.beginRun(
            candidates = listOf(candidate),
            options = SourceEvaluationOptions(batchSize = 1),
            continuationMetadata = metadata("cancel", listOf(candidate)),
        )

        SourceEvaluationJobState.cancelActive()

        assertEquals(SourceEvaluationQueueState.Status.Cancelled, SourceEvaluationJobState.activeQueueState.value?.status)
        assertFalse(
            SourceEvaluationJobState.publish(
                generation,
                SourceEvaluationQueueState(status = SourceEvaluationQueueState.Status.Completed),
            ),
        )
        assertFalse(SourceEvaluationJobState.finish(generation, setOf("late-key")))
        assertTrue(SourceEvaluationJobState.lastCompletedCandidateKeys.isEmpty())
    }

    @Test
    fun `SourceRecommendationQualityJobState reset clears pending targets and queue state`() {
        SourceRecommendationQualityJobState.pendingTargets = emptyList()
        SourceRecommendationQualityJobState.activeQueueState.value =
            SourceRecommendationQualityQueueState(status = SourceRecommendationQualityQueueState.Status.Running)

        SourceRecommendationQualityJobState.reset()

        assertNull(SourceRecommendationQualityJobState.pendingTargets)
        assertNull(SourceRecommendationQualityJobState.activeQueueState.value)
    }

    private fun candidate(key: String) = EvaluationCandidate(
        extension = mockk<Extension.Available>(relaxed = true),
        priorityRank = key.hashCode(),
    )

    private fun metadata(fingerprint: String, candidates: List<EvaluationCandidate>) =
        SourceEvaluationContinuationMetadata(
            fingerprint = fingerprint,
            allCandidates = candidates,
            isStaleRun = false,
        )
}
// KMK <--

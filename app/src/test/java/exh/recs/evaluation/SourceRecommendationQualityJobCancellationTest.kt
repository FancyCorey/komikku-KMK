package exh.recs.evaluation

import androidx.work.ListenableWorker.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

// KMK -->
// Direct regression coverage for SourceRecommendationQualityJob's cancellation-contract fix (KMK
// Security Remediation, 2026-07-31 pass). Previously catch (e: CancellationException) performed
// cleanup and then returned Result.success(), reporting a cancelled run as succeeded to
// WorkManager. See SourceEvaluationJobCancellationTest for the identical rationale on why this
// worker cannot be instantiated directly in a JVM unit test and why a narrow orchestration seam
// (runSourceRecommendationQualityWork) was extracted instead of adding Robolectric or
// androidx.work:work-testing.
class SourceRecommendationQualityJobCancellationTest {

    private class FakeNotifier : SourceRecommendationQualityWorkerNotifier {
        var updateProgressCalls = 0
        var dismissProgressCalled = false
        var showCompleteCall: Int? = null

        override fun updateProgress(queueState: SourceRecommendationQualityQueueState) {
            updateProgressCalls++
        }

        override fun dismissProgress() {
            dismissProgressCalled = true
        }

        override fun showComplete(checkedCount: Int) {
            showCompleteCall = checkedCount
        }
    }

    @Test
    fun `cancellation cancels the runner, publishes Cancelled status, dismisses the notification, and is rethrown`() = runTest {
        val notifier = FakeNotifier()
        var state: SourceRecommendationQualityQueueState? =
            SourceRecommendationQualityQueueState(status = SourceRecommendationQualityQueueState.Status.Running)
        var runnerCancelled = false
        var finallyRan = false

        var thrown: CancellationException? = null
        try {
            runSourceRecommendationQualityWork(
                collectUntilTerminal = { onState ->
                    onState(
                        SourceRecommendationQualityQueueState(
                            status = SourceRecommendationQualityQueueState.Status.Running,
                            completedCount = 4,
                        ),
                    )
                    throw CancellationException("job cancelled")
                },
                notifier = notifier,
                getState = { state },
                setState = { state = it },
                cancelRunner = { runnerCancelled = true },
                finallyCleanup = { finallyRan = true },
                logUnexpectedError = { },
            )
            fail("runSourceRecommendationQualityWork must rethrow CancellationException, not swallow it")
        } catch (e: CancellationException) {
            thrown = e
        }

        assertEquals("job cancelled", thrown?.message)
        assertTrue(runnerCancelled)
        assertEquals(SourceRecommendationQualityQueueState.Status.Cancelled, state?.status)
        assertEquals(4, state?.completedCount)
        assertTrue(notifier.updateProgressCalls > 0)
        assertTrue(notifier.dismissProgressCalled)
        assertNull(notifier.showCompleteCall)
        assertTrue(finallyRan)
    }

    @Test
    fun `an ordinary exception fails the run, dismisses the notification, and still runs finally cleanup`() = runTest {
        val notifier = FakeNotifier()
        var state: SourceRecommendationQualityQueueState? =
            SourceRecommendationQualityQueueState(status = SourceRecommendationQualityQueueState.Status.Running)
        var finallyRan = false

        val result = runSourceRecommendationQualityWork(
            collectUntilTerminal = { throw IllegalStateException("boom") },
            notifier = notifier,
            getState = { state },
            setState = { state = it },
            cancelRunner = { fail("cancelRunner must not be called on an ordinary failure") },
            finallyCleanup = { finallyRan = true },
            logUnexpectedError = { },
        )

        assertEquals(Result.failure(), result)
        assertEquals(SourceRecommendationQualityQueueState.Status.Failed, state?.status)
        assertEquals(SourceEvaluationProbeErrorKind.INTERNAL.storageKey, state?.errorMessage)
        assertTrue(notifier.dismissProgressCalled)
        assertNull(notifier.showCompleteCall)
        assertTrue(finallyRan)
    }

    @Test
    fun `a completed run reports success and shows the completion notification`() = runTest {
        val notifier = FakeNotifier()
        var state: SourceRecommendationQualityQueueState? = null
        var finallyRan = false

        val result = runSourceRecommendationQualityWork(
            collectUntilTerminal = { onState ->
                onState(
                    SourceRecommendationQualityQueueState(
                        status = SourceRecommendationQualityQueueState.Status.Completed,
                        completedCount = 7,
                    ),
                )
            },
            notifier = notifier,
            getState = { state },
            setState = { state = it },
            cancelRunner = { fail("cancelRunner must not be called on success") },
            finallyCleanup = { finallyRan = true },
            logUnexpectedError = { },
        )

        assertEquals(Result.success(), result)
        assertFalse(notifier.dismissProgressCalled)
        assertEquals(7, notifier.showCompleteCall)
        assertTrue(finallyRan)
    }
}
// KMK <--

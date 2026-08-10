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
// Direct regression coverage for SourceEvaluationJob's cancellation-contract fix (KMK Security
// Remediation, 2026-07-31 pass). Previously catch (e: CancellationException) performed cleanup
// and then returned Result.success(), reporting a cancelled run as succeeded to WorkManager.
//
// SourceEvaluationJob itself cannot be instantiated in a JVM unit test -- constructing a real
// CoroutineWorker requires androidx.work's WorkerParameters/WorkDatabase machinery, only
// available via the androidx.work:work-testing artifact (typically paired with Robolectric for a
// JVM Context). Neither is a dependency of this project (confirmed absent from
// gradle/androidx.versions.toml and app/build.gradle.kts before writing this test). Rather than
// add either dependency just to construct a worker, SourceEvaluationJob.kt now exposes
// runSourceEvaluationWork(...) -- the exact try/catch/finally control flow doWork() delegates to
// once candidates/options are resolved, parameterized over the notifier, runner, and
// SourceEvaluationJobState side effects doWork() previously called directly. This test exercises
// that real function with fakes, not a duplicated policy.
class SourceEvaluationJobCancellationTest {

    private class FakeNotifier : SourceEvaluationWorkerNotifier {
        var updateProgressCalls = 0
        var dismissProgressCalled = false
        var showCompleteCall: Int? = null

        override fun updateProgress(queueState: SourceEvaluationQueueState) {
            updateProgressCalls++
        }

        override fun dismissProgress() {
            dismissProgressCalled = true
        }

        override fun showComplete(strongFitCount: Int) {
            showCompleteCall = strongFitCount
        }
    }

    @Test
    fun `cancellation cancels the runner, publishes Cancelled status, dismisses the notification, and is rethrown`() = runTest {
        val notifier = FakeNotifier()
        var state: SourceEvaluationQueueState? = SourceEvaluationQueueState(status = SourceEvaluationQueueState.Status.Running)
        var runnerCancelled = false
        var finallyRan = false

        var thrown: CancellationException? = null
        try {
            runSourceEvaluationWork(
                collectUntilTerminal = { onState ->
                    onState(SourceEvaluationQueueState(status = SourceEvaluationQueueState.Status.Running, completedCount = 2))
                    throw CancellationException("job cancelled")
                },
                notifier = notifier,
                getState = { state },
                setState = { state = it },
                cancelRunner = { runnerCancelled = true },
                finallyCleanup = { finallyRan = true },
                logUnexpectedError = { },
            )
            fail("runSourceEvaluationWork must rethrow CancellationException, not swallow it")
        } catch (e: CancellationException) {
            thrown = e
        }

        assertEquals("job cancelled", thrown?.message)
        assertTrue(runnerCancelled)
        assertEquals(SourceEvaluationQueueState.Status.Cancelled, state?.status)
        // The completedCount observed before cancellation is preserved by copy(), not reset.
        assertEquals(2, state?.completedCount)
        assertTrue(notifier.updateProgressCalls > 0)
        assertTrue(notifier.dismissProgressCalled)
        assertNull(notifier.showCompleteCall)
        assertTrue(finallyRan)
    }

    @Test
    fun `an ordinary exception fails the run, dismisses the notification, and still runs finally cleanup`() = runTest {
        val notifier = FakeNotifier()
        var state: SourceEvaluationQueueState? = SourceEvaluationQueueState(status = SourceEvaluationQueueState.Status.Running)
        var finallyRan = false

        val result = runSourceEvaluationWork(
            collectUntilTerminal = { throw IllegalStateException("boom") },
            notifier = notifier,
            getState = { state },
            setState = { state = it },
            cancelRunner = { fail("cancelRunner must not be called on an ordinary failure") },
            finallyCleanup = { finallyRan = true },
            logUnexpectedError = { },
        )

        assertEquals(Result.failure(), result)
        assertEquals(SourceEvaluationQueueState.Status.Failed, state?.status)
        assertEquals(SourceEvaluationProbeErrorKind.INTERNAL.storageKey, state?.errorMessage)
        assertTrue(notifier.dismissProgressCalled)
        assertNull(notifier.showCompleteCall)
        assertTrue(finallyRan)
    }

    @Test
    fun `a completed run reports success and shows the completion notification`() = runTest {
        val notifier = FakeNotifier()
        var state: SourceEvaluationQueueState? = null
        var finallyRan = false

        val result = runSourceEvaluationWork(
            collectUntilTerminal = { onState ->
                onState(SourceEvaluationQueueState(status = SourceEvaluationQueueState.Status.Completed))
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
        assertEquals(0, notifier.showCompleteCall)
        assertTrue(finallyRan)
    }

    @Test
    fun `a non-completed terminal status such as connectivity lost dismisses rather than shows completion`() = runTest {
        val notifier = FakeNotifier()
        var state: SourceEvaluationQueueState? = null

        val result = runSourceEvaluationWork(
            collectUntilTerminal = { onState ->
                onState(SourceEvaluationQueueState(status = SourceEvaluationQueueState.Status.ConnectivityLost))
            },
            notifier = notifier,
            getState = { state },
            setState = { state = it },
            cancelRunner = { fail("cancelRunner must not be called") },
            finallyCleanup = { },
            logUnexpectedError = { },
        )

        assertEquals(Result.success(), result)
        assertTrue(notifier.dismissProgressCalled)
        assertNull(notifier.showCompleteCall)
    }
}
// KMK <--

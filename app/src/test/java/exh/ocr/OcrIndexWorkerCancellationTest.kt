package exh.ocr

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
// Direct regression coverage for OcrIndexWorker's cancellation-contract fix (KMK Security
// Remediation, 2026-07-31 pass). Previously catch (e: CancellationException) performed cleanup
// and then returned Result.success(), reporting a cancelled run as succeeded to WorkManager.
//
// OcrIndexWorker itself cannot be instantiated in a JVM unit test -- constructing a real
// CoroutineWorker requires androidx.work's WorkerParameters/WorkDatabase machinery, only
// available via the androidx.work:work-testing artifact (typically paired with Robolectric for a
// JVM Context). Neither is a dependency of this project (confirmed absent from
// gradle/androidx.versions.toml and app/build.gradle.kts before writing this test). Rather than
// add either dependency just to construct a worker, OcrIndexWorker.kt now exposes
// runOcrIndexWork(...) -- the exact try/catch/finally control flow doWork() delegates to,
// parameterized over the notifier and state-publishing side effects doWork() previously called
// directly. This test exercises that real function with fakes, not a duplicated policy.
class OcrIndexWorkerCancellationTest {

    private class FakeNotifier : OcrIndexWorkerNotifier {
        var updateProgressCalls = 0
        var dismissProgressCalled = false
        var showCompleteCall: Triple<Int, Int, Int>? = null

        override fun updateProgress(progress: OcrIndexProgress) {
            updateProgressCalls++
        }

        override fun dismissProgress() {
            dismissProgressCalled = true
        }

        override fun showComplete(recognizedPages: Int, emptyPages: Int, failedPages: Int) {
            showCompleteCall = Triple(recognizedPages, emptyPages, failedPages)
        }
    }

    @Test
    fun `cancellation runs cleanup, publishes cancelled state, dismisses the notification, and is rethrown`() = runTest {
        val notifier = FakeNotifier()
        var cancelledProgress: OcrIndexProgress? = null
        var notRunning = false
        var progressPublished: OcrIndexProgress? = null

        var thrown: CancellationException? = null
        try {
            runOcrIndexWork(
                runIndexing = { onProgress ->
                    onProgress(OcrIndexProgress(completedPages = 3, totalPages = 10))
                    throw CancellationException("job cancelled")
                },
                notifier = notifier,
                publishProgress = { progressPublished = it },
                // Mirrors OcrIndexWorker.doWork()'s real wiring: markCancelled receives the raw
                // last-observed progress and is responsible for stamping isCancelled itself.
                markCancelled = { cancelledProgress = it.copy(isCancelled = true) },
                markFailed = { fail("markFailed must not be called on cancellation") },
                markNotRunning = { notRunning = true },
                logUnexpectedError = { },
            )
            fail("runOcrIndexWork must rethrow CancellationException, not swallow it")
        } catch (e: CancellationException) {
            thrown = e
        }

        assertEquals("job cancelled", thrown?.message)
        assertEquals(3, progressPublished?.completedPages)
        assertTrue(notifier.updateProgressCalls > 0)
        assertTrue(notifier.dismissProgressCalled)
        assertNull(notifier.showCompleteCall)
        assertTrue(cancelledProgress?.isCancelled == true)
        // The last progress observed before cancellation is preserved, not reset.
        assertEquals(3, cancelledProgress?.completedPages)
        assertTrue(notRunning)
    }

    @Test
    fun `an ordinary exception fails the run, dismisses the notification, and still runs finally cleanup`() = runTest {
        val notifier = FakeNotifier()
        var failedProgress: OcrIndexProgress? = null
        var notRunning = false

        val result = runOcrIndexWork(
            runIndexing = { throw IllegalStateException("boom") },
            notifier = notifier,
            publishProgress = { },
            markCancelled = { fail("markCancelled must not be called on an ordinary failure") },
            markFailed = { failedProgress = it },
            markNotRunning = { notRunning = true },
            logUnexpectedError = { },
        )

        assertEquals(Result.failure(), result)
        assertTrue(notifier.dismissProgressCalled)
        assertNull(notifier.showCompleteCall)
        assertTrue(failedProgress?.isFailed == true)
        assertEquals(OcrErrorKey.Internal.storageKey, failedProgress?.lastError)
        assertTrue(notRunning)
    }

    @Test
    fun `a completed, non-cancelled run reports success and shows the completion notification`() = runTest {
        val notifier = FakeNotifier()
        var notRunning = false

        val result = runOcrIndexWork(
            runIndexing = { onProgress ->
                onProgress(OcrIndexProgress(recognizedPages = 5, emptyPages = 1, failedPages = 0, isComplete = true))
            },
            notifier = notifier,
            publishProgress = { },
            markCancelled = { fail("markCancelled must not be called on success") },
            markFailed = { fail("markFailed must not be called on success") },
            markNotRunning = { notRunning = true },
            logUnexpectedError = { },
        )

        assertEquals(Result.success(), result)
        assertFalse(notifier.dismissProgressCalled)
        assertEquals(Triple(5, 1, 0), notifier.showCompleteCall)
        assertTrue(notRunning)
    }
}
// KMK <--

package exh.eh

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// KMK -->
// Regression coverage for the EHentaiUpdateWorker.doWork() retry/failure truthfulness fix (KMK
// Security and Degraded-Environment Hardening, follow-up pass). Previously every ordinary
// exception was swallowed into Result.success(), which lied to WorkManager about the run's
// outcome and discarded its retry/backoff machinery.
//
// EHentaiUpdateWorker is enqueued via PeriodicWorkRequestBuilder (see
// EHentaiUpdateWorker.scheduleBackground), so it is periodic work. WorkManager 2.7+ supports
// Result.retry() for periodic work (this project pins androidx.work 2.11.1), so a transient
// failure should retry with backoff rather than silently reporting success or waiting a full
// period. EHentaiUpdateWorkerResultPolicy.classify(...) is the pure boundary this contract turns
// on; doWork() itself is a thin CoroutineWorker wrapper around it (see doWork()'s own doc
// comment) that this project's test suite cannot instantiate directly -- constructing a real
// CoroutineWorker requires androidx.work's WorkerParameters/WorkDatabase machinery, which is only
// available through the `androidx.work:work-testing` artifact (typically paired with Robolectric
// for a JVM Context). Neither is a dependency of this project (confirmed: no `work-testing` or
// Robolectric reference exists in app/build.gradle.kts or gradle/androidx.versions.toml), so
// end-to-end doWork() behavior (CancellationException rethrow through a live CoroutineWorker,
// and the finally-block notification cleanup call) is verified by code inspection and the
// `try/catch(CancellationException) { throw e } ... finally { cancelProgressNotification() }`
// structure itself, not by an instrumented test in this pass -- adding that dependency was
// judged out of scope for one worker's test coverage. This is stated explicitly rather than
// silently omitted, per this pass's own reporting requirement.
class EHentaiUpdateWorkerResultPolicyTest {

    @Test
    fun `an attempt count below the cap retries`() {
        assertEquals(
            EHentaiUpdateWorkerResultPolicy.Outcome.Retry,
            EHentaiUpdateWorkerResultPolicy.classify(runAttemptCount = 0, maxAttempts = 3),
        )
        assertEquals(
            EHentaiUpdateWorkerResultPolicy.Outcome.Retry,
            EHentaiUpdateWorkerResultPolicy.classify(runAttemptCount = 2, maxAttempts = 3),
        )
    }

    @Test
    fun `an attempt count at the cap fails instead of retrying forever`() {
        assertEquals(
            EHentaiUpdateWorkerResultPolicy.Outcome.Failure,
            EHentaiUpdateWorkerResultPolicy.classify(runAttemptCount = 3, maxAttempts = 3),
        )
    }

    @Test
    fun `an attempt count beyond the cap still fails, it does not resume retrying`() {
        assertEquals(
            EHentaiUpdateWorkerResultPolicy.Outcome.Failure,
            EHentaiUpdateWorkerResultPolicy.classify(runAttemptCount = 100, maxAttempts = 3),
        )
    }

    @Test
    fun `matches EHentaiUpdateWorker's own configured attempt cap at the real boundary`() {
        // EHentaiUpdateWorker.MAX_RUN_ATTEMPTS is the value doWork() actually passes -- this
        // pins the test to that real constant instead of an arbitrary literal, so a change to
        // the worker's cap is reflected here automatically.
        val cap = EHentaiUpdateWorker.MAX_RUN_ATTEMPTS
        assertEquals(
            EHentaiUpdateWorkerResultPolicy.Outcome.Retry,
            EHentaiUpdateWorkerResultPolicy.classify(runAttemptCount = cap - 1, maxAttempts = cap),
        )
        assertEquals(
            EHentaiUpdateWorkerResultPolicy.Outcome.Failure,
            EHentaiUpdateWorkerResultPolicy.classify(runAttemptCount = cap, maxAttempts = cap),
        )
    }
}
// KMK <--

package eu.kanade.tachiyomi.data.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SyncDataJobResultPolicyTest {
    @Test
    fun `ordinary failures retry below the cap`() {
        assertEquals(
            SyncDataJobResultPolicy.Outcome.Retry,
            SyncDataJobResultPolicy.classify(runAttemptCount = 0, maxAttempts = 3),
        )
        assertEquals(
            SyncDataJobResultPolicy.Outcome.Retry,
            SyncDataJobResultPolicy.classify(runAttemptCount = 2, maxAttempts = 3),
        )
    }

    @Test
    fun `failures stop retrying at and beyond the cap`() {
        assertEquals(
            SyncDataJobResultPolicy.Outcome.Failure,
            SyncDataJobResultPolicy.classify(runAttemptCount = 3, maxAttempts = 3),
        )
        assertEquals(
            SyncDataJobResultPolicy.Outcome.Failure,
            SyncDataJobResultPolicy.classify(runAttemptCount = 99, maxAttempts = 3),
        )
    }

    @Test
    fun `the test boundary follows the production cap`() {
        val cap = SyncDataJob.MAX_RUN_ATTEMPTS
        assertEquals(
            SyncDataJobResultPolicy.Outcome.Retry,
            SyncDataJobResultPolicy.classify(cap - 1, cap),
        )
        assertEquals(
            SyncDataJobResultPolicy.Outcome.Failure,
            SyncDataJobResultPolicy.classify(cap, cap),
        )
    }
}

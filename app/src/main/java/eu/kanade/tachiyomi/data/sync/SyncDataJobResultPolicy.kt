package eu.kanade.tachiyomi.data.sync

/** Bounds ordinary sync retries while keeping cancellation outside the failure path. */
internal object SyncDataJobResultPolicy {
    sealed interface Outcome {
        data object Retry : Outcome
        data object Failure : Outcome
    }

    fun classify(runAttemptCount: Int, maxAttempts: Int): Outcome =
        if (runAttemptCount < maxAttempts) Outcome.Retry else Outcome.Failure
}

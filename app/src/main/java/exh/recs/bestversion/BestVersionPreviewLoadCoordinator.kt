package exh.recs.bestversion

import exh.recs.RecommendationEffectiveResourcePolicy
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Bounds concurrent candidate preview fetches while allowing each result to publish immediately. */
internal class BestVersionPreviewLoadCoordinator(
    maxConcurrent: Int = DEFAULT_MAX_CONCURRENT,
) {
    private val semaphore = Semaphore(maxConcurrent.coerceAtLeast(1))

    suspend fun <T> runBounded(operation: suspend () -> T): T = semaphore.withPermit { operation() }

    companion object {
        // Preview work includes page-list and image-url resolution, so keep the aggregate below the
        // five-thread Best Version dispatcher and avoid a burst on constrained devices.
        const val DEFAULT_MAX_CONCURRENT = RecommendationEffectiveResourcePolicy.DEFAULT_PREVIEW_CONCURRENCY
    }
}

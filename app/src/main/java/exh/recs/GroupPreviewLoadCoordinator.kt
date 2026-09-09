package exh.recs

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

// KMK v0.8.6 -->
/**
 * Small, pure-ish, directly testable coordinator for GROUP_PREVIEW row operations. Extracted out of
 * [RecommendsScreenModel] so bounded-concurrency and timeout behavior can be unit tested without
 * building a full Injekt-backed [RecommendsScreenModel] test harness (documented behavior
 * "max active source operations" / progressive-completion requirements).
 *
 * Responsibilities are deliberately narrow: bound concurrency via [Semaphore], bound wall-clock time
 * via [withTimeoutOrNull], and track the maximum concurrency actually observed (for diagnostics).
 * Exception handling/isolation (catching per-source errors, distinguishing CancellationException,
 * fatal Errors) remains the caller's responsibility — this class never swallows an exception thrown
 * by [operation]; it always propagates after releasing its permit (guaranteed by [withPermit]'s own
 * finally semantics).
 */
class GroupPreviewLoadCoordinator(
    maxConcurrent: Int = DEFAULT_MAX_CONCURRENT,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    maxConcurrentEnrichment: Int = DEFAULT_MAX_CONCURRENT_ENRICHMENT,
) {
    private val semaphore = Semaphore(maxConcurrent)
    private val activeCount = AtomicInteger(0)
    private val maxObservedConcurrency = AtomicInteger(0)

    // KMK v0.8.6: shared across every source active under this coordinator (passed down into
    // CrossExtensionGenreSearchSource) so nested detail-enrichment requests are bounded by ONE total
    // in-flight count across all concurrently active GROUP_PREVIEW sources, not per source. Without
    // this, up to [maxConcurrent] sources could each independently run their own
    // MAX_ENRICH_PER_SOURCE-bounded enrichment fan-out at the same time, multiplying the effective
    // total far past the intended shared budget (documented behavior).
    val sharedEnrichmentSemaphore: Semaphore = Semaphore(maxConcurrentEnrichment)

    /**
     * Runs [operation] under the concurrency limit and timeout. Returns null on timeout (never
     * throws a TimeoutCancellationException). Any other exception thrown by [operation] propagates
     * to the caller after the permit is released. CancellationException propagates uninterrupted.
     */
    suspend fun <T> runBounded(operation: suspend () -> T): T? {
        return semaphore.withPermit {
            val current = activeCount.incrementAndGet()
            maxObservedConcurrency.updateAndGet { prev -> maxOf(prev, current) }
            try {
                withTimeoutOrNull(timeoutMs) { operation() }
            } finally {
                activeCount.decrementAndGet()
            }
        }
    }

    /** Maximum number of [runBounded] calls that were simultaneously inside their permit, across this coordinator's lifetime. */
    fun observedMaxConcurrency(): Int = maxObservedConcurrency.get()

    companion object {
        const val DEFAULT_MAX_CONCURRENT = 4
        const val DEFAULT_TIMEOUT_MS = 20_000L

        /**
         * Total in-flight detail-request ("enrichment") budget shared across every concurrently
         * active GROUP_PREVIEW source. Deliberately not simply [DEFAULT_MAX_CONCURRENT] — a small
         * multiple of it (each of up to 4 concurrent sources may reasonably have 1-2 detail requests
         * genuinely in flight at once) while still capping the total far below the un-bounded
         * `4 sources x MAX_ENRICH_PER_SOURCE(10) = 40` worst case this fixes.
         */
        const val DEFAULT_MAX_CONCURRENT_ENRICHMENT = 8
    }
}

/**
 * Small, pure, directly testable generation/staleness guard (documented behavior "state updates verify
 * ... generation before mutation" and "stale results from an old ... generation are ignored").
 * [RecommendsScreenModel] holds one instance; each load captures [next] at start, and every state
 * mutation checks [isCurrent] before applying.
 */
class GenerationGuard {
    private val current = AtomicInteger(0)

    /** Starts a new generation, superseding any prior one. Returns the token this load must present to [isCurrent]. */
    fun next(): Int = current.incrementAndGet()

    /** True only if [generation] is still the latest one started — false once a newer [next] has been called. */
    fun isCurrent(generation: Int): Boolean = current.get() == generation
}
// KMK <--

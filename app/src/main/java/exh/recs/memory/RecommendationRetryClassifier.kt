package exh.recs.memory

// KMK --> v0.7.40: bounded retry policy for For You discovery page failures
import eu.kanade.tachiyomi.network.HttpException
import tachiyomi.domain.taste.model.RecommendationDiscoveryProgress
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Pure classifier that decides whether a discovery page failure should be retried and when.
 *
 * Retry policy:
 * - Maximum [MAX_ATTEMPTS] retries per page.
 * - Initial backoff of [INITIAL_DELAY_MS]; doubles per attempt up to [MAX_DELAY_MS].
 * - Only connectivity/I/O/timeout failures with evidence of transience are retryable.
 * - HTTP 429 and 5xx responses are retryable; other HTTP 4xx responses,
 *   UnsupportedOperation, and unknown/runtime exceptions are permanent.
 *   Treating unknown extension/runtime failures as permanent avoids repeatedly re-invoking
 *   broken extension code with no evidence the failure is transient (v0.7.41 correction D).
 * - [CancellationException] is never passed here; callers must rethrow it before reaching this,
 *   so cancellation is never classified or persisted as a failed retry.
 */
object RecommendationRetryClassifier {

    const val MAX_ATTEMPTS = 3
    const val INITIAL_DELAY_MS = 5L * 60 * 1000 // 5 minutes
    const val MAX_DELAY_MS = 24L * 60 * 60 * 1000 // 24 hours

    /**
     * Returns [RecommendationDiscoveryProgress.FAILURE_KIND_RETRYABLE] or [FAILURE_KIND_PERMANENT].
     *
     * Typed HTTP status codes are handled before the [IOException] fallback. The HTTP 4xx message
     * check remains for extensions that wrap client errors (404, 403, etc.) in an [IOException].
     */
    fun classify(e: Throwable): String = when {
        e is UnsupportedOperationException -> RecommendationDiscoveryProgress.FAILURE_KIND_PERMANENT
        e is HttpException && (e.code == 429 || e.code in 500..599) ->
            RecommendationDiscoveryProgress.FAILURE_KIND_RETRYABLE
        e is HttpException && e.code in 400..499 -> RecommendationDiscoveryProgress.FAILURE_KIND_PERMANENT
        e.message?.contains("HTTP 4") == true -> RecommendationDiscoveryProgress.FAILURE_KIND_PERMANENT
        e is UnknownHostException -> RecommendationDiscoveryProgress.FAILURE_KIND_RETRYABLE
        e is SocketTimeoutException -> RecommendationDiscoveryProgress.FAILURE_KIND_RETRYABLE
        e is IOException -> RecommendationDiscoveryProgress.FAILURE_KIND_RETRYABLE
        else -> RecommendationDiscoveryProgress.FAILURE_KIND_PERMANENT
    }

    /** Returns true if a failure with this kind and attempt count may be retried. */
    fun isRetryable(failureKind: String?, attemptCount: Int): Boolean =
        failureKind == RecommendationDiscoveryProgress.FAILURE_KIND_RETRYABLE && attemptCount < MAX_ATTEMPTS

    /** Returns the absolute timestamp after which the next retry is allowed (exponential backoff). */
    fun nextRetryAt(attemptCount: Int, nowMs: Long): Long {
        val delay = (INITIAL_DELAY_MS shl attemptCount.coerceIn(0, 8)).coerceAtMost(MAX_DELAY_MS)
        return nowMs + delay
    }
}
// KMK <--

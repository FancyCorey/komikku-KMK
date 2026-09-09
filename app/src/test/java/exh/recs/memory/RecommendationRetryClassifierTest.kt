package exh.recs.memory

// KMK --> v0.7.41: conservative retry classification tests
import eu.kanade.tachiyomi.network.HttpException
import exh.recs.RecommendationErrorClassifier
import exh.recs.isRetryableForDiscovery
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.RecommendationDiscoveryProgress
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Tests for [RecommendationRetryClassifier].
 *
 * Transient HTTP responses (429 and 5xx) are retryable, while client errors, unsupported
 * operations, and unknown/runtime exceptions remain permanent so broken extension code is not
 * repeatedly re-invoked.
 *
 * Run with: ./gradlew :app:testDebugUnitTest --tests "*.RecommendationRetryClassifierTest"
 */
class RecommendationRetryClassifierTest {

    private val retryable = RecommendationDiscoveryProgress.FAILURE_KIND_RETRYABLE
    private val permanent = RecommendationDiscoveryProgress.FAILURE_KIND_PERMANENT

    // ---- Retryable (transient) ----

    @Test
    fun `IOException is retryable`() {
        assertEquals(retryable, RecommendationRetryClassifier.classify(IOException("boom")))
    }

    @Test
    fun `UnknownHostException is retryable`() {
        assertEquals(retryable, RecommendationRetryClassifier.classify(UnknownHostException("no dns")))
    }

    @Test
    fun `SocketTimeoutException is retryable`() {
        assertEquals(retryable, RecommendationRetryClassifier.classify(SocketTimeoutException("timeout")))
    }

    @Test
    fun `HTTP 429 and 5xx are retryable`() {
        assertEquals(retryable, RecommendationRetryClassifier.classify(HttpException(429)))
        assertEquals(retryable, RecommendationRetryClassifier.classify(HttpException(500)))
        assertEquals(retryable, RecommendationRetryClassifier.classify(HttpException(502)))
    }

    // ---- Permanent ----

    @Test
    fun `UnsupportedOperationException is permanent`() {
        assertEquals(permanent, RecommendationRetryClassifier.classify(UnsupportedOperationException("not supported")))
    }

    @Test
    fun `HTTP 4xx wrapped in IOException is permanent`() {
        // Client errors (404, 403…) are commonly wrapped in an IOException but must not be retried.
        assertEquals(permanent, RecommendationRetryClassifier.classify(IOException("HTTP 404 Not Found")))
    }

    @Test
    fun `typed HTTP client errors are permanent`() {
        assertEquals(permanent, RecommendationRetryClassifier.classify(HttpException(400)))
        assertEquals(permanent, RecommendationRetryClassifier.classify(HttpException(401)))
        assertEquals(permanent, RecommendationRetryClassifier.classify(HttpException(404)))
    }

    @Test
    fun `unknown runtime exception is permanent`() {
        assertEquals(permanent, RecommendationRetryClassifier.classify(IllegalStateException("weird extension bug")))
    }

    @Test
    fun `generic exception is permanent`() {
        assertEquals(permanent, RecommendationRetryClassifier.classify(RuntimeException("mystery")))
    }

    // ---- isRetryable gating ----

    @Test
    fun `isRetryable is true for retryable kind below max attempts`() {
        assertTrue(RecommendationRetryClassifier.isRetryable(retryable, 0))
        assertTrue(RecommendationRetryClassifier.isRetryable(retryable, RecommendationRetryClassifier.MAX_ATTEMPTS - 1))
    }

    @Test
    fun `isRetryable is false at or beyond max attempts`() {
        assertFalse(RecommendationRetryClassifier.isRetryable(retryable, RecommendationRetryClassifier.MAX_ATTEMPTS))
    }

    @Test
    fun `isRetryable is false for permanent kind`() {
        assertFalse(RecommendationRetryClassifier.isRetryable(permanent, 0))
    }

    @Test
    fun `isRetryable is false for null kind`() {
        assertFalse(RecommendationRetryClassifier.isRetryable(null, 0))
    }

    // ---- backoff ----

    @Test
    fun `nextRetryAt grows with attempt count and is capped`() {
        val base = 1_000_000L
        val first = RecommendationRetryClassifier.nextRetryAt(0, base) - base
        val second = RecommendationRetryClassifier.nextRetryAt(1, base) - base
        assertTrue(second > first) { "Backoff should grow: first=$first second=$second" }
        val huge = RecommendationRetryClassifier.nextRetryAt(30, base) - base
        assertTrue(huge <= RecommendationRetryClassifier.MAX_DELAY_MS) { "Backoff must be capped at MAX_DELAY_MS" }
    }

    // ---- AG14 candidate finding: HTTP handling reconciled with RecommendationErrorClassifier ----

    /**
     * [RecommendationRetryClassifier] (discovery-page-level retry/backoff) and
     * [exh.recs.RecommendationErrorClassifier.isRetryableForDiscovery] (per-source-request-level
     * retry gating) are two independent, differently-scoped classifiers that must still agree on
     * which HTTP outcomes are worth retrying a second time -- otherwise one owner could keep
     * hammering a permanently-failing source while the other gives up on a merely rate-limited one.
     * Pins that agreement directly for every status this codebase treats as meaningfully distinct.
     */
    @Test
    fun `HTTP retryability agrees with RecommendationErrorClassifier for every distinguished status`() {
        val statuses = listOf(429, 500, 502, 503, 400, 401, 403, 404)
        statuses.forEach { status ->
            val retryClassifierSaysRetryable =
                RecommendationRetryClassifier.classify(HttpException(status)) == retryable
            val errorClassifierSaysRetryable =
                RecommendationErrorClassifier.classify(HttpException(status)).isRetryableForDiscovery()
            assertEquals(
                retryClassifierSaysRetryable,
                errorClassifierSaysRetryable,
                "HTTP $status: RecommendationRetryClassifier retryable=$retryClassifierSaysRetryable but " +
                    "RecommendationErrorClassifier.isRetryableForDiscovery=$errorClassifierSaysRetryable",
            )
        }
    }
}
// KMK <--

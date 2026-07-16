package exh.recs.memory

// KMK --> v0.7.41: conservative retry classification tests
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
 * v0.7.41 correction D: only connectivity/I/O/timeout failures are retryable; HTTP 4xx,
 * UnsupportedOperation, and unknown/runtime exceptions are permanent (fail closed) so broken
 * extension code is not repeatedly re-invoked.
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
}
// KMK <--

package exh.recs

import eu.kanade.tachiyomi.network.HttpException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

// KMK v0.8.21-fix2 -->
/**
 * Regression coverage for the reopened AUG-05/AUG-09 investigation's confirmed root cause: a real
 * HttpException (carrying a genuine HTTP status code) was previously classified as generic
 * [RecommendationErrorKind.Internal], giving the user no truthful reason for a source failure.
 */
class RecommendationErrorClassifierTest {

    @Test
    fun `HTTP 429 classifies as RateLimit, not Internal`() {
        assertEquals(RecommendationErrorKind.RateLimit, RecommendationErrorClassifier.classify(HttpException(429)))
    }

    @Test
    fun `HTTP 401 and 403 classify as Authentication, not Internal`() {
        assertEquals(RecommendationErrorKind.Authentication, RecommendationErrorClassifier.classify(HttpException(401)))
        assertEquals(RecommendationErrorKind.Authentication, RecommendationErrorClassifier.classify(HttpException(403)))
    }

    @Test
    fun `HTTP 5xx classifies as ServerError, not Internal`() {
        // KMK v0.8.21-fix4: R2/AUG-05 source-review correction -- the live-reproduced failure was a
        // real HTTP 502 that fix2 deliberately left folded into Internal. The corrective review
        // specifically named 502 as the reproduced case and required truthful classification.
        assertEquals(RecommendationErrorKind.ServerError, RecommendationErrorClassifier.classify(HttpException(500)))
        assertEquals(RecommendationErrorKind.ServerError, RecommendationErrorClassifier.classify(HttpException(502)))
        assertEquals(RecommendationErrorKind.ServerError, RecommendationErrorClassifier.classify(HttpException(503)))
        assertEquals(RecommendationErrorKind.ServerError, RecommendationErrorClassifier.classify(HttpException(504)))
    }

    @Test
    fun `HTTP statuses outside the 4xx-derived and 5xx categories remain Internal`() {
        assertEquals(RecommendationErrorKind.Internal, RecommendationErrorClassifier.classify(HttpException(404)))
        assertEquals(RecommendationErrorKind.Internal, RecommendationErrorClassifier.classify(HttpException(400)))
    }

    @Test
    fun `only the transient kinds are retryable for discovery`() {
        assertEquals(true, RecommendationErrorKind.Network.isRetryableForDiscovery())
        assertEquals(true, RecommendationErrorKind.Timeout.isRetryableForDiscovery())
        assertEquals(true, RecommendationErrorKind.RateLimit.isRetryableForDiscovery())
        assertEquals(true, RecommendationErrorKind.ServerError.isRetryableForDiscovery())
        assertEquals(false, RecommendationErrorKind.Authentication.isRetryableForDiscovery())
        assertEquals(false, RecommendationErrorKind.Internal.isRetryableForDiscovery())
        assertEquals(false, RecommendationErrorKind.FileAccess.isRetryableForDiscovery())
        assertEquals(false, RecommendationErrorKind.ExtensionIncompatible.isRetryableForDiscovery())
        assertEquals(false, RecommendationErrorKind.Cancelled.isRetryableForDiscovery())
    }

    @Test
    fun `existing classifications are unchanged by the HttpException check`() {
        assertEquals(RecommendationErrorKind.Network, RecommendationErrorClassifier.classify(UnknownHostException()))
        assertEquals(RecommendationErrorKind.Timeout, RecommendationErrorClassifier.classify(SocketTimeoutException()))
        assertEquals(RecommendationErrorKind.FileAccess, RecommendationErrorClassifier.classify(FileNotFoundException()))
        assertEquals(RecommendationErrorKind.Network, RecommendationErrorClassifier.classify(IOException()))
        assertEquals(RecommendationErrorKind.Internal, RecommendationErrorClassifier.classify(IllegalStateException()))
    }

    @Test
    fun `every RecommendationErrorKind has a distinct string resource, no accidental collision`() {
        val resources = RecommendationErrorKind.entries.map { recommendationErrorMessageRes(it) }
        assertEquals(RecommendationErrorKind.entries.size, resources.toSet().size)
    }
}
// KMK <--

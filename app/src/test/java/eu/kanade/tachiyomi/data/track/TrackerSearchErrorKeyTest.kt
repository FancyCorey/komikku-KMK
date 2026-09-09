package eu.kanade.tachiyomi.data.track

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException

class TrackerSearchErrorKeyTest {

    @Test
    fun `transport failures classify as no-network`() {
        assertEquals(TrackerSearchErrorKey.NoNetwork, TrackerSearchErrorKey.from(UnknownHostException("api.host")))
        assertEquals(TrackerSearchErrorKey.NoNetwork, TrackerSearchErrorKey.from(SocketTimeoutException()))
        assertEquals(TrackerSearchErrorKey.NoNetwork, TrackerSearchErrorKey.from(IOException()))
    }

    @Test
    fun `a server-controlled http error body never selects a verbatim branch`() {
        // The tracker controls this string; it must not influence the rendered key.
        val serverText = "500: <html><body>internal token=abc123 user=someone</body></html>"
        assertEquals(TrackerSearchErrorKey.Unknown, TrackerSearchErrorKey.from(IllegalStateException(serverText)))
    }

    @Test
    fun `a blank message still resolves to a real localized key rather than an empty screen`() {
        // The original `?:` only substituted for a NULL message, so these rendered nothing at all.
        assertEquals(TrackerSearchErrorKey.Unknown, TrackerSearchErrorKey.from(RuntimeException("")))
        assertEquals(TrackerSearchErrorKey.Unknown, TrackerSearchErrorKey.from(RuntimeException("   ")))
        assertEquals(TrackerSearchErrorKey.NoNetwork, TrackerSearchErrorKey.from(IOException("")))
    }

    @Test
    fun `a null message resolves to unknown`() {
        assertEquals(TrackerSearchErrorKey.Unknown, TrackerSearchErrorKey.from(RuntimeException()))
    }

    @Test
    fun `a null throwable resolves to unknown rather than throwing`() {
        assertEquals(TrackerSearchErrorKey.Unknown, TrackerSearchErrorKey.from(null))
    }

    @Test
    fun `identity-bearing text in the message is never echoed by the key`() {
        val identityBearing = IllegalArgumentException("failed for user kiyoshi at https://tracker.example/u/1")
        val key = TrackerSearchErrorKey.from(identityBearing)
        assertEquals(TrackerSearchErrorKey.Unknown, key)
        // The enum carries no free-form payload at all, which is what makes the leak structurally
        // impossible rather than merely unlikely.
        assertEquals("Unknown", key.name)
    }

    @Test
    fun `cancellation is not classified here because it never reaches this owner`() {
        // TrackerSearchScreenModel.trackingSearch rethrows CancellationException *before* building
        // Result.failure, so cancellation never becomes a rendered failure. If someone ever routed it
        // here anyway, it must not masquerade as an actionable network problem.
        assertEquals(TrackerSearchErrorKey.Unknown, TrackerSearchErrorKey.from(CancellationException()))
    }
}

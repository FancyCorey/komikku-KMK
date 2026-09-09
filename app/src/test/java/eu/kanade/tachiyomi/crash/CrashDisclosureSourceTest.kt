package eu.kanade.tachiyomi.crash

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class CrashDisclosureSourceTest {
    private fun source(path: String): String = Files.readAllBytes(Path.of(path)).toString(Charsets.UTF_8)

    @Test
    fun `default crash surface does not render exception text`() {
        val screen = source("src/main/java/eu/kanade/presentation/crash/CrashScreen.kt")

        assertFalse("text = exception.toString()" in screen)
        assertTrue("if (detailsVisible)" in screen)
        assertTrue("crash_screen_show_details" in screen)
        assertTrue("exception?.message" in screen)
    }

    @Test
    fun `crash-log sharing requires explicit privacy confirmation`() {
        val screen = source("src/main/java/eu/kanade/presentation/crash/CrashScreen.kt")

        assertTrue("onAcceptClick = { confirmShare = true }" in screen)
        assertTrue("crash_screen_share_warning_message" in screen)
        assertTrue(screen.indexOf("if (confirmShare)") < screen.indexOf("CrashLogUtil(context).dumpLogs(exception)"))
    }

    @Test
    fun `intent transport is bounded and missing payload is not force unwrapped`() {
        val handler = source("src/main/java/eu/kanade/tachiyomi/crash/GlobalExceptionHandler.kt")

        assertTrue("CrashPayloadPolicy.detailsForTransport" in handler)
        assertTrue("CrashPayloadPolicy.acceptEncodedPayload" in handler)
        assertFalse("getStringExtra(INTENT_EXTRA)!!" in handler)
        assertFalse("value.stackTraceToString()" in handler)
    }
}

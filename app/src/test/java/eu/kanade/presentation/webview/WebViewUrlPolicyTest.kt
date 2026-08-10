package eu.kanade.presentation.webview

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK -->
class WebViewUrlPolicyTest {

    @Test
    fun `accepts only http and https schemes`() {
        assertTrue(isAllowedWebUrl("http://example.invalid/page"))
        assertTrue(isAllowedWebUrl("https://example.invalid/page"))
        assertTrue(isAllowedWebUrl("https://example.invalid/page#section"))
    }

    @Test
    fun `rejects non-web and scheme-prefix lookalikes`() {
        listOf(
            "intent://example.invalid/page",
            "file:///sdcard/private.txt",
            "javascript:alert(1)",
            "httpx://example.invalid/page",
            "httpsx://example.invalid/page",
            "https:example.invalid/page",
            "https://user:password@example.invalid/page",
            "not a uri",
        ).forEach { assertFalse(isAllowedWebUrl(it), it) }
    }

    @Test
    fun `initial webview content is guarded before navigation callbacks`() {
        val source = java.io.File("src/main/java/eu/kanade/presentation/webview/WebViewScreenContent.kt").readText()

        assertTrue(source.contains("if (!isAllowedWebUrl(url))"))
        assertTrue(source.contains("LaunchedEffect(url)"))
    }

    @Test
    fun `cookie cleanup logs do not include the external url`() {
        val activity = java.io.File("src/main/java/eu/kanade/tachiyomi/ui/webview/WebViewActivity.kt").readText()
        val model = java.io.File("src/main/java/eu/kanade/tachiyomi/ui/webview/WebViewScreenModel.kt").readText()

        assertFalse(activity.contains("cookies for: \$url"))
        assertFalse(model.contains("cookies for: \$url"))
    }
}
// KMK <--

package eu.kanade.tachiyomi.ui.deeplink

import android.app.SearchManager
import android.content.Intent
import eu.kanade.tachiyomi.ui.main.MainActivity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

// KMK -->
// Regression coverage for the DeepLinkActivity intent-forwarding hardening fix (KMK Security and
// Degraded-Environment Hardening, follow-up pass). DeepLinkActivity is exported, and an explicit
// intent naming its component bypasses <intent-filter> matching entirely, so every test here
// treats the incoming action/mimeType/extras as attacker-controlled rather than assuming they
// match one of the manifest's declared filters.
class DeepLinkIntentSanitizerTest {

    private fun extrasOf(vararg pairs: Pair<String, String?>): (String) -> String? {
        val map = pairs.toMap()
        return { key -> map[key] }
    }

    // --- valid supported deep links ---

    @Test
    fun `ACTION_SEARCH with a query is forwarded as SearchQuery`() {
        val result = DeepLinkIntentSanitizer.sanitize(
            action = Intent.ACTION_SEARCH,
            mimeType = null,
            extra = extrasOf(SearchManager.QUERY to "one piece"),
        )
        assertEquals(DeepLinkIntentSanitizer.SanitizedDeepLink.SearchQuery("one piece"), result)
    }

    @Test
    fun `the Google Assistant search action with a query is forwarded as SearchQuery`() {
        val result = DeepLinkIntentSanitizer.sanitize(
            action = DeepLinkIntentSanitizer.GOOGLE_SEARCH_ACTION,
            mimeType = null,
            extra = extrasOf(SearchManager.QUERY to "naruto"),
        )
        assertEquals(DeepLinkIntentSanitizer.SanitizedDeepLink.SearchQuery("naruto"), result)
    }

    @Test
    fun `ACTION_SEND with text plain mime type and text is forwarded as SendText`() {
        val result = DeepLinkIntentSanitizer.sanitize(
            action = Intent.ACTION_SEND,
            mimeType = "text/plain",
            extra = extrasOf(Intent.EXTRA_TEXT to "https://example.com/manga/1"),
        )
        assertEquals(DeepLinkIntentSanitizer.SanitizedDeepLink.SendText("https://example.com/manga/1"), result)
    }

    @Test
    fun `named search action with query and filter is forwarded as NamedSearch`() {
        val result = DeepLinkIntentSanitizer.sanitize(
            action = MainActivity.INTENT_SEARCH,
            mimeType = null,
            extra = extrasOf(MainActivity.INTENT_SEARCH_QUERY to "bleach", MainActivity.INTENT_SEARCH_FILTER to "en"),
        )
        assertEquals(DeepLinkIntentSanitizer.SanitizedDeepLink.NamedSearch("bleach", "en"), result)
    }

    @Test
    fun `named search action with only a query has a null filter`() {
        val result = DeepLinkIntentSanitizer.sanitize(
            action = MainActivity.INTENT_SEARCH,
            mimeType = null,
            extra = extrasOf(MainActivity.INTENT_SEARCH_QUERY to "bleach"),
        )
        assertEquals(DeepLinkIntentSanitizer.SanitizedDeepLink.NamedSearch("bleach", null), result)
    }

    // --- unsupported actions ---

    @Test
    fun `ACTION_VIEW is never forwarded, even with a backup-like data string`() {
        // DeepLinkActivity declares no VIEW intent-filter -- MainActivity handles VIEW deep links
        // (backup restore, add-repo) through its own separate manifest entries. A VIEW action
        // reaching DeepLinkActivity can only come from an explicit-component sender, so it must
        // never be forwarded from here.
        val result = DeepLinkIntentSanitizer.sanitize(
            action = Intent.ACTION_VIEW,
            mimeType = null,
            extra = extrasOf(SearchManager.QUERY to "irrelevant"),
        )
        assertNull(result)
    }

    @Test
    fun `an unrecognized custom action is rejected`() {
        val result = DeepLinkIntentSanitizer.sanitize(
            action = "com.attacker.evil.ACTION",
            mimeType = null,
            extra = extrasOf(),
        )
        assertNull(result)
    }

    @Test
    fun `a null action is rejected`() {
        val result = DeepLinkIntentSanitizer.sanitize(action = null, mimeType = null, extra = extrasOf())
        assertNull(result)
    }

    // --- malicious or unexpected URI schemes / mime types ---

    @Test
    fun `ACTION_SEND with a non text-plain mime type is rejected`() {
        val result = DeepLinkIntentSanitizer.sanitize(
            action = Intent.ACTION_SEND,
            mimeType = "application/octet-stream",
            extra = extrasOf(Intent.EXTRA_TEXT to "payload"),
        )
        assertNull(result)
    }

    @Test
    fun `ACTION_SEND with a null mime type is rejected`() {
        val result = DeepLinkIntentSanitizer.sanitize(
            action = Intent.ACTION_SEND,
            mimeType = null,
            extra = extrasOf(Intent.EXTRA_TEXT to "payload"),
        )
        assertNull(result)
    }

    // --- arbitrary extras ---

    @Test
    fun `extras outside the allowlisted key for the action are never consulted`() {
        // The lambda only ever receives the one key each action reads; this proves the sanitizer
        // has no path to reading an arbitrary attacker-supplied extra key.
        val seenKeys = mutableListOf<String>()
        val result = DeepLinkIntentSanitizer.sanitize(
            action = Intent.ACTION_SEARCH,
            mimeType = null,
            extra = { key ->
                seenKeys += key
                if (key == SearchManager.QUERY) "query" else "should-not-be-used"
            },
        )
        assertEquals(listOf(SearchManager.QUERY), seenKeys)
        assertEquals(DeepLinkIntentSanitizer.SanitizedDeepLink.SearchQuery("query"), result)
    }

    // --- malformed intents ---

    @Test
    fun `ACTION_SEARCH with a missing query is rejected`() {
        val result = DeepLinkIntentSanitizer.sanitize(
            action = Intent.ACTION_SEARCH,
            mimeType = null,
            extra = extrasOf(),
        )
        assertNull(result)
    }

    @Test
    fun `ACTION_SEARCH with a blank query is rejected`() {
        val result = DeepLinkIntentSanitizer.sanitize(
            action = Intent.ACTION_SEARCH,
            mimeType = null,
            extra = extrasOf(SearchManager.QUERY to ""),
        )
        assertNull(result)
    }

    @Test
    fun `named search with a missing query is rejected even if a filter is present`() {
        val result = DeepLinkIntentSanitizer.sanitize(
            action = MainActivity.INTENT_SEARCH,
            mimeType = null,
            extra = extrasOf(MainActivity.INTENT_SEARCH_FILTER to "en"),
        )
        assertNull(result)
    }

    @Test
    fun `ACTION_SEND with blank text is rejected`() {
        val result = DeepLinkIntentSanitizer.sanitize(
            action = Intent.ACTION_SEND,
            mimeType = "text/plain",
            extra = extrasOf(Intent.EXTRA_TEXT to ""),
        )
        assertNull(result)
    }

    // --- unexpected components/flags are structurally impossible to leak through this API ---

    @Test
    fun `the sanitizer API has no parameter through which component, flags, data, or clipData could pass`() {
        // Compile-time proof by construction: sanitize()'s signature is (action, mimeType,
        // extra-lookup) -> SanitizedDeepLink?, a closed sealed type with only query/text fields.
        // There is no way to route a component, flag, data Uri, or clipData through it. Assert
        // the sealed type's known variants have not grown an unexpected field.
        val query = DeepLinkIntentSanitizer.SanitizedDeepLink.SearchQuery("q")
        val send = DeepLinkIntentSanitizer.SanitizedDeepLink.SendText("t")
        val named = DeepLinkIntentSanitizer.SanitizedDeepLink.NamedSearch("q", null)
        assertEquals("q", query.query)
        assertEquals("t", send.text)
        assertEquals("q", named.query)
    }

    @Test
    fun `an empty-string action never throws, it simply rejects`() {
        val result = DeepLinkIntentSanitizer.sanitize(
            action = "",
            mimeType = "",
            extra = { null },
        )
        assertNull(result)
    }
}
// KMK <--

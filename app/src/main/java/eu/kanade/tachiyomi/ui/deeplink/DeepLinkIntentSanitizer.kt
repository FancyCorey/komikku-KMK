package eu.kanade.tachiyomi.ui.deeplink

import android.app.SearchManager
import android.content.Intent
import eu.kanade.tachiyomi.ui.main.MainActivity

// KMK -->
/**
 * Pure allowlist/sanitizer for the deep-link intents [DeepLinkActivity]'s manifest
 * `<intent-filter>` entries declare support for.
 *
 * [DeepLinkActivity] is `android:exported="true"`. An explicit intent naming its component
 * bypasses `<intent-filter>` action/category matching entirely -- any app can start it directly
 * with `ComponentName("app.komikku...", "eu.kanade.tachiyomi.ui.deeplink.DeepLinkActivity")` and
 * an arbitrary action, MIME type, data Uri, extras, categories, or flags. The action/MIME type
 * reaching [DeepLinkActivity.onCreate] must therefore never be assumed to match one of the
 * declared filters (`android.intent.action.SEARCH`, the Google Assistant search action,
 * `eu.kanade.tachiyomi.SEARCH`, or `android.intent.action.SEND` with `text/plain`).
 *
 * This function decides, from only the specific fields each supported action actually reads,
 * whether to forward anything at all -- and if so, exactly which minimal, explicit intent to
 * build for [MainActivity][eu.kanade.tachiyomi.ui.main.MainActivity]. It never sees or forwards
 * the original intent's `data`, `clipData`, `categories`, `component`, or any extra beyond the
 * one or two keys each action reads. In particular, `android.intent.action.VIEW` (used to open
 * `.tachibk` backup files and `tachiyomi://add-repo` links) is intentionally not in this
 * allowlist: `DeepLinkActivity` declares no `VIEW` intent filter, so a `VIEW` intent reaching it
 * can only come from an explicit-component sender bypassing the manifest, not a legitimate deep
 * link -- forwarding it would let a malicious app trick this exported proxy into opening an
 * attacker-controlled backup file. `MainActivity` already handles `VIEW` deep links directly
 * through its own manifest `<intent-filter>` entries; that path is unaffected by this file.
 *
 * Operates on plain extracted fields (never a real `android.content.Intent`) so it can be unit
 * tested without Robolectric or framework mocking -- `Intent`/`SearchManager` string constants
 * referenced below are compile-time constants baked into the Android stub jar and are safe to
 * read in a plain JVM unit test; only *method calls* on framework classes are stubbed to throw.
 */
object DeepLinkIntentSanitizer {

    const val GOOGLE_SEARCH_ACTION = "com.google.android.gms.actions.SEARCH_ACTION"
    const val PLAIN_TEXT_MIME_TYPE = "text/plain"

    /** The minimal, explicit set of fields safe to forward for one supported action family. */
    sealed interface SanitizedDeepLink {
        data class SearchQuery(val query: String) : SanitizedDeepLink
        data class SendText(val text: String) : SanitizedDeepLink
        data class NamedSearch(val query: String, val filter: String?) : SanitizedDeepLink
    }

    /**
     * @param action the incoming intent's action, or null.
     * @param mimeType the incoming intent's resolved MIME type ([Intent.getType]), or null --
     * only consulted for [Intent.ACTION_SEND], matching this activity's declared
     * `android:mimeType="text/plain"` filter.
     * @param extra looks up a single `String` extra by key from the incoming intent. Every other
     * extra, and every non-extra field, is never consulted and is never forwarded.
     * @return `null` when [action] does not match a supported action family, or when the
     * required extra for that family is missing/blank -- the caller must not forward anything in
     * that case.
     */
    fun sanitize(action: String?, mimeType: String?, extra: (String) -> String?): SanitizedDeepLink? {
        return when (action) {
            Intent.ACTION_SEARCH, GOOGLE_SEARCH_ACTION -> {
                val query = extra(SearchManager.QUERY)
                if (query.isNullOrEmpty()) null else SanitizedDeepLink.SearchQuery(query)
            }
            Intent.ACTION_SEND -> {
                if (mimeType != PLAIN_TEXT_MIME_TYPE) {
                    null
                } else {
                    val text = extra(Intent.EXTRA_TEXT)
                    if (text.isNullOrEmpty()) null else SanitizedDeepLink.SendText(text)
                }
            }
            MainActivity.INTENT_SEARCH -> {
                val query = extra(MainActivity.INTENT_SEARCH_QUERY)
                if (query.isNullOrEmpty()) null else SanitizedDeepLink.NamedSearch(query, extra(MainActivity.INTENT_SEARCH_FILTER))
            }
            else -> null
        }
    }
}
// KMK <--

package eu.kanade.tachiyomi.ui.deeplink

import android.app.Activity
import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import eu.kanade.tachiyomi.ui.main.MainActivity

// KMK -->
/**
 * Exported, no-display proxy for a small allowlisted set of search/send deep-link actions into
 * [MainActivity]. See [DeepLinkIntentSanitizer] for why the incoming intent must never be
 * forwarded as-is.
 */
// KMK <--
class DeepLinkActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // KMK -->
        val sanitized = DeepLinkIntentSanitizer.sanitize(
            action = intent.action,
            mimeType = intent.type,
            extra = intent::getStringExtra,
        )
        if (sanitized != null) {
            startActivity(buildForwardIntent(sanitized))
        }
        // KMK <--
        finish()
    }

    // KMK -->
    private fun buildForwardIntent(sanitized: DeepLinkIntentSanitizer.SanitizedDeepLink): Intent {
        return Intent().apply {
            setClass(applicationContext, MainActivity::class.java)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            when (sanitized) {
                is DeepLinkIntentSanitizer.SanitizedDeepLink.SearchQuery -> {
                    action = Intent.ACTION_SEARCH
                    putExtra(SearchManager.QUERY, sanitized.query)
                }
                is DeepLinkIntentSanitizer.SanitizedDeepLink.SendText -> {
                    action = Intent.ACTION_SEND
                    type = DeepLinkIntentSanitizer.PLAIN_TEXT_MIME_TYPE
                    putExtra(Intent.EXTRA_TEXT, sanitized.text)
                }
                is DeepLinkIntentSanitizer.SanitizedDeepLink.NamedSearch -> {
                    action = MainActivity.INTENT_SEARCH
                    putExtra(MainActivity.INTENT_SEARCH_QUERY, sanitized.query)
                    sanitized.filter?.let { putExtra(MainActivity.INTENT_SEARCH_FILTER, it) }
                }
            }
        }
    }
    // KMK <--
}

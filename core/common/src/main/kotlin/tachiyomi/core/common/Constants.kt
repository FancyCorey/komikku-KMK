package tachiyomi.core.common

object Constants {
    const val SPONSOR = "https://github.com/sponsors/cuong-tran"

    const val URL_HELP = "https://komikku-app.github.io/docs/guides/troubleshooting/"
    const val URL_HELP_UPCOMING = "https://komikku-app.github.io/docs/faq/updates/upcoming"

    const val MANGA_EXTRA = "manga"

    const val MAIN_ACTIVITY = "eu.kanade.tachiyomi.ui.main.MainActivity"

    // Shortcut actions
    const val SHORTCUT_LIBRARY = "eu.kanade.tachiyomi.SHOW_LIBRARY"
    const val SHORTCUT_MANGA = "eu.kanade.tachiyomi.SHOW_MANGA"
    const val SHORTCUT_UPDATES = "eu.kanade.tachiyomi.SHOW_RECENTLY_UPDATED"
    const val SHORTCUT_HISTORY = "eu.kanade.tachiyomi.SHOW_RECENTLY_READ"
    const val SHORTCUT_SOURCES = "eu.kanade.tachiyomi.SHOW_CATALOGUES"
    const val SHORTCUT_EXTENSIONS = "eu.kanade.tachiyomi.EXTENSIONS"
    const val SHORTCUT_DOWNLOADS = "eu.kanade.tachiyomi.SHOW_DOWNLOADS"

    // KMK -->
    const val SHORTCUT_LIBRARY_UPDATE_ERRORS = "eu.kanade.tachiyomi.SHOW_LIBRARY_UPDATE_ERRORS"
    const val OPEN_SOURCE_EVALUATION = "eu.kanade.tachiyomi.OPEN_SOURCE_EVALUATION"
    // KMK v0.8.8: chapter-completion rating prompt's "rate other versions" step. Extras carry only
    // primitives (manga id + rating int) — never a screen or match-mode object — through the Intent.
    const val OPEN_CROSS_EXTENSION_MATCH_FOR_RATING = "eu.kanade.tachiyomi.OPEN_CROSS_EXTENSION_MATCH_FOR_RATING"
    const val CROSS_EXTENSION_MATCH_MANGA_ID_EXTRA = "cross_extension_match_manga_id"
    const val CROSS_EXTENSION_MATCH_RATING_EXTRA = "cross_extension_match_rating"
    // KMK OCR -->
    const val OPEN_OCR_SEARCH = "eu.kanade.tachiyomi.OPEN_OCR_SEARCH"
    // KMK OCR <--
    // KMK <--
}

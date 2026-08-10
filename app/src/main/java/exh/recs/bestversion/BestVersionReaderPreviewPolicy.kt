package exh.recs.bestversion

// KMK v0.8.18 -->
/**
 * Pure mapping from a handful of safe, read-only `ReaderPreferences` values to the display
 * behavior the Best Version full-screen candidate comparison should use, so page-vs-strip sources
 * compare more fairly without embedding the real reader.
 *
 * Deliberately does **not** touch `ReaderActivity`/`ReaderViewModel` or any reader lifecycle
 * concern. This screen only reads three preference values (`defaultReadingMode`,
 * `cropBordersWebtoon`, `webtoonSidePadding`) through the existing `ReaderPreferences` accessor
 * object -- no `Composable`/screen-model coupling, no reader `Composable`s, no reader navigation.
 *
 * Explicitly reused: only the *values* of these three preferences, read directly off
 * `ReaderPreferences` the same way any other settings screen already does.
 *
 * Explicitly **not** reused, by design: reading history, mark-as-read, page-turn timers, scheduled
 * jobs, chapter transitions, page preloading, Discord RPC, the reader's page-turn/tap-zone menus,
 * or any reader page action (bookmark, share, save, set-as-cover). This screen stays a read-only,
 * comparison-only surface built on `PagePreviewFetcher`/`SourceRuntime`, exactly as before.
 */
object BestVersionReaderPreviewPolicy {

    /** Mirrors `ReaderPreferences.ReadingModeType`'s stored int values without importing reader UI code. */
    private const val READING_MODE_WEBTOON = 4
    private const val READING_MODE_CONTINUOUS_VERTICAL = 5

    data class Display(
        /** True when the user's own reader default reading mode is a webtoon/strip-style mode. */
        val webtoonStyle: Boolean,
        /** Horizontal side padding (dp) to apply around full-screen preview pages. */
        val sidePaddingDp: Int,
    )

    /**
     * @param defaultReadingModeValue raw value from `ReaderPreferences.defaultReadingMode().get()`.
     * @param webtoonSidePaddingPercent raw value from `ReaderPreferences.webtoonSidePadding().get()`
     *   (a 0-25 percent-of-width value, same range/meaning the reader itself uses).
     */
    fun resolve(defaultReadingModeValue: Int, webtoonSidePaddingPercent: Int): Display {
        val webtoonStyle = defaultReadingModeValue == READING_MODE_WEBTOON ||
            defaultReadingModeValue == READING_MODE_CONTINUOUS_VERTICAL
        // KMK v0.8.18: only apply the user's own side-padding preference when they actually read in
        // a webtoon-style mode -- a paged-mode reader user's webtoon padding preference (often 0,
        // since they rarely see it) shouldn't visually squeeze a page-style comparison.
        val sidePaddingDp = if (webtoonStyle) webtoonSidePaddingPercent.coerceIn(0, 25) else 0
        return Display(webtoonStyle = webtoonStyle, sidePaddingDp = sidePaddingDp)
    }
}
// KMK <--

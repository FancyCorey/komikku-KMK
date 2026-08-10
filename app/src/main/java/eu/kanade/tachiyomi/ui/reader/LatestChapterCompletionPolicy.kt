package eu.kanade.tachiyomi.ui.reader

// KMK v0.8.8 -->
/**
 * Pure decision helper: does this page-progress update represent *genuine completion of the latest
 * available chapter*? Used to decide whether to show the chapter-completion rating prompt.
 *
 * Deliberately never compares chapter names/numbers as strings — "latest" is defined purely by
 * chapter ordering ([hasNextChapter], derived from the caller's authoritative
 * `viewerChapters.nextChapter` — the same source of truth the reader's own next/previous navigation
 * already uses), and "genuine completion" mirrors the exact same last-page check
 * `ReaderViewModel.updateChapterProgress` already uses to mark a chapter read (last real page index,
 * or the second-to-last index when [hasExtraPage] applies), so this can never disagree with what the
 * reader itself considers "read."
 */
object LatestChapterCompletionPolicy {

    /**
     * @param pageIndex the page index just reached.
     * @param lastPageIndex the completed chapter's last page index (`pages.lastIndex`), or null if pages aren't loaded (never a genuine completion).
     * @param hasExtraPage true when the viewer's extra-page (double-page) mode means the second-to-last index is the effective end.
     * @param hasNextChapter true when the reader's chapter list has a chapter after this one (i.e. this is NOT the latest available chapter).
     * @param isErrorPage true when the reached page is in an error state (never a genuine completion).
     */
    fun isGenuineLatestChapterCompletion(
        pageIndex: Int,
        lastPageIndex: Int?,
        hasExtraPage: Boolean,
        hasNextChapter: Boolean,
        isErrorPage: Boolean,
    ): Boolean {
        if (isErrorPage || lastPageIndex == null) return false
        if (hasNextChapter) return false // not the latest chapter
        val reachedLastPage = pageIndex == lastPageIndex || (hasExtraPage && pageIndex == lastPageIndex - 1)
        return reachedLastPage
    }
}
// KMK <--

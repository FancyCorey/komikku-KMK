package eu.kanade.tachiyomi.ui.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// KMK v0.8.8 -->
class LatestChapterCompletionPolicyTest {

    @Test
    fun `reaching the last page of the latest chapter is a genuine completion`() {
        assertEquals(
            true,
            LatestChapterCompletionPolicy.isGenuineLatestChapterCompletion(
                pageIndex = 9,
                lastPageIndex = 9,
                hasExtraPage = false,
                hasNextChapter = false,
                isErrorPage = false,
            ),
        )
    }

    @Test
    fun `reaching the last page of a non-latest chapter is not a genuine completion`() {
        assertEquals(
            false,
            LatestChapterCompletionPolicy.isGenuineLatestChapterCompletion(
                pageIndex = 9,
                lastPageIndex = 9,
                hasExtraPage = false,
                hasNextChapter = true,
                isErrorPage = false,
            ),
        )
    }

    @Test
    fun `an intermediate page is never a completion regardless of chapter position`() {
        assertEquals(
            false,
            LatestChapterCompletionPolicy.isGenuineLatestChapterCompletion(
                pageIndex = 3,
                lastPageIndex = 9,
                hasExtraPage = false,
                hasNextChapter = false,
                isErrorPage = false,
            ),
        )
    }

    @Test
    fun `an extra (double) page at the second-to-last index still counts as reaching the end`() {
        assertEquals(
            true,
            LatestChapterCompletionPolicy.isGenuineLatestChapterCompletion(
                pageIndex = 8,
                lastPageIndex = 9,
                hasExtraPage = true,
                hasNextChapter = false,
                isErrorPage = false,
            ),
        )
    }

    @Test
    fun `without hasExtraPage the second-to-last index is not a completion`() {
        assertEquals(
            false,
            LatestChapterCompletionPolicy.isGenuineLatestChapterCompletion(
                pageIndex = 8,
                lastPageIndex = 9,
                hasExtraPage = false,
                hasNextChapter = false,
                isErrorPage = false,
            ),
        )
    }

    @Test
    fun `an error page is never a genuine completion even at the last index`() {
        assertEquals(
            false,
            LatestChapterCompletionPolicy.isGenuineLatestChapterCompletion(
                pageIndex = 9,
                lastPageIndex = 9,
                hasExtraPage = false,
                hasNextChapter = false,
                isErrorPage = true,
            ),
        )
    }

    @Test
    fun `unloaded pages (null lastPageIndex) are never a genuine completion`() {
        assertEquals(
            false,
            LatestChapterCompletionPolicy.isGenuineLatestChapterCompletion(
                pageIndex = 0,
                lastPageIndex = null,
                hasExtraPage = false,
                hasNextChapter = false,
                isErrorPage = false,
            ),
        )
    }

    @Test
    fun `a single-page latest chapter completes immediately on its only page`() {
        assertEquals(
            true,
            LatestChapterCompletionPolicy.isGenuineLatestChapterCompletion(
                pageIndex = 0,
                lastPageIndex = 0,
                hasExtraPage = false,
                hasNextChapter = false,
                isErrorPage = false,
            ),
        )
    }
}
// KMK <--

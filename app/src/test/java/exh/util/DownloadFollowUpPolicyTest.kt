package exh.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

// KMK -->
/**
 * Direct tests for the pure [DownloadFollowUpPolicy.evaluate] decision -- mirrors
 * [MigrationFollowUpPolicyTest]'s own shape: no mocking needed, every branch is a plain input
 * combination.
 */
class DownloadFollowUpPolicyTest {

    private fun manga(id: Long = 1L) = Manga.create().copy(id = id, source = 1L, url = "/m", favorite = false)

    private fun chapter(id: Long) = Chapter.create().copy(id = id, mangaId = 1L)

    @Test
    fun `offers a follow-up with the resolved chapters when manga exists, source installed, and chapters still exist`() {
        val chapters = listOf(chapter(10L), chapter(11L))

        val result = DownloadFollowUpPolicy.evaluate(
            manga = manga(),
            sourceInstalled = true,
            resolvedChapters = chapters,
        )

        assertEquals(DownloadFollowUpPolicy.RedownloadFollowUp.Offered(chapters), result)
    }

    @Test
    fun `refuses when the manga row no longer exists`() {
        val result = DownloadFollowUpPolicy.evaluate(
            manga = null,
            sourceInstalled = true,
            resolvedChapters = listOf(chapter(10L)),
        )

        assertEquals(
            DownloadFollowUpPolicy.RedownloadFollowUp.Unavailable(DownloadFollowUpPolicy.RedownloadFollowUp.Reason.MANGA_NOT_FOUND),
            result,
        )
    }

    @Test
    fun `refuses when the source is not installed`() {
        val result = DownloadFollowUpPolicy.evaluate(
            manga = manga(),
            sourceInstalled = false,
            resolvedChapters = listOf(chapter(10L)),
        )

        assertEquals(
            DownloadFollowUpPolicy.RedownloadFollowUp.Unavailable(DownloadFollowUpPolicy.RedownloadFollowUp.Reason.SOURCE_NOT_INSTALLED),
            result,
        )
    }

    @Test
    fun `refuses when none of the originally-deleted chapter ids still resolve`() {
        val result = DownloadFollowUpPolicy.evaluate(
            manga = manga(),
            sourceInstalled = true,
            resolvedChapters = emptyList(),
        )

        assertEquals(
            DownloadFollowUpPolicy.RedownloadFollowUp.Unavailable(DownloadFollowUpPolicy.RedownloadFollowUp.Reason.NO_CHAPTERS_STILL_EXIST),
            result,
        )
    }

    @Test
    fun `checks are evaluated in order -- a missing manga wins over missing chapters`() {
        val result = DownloadFollowUpPolicy.evaluate(
            manga = null,
            sourceInstalled = false,
            resolvedChapters = emptyList(),
        )

        assertEquals(
            DownloadFollowUpPolicy.RedownloadFollowUp.Unavailable(DownloadFollowUpPolicy.RedownloadFollowUp.Reason.MANGA_NOT_FOUND),
            result,
        )
    }

    @Test
    fun `offers a follow-up even when only some of the originally-deleted chapters still resolve`() {
        val partiallyResolved = listOf(chapter(10L))

        val result = DownloadFollowUpPolicy.evaluate(
            manga = manga(),
            sourceInstalled = true,
            resolvedChapters = partiallyResolved,
        )

        assertEquals(DownloadFollowUpPolicy.RedownloadFollowUp.Offered(partiallyResolved), result)
    }
}
// KMK <--

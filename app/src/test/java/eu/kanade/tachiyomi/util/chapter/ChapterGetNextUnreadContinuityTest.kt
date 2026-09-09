package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.manga.ChapterList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterLinePreference

class ChapterGetNextUnreadContinuityTest {

    private fun item(
        id: Long,
        number: Double,
        read: Boolean = false,
        lastPageRead: Long = 0L,
        scanlator: String? = null,
        url: String = "chapter-$id",
    ) = ChapterList.Item(
        chapter = Chapter.create().copy(
            id = id,
            mangaId = 1L,
            chapterNumber = number,
            scanlator = scanlator,
            url = url,
            name = "Chapter $number ($id)",
            read = read,
            lastPageRead = lastPageRead,
        ),
        downloadState = Download.State.NOT_DOWNLOADED,
        downloadProgress = 0,
        sourceName = "Fixture source",
        showScanlator = true,
    )

    @Test
    fun `enabled duplicate skipping does not reopen an unread same-number variant`() {
        val chapters = listOf(
            item(101L, 1.0, read = true),
            item(102L, 1.0),
            item(103L, 2.0),
        )

        assertEquals(103L, ChapterLineContinuityPolicy.collapseItems(chapters).last().chapter.id)
    }

    @Test
    fun `disabled duplicate skipping preserves intentional same-number line changes`() {
        val chapters = listOf(
            item(101L, 1.0, read = true),
            item(102L, 1.0),
            item(103L, 2.0),
        )

        assertEquals(102L, chapters[1].chapter.id)
    }

    @Test
    fun `enabled duplicate skipping keeps the furthest in-progress same-number variant`() {
        val chapters = listOf(
            item(201L, 3.0, lastPageRead = 2L),
            item(202L, 3.0, lastPageRead = 8L),
            item(203L, 4.0),
        )

        assertEquals(202L, ChapterLineContinuityPolicy.collapseItems(chapters).first().chapter.id)
    }

    @Test
    fun `enabled duplicate skipping remains deterministic for unread-only variants`() {
        val chapters = listOf(
            item(301L, 5.0),
            item(302L, 5.0),
            item(303L, 6.0),
        )

        assertEquals(301L, ChapterLineContinuityPolicy.collapseItems(chapters).first().chapter.id)
    }

    @Test
    fun `descending order still skips a completed same-number line`() {
        val chapters = listOf(
            item(401L, 2.0),
            item(402L, 1.0),
            item(403L, 1.0, read = true),
        )

        assertEquals(401L, ChapterLineContinuityPolicy.collapseItems(chapters).first().chapter.id)
    }

    @Test
    fun `confirmed scanlator preference selects the same line across chapter numbers`() {
        val chapters = listOf(
            item(501L, 1.0),
            item(502L, 1.0),
            item(503L, 2.0),
        ).mapIndexed { index, chapter ->
            chapter.copy(
                chapter = chapter.chapter.copy(scanlator = if (index == 1) " Flame  Scans " else "Other"),
            )
        }
        val preference = ChapterLinePreference(
            mangaId = 1L,
            sourceId = 42L,
            preferredScanlator = "flame scans",
            anchorChapterUrl = "/chapter-1",
            anchorChapterNumber = 1.0,
            confirmedAt = 1_000,
            updatedAt = 1_000,
        )

        assertEquals(502L, ChapterLineContinuityPolicy.collapseItems(chapters, preference).first().chapter.id)
    }

    @Test
    fun `ambiguous preference without scanlator only matches its anchor URL`() {
        val chapters = listOf(
            item(601L, 1.0),
            item(602L, 1.0),
        ).mapIndexed { index, chapter ->
            chapter.copy(chapter = chapter.chapter.copy(url = if (index == 0) "/anchor" else "/other"))
        }
        val preference = ChapterLinePreference(
            mangaId = 1L,
            sourceId = 42L,
            preferredScanlator = null,
            anchorChapterUrl = "/anchor",
            anchorChapterNumber = 1.0,
            confirmedAt = 1_000,
            updatedAt = 1_000,
        )

        assertEquals(601L, ChapterLineContinuityPolicy.collapseItems(chapters, preference).first().chapter.id)
    }

    @Test
    fun `refresh with a deleted anchor falls back without selecting another line`() {
        val refreshed = listOf(
            item(701L, 1.0, scanlator = "Other", url = "replacement"),
            item(702L, 1.0, scanlator = "Different", url = "different"),
        )
        val preference = ChapterLinePreference(
            mangaId = 1L,
            sourceId = 42L,
            preferredScanlator = null,
            anchorChapterUrl = "deleted-anchor",
            anchorChapterNumber = 1.0,
            confirmedAt = 1_000,
            updatedAt = 1_000,
        )

        assertEquals(701L, ChapterLineContinuityPolicy.collapseItems(refreshed, preference).first().chapter.id)
        assertEquals("replacement", refreshed.first().chapter.url)
        assertEquals("different", refreshed[1].chapter.url)
    }

    @Test
    fun `reset represented by absent preference restores deterministic legacy behavior`() {
        val chapters = listOf(
            item(801L, 1.0, scanlator = "Other"),
            item(802L, 1.0, scanlator = "Flame Scans"),
        )

        assertEquals(801L, ChapterLineContinuityPolicy.collapseItems(chapters).first().chapter.id)
        assertEquals(801L, ChapterLineContinuityPolicy.collapseItems(chapters, null).first().chapter.id)
    }

    @Test
    fun `preference collapse never mutates read or page state`() {
        val chapters = listOf(
            item(901L, 1.0, read = true, lastPageRead = 4L, scanlator = "Other"),
            item(902L, 1.0, read = false, lastPageRead = 2L, scanlator = "Flame Scans"),
        )
        val before = chapters.map { it.chapter.id to (it.chapter.read to it.chapter.lastPageRead) }
        val preference = ChapterLinePreference(
            mangaId = 1L,
            sourceId = 42L,
            preferredScanlator = "flame scans",
            anchorChapterUrl = "chapter-902",
            anchorChapterNumber = 1.0,
            confirmedAt = 1_000,
            updatedAt = 1_000,
        )

        ChapterLineContinuityPolicy.collapseItems(chapters, preference)

        assertEquals(before, chapters.map { it.chapter.id to (it.chapter.read to it.chapter.lastPageRead) })
    }
}

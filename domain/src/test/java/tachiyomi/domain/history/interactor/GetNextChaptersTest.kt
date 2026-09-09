package tachiyomi.domain.history.interactor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

class GetNextChaptersTest {

    @Test
    fun `missing history chapter keeps the first available chapter`() {
        val chapters = listOf(chapter(1L, read = false), chapter(2L, read = false))

        assertEquals(chapters, selectNextChapters(chapters, fromChapterId = 99L, onlyUnread = false))
    }

    @Test
    fun `read history chapter resumes with the following chapter`() {
        val chapters = listOf(chapter(1L, read = true), chapter(2L, read = false))

        assertEquals(listOf(chapters[1]), selectNextChapters(chapters, fromChapterId = 1L, onlyUnread = false))
    }

    private fun chapter(id: Long, read: Boolean) = Chapter.create().copy(
        id = id,
        mangaId = 1L,
        read = read,
        chapterNumber = id.toDouble(),
        url = "/chapter/$id",
        name = "Chapter $id",
    )
}

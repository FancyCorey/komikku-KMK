package eu.kanade.tachiyomi.util.chapter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

class ChapterRemoveDuplicatesContinuityTest {

    private fun chapter(
        id: Long,
        number: Double,
        scanlator: String? = null,
    ) = Chapter.create().copy(
        id = id,
        mangaId = 1L,
        chapterNumber = number,
        name = "Chapter $number ($id)",
        scanlator = scanlator,
    )

    @Test
    fun `keeps the currently open line when its chapter number has variants`() {
        val current = chapter(2L, 1.0, "line-b")
        val chapters = listOf(
            chapter(1L, 1.0, "line-a"),
            current,
            chapter(3L, 2.0, "line-a"),
        )

        assertEquals(listOf(2L, 3L), chapters.removeDuplicates(current).map { it.id })
    }

    @Test
    fun `falls back to the matching scanlator when the preferred line ends`() {
        val current = chapter(2L, 1.0, "line-b")
        val chapters = listOf(
            chapter(1L, 1.0, "line-a"),
            chapter(3L, 2.0, "line-a"),
        )

        assertEquals(listOf(1L, 3L), chapters.removeDuplicates(current).map { it.id })
    }

    @Test
    fun `falls back deterministically to the first variant when identity is unavailable`() {
        val current = chapter(99L, 1.0, "line-z")
        val chapters = listOf(
            chapter(1L, 1.0, "line-a"),
            chapter(2L, 1.0, "line-b"),
            chapter(3L, 2.0, "line-c"),
        )

        assertEquals(listOf(1L, 3L), chapters.removeDuplicates(current).map { it.id })
    }
}

package eu.kanade.tachiyomi.ui.reader.bridge

import eu.kanade.tachiyomi.source.model.SChapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AlternateSourceReaderChapterPolicyTest {

    @Test
    fun `chapter labels resolve missing source numbers before sorting`() {
        val chapters = AlternateSourceReaderChapterPolicy.normalizeAndSort(
            mangaTitle = "Resurrection Boy",
            chapters = listOf(
                chapter("/c/12", "Ch.012", -1f),
                chapter("/c/10", "Ch.010", -1f),
            ),
        )

        assertEquals(listOf(12f, 10f), chapters.map { it.chapter_number })
    }

    @Test
    fun `only duplicate URLs are removed and same-number variants remain`() {
        val chapters = AlternateSourceReaderChapterPolicy.normalizeAndSort(
            mangaTitle = "Resurrection Boy",
            chapters = listOf(
                chapter("/c/12", "Chapter 12 - scan A", 12f),
                chapter("/c/12", "Chapter 12 - duplicate URL", 12f),
                chapter("/c/12-b", "Chapter 12 - scan B", 12f),
            ),
        )

        assertEquals(listOf("/c/12", "/c/12-b"), chapters.map { it.url })
    }

    @Test
    fun `ordering is deterministic for equal chapter numbers`() {
        val chapters = AlternateSourceReaderChapterPolicy.normalizeAndSort(
            mangaTitle = "Resurrection Boy",
            chapters = listOf(
                chapter("/c/b", "Chapter 12 - B", 12f),
                chapter("/c/a", "Chapter 12 - A", 12f),
                chapter("/c/13", "Chapter 13", 13f),
            ),
        )

        assertEquals(listOf("/c/13", "/c/a", "/c/b"), chapters.map { it.url })
    }

    @Test
    fun `unresolvable nonfinite chapter metadata is excluded`() {
        val chapters = AlternateSourceReaderChapterPolicy.normalizeAndSort(
            mangaTitle = "Resurrection Boy",
            chapters = listOf(
                chapter("/c/unknown", "Bonus", Float.NaN),
                chapter("/c/valid", "Chapter 3", 3f),
            ),
        )

        assertEquals(listOf("/c/valid"), chapters.map { it.url })
    }

    private fun chapter(url: String, name: String, number: Float): SChapter = SChapter.create().also {
        it.url = url
        it.name = name
        it.chapter_number = number
    }
}

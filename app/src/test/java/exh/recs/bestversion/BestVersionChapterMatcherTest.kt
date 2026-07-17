package exh.recs.bestversion

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SChapterImpl
import kotlinx.serialization.json.JsonObject
import mihon.core.common.extensions.EMPTY
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

// KMK --> v0.7.8
class BestVersionChapterMatcherTest {

    private fun makeChapter(number: Float, name: String = "Chapter $number"): SChapter {
        return SChapterImpl().apply {
            this.name = name
            this.chapter_number = number
            this.url = "/chapter/$number"
            this.date_upload = 0L
            this.scanlator = null
        }
    }

    private fun makeDomainChapter(
        number: Double,
        read: Boolean = false,
        lastPageRead: Long = 0,
    ): Chapter {
        return Chapter(
            id = number.toLong(),
            mangaId = 1L,
            read = read,
            bookmark = false,
            lastPageRead = lastPageRead,
            dateFetch = 0L,
            sourceOrder = number.toLong(),
            url = "/ch/$number",
            name = "Chapter $number",
            dateUpload = 0L,
            chapterNumber = number,
            scanlator = null,
            lastModifiedAt = 0L,
            version = 1L,
            memo = JsonObject.EMPTY,
        )
    }

    // --- findMatch ---

    @Test
    fun `findMatch returns null for empty list`() {
        assertNull(BestVersionChapterMatcher.findMatch(5.0, emptyList()))
    }

    @Test
    fun `findMatch returns exact chapter number match`() {
        val chapters = listOf(makeChapter(3f), makeChapter(5f), makeChapter(7f))
        val match = BestVersionChapterMatcher.findMatch(5.0, chapters)
        assertEquals(5f, match?.chapter_number)
    }

    @Test
    fun `findMatch returns closest chapter within tolerance`() {
        val chapters = listOf(makeChapter(4.9f), makeChapter(7f))
        val match = BestVersionChapterMatcher.findMatch(5.0, chapters)
        assertEquals(4.9f, match?.chapter_number)
    }

    @Test
    fun `findMatch returns null when closest is more than 1 away`() {
        val chapters = listOf(makeChapter(3f), makeChapter(7f))
        val match = BestVersionChapterMatcher.findMatch(5.0, chapters)
        // Closest is 3f (distance 2.0) or 7f (distance 2.0), both >1.0 — returns null
        assertNull(match)
    }

    @Test
    fun `findMatch works with single chapter`() {
        val chapters = listOf(makeChapter(1f))
        val match = BestVersionChapterMatcher.findMatch(1.0, chapters)
        assertEquals(1f, match?.chapter_number)
    }

    // --- selectDefaultChapter ---

    @Test
    fun `selectDefaultChapter returns null for empty list`() {
        assertNull(BestVersionChapterMatcher.selectDefaultChapter(emptyList()))
    }

    @Test
    fun `selectDefaultChapter returns in-progress chapter first`() {
        val chapters = listOf(
            makeDomainChapter(1.0, read = true),
            makeDomainChapter(5.0, read = false, lastPageRead = 12),
            makeDomainChapter(10.0, read = false),
        )
        val result = BestVersionChapterMatcher.selectDefaultChapter(chapters)
        assertEquals(5.0, result?.chapterNumber)
    }

    @Test
    fun `selectDefaultChapter picks latest read chapter when no in-progress`() {
        val chapters = listOf(
            makeDomainChapter(1.0, read = true),
            makeDomainChapter(5.0, read = true),
            makeDomainChapter(10.0, read = false),
        )
        val result = BestVersionChapterMatcher.selectDefaultChapter(chapters)
        assertEquals(5.0, result?.chapterNumber)
    }

    @Test
    fun `selectDefaultChapter falls back to latest chapter when none read`() {
        val chapters = listOf(
            makeDomainChapter(1.0),
            makeDomainChapter(5.0),
            makeDomainChapter(10.0),
        )
        val result = BestVersionChapterMatcher.selectDefaultChapter(chapters)
        assertEquals(10.0, result?.chapterNumber)
    }

    @Test
    fun `selectDefaultChapter returns single chapter`() {
        val chapters = listOf(makeDomainChapter(3.0))
        val result = BestVersionChapterMatcher.selectDefaultChapter(chapters)
        assertEquals(3.0, result?.chapterNumber)
    }
}
// KMK <--

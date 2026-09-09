package exh.recs.bestversion

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SChapterImpl
import kotlinx.serialization.json.JsonObject
import mihon.core.common.extensions.EMPTY
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
        assertTrue(match is BestVersionChapterMatcher.ChapterMatchResult.Exact)
        assertEquals(5f, match?.chapter?.chapter_number)
    }

    @Test
    fun `findMatch prefers exact chapter over a nearer listed candidate`() {
        val chapters = listOf(makeChapter(5.01f), makeChapter(5f))
        val match = BestVersionChapterMatcher.findMatch(5.0, chapters)
        assertTrue(match is BestVersionChapterMatcher.ChapterMatchResult.Exact)
        assertEquals(5f, match?.chapter?.chapter_number)
    }

    @Test
    fun `findMatch resolves a chapter number from the source label when metadata is sentinel`() {
        val chapters = listOf(
            makeChapter(-1f, "Ch.011"),
            makeChapter(-1f, "Ch.012"),
        )
        val match = BestVersionChapterMatcher.findMatch(12.0, chapters, "Resurrection Boy")
        assertTrue(match is BestVersionChapterMatcher.ChapterMatchResult.Exact)
        assertEquals("Ch.012", match?.chapter?.name)
        assertEquals(12f, match?.chapter?.chapter_number)
    }

    @Test
    fun `findMatch resolves a chapter label when metadata is nonfinite`() {
        val chapters = listOf(
            makeChapter(Float.NaN, "Chapter 12"),
            makeChapter(13f),
        )

        val match = BestVersionChapterMatcher.findMatch(12.0, chapters, "Resurrection Boy")

        assertTrue(match is BestVersionChapterMatcher.ChapterMatchResult.Exact)
        assertEquals("Chapter 12", match?.chapter?.name)
        assertEquals(12f, match?.chapter?.chapter_number)
    }

    @Test
    fun `title-aware normalization preserves a special origin target instead of selecting latest`() {
        val chapters = listOf(
            makeChapter(-1f, "Prologue"),
            makeChapter(-1f, "Resurrection Boy Ch. 12"),
        )
        val match = BestVersionChapterMatcher.findMatch(-1.0, chapters, "Resurrection Boy")

        assertTrue(match is BestVersionChapterMatcher.ChapterMatchResult.Exact)
        assertEquals("Prologue", match?.chapter?.name)
        assertEquals(-1f, match?.chapter?.chapter_number)
    }

    @Test
    fun `selectLatestCandidate resolves sentinel labels before selecting latest chapter`() {
        val chapters = listOf(
            makeChapter(-1f, "Ch.011"),
            makeChapter(-1f, "Ch.017"),
        )

        val selected = BestVersionChapterMatcher.selectLatestCandidate(chapters, "Resurrection Boy")

        assertEquals("Ch.017", selected?.name)
        assertEquals(17f, selected?.chapter_number)
    }

    @Test
    fun `normalizeChapter fixes sentinel metadata for a manually selected labelled chapter`() {
        val selected = BestVersionChapterMatcher.normalizeChapter(
            makeChapter(-1f, "Ch.012"),
            "Resurrection Boy",
        )

        assertEquals("Ch.012", selected.name)
        assertEquals(12f, selected.chapter_number)
    }

    @Test
    fun `findMatch uses resolved label numbers for nearest disclosure`() {
        val chapters = listOf(
            makeChapter(-1f, "Chapter 12"),
            makeChapter(-1f, "Chapter 15"),
        )
        val match = BestVersionChapterMatcher.findMatch(13.0, chapters, "Resurrection Boy")
        assertTrue(match is BestVersionChapterMatcher.ChapterMatchResult.Nearest)
        val nearest = match as BestVersionChapterMatcher.ChapterMatchResult.Nearest
        assertEquals("Chapter 12", nearest.chapter.name)
        assertEquals(12f, nearest.chapter.chapter_number)
        assertEquals(12.0, nearest.candidateChapterNumber)
    }

    @Test
    fun `findMatch returns closest chapter as disclosed Nearest when outside exact tolerance`() {
        val chapters = listOf(makeChapter(4.9f), makeChapter(7f))
        val match = BestVersionChapterMatcher.findMatch(5.0, chapters)
        // 4.9 is not within the strict +/-0.01 exact-match tolerance, so it is reported as a disclosed
        // Nearest match -- never silently presented as if it were chapter 5 itself.
        assertTrue(match is BestVersionChapterMatcher.ChapterMatchResult.Nearest)
        assertEquals(4.9f, match?.chapter?.chapter_number)
        val nearest = match as BestVersionChapterMatcher.ChapterMatchResult.Nearest
        assertEquals(5.0, nearest.originChapterNumber)
        assertEquals(4.9, nearest.candidateChapterNumber, 0.0001)
    }

    // KMK R2-AUG-05-ACTUAL-BEST-VERSION-FAILURE-PATH: this used to assert null (the old +/-1.0 cap on
    // the fallback tier). That was the confirmed false-"unavailable" defect -- a candidate source with
    // readable chapters (3f, 7f here) that simply don't happen to be numerically close to the origin's
    // chapter number must never be reported as Unavailable. It must fall back to the nearest readable
    // chapter, disclosed as Nearest (never presented as equivalent to the origin chapter).
    @Test
    fun `findMatch falls back to nearest readable chapter beyond the old +-1 cap, disclosed as Nearest`() {
        val chapters = listOf(makeChapter(3f), makeChapter(7f))
        val match = BestVersionChapterMatcher.findMatch(5.0, chapters)
        assertTrue(match is BestVersionChapterMatcher.ChapterMatchResult.Nearest)
        val nearest = match as BestVersionChapterMatcher.ChapterMatchResult.Nearest
        // Both 3f and 7f are distance 2.0 away -- comparator tiebreaks by chapter_number then url, so
        // the lower chapter number (3f) wins deterministically.
        assertEquals(3f, nearest.chapter.chapter_number)
        assertEquals(5.0, nearest.originChapterNumber)
        assertEquals(3.0, nearest.candidateChapterNumber, 0.0001)
    }

    @Test
    fun `findMatch falls back to nearest readable chapter far beyond old cap (renumbered source)`() {
        // Origin at chapter 500, candidate source only has chapters 1-50 (restarted numbering) -- the
        // exact false-"unavailable" scenario from the R2-AUG-05 audit.
        val chapters = (1..50).map { makeChapter(it.toFloat()) }
        val match = BestVersionChapterMatcher.findMatch(500.0, chapters)
        assertTrue(match is BestVersionChapterMatcher.ChapterMatchResult.Nearest)
        val nearest = match as BestVersionChapterMatcher.ChapterMatchResult.Nearest
        assertEquals(50f, nearest.chapter.chapter_number)
        assertEquals(500.0, nearest.originChapterNumber)
        assertEquals(50.0, nearest.candidateChapterNumber, 0.0001)
    }

    // KMK R2-AUG-05: the fallback only ever applies when the candidate source has SOME readable
    // (finite chapter-number) chapter -- an empty/missing candidate chapter list must still correctly
    // yield null (caller maps this to CandidateChapterState.Unavailable), never a bogus fallback match.
    @Test
    fun `findMatch still returns null when candidate list is empty (genuinely unavailable)`() {
        assertNull(BestVersionChapterMatcher.findMatch(500.0, emptyList()))
    }

    @Test
    fun `findMatch still returns null when candidate list has no finite chapter numbers`() {
        val chapters = listOf(makeChapter(Float.NaN), makeChapter(Float.POSITIVE_INFINITY))
        assertNull(BestVersionChapterMatcher.findMatch(5.0, chapters))
    }

    @Test
    fun `findMatch works with single chapter`() {
        val chapters = listOf(makeChapter(1f))
        val match = BestVersionChapterMatcher.findMatch(1.0, chapters)
        assertTrue(match is BestVersionChapterMatcher.ChapterMatchResult.Exact)
        assertEquals(1f, match?.chapter?.chapter_number)
    }

    @Test
    fun `findMatch ignores non-finite candidate chapter numbers`() {
        val chapters = listOf(makeChapter(Float.NaN), makeChapter(Float.POSITIVE_INFINITY), makeChapter(5f))
        val match = BestVersionChapterMatcher.findMatch(5.0, chapters)
        assertTrue(match is BestVersionChapterMatcher.ChapterMatchResult.Exact)
        assertEquals(5f, match?.chapter?.chapter_number)
    }

    @Test
    fun `findMatch rejects non-finite target chapter number`() {
        val chapters = listOf(makeChapter(5f))
        assertNull(BestVersionChapterMatcher.findMatch(Double.NaN, chapters))
        assertNull(BestVersionChapterMatcher.findMatch(Double.POSITIVE_INFINITY, chapters))
    }

    // KMK R2-AUG-05: malformed numbering (negative chapter numbers) must still be handled safely --
    // a negative but finite chapter number is a legitimate (if unusual) chapter number and must be
    // matched/falled-back-to like any other finite number, never crash or silently drop.
    @Test
    fun `findMatch handles negative chapter numbers safely`() {
        val chapters = listOf(makeChapter(-1f), makeChapter(-5f))
        val match = BestVersionChapterMatcher.findMatch(-1.0, chapters)
        assertTrue(match is BestVersionChapterMatcher.ChapterMatchResult.Exact)
        assertEquals(-1f, match?.chapter?.chapter_number)
    }

    @Test
    fun `findMatch falls back safely when target is negative and only positive chapters exist`() {
        val chapters = listOf(makeChapter(1f), makeChapter(2f))
        val match = BestVersionChapterMatcher.findMatch(-100.0, chapters)
        assertTrue(match is BestVersionChapterMatcher.ChapterMatchResult.Nearest)
        val nearest = match as BestVersionChapterMatcher.ChapterMatchResult.Nearest
        assertEquals(1f, nearest.chapter.chapter_number)
    }

    // --- C2 (HR-2026-08-26-RATED-COLLECTIONS-AND-BEST-VERSION-CORRECTIONS, E3) re-audit: chapter 0,
    // decimals, specials/prologues, duplicates, missing numbers, scanlator variants, source-order
    // differences. Each proves the existing comparator (distance, then chapter_number, then url)
    // already behaves correctly and deterministically for these cases -- no defect was found in
    // findMatch itself; this is the required re-audit evidence, not a behavior change.

    @Test
    fun `findMatch treats chapter 0 as an ordinary exact match, not a missing-number sentinel`() {
        val chapters = listOf(makeChapter(0f), makeChapter(1f), makeChapter(2f))
        val match = BestVersionChapterMatcher.findMatch(0.0, chapters)
        assertTrue(match is BestVersionChapterMatcher.ChapterMatchResult.Exact)
        assertEquals(0f, match?.chapter?.chapter_number)
    }

    @Test
    fun `findMatch matches a decimal sub-chapter (e_g_ 1_1) exactly, not as Nearest to 1 or 2`() {
        val chapters = listOf(makeChapter(1f), makeChapter(1.1f), makeChapter(2f))
        val match = BestVersionChapterMatcher.findMatch(1.1, chapters)
        assertTrue(match is BestVersionChapterMatcher.ChapterMatchResult.Exact)
        assertEquals(1.1f, match?.chapter?.chapter_number)
    }

    // KMK: Tachiyomi/Mihon's own convention represents specials and prologues as a negative
    // chapter_number (most commonly -1) so they sort before chapter 1 without colliding with a real
    // "chapter 0". findMatch has no special-case branch for this -- it is simply another finite
    // double, matched by the same distance/tiebreak comparator as every other chapter, which is the
    // correct behavior: a special/prologue must be reachable as an Exact match when the origin is
    // itself a special (both use the same sentinel), and as an ordinary (disclosed) Nearest fallback
    // otherwise, never silently coerced to chapter 0 or dropped.
    @Test
    fun `findMatch treats a special-prologue sentinel chapter (-1) as an ordinary exact match against another special`() {
        val chapters = listOf(makeChapter(-1f, name = "Prologue"), makeChapter(1f), makeChapter(2f))
        val match = BestVersionChapterMatcher.findMatch(-1.0, chapters)
        assertTrue(match is BestVersionChapterMatcher.ChapterMatchResult.Exact)
        assertEquals(-1f, match?.chapter?.chapter_number)
        assertEquals("Prologue", match?.chapter?.name)
    }

    @Test
    fun `findMatch falls back to the disclosed Nearest special when the origin chapter has no special counterpart`() {
        val chapters = listOf(makeChapter(-1f, name = "Prologue"), makeChapter(50f))
        val match = BestVersionChapterMatcher.findMatch(25.0, chapters)
        assertTrue(match is BestVersionChapterMatcher.ChapterMatchResult.Nearest)
        // -1 is 26 away, 50 is 25 away -- 50 must win on genuine distance, not the special sentinel.
        assertEquals(50f, (match as BestVersionChapterMatcher.ChapterMatchResult.Nearest).chapter.chapter_number)
    }

    // KMK: two candidate rows sharing the exact same chapter_number is a real, observed shape (two
    // scanlator groups' releases of the same chapter, or a source that lists a chapter twice with
    // different urls). findMatch's comparator already tiebreaks deterministically by chapter_number
    // then url -- proven here so a genuine duplicate can never make the match non-deterministic
    // (flip-flopping between runs) or crash minWithOrNull.
    @Test
    fun `findMatch resolves a duplicate chapter number (two scanlator variants) deterministically by url`() {
        val officialRelease = SChapterImpl().apply {
            name = "Chapter 5 (Official)"
            chapter_number = 5f
            url = "/official/chapter-5"
            scanlator = "OfficialScans"
        }
        val fanRelease = SChapterImpl().apply {
            name = "Chapter 5 (Fan TL)"
            chapter_number = 5f
            url = "/fanscans/chapter-5"
            scanlator = "FanScans"
        }
        val chapters = listOf(fanRelease, officialRelease)
        val match = BestVersionChapterMatcher.findMatch(5.0, chapters)
        assertTrue(match is BestVersionChapterMatcher.ChapterMatchResult.Exact)
        // Both are distance 0 from the target and share chapter_number -- the comparator's final
        // tiebreak (url) must deterministically pick the same one every time, not whichever the
        // source happened to list first.
        assertEquals("/fanscans/chapter-5", match?.chapter?.url, "url \"/fanscans/...\" sorts before \"/official/...\"")

        val reversedOrderChapters = listOf(officialRelease, fanRelease)
        val matchReversed = BestVersionChapterMatcher.findMatch(5.0, reversedOrderChapters)
        assertEquals(
            match?.chapter?.url,
            matchReversed?.chapter?.url,
            "source list order (source-order differences) must never change which duplicate is selected",
        )
    }

    @Test
    fun `findMatch ignores a chapter with a missing (non-finite) number even when duplicated alongside a valid one`() {
        val missingNumber = makeChapter(Float.NaN, name = "Chapter (unnumbered)")
        val valid = makeChapter(5f)
        val match = BestVersionChapterMatcher.findMatch(5.0, listOf(missingNumber, valid))
        assertTrue(match is BestVersionChapterMatcher.ChapterMatchResult.Exact)
        assertEquals(5f, match?.chapter?.chapter_number)
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

    @Test
    fun `selectDefaultChapter ignores unrecognized sentinel when a numbered chapter exists`() {
        val chapters = listOf(
            makeDomainChapter(-1.0, read = false, lastPageRead = 99),
            makeDomainChapter(12.0),
        )
        val result = BestVersionChapterMatcher.selectDefaultChapter(chapters)
        assertEquals(12.0, result?.chapterNumber)
    }

    @Test
    fun `selectDefaultChapter preserves unrecognized chapter when no numbered chapter exists`() {
        val result = BestVersionChapterMatcher.selectDefaultChapter(
            listOf(makeDomainChapter(-1.0, read = true)),
        )
        assertEquals(-1.0, result?.chapterNumber)
    }
}
// KMK <--

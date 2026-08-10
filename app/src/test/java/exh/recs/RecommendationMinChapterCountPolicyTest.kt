package exh.recs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.model.RatedMangaVisibility

// KMK_CLAUDE_LATEST_CATALOGUE_AND_EXPOSURE_PLAN_2026-08-08 -->
/**
 * Conformance tests for [RecommendationMinChapterCountPolicy] and for the existing minimum-chapter
 * *behavior contract* it now guards, per the implementation packet's Step 5A prerequisite.
 *
 * The behavior half of these tests deliberately exercises the real
 * [RecommendationCandidateVisibilityPolicy] rather than restating its rules, so the documented
 * contract -- below-threshold hidden, at-or-above visible, unknown fails open, lookup failure fails
 * open -- is proven against the actual shared policy the pipeline uses.
 */
class RecommendationMinChapterCountPolicyTest {

    // --- Supported values / default / malformed resolution ---

    @Test
    fun `supported values are exactly Off, 5, 10, 20 and 50 in display order`() {
        assertEquals(listOf(0, 5, 10, 20, 50), RecommendationMinChapterCountPolicy.SUPPORTED_VALUES)
    }

    @Test
    fun `the default is Off, matching the existing product contract`() {
        assertEquals(0, RecommendationMinChapterCountPolicy.DEFAULT)
        assertEquals(RecommendationMinChapterCountPolicy.OFF, RecommendationMinChapterCountPolicy.DEFAULT)
    }

    @Test
    fun `every supported value resolves to itself`() {
        RecommendationMinChapterCountPolicy.SUPPORTED_VALUES.forEach { value ->
            assertEquals(value, RecommendationMinChapterCountPolicy.resolve(value), "$value must resolve to itself")
        }
    }

    @Test
    fun `negative, unsupported and oversized persisted values resolve to the safe default`() {
        // A malformed value must never appear as an unformatted raw number in the UI and must never
        // silently filter at a threshold the picker never offered.
        listOf(-100, -1, 1, 3, 7, 11, 19, 21, 49, 51, 1000, Int.MAX_VALUE, Int.MIN_VALUE).forEach { value ->
            assertEquals(
                RecommendationMinChapterCountPolicy.DEFAULT,
                RecommendationMinChapterCountPolicy.resolve(value),
                "unsupported value $value must resolve to the default",
            )
        }
    }

    @Test
    fun `isFilterActive is false for Off and for every malformed value`() {
        assertFalse(RecommendationMinChapterCountPolicy.isFilterActive(0))
        assertFalse(RecommendationMinChapterCountPolicy.isFilterActive(-5))
        assertFalse(RecommendationMinChapterCountPolicy.isFilterActive(7))
        assertFalse(RecommendationMinChapterCountPolicy.isFilterActive(Int.MAX_VALUE))
    }

    @Test
    fun `isFilterActive is true for every supported non-Off value`() {
        listOf(5, 10, 20, 50).forEach { assertTrue(RecommendationMinChapterCountPolicy.isFilterActive(it)) }
    }

    // --- Behavior contract, proven against the real shared visibility policy ---

    private fun manga(id: Long) = Manga.create().copy(id = id, source = 1L, url = "/m/$id", ogTitle = "M$id")

    private fun visibility(mangaId: Long, threshold: Int, chapterCounts: Map<Long, Long>) =
        RecommendationCandidateVisibilityPolicy.evaluate(
            manga = manga(mangaId),
            tasteByKey = emptyMap(),
            visibility = RatedMangaVisibility.SHOW_ALL_RATED,
            seenKeys = emptySet(),
            knownIds = emptySet(),
            minChapterCount = RecommendationMinChapterCountPolicy.resolve(threshold),
            chapterCounts = chapterCounts,
        )

    @Test
    fun `a known count below the threshold is hidden when 20 is selected`() {
        assertEquals(CandidateVisibility.HIDDEN_MIN_CHAPTERS, visibility(1L, 20, mapOf(1L to 19L)))
        assertEquals(CandidateVisibility.HIDDEN_MIN_CHAPTERS, visibility(1L, 20, mapOf(1L to 1L)))
    }

    @Test
    fun `a count exactly at the threshold remains visible`() {
        assertEquals(CandidateVisibility.VISIBLE, visibility(1L, 20, mapOf(1L to 20L)))
    }

    @Test
    fun `a count above the threshold remains visible`() {
        assertEquals(CandidateVisibility.VISIBLE, visibility(1L, 20, mapOf(1L to 21L)))
        assertEquals(CandidateVisibility.VISIBLE, visibility(1L, 20, mapOf(1L to 500L)))
    }

    @Test
    fun `an unknown count fails open and stays visible`() {
        // Not present in the map at all, and the explicit 0L "unknown" sentinel -- both must fail open.
        assertEquals(CandidateVisibility.VISIBLE, visibility(1L, 20, mapOf(2L to 5L)))
        assertEquals(CandidateVisibility.VISIBLE, visibility(1L, 20, mapOf(1L to 0L)))
    }

    @Test
    fun `a chapter-count lookup failure fails open without hiding every candidate`() {
        // The callers pass an empty map when getChapterCounts throws -- that must never be read as
        // "every candidate has zero chapters".
        assertEquals(CandidateVisibility.VISIBLE, visibility(1L, 20, emptyMap()))
        assertEquals(CandidateVisibility.VISIBLE, visibility(1L, 50, emptyMap()))
    }

    @Test
    fun `a malformed persisted threshold cannot filter anything`() {
        // 7 is not a supported value; resolving it to Off means a candidate with 5 chapters that
        // would have been hidden at a raw 7 stays visible.
        assertEquals(CandidateVisibility.VISIBLE, visibility(1L, 7, mapOf(1L to 5L)))
        assertEquals(CandidateVisibility.VISIBLE, visibility(1L, -20, mapOf(1L to 1L)))
    }

    @Test
    fun `changing the threshold changes which candidates are hidden`() {
        val counts = mapOf(1L to 12L)
        assertEquals(CandidateVisibility.VISIBLE, visibility(1L, 10, counts))
        assertEquals(CandidateVisibility.HIDDEN_MIN_CHAPTERS, visibility(1L, 20, counts))
        assertEquals(CandidateVisibility.VISIBLE, visibility(1L, 0, counts))
    }
}
// KMK <--

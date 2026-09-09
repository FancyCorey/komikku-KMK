package exh.recs.bestversion

import exh.recs.RecommendationErrorKind
import exh.recs.matching.MangaIdentityKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga

// KMK Confirmed Blocker Remediation Phase 3 2026-07-29 -->
/**
 * Tests for [BestVersionPreviewFetchPolicy.partition] -- proves the plan's required "no network
 * call for unavailable chapter" behavior structurally: an unavailable candidate is never present in
 * [BestVersionPreviewFetchPolicy.Previewable.previewable], the only list
 * [BestVersionCompareScreenModel.startPreview] passes into its async page-fetch loop.
 */
class BestVersionPreviewFetchPolicyTest {

    private fun manga(source: Long, url: String) = Manga.create().copy(source = source, url = url)

    @Test
    fun `an available-chapter candidate is previewable, not unavailable`() {
        val m = manga(1L, "/a")
        val key = MangaIdentityKey(1L, "/a")
        val result = BestVersionPreviewFetchPolicy.partition(
            listOf(m),
            mapOf(key to CandidateChapterState.Available(eu.kanade.tachiyomi.source.model.SChapter.create(), totalChapters = 1)),
        )
        assertEquals(listOf(m), result.previewable)
        assertTrue(result.unavailable.isEmpty())
    }

    @Test
    fun `an Unavailable-chapter candidate never lands in previewable`() {
        val m = manga(2L, "/b")
        val key = MangaIdentityKey(2L, "/b")
        val result = BestVersionPreviewFetchPolicy.partition(listOf(m), mapOf(key to CandidateChapterState.Unavailable))
        assertTrue(result.previewable.isEmpty(), "an unavailable candidate must never be sent into the network-fetching path")
        assertEquals(listOf(m), result.unavailable)
    }

    @Test
    fun `a ChapterError candidate is treated as unavailable for preview purposes, not fetched`() {
        val m = manga(3L, "/c")
        val key = MangaIdentityKey(3L, "/c")
        val result = BestVersionPreviewFetchPolicy.partition(
            listOf(m),
            mapOf(
                key to CandidateChapterState.ChapterError(
                    BestVersionErrorReason.Recommendation(RecommendationErrorKind.Internal),
                ),
            ),
        )
        assertTrue(result.previewable.isEmpty())
        assertEquals(listOf(m), result.unavailable)
    }

    @Test
    fun `a candidate missing from chapterMap entirely is treated as unavailable, not fetched`() {
        val m = manga(4L, "/d")
        val result = BestVersionPreviewFetchPolicy.partition(listOf(m), emptyMap())
        assertTrue(result.previewable.isEmpty())
        assertEquals(listOf(m), result.unavailable)
    }

    @Test
    fun `a mixed set correctly separates previewable from unavailable, preserving order`() {
        val available = manga(1L, "/a")
        val unavailable = manga(2L, "/b")
        val loading = manga(3L, "/c")
        val result = BestVersionPreviewFetchPolicy.partition(
            listOf(available, unavailable, loading),
            mapOf(
                MangaIdentityKey(1L, "/a") to CandidateChapterState.Available(eu.kanade.tachiyomi.source.model.SChapter.create(), totalChapters = 1),
                MangaIdentityKey(2L, "/b") to CandidateChapterState.Unavailable,
                MangaIdentityKey(3L, "/c") to CandidateChapterState.Loading,
            ),
        )
        assertEquals(listOf(available), result.previewable)
        assertEquals(listOf(unavailable, loading), result.unavailable)
    }

    @Test
    fun `an empty comparison set produces empty results on both sides`() {
        val result = BestVersionPreviewFetchPolicy.partition(emptyList(), emptyMap())
        assertTrue(result.previewable.isEmpty())
        assertTrue(result.unavailable.isEmpty())
    }
}
// KMK <--

package exh.recs.bestversion

import eu.kanade.domain.manga.model.PagePreview
import eu.kanade.tachiyomi.source.model.SChapter
import exh.recs.RecommendationErrorKind
import exh.recs.matching.MangaIdentityKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BestVersionRetryScopePolicyTest {
    @Test
    fun `candidate retry scope includes only current retryable failures`() {
        val loaded = MangaIdentityKey(1L, "loaded")
        val failed = MangaIdentityKey(2L, "failed")
        val failedSecond = MangaIdentityKey(5L, "failed-second")
        val partial = MangaIdentityKey(6L, "partial")
        val skipped = MangaIdentityKey(3L, "skipped")
        val unavailableChapter = MangaIdentityKey(4L, "unavailable")

        assertEquals(
            listOf(failed, failedSecond, partial),
            BestVersionRetryScopePolicy.candidateKeys(
                previews = mapOf(
                    loaded to CandidatePreviewState.Loaded(emptyList()),
                    failed to CandidatePreviewState.PreviewError(BestVersionErrorReason.SourceUnavailable),
                    failedSecond to CandidatePreviewState.PreviewError(
                        BestVersionErrorReason.Recommendation(RecommendationErrorKind.Timeout),
                    ),
                    partial to CandidatePreviewState.Loaded(emptyList(), failedPageIndexes = setOf(2)),
                    skipped to CandidatePreviewState.Skipped,
                    unavailableChapter to CandidatePreviewState.PreviewError(BestVersionErrorReason.SourceUnavailable),
                ),
                chapters = mapOf(
                    failed to CandidateChapterState.Available(testChapter(), totalChapters = 1),
                    failedSecond to CandidateChapterState.Available(testChapter(), totalChapters = 1),
                    partial to CandidateChapterState.Available(testChapter(), totalChapters = 1),
                    unavailableChapter to CandidateChapterState.Unavailable,
                ),
            ),
        )
    }

    @Test
    fun `fullscreen retry scope includes only failed pages`() {
        assertEquals(
            listOf(1, 4),
            BestVersionRetryScopePolicy.fullscreenPageIndexes(
                mapOf(
                    0 to PreviewPageImageState.Resolved(testSampledPage()),
                    1 to PreviewPageImageState.Failed(BestVersionErrorReason.SourceUnavailable),
                    2 to PreviewPageImageState.Loading,
                    4 to PreviewPageImageState.Failed(BestVersionErrorReason.Recommendation(RecommendationErrorKind.Network)),
                ),
            ),
        )
    }

    @Test
    fun `thumbnail retry scope fans out failed samples and excludes successful samples`() {
        val first = MangaIdentityKey(2L, "first")
        val second = MangaIdentityKey(1L, "second")

        assertEquals(
            listOf(second to 0, second to 3, first to 1),
            BestVersionRetryScopePolicy.thumbnailPageKeys(
                mapOf(
                    first to setOf(1),
                    second to setOf(3, 0),
                ),
            ),
        )
    }

    private fun testChapter(): SChapter = SChapter.create()

    private fun testSampledPage(): SampledPage = SampledPage(
        index = 0,
        preview = PagePreview(index = 0, imageUrl = "https://example.com/page.jpg", source = 1L),
    )
}

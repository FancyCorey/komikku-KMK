package exh.recs.bestversion

import exh.recs.matching.MangaIdentityKey

/** Defines the retryable set for the two same-screen Best Version preview surfaces. */
object BestVersionRetryScopePolicy {
    fun candidateKeys(
        previews: Map<MangaIdentityKey, CandidatePreviewState>,
        chapters: Map<MangaIdentityKey, CandidateChapterState>,
    ): List<MangaIdentityKey> = previews.keys
        .filter {
            when (val preview = previews[it]) {
                is CandidatePreviewState.PreviewError -> true
                is CandidatePreviewState.Loaded -> preview.failedPageIndexes.isNotEmpty()
                else -> false
            }
        }
        .filter { chapters[it] is CandidateChapterState.Available }
        .sortedWith(compareBy<MangaIdentityKey> { it.source }.thenBy { it.url })

    fun fullscreenPageIndexes(images: Map<Int, PreviewPageImageState>): List<Int> = images
        .filterValues { it is PreviewPageImageState.Failed }
        .keys
        .sorted()

    fun thumbnailPageKeys(failedPages: Map<MangaIdentityKey, Set<Int>>): List<Pair<MangaIdentityKey, Int>> =
        failedPages
            .flatMap { (key, indexes) -> indexes.map { index -> key to index } }
            .sortedWith(compareBy<Pair<MangaIdentityKey, Int>> { it.first.source }.thenBy { it.first.url }.thenBy { it.second })
}

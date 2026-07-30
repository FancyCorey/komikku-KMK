package exh.recs.bestversion

import exh.recs.matching.MangaIdentityKey
import tachiyomi.domain.manga.model.Manga

// KMK Confirmed Blocker Remediation Phase 3 2026-07-29 -->
/**
 * Pure partition of [BestVersionCompareScreenModel.startPreview]'s comparison set into candidates
 * whose chapter is fetchable ([Previewable.previewable]) and candidates that must never be sent into
 * page-list/image-url fetching ([Previewable.unavailable]). Extracted so the plan's required "no
 * network call for unavailable chapter" behavior is directly provable: [unavailable] is computed
 * from state alone (no I/O), and [BestVersionCompareScreenModel.startPreview] only ever passes
 * [Previewable.previewable] into its async fetch loop -- an entry that lands in [unavailable] is
 * structurally impossible to reach that loop.
 */
object BestVersionPreviewFetchPolicy {
    data class Previewable(val previewable: List<Manga>, val unavailable: List<Manga>)

    fun partition(compare: List<Manga>, chapterMap: Map<MangaIdentityKey, CandidateChapterState>): Previewable {
        val previewable = compare.filter { manga ->
            chapterMap[MangaIdentityKey(manga.source, manga.url)] is CandidateChapterState.Available
        }
        val unavailable = compare.filter { manga ->
            chapterMap[MangaIdentityKey(manga.source, manga.url)] !is CandidateChapterState.Available
        }
        return Previewable(previewable, unavailable)
    }
}
// KMK <--

package exh.recs

import tachiyomi.domain.taste.model.MangaTaste

// KMK v0.8.6 -->
/**
 * Pure builder for the `visibilityFingerprint` component of [GroupPreviewCache.Key]. Extracted out
 * of [RecommendsScreenModel] so the fingerprint's "changed input -> different fingerprint" property
 * can be unit tested directly, without constructing the full screen model.
 *
 * Cache contract: the cache key must invalidate on every recommendation-affecting
 * input, not just group/source/language/budget. This covers everything [RecommendsScreenModel]'s
 * group-recommendation candidate-visibility pipeline reads that is not already part of
 * [GroupPreviewCache.Key]'s other fields: disabled-source state, source order, source-quality
 * (dislike) preferences, seen-entries state, taste (Love/Like/Dislike rating) state, and the
 * remaining visibility-policy settings (rated-manga visibility, hide-known-manga, min-chapter-count).
 */
object GroupPreviewVisibilityFingerprint {

    internal fun build(
        effectiveDisabledSourceIds: Set<Long>,
        storedSourceOrder: List<Long>,
        dislikedSourceRaw: String,
        qualityDislikedSourceRaw: String,
        seenKeys: Set<SeenMangaKey>,
        tasteByKey: Map<MangaTasteKey, MangaTaste>,
        visibility: Any,
        hideKnownManga: Boolean,
        minChapterCount: Int,
    ): String {
        val tasteFingerprint = tasteByKey.entries
            .sortedWith(compareBy({ it.key.source }, { it.key.url }))
            .joinToString("|") { "${it.key.source}:${it.key.url}:${it.value.rating}" }
            .hashCode()
        return listOf(
            effectiveDisabledSourceIds.sorted().joinToString(","),
            storedSourceOrder.joinToString(","),
            dislikedSourceRaw,
            qualityDislikedSourceRaw,
            seenKeys.map { it.toString() }.sorted().joinToString(","),
            tasteFingerprint.toString(),
            visibility.toString(),
            hideKnownManga.toString(),
            minChapterCount.toString(),
        ).joinToString("|")
    }
}
// KMK <--

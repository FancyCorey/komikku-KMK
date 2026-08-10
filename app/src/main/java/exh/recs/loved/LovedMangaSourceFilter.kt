package exh.recs.loved

// KMK -->
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaTaste

internal fun filterLovedTastesByInstalledSources(
    tastes: List<MangaTaste>,
    installedSourceIds: Set<Long>,
): List<MangaTaste> = filterRatedTastesByInstalledSources(
    tastes,
    installedSourceIds,
    allowedRatings = setOf(MangaRating.LOVE.value),
)

// KMK --> v0.7.35: generalized filter used by Liked/Disliked entry points
internal fun filterRatedTastesByInstalledSources(
    tastes: List<MangaTaste>,
    installedSourceIds: Set<Long>,
    allowedRatings: Set<Int>,
): List<MangaTaste> = tastes.filter {
    it.rating in allowedRatings && it.source in installedSourceIds
}
// KMK <--

// KMK --> v0.7.37: cross-source group rating exclusivity — display-only resolution
/**
 * For confirmed cross-source link groups that contain members with different ratings, resolves the
 * conflict by keeping only members whose rating matches the group's "winning" rating (the rating of
 * the most-recently-updated member in that group). Standalone entries (no linkGroupId) are kept as-is.
 *
 * This prevents the same grouped manga from appearing in multiple rating tabs simultaneously.
 * The [linkGroupByKey] map must use the same "source|url" key format as [LovedMangaScreenModel].
 */
internal fun resolveLinkedGroupRatingConflicts(
    tastes: List<MangaTaste>,
    linkGroupByKey: Map<String, String>,
): List<MangaTaste> {
    // Find the most-recently-updated taste per confirmed link group
    val groupWinnerRating = mutableMapOf<String, Int>()
    val groupLatestUpdatedAt = mutableMapOf<String, Long>()
    for (taste in tastes) {
        val groupId = linkGroupByKey["${taste.source}|${taste.url}"] ?: continue
        val existingAt = groupLatestUpdatedAt[groupId]
        if (existingAt == null || taste.updatedAt > existingAt) {
            groupLatestUpdatedAt[groupId] = taste.updatedAt
            groupWinnerRating[groupId] = taste.rating
        }
    }

    // Keep only tastes that match their group's winner rating; standalone entries always pass
    return tastes.filter { taste ->
        val groupId = linkGroupByKey["${taste.source}|${taste.url}"] ?: return@filter true
        val winnerRating = groupWinnerRating[groupId] ?: return@filter true
        taste.rating == winnerRating
    }
}
// KMK <--
// KMK <--

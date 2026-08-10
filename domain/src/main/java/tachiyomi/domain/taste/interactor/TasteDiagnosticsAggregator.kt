package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.TagAlias
import tachiyomi.domain.taste.model.TagPreference
import tachiyomi.domain.taste.model.TagTaste
import tachiyomi.domain.taste.model.normalizeTag

// KMK v0.8.10 -->
/** How many rated manga contributed to the current rating counts, split by [MangaRating]. */
data class TasteRatingCounts(val love: Int, val like: Int, val dislike: Int) {
    val total: Int get() = love + like + dislike
}

/** One already-explicitly-set tag preference, with how much of the user's own rated-manga history backs it up. */
data class TasteTagEvidence(val displayName: String, val evidenceCount: Int)

data class TasteDiagnosticsSummary(
    val ratingCounts: TasteRatingCounts,
    val preferredTagEvidence: List<TasteTagEvidence>,
    val blockedTagEvidence: List<TasteTagEvidence>,
    val explicitPreferredTagCount: Int,
    val explicitBlockedTagCount: Int,
    val explicitDislikedTagCount: Int,
)

/**
 * Pure, Android/database-free aggregation of local diagnostics for the existing taste system --
 * "what signals do we actually have, and how strong are they" -- built entirely from data the app
 * already collects (rated manga + their genres, tag preferences, tag aliases). Reads nothing new,
 * writes nothing, and never surfaces raw URLs/cookies/extension internals/full manga content --
 * only tag display names and counts.
 */
object TasteDiagnosticsAggregator {
    fun aggregate(
        ratedManga: List<RatedMangaGenres>,
        aliases: List<TagAlias>,
        tagPreferences: List<TagTaste>,
    ): TasteDiagnosticsSummary {
        val aliasToGroup: Map<String, String> = aliases.associate { it.normalizedAlias to it.groupKey }

        fun String.toGroupKey(): String {
            val normalized = normalizeTag()
            return aliasToGroup[normalized] ?: normalized
        }

        val evidenceCount = mutableMapOf<String, MutableSet<Int>>()
        ratedManga.forEachIndexed { index, entry ->
            for (genre in entry.genres) {
                evidenceCount.getOrPut(genre.toGroupKey()) { mutableSetOf() }.add(index)
            }
        }

        val ratingCounts = TasteRatingCounts(
            love = ratedManga.count { it.ratingValue == MangaRating.LOVE.value },
            like = ratedManga.count { it.ratingValue == MangaRating.LIKE.value },
            dislike = ratedManga.count { it.ratingValue == MangaRating.DISLIKE.value },
        )

        fun evidenceFor(entries: List<TagTaste>): List<TasteTagEvidence> = entries
            .map { entry -> TasteTagEvidence(entry.displayName, evidenceCount[entry.normalizedTag.toGroupKey()]?.size ?: 0) }
            .sortedByDescending { it.evidenceCount }

        val preferred = tagPreferences.filter { it.preference == TagPreference.PREFER.value }
        val blocked = tagPreferences.filter { it.preference == TagPreference.BLOCK.value }
        val disliked = tagPreferences.filter { it.preference == TagPreference.DISLIKE.value }

        return TasteDiagnosticsSummary(
            ratingCounts = ratingCounts,
            preferredTagEvidence = evidenceFor(preferred),
            blockedTagEvidence = evidenceFor(blocked),
            explicitPreferredTagCount = preferred.size,
            explicitBlockedTagCount = blocked.size,
            explicitDislikedTagCount = disliked.size,
        )
    }
}
// KMK <--

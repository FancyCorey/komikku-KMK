package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.TagAlias
import tachiyomi.domain.taste.model.normalizeTag

// KMK v0.8.10 -->
/**
 * One rated manga's rating plus the genre strings stored for it, as read from [MangaRepository] --
 * kept minimal (not the full [tachiyomi.domain.manga.model.Manga]/[tachiyomi.domain.taste.model.MangaTaste])
 * so [TasteSuggestionAggregator] has zero repository/database dependency and is directly testable.
 */
data class RatedMangaGenres(val ratingValue: Int, val genres: List<String>)

/** One suggested tag, with the evidence backing it. */
data class TasteSuggestionCandidate(
    val groupKey: String,
    val displayName: String,
    val evidenceCount: Int,
    val netWeight: Double,
)

data class TasteSuggestionResult(
    val preferred: List<TasteSuggestionCandidate>,
    val blocked: List<TasteSuggestionCandidate>,
    val totalRatedManga: Int,
) {
    /**
     * True when there simply isn't enough rated-manga data to produce any suggestion, regardless
     * of genre spread -- distinct from "there is data, but nothing crossed the evidence floor,"
     * which just means [preferred] and [blocked] are both empty for a user with real (if sparse)
     * ratings. The UI is expected to show a different message for each case.
     */
    val hasInsufficientData: Boolean
        get() = totalRatedManga < TasteSuggestionAggregator.MIN_EVIDENCE_COUNT
}

/**
 * Pure, Android/database-free aggregation of tag *suggestions* (a discoverable "you rate a lot of
 * X, want to prefer/block it?" prompt) from already-rated manga. Deliberately separate from
 * [GetTasteProfile]/[tachiyomi.domain.taste.model.TasteProfile] -- that pipeline feeds live
 * recommendation/source-evaluation scoring and is not touched or duplicated here; this aggregator
 * only decides what to *suggest adding* to the tag-preference table, using the exact same mutation
 * path ([SetTagTaste]) a manually-added tag preference already uses.
 *
 * Rules (see the v0.8.10 plan's Phase E requirements):
 * - a group key only becomes a suggestion once at least [MIN_EVIDENCE_COUNT] distinct rated manga
 *   contribute to it -- this is what prevents inferring a blocked tag from one isolated Dislike;
 * - preferred suggestions come from Love/Like ratings' genres (net-positive weight);
 * - blocked suggestions come from Dislike ratings' genres (net-negative weight);
 * - aliases are resolved through the same [TagAlias] group-key system [GetTasteProfile] uses, so a
 *   suggestion for "Yuri" and a suggestion for "Girls Love" collapse into one candidate;
 * - a group key already present in [alreadySetNormalizedTags] (an explicit tag preference already
 *   exists for it, in either direction, resolved through the same alias system) is never
 *   suggested again.
 */
object TasteSuggestionAggregator {
    const val MIN_EVIDENCE_COUNT = 3

    fun aggregate(
        ratedManga: List<RatedMangaGenres>,
        aliases: List<TagAlias>,
        alreadySetNormalizedTags: Collection<String>,
    ): TasteSuggestionResult {
        val aliasToGroup: Map<String, String> = aliases.associate { it.normalizedAlias to it.groupKey }
        val displayNameByGroup = mutableMapOf<String, String>()

        fun String.toGroupKey(): String {
            val normalized = normalizeTag()
            return aliasToGroup[normalized] ?: normalized
        }

        // KMK v0.8.10: resolved through the same alias map, matching GetTasteProfile's own
        // `tt.normalizedTag.toGroupKey()` handling for explicitTagPreferences -- a tag_taste row's
        // stored normalizedTag is not itself guaranteed to already be group-resolved.
        val alreadySetGroupKeys = alreadySetNormalizedTags.map { it.toGroupKey() }.toSet()

        val evidenceCount = mutableMapOf<String, MutableSet<Int>>() // groupKey -> distinct manga indices
        val netWeight = mutableMapOf<String, Double>()

        ratedManga.forEachIndexed { index, entry ->
            val weight = when (entry.ratingValue) {
                MangaRating.LOVE.value -> 2.0
                MangaRating.LIKE.value -> 1.0
                MangaRating.DISLIKE.value -> -2.0
                else -> return@forEachIndexed
            }
            for (genre in entry.genres) {
                val groupKey = genre.toGroupKey()
                displayNameByGroup.putIfAbsent(groupKey, genre)
                evidenceCount.getOrPut(groupKey) { mutableSetOf() }.add(index)
                netWeight[groupKey] = (netWeight[groupKey] ?: 0.0) + weight
            }
        }

        val candidates = evidenceCount.keys
            .filter { it !in alreadySetGroupKeys }
            .mapNotNull { groupKey ->
                val count = evidenceCount.getValue(groupKey).size
                if (count < MIN_EVIDENCE_COUNT) return@mapNotNull null
                val weight = netWeight[groupKey] ?: 0.0
                if (weight == 0.0) return@mapNotNull null
                TasteSuggestionCandidate(
                    groupKey = groupKey,
                    displayName = displayNameByGroup[groupKey] ?: groupKey,
                    evidenceCount = count,
                    netWeight = weight,
                )
            }

        return TasteSuggestionResult(
            preferred = candidates.filter { it.netWeight > 0 }.sortedByDescending { it.netWeight },
            blocked = candidates.filter { it.netWeight < 0 }.sortedBy { it.netWeight },
            totalRatedManga = ratedManga.size,
        )
    }
}
// KMK <--

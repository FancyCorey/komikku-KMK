package exh.recs.group

// KMK -->
import tachiyomi.domain.manga.model.Manga
import java.util.Locale

/**
 * Pure scorer that computes the group-seed component of a recommendation score.
 *
 * Scoring model (v0.7.38):
 * - For each candidate genre that matches a [GroupSeedTag]: score += baseMatch * seedTag.weight
 * - Strong match ([STRONG_MATCH_SCORE]): tag appeared in multiple linked members (memberCount ≥ 2).
 * - Weak match ([WEAK_MATCH_SCORE]): tag appeared in exactly one linked member.
 * - [aliasMap] normalizes tag aliases before lookup (same map used by PersonalRecommendationScorer).
 * - No hard reject here — blocked-tag rejection stays in PersonalRecommendationScorer.
 *
 * Callers compute the final score as: personalScore + groupSeedScore
 */
object GroupSeedRecommendationScorer {

    private const val STRONG_MATCH_SCORE = 1.0
    private const val WEAK_MATCH_SCORE = 0.3
    private const val STRONG_MATCH_THRESHOLD = 2

    /**
     * Returns the group-seed score component for [candidate] against [seed].
     * Returns 0.0 if the seed has no tags or the candidate has no genres.
     */
    fun score(
        candidate: Manga,
        seed: GroupRecommendationSeed,
        aliasMap: Map<String, String>,
    ): Double {
        if (seed.seedTags.isEmpty()) return 0.0

        val candidateGenres = candidate.genre
            ?.map { g -> (aliasMap[g.trim().lowercase(Locale.ROOT)] ?: g.trim().lowercase(Locale.ROOT)) }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: return 0.0

        val seedTagsByName = seed.seedTags.associateBy { it.name }

        var total = 0.0
        for (genre in candidateGenres) {
            val seedTag = seedTagsByName[genre] ?: continue
            val baseMatch = if (seedTag.memberCount >= STRONG_MATCH_THRESHOLD) {
                STRONG_MATCH_SCORE
            } else {
                WEAK_MATCH_SCORE
            }
            total += baseMatch * seedTag.weight
        }
        return total
    }
}
// KMK <--

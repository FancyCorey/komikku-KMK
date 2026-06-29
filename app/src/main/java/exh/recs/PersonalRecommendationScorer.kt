package exh.recs

import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.model.TagPreference
import tachiyomi.domain.taste.model.TasteProfile
import tachiyomi.domain.taste.model.normalizeTag

// KMK -->
/**
 * Scores a candidate manga against a user's [TasteProfile].
 *
 * Hard filter:
 * - If any candidate tag resolves to a group in [TasteProfile.blockedGroups], the candidate is
 *   rejected before scoring (score = 0, blocked = true).
 *
 * Soft score (summed, all components are additive):
 * - Explicit tag preference: +3 per preferred tag group, -2 per disliked tag group.
 * - Learned tag weight: contribution from [TasteProfile.learnedTagWeights] per matched group.
 * - Source affinity: [TasteProfile.sourceAffinity] value for the candidate's source (weak signal).
 *
 * The score is not normalized to [0,1]; the Browse Recommendations tab uses it only for
 * ordering within a single source row.
 */
internal object PersonalRecommendationScorer {

    data class ScoredCandidate(
        val manga: Manga,
        val score: Double,
        val blocked: Boolean,
        val matchedGroups: List<String>,
        val reasons: List<String>,
    )

    fun score(candidate: Manga, profile: TasteProfile, aliasMap: Map<String, String>): ScoredCandidate {
        val genres = candidate.genre ?: emptyList()

        // Resolve each genre to a group key
        val candidateGroups: List<String> = genres.map { genre ->
            val normalized = genre.normalizeTag()
            aliasMap[normalized] ?: normalized
        }

        // Hard block: if any group is blocked, reject immediately
        val blockedGroup = candidateGroups.firstOrNull { it in profile.blockedGroups }
        if (blockedGroup != null) {
            return ScoredCandidate(
                manga = candidate,
                score = 0.0,
                blocked = true,
                matchedGroups = listOf(blockedGroup),
                reasons = listOf("Blocked: $blockedGroup"),
            )
        }

        // Explicit tag preferences (+3 prefer, -2 dislike)
        var explicitScore = 0.0
        val explicitMatches = mutableListOf<String>()
        for (group in candidateGroups) {
            when (profile.explicitTagPreferences[group]) {
                TagPreference.PREFER.value -> {
                    explicitScore += 3.0
                    explicitMatches.add(group)
                }
                TagPreference.DISLIKE.value -> {
                    explicitScore -= 2.0
                }
            }
        }

        // Learned tag weights (from manga ratings)
        var learnedScore = 0.0
        val learnedMatches = mutableListOf<String>()
        for (group in candidateGroups) {
            val weight = profile.learnedTagWeights[group] ?: 0.0
            if (weight != 0.0) {
                learnedScore += weight
                if (weight > 0.0) learnedMatches.add(group)
            }
        }

        // Source affinity (weak)
        val sourceAffinityScore = profile.sourceAffinity[candidate.source] ?: 0.0

        val totalScore = explicitScore + learnedScore + sourceAffinityScore

        // Build human-readable reason strings
        val reasons = buildList {
            if (explicitMatches.isNotEmpty()) add("Preferred tags: ${explicitMatches.joinToString()}")
            if (learnedMatches.isNotEmpty()) add("Liked tags: ${learnedMatches.joinToString()}")
        }

        return ScoredCandidate(
            manga = candidate,
            score = totalScore,
            blocked = false,
            matchedGroups = (explicitMatches + learnedMatches).distinct(),
            reasons = reasons,
        )
    }

    /**
     * Filters blocked candidates and returns the rest sorted by score descending.
     * [limit] caps the result list.
     */
    fun rankCandidates(
        candidates: List<Manga>,
        profile: TasteProfile,
        aliasMap: Map<String, String>,
        limit: Int = 10,
    ): List<ScoredCandidate> {
        return candidates
            .map { score(it, profile, aliasMap) }
            .filter { !it.blocked }
            .sortedByDescending { it.score }
            .take(limit)
    }
}
// KMK <--

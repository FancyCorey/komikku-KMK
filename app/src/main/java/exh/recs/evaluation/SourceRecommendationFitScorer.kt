package exh.recs.evaluation

// KMK -->
/**
 * Pure stateless scorer that computes a recommendation-quality score [0.0, 1.0] for a source
 * based on observed outcomes from a bounded rec-fit probe run.
 *
 * This scorer is intentionally decoupled from the probe execution. It takes an [Outcome]
 * data class representing what the probe observed and returns a single quality score.
 *
 * Scoring notes:
 * - Positive signals: visible candidates, high average score, matched groups, Top Picks hits.
 * - Negative signals: NoMatches results, FilteredOut candidates, blocked-tag candidates, errors.
 * - Score is clamped to [0.0, 1.0].
 *
 * The actual probe execution (querying the temporarily-installed extension and collecting
 * For You results) is deferred and not part of this class.
 */
object SourceRecommendationFitScorer {

    data class Outcome(
        /** Number of recommendation candidates that passed all filters and were visible to the user. */
        val visibleCandidateCount: Int = 0,
        /** Number of candidates filtered out (blocked tags, already-read, etc.). */
        val filteredOutCount: Int = 0,
        /** Number of visible candidates where blocked tags were found in their tag lists. */
        val blockedTagCandidateCount: Int = 0,
        /** Number of cross-source manga groups that matched at least one candidate. */
        val matchedGroupCount: Int = 0,
        /** Number of candidates that contributed to the Top Picks list. */
        val topPicksContribution: Int = 0,
        /** Number of searches that returned zero results. */
        val noMatchesCount: Int = 0,
        /** Number of errors encountered during the probe. */
        val errorCount: Int = 0,
        /** Average recommendation score across visible candidates (0.0 if none). */
        val avgCandidateScore: Double = 0.0,
    )

    fun score(outcome: Outcome): Double {
        if (outcome.errorCount > 0 && outcome.visibleCandidateCount == 0) return 0.0

        // Positive: visible candidates
        val visibilityScore = when {
            outcome.visibleCandidateCount >= 10 -> 0.9
            outcome.visibleCandidateCount >= 5 -> 0.75
            outcome.visibleCandidateCount >= 2 -> 0.55
            outcome.visibleCandidateCount == 1 -> 0.35
            else -> 0.0
        }

        // Positive: average candidate score boost
        val avgScoreBoost = (outcome.avgCandidateScore * 0.2).coerceIn(0.0, 0.2)

        // Positive: matched groups and top picks
        val groupBoost = when {
            outcome.matchedGroupCount >= 3 -> 0.15
            outcome.matchedGroupCount >= 1 -> 0.08
            else -> 0.0
        }
        val topPicksBoost = when {
            outcome.topPicksContribution >= 3 -> 0.10
            outcome.topPicksContribution >= 1 -> 0.05
            else -> 0.0
        }

        // Negative: no-matches and filtered-out
        val noMatchesPenalty = when {
            outcome.noMatchesCount >= 5 -> 0.30
            outcome.noMatchesCount >= 2 -> 0.15
            outcome.noMatchesCount >= 1 -> 0.05
            else -> 0.0
        }
        val filteredPenalty = when {
            outcome.filteredOutCount >= 5 -> 0.15
            outcome.filteredOutCount >= 2 -> 0.08
            else -> 0.0
        }
        val blockedTagPenalty = (
            outcome.blockedTagCandidateCount.toDouble() /
                (outcome.visibleCandidateCount + outcome.filteredOutCount + 1) * 0.25
            )
            .coerceIn(0.0, 0.25)
        val errorPenalty = (outcome.errorCount * 0.05).coerceIn(0.0, 0.20)

        val raw = visibilityScore + avgScoreBoost + groupBoost + topPicksBoost -
            noMatchesPenalty - filteredPenalty - blockedTagPenalty - errorPenalty

        return raw.coerceIn(0.0, 1.0)
    }
}
// KMK <--

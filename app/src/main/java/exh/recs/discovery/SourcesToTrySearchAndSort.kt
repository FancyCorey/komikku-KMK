package exh.recs.discovery

import java.util.Locale

// KMK v0.8.10 -->
/**
 * Sort modes for Sources To Try. Each is backed by data [NonInstalledSourceSuggestion] already
 * carries -- this does not introduce a second scoring system, only orderings over the existing
 * score/confidence/reasons/displayLang/displayName fields [NonInstalledSourceSuggestionScorer]
 * already computes. "Freshness" (evidence recency) is not offered as a sort mode because no
 * suggestion currently carries an evaluation timestamp -- see the phase report; adding one would
 * mean plumbing SourceEvaluation.updatedAt through the suggestion pipeline, out of this pass's
 * scope for a UI-only sort addition.
 */
enum class SourcesToTrySortMode { BEST_FIT, NAME_AZ, LANGUAGE }

/**
 * Pure local search and sort over the already-scored, already-filtered suggestion list Sources To
 * Try displays. Never re-derives a verdict or eligibility decision -- [bestFitRank] only orders by
 * the verdict-derived reasons [NonInstalledSourceSuggestionScorer] already attached, the same single
 * source of truth [exh.recs.evaluation.SourceRecommendationFitDisplayPolicy] and the Source
 * Evaluation screen itself read for the same row (see [NonInstalledSourceSuggestionScorer]'s
 * `evaluations: Map<String, SourceEvaluationVerdict>` parameter, sourced from the same
 * `GetSourceEvaluations` interactor Source Evaluation's own screen model uses).
 */
object SourcesToTrySearchAndSort {

    fun search(suggestions: List<NonInstalledSourceSuggestion>, query: String): List<NonInstalledSourceSuggestion> {
        val q = normalize(query)
        if (q.isBlank()) return suggestions
        return suggestions.filter { s ->
            normalize(s.displayName).contains(q) ||
                normalize(s.displayLang).contains(q) ||
                normalize(s.displayRepoName).contains(q) ||
                normalize(s.extension.pkgName).contains(q)
        }
    }

    fun sort(suggestions: List<NonInstalledSourceSuggestion>, mode: SourcesToTrySortMode): List<NonInstalledSourceSuggestion> =
        when (mode) {
            SourcesToTrySortMode.BEST_FIT -> suggestions.sortedWith(
                compareByDescending<NonInstalledSourceSuggestion> { bestFitRank(it) }
                    .thenByDescending { it.score }
                    .thenBy { stableKey(it) },
            )
            SourcesToTrySortMode.NAME_AZ -> suggestions.sortedWith(
                compareBy<NonInstalledSourceSuggestion> { it.displayName.lowercase(Locale.ROOT) }
                    .thenBy { stableKey(it) },
            )
            SourcesToTrySortMode.LANGUAGE -> suggestions.sortedWith(
                compareBy<NonInstalledSourceSuggestion> { it.displayLang.lowercase(Locale.ROOT) }
                    .thenBy { it.displayName.lowercase(Locale.ROOT) }
                    .thenBy { stableKey(it) },
            )
        }

    /**
     * Higher ranks first. Mirrors the verdict quality ordering evaluation already implies
     * (STRONG_FIT > WORTH_TRYING > everything else), then confidence, without re-deriving either --
     * both are read directly off [NonInstalledSourceSuggestion.reasons]/`confidence`.
     */
    internal fun bestFitRank(suggestion: NonInstalledSourceSuggestion): Int {
        val reasonRank = when {
            suggestion.reasons.contains(NonInstalledSuggestionReason.EvaluatedStrongFit) -> 3
            suggestion.reasons.contains(NonInstalledSuggestionReason.EvaluatedWorthTrying) -> 2
            suggestion.reasons.contains(NonInstalledSuggestionReason.UserLikedSource) -> 1
            else -> 0
        }
        val confidenceRank = if (suggestion.confidence == SuggestionConfidence.MEDIUM) 1 else 0
        val evidenceRank = (SourcesToTryRankingPolicy.usefulnessScore(suggestion.rankingEvidence) * 100).toInt()
        return evidenceRank * 100 + reasonRank * 10 + confidenceRank
    }

    private fun stableKey(suggestion: NonInstalledSourceSuggestion): String =
        "${suggestion.dismissalKey}|${suggestion.displayName.lowercase(Locale.ROOT)}"

    private fun normalize(s: String): String = s.trim().lowercase(Locale.ROOT)
}
// KMK <--

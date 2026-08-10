package exh.recs.evaluation

// KMK -->
data class SourceRecommendationFitProbeOutcome(
    val queryCount: Int = 0,
    val querySuccessCount: Int = 0,
    val rawResultCount: Int = 0,
    val visibleCandidateCount: Int = 0,
    val filteredOutCount: Int = 0,
    val blockedTagCandidateCount: Int = 0,
    val matchedGroupCount: Int = 0,
    val topPicksContribution: Int = 0,
    val noMatchesCount: Int = 0,
    val errorCount: Int = 0,
    val avgCandidateScore: Double = 0.0,
    val reasons: List<String> = emptyList(),
    // KMK --> v0.7.13: enrichment diagnostics
    /** How many raw candidates had getMangaDetails called to fill in missing genre metadata. */
    val enrichedCandidateCount: Int = 0,
    /** How many candidates still had no genre after enrichment — limits probe scoring accuracy. */
    val weakMetadataCandidateCount: Int = 0,
    // KMK <--
) {

    /** Convert to the scorer's Outcome model. */
    fun toScorerOutcome(): SourceRecommendationFitScorer.Outcome = SourceRecommendationFitScorer.Outcome(
        visibleCandidateCount = visibleCandidateCount,
        filteredOutCount = filteredOutCount,
        blockedTagCandidateCount = blockedTagCandidateCount,
        matchedGroupCount = matchedGroupCount,
        topPicksContribution = topPicksContribution,
        noMatchesCount = noMatchesCount,
        errorCount = errorCount,
        avgCandidateScore = avgCandidateScore,
    )

    /**
     * Derive a human-readable label for display in Source Evaluation results.
     *
     * Labels (from best to worst):
     * - Great: score >= 0.80
     * - Good: score >= 0.55
     * - Mixed: score >= 0.30
     * - Weak: score > 0 but < 0.30
     * - No matches: no raw results at all
     * - Error: only errors, no successes
     * - Too little evidence: not enough probes ran
     */
    fun label(): RecommendationQualityLabel {
        if (queryCount == 0) return RecommendationQualityLabel.TOO_LITTLE_EVIDENCE
        if (errorCount > 0 && querySuccessCount == 0) return RecommendationQualityLabel.ERROR
        if (rawResultCount == 0) return RecommendationQualityLabel.NO_MATCHES
        val score = SourceRecommendationFitScorer.score(toScorerOutcome())
        return when {
            score >= 0.80 -> RecommendationQualityLabel.GREAT
            score >= 0.55 -> RecommendationQualityLabel.GOOD
            score >= 0.30 -> RecommendationQualityLabel.MIXED
            else -> RecommendationQualityLabel.WEAK
        }
    }
}

enum class RecommendationQualityLabel {
    GREAT,
    GOOD,
    MIXED,
    WEAK,
    NO_MATCHES,
    ERROR,
    TOO_LITTLE_EVIDENCE,
}
// KMK <--

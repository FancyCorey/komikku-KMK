package exh.recs.discovery

import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationMetadataConfidence
import tachiyomi.domain.taste.model.SourceRecommendationFit
import java.util.Locale

/** Orders Sources To Try by observed usefulness before metadata similarity. */
object SourcesToTryRankingPolicy {
    fun evidence(
        evaluation: SourceEvaluation?,
        fit: SourceRecommendationFit?,
    ): SourcesToTryRankingEvidence {
        val sampledTitles = evaluation?.sampledTitlesJson
            ?.split('|')
            ?.map { it.trim().lowercase(Locale.ROOT) }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        val noveltyScore = when {
            fit != null && fit.visibleCandidateCount + fit.filteredOutCount > 0 ->
                fit.visibleCandidateCount.toDouble() / (fit.visibleCandidateCount + fit.filteredOutCount)
            sampledTitles.isNotEmpty() -> sampledTitles.distinct().size.toDouble() / sampledTitles.size
            (evaluation?.sampleCount ?: 0) > 0 -> 0.5
            else -> 0.0
        }
        val rawCatalogueCount = (evaluation?.popularCount ?: 0) + (evaluation?.latestCount ?: 0)
        val duplicateCoverage = when {
            rawCatalogueCount > 0 -> ((evaluation?.sampleCount ?: 0).toDouble() / rawCatalogueCount).coerceIn(0.0, 1.0)
            (evaluation?.sampleCount ?: 0) > 0 -> 0.5
            else -> 0.0
        }
        return SourcesToTryRankingEvidence(
            catalogueSampleCount = evaluation?.sampleCount ?: 0,
            noveltyScore = noveltyScore.coerceIn(0.0, 1.0),
            duplicateCoverage = duplicateCoverage,
            catalogueFitScore = evaluation?.recommendationFitScore?.coerceIn(0.0, 1.0) ?: 0.0,
            visibleCandidateCount = fit?.visibleCandidateCount ?: 0,
            filteredOutCount = fit?.filteredOutCount ?: 0,
            matchedGroupCount = fit?.matchedGroupCount ?: 0,
            topPicksContribution = fit?.topPicksContribution ?: 0,
            recommendationQualityScore = fit?.recommendationQualityScore ?: 0.0,
            catalogueMetadataConfidence = evaluation?.catalogueMetadataConfidence
                ?: SourceEvaluationMetadataConfidence.UNKNOWN,
        )
    }

    /** Metadata fit remains a bounded tie-break through the caller's existing score. */
    fun usefulnessScore(evidence: SourcesToTryRankingEvidence): Double {
        val samples = evidence.catalogueSampleCount.coerceAtLeast(0)
        val catalogueSize = (samples / 10.0).coerceIn(0.0, 1.0)
        val groupCoverage = ((evidence.matchedGroupCount + evidence.topPicksContribution) / 5.0)
            .coerceIn(0.0, 1.0)
        val confidence = when (evidence.catalogueMetadataConfidence) {
            SourceEvaluationMetadataConfidence.HIGH -> 1.0
            SourceEvaluationMetadataConfidence.MODERATE -> 0.7
            SourceEvaluationMetadataConfidence.LOW -> 0.3
            SourceEvaluationMetadataConfidence.UNKNOWN -> 0.0
        }
        return (
            catalogueSize * 0.25 +
                evidence.noveltyScore * 0.20 +
                evidence.duplicateCoverage * 0.15 +
                evidence.catalogueFitScore * 0.20 +
                groupCoverage * 0.10 +
                evidence.recommendationQualityScore.coerceIn(0.0, 1.0) * 0.05 +
                confidence * 0.05
            ).coerceIn(0.0, 1.0)
    }
}

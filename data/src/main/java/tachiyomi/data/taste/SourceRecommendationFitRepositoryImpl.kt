package tachiyomi.data.taste

import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.taste.model.RecommendationQualityVerdict
import tachiyomi.domain.taste.model.SourceRecommendationFit
import tachiyomi.domain.taste.repository.SourceRecommendationFitRepository

// KMK --> v0.7.6
class SourceRecommendationFitRepositoryImpl(
    private val handler: DatabaseHandler,
) : SourceRecommendationFitRepository {

    override suspend fun getAll(): List<SourceRecommendationFit> {
        return handler.awaitList {
            source_recommendation_fitQueries.getAll(sourceRecommendationFitMapper)
        }
    }

    override suspend fun getByKey(fitKey: String): SourceRecommendationFit? {
        return handler.awaitOneOrNull {
            source_recommendation_fitQueries.getByKey(fitKey, sourceRecommendationFitMapper)
        }
    }

    override suspend fun getByEvaluationKey(evaluationKey: String): List<SourceRecommendationFit> {
        return handler.awaitList {
            source_recommendation_fitQueries.getByEvaluationKey(evaluationKey, sourceRecommendationFitMapper)
        }
    }

    override suspend fun upsert(fit: SourceRecommendationFit) {
        handler.await(inTransaction = true) {
            source_recommendation_fitQueries.upsert(
                fitKey = fit.fitKey,
                evaluationKey = fit.evaluationKey,
                sourceId = fit.sourceId,
                extensionPkgName = fit.extensionPkgName,
                signatureHash = fit.signatureHash,
                extensionName = fit.extensionName,
                sourceName = fit.sourceName,
                lang = fit.lang,
                evaluatedAt = fit.evaluatedAt,
                queryCount = fit.queryCount.toLong(),
                querySuccessCount = fit.querySuccessCount.toLong(),
                rawResultCount = fit.rawResultCount.toLong(),
                visibleCandidateCount = fit.visibleCandidateCount.toLong(),
                filteredOutCount = fit.filteredOutCount.toLong(),
                blockedTagCandidateCount = fit.blockedTagCandidateCount.toLong(),
                matchedGroupCount = fit.matchedGroupCount.toLong(),
                topPicksContribution = fit.topPicksContribution.toLong(),
                noMatchesCount = fit.noMatchesCount.toLong(),
                errorCount = fit.errorCount.toLong(),
                avgCandidateScore = fit.avgCandidateScore,
                recommendationQualityScore = fit.recommendationQualityScore,
                verdict = fit.verdict.serialized,
                reasonsJson = fit.reasonsJson,
                errorMessage = fit.errorMessage,
                // KMK --> v0.7.42
                evaluationVersion = fit.evaluationVersion.toLong(),
                expiresAt = fit.expiresAt,
                // KMK <--
            )
        }
    }

    override suspend fun deleteByKey(fitKey: String) {
        handler.await { source_recommendation_fitQueries.deleteByKey(fitKey) }
    }

    override suspend fun deleteByEvaluationKey(evaluationKey: String) {
        handler.await { source_recommendation_fitQueries.deleteByEvaluationKey(evaluationKey) }
    }

    override suspend fun deleteAll() {
        handler.await { source_recommendation_fitQueries.deleteAll() }
    }
}

private val sourceRecommendationFitMapper = {
        fitKey: String,
        evaluationKey: String,
        sourceId: Long?,
        extensionPkgName: String,
        signatureHash: String,
        extensionName: String,
        sourceName: String,
        lang: String,
        evaluatedAt: Long,
        queryCount: Long,
        querySuccessCount: Long,
        rawResultCount: Long,
        visibleCandidateCount: Long,
        filteredOutCount: Long,
        blockedTagCandidateCount: Long,
        matchedGroupCount: Long,
        topPicksContribution: Long,
        noMatchesCount: Long,
        errorCount: Long,
        avgCandidateScore: Double,
        recommendationQualityScore: Double,
        verdict: String,
        reasonsJson: String,
        errorMessage: String?,
        // KMK --> v0.7.42
        evaluationVersion: Long,
        expiresAt: Long?,
    // KMK <--
    ->
    SourceRecommendationFit(
        fitKey = fitKey,
        evaluationKey = evaluationKey,
        sourceId = sourceId,
        extensionPkgName = extensionPkgName,
        signatureHash = signatureHash,
        extensionName = extensionName,
        sourceName = sourceName,
        lang = lang,
        evaluatedAt = evaluatedAt,
        queryCount = queryCount.toInt(),
        querySuccessCount = querySuccessCount.toInt(),
        rawResultCount = rawResultCount.toInt(),
        visibleCandidateCount = visibleCandidateCount.toInt(),
        filteredOutCount = filteredOutCount.toInt(),
        blockedTagCandidateCount = blockedTagCandidateCount.toInt(),
        matchedGroupCount = matchedGroupCount.toInt(),
        topPicksContribution = topPicksContribution.toInt(),
        noMatchesCount = noMatchesCount.toInt(),
        errorCount = errorCount.toInt(),
        avgCandidateScore = avgCandidateScore,
        recommendationQualityScore = recommendationQualityScore,
        verdict = RecommendationQualityVerdict.fromSerialized(verdict),
        reasonsJson = reasonsJson,
        errorMessage = errorMessage,
        // KMK --> v0.7.42
        evaluationVersion = evaluationVersion.toInt(),
        expiresAt = expiresAt,
        // KMK <--
    )
}
// KMK <--

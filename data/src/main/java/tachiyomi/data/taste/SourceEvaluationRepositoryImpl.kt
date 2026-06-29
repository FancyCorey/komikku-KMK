package tachiyomi.data.taste

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationVerdict
import tachiyomi.domain.taste.repository.SourceEvaluationRepository

// KMK -->
class SourceEvaluationRepositoryImpl(
    private val handler: DatabaseHandler,
) : SourceEvaluationRepository {

    override suspend fun getAll(): List<SourceEvaluation> {
        return handler.awaitList {
            source_evaluationQueries.getAll(sourceEvaluationMapper)
        }
    }

    override fun getAllAsFlow(): Flow<List<SourceEvaluation>> {
        return handler.subscribeToList {
            source_evaluationQueries.getAllAsFlow(sourceEvaluationMapper)
        }
    }

    override suspend fun getByKey(key: String): SourceEvaluation? {
        return handler.awaitOneOrNull {
            source_evaluationQueries.getByKey(key, sourceEvaluationMapper)
        }
    }

    override suspend fun getBySourceId(sourceId: Long): SourceEvaluation? {
        return handler.awaitOneOrNull {
            source_evaluationQueries.getBySourceId(sourceId, sourceEvaluationMapper)
        }
    }

    override suspend fun getByPackage(pkgName: String, signatureHash: String): List<SourceEvaluation> {
        return handler.awaitList {
            source_evaluationQueries.getByPackage(pkgName, signatureHash, sourceEvaluationMapper)
        }
    }

    override suspend fun upsert(evaluation: SourceEvaluation) {
        handler.await(inTransaction = true) {
            source_evaluationQueries.upsert(
                evaluationKey = evaluation.evaluationKey,
                sourceId = evaluation.sourceId,
                extensionPkgName = evaluation.extensionPkgName,
                signatureHash = evaluation.signatureHash,
                extensionName = evaluation.extensionName,
                sourceName = evaluation.sourceName,
                lang = evaluation.lang,
                baseUrl = evaluation.baseUrl,
                repoName = evaluation.repoName,
                sourceCount = evaluation.sourceCount.toLong(),
                isNsfw = if (evaluation.isNsfw) 1L else 0L,
                evaluationVersion = evaluation.evaluationVersion.toLong(),
                evaluatedAt = evaluation.evaluatedAt,
                expiresAt = evaluation.expiresAt,
                sampleCount = evaluation.sampleCount.toLong(),
                popularCount = evaluation.popularCount.toLong(),
                latestCount = evaluation.latestCount.toLong(),
                searchCount = evaluation.searchCount.toLong(),
                searchSuccessCount = evaluation.searchSuccessCount.toLong(),
                likedTitleMatchCount = evaluation.likedTitleMatchCount.toLong(),
                preferredTagMatchCount = evaluation.preferredTagMatchCount.toLong(),
                blockedTagMatchCount = evaluation.blockedTagMatchCount.toLong(),
                explicitSignalCount = evaluation.explicitSignalCount.toLong(),
                ecchiSignalCount = evaluation.ecchiSignalCount.toLong(),
                errorCount = evaluation.errorCount.toLong(),
                qualityScore = evaluation.qualityScore,
                recommendationFitScore = evaluation.recommendationFitScore,
                searchReliabilityScore = evaluation.searchReliabilityScore,
                explicitScore = evaluation.explicitScore,
                ecchiScore = evaluation.ecchiScore,
                verdict = evaluation.verdict.serialized,
                sampledTitlesJson = evaluation.sampledTitlesJson,
                sampledTagsJson = evaluation.sampledTagsJson,
                errorMessage = evaluation.errorMessage,
                // KMK --> v0.7.4: extension version fields
                extensionVersionName = evaluation.extensionVersionName,
                extensionVersionCode = evaluation.extensionVersionCode,
                extensionApkName = evaluation.extensionApkName,
                // KMK <--
            )
        }
    }

    override suspend fun deleteByKey(key: String) {
        handler.await { source_evaluationQueries.deleteByKey(key) }
    }

    override suspend fun deleteByPackage(pkgName: String, signatureHash: String) {
        handler.await { source_evaluationQueries.deleteByPackage(pkgName, signatureHash) }
    }

    override suspend fun deleteAll() {
        handler.await { source_evaluationQueries.deleteAll() }
    }
}

private val sourceEvaluationMapper = {
        evaluationKey: String,
        sourceId: Long?,
        extensionPkgName: String,
        signatureHash: String,
        extensionName: String,
        sourceName: String,
        lang: String,
        baseUrl: String?,
        repoName: String?,
        sourceCount: Long,
        isNsfw: Long,
        evaluationVersion: Long,
        evaluatedAt: Long,
        expiresAt: Long?,
        sampleCount: Long,
        popularCount: Long,
        latestCount: Long,
        searchCount: Long,
        searchSuccessCount: Long,
        likedTitleMatchCount: Long,
        preferredTagMatchCount: Long,
        blockedTagMatchCount: Long,
        explicitSignalCount: Long,
        ecchiSignalCount: Long,
        errorCount: Long,
        qualityScore: Double,
        recommendationFitScore: Double,
        searchReliabilityScore: Double,
        explicitScore: Double,
        ecchiScore: Double,
        verdict: String,
        sampledTitlesJson: String?,
        sampledTagsJson: String?,
        errorMessage: String?,
        // KMK --> v0.7.4: extension version fields
        extensionVersionName: String?,
        extensionVersionCode: Long?,
        extensionApkName: String?,
    // KMK <--
    ->
    SourceEvaluation(
        evaluationKey = evaluationKey,
        sourceId = sourceId,
        extensionPkgName = extensionPkgName,
        signatureHash = signatureHash,
        extensionName = extensionName,
        sourceName = sourceName,
        lang = lang,
        baseUrl = baseUrl,
        repoName = repoName,
        sourceCount = sourceCount.toInt(),
        isNsfw = isNsfw != 0L,
        evaluationVersion = evaluationVersion.toInt(),
        evaluatedAt = evaluatedAt,
        expiresAt = expiresAt,
        sampleCount = sampleCount.toInt(),
        popularCount = popularCount.toInt(),
        latestCount = latestCount.toInt(),
        searchCount = searchCount.toInt(),
        searchSuccessCount = searchSuccessCount.toInt(),
        likedTitleMatchCount = likedTitleMatchCount.toInt(),
        preferredTagMatchCount = preferredTagMatchCount.toInt(),
        blockedTagMatchCount = blockedTagMatchCount.toInt(),
        explicitSignalCount = explicitSignalCount.toInt(),
        ecchiSignalCount = ecchiSignalCount.toInt(),
        errorCount = errorCount.toInt(),
        qualityScore = qualityScore,
        recommendationFitScore = recommendationFitScore,
        searchReliabilityScore = searchReliabilityScore,
        explicitScore = explicitScore,
        ecchiScore = ecchiScore,
        verdict = SourceEvaluationVerdict.fromSerialized(verdict),
        sampledTitlesJson = sampledTitlesJson,
        sampledTagsJson = sampledTagsJson,
        errorMessage = errorMessage,
        // KMK --> v0.7.4: extension version fields
        extensionVersionName = extensionVersionName,
        extensionVersionCode = extensionVersionCode,
        extensionApkName = extensionApkName,
        // KMK <--
    )
}
// KMK <--

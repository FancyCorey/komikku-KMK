package tachiyomi.domain.taste.repository

import tachiyomi.domain.taste.model.SourceRecommendationFit

// KMK --> v0.7.6
interface SourceRecommendationFitRepository {

    suspend fun getAll(): List<SourceRecommendationFit>

    suspend fun getByKey(fitKey: String): SourceRecommendationFit?

    suspend fun getByEvaluationKey(evaluationKey: String): List<SourceRecommendationFit>

    suspend fun upsert(fit: SourceRecommendationFit)

    suspend fun deleteByKey(fitKey: String)

    suspend fun deleteByEvaluationKey(evaluationKey: String)

    suspend fun deleteAll()
}
// KMK <--

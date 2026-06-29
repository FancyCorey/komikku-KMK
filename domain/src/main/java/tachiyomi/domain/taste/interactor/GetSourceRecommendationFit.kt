package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.model.SourceRecommendationFit
import tachiyomi.domain.taste.repository.SourceRecommendationFitRepository

// KMK --> v0.7.6
class GetSourceRecommendationFit(
    private val repository: SourceRecommendationFitRepository,
) {
    suspend fun awaitAll(): List<SourceRecommendationFit> = repository.getAll()

    suspend fun awaitByEvaluationKey(evaluationKey: String): List<SourceRecommendationFit> =
        repository.getByEvaluationKey(evaluationKey)

    suspend fun awaitByKey(fitKey: String): SourceRecommendationFit? =
        repository.getByKey(fitKey)
}
// KMK <--

package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.model.SourceRecommendationFit
import tachiyomi.domain.taste.repository.SourceRecommendationFitRepository

// KMK --> v0.7.6
class UpsertSourceRecommendationFit(
    private val repository: SourceRecommendationFitRepository,
) {
    suspend fun await(fit: SourceRecommendationFit) = repository.upsert(fit)
}
// KMK <--

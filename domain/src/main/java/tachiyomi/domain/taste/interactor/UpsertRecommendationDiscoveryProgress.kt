package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.model.RecommendationDiscoveryProgress
import tachiyomi.domain.taste.repository.RecommendationDiscoveryProgressRepository

// KMK --> v0.7.39: For You rolling discovery progress
class UpsertRecommendationDiscoveryProgress(
    private val repository: RecommendationDiscoveryProgressRepository,
) {
    suspend fun await(entry: RecommendationDiscoveryProgress) = repository.upsert(entry)
}
// KMK <--

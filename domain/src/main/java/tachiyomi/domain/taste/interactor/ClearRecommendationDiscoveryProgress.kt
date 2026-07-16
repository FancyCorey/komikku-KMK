package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.repository.RecommendationDiscoveryProgressRepository

// KMK --> v0.7.39: For You rolling discovery progress
class ClearRecommendationDiscoveryProgress(
    private val repository: RecommendationDiscoveryProgressRepository,
) {
    suspend fun await() = repository.deleteAll()
}
// KMK <--

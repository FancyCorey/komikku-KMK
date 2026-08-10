package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.repository.RecommendationExposureRepository

// KMK -->
class PruneRecommendationExposure(
    private val repository: RecommendationExposureRepository,
) {
    suspend fun await(cutoff: Long) {
        repository.pruneOlderThan(cutoff)
    }
}
// KMK <--

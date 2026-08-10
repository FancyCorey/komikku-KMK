package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.repository.RecommendationExposureRepository

// KMK_CLAUDE_LATEST_EXPLORATION_STRUCTURAL_COMPLETION_2026-08-08 -->
class PruneRecommendationExposure(
    private val repository: RecommendationExposureRepository,
) {
    suspend fun await(cutoff: Long) {
        repository.pruneOlderThan(cutoff)
    }
}
// KMK <--

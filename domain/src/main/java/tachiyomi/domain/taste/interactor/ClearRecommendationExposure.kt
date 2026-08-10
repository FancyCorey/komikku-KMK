package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.repository.RecommendationExposureRepository

// KMK_CLAUDE_LATEST_EXPLORATION_STRUCTURAL_COMPLETION_2026-08-08 -->
/** User-facing "clear exposure history" action -- clears ordering history only, never ratings/library/taste. */
class ClearRecommendationExposure(
    private val repository: RecommendationExposureRepository,
) {
    suspend fun await() {
        repository.deleteAll()
    }
}
// KMK <--

package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.repository.RecommendationExposureRepository

// KMK_CLAUDE_LATEST_EXPLORATION_STRUCTURAL_COMPLETION_2026-08-08 -->
class RecordRecommendationExposure(
    private val repository: RecommendationExposureRepository,
) {
    suspend fun await(keys: List<Pair<Long, String>>, mangaIds: Map<Pair<Long, String>, Long?>, timestamp: Long) {
        repository.recordExposureBatch(keys, mangaIds, timestamp)
    }
}
// KMK <--

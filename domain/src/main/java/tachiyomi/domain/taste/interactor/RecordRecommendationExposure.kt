package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.repository.RecommendationExposureRepository

// KMK -->
class RecordRecommendationExposure(
    private val repository: RecommendationExposureRepository,
) {
    suspend fun await(keys: List<Pair<Long, String>>, mangaIds: Map<Pair<Long, String>, Long?>, timestamp: Long) {
        repository.recordExposureBatch(keys, mangaIds, timestamp)
    }
}
// KMK <--

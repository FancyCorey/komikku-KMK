package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.model.RecommendationExposure
import tachiyomi.domain.taste.repository.RecommendationExposureRepository

// KMK -->
class GetRecommendationExposure(
    private val repository: RecommendationExposureRepository,
) {
    suspend fun awaitBySourceUrls(keys: List<Pair<Long, String>>): List<RecommendationExposure> =
        repository.getBySourceUrls(keys)

    suspend fun awaitBySource(sourceId: Long): List<RecommendationExposure> =
        repository.getBySource(sourceId)
}
// KMK <--

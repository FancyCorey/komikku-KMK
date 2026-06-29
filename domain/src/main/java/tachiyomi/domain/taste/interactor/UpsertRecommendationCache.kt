package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.model.RecommendationCacheEntry
import tachiyomi.domain.taste.repository.RecommendationCacheRepository

// KMK -->
class UpsertRecommendationCache(
    private val repository: RecommendationCacheRepository,
) {
    suspend fun await(entry: RecommendationCacheEntry) = repository.upsertCache(entry)
}
// KMK <--

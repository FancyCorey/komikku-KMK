package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.repository.TasteRepository

// KMK -->
class SetRecommendationSourceEnabled(
    private val repository: TasteRepository,
) {
    suspend fun await(sourceId: Long, enabled: Boolean) {
        if (enabled) {
            repository.enableSource(sourceId)
        } else {
            repository.disableSource(sourceId)
        }
    }
}
// KMK <--

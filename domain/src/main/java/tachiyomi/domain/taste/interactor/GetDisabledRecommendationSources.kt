package tachiyomi.domain.taste.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.taste.repository.TasteRepository

// KMK -->
class GetDisabledRecommendationSources(
    private val repository: TasteRepository,
) {
    suspend fun await(): List<Long> = repository.getAllDisabledSourceIds()

    fun subscribe(): Flow<List<Long>> = repository.getAllDisabledSourceIdsAsFlow()
}
// KMK <--

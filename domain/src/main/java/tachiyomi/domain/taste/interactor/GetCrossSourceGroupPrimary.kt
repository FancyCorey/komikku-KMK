package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.model.CrossSourceGroupPrimary
import tachiyomi.domain.taste.repository.TasteRepository

// KMK --> v0.8.0
class GetCrossSourceGroupPrimary(
    private val repository: TasteRepository,
) {
    suspend fun awaitByGroupId(groupId: String): CrossSourceGroupPrimary? =
        repository.getCrossSourceGroupPrimary(groupId)

    suspend fun awaitAll(): List<CrossSourceGroupPrimary> =
        repository.getAllCrossSourceGroupPrimaries()
}
// KMK <--

package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.repository.TasteRepository

// KMK --> v0.8.0
class ClearCrossSourceGroupPrimary(
    private val repository: TasteRepository,
) {
    suspend fun await(groupId: String) = repository.deleteCrossSourceGroupPrimary(groupId)
}
// KMK <--

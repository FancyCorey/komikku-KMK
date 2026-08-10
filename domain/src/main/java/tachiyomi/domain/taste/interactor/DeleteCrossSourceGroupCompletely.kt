package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.repository.TasteRepository

// KMK v0.8.20 -->
/** Atomically deletes every link and the primary-version row for a group. See [TasteRepository.deleteCrossSourceGroupCompletely]. */
class DeleteCrossSourceGroupCompletely(
    private val repository: TasteRepository,
) {
    suspend fun await(groupId: String) = repository.deleteCrossSourceGroupCompletely(groupId)
}
// KMK <--

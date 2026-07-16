package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.model.CrossSourceGroupPrimary
import tachiyomi.domain.taste.repository.TasteRepository

// KMK --> v0.8.0
class SetCrossSourceGroupPrimary(
    private val repository: TasteRepository,
) {
    suspend fun await(groupId: String, source: Long, url: String) {
        repository.upsertCrossSourceGroupPrimary(
            CrossSourceGroupPrimary(
                groupId = groupId,
                source = source,
                url = url,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }
}
// KMK <--

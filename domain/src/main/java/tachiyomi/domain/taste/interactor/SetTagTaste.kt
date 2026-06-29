package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.model.TagPreference
import tachiyomi.domain.taste.model.TagTaste
import tachiyomi.domain.taste.model.normalizeTag
import tachiyomi.domain.taste.repository.TasteRepository

// KMK -->
class SetTagTaste(
    private val repository: TasteRepository,
) {
    suspend fun await(displayName: String, preference: TagPreference) {
        val now = System.currentTimeMillis()
        val normalized = displayName.normalizeTag()
        repository.upsertTagTaste(
            TagTaste(
                normalizedTag = normalized,
                displayName = displayName,
                preference = preference.value,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }
}
// KMK <--

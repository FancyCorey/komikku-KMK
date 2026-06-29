package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.repository.TasteRepository

// KMK -->
class ClearTagTaste(
    private val repository: TasteRepository,
) {
    suspend fun await(normalizedTag: String) = repository.deleteTagTaste(normalizedTag)
}
// KMK <--

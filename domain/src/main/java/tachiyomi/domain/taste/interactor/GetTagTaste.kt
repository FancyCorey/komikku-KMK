package tachiyomi.domain.taste.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.taste.model.TagTaste
import tachiyomi.domain.taste.repository.TasteRepository

// KMK -->
class GetTagTaste(
    private val repository: TasteRepository,
) {
    suspend fun await(normalizedTag: String): TagTaste? = repository.getTagTaste(normalizedTag)

    suspend fun awaitAll(): List<TagTaste> = repository.getAllTagTastes()

    fun subscribeAll(): Flow<List<TagTaste>> = repository.getAllTagTastesAsFlow()
}
// KMK <--

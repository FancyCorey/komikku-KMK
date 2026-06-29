package tachiyomi.domain.taste.interactor

// KMK --> v0.7.27: delete quality signal records from the Best Version history
import tachiyomi.domain.taste.repository.MangaSourceQualitySignalRepository

class DeleteMangaSourceQualitySignal(
    private val repository: MangaSourceQualitySignalRepository,
) {
    suspend fun await(id: Long) = repository.deleteById(id)
    suspend fun awaitAll() = repository.deleteAll()
}
// KMK <--

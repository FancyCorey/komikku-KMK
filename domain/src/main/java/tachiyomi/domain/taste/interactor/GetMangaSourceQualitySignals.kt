package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.model.MangaSourceQualitySignal
import tachiyomi.domain.taste.repository.MangaSourceQualitySignalRepository

// KMK --> v0.7.8
class GetMangaSourceQualitySignals(
    private val repository: MangaSourceQualitySignalRepository,
) {
    suspend fun getAll(): List<MangaSourceQualitySignal> = repository.getAll()

    suspend fun getByOrigin(originSourceId: Long, originUrl: String): List<MangaSourceQualitySignal> =
        repository.getByOrigin(originSourceId, originUrl)

    suspend fun getBySelectedSource(selectedSourceId: Long): List<MangaSourceQualitySignal> =
        repository.getBySelectedSource(selectedSourceId)
}
// KMK <--

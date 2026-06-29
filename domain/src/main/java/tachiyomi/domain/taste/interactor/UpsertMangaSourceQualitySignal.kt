package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.model.MangaSourceQualitySignal
import tachiyomi.domain.taste.repository.MangaSourceQualitySignalRepository

// KMK --> v0.7.8
class UpsertMangaSourceQualitySignal(
    private val repository: MangaSourceQualitySignalRepository,
) {
    suspend fun insert(signal: MangaSourceQualitySignal) = repository.insert(signal)
}
// KMK <--

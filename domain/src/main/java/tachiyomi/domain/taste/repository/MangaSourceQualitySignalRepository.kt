package tachiyomi.domain.taste.repository

import tachiyomi.domain.taste.model.MangaSourceQualitySignal

// KMK --> v0.7.8
interface MangaSourceQualitySignalRepository {
    suspend fun getAll(): List<MangaSourceQualitySignal>
    suspend fun getByOrigin(originSourceId: Long, originUrl: String): List<MangaSourceQualitySignal>
    suspend fun getBySelectedSource(selectedSourceId: Long): List<MangaSourceQualitySignal>
    suspend fun insert(signal: MangaSourceQualitySignal)
    suspend fun deleteById(id: Long)
    suspend fun deleteAll()
}
// KMK <--

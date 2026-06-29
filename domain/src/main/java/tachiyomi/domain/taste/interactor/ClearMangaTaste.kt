package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.repository.TasteRepository

// KMK -->
class ClearMangaTaste(
    private val repository: TasteRepository,
) {
    suspend fun await(mangaId: Long) = repository.deleteMangaTaste(mangaId)

    suspend fun await(source: Long, url: String) = repository.deleteMangaTaste(source, url)
}
// KMK <--

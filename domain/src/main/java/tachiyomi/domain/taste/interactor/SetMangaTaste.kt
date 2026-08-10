package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaTaste
import tachiyomi.domain.taste.repository.TasteRepository

// KMK -->
class SetMangaTaste(
    private val repository: TasteRepository,
) {
    suspend fun await(mangaId: Long, source: Long, url: String, title: String, rating: MangaRating) {
        val now = System.currentTimeMillis()
        repository.upsertMangaTaste(
            MangaTaste(
                mangaId = mangaId,
                source = source,
                url = url,
                title = title,
                rating = rating.value,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }
}
// KMK <--

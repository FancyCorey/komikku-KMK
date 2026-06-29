package tachiyomi.domain.taste.interactor

import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaTaste
import tachiyomi.domain.taste.repository.TasteRepository

// KMK -->
class SetMangaTasteBatch(
    private val repository: TasteRepository,
) {
    suspend fun await(mangas: List<Manga>, rating: MangaRating) {
        val now = System.currentTimeMillis()
        mangas.forEach { manga ->
            repository.upsertMangaTaste(
                MangaTaste(
                    mangaId = manga.id,
                    source = manga.source,
                    url = manga.url,
                    title = manga.title,
                    rating = rating.value,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }
}
// KMK <--

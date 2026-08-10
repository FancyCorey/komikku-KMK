package tachiyomi.domain.taste.interactor

// KMK --> v0.7.26: batch chapter count lookup for minimum-chapter filter
import tachiyomi.domain.manga.repository.MangaRepository

/**
 * Returns a map of mangaId → locally-stored chapter count for each given ID.
 * Uses local DB only. Does not fetch chapter lists from sources.
 * Fails open — callers should catch and treat failures as empty map.
 */
class GetChapterCountsByMangaIds(
    private val mangaRepository: MangaRepository,
) {
    suspend fun await(mangaIds: Collection<Long>): Map<Long, Long> =
        mangaRepository.getChapterCountsByMangaIds(mangaIds)
}
// KMK <--

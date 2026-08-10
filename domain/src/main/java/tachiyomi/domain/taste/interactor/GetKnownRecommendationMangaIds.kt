package tachiyomi.domain.taste.interactor

// KMK -->
import tachiyomi.domain.manga.repository.MangaRepository

/**
 * Returns the subset of the given manga IDs that are "known" to the user locally:
 * rated, in library/favorite, has at least one read or started chapter, or has history.
 *
 * Uses local DB data only. Does not call trackers or fetch chapter lists.
 * Fails open — if the query throws, callers should catch and return emptySet().
 */
class GetKnownRecommendationMangaIds(
    private val mangaRepository: MangaRepository,
) {
    suspend fun await(mangaIds: Collection<Long>): Set<Long> =
        mangaRepository.getKnownRecommendationMangaIds(mangaIds)
}
// KMK <--

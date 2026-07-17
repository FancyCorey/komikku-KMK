package tachiyomi.domain.taste.interactor

import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.taste.repository.TasteRepository

// KMK v0.8.10 -->
/**
 * Read-only interactor wiring real repository data into [TasteSuggestionAggregator]. Never mutates
 * anything -- adding a suggestion is a separate, existing call to [SetTagTaste], the same mutation
 * path a manually-added tag preference already uses.
 */
class GetTasteSuggestions(
    private val tasteRepository: TasteRepository,
    private val mangaRepository: MangaRepository,
) {
    suspend fun await(): TasteSuggestionResult {
        val tastes = tasteRepository.getAllMangaTastes()
        val aliases = tasteRepository.getAllTagAliases()
        val alreadySetNormalizedTags = tasteRepository.getAllTagTastes().map { it.normalizedTag }

        val ratedManga = tastes.mapNotNull { taste ->
            val genres = runCatching { mangaRepository.getMangaById(taste.mangaId).genre }.getOrNull()
                ?: return@mapNotNull null
            RatedMangaGenres(ratingValue = taste.rating, genres = genres)
        }

        return TasteSuggestionAggregator.aggregate(
            ratedManga = ratedManga,
            aliases = aliases,
            alreadySetNormalizedTags = alreadySetNormalizedTags,
        )
    }
}
// KMK <--

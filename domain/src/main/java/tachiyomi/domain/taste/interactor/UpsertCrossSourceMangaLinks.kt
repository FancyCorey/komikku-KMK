package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.model.CrossSourceMangaLink
import tachiyomi.domain.taste.repository.TasteRepository

// KMK --> v0.7.0: Phase 4 – persistent cross-source manga link groups
class UpsertCrossSourceMangaLinks(
    private val repository: TasteRepository,
) {
    suspend fun await(links: List<CrossSourceMangaLink>) =
        repository.upsertCrossSourceMangaLinks(links)
}
// KMK <--

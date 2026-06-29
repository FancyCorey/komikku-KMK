package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.model.CrossSourceMangaLink
import tachiyomi.domain.taste.repository.TasteRepository

// KMK --> v0.7.0: Phase 4 – persistent cross-source manga link groups
class GetCrossSourceMangaLinks(
    private val repository: TasteRepository,
) {
    suspend fun awaitByGroupId(groupId: String): List<CrossSourceMangaLink> =
        repository.getCrossSourceMangaLinksByGroupId(groupId)

    suspend fun awaitBySourceUrl(source: Long, url: String): CrossSourceMangaLink? =
        repository.getCrossSourceMangaLinkBySourceUrl(source, url)

    suspend fun awaitAll(): List<CrossSourceMangaLink> =
        repository.getAllCrossSourceMangaLinks()
}
// KMK <--

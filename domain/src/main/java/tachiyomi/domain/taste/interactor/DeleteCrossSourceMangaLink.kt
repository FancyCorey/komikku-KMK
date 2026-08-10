package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.repository.TasteRepository

// KMK --> v0.7.0: Phase 4 – persistent cross-source manga link groups
class DeleteCrossSourceMangaLink(
    private val repository: TasteRepository,
) {
    suspend fun awaitBySourceUrl(source: Long, url: String) =
        repository.deleteCrossSourceMangaLink(source, url)

    suspend fun awaitByGroupId(groupId: String) =
        repository.deleteCrossSourceMangaLinksByGroupId(groupId)
}
// KMK <--

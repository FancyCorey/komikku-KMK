package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.model.CrossSourceGroupPrimary
import tachiyomi.domain.taste.model.CrossSourceMangaLink
import tachiyomi.domain.taste.repository.TasteRepository

// KMK v0.8.20 -->
/** Typed, transactional restore for the group-action Undo Journal. See [TasteRepository.restoreCrossSourceGroupState]. */
class RestoreCrossSourceGroupState(
    private val repository: TasteRepository,
) {
    suspend fun await(
        linkUpserts: List<CrossSourceMangaLink>,
        linkDeletes: List<Pair<Long, String>>,
        primaryUpserts: List<CrossSourceGroupPrimary>,
        primaryDeletes: List<String>,
    ) = repository.restoreCrossSourceGroupState(linkUpserts, linkDeletes, primaryUpserts, primaryDeletes)
}
// KMK <--

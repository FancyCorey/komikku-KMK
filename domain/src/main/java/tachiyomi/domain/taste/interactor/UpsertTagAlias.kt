package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.model.TagAlias
import tachiyomi.domain.taste.model.normalizeTag
import tachiyomi.domain.taste.repository.TasteRepository

// KMK -->
class UpsertTagAlias(
    private val repository: TasteRepository,
) {
    suspend fun await(alias: String, groupKey: String, displayName: String) {
        repository.upsertTagAlias(
            TagAlias(
                alias = alias,
                normalizedAlias = alias.normalizeTag(),
                groupKey = groupKey,
                displayName = displayName,
            ),
        )
    }
}
// KMK <--

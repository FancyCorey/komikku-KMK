package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.model.TagAlias
import tachiyomi.domain.taste.repository.TasteRepository

// KMK -->
class GetTagAliases(
    private val repository: TasteRepository,
) {
    suspend fun awaitAll(): List<TagAlias> = repository.getAllTagAliases()

    suspend fun awaitByNormalized(normalizedAlias: String): TagAlias? =
        repository.getTagAliasByNormalized(normalizedAlias)

    /** Returns a map of normalizedAlias -> groupKey for fast lookup. */
    suspend fun awaitAliasMap(): Map<String, String> =
        repository.getAllTagAliases().associate { it.normalizedAlias to it.groupKey }

    /** Returns a map of groupKey -> list of normalizedAlias strings for query expansion. */
    suspend fun awaitGroupToAliasesMap(): Map<String, List<String>> =
        repository.getAllTagAliases().groupBy({ it.groupKey }) { it.normalizedAlias }
}
// KMK <--

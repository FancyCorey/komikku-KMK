package mihon.feature.migration.list.search

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceRuntime
import eu.kanade.tachiyomi.source.SourceRuntimeOperation
import eu.kanade.tachiyomi.source.getOrThrowSourceRuntimeException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import mihon.domain.manga.model.toDomainManga
import tachiyomi.domain.manga.model.Manga

class SmartSourceSearchEngine(extraSearchParams: String?) : BaseSmartSearchEngine<SManga>(extraSearchParams) {

    override fun getTitle(result: SManga) = result.originalTitle

    suspend fun regularSearch(source: Source, title: String): Manga? {
        return regularSearch(makeSearchAction(source), title).let {
            it?.toDomainManga(source.id)
        }
    }

    suspend fun deepSearch(source: Source, title: String): Manga? {
        return deepSearch(makeSearchAction(source), title).let {
            it?.toDomainManga(source.id)
        }
    }

    // KMK v0.8.10-fix6: migration/smart-search can invoke a broken extension's lazy client builder
    // just like Browse/For You -- route both the filter-list and search calls through the shared
    // SourceRuntime boundary instead of calling the source directly.
    private fun makeSearchAction(source: Source): SearchAction<SManga> = { query ->
        val filters = SourceRuntime.run(source, SourceRuntimeOperation.FilterList) {
            getFilterList()
        }.getOrElse { FilterList() }

        SourceRuntime.run(source, SourceRuntimeOperation.Search) {
            getSearchManga(1, query, filters)
        }.getOrThrowSourceRuntimeException().mangas
    }
}

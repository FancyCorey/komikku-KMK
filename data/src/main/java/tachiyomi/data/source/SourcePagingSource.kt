package tachiyomi.data.source

import androidx.paging.PagingState
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.isRecoverableSourceRuntimeFailure
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.MetadataMangasPage
import eu.kanade.tachiyomi.source.unwrapSourceRuntimeCause
import exh.log.xLogE
import exh.metadata.metadata.RaisedSearchMetadata
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.QuerySanitizer.sanitize
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.repository.SourcePagingSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SourceSearchPagingSource(
    source: Source,
    private val query: String,
    private val filters: FilterList,
) : BaseSourcePagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        return source.getSearchManga(currentPage, query.sanitize(), filters)
    }
}

class SourcePopularPagingSource(source: Source) : BaseSourcePagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        return source.getPopularManga(currentPage)
    }
}

class SourceLatestPagingSource(source: Source) : BaseSourcePagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        return source.getLatestUpdates(currentPage)
    }
}

abstract class BaseSourcePagingSource(
    protected val source: Source,
    protected val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
) : SourcePagingSource() {

    protected val seenManga = hashSetOf<String>()

    abstract suspend fun requestNextPage(currentPage: Int): MangasPage

    override suspend fun load(
        params: LoadParams<Long>,
    ): LoadResult<Long, /*SY --> */ Pair<Manga, RaisedSearchMetadata?>/*SY <-- */> {
        val page = params.key ?: 1

        return try {
            val mangasPage = withIOContext {
                requestNextPage(page.toInt())
                    .takeIf { it.mangas.isNotEmpty() }
                    ?: throw NoResultsException()
            }

            // SY -->
            getPageLoadResult(params, mangasPage)
            // SY <--
        } catch (e: Exception) {
            xLogE("Source paging load failed")
            LoadResult.Error(e)
        } catch (e: Error) {
            // KMK v0.8.10-fix3: a broken/incompletely-packaged extension (e.g. a missing runtime
            // dependency lazily touched while constructing its HTTP client) throws a LinkageError,
            // which is an Error, not an Exception -- this catch block previously let it escape
            // uncaught and crash Browse/search paging entirely instead of becoming a page load
            // error. Genuinely fatal VM errors (OutOfMemoryError, StackOverflowError, ...) are still
            // rethrown, exactly as before this fix. Uses the pure classifier shared with `app`'s
            // SourceRuntime via core:common (see SourceRuntimeClassifier.kt) -- this `data`-module
            // file cannot depend on `app`, so it cannot use SourceRuntime.run() itself, but the
            // classification decision is identical either way, not a divergent copy.
            val unwrapped = e.unwrapSourceRuntimeCause()
            if (!unwrapped.isRecoverableSourceRuntimeFailure()) throw e
            xLogE("Source paging linkage failure")
            LoadResult.Error(unwrapped)
        }
    }

    // SY -->
    open suspend fun getPageLoadResult(
        params: LoadParams<Long>,
        mangasPage: MangasPage,
    ): LoadResult.Page<Long, /*SY --> */ Pair<Manga, RaisedSearchMetadata?>/*SY <-- */> {
        val page = params.key ?: 1

        // SY -->
        val metadata = if (mangasPage is MetadataMangasPage) {
            mangasPage.mangasMetadata
        } else {
            emptyList()
        }
        // SY <--

        val manga = mangasPage.mangas
            // SY -->
            .mapIndexed { index, sManga -> sManga.toDomainManga(source.id) to metadata.getOrNull(index) }
            .filter { seenManga.add(it.first.url) }
            // KMK -->
            .let { pairs -> networkToLocalManga(pairs.map { it.first }).zip(pairs.map { it.second }) }
        // KMK <--
        // SY <--

        return LoadResult.Page(
            data = manga,
            prevKey = null,
            nextKey = if (mangasPage.hasNextPage) page + 1 else null,
        )
    }
    // SY <--

    override fun getRefreshKey(
        state: PagingState<Long, /*SY --> */ Pair<Manga, RaisedSearchMetadata?>/*SY <-- */>,
    ): Long? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey ?: anchorPage?.nextKey
        }
    }
}

class NoResultsException : Exception()

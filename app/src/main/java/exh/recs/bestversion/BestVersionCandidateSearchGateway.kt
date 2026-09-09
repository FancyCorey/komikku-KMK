package exh.recs.bestversion

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.Source
import exh.recs.matching.SameMangaCandidateSearcher
import exh.recs.matching.SameMangaMatchSettings
import exh.recs.matching.SameMangaSourceResult
import kotlinx.coroutines.CoroutineDispatcher
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager

/** Narrow candidate-search boundary used by the Best Version route and deterministic host fixtures. */
interface BestVersionCandidateSearchGateway {
    fun getMatchingSources(): List<Source>

    suspend fun search(
        queries: List<String>,
        settings: SameMangaMatchSettings,
        originManga: Manga,
        sources: List<Source>,
        onResult: suspend (SameMangaSourceResult) -> Unit,
    )
}

class SameMangaBestVersionCandidateSearchGateway(
    sourcePreferences: SourcePreferences,
    sourceManager: SourceManager,
    networkToLocalManga: NetworkToLocalManga,
    coroutineDispatcher: CoroutineDispatcher,
) : BestVersionCandidateSearchGateway {
    private val delegate = SameMangaCandidateSearcher(
        sourcePreferences = sourcePreferences,
        sourceManager = sourceManager,
        networkToLocalManga = networkToLocalManga,
        coroutineDispatcher = coroutineDispatcher,
    )

    override fun getMatchingSources(): List<Source> = delegate.getMatchingSources()

    override suspend fun search(
        queries: List<String>,
        settings: SameMangaMatchSettings,
        originManga: Manga,
        sources: List<Source>,
        onResult: suspend (SameMangaSourceResult) -> Unit,
    ) {
        delegate.search(queries, settings, originManga, sources, onResult)
    }
}

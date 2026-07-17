package exh.recs.matching

// KMK --> v0.7.8
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.Source
import exh.recs.RecommendationSourceFilter
import exh.recs.RecommendationSourceOrdering
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.QuerySanitizer.sanitize
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.Executors

/**
 * Shared bounded same-manga candidate search used by
 * [CrossExtensionMatchScreenModel] and [BestVersionCompareScreenModel].
 *
 * Applies recommendation-language filtering, source priority ordering,
 * per-source result cap, origin filtering, and multi-query deduplication.
 * Does NOT affect normal global search.
 */
class SameMangaCandidateSearcher(
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
) {
    private val coroutineDispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher()

    fun getMatchingSources(): List<Source> {
        val recLanguages = RecommendationSourceFilter.normalizeLanguages(
            sourcePreferences.recommendationSourceLanguages().get(),
        )
        val storedOrder = RecommendationSourceOrdering.parse(
            sourcePreferences.recommendationSourceOrder().get(),
        )
        val disabledSourceIds = sourcePreferences.disabledSources().get()
            .mapNotNull { it.toLongOrNull() }.toSet()
        val all = sourceManager.getVisibleSources()
        val filtered = RecommendationSourceFilter.filterForRecommendations(all, recLanguages)
        return RecommendationSourceOrdering.apply(filtered, storedOrder, disabledSourceIds)
    }

    /**
     * Searches [sources] (or all matching sources if null) with [queries] using the configured
     * per-source cap from [SameMangaMatchSettings]. The [originManga] is excluded from results.
     * Returns one [SameMangaSourceResult] per source.
     */
    suspend fun search(
        queries: List<String>,
        settings: SameMangaMatchSettings,
        originManga: Manga,
        sources: List<Source> = getMatchingSources(),
        onResult: suspend (SameMangaSourceResult) -> Unit,
    ) = coroutineScope {
        val cap = SameMangaMatchSettings.clampResultCap(settings.resultsPerSource)

        sources.map { source ->
            async {
                val result = searchOneSource(source, queries, cap, originManga)
                onResult(SameMangaSourceResult(source, result))
            }
        }.awaitAll()
    }

    private suspend fun searchOneSource(
        source: Source,
        queries: List<String>,
        cap: Int,
        originManga: Manga,
    ): SameMangaCandidateResult {
        return try {
            val seen = LinkedHashMap<String, Manga>()
            var lastError: Exception? = null
            for (query in queries) {
                if (!coroutineScope { isActive } && seen.isEmpty()) break
                if (seen.size >= cap) break
                try {
                    val page = withContext(coroutineDispatcher) {
                        source.getSearchManga(1, query.sanitize(), source.getFilterList())
                    }
                    val resolved = page.mangas
                        .map { it.toDomainManga(source.id) }
                        .distinctBy { it.url }
                        .let { networkToLocalManga(it) }
                        .filterNot { it.source == originManga.source && it.url == originManga.url }
                    for (manga in resolved) {
                        if (seen.size >= cap) break
                        seen.putIfAbsent(manga.url, manga)
                    }
                } catch (e: Exception) {
                    lastError = e
                }
            }
            when {
                seen.isNotEmpty() -> SameMangaCandidateResult.Success(seen.values.toList())
                lastError != null -> SameMangaCandidateResult.Error(lastError)
                else -> SameMangaCandidateResult.Success(emptyList())
            }
        } catch (e: Exception) {
            SameMangaCandidateResult.Error(e)
        }
    }
}
// KMK <--

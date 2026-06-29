package exh.recs.sources

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.source.NoResultsException
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.kmk.KMR
import xyz.nulldev.ts.api.http.serializer.FilterSerializer

// KMK -->
/**
 * A [RecommendationPagingSource] that searches a single installed [CatalogueSource] by the
 * source manga's genres, then scores and sorts the results using [exh.recs.RecommendationScorer].
 *
 * One instance is created per visible catalogue source when the cross-extension search preference
 * is enabled. All instances run in the existing [exh.recs.RecommendsScreenModel] async/awaitAll
 * loop — no structural changes to the screen model are needed.
 *
 * When the user taps the row header on the recommendations screen, they are taken directly to
 * [eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen] with the genre filters
 * pre-applied via [cachedFiltersJson], rather than using the BrowseRecommendsScreen drill-down
 * (which cannot distinguish multiple instances of the same class by name).
 */
internal class CrossExtensionGenreSearchSource(
    manga: Manga,
    private val catalogueSource: CatalogueSource,
) : RecommendationPagingSource(
    manga,
    RecommendationSource(catalogueSource.id),
) {
    override val name: String get() = catalogueSource.name
    override val category: StringResource get() = KMR.strings.rec_extension_search
    override val associatedSourceId: Long get() = catalogueSource.id

    /**
     * Serialized [FilterList] JSON, populated after [requestNextPage] completes.
     * Used by [exh.recs.RecommendsScreen] to open [eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen]
     * with genre filters pre-applied when the user taps the row header.
     */
    var cachedFiltersJson: String? = null
        private set

    /**
     * Text query fallback for genres that could not be mapped to filters.
     * Also passed to [eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen].
     */
    var cachedTextQuery: String = ""
        private set

    private val filterSerializer = FilterSerializer()

    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        val desiredGenres = manga.genre.orEmpty()

        // When the source manga has no genres, fall back to a title search so the row still
        // returns relevant results instead of an empty or random popular-manga result.
        if (desiredGenres.isEmpty()) {
            cachedTextQuery = manga.ogTitle
            cachedFiltersJson = null
            val fallbackPage = catalogueSource.getSearchManga(1, manga.ogTitle, FilterList())
            if (fallbackPage.mangas.isEmpty()) throw NoResultsException()
            return MangasPage(fallbackPage.mangas, false)
        }

        // Step 1: get filter list (local call into extension APK — no network in most extensions)
        val filterList = try {
            catalogueSource.getFilterList()
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) {
                "CrossExtensionGenreSearch[${catalogueSource.name}]: getFilterList failed, falling back to empty"
            }
            FilterList()
        }

        // Step 2: map genres to filters; unmatched genres become a text query
        val searchParams = GenreFilterMapper.buildSearch(filterList, desiredGenres)
        cachedTextQuery = searchParams.textQuery

        // Serialize the mutated filter state for row-header navigation to BrowseSourceScreen.
        // FilterSerializer.serialize() captures the current (genre-enabled) state of the filters.
        // BrowseSourceScreen deserializes this JSON into a fresh FilterList on the receiving end.
        cachedFiltersJson = runCatching {
            filterSerializer.serialize(searchParams.filters).toString()
        }.getOrNull()

        // Step 3: search the extension (one network call)
        val mangasPage = try {
            catalogueSource.getSearchManga(1, searchParams.textQuery, searchParams.filters)
        } catch (e: Exception) {
            if (e !is NoResultsException) {
                logcat(LogPriority.ERROR, e) {
                    "CrossExtensionGenreSearch[${catalogueSource.name}]: search failed"
                }
            }
            throw e
        }

        if (mangasPage.mangas.isEmpty()) throw NoResultsException()

        // Step 4: enrich the top results to populate genre data so RecommendationScorer has
        // something to compare against. Capped at MAX_ENRICH_PER_SOURCE to limit network calls.
        // Results are mutated in-place; SManga.genre is set from the details response.
        coroutineScope {
            mangasPage.mangas
                .take(MAX_ENRICH_PER_SOURCE)
                .filter { it.genre.isNullOrEmpty() }
                .map { smanga ->
                    async {
                        runCatching {
                            val details = catalogueSource.getMangaDetails(smanga)
                            smanga.genre = details.genre
                            smanga.description = details.description
                            smanga.status = details.status
                        }.onFailure { e ->
                            logcat(LogPriority.WARN, e) {
                                "CrossExtensionGenreSearch[${catalogueSource.name}]: getMangaDetails failed for ${smanga.title}"
                            }
                        }
                    }
                }.awaitAll()
        }

        return MangasPage(mangasPage.mangas, false)
    }

    companion object {
        private const val MAX_ENRICH_PER_SOURCE = 10
    }
}
// KMK <--

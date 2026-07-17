package exh.recs.sources

import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import exh.md.similar.MangaDexSimilarPagingSource
import exh.source.COMICK_IDS
import exh.source.MANGADEX_IDS
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.source.BaseSourcePagingSource
import tachiyomi.data.source.NoResultsException
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.i18n.sy.SYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

/**
 * General class for recommendation sources.
 */
abstract class RecommendationPagingSource(
    protected val manga: Manga,
    // KMK -->
    source: RecommendationSource = RecommendationSource(),
    // KMK <--
) : BaseSourcePagingSource(source) {
    // Display name
    abstract val name: String

    // Localized category name
    open val category: StringResource = SYMR.strings.similar_titles

    /**
     * Recommendation sources that display results from a source extension,
     * can override this property to associate results with a specific source.
     * This is used to redirect the user directly to the corresponding MangaScreen.
     * If null, the user will be prompted to choose a source via SmartSearch when clicking on a recommendation.
     */
    open val associatedSourceId: Long? = null

    companion object {
        // KMK -->
        private const val MAX_CROSS_EXTENSION_SOURCES = 20
        // KMK <--

        internal fun createSources(
            manga: Manga,
            // KMK -->
            recommendationSource: RecommendationSource,
            // KMK --> v0.7.43: group-seeded recommendations pass the combined/weighted group tag
            // list here so cross-extension rows search by the whole group's genres, not just
            // this one manga's. Null (single-manga path) keeps using manga.genre as before.
            groupGenreOverride: List<String>? = null,
            // KMK --> v0.7.44: group-seeded recommendations pass the combined/alternate group title
            // list so a source that has no useful tag match can still fall back to a title search
            // across every linked version, not just the primary manga's title.
            groupTitlesOverride: List<String>? = null,
            // KMK --> v0.7.44: when non-null (group-seeded path only), used instead of
            // `sourceManager.getVisibleSources().take(...)` for cross-extension rows, so
            // group recommendations honor the same language/priority/disabled/disliked source
            // policy as For You (RecommendationSourceSelector). Computed by the caller (already
            // running in a coroutine) so this function can stay non-suspend. Null (single-manga
            // path) keeps the existing raw visible-source behavior unchanged.
            eligibleCrossExtensionSources: List<Source>? = null,
            // KMK <--
            // KMK v0.8.6: shared across every CrossExtensionGenreSearchSource created by this call so
            // nested detail-enrichment requests are bounded by one total budget across all
            // concurrently active GROUP_PREVIEW sources, not per-source. Null (single-manga path)
            // keeps each source's enrichment unbounded-within-itself, same as before.
            sharedEnrichmentSemaphore: kotlinx.coroutines.sync.Semaphore? = null,
        ): List<RecommendationPagingSource> {
            return buildList {
                add(AniListPagingSource(manga))
                add(MangaUpdatesCommunityPagingSource(manga))
                add(MangaUpdatesSimilarPagingSource(manga))
                add(MyAnimeListPagingSource(manga))

                // Only include MangaDex if the delegate sources are enabled and the source is MD-based
                if (
                    // KMK -->
                    recommendationSource.isMangaDexSource()
                    // KMK <--
                ) {
                    add(
                        MangaDexSimilarPagingSource(
                            manga,
                            // KMK -->
                            recommendationSource,
                            // KMK <--
                        ),
                    )
                }

                // Only include Comick if the source manga is from there
                if (
                    // KMK -->
                    recommendationSource.isComickSource()
                    // KMK <--
                ) {
                    add(
                        ComickPagingSource(
                            manga,
                            // KMK -->
                            recommendationSource,
                            // KMK <--
                        ),
                    )
                }

                // KMK -->
                // When cross-extension search is enabled, add one source per installed extension
                // (capped at MAX_CROSS_EXTENSION_SOURCES). Each source searches by genre and
                // enriches the top results so RecommendationScorer has real genre data to compare.
                val sourcePreferences: SourcePreferences = Injekt.get()
                if (sourcePreferences.recommendationCrossExtensionSearch().get()) {
                    val crossExtensionSources = eligibleCrossExtensionSources ?: run {
                        val sourceManager: SourceManager = Injekt.get()
                        sourceManager.getVisibleSources().take(MAX_CROSS_EXTENSION_SOURCES)
                    }
                    crossExtensionSources.forEach { catalogueSource ->
                        add(
                            CrossExtensionGenreSearchSource(
                                manga,
                                catalogueSource,
                                groupGenreOverride,
                                groupTitlesOverride,
                                sharedEnrichmentSemaphore,
                            ),
                        )
                    }
                }
                // KMK <--
            }.sortedWith(compareBy({ it.name }, { it.category.resourceId }))
        }
    }
}

/**
 * General class for recommendation sources backed by trackers.
 */
abstract class TrackerRecommendationPagingSource(
    protected val endpoint: String,
    manga: Manga,
) : RecommendationPagingSource(manga) {
    private val getTracks: GetTracks by injectLazy()

    protected val trackerManager: TrackerManager by injectLazy()
    protected val client by lazy { Injekt.get<NetworkHelper>().client }
    protected val json by injectLazy<Json>()

    /**
     * Tracker id associated with the recommendation source.
     *
     * If not null and the tracker is attached to the source manga,
     * the remote id will be used to directly identify the manga on the tracker.
     * Otherwise, a search will be performed using the manga title.
     */
    abstract val associatedTrackerId: Long?

    abstract suspend fun getRecsBySearch(search: String): List<SManga>
    abstract suspend fun getRecsById(id: String): List<SManga>

    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        val tracks = getTracks.await(manga.id)

        val recs = try {
            val id = tracks.find { it.trackerId == associatedTrackerId }?.remoteId
            val results = if (id != null) {
                getRecsById(id.toString())
            } else {
                getRecsBySearch(manga.ogTitle)
            }
            logcat { name + " > Results: " + results.size }

            results.ifEmpty { throw NoResultsException() }
        } catch (e: Exception) {
            // 'No results' should not be logged as it happens frequently and is expected
            if (e !is NoResultsException) {
                logcat(LogPriority.ERROR, e) { name }
            }
            throw e
        }

        return MangasPage(recs, false)
    }
}

// KMK -->
class RecommendationSource(
    override val id: Long = RECOMMENDS_SOURCE,
    sourceManager: SourceManager = Injekt.get(),
) : Source {
    private val delegate by lazy {
        sourceManager.get(id)
    }

    fun isComickSource(): Boolean = id in COMICK_IDS
    fun isMangaDexSource(): Boolean = id in MANGADEX_IDS

    override val name: String by lazy { delegate?.name ?: "Recommends Source" }
    override val lang: String by lazy { delegate?.lang ?: "all" }
    override val supportsLatest by lazy { delegate?.supportsLatest ?: false }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ) = delegate?.getMangaUpdate(manga, chapters, fetchDetails, fetchChapters)
        ?: throw UnsupportedOperationException()
    override suspend fun getPageList(chapter: SChapter) =
        delegate?.getPageList(chapter)
            ?: throw UnsupportedOperationException()

    override suspend fun getPopularManga(page: Int) =
        delegate?.getPopularManga(page)
            ?: throw UnsupportedOperationException()
    override suspend fun getLatestUpdates(page: Int) =
        delegate?.getLatestUpdates(page)
            ?: throw UnsupportedOperationException()
    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList) =
        delegate?.getSearchManga(page, query, filters)
            ?: throw UnsupportedOperationException()
    override fun getFilterList() =
        delegate?.getFilterList()
            ?: throw UnsupportedOperationException()
}

const val RECOMMENDS_SOURCE = -1L
// KMK <--

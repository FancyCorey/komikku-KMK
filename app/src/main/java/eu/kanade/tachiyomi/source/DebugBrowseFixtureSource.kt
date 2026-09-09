package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseDeterministicFixtureException

/** In-memory debug-only source identity used to enter the Browse fixture route. */
class DebugBrowseFixtureSource : CatalogueSource {
    override val id: Long = ID
    override val name: String = "Browse debug fixture"
    override val lang: String = "en"
    override val supportsLatest: Boolean = false

    override fun getFilterList(): FilterList = FilterList()

    override suspend fun getPopularManga(page: Int): MangasPage = unavailable()
    override suspend fun getLatestUpdates(page: Int): MangasPage = unavailable()
    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage = unavailable()
    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = unavailable()
    override suspend fun getPageList(chapter: SChapter): List<Page> = unavailable()

    private fun unavailable(): Nothing = throw BrowseDeterministicFixtureException()

    companion object {
        const val ID: Long = -8_003_034L
    }
}

internal fun shouldExposeDebugBrowseFixture(isDebugBuild: Boolean, mode: String): Boolean =
    isDebugBuild && mode == "source_unavailable"

internal fun resolveDebugBrowseFixture(
    sourceKey: Long,
    isDebugBuild: Boolean,
    mode: String,
): DebugBrowseFixtureSource? =
    if (sourceKey == DebugBrowseFixtureSource.ID && shouldExposeDebugBrowseFixture(isDebugBuild, mode)) {
        DebugBrowseFixtureSource()
    } else {
        null
    }

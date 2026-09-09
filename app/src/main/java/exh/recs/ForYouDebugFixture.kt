package exh.recs

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceRuntimeFailureRegistry
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.taste.model.TasteProfile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Debug-only deterministic inputs for the real For You refresh route. */
enum class ForYouDebugFixtureMode(val prefValue: String) {
    OFF("off"),
    TOP_PICKS("top_picks"),
    TOP_PICKS_PARTIAL_FAILURE("top_picks_partial_failure"),
    ;

    companion object {
        fun fromPrefValue(value: String): ForYouDebugFixtureMode =
            entries.firstOrNull { it.prefValue == value } ?: OFF
    }
}

object ForYouDebugFixture {
    private const val SOURCE_A_ID = 910_000_000_000_000_101L
    private const val SOURCE_B_ID = 910_000_000_000_000_102L
    private const val SOURCE_C_ID = 910_000_000_000_000_103L

    private val requestCounts = ConcurrentHashMap<Long, AtomicInteger>()

    fun resolveMode(isDebugBuild: Boolean, preferenceValue: String): ForYouDebugFixtureMode =
        if (isDebugBuild) ForYouDebugFixtureMode.fromPrefValue(preferenceValue) else ForYouDebugFixtureMode.OFF

    fun usesSyntheticInputs(mode: ForYouDebugFixtureMode): Boolean = mode != ForYouDebugFixtureMode.OFF

    suspend fun profile(mode: ForYouDebugFixtureMode, realProfile: suspend () -> TasteProfile): TasteProfile =
        if (usesSyntheticInputs(mode)) {
            TasteProfile(
                learnedTagWeights = mapOf("action" to 4.0),
                explicitTagPreferences = mapOf("action" to 1),
                sourceAffinity = emptyMap(),
                blockedGroups = emptySet(),
            )
        } else {
            realProfile()
        }

    fun sources(mode: ForYouDebugFixtureMode, realSources: List<Source>): List<Source> {
        sourceIds(mode).forEach(SourceRuntimeFailureRegistry::clear)
        requestCounts.clear()
        return when (mode) {
            ForYouDebugFixtureMode.OFF -> realSources
            ForYouDebugFixtureMode.TOP_PICKS -> listOf(
                FixtureSource(SOURCE_A_ID, "Fixture Source A", page("alpha")),
                FixtureSource(SOURCE_B_ID, "Fixture Source B", page("beta")),
            )
            ForYouDebugFixtureMode.TOP_PICKS_PARTIAL_FAILURE -> listOf(
                FixtureSource(SOURCE_A_ID, "Fixture Source A", page("alpha")),
                FixtureSource(SOURCE_B_ID, "Fixture Source B") {
                    throw IllegalStateException("fixture-source-unavailable")
                },
                FixtureSource(SOURCE_C_ID, "Fixture Source C") {
                    throw IllegalStateException("fixture-source-unavailable")
                },
            )
        }
    }

    internal fun requestCountSnapshot(): Map<Long, Int> =
        requestCounts.mapValues { (_, count) -> count.get() }

    private fun recordRequest(sourceId: Long, sourceName: String) {
        val count = requestCounts.getOrPut(sourceId) { AtomicInteger() }.incrementAndGet()
        logcat(LogPriority.INFO, tag = "ForYouFixture") {
            "request source=$sourceName id=$sourceId count=$count"
        }
    }

    fun sourceIds(mode: ForYouDebugFixtureMode): Set<Long> = when (mode) {
        ForYouDebugFixtureMode.OFF -> emptySet()
        ForYouDebugFixtureMode.TOP_PICKS -> setOf(SOURCE_A_ID, SOURCE_B_ID)
        ForYouDebugFixtureMode.TOP_PICKS_PARTIAL_FAILURE -> setOf(SOURCE_A_ID, SOURCE_B_ID, SOURCE_C_ID)
    }

    private fun page(prefix: String): suspend (String) -> MangasPage = {
        MangasPage(
            mangas = (1..5).map { index ->
                SManga(
                    url = "/fixture/$prefix/$index",
                    title = "Fixture $prefix manga $index",
                    description = "Deterministic fixture candidate $index",
                    genre = "action",
                    status = SManga.ONGOING,
                    initialized = true,
                )
            },
            hasNextPage = false,
        )
    }

    private class FixtureSource(
        override val id: Long,
        override val name: String,
        private val search: suspend (String) -> MangasPage,
    ) : Source {
        override val lang = "en"
        override val supportsLatest = false

        override fun getFilterList() = FilterList()
        override suspend fun getPopularManga(page: Int): MangasPage {
            recordRequest(id, name)
            return search("popular")
        }
        override suspend fun getLatestUpdates(page: Int): MangasPage =
            throw UnsupportedOperationException("fixture-latest-not-supported")
        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
            recordRequest(id, name)
            return search(query)
        }
        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ) = SMangaUpdate(manga, chapters)
        override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
    }
}

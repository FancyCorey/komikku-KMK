package exh.recs.loved

// KMK -->
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.taste.interactor.GetCrossSourceMangaLinks
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.model.MangaTaste
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK --> v0.7.14: sort options for Loved Manga
enum class LoveSortMode { RECENT, OLDEST, TITLE_AZ, SOURCE }
// KMK <--

data class LovedMangaEntry(
    val taste: MangaTaste,
    val manga: Manga?,
)

@Immutable
data class LovedDisplayItem(
    val taste: MangaTaste,
    val manga: Manga?,
    val versionCount: Int,
)

class LovedMangaScreenModel(
    private val getMangaTaste: GetMangaTaste = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    // KMK --> v0.7.2: use confirmed cross-source link groups for grouping
    private val getCrossSourceMangaLinks: GetCrossSourceMangaLinks = Injekt.get(),
    // KMK <--
    // KMK --> v0.7.3: filter entries from uninstalled sources
    private val sourceManager: SourceManager = Injekt.get(),
    // KMK <--
) : StateScreenModel<LovedMangaScreenModel.State>(State.Loading) {

    init {
        // KMK --> v0.7.29: subscribe to live updates so the screen reacts to taste changes without manual refresh
        screenModelScope.launch {
            getMangaTaste.subscribeAll().collectLatest { allTastes -> load(allTastes) }
        }
        // KMK <--
    }

    private suspend fun load(allTastes: List<MangaTaste>) {
        runCatching {
            // KMK --> v0.7.3: fail-safe source id lookup; empty set → hides all rather than showing uninstalled entries
            val installedSourceIds: Set<Long> = runCatching {
                sourceManager.getVisibleCatalogueSources().map { it.id }.toSet()
            }.getOrDefault(emptySet())
            // KMK <--
            val lovedTastes = filterLovedTastesByInstalledSources(allTastes, installedSourceIds)
                .sortedByDescending { it.updatedAt }

            val entries = lovedTastes.map { taste ->
                val manga = getManga.await(taste.mangaId)
                    ?: getManga.await(taste.url, taste.source)
                LovedMangaEntry(taste = taste, manga = manga)
            }

            // KMK --> v0.7.2: load cross-source link groups; fail open with empty map on error
            val linkGroupByKey: Map<String, String> = runCatching {
                getCrossSourceMangaLinks.awaitAll()
                    .associate { "${it.source}|${it.url}" to it.groupId }
            }.getOrDefault(emptyMap())
            // KMK <--

            mutableState.value = if (entries.isEmpty()) {
                State.Empty
            } else {
                State.Success(entries = entries, groupDuplicates = false, linkGroupByKey = linkGroupByKey)
            }
        }.onFailure { e ->
            mutableState.value = State.Error(e)
        }
    }

    fun toggleGroupDuplicates() {
        val current = mutableState.value as? State.Success ?: return
        mutableState.value = current.copy(groupDuplicates = !current.groupDuplicates)
    }

    // KMK --> v0.7.14: sort mode toggle
    fun setSortMode(mode: LoveSortMode) {
        val current = mutableState.value as? State.Success ?: return
        mutableState.value = current.copy(sortMode = mode)
    }
    // KMK <--

    sealed interface State {
        data object Loading : State
        data object Empty : State
        data class Error(val error: Throwable) : State

        @Immutable
        data class Success(
            val entries: List<LovedMangaEntry>,
            val groupDuplicates: Boolean,
            // KMK --> v0.7.2: "source|url" → groupId, populated from manga_cross_source_link
            val linkGroupByKey: Map<String, String> = emptyMap(),
            // KMK <--
            // KMK --> v0.7.14: active sort mode; RECENT matches the load-time sort order
            val sortMode: LoveSortMode = LoveSortMode.RECENT,
            // KMK <--
        ) : State {
            val displayItems: List<LovedDisplayItem>
                // KMK --> v0.7.14: sort before grouping
                get() {
                    val sorted = sortEntries(entries, sortMode)
                    return if (groupDuplicates) buildGroupedItems(sorted, linkGroupByKey) else buildFlatItems(sorted)
                }
            // KMK <--
        }
    }
}

// KMK --> v0.7.14: sort entries according to the chosen sort mode
private fun sortEntries(entries: List<LovedMangaEntry>, mode: LoveSortMode): List<LovedMangaEntry> = when (mode) {
    LoveSortMode.RECENT -> entries // already sorted desc by updatedAt at load time
    LoveSortMode.OLDEST -> entries.reversed()
    LoveSortMode.TITLE_AZ -> entries.sortedBy { (it.manga?.title ?: it.taste.title).lowercase() }
    LoveSortMode.SOURCE -> entries.sortedBy { it.taste.source }
}
// KMK <--

private fun buildFlatItems(entries: List<LovedMangaEntry>): List<LovedDisplayItem> =
    entries.map { LovedDisplayItem(it.taste, it.manga, 1) }

// KMK --> v0.7.2: pass author, artist, and linkGroupId into the grouper
private fun buildGroupedItems(
    entries: List<LovedMangaEntry>,
    linkGroupByKey: Map<String, String>,
): List<LovedDisplayItem> {
    val inputs = entries.map { entry ->
        val key = "${entry.taste.source}|${entry.taste.url}"
        LovedMangaDuplicateGrouper.GroupInput(
            key = key,
            source = entry.taste.source,
            url = entry.taste.url,
            title = entry.manga?.title ?: entry.taste.title,
            description = entry.manga?.description.orEmpty(),
            author = entry.manga?.author,
            artist = entry.manga?.artist,
            linkGroupId = linkGroupByKey[key],
        )
    }
    val keyToEntry = entries.associateBy { "${it.taste.source}|${it.taste.url}" }
    return LovedMangaDuplicateGrouper.computeGroups(inputs).mapNotNull { group ->
        val primary = keyToEntry[group.primaryKey] ?: return@mapNotNull null
        LovedDisplayItem(primary.taste, primary.manga, group.versionCount)
    }
}
// KMK <--

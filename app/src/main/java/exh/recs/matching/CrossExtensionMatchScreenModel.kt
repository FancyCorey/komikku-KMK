package exh.recs.matching

// KMK -->
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.source.CatalogueSource
import exh.recs.RecommendationSourceFilter
import exh.recs.RecommendationSourceOrdering
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.QuerySanitizer.sanitize
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.toMangaUpdate
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.taste.interactor.SetMangaTasteBatch
import tachiyomi.domain.taste.interactor.UpsertCrossSourceMangaLinks
import tachiyomi.domain.taste.model.CrossSourceMangaLink
import tachiyomi.domain.taste.model.MangaRating
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.UUID
import java.util.concurrent.Executors

data class MangaIdentityKey(val source: Long, val url: String)

sealed interface CrossExtensionMatchMode {
    data class Rating(val rating: MangaRating) : CrossExtensionMatchMode
    // KMK --> v0.6.20: neutral seen/already-read marker
    data object MarkSeen : CrossExtensionMatchMode
    // KMK <--
    // KMK --> v0.7.0: Phase 3 – add to library
    data object Favorite : CrossExtensionMatchMode
    // KMK <--
}

sealed interface MatchItemResult {
    data object Loading : MatchItemResult
    data class Error(val throwable: Throwable) : MatchItemResult
    data class Success(val result: List<Manga>) : MatchItemResult
}

class CrossExtensionMatchScreenModel(
    val originMangaId: Long,
    val mode: CrossExtensionMatchMode,
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val getMangaInteractor: GetManga = Injekt.get(),
    private val setMangaTasteBatch: SetMangaTasteBatch = Injekt.get(),
    // KMK --> v0.7.0: Phase 3 – Favorite other versions
    private val updateManga: UpdateManga = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    // KMK <--
    // KMK --> v0.7.0: Phase 4 – persistent cross-source link groups
    private val upsertCrossSourceMangaLinks: UpsertCrossSourceMangaLinks = Injekt.get(),
    // KMK <--
) : StateScreenModel<CrossExtensionMatchScreenModel.State>(State()) {

    private val coroutineDispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher()
    private var searchJob: Job? = null
    private var originManga: Manga? = null

    // KMK --> v0.7.8: cap is now read from SourcePreferences; this constant is kept only as a fallback default
    companion object {
        const val PER_SOURCE_RESULT_LIMIT = 2
    }
    // KMK <--

    // KMK -->
    private fun isOrigin(manga: Manga): Boolean {
        val origin = originManga ?: return false
        return manga.source == origin.source && manga.url == origin.url
    }
    // KMK <--

    init {
        screenModelScope.launch {
            val manga = getMangaInteractor.await(originMangaId) ?: return@launch
            originManga = manga
            mutableState.update { it.copy(searchQuery = manga.title) }
            // KMK --> v0.7.0: Phase 2 – multi-query alternate-title matching
            search(CrossExtensionMatchQueryPlanner.buildQueries(manga))
            // KMK <--
        }
    }

    @Composable
    fun getManga(initialManga: Manga): androidx.compose.runtime.State<Manga> {
        return produceState(initialValue = initialManga) {
            getMangaInteractor.subscribe(initialManga.url, initialManga.source)
                .filterNotNull()
                .collectLatest { manga -> value = manga }
        }
    }

    private fun getMatchingSources(): List<CatalogueSource> {
        val recLanguages = RecommendationSourceFilter.normalizeLanguages(
            sourcePreferences.recommendationSourceLanguages().get(),
        )
        val storedOrder = RecommendationSourceOrdering.parse(
            sourcePreferences.recommendationSourceOrder().get(),
        )
        val disabledSourceIds = sourcePreferences.disabledSources().get()
            .mapNotNull { it.toLongOrNull() }.toSet()
        val all = sourceManager.getVisibleCatalogueSources()
        val filtered = RecommendationSourceFilter.filterForRecommendations(all, recLanguages)
        return RecommendationSourceOrdering.apply(filtered, storedOrder, disabledSourceIds)
    }

    // KMK --> v0.7.0: Phase 2 – multi-query search; merges results by (source, url) before cap
    // KMK --> v0.7.8: cap is read from sameMangaMatchResultsPerSource preference
    private fun search(queries: List<String>) {
        searchJob?.cancel()
        val sources = getMatchingSources()
        val cap = SameMangaMatchSettings.clampResultCap(
            sourcePreferences.sameMangaMatchResultsPerSource().get(),
        )
        mutableState.update {
            it.copy(items = sources.associateWith { MatchItemResult.Loading }.toPersistentMap())
        }

        searchJob = ioCoroutineScope.launch {
            sources.map { source ->
                async {
                    try {
                        val seen = LinkedHashMap<String, Manga>()
                        var lastError: Exception? = null
                        for (query in queries) {
                            if (!isActive) break
                            if (seen.size >= cap) break
                            try {
                                val page = withContext(coroutineDispatcher) {
                                    source.getSearchManga(1, query.sanitize(), source.getFilterList())
                                }
                                val resolved = page.mangas
                                    .map { it.toDomainManga(source.id) }
                                    .distinctBy { it.url }
                                    .let { networkToLocalManga(it) }
                                    .filterNot(::isOrigin)
                                for (manga in resolved) {
                                    if (seen.size >= cap) break
                                    seen.putIfAbsent(manga.url, manga)
                                }
                            } catch (e: Exception) {
                                lastError = e
                            }
                        }
                        if (isActive) {
                            if (seen.isNotEmpty()) {
                                updateItem(source, MatchItemResult.Success(seen.values.toList()))
                            } else if (lastError != null) {
                                updateItem(source, MatchItemResult.Error(lastError))
                            } else {
                                updateItem(source, MatchItemResult.Success(emptyList()))
                            }
                        }
                    } catch (e: Exception) {
                        if (isActive) updateItem(source, MatchItemResult.Error(e))
                    }
                }
            }.awaitAll()
        }
    }
    // KMK <--
    // KMK <--

    private fun updateItem(source: CatalogueSource, result: MatchItemResult) {
        val origin = originManga
        // KMK --> v0.7.8: respect sameMangaMatchPreselectResults preference
        val preselect = sourcePreferences.sameMangaMatchPreselectResults().get()
        // KMK <--
        mutableState.update { current ->
            val newItems: PersistentMap<CatalogueSource, MatchItemResult> = current.items.mutate { it[source] = result }
            val newSelected = if (result is MatchItemResult.Success && preselect) {
                val newKeys = result.result.mapNotNull { manga ->
                    val key = MangaIdentityKey(manga.source, manga.url)
                    // Exclude origin manga and manually deselected candidates
                    if (origin != null && manga.source == origin.source && manga.url == origin.url) return@mapNotNull null
                    if (key in current.manuallyDeselectedKeys) return@mapNotNull null
                    key
                }.toSet()
                current.selectedKeys + newKeys
            } else {
                current.selectedKeys
            }
            current.copy(items = newItems, selectedKeys = newSelected)
        }
    }

    fun toggleSelection(key: MangaIdentityKey) {
        // KMK -->
        val origin = originManga
        if (origin != null && key.source == origin.source && key.url == origin.url) return
        // KMK <--
        mutableState.update { current ->
            if (key in current.selectedKeys) {
                current.copy(
                    selectedKeys = current.selectedKeys - key,
                    manuallyDeselectedKeys = current.manuallyDeselectedKeys + key,
                )
            } else {
                current.copy(
                    selectedKeys = current.selectedKeys + key,
                    manuallyDeselectedKeys = current.manuallyDeselectedKeys - key,
                )
            }
        }
    }

    fun applyRating(onComplete: () -> Unit) {
        val selectedKeys = state.value.selectedKeys
        val targets = state.value.items.values
            .filterIsInstance<MatchItemResult.Success>()
            .flatMap { it.result }
            .filter { MangaIdentityKey(it.source, it.url) in selectedKeys }
        if (targets.isEmpty()) {
            onComplete()
            return
        }
        mutableState.update { it.copy(isApplying = true) }
        screenModelScope.launch {
            when (val m = mode) {
                is CrossExtensionMatchMode.Rating -> {
                    setMangaTasteBatch.await(targets, m.rating)
                }
                // KMK --> v0.6.20: write seen records — no taste rows, no tag weight changes
                CrossExtensionMatchMode.MarkSeen -> {
                    val raw = sourcePreferences.seenRecommendationMangaKeys().get()
                    val current = exh.recs.SeenRecommendationMangaStore.parse(raw)
                    val updated = targets.fold(current) { acc, manga ->
                        exh.recs.SeenRecommendationMangaStore.add(
                            acc,
                            exh.recs.SeenMangaKey(manga.source, manga.url),
                        )
                    }
                    sourcePreferences.seenRecommendationMangaKeys().set(
                        exh.recs.SeenRecommendationMangaStore.serialize(updated),
                    )
                }
                // KMK <--
                // KMK --> v0.7.0: Phase 3 – add selected manga to library
                CrossExtensionMatchMode.Favorite -> {
                    val allCategories = getCategories.subscribe().firstOrNull()
                        ?.filterNot { it.isSystemCategory }
                        .orEmpty()
                    val defaultCategoryId = libraryPreferences.defaultCategory().get()
                    val defaultCategory = allCategories.find { it.id == defaultCategoryId.toLong() }
                    for (manga in targets) {
                        if (manga.favorite) continue
                        val categoryIds = when {
                            defaultCategory != null -> listOf(defaultCategory.id)
                            defaultCategoryId == 0 || allCategories.isEmpty() -> emptyList()
                            else -> emptyList()
                        }
                        setMangaCategories.await(manga.id, categoryIds)
                        updateManga.await(manga.copy(favorite = true).toMangaUpdate())
                    }
                }
                // KMK <--
            }
            // KMK --> v0.7.0: Phase 4 – persist selected matches as a cross-source link group
            val origin = originManga
            if (origin != null && targets.isNotEmpty()) {
                val groupId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val links = buildList {
                    add(
                        CrossSourceMangaLink(
                            source = origin.source,
                            url = origin.url,
                            groupId = groupId,
                            title = origin.title,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                    for (target in targets) {
                        add(
                            CrossSourceMangaLink(
                                source = target.source,
                                url = target.url,
                                groupId = groupId,
                                title = target.title,
                                createdAt = now,
                                updatedAt = now,
                            ),
                        )
                    }
                }
                upsertCrossSourceMangaLinks.await(links)
            }
            // KMK <--
            mutableState.update { it.copy(isApplying = false) }
            onComplete()
        }
    }

    data class State(
        val searchQuery: String = "",
        val items: PersistentMap<CatalogueSource, MatchItemResult> = persistentMapOf(),
        val selectedKeys: Set<MangaIdentityKey> = emptySet(),
        val manuallyDeselectedKeys: Set<MangaIdentityKey> = emptySet(),
        val isApplying: Boolean = false,
    ) {
        val progress: Int = items.count { it.value !is MatchItemResult.Loading }
        val total: Int = items.size
        val totalCandidates: Int = items.values.sumOf {
            if (it is MatchItemResult.Success) it.result.size else 0
        }
    }
}
// KMK <--

package exh.recs.bestversion

// KMK --> v0.7.8
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import exh.recs.RecommendationErrorClassifier
import exh.recs.matching.MangaIdentityKey
import exh.recs.matching.SameMangaCandidateResult
import exh.recs.matching.SameMangaCandidateSearcher
import exh.recs.matching.SameMangaMatchSettings
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.domain.migration.usecases.MigrateMangaUseCase
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.taste.interactor.UpsertMangaSourceQualitySignal
import tachiyomi.domain.taste.model.MangaSourceQualitySignal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.Executors

sealed interface BestVersionStep {
    data object LoadingOrigin : BestVersionStep
    data object SearchingCandidates : BestVersionStep
    data object ConfirmCandidates : BestVersionStep
    data object LoadingChapters : BestVersionStep
    data object SelectChapter : BestVersionStep
    data object LoadingPreview : BestVersionStep
    data object ComparePreview : BestVersionStep
    data object PreparingMigration : BestVersionStep
    data class Error(val message: String) : BestVersionStep
    data object Done : BestVersionStep
}

sealed interface CandidateChapterState {
    data object Loading : CandidateChapterState
    data class Available(val chapter: eu.kanade.tachiyomi.source.model.SChapter, val totalChapters: Int) : CandidateChapterState
    data object Unavailable : CandidateChapterState
    data class ChapterError(val message: String) : CandidateChapterState
}

sealed interface CandidatePreviewState {
    data object Loading : CandidatePreviewState
    data class Loaded(val pages: List<SampledPage>) : CandidatePreviewState
    data class PreviewError(val message: String) : CandidatePreviewState
}

data class SampledPage(val index: Int, val imageUrl: String)

class BestVersionCompareScreenModel(
    val originMangaId: Long,
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val getMangaInteractor: GetManga = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val migrateMangaUseCase: MigrateMangaUseCase = Injekt.get(),
    private val upsertQualitySignal: UpsertMangaSourceQualitySignal = Injekt.get(),
) : StateScreenModel<BestVersionCompareScreenModel.State>(State()) {

    private val coroutineDispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher()
    private val searcher = SameMangaCandidateSearcher(sourcePreferences, sourceManager, networkToLocalManga)
    private var searchJob: Job? = null
    private var originManga: Manga? = null

    data class State(
        val step: BestVersionStep = BestVersionStep.LoadingOrigin,
        val originManga: Manga? = null,
        val candidates: PersistentMap<Source, SameMangaCandidateResult> = persistentMapOf(),
        val selectedKeys: Set<MangaIdentityKey> = emptySet(),
        val manuallyDeselectedKeys: Set<MangaIdentityKey> = emptySet(),
        val selectedChapterNumber: Double? = null,
        val originChapters: List<Chapter> = emptyList(),
        val candidateChapters: PersistentMap<MangaIdentityKey, CandidateChapterState> = persistentMapOf(),
        val candidatePreviews: PersistentMap<MangaIdentityKey, CandidatePreviewState> = persistentMapOf(),
        val sampleSize: Int = 5,
        val avoidFirstPages: Boolean = true,
        val selectedBestKey: MangaIdentityKey? = null,
        val isMigrating: Boolean = false,
        val migrationComplete: Boolean = false,
    ) {
        val searchProgress: Int get() = candidates.count { it.value !is SameMangaCandidateResult.Loading }
        val searchTotal: Int get() = candidates.size
        val selectedCandidates: List<Manga>
            get() = candidates.values
                .filterIsInstance<SameMangaCandidateResult.Success>()
                .flatMap { it.results }
                .filter { MangaIdentityKey(it.source, it.url) in selectedKeys }
    }

    init {
        screenModelScope.launch {
            val manga = getMangaInteractor.await(originMangaId)
            if (manga == null) {
                mutableState.update {
                    it.copy(step = BestVersionStep.Error("Could not load origin manga."))
                }
                return@launch
            }
            originManga = manga
            mutableState.update { it.copy(originManga = manga) }
            startSearch(manga)
        }
    }

    private fun startSearch(manga: Manga) {
        val settings = resolveSettings()
        val sources = searcher.getMatchingSources()
        mutableState.update {
            it.copy(
                step = BestVersionStep.SearchingCandidates,
                candidates = sources.associateWith<Source, SameMangaCandidateResult> {
                    SameMangaCandidateResult.Loading
                }.toPersistentMap(),
                sampleSize = settings.previewSampleSize,
                avoidFirstPages = settings.avoidFirstPages,
            )
        }
        val queries = exh.recs.matching.CrossExtensionMatchQueryPlanner.buildQueries(manga)
        searchJob = ioCoroutineScope.launch {
            searcher.search(
                queries = queries,
                settings = settings,
                originManga = manga,
                sources = sources,
            ) { result ->
                updateCandidate(result.source, result.result, settings.preselectResults)
            }
            if (isActive) {
                mutableState.update {
                    it.copy(step = BestVersionStep.ConfirmCandidates)
                }
            }
        }
    }

    private fun updateCandidate(source: Source, result: SameMangaCandidateResult, preselect: Boolean) {
        val origin = originManga
        mutableState.update { current ->
            val newCandidates = current.candidates.mutate { it[source] = result }
            val newSelected = if (result is SameMangaCandidateResult.Success && preselect) {
                val newKeys = result.results.mapNotNull { manga ->
                    val key = MangaIdentityKey(manga.source, manga.url)
                    if (origin != null && manga.source == origin.source && manga.url == origin.url) return@mapNotNull null
                    if (key in current.manuallyDeselectedKeys) return@mapNotNull null
                    key
                }.toSet()
                current.selectedKeys + newKeys
            } else {
                current.selectedKeys
            }
            current.copy(candidates = newCandidates, selectedKeys = newSelected)
        }
    }

    fun toggleSelection(key: MangaIdentityKey) {
        val origin = originManga
        if (origin != null && key.source == origin.source && key.url == origin.url) return
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

    fun confirmCandidates() {
        val selected = state.value.selectedCandidates
        if (selected.isEmpty()) return
        loadChapters(selected)
    }

    private fun loadChapters(candidates: List<Manga>) {
        val origin = originManga ?: return
        mutableState.update { it.copy(step = BestVersionStep.LoadingChapters) }
        ioCoroutineScope.launch {
            // Load origin chapters to determine default chapter
            val originChapters = runCatching { getChaptersByMangaId.await(origin.id) }.getOrElse { emptyList() }
            val defaultChapter = BestVersionChapterMatcher.selectDefaultChapter(originChapters)
            val targetChapterNumber = defaultChapter?.chapterNumber ?: originChapters.maxOfOrNull { it.chapterNumber } ?: -1.0

            // For each selected candidate, fetch chapter list from source
            val chapterResults = candidates.map { manga ->
                async {
                    val key = MangaIdentityKey(manga.source, manga.url)
                    val source = sourceManager.get(manga.source)
                    if (source == null) {
                        key to CandidateChapterState.ChapterError("Source not available")
                    } else {
                        try {
                            val sManga = manga.toSManga()
                            val chapters = withContext(coroutineDispatcher) {
                                source.getMangaUpdate(
                                    manga = sManga,
                                    chapters = emptyList(),
                                    fetchDetails = false,
                                    fetchChapters = true,
                                ).chapters
                            }
                            val match = if (targetChapterNumber >= 0) {
                                BestVersionChapterMatcher.findMatch(targetChapterNumber, chapters)
                            } else {
                                chapters.maxByOrNull { it.chapter_number }
                            }
                            key to if (match != null) {
                                CandidateChapterState.Available(match, chapters.size)
                            } else {
                                CandidateChapterState.Unavailable
                            }
                        } catch (e: Exception) {
                            // KMK v0.7.46: stable key, not raw exception text — see RecommendationErrorClassifier.
                            key to CandidateChapterState.ChapterError(RecommendationErrorClassifier.classifyToStorageKey(e))
                        }
                    }
                }
            }.awaitAll()

            if (isActive) {
                val chapterMap = chapterResults.toMap().toPersistentMap()
                mutableState.update {
                    it.copy(
                        originChapters = originChapters,
                        selectedChapterNumber = targetChapterNumber.takeIf { it >= 0 },
                        candidateChapters = chapterMap,
                        step = BestVersionStep.SelectChapter,
                    )
                }
            }
        }
    }

    fun startPreview() {
        val selected = state.value.selectedCandidates
        val chapterMap = state.value.candidateChapters
        val sampleSize = state.value.sampleSize
        val avoidFirstPages = state.value.avoidFirstPages

        val previewable = selected.filter { manga ->
            val key = MangaIdentityKey(manga.source, manga.url)
            chapterMap[key] is CandidateChapterState.Available
        }
        if (previewable.isEmpty()) return

        mutableState.update {
            val emptyPreviews = previewable.associate { manga ->
                MangaIdentityKey(manga.source, manga.url) to (CandidatePreviewState.Loading as CandidatePreviewState)
            }.toPersistentMap()
            it.copy(step = BestVersionStep.LoadingPreview, candidatePreviews = emptyPreviews)
        }

        ioCoroutineScope.launch {
            previewable.map { manga ->
                async {
                    val key = MangaIdentityKey(manga.source, manga.url)
                    val candidateChapter = (chapterMap[key] as? CandidateChapterState.Available)?.chapter
                        ?: return@async
                    val source = sourceManager.get(manga.source) as? HttpSource ?: return@async
                    val result = try {
                        val pages = withContext(coroutineDispatcher) {
                            source.getPageList(candidateChapter)
                        }
                        val indexes = BestVersionPageSampler.sample(pages.size, sampleSize, avoidFirstPages)
                        val sampledPages = indexes.mapNotNull { idx ->
                            val page = pages.getOrNull(idx) ?: return@mapNotNull null
                            val imageUrl = page.imageUrl ?: runCatching {
                                withContext(coroutineDispatcher) { source.getImageUrl(page) }
                            }.getOrNull() ?: return@mapNotNull null
                            SampledPage(idx, imageUrl)
                        }
                        CandidatePreviewState.Loaded(sampledPages)
                    } catch (e: Exception) {
                        // KMK v0.7.46: stable key, not raw exception text — see RecommendationErrorClassifier.
                        CandidatePreviewState.PreviewError(RecommendationErrorClassifier.classifyToStorageKey(e))
                    }
                    mutableState.update { current ->
                        current.copy(candidatePreviews = current.candidatePreviews.mutate { it[key] = result })
                    }
                }
            }.awaitAll()
            if (isActive) {
                mutableState.update { it.copy(step = BestVersionStep.ComparePreview) }
            }
        }
    }

    fun selectBestVersion(key: MangaIdentityKey) {
        mutableState.update { it.copy(selectedBestKey = key) }
    }

    // KMK --> v0.7.9: dedicated dismiss — clears fake sentinel, preserves all preview state
    fun dismissMigrationDialog() {
        mutableState.update { it.copy(selectedBestKey = null, isMigrating = false) }
    }
    // KMK <--

    fun confirmMigration(replace: Boolean) {
        // KMK --> v0.7.9: defensive guard — clear selected key if origin or target cannot be resolved
        val origin = originManga ?: run {
            mutableState.update { it.copy(step = BestVersionStep.Error("Could not load origin manga."), selectedBestKey = null) }
            return
        }
        val key = state.value.selectedBestKey ?: return
        val target = state.value.selectedCandidates.find { it.source == key.source && it.url == key.url }
        if (target == null) {
            dismissMigrationDialog()
            return
        }
        // KMK <--
        mutableState.update { it.copy(step = BestVersionStep.PreparingMigration, isMigrating = true) }
        screenModelScope.launch {
            try {
                migrateMangaUseCase(current = origin, target = target, replace = replace)
                saveQualitySignal(origin, target)
                mutableState.update { it.copy(step = BestVersionStep.Done, isMigrating = false, migrationComplete = true) }
            } catch (e: Exception) {
                mutableState.update {
                    it.copy(
                        // KMK v0.7.46: stable key, not raw exception text — see RecommendationErrorClassifier.
                        step = BestVersionStep.Error(RecommendationErrorClassifier.classifyToStorageKey(e)),
                        isMigrating = false,
                    )
                }
            }
        }
    }

    private suspend fun saveQualitySignal(origin: Manga, target: Manga) {
        val source = sourceManager.get(target.source)
        val signal = MangaSourceQualitySignal(
            id = 0,
            originSourceId = origin.source,
            originUrl = origin.url,
            originTitle = origin.title,
            selectedSourceId = target.source,
            selectedUrl = target.url,
            selectedTitle = target.title,
            selectedSourceName = source?.name ?: "",
            comparedCandidatesJson = "[]",
            chapterNumber = state.value.selectedChapterNumber,
            chapterName = "",
            sampleSize = state.value.sampleSize,
            sampledPagesJson = "[]",
            selectedAt = System.currentTimeMillis(),
            qualitySignalVersion = 1,
        )
        runCatching { upsertQualitySignal.insert(signal) }
    }

    private fun resolveSettings(): SameMangaMatchSettings {
        return SameMangaMatchSettings(
            resultsPerSource = SameMangaMatchSettings.clampResultCap(
                sourcePreferences.sameMangaMatchResultsPerSource().get(),
            ),
            preselectResults = sourcePreferences.sameMangaMatchPreselectResults().get(),
            previewSampleSize = SameMangaMatchSettings.clampSampleSize(
                sourcePreferences.bestVersionPreviewSampleSize().get(),
            ),
            avoidFirstPages = sourcePreferences.bestVersionAvoidFirstPages().get(),
        )
    }
}
// KMK <--

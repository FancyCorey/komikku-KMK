package exh.recs.matching

// KMK -->
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.model.LocalTrackingActionPolicy
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.manga.track.LocalTrackingHistoryProgressPolicy
import exh.util.DispatcherHandle
import exh.util.ownedFixedThreadPoolDispatcherHandle
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.toMangaUpdate
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.taste.interactor.GetCrossSourceIdentityDecisions
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.interactor.SetMangaTasteBatch
import tachiyomi.domain.taste.interactor.UpsertCrossSourceMangaLinks
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionPolicy
import tachiyomi.domain.taste.model.CrossSourceIdentityPair
import tachiyomi.domain.taste.model.CrossSourceMangaLink
import tachiyomi.domain.taste.model.CrossSourceRecordKey
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.tracker.model.LocalTrackedWork
import tachiyomi.domain.tracker.model.LocalTrackedWorkSource
import tachiyomi.domain.tracker.model.LocalTrackedWorkSourceConfirmation
import tachiyomi.domain.tracker.model.LocalTrackedWorkStatus
import tachiyomi.domain.tracker.repository.LocalTrackerRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale
import java.util.UUID

data class MangaIdentityKey(val source: Long, val url: String)

sealed interface CrossExtensionMatchMode {
    // KMK v0.8.21-fix3: R1 correction -- Not Interested is Rating(MangaRating.NOT_INTERESTED),
    // no longer a separate MarkSeen mode writing only the legacy seen-key preference.
    data class Rating(val rating: MangaRating) : CrossExtensionMatchMode
    data class LocalTracking(val status: LocalTrackedWorkStatus) : CrossExtensionMatchMode
    // KMK --> v0.7.0: Phase 3 – add to library
    data object Favorite : CrossExtensionMatchMode
    // KMK <--
}

sealed interface MatchItemResult {
    data object Loading : MatchItemResult
    data class Error(val throwable: Throwable) : MatchItemResult
    data class Success(
        val result: List<Manga>,
        val evidence: Map<MangaIdentityKey, SameMangaIdentityAssessment> = emptyMap(),
    ) : MatchItemResult
}

class CrossExtensionMatchScreenModel(
    val originMangaId: Long,
    val mode: CrossExtensionMatchMode,
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val dispatcherHandle: DispatcherHandle = ownedFixedThreadPoolDispatcherHandle(),
    private val getMangaInteractor: GetManga = Injekt.get(),
    private val setMangaTasteBatch: SetMangaTasteBatch = Injekt.get(),
    private val getMangaTaste: GetMangaTaste = Injekt.get(),
    // KMK --> v0.7.0: Phase 3 – Favorite other versions
    private val updateManga: UpdateManga = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    // KMK <--
    // KMK --> v0.7.0: Phase 4 – persistent cross-source link groups
    private val upsertCrossSourceMangaLinks: UpsertCrossSourceMangaLinks = Injekt.get(),
    private val getCrossSourceIdentityDecisions: GetCrossSourceIdentityDecisions = Injekt.get(),
    private val identityController: CrossSourceIdentityDecisionController = CrossSourceIdentityDecisionController(),
    private val localTrackerRepository: LocalTrackerRepository = Injekt.get(),
    private val confirmedGroupLocalTrackingPropagator: ConfirmedGroupLocalTrackingPropagator =
        ConfirmedGroupLocalTrackingPropagator(localTrackerRepository),
    private val getHistory: GetHistory = Injekt.get(),
    private val getChapter: GetChapter = Injekt.get(),
    // KMK <--
) : StateScreenModel<CrossExtensionMatchScreenModel.State>(State()) {

    private val searcher = SameMangaCandidateSearcher(
        sourcePreferences,
        sourceManager,
        networkToLocalManga,
        dispatcherHandle.dispatcher,
    )
    private var searchJob: Job? = null
    private var originManga: Manga? = null
    private var disposed = false

    companion object {
        const val PER_SOURCE_RESULT_LIMIT = 2
    }

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
        screenModelScope.launch {
            sourcePreferences.sameMangaMatchPreselectionMode().changes()
                .drop(1)
                .map { storedMode ->
                    SameMangaPreselectionMode.resolve(
                        storedMode,
                        sourcePreferences.sameMangaMatchPreselectResults().get(),
                    )
                }
                .distinctUntilChanged()
                .collectLatest { preselectionMode ->
                    refreshSelectionForCurrentResults(preselectionMode)
                }
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

    // KMK --> v0.7.0: Phase 2 – multi-query search; merges results by (source, url) before cap
    // KMK --> v0.7.8: cap is read from sameMangaMatchResultsPerSource preference
    private fun search(
        queries: List<String>,
        requestedSources: List<Source>? = null,
    ) {
        searchJob?.cancel()
        val origin = originManga ?: return
        val sources = requestedSources ?: searcher.getMatchingSources()
        val preselectionMode = SameMangaPreselectionMode.resolve(
            sourcePreferences.sameMangaMatchPreselectionMode().get(),
            sourcePreferences.sameMangaMatchPreselectResults().get(),
        )
        val settings = SameMangaMatchSettings(
            resultsPerSource = SameMangaMatchSettings.clampResultCap(
                sourcePreferences.sameMangaMatchResultsPerSource().get(),
            ),
            preselectResults = preselectionMode != SameMangaPreselectionMode.NONE,
            previewSampleSize = SameMangaMatchSettings.clampSampleSize(
                sourcePreferences.bestVersionPreviewSampleSize().get(),
            ),
            avoidFirstPages = sourcePreferences.bestVersionAvoidFirstPages().get(),
            preselectionMode = preselectionMode,
        )
        mutableState.update { current ->
            current.copy(
                items = if (requestedSources == null) {
                    sources.associateWith { MatchItemResult.Loading }.toPersistentMap()
                } else {
                    current.items.mutate {
                        sources.forEach { source -> it[source] = MatchItemResult.Loading }
                    }
                },
                // A full search is a new comparison pass. Do not carry a prior
                // screen/search selection into it, or a newly configured NONE
                // mode can still display stale selected candidates.
                selectedKeys = if (requestedSources == null) emptySet() else current.selectedKeys,
                manuallyDeselectedKeys = if (requestedSources == null) emptySet() else current.manuallyDeselectedKeys,
            )
        }

        searchJob = ioCoroutineScope.launch {
            searcher.search(queries, settings, origin, sources) { sourceResult ->
                updateItem(
                    sourceResult.source,
                    sourceResult.result.toMatchItemResult(),
                    settings.preselectionMode,
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun searchCurrentQuery() {
        val query = state.value.searchQuery.trim()
        if (query.isNotBlank()) search(listOf(query))
    }

    fun searchSource(source: Source) {
        val query = state.value.searchQuery.trim()
        if (query.isNotBlank()) search(listOf(query), requestedSources = listOf(source))
    }

    private suspend fun refreshSelectionForCurrentResults(preselectionMode: SameMangaPreselectionMode) {
        val origin = originManga ?: return
        val results = state.value.items.values.filterIsInstance<MatchItemResult.Success>()
        if (results.isEmpty()) return

        val selectedKeys = results.flatMap { result ->
            val decisions = result.result.associate { manga ->
                val pair = identityPair(origin, manga)
                MangaIdentityKey(manga.source, manga.url) to getCrossSourceIdentityDecisions.await(pair)
            }
            CrossSourceMatchSelectionPolicy.automaticSelections(
                candidates = result.result,
                origin = origin,
                preselectionMode = preselectionMode,
                decisions = decisions,
                manuallyDeselected = state.value.manuallyDeselectedKeys,
            )
        }.toSet()

        mutableState.update { current -> current.copy(selectedKeys = selectedKeys) }
    }
    // KMK <--
    // KMK <--

    private fun SameMangaCandidateResult.toMatchItemResult(): MatchItemResult = when (this) {
        SameMangaCandidateResult.Loading -> MatchItemResult.Loading
        is SameMangaCandidateResult.Error -> MatchItemResult.Error(throwable)
        is SameMangaCandidateResult.Success -> MatchItemResult.Success(results, evidence)
    }

    private suspend fun updateItem(
        source: Source,
        result: MatchItemResult,
        preselectionMode: SameMangaPreselectionMode,
    ) {
        val origin = originManga
        val decisions = if (origin != null && result is MatchItemResult.Success) {
            result.result.associate { manga ->
                val pair = identityPair(origin, manga)
                MangaIdentityKey(manga.source, manga.url) to getCrossSourceIdentityDecisions.await(pair)
            }
        } else {
            emptyMap()
        }
        val visibleResult = if (result is MatchItemResult.Success) {
            result.copy(
                result = result.result.filterNot { manga ->
                    CrossSourceIdentityDecisionPolicy.isCurrentRejection(
                        decisions[MangaIdentityKey(manga.source, manga.url)],
                    )
                },
            )
        } else {
            result
        }
        mutableState.update { current ->
            val newItems: PersistentMap<Source, MatchItemResult> = current.items.mutate { it[source] = visibleResult }
            val newSelected = if (visibleResult is MatchItemResult.Success) {
                val newKeys = CrossSourceMatchSelectionPolicy.automaticSelections(
                    candidates = visibleResult.result,
                    origin = origin,
                    preselectionMode = preselectionMode,
                    decisions = decisions,
                    manuallyDeselected = current.manuallyDeselectedKeys,
                )
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
        val selectedTargets = state.value.items.values
            .filterIsInstance<MatchItemResult.Success>()
            .flatMap { it.result }
            .filter { MangaIdentityKey(it.source, it.url) in selectedKeys }
        if (selectedTargets.isEmpty()) {
            onComplete()
            return
        }
        mutableState.update { it.copy(isApplying = true) }
        screenModelScope.launch {
            var completed = false
            try {
                // Search results can outlive a restore, refresh, or another screen's
                // insert. Resolve by the stable source/URL identity immediately before
                // mutation so first-write rating/tracking uses the authoritative local
                // manga row, including newly selected unrated candidates.
                val targets = selectedTargets
                    .map { manga ->
                        getMangaInteractor.await(manga.url, manga.source) ?: manga
                    }
                    .distinctBy { MangaIdentityKey(it.source, it.url) }
                val origin = originManga
                if (origin != null) {
                    val identityResult = identityController.mutateAll(
                        targets.map { identityPair(origin, it) },
                        CrossSourceIdentityMutation.CONFIRM,
                    )
                    if (
                        identityResult == CrossSourceIdentityMutationResult.CONFLICT ||
                        identityResult == CrossSourceIdentityMutationResult.FAILED
                    ) {
                        mutableState.update { it.copy(identityFeedback = identityResult) }
                        return@launch
                    }
                }
                when (val m = mode) {
                    is CrossExtensionMatchMode.LocalTracking -> {
                        val now = System.currentTimeMillis()
                        var sharedWorkId = origin?.let {
                            localTrackerRepository.getWorkIdBySourceUrl(it.source, it.url)
                        }
                        for (manga in targets) {
                            val existingId = localTrackerRepository.getWorkIdBySourceUrl(manga.source, manga.url)
                            if (existingId != null) {
                                val work = localTrackerRepository.getWork(existingId) ?: continue
                                localTrackerRepository.upsertWork(
                                    work.copy(
                                        status = m.status,
                                        startDate = work.startDate ?: now.takeIf { m.status != LocalTrackedWorkStatus.PLANNED },
                                        finishDate = LocalTrackingActionPolicy.finishDateForStatus(m.status, now),
                                        updatedAt = now,
                                    ),
                                )
                                if (sharedWorkId == null) sharedWorkId = existingId
                                localTrackerRepository.upsertSource(
                                    LocalTrackedWorkSource(
                                        workId = existingId,
                                        source = manga.source,
                                        url = manga.url,
                                        title = manga.title,
                                        confidence = 100,
                                        confirmation = LocalTrackedWorkSourceConfirmation.USER_CONFIRMED,
                                        createdAt = work.createdAt,
                                        updatedAt = now,
                                    ),
                                )
                            } else {
                                val workId = sharedWorkId ?: UUID.randomUUID().toString().also { newWorkId ->
                                    val historyProgress = LocalTrackingHistoryProgressPolicy.resolve(
                                        getHistory.await(manga.id),
                                    )
                                    val latestRead = historyProgress
                                        ?.let { progress -> getChapter.await(progress.chapterId)?.let { progress to it } }
                                    localTrackerRepository.upsertWork(
                                        LocalTrackedWork(
                                            id = newWorkId,
                                            title = manga.title,
                                            normalizedTitle = manga.title.trim().lowercase(Locale.ROOT),
                                            status = m.status,
                                            lastChapterSource = latestRead?.second?.let { manga.source },
                                            lastChapterNumber = latestRead?.second
                                                ?.takeIf { it.isRecognizedNumber }
                                                ?.chapterNumber,
                                            lastChapterUrl = latestRead?.second?.url,
                                            lastChapterLabel = latestRead?.second?.name,
                                            lastProgressAt = latestRead?.first?.progressAt,
                                            createdAt = now,
                                            updatedAt = now,
                                            startDate = historyProgress?.firstReadAt
                                                ?: now.takeIf { m.status != LocalTrackedWorkStatus.PLANNED },
                                            finishDate = LocalTrackingActionPolicy.finishDateForStatus(m.status, now),
                                        ),
                                    )
                                    sharedWorkId = newWorkId
                                }
                                localTrackerRepository.upsertSource(
                                    LocalTrackedWorkSource(
                                        workId = workId,
                                        source = manga.source,
                                        url = manga.url,
                                        title = manga.title,
                                        confidence = 100,
                                        confirmation = LocalTrackedWorkSourceConfirmation.USER_CONFIRMED,
                                        createdAt = now,
                                        updatedAt = now,
                                    ),
                                )
                            }
                        }
                    }
                    is CrossExtensionMatchMode.Rating -> {
                        // KMK v0.8.21-fix3: R1 correction -- Not Interested is just another rating
                        // value now; this single branch already writes MangaTaste for all four
                        // states via setMangaTasteBatch, so no separate MarkSeen path is needed.
                        val journalEntries = exh.util.EvaluationModeJournalRecorder.buildRatingChange(
                            getMangaTaste,
                            targets,
                            m.rating.value,
                            when (m.rating) {
                                MangaRating.LOVE -> exh.util.EvaluationJournalActionType.RATE_LOVE
                                MangaRating.LIKE -> exh.util.EvaluationJournalActionType.RATE_LIKE
                                MangaRating.DISLIKE -> exh.util.EvaluationJournalActionType.RATE_DISLIKE
                                MangaRating.NOT_INTERESTED -> exh.util.EvaluationJournalActionType.NOT_INTERESTED
                            },
                        )
                        setMangaTasteBatch.await(targets, m.rating)
                        exh.util.EvaluationModeJournalRecorder.commit(journalEntries)
                    }
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
                if (
                    mode is CrossExtensionMatchMode.LocalTracking &&
                    sourcePreferences.confirmedTrackedVersionLocalTrackingPropagationEnabled().get()
                ) {
                    confirmedGroupLocalTrackingPropagator.propagateIfAnyTracked(
                        listOfNotNull(origin) + targets,
                    )
                }
                // Persist the confirmed group before attaching local tracking. Otherwise a newly
                // selected version can be rated successfully but miss the tracked-work fan-out
                // because it was not yet part of the confirmed group at propagation time.
                if (
                    mode is CrossExtensionMatchMode.Rating &&
                    sourcePreferences.confirmedTrackedVersionLocalTrackingPropagationEnabled().get()
                ) {
                    confirmedGroupLocalTrackingPropagator.propagateIfAnyTracked(
                        listOfNotNull(origin) + targets,
                    )
                }
                // KMK <--
                completed = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.update { it.copy(identityFeedback = CrossSourceIdentityMutationResult.FAILED) }
            } finally {
                mutableState.update { it.copy(isApplying = false) }
            }
            if (completed) onComplete()
        }
    }

    private fun identityPair(origin: Manga, target: Manga): CrossSourceIdentityPair =
        CrossSourceIdentityDecisionPolicy.canonicalPair(
            CrossSourceRecordKey(origin.source, origin.url),
            CrossSourceRecordKey(target.source, target.url),
        )

    override fun onDispose() {
        super.onDispose()
        searchJob?.cancel()
        if (!disposed) {
            disposed = true
            dispatcherHandle.close()
        }
    }

    data class State(
        val searchQuery: String = "",
        val items: PersistentMap<Source, MatchItemResult> = persistentMapOf(),
        val selectedKeys: Set<MangaIdentityKey> = emptySet(),
        val manuallyDeselectedKeys: Set<MangaIdentityKey> = emptySet(),
        val identityFeedback: CrossSourceIdentityMutationResult? = null,
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

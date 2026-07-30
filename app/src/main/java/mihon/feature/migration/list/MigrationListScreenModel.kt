package mihon.feature.migration.list

import androidx.annotation.FloatRange
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.getNameForMangaInfo
import eu.kanade.tachiyomi.source.getOrThrowSourceRuntimeException
import exh.source.MERGED_SOURCE_ID
import exh.util.ThrottleManager
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import mihon.domain.migration.usecases.MigrateMangaUseCase
import mihon.domain.source.interactor.UpdateMangaFromRemote
import mihon.feature.migration.list.models.MigratingManga
import mihon.feature.migration.list.models.MigratingManga.SearchResult
import mihon.feature.migration.list.search.SmartSourceSearchEngine
import mihon.feature.migration.list.search.SourceMatchScorer
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrationListScreenModel(
    mangaIds: Collection<Long>,
    extraSearchQuery: String?,
    // KMK -->
    runManually: Boolean = false,
    // KMK <--
    val preferences: SourcePreferences = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val updateMangaFromRemote: UpdateMangaFromRemote = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val migrateManga: MigrateMangaUseCase = Injekt.get(),
    // KMK Confirmed Blocker Remediation Corrective Completion Plan V2 2026-07-29: injectable at the
    // narrowest existing screen-model boundary, defaulting to the real production dispatcher.
    // Direct fixture tests can pass a deterministic test dispatcher (tied to the test's own
    // TestCoroutineScheduler) instead of relying on the real, process-global Dispatchers.IO thread
    // pool and wall-clock polling -- the root cause of this screen model's full-suite-only test
    // flakiness identified in the prior Corrective Pass report.
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : StateScreenModel<MigrationListScreenModel.State>(State()) {

    private val smartSearchEngine = SmartSourceSearchEngine(extraSearchQuery)

    // SY -->
    private val throttleManager = ThrottleManager()
    // SY <--

    val items
        inline get() = state.value.items

    private var hideUnmatched = preferences.migrationHideUnmatched().get()
    private var hideWithoutUpdates = preferences.migrationHideWithoutUpdates().get()
    // KMK -->
    private var prioritizeByChapters = preferences.migrationPrioritizeByChapters().get()
    private var deepSearchMode = preferences.migrationDeepSearchMode().get()
    // KMK <--

    private val navigateBackChannel = Channel<Unit>()
    val navigateBackEvent = navigateBackChannel.receiveAsFlow()

    private var migrateJob: Job? = null

    init {
        screenModelScope.launch(ioDispatcher) {
            val manga = mangaIds
                .map { mangaId ->
                    async {
                        // KMK -->
                        // A metadata-lookup failure for one manga must not blank out the
                        // whole migration list for every other manga (V2 corrective plan).
                        try {
                            // KMK <--
                            val manga = getManga.await(mangaId) ?: return@async null
                            val chapterInfo = getChapterInfo(mangaId)
                            MigratingManga(
                                manga = manga,
                                chapterCount = chapterInfo.chapterCount,
                                latestChapter = chapterInfo.latestChapter,
                                source = sourceManager.getOrStub(manga.source).getNameForMangaInfo(
                                    // KMK -->
                                    if (manga.source == MERGED_SOURCE_ID) {
                                        sourceManager.getMergedSources(manga.id)
                                    } else {
                                        null
                                    },
                                    // KMK <--
                                ),
                                parentContext = screenModelScope.coroutineContext,
                                // KMK -->
                            ).apply {
                                if (runManually) searchResult.value = SearchResult.NotFound
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            logcat(LogPriority.ERROR, e) { "Failed to load migration info for manga $mangaId" }
                            null
                            // KMK <--
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
            mutableState.update { it.copy(items = manga.toImmutableList()) }
            // KMK -->
            if (runManually) return@launch
            // KMK <--
            runMigrations(manga)
        }
    }

    private suspend fun getChapterInfo(id: Long) = getChaptersByMangaId.await(id).let { chapters ->
        ChapterInfo(
            latestChapter = chapters.maxOfOrNull { it.chapterNumber },
            chapterCount = chapters.size,
        )
    }

    private suspend fun Manga.toSuccessSearchResult(): SearchResult.Success {
        val chapterInfo = getChapterInfo(id)
        val source = sourceManager.getOrStub(source).getNameForMangaInfo()
        return SearchResult.Success(
            manga = this,
            chapterCount = chapterInfo.chapterCount,
            latestChapter = chapterInfo.latestChapter,
            source = source,
        )
    }

    private suspend fun runMigrations(mangas: List<MigratingManga>) {
        // SY -->
        throttleManager.resetThrottle()
        // SY <--
        // KMK -->
        // val prioritizeByChapters = preferences.migrationPrioritizeByChapters().get()
        // val deepSearchMode = preferences.migrationDeepSearchMode().get()
        // KMK <--

        val sources = preferences.migrationSources().get()
            .mapNotNull { sourceManager.get(it) as? CatalogueSource }

        for (manga in mangas) {
            if (!currentCoroutineContext().isActive) break
            if (manga.manga.id !in state.value.mangaIds) continue
            if (manga.searchResult.value != SearchResult.Searching) continue
            if (!manga.migrationScope.isActive) continue

            val result = try {
                // KMK -->
                manga.searchingJob = manga.migrationScope.async {
                    // KMK <--
                    if (prioritizeByChapters) {
                        val sourceSemaphore = Semaphore(5)
                        sources.map { source ->
                            async innerAsync@{
                                sourceSemaphore.withPermit {
                                    val result = searchSource(manga, source, deepSearchMode)
                                    if (result == null || result.second.chapterCount == 0) return@innerAsync null
                                    result
                                }
                            }
                        }
                            .mapNotNull { it.await() }
                            .maxByOrNull { it.second.matchScore }
                    } else {
                        sources.forEach { source ->
                            val result = searchSource(manga, source, deepSearchMode)
                            if (result != null) return@async result
                        }
                        null
                    }
                }
                // KMK -->
                manga.searchingJob?.await()
                // KMK <--
            } catch (_: CancellationException) {
                continue
            }

            if (result != null && result.first.thumbnailUrl == null) {
                try {
                    // KMK v0.8.10-fix7: genuine hole -- no catch(Error) existed here, so a raw
                    // NoClassDefFoundError from .getOrThrow() would have escaped uncaught.
                    updateMangaFromRemote(
                        manga = result.first,
                        fetchDetails = true,
                        manualFetch = true,
                    ).getOrThrowSourceRuntimeException()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }

            manga.searchResult.value = result?.first?.toSuccessSearchResult() ?: SearchResult.NotFound

            if (result == null && hideUnmatched) {
                removeManga(manga)
            }
            if (result != null &&
                hideWithoutUpdates &&
                (result.second.latestChapter ?: 0.0) <= (manga.latestChapter ?: 0.0)
            ) {
                removeManga(manga)
            }

            updateMigrationProgress()
        }
    }

    private suspend fun searchSource(
        manga: MigratingManga,
        source: Source,
        deepSearchMode: Boolean,
    ): Pair<Manga, ChapterInfo>? {
        return try {
            val searchResult = if (deepSearchMode) {
                smartSearchEngine.deepSearch(source, manga.manga.title)
            } else {
                smartSearchEngine.regularSearch(source, manga.manga.title)
            }

            if (searchResult == null || (searchResult.url == manga.manga.url && source.id == manga.manga.source)) return null

            var localManga = networkToLocalManga(searchResult)
            try {
                // KMK v0.8.10-fix7: genuine hole -- no catch(Error) existed here.
                updateMangaFromRemote(
                    source = source,
                    manga = localManga,
                    fetchDetails = true,
                    fetchChapters = true,
                    manualFetch = true,
                    // SY -->
                    throttleFunc = throttleManager::throttle,
                    // SY <--
                ).getOrThrowSourceRuntimeException()
                localManga = getManga.await(localManga.id) ?: localManga
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
            }
            val chapterInfo = getChapterInfo(localManga.id)
            localManga to chapterInfo.withMatchScore(
                SourceMatchScorer.score(
                    current = manga.manga,
                    candidate = localManga,
                    currentChapterCount = manga.chapterCount,
                    currentLatestChapter = manga.latestChapter,
                    candidateChapterCount = chapterInfo.chapterCount,
                    candidateLatestChapter = chapterInfo.latestChapter,
                ).total,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun updateMigrationProgress() {
        mutableState.update { state ->
            state.copy(
                // KMK -->
                finishedCount = state.items.count { it.searchResult.value != SearchResult.Searching },
                migrationComplete = state.migrationComplete(),
                // KMK <--
            )
        }
        if (items.isEmpty()) {
            navigateBack()
        }
    }

    // KMK -->
    private fun State.migrationComplete() =
        // KMK <--
        items.all { it.searchResult.value != SearchResult.Searching } &&
            items.any { it.searchResult.value is SearchResult.Success }

    /** Set a manga picked from manual search to be used as migration target */
    fun useMangaForMigration(current: Long, target: Long, onMissingChapters: () -> Unit) {
        val migratingManga = items.find { it.manga.id == current } ?: return
        migratingManga.searchResult.value = SearchResult.Searching
        screenModelScope.launch(ioDispatcher) {
            val result = migratingManga.migrationScope.async {
                val manga = getManga.await(target) ?: return@async null
                try {
                    val source = sourceManager.get(manga.source)!!
                    // KMK v0.8.10-fix7: genuine hole -- no catch(Error) existed here.
                    updateMangaFromRemote(
                        source = source,
                        manga = manga,
                        fetchDetails = true,
                        fetchChapters = true,
                        manualFetch = true,
                        // SY -->
                        throttleFunc = throttleManager::throttle,
                        // SY <--
                    ).getOrThrowSourceRuntimeException().manga
                } catch (_: Exception) {
                    return@async null
                }
            }
                .await()

            if (result == null) {
                migratingManga.searchResult.value = SearchResult.NotFound
                withUIContext { onMissingChapters() }
                return@launch
            }

            migratingManga.searchResult.value = result.toSuccessSearchResult()
            updateMigrationProgress()
        }
    }

    fun migrateMangas() {
        migrateMangas(replace = true)
    }

    fun copyMangas() {
        migrateMangas(replace = false)
    }

    private fun migrateMangas(replace: Boolean) {
        migrateJob = screenModelScope.launch(ioDispatcher) {
            mutableState.update { it.copy(dialog = Dialog.Progress(0f)) }
            val items = items
            // KMK Confirmed Blocker Remediation follow-up Phase 1 2026-07-29: previously the return
            // value of migrateManga(...) was discarded entirely, so a PartialFailure/NotStarted
            // outcome for any item was silently indistinguishable from Success -- the loop always
            // finished by navigating back as if every item had fully migrated.
            // KMK Confirmed Blocker Remediation Corrective Pass 2026-07-29: tracked separately per
            // the plan's "report successful, failed, skipped, and unresolved counts" requirement --
            // failedCount is an attempt that did not reach Success; skippedCount is an item with no
            // successful search result, so migration was never attempted for it at all.
            var failedCount = 0
            var skippedCount = 0
            // KMK: collected instead of removed mid-loop -- removing via the async public
            // removeManga(mangaId) here would race with updateMigrationProgress()'s own
            // items.isEmpty() -> navigateBack() check while this loop is still processing other
            // items. Successful items are pruned from state in one batch after the loop finishes.
            val successfulIds = mutableSetOf<Long>()
            try {
                items.forEachIndexed { index, manga ->
                    try {
                        ensureActive()
                        val target = manga.searchResult.value.let {
                            if (it is SearchResult.Success) {
                                it.manga
                            } else {
                                null
                            }
                        }
                        if (target != null) {
                            manga.migrationResult.value = MigratingManga.MigrationResultState.InProgress
                            val outcome = migrateManga(current = manga.manga, target = target, replace = replace)
                            // KMK: shares MigrationOutcomeReducer with migrateNow() so the two
                            // flows cannot drift on what counts as a real success.
                            when (MigrationOutcomeReducer.reduce(outcome)) {
                                MigrationListItemState.SUCCESS -> {
                                    manga.migrationResult.value = null
                                    successfulIds += manga.manga.id
                                }
                                MigrationListItemState.RETRYABLE_FAILURE, MigrationListItemState.NOT_STARTED -> {
                                    failedCount++
                                    logcat(LogPriority.WARN) {
                                        "Migration did not complete for manga ${manga.manga.id}: $outcome"
                                    }
                                    manga.migrationResult.value = MigratingManga.MigrationResultState.Failed(retryable = true)
                                }
                                MigrationListItemState.SKIPPED -> Unit
                            }
                        } else {
                            // No successful search result for this item -- never attempted, never
                            // counted as success or as a retryable failure.
                            skippedCount++
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) {
                            manga.migrationResult.value = null
                            throw e
                        }
                        failedCount++
                        manga.migrationResult.value = MigratingManga.MigrationResultState.Failed(retryable = true)
                        logcat(LogPriority.WARN, throwable = e)
                    }
                    mutableState.update {
                        it.copy(dialog = Dialog.Progress((index.toFloat() / items.size).coerceAtMost(1f)))
                    }
                }

                if (successfulIds.isNotEmpty()) {
                    successfulIds.forEach { id -> items.find { it.manga.id == id }?.migrationScope?.cancel() }
                    mutableState.update {
                        it.copy(items = it.items.filterNot { m -> m.manga.id in successfulIds }.toImmutableList())
                    }
                }

                if (failedCount > 0 || skippedCount > 0) {
                    // KMK Confirmed Blocker Remediation Corrective Pass 2026-07-29: failed/unresolved
                    // items are still in `items` (never removed by this loop) -- the Result dialog is
                    // an acknowledgement, not a discard. Dismissing it (see dismissResultDialog())
                    // does NOT navigate back, unlike the Follow-up Pass 1 version of this dialog:
                    // navigating away here would leave this screen entirely and hide the very items
                    // this dialog is telling the user still need attention, defeating the plan's
                    // "retryable or explicitly dismissible" requirement. The user stays on this
                    // screen and can retry (Migrate/Copy now) or explicitly Skip each remaining item.
                    mutableState.update { it.copy(dialog = Dialog.Result(failedCount, skippedCount, items.size)) }
                } else {
                    mutableState.update { it.copy(dialog = null) }
                    navigateBack()
                }
            } catch (e: CancellationException) {
                mutableState.update { it.copy(dialog = null) }
                throw e
            } finally {
                migrateJob = null
            }
        }
    }

    fun cancelMigrate() {
        migrateJob?.cancel()
        migrateJob = null
    }

    private suspend fun navigateBack() {
        navigateBackChannel.send(Unit)
    }

    // KMK Confirmed Blocker Remediation Corrective Pass 2026-07-29: previously (Follow-up Pass 1)
    // logged a non-Success MigrationOutcome and then called removeManga(mangaId) unconditionally --
    // "logging" is not user-facing failure handling, and the item silently vanished from the list
    // exactly as if it had migrated. Now uses the same MigrationOutcomeReducer decision the bulk
    // loop below uses: only MigrationListItemState.SUCCESS removes the item. A PartialFailure/
    // NotStarted outcome instead sets the item's migrationResult to a retryable Failed state (shown
    // by MigrationListItemAction) so the user can retry via the same "Migrate now"/"Copy now"
    // actions, or explicitly dismiss via the existing Skip action -- the item is never silently
    // discarded. A missing target (no successful search result) is reported the same way rather
    // than silently treated as a success.
    fun migrateNow(mangaId: Long, replace: Boolean) {
        screenModelScope.launch(ioDispatcher) {
            val manga = items.find { it.manga.id == mangaId } ?: return@launch
            val target = (manga.searchResult.value as? SearchResult.Success)?.manga
            if (target == null) {
                manga.migrationResult.value = MigratingManga.MigrationResultState.Failed(retryable = false)
                return@launch
            }
            manga.migrationResult.value = MigratingManga.MigrationResultState.InProgress
            try {
                val outcome = migrateManga(current = manga.manga, target = target, replace = replace)
                when (MigrationOutcomeReducer.reduce(outcome)) {
                    MigrationListItemState.SUCCESS -> {
                        manga.migrationResult.value = null
                        removeManga(mangaId)
                    }
                    MigrationListItemState.RETRYABLE_FAILURE, MigrationListItemState.NOT_STARTED -> {
                        logcat(LogPriority.WARN) { "Migration did not complete for manga $mangaId: $outcome" }
                        manga.migrationResult.value = MigratingManga.MigrationResultState.Failed(retryable = true)
                    }
                    MigrationListItemState.SKIPPED -> Unit
                }
            } catch (e: CancellationException) {
                // A cancelled attempt must not leave the item stuck showing "in progress" and must
                // not create a success/failure receipt of any kind.
                manga.migrationResult.value = null
                throw e
            }
        }
    }

    // KMK -->
    /** Cancel searching without remove it from list so user can perform manual search */
    fun cancelManga(mangaId: Long) {
        screenModelScope.launch(ioDispatcher) {
            val item = items.find { it.manga.id == mangaId } ?: return@launch
            item.searchingJob?.cancel()
            item.searchingJob = null
            item.searchResult.value = SearchResult.NotFound
            updateMigrationProgress()
        }
    }
    // KMK <--

    fun removeManga(mangaId: Long) {
        screenModelScope.launch(ioDispatcher) {
            val item = items.find { it.manga.id == mangaId } ?: return@launch
            removeManga(item)
            item.migrationScope.cancel()
            updateMigrationProgress()
        }
    }

    private fun removeManga(item: MigratingManga) {
        mutableState.update { it.copy(items = items.toPersistentList().remove(item)) }
    }

    override fun onDispose() {
        super.onDispose()
        items.forEach {
            it.migrationScope.cancel()
        }
    }

    fun showMigrateDialog(copy: Boolean) {
        mutableState.update { state ->
            state.copy(
                dialog = Dialog.Migrate(
                    copy = copy,
                    // KMK -->
                    totalCount = state.items.size,
                    skippedCount = state.items.count { it.searchResult.value == SearchResult.NotFound },
                    // KMK <--
                ),
            )
        }
    }

    fun showExitDialog() {
        mutableState.update {
            it.copy(dialog = Dialog.Exit)
        }
    }

    fun dismissDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    // KMK Confirmed Blocker Remediation Corrective Pass 2026-07-29: the Result dialog acknowledges
    // a finished (partially-failed/unresolved) bulk run. Dismissing it only closes the dialog and
    // keeps the user on this screen -- the remaining failed/skipped items are still in the list,
    // retryable via Migrate/Copy or explicitly dismissible via Skip. (Corrects the Follow-up Pass 1
    // version of this function, which called navigateBack() here and so hid those same items from
    // the user immediately after telling them the items needed attention.)
    fun dismissResultDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    // KMK -->
    fun openOptionsDialog() {
        mutableState.update {
            it.copy(dialog = Dialog.Options)
        }
    }

    fun updateOptions() {
        hideUnmatched = preferences.migrationHideUnmatched().get()
        hideWithoutUpdates = preferences.migrationHideWithoutUpdates().get()
        prioritizeByChapters = preferences.migrationPrioritizeByChapters().get()
        deepSearchMode = preferences.migrationDeepSearchMode().get()
    }
    // KMK <--

    data class ChapterInfo(
        val latestChapter: Double?,
        val chapterCount: Int,
        val matchScore: Double = 0.0,
    ) {
        fun withMatchScore(matchScore: Double) = copy(matchScore = matchScore)
    }

    sealed interface Dialog {
        data class Migrate(val copy: Boolean, val totalCount: Int, val skippedCount: Int) : Dialog
        data class Progress(@FloatRange(0.0, 1.0) val progress: Float) : Dialog
        data object Exit : Dialog

        // KMK -->
        data object Options : Dialog
        // KMK <--
        // KMK Confirmed Blocker Remediation follow-up Phase 1, extended by the Corrective Pass:
        // shown after a bulk run when one or more items returned a non-Success MigrationOutcome or
        // had no successful search result at all. Failed/skipped items remain in the item list
        // (never removed by this dialog) so the user can retry or explicitly Skip them.
        data class Result(val failedCount: Int, val skippedCount: Int, val totalCount: Int) : Dialog
    }

    data class State(
        val items: ImmutableList<MigratingManga> = persistentListOf(),
        val finishedCount: Int = 0,
        val migrationComplete: Boolean = false,
        val dialog: Dialog? = null,
    ) {
        val mangaIds: List<Long> = items.map { it.manga.id }
    }
}

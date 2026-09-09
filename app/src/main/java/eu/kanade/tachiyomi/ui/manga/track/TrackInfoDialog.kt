package eu.kanade.tachiyomi.ui.manga.track

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.interactor.RefreshTracks
import eu.kanade.domain.track.model.LocalTrackingActionPolicy
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.manga.components.LocalTrackDetailsDialog
import eu.kanade.presentation.manga.components.LocalTrackStatusDialog
import eu.kanade.presentation.track.LocalTrackingReconciliationDialog
import eu.kanade.presentation.track.TrackChapterSelector
import eu.kanade.presentation.track.TrackDateSelector
import eu.kanade.presentation.track.TrackInfoDialogHome
import eu.kanade.presentation.track.TrackScoreSelector
import eu.kanade.presentation.track.TrackStatusSelector
import eu.kanade.presentation.track.TrackerSearch
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.data.track.DeletableTracker
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.source.rethrowIfFatal
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.lang.convertEpochMillisZone
import eu.kanade.tachiyomi.util.lang.toLocalDate
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import exh.metadata.metadata.base.TrackerIdMetadata
import exh.recs.matching.ConfirmedMangaGroupTargets
import exh.recs.matching.CrossExtensionMatchMode
import exh.recs.matching.CrossExtensionMatchScreen
import exh.source.MERGED_SOURCE_ID
import exh.source.getMainSource
import exh.util.TrackWriteField
import exh.util.recordSuccessfulTrackWrite
import exh.util.recordSuccessfulTrackerBinding
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.QuerySanitizer.sanitize
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.chapter.interactor.GetChapterByUrlAndMangaId
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.GetMergedReferencesById
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.DeleteTrack
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.model.Track
import tachiyomi.domain.tracker.model.LocalTrackedWork
import tachiyomi.domain.tracker.model.LocalTrackedWorkSource
import tachiyomi.domain.tracker.model.LocalTrackedWorkSourceConfirmation
import tachiyomi.domain.tracker.model.LocalTrackedWorkStatus
import tachiyomi.domain.tracker.repository.LocalTrackerRepository
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.AlertDialogContent
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

internal suspend fun <T> runTrackerSearch(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: Throwable) {
    rethrowIfFatal(e)
    Result.failure(e)
}

internal class TrackerSearchRequestGate {
    class Request internal constructor(
        val generation: Long,
        val query: String,
    )

    private var generation = 0L
    private var current: Request? = null

    fun begin(query: String): Request? {
        if (current?.query == query) return null
        return Request(++generation, query).also { current = it }
    }

    fun isCurrent(request: Request): Boolean = current == request

    fun finish(request: Request) {
        if (isCurrent(request)) current = null
    }
}

data class TrackInfoDialogHomeScreen(
    private val mangaId: Long,
    private val mangaTitle: String,
    private val sourceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { Model(mangaId, sourceId) }

        val dateFormat = remember { UiPreferences.dateFormat(Injekt.get<UiPreferences>().dateFormat().get()) }
        val state by screenModel.state.collectAsState()

        // SY -->
        Column(modifier = Modifier.animateContentSize()) {
            if (state.isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                        .windowInsetsPadding(WindowInsets.systemBars),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Text(
                        stringResource(MR.strings.loading),
                        fontSize = 14.sp,
                    )
                }
            }
            // SY <--
            else {
                TrackInfoDialogHome(
                    entries = TrackerEntry.build(state.trackItems, state.localWork),
                    dateFormat = dateFormat,
                    onStatusClick = {
                        navigator.push(
                            TrackStatusSelectorScreen(
                                track = it.track!!,
                                serviceId = it.tracker.id,
                            ),
                        )
                    },
                    onChapterClick = {
                        navigator.push(
                            TrackChapterSelectorScreen(
                                track = it.track!!,
                                serviceId = it.tracker.id,
                            ),
                        )
                    },
                    onScoreClick = {
                        navigator.push(
                            TrackScoreSelectorScreen(
                                track = it.track!!,
                                serviceId = it.tracker.id,
                            ),
                        )
                    },
                    onStartDateEdit = {
                        navigator.push(
                            TrackDateSelectorScreen(
                                track = it.track!!,
                                serviceId = it.tracker.id,
                                start = true,
                            ),
                        )
                    },
                    onEndDateEdit = {
                        navigator.push(
                            TrackDateSelectorScreen(
                                track = it.track!!,
                                serviceId = it.tracker.id,
                                start = false,
                            ),
                        )
                    },
                    onNewSearch = {
                        if (it.tracker is EnhancedTracker) {
                            screenModel.registerEnhancedTracking(it)
                        } else {
                            // SY -->
                            screenModel.newSearch(navigator, it, mangaTitle)
                            // SY <--
                        }
                    },
                    onOpenInBrowser = { openTrackerInBrowser(context, it) },
                    onRemoved = {
                        navigator.push(
                            TrackerRemoveScreen(
                                mangaId = mangaId,
                                track = it.track!!,
                                serviceId = it.tracker.id,
                            ),
                        )
                    },
                    onCopyLink = { context.copyTrackerLink(it) },
                    onTogglePrivate = screenModel::togglePrivate,
                    onLocalClick = screenModel::openLocalStatusDialog,
                    onLocalDetailsClick = screenModel::openLocalDetailsDialog,
                    onLocalChapterClick = screenModel::openLocalChapter,
                    onLocalReconcileClick = screenModel::openLocalReconciliationDialog,
                    onLocalOtherVersionsClick = {
                        state.localWork?.status?.let { status ->
                            navigator.push(
                                CrossExtensionMatchScreen.fromMode(
                                    mangaId,
                                    CrossExtensionMatchMode.LocalTracking(status),
                                ),
                            )
                        }
                    },
                    localDateFormat = dateFormat,
                )
            }
        }
        // KMK v0.8.21-fix2: Local's own dialog lives inside this sheet, entirely independent of the
        // external-tracker rows above -- opening/closing it never touches trackItems state.
        if (state.showLocalStatusDialog) {
            LocalTrackStatusDialog(
                currentStatus = state.localWork?.status,
                onStatusSelected = screenModel::setLocalTrackingStatus,
                onRemove = screenModel::removeLocalTracking,
                onDismissRequest = screenModel::dismissLocalStatusDialog,
            )
        }
        if (state.showLocalDetailsDialog && state.localWork != null) {
            LocalTrackDetailsDialog(
                work = state.localWork!!,
                dateFormat = dateFormat,
                onSave = screenModel::saveLocalDetails,
                onDismissRequest = screenModel::dismissLocalDetailsDialog,
            )
        }
        if (state.showLocalReconciliationDialog && state.localWork != null) {
            LocalTrackingReconciliationDialog(
                work = state.localWork!!,
                entries = state.trackItems,
                dateFormat = dateFormat,
                onImport = screenModel::importExternalMetadata,
                onExport = screenModel::exportLocalMetadata,
                onDismissRequest = screenModel::dismissLocalReconciliationDialog,
            )
        }
    }

    /**
     * Opens registered tracker url in browser
     */
    private fun openTrackerInBrowser(context: Context, trackItem: TrackItem) {
        val url = trackItem.track?.remoteUrl ?: return
        if (url.isNotBlank()) {
            context.openInBrowser(url)
        }
    }

    private fun Context.copyTrackerLink(trackItem: TrackItem) {
        val url = trackItem.track?.remoteUrl ?: return
        if (url.isNotBlank()) {
            copyToClipboard(url, url)
        }
    }

    private class Model(
        private val mangaId: Long,
        private val sourceId: Long,
        private val getTracks: GetTracks = Injekt.get(),
        // SY -->
        private val trackerManager: TrackerManager = Injekt.get(),
        private val trackPreferences: TrackPreferences = Injekt.get(),
        // SY <--
        // KMK -->
        private val sourceManager: SourceManager = Injekt.get(),
        // KMK v0.8.21-fix2: Local now lives in this same screen model instead of MangaScreenModel --
        // see TrackerEntry's doc for why it is a peer entry here rather than a separate manga-detail
        // action.
        private val localTrackerRepository: LocalTrackerRepository = Injekt.get(),
        private val getHistory: GetHistory = Injekt.get(),
        private val getChapter: GetChapter = Injekt.get(),
        private val sourcePreferences: SourcePreferences = Injekt.get(),
        private val confirmedMangaGroupTargets: ConfirmedMangaGroupTargets = Injekt.get(),
        private val getChapterByUrlAndMangaId: GetChapterByUrlAndMangaId = Injekt.get(),
        private val application: Application = Injekt.get(),
        // KMK <--
    ) : StateScreenModel<Model.State>(State()) {
        // KMK -->
        private val getFlatMetadataById: GetFlatMetadataById by injectLazy()
        private val getMangaById: GetManga by injectLazy()
        private val getMergedReferencesById: GetMergedReferencesById by injectLazy()
        // KMK <--

        init {
            screenModelScope.launch {
                refreshTrackers()
            }

            screenModelScope.launch {
                getTracks.subscribe(mangaId)
                    .catch { logcat(LogPriority.ERROR) { "Tracker list loading failed" } }
                    .distinctUntilChanged()
                    .map { it.mapToTrackItem() }
                    .collectLatest { trackItems -> mutableState.update { it.copy(trackItems = trackItems) } }
            }

            // KMK v0.8.21-fix2 -->
            screenModelScope.launch {
                val manga = getMangaById.await(mangaId) ?: return@launch
                combine(
                    localTrackerRepository.observeWorkIdBySourceUrl(manga.source, manga.url),
                    localTrackerRepository.getAllWorksAsFlow(),
                ) { workId, works ->
                    workId?.let { id -> works.firstOrNull { it.id == id } }
                }
                    .distinctUntilChanged()
                    .collectLatest { work -> mutableState.update { it.copy(localWork = work) } }
            }
            // KMK <--
        }

        // KMK v0.8.21-fix2 -->
        private suspend fun refreshLocalTrackedWork() {
            val manga = getMangaById.await(mangaId) ?: return
            val work = localTrackerRepository.getWorkIdBySourceUrl(manga.source, manga.url)
                ?.let { localTrackerRepository.getWork(it) }
            mutableState.update { it.copy(localWork = work) }
        }

        fun openLocalStatusDialog() {
            mutableState.update { it.copy(showLocalStatusDialog = true) }
        }

        fun dismissLocalStatusDialog() {
            mutableState.update { it.copy(showLocalStatusDialog = false) }
        }

        fun openLocalDetailsDialog() {
            if (state.value.localWork != null) mutableState.update { it.copy(showLocalDetailsDialog = true) }
        }

        fun openLocalChapter() {
            screenModelScope.launch {
                val manga = getMangaById.await(mangaId) ?: return@launch
                val workId = localTrackerRepository.getWorkIdBySourceUrl(manga.source, manga.url)
                val sourceProgress = workId?.let {
                    localTrackerRepository.getSourceProgress(it, manga.source, manga.url)
                }
                val chapter = sourceProgress?.chapterUrl
                    ?.takeIf { it.isNotBlank() }
                    ?.let { getChapterByUrlAndMangaId.await(it, manga.id) }
                    ?: LocalTrackingHistoryProgressPolicy.resolve(getHistory.await(mangaId))
                        ?.let { getChapter.await(it.chapterId) }
                    ?: return@launch
                application.startActivity(
                    ReaderActivity.newIntent(application, mangaId, chapter.id)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }

        fun dismissLocalDetailsDialog() {
            mutableState.update { it.copy(showLocalDetailsDialog = false) }
        }

        fun openLocalReconciliationDialog() {
            if (state.value.localWork != null && state.value.trackItems.any { it.track != null }) {
                mutableState.update { it.copy(showLocalReconciliationDialog = true) }
            }
        }

        fun dismissLocalReconciliationDialog() {
            mutableState.update { it.copy(showLocalReconciliationDialog = false) }
        }

        fun importExternalMetadata(item: TrackItem) {
            val external = item.track ?: return
            val work = state.value.localWork ?: return
            dismissLocalReconciliationDialog()
            runLocalTrackingAction {
                localTrackerRepository.upsertWork(
                    LocalTrackingReconciliationPolicy.mergeIntoLocal(
                        work,
                        LocalTrackingReconciliationPolicy.externalSnapshot(external, item.tracker),
                    ),
                )
            }
        }

        fun exportLocalMetadata(item: TrackItem) {
            val external = item.track ?: return
            val work = state.value.localWork ?: return
            runLocalTrackingAction {
                val tracker = item.tracker
                val remoteTrack = external.toDbTrack()
                val applicableFields = buildSet {
                    LocalTrackingReconciliationPolicy.externalStatus(tracker, work.status)?.let { add(TrackWriteField.STATUS) }
                    work.lastChapterNumber?.let { add(TrackWriteField.CHAPTER_PROGRESS) }
                    (
                        work.score?.let { LocalTrackingReconciliationPolicy.scoreString(tracker, it) }
                            ?: LocalTrackingReconciliationPolicy.clearScoreString(tracker)
                        )
                        ?.takeIf { work.score != null || external.score != 0.0 }
                        ?.let { add(TrackWriteField.SCORE) }
                    if (tracker.supportsReadingDates) {
                        (work.startDate ?: 0L).takeIf { work.startDate != null || external.startDate > 0L }?.let { add(TrackWriteField.START_DATE) }
                        (work.finishDate ?: 0L).takeIf { work.finishDate != null || external.finishDate > 0L }?.let { add(TrackWriteField.FINISH_DATE) }
                    }
                }
                val pendingFields = state.value.pendingExternalWrites[tracker.id]
                val fieldsToAttempt = LocalTrackingReconciliationPolicy.fieldsToAttempt(
                    applicableFields = applicableFields,
                    pendingFields = pendingFields,
                )
                var attemptedFields = 0
                val failedFields = mutableSetOf<TrackWriteField>()
                LocalTrackingReconciliationPolicy.externalStatus(tracker, work.status)
                    ?.takeIf { TrackWriteField.STATUS in fieldsToAttempt }
                    ?.let { status ->
                        attemptedFields++
                        if (!withTrackedWrite(
                                tracker = tracker,
                                track = external,
                                field = TrackWriteField.STATUS,
                                previousStatus = external.status,
                            ) {
                                tracker.setRemoteStatus(remoteTrack, status)
                            }
                        ) {
                            failedFields += TrackWriteField.STATUS
                        }
                    }
                work.lastChapterNumber?.takeIf { TrackWriteField.CHAPTER_PROGRESS in fieldsToAttempt }?.let { chapterNumber ->
                    attemptedFields++
                    if (!withTrackedWrite(
                            tracker = tracker,
                            track = external,
                            field = TrackWriteField.CHAPTER_PROGRESS,
                            previousChapterProgress = external.lastChapterRead.toInt(),
                        ) {
                            tracker.setRemoteLastChapterRead(remoteTrack, chapterNumber.toInt())
                        }
                    ) {
                        failedFields += TrackWriteField.CHAPTER_PROGRESS
                    }
                }
                (
                    work.score?.let { LocalTrackingReconciliationPolicy.scoreString(tracker, it) }
                        ?: LocalTrackingReconciliationPolicy.clearScoreString(tracker)
                    )
                    ?.takeIf { work.score != null || external.score != 0.0 }
                    ?.takeIf { TrackWriteField.SCORE in fieldsToAttempt }
                    ?.let { score ->
                        attemptedFields++
                        if (!withTrackedWrite(
                                tracker = tracker,
                                track = external,
                                field = TrackWriteField.SCORE,
                            ) {
                                tracker.setRemoteScore(remoteTrack, score)
                            }
                        ) {
                            failedFields += TrackWriteField.SCORE
                        }
                    }
                if (tracker.supportsReadingDates) {
                    (work.startDate ?: 0L).takeIf {
                        (work.startDate != null || external.startDate > 0L) && TrackWriteField.START_DATE in fieldsToAttempt
                    }?.let { startDate ->
                        attemptedFields++
                        if (!withTrackedWrite(
                                tracker = tracker,
                                track = external,
                                field = TrackWriteField.START_DATE,
                                previousStartDate = external.startDate,
                            ) {
                                tracker.setRemoteStartDate(remoteTrack, startDate)
                            }
                        ) {
                            failedFields += TrackWriteField.START_DATE
                        }
                    }
                    (work.finishDate ?: 0L).takeIf {
                        (work.finishDate != null || external.finishDate > 0L) && TrackWriteField.FINISH_DATE in fieldsToAttempt
                    }?.let { finishDate ->
                        attemptedFields++
                        if (!withTrackedWrite(
                                tracker = tracker,
                                track = external,
                                field = TrackWriteField.FINISH_DATE,
                                previousFinishDate = external.finishDate,
                            ) {
                                tracker.setRemoteFinishDate(remoteTrack, finishDate)
                            }
                        ) {
                            failedFields += TrackWriteField.FINISH_DATE
                        }
                    }
                }
                if (failedFields.isNotEmpty()) {
                    mutableState.update {
                        it.copy(pendingExternalWrites = it.pendingExternalWrites + (tracker.id to failedFields))
                    }
                    withUIContext {
                        application.toast(
                            application.stringResource(
                                KMR.strings.local_tracking_export_partial,
                                LocalTrackingReconciliationPolicy.completedFieldCount(attemptedFields, failedFields),
                                failedFields.size,
                            ),
                        )
                    }
                } else {
                    mutableState.update {
                        it.copy(pendingExternalWrites = it.pendingExternalWrites - tracker.id)
                    }
                    dismissLocalReconciliationDialog()
                }
            }
        }

        fun saveLocalDetails(score: Double?, startDate: Long?, finishDate: Long?) {
            mutableState.update { it.copy(showLocalDetailsDialog = false) }
            runLocalTrackingAction {
                val work = state.value.localWork ?: return@runLocalTrackingAction
                localTrackerRepository.upsertWork(
                    work.copy(score = score, startDate = startDate, finishDate = finishDate, updatedAt = System.currentTimeMillis()),
                )
            }
        }

        /**
         * Fences every local-tracking write against duplicate taps -- same shared in-flight-flag
         * pattern already used by [MangaScreenModel][eu.kanade.tachiyomi.ui.manga.MangaScreenModel]'s
         * own local-tracking actions before this move.
         */
        private fun runLocalTrackingAction(block: suspend () -> Unit) {
            if (state.value.isLocalTrackingActionInProgress) return
            mutableState.update { it.copy(isLocalTrackingActionInProgress = true) }
            screenModelScope.launch {
                try {
                    block()
                    refreshLocalTrackedWork()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR) { "Local tracking action failed" }
                    withUIContext {
                        application.toast(application.stringResource(MR.strings.unknown_error))
                    }
                } finally {
                    mutableState.update { it.copy(isLocalTrackingActionInProgress = false) }
                }
            }
        }

        /** Applies a status from the local status dialog, creating the work on first use. */
        fun setLocalTrackingStatus(status: LocalTrackedWorkStatus) {
            mutableState.update { it.copy(showLocalStatusDialog = false) }
            runLocalTrackingAction {
                val manga = getMangaById.await(mangaId) ?: return@runLocalTrackingAction
                val now = System.currentTimeMillis()
                val targets = if (sourcePreferences.confirmedTrackedVersionLocalTrackingPropagationEnabled().get()) {
                    confirmedMangaGroupTargets.await(manga)
                } else {
                    listOf(manga)
                }
                // Seed a newly created shared work from the latest read across the confirmed
                // versions. Reading one version must not lose progress when tracking another.
                val sharedHistoryProgress = targets
                    .mapNotNull { target ->
                        LocalTrackingHistoryProgressPolicy.resolve(getHistory.await(target.id))
                            ?.let { progress -> target to progress }
                    }
                    .maxByOrNull { it.second.progressAt }
                val sharedLatestRead = sharedHistoryProgress?.let { (target, progress) ->
                    getChapter.await(progress.chapterId)?.let { chapter -> Triple(target, progress, chapter) }
                }
                var sharedWorkId = localTrackerRepository.getWorkIdBySourceUrl(manga.source, manga.url)
                var sharedWorkInitialized = false
                for (target in targets) {
                    val existingId = localTrackerRepository.getWorkIdBySourceUrl(target.source, target.url)
                    val workId = existingId ?: sharedWorkId ?: UUID.randomUUID().toString().also { sharedWorkId = it }
                    val existingWork = existingId?.let { localTrackerRepository.getWork(it) }
                    if (existingWork != null) {
                        sharedWorkInitialized = true
                        if (sharedWorkId == null) sharedWorkId = existingId
                        localTrackerRepository.upsertWork(
                            existingWork.copy(
                                status = status,
                                startDate = existingWork.startDate
                                    ?: now.takeIf { status != LocalTrackedWorkStatus.PLANNED },
                                finishDate = LocalTrackingActionPolicy.finishDateForStatus(status, now),
                                updatedAt = now,
                            ),
                        )
                    } else if (!sharedWorkInitialized) {
                        val historyProgress = sharedHistoryProgress?.second
                        val latestRead = sharedLatestRead?.let { (_, progress, chapter) -> progress to chapter }
                        localTrackerRepository.upsertWork(
                            LocalTrackedWork(
                                id = workId,
                                title = target.title,
                                normalizedTitle = target.title.trim().lowercase(),
                                status = status,
                                lastChapterSource = latestRead?.second?.let { target.source },
                                lastChapterNumber = latestRead?.second
                                    ?.takeIf { it.isRecognizedNumber }
                                    ?.chapterNumber,
                                lastChapterUrl = latestRead?.second?.url,
                                lastChapterLabel = latestRead?.second?.name,
                                lastProgressAt = latestRead?.first?.progressAt,
                                startDate = historyProgress?.firstReadAt
                                    ?: now.takeIf { status != LocalTrackedWorkStatus.PLANNED },
                                finishDate = LocalTrackingActionPolicy.finishDateForStatus(status, now),
                                createdAt = now,
                                updatedAt = now,
                            ),
                        )
                        sharedWorkInitialized = true
                    }
                    localTrackerRepository.upsertSource(
                        LocalTrackedWorkSource(
                            workId = workId,
                            source = target.source,
                            url = target.url,
                            title = target.title,
                            confidence = 100,
                            confirmation = LocalTrackedWorkSourceConfirmation.USER_CONFIRMED,
                            createdAt = existingWork?.createdAt ?: now,
                            updatedAt = now,
                        ),
                    )
                }
            }
        }

        fun removeLocalTracking() {
            mutableState.update { it.copy(showLocalStatusDialog = false) }
            runLocalTrackingAction {
                val manga = getMangaById.await(mangaId) ?: return@runLocalTrackingAction
                localTrackerRepository.getWorkIdBySourceUrl(manga.source, manga.url)?.let {
                    localTrackerRepository.deleteWork(it)
                }
            }
        }
        // KMK <--

        // KMK -->
        private suspend fun getMangaForTracking(item: TrackItem): Manga? {
            if (sourceId != MERGED_SOURCE_ID) {
                return getMangaById.await(mangaId)
            }
            item.tracker as EnhancedTracker
            val references = getMergedReferencesById.await(mangaId)
            return references.distinctBy { it.mangaSourceId }.firstNotNullOfOrNull { ref ->
                sourceManager.get(ref.mangaSourceId)
                    ?.takeIf(item.tracker::accept)
                    ?.let { ref.mangaId?.let { mangaId -> getMangaById.await(mangaId) } }
            }
        }
        // KMK <--

        fun registerEnhancedTracking(item: TrackItem) {
            item.tracker as EnhancedTracker
            screenModelScope.launchNonCancellable {
                val manga = getMangaForTracking(item) ?: return@launchNonCancellable
                try {
                    val matchResult = item.tracker.match(manga) ?: throw Exception()
                    item.tracker.register(matchResult, mangaId)
                    recordSuccessfulTrackerBinding(
                        evaluationModeEnabled = Injekt.get<SourcePreferences>().evaluationMode().get(),
                        mangaId = mangaId,
                        trackerId = matchResult.tracker_id,
                        remoteId = matchResult.remote_id,
                    )
                } catch (_: Exception) {
                    withUIContext { Injekt.get<Application>().toast(MR.strings.error_no_match) }
                }
            }
        }

        // SY -->
        fun newSearch(navigator: Navigator, item: TrackItem, mangaTitle: String) {
            screenModelScope.launchNonCancellable {
                if (trackPreferences.resolveUsingSourceMetadata().get()) {
                    // Check if the tracker id is contained in the metadata
                    val result = getTrackerIdFromMetadata(item.tracker.id)
                    if (result != null) {
                        mutableState.update { it.copy(isLoading = true) }

                        // Try to register tracking by id
                        val success = registerTrackingById(item.tracker.id, result)

                        mutableState.update { it.copy(isLoading = false) }

                        if (success) {
                            // Return on success
                            return@launchNonCancellable
                        }
                    }
                }

                // Open search screen
                navigator.push(
                    TrackerSearchScreen(
                        mangaId = mangaId,
                        initialQuery = item.track?.title ?: mangaTitle,
                        currentUrl = item.track?.remoteUrl,
                        serviceId = item.tracker.id,
                    ),
                )
            }
        }

        suspend fun getTrackerIdFromMetadata(trackerId: Long): String? {
            try {
                val metadataSource = sourceManager.get(sourceId)
                    ?.getMainSource<MetadataSource<*, *>>() ?: return null

                return getFlatMetadataById.await(mangaId)?.run {
                    // Use 'raise' to dynamically obtain the specific metadata type and then attempt to cast
                    raise(metadataSource.metaClass) as? TrackerIdMetadata
                }?.let { metadata ->
                    when (trackerId) {
                        trackerManager.aniList.id -> metadata.anilistId
                        trackerManager.kitsu.id -> metadata.kitsuId
                        trackerManager.myAnimeList.id -> metadata.myAnimeListId
                        trackerManager.mangaUpdates.id -> metadata.mangaUpdatesId
                        else -> null
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR) { "Tracker metadata ID lookup failed" }
                return null
            }
        }

        suspend fun registerTrackingById(trackerId: Long, remoteId: String): Boolean {
            trackerManager.get(trackerId)?.let { tracker ->
                try {
                    tracker.searchById(remoteId)?.let { track ->
                        tracker.register(track, mangaId)
                        recordSuccessfulTrackerBinding(
                            evaluationModeEnabled = Injekt.get<SourcePreferences>().evaluationMode().get(),
                            mangaId = mangaId,
                            trackerId = track.tracker_id,
                            remoteId = track.remote_id,
                        )
                        return true
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    logcat(LogPriority.ERROR) { "Tracker bind-by-ID failed" }
                }
            }
            return false
        }
        // SY <--

        private suspend fun refreshTrackers() {
            val refreshTracks = Injekt.get<RefreshTracks>()
            val context = Injekt.get<Application>()

            refreshTracks.await(mangaId)
                .filter { it.first != null }
                .forEach { (track, e) ->
                    logcat(LogPriority.ERROR) { "Tracker refresh failed" }
                    withUIContext {
                        context.toast(
                            context.stringResource(
                                MR.strings.track_error,
                                track!!.name,
                                with(context) { e.formattedMessage },
                            ),
                        )
                    }
                }
        }

        fun togglePrivate(item: TrackItem) {
            screenModelScope.launchNonCancellable {
                val track = item.track ?: return@launchNonCancellable
                withTrackedWrite(
                    tracker = item.tracker,
                    track = track,
                    field = TrackWriteField.PRIVATE,
                    previousPrivate = track.private,
                ) {
                    item.tracker.setRemotePrivate(track.toDbTrack(), !track.private)
                }
            }
        }

        private suspend fun List<Track>.mapToTrackItem(): List<TrackItem> {
            val loggedInTrackers = trackerManager.loggedInTrackers()
            val source = sourceManager.getOrStub(sourceId)
            return loggedInTrackers
                // Map to TrackItem
                .map { service -> TrackItem(find { it.trackerId == service.id }, service) }
                // Show only if the service supports this manga's source
                // KMK -->
                .let { trackers ->
                    val sources = if (source is MergedSource) {
                        sourceManager.getMergedSources(mangaId)
                    } else {
                        listOf(source)
                    }
                    trackers.filter { (it.tracker as? EnhancedTracker)?.accept(sources) ?: true }
                }
            // KMK <--
        }

        @Immutable
        data class State(
            val trackItems: List<TrackItem> = emptyList(),
            // SY -->
            val isLoading: Boolean = false,
            // SY <--
            // KMK v0.8.21-fix2 -->
            val localWork: LocalTrackedWork? = null,
            val showLocalStatusDialog: Boolean = false,
            val showLocalDetailsDialog: Boolean = false,
            val showLocalReconciliationDialog: Boolean = false,
            val isLocalTrackingActionInProgress: Boolean = false,
            val pendingExternalWrites: Map<Long, Set<TrackWriteField>> = emptyMap(),
            // KMK <--
        )
    }
}

// KMK Universal Action History Recovery Plan 2026-08-01: the BaseTracker write path rethrows
// ordinary remote failures after preserving its existing log/toast behavior. This helper keeps the
// three user-initiated setter models consistent: a receipt is committed only after normal completion,
// and ordinary failures remain visible through the existing toast without escaping the dialog scope.
// `internal` (not
// `private`) so WithTrackedWriteTest can exercise this exact function directly with a local fake
// tracker write -- no other production code change, no real tracker account. [sourcePreferences] is
// now an explicit default-Injekt parameter (matching every other Injekt dependency's convention in
// this codebase) instead of an inline `Injekt.get()` call in the body -- Injekt's singleton caching
// is process-global, so an inline call could observe a `SourcePreferences` instance already cached
// by unrelated code earlier in the same test JVM; an explicit parameter lets a test substitute its
// own instance deterministically, the same way every other test in this codebase already does.
internal suspend fun withTrackedWrite(
    tracker: Tracker,
    track: Track,
    field: TrackWriteField,
    previousStatus: Long? = null,
    previousScore: String? = null,
    previousChapterProgress: Int? = null,
    previousStartDate: Long? = null,
    previousFinishDate: Long? = null,
    previousPrivate: Boolean? = null,
    sourcePreferences: SourcePreferences = Injekt.get(),
    write: suspend () -> Unit,
): Boolean {
    try {
        write()
        recordSuccessfulTrackWrite(
            evaluationModeEnabled = sourcePreferences.evaluationMode().get(),
            mangaId = track.mangaId,
            trackerId = tracker.id,
            field = field,
            previousStatus = previousStatus,
            previousScore = previousScore,
            previousChapterProgress = previousChapterProgress,
            previousStartDate = previousStartDate,
            previousFinishDate = previousFinishDate,
            previousPrivate = previousPrivate,
        )
        return true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // BaseTracker has already logged and surfaced the remote failure to the user.
        return false
    }
}

private data class TrackStatusSelectorScreen(
    private val track: Track,
    private val serviceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel {
            Model(
                track = track,
                tracker = Injekt.get<TrackerManager>().get(serviceId)!!,
            )
        }
        val state by screenModel.state.collectAsState()
        TrackStatusSelector(
            selection = state.selection,
            onSelectionChange = screenModel::setSelection,
            selections = remember { screenModel.getSelections() },
            onConfirm = {
                screenModel.setStatus()
                navigator.pop()
            },
            onDismissRequest = navigator::pop,
        )
    }

    private class Model(
        private val track: Track,
        private val tracker: Tracker,
    ) : StateScreenModel<Model.State>(State(track.status)) {

        fun getSelections(): Map<Long, StringResource?> {
            return tracker.getStatusList().associateWith { tracker.getStatus(it) }
        }

        fun setSelection(selection: Long) {
            mutableState.update { it.copy(selection = selection) }
        }

        fun setStatus() {
            screenModelScope.launchNonCancellable {
                withTrackedWrite(
                    tracker = tracker,
                    track = track,
                    field = TrackWriteField.STATUS,
                    previousStatus = track.status,
                ) {
                    tracker.setRemoteStatus(track.toDbTrack(), state.value.selection)
                }
            }
        }

        @Immutable
        data class State(
            val selection: Long,
        )
    }
}

private data class TrackChapterSelectorScreen(
    private val track: Track,
    private val serviceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel {
            Model(
                track = track,
                tracker = Injekt.get<TrackerManager>().get(serviceId)!!,
            )
        }
        val state by screenModel.state.collectAsState()

        TrackChapterSelector(
            selection = state.selection,
            onSelectionChange = screenModel::setSelection,
            range = remember { screenModel.getRange() },
            onConfirm = {
                screenModel.setChapter()
                navigator.pop()
            },
            onDismissRequest = navigator::pop,
        )
    }

    private class Model(
        private val track: Track,
        private val tracker: Tracker,
    ) : StateScreenModel<Model.State>(State(track.lastChapterRead.toInt())) {

        fun getRange(): Iterable<Int> {
            val endRange = if (track.totalChapters > 0) {
                track.totalChapters
            } else {
                10000
            }
            return 0..endRange.toInt()
        }

        fun setSelection(selection: Int) {
            mutableState.update { it.copy(selection = selection) }
        }

        fun setChapter() {
            screenModelScope.launchNonCancellable {
                withTrackedWrite(
                    tracker = tracker,
                    track = track,
                    field = TrackWriteField.CHAPTER_PROGRESS,
                    previousChapterProgress = track.lastChapterRead.toInt(),
                ) {
                    tracker.setRemoteLastChapterRead(track.toDbTrack(), state.value.selection)
                }
            }
        }

        @Immutable
        data class State(
            val selection: Int,
        )
    }
}

private data class TrackScoreSelectorScreen(
    private val track: Track,
    private val serviceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel {
            Model(
                track = track,
                tracker = Injekt.get<TrackerManager>().get(serviceId)!!,
            )
        }
        val state by screenModel.state.collectAsState()

        TrackScoreSelector(
            selection = state.selection,
            onSelectionChange = screenModel::setSelection,
            selections = remember { screenModel.getSelections() },
            onConfirm = {
                screenModel.setScore()
                navigator.pop()
            },
            onDismissRequest = navigator::pop,
        )
    }

    private class Model(
        private val track: Track,
        private val tracker: Tracker,
    ) : StateScreenModel<Model.State>(State(tracker.displayScore(track))) {

        fun getSelections(): ImmutableList<String> {
            return tracker.getScoreList()
        }

        fun setSelection(selection: String) {
            mutableState.update { it.copy(selection = selection) }
        }

        fun setScore() {
            screenModelScope.launchNonCancellable {
                withTrackedWrite(
                    tracker = tracker,
                    track = track,
                    field = TrackWriteField.SCORE,
                    previousScore = tracker.displayScore(track),
                ) {
                    tracker.setRemoteScore(track.toDbTrack(), state.value.selection)
                }
            }
        }

        @Immutable
        data class State(
            val selection: String,
        )
    }
}

private data class TrackDateSelectorScreen(
    private val track: Track,
    private val serviceId: Long,
    private val start: Boolean,
) : Screen() {

    @Transient
    private val selectableDates = object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
            val targetDate = Instant.ofEpochMilli(utcTimeMillis).toLocalDate(ZoneOffset.UTC)

            // Disallow future dates
            if (targetDate > LocalDate.now(ZoneOffset.UTC)) return false

            return when {
                // Disallow setting start date after finish date
                start && track.finishDate > 0 -> {
                    val finishDate = Instant.ofEpochMilli(track.finishDate).toLocalDate(ZoneOffset.UTC)
                    targetDate <= finishDate
                }
                // Disallow setting finish date before start date
                !start && track.startDate > 0 -> {
                    val startDate = Instant.ofEpochMilli(track.startDate).toLocalDate(ZoneOffset.UTC)
                    startDate <= targetDate
                }
                else -> {
                    true
                }
            }
        }

        override fun isSelectableYear(year: Int): Boolean {
            // Disallow future years
            if (year > LocalDate.now(ZoneOffset.UTC).year) return false

            return when {
                // Disallow setting start year after finish year
                start && track.finishDate > 0 -> {
                    val finishDate = Instant.ofEpochMilli(track.finishDate).toLocalDate(ZoneOffset.UTC)
                    year <= finishDate.year
                }
                // Disallow setting finish year before start year
                !start && track.startDate > 0 -> {
                    val startDate = Instant.ofEpochMilli(track.startDate).toLocalDate(ZoneOffset.UTC)
                    startDate.year <= year
                }
                else -> {
                    true
                }
            }
        }
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel {
            Model(
                track = track,
                tracker = Injekt.get<TrackerManager>().get(serviceId)!!,
                start = start,
            )
        }

        val canRemove = if (start) {
            track.startDate > 0
        } else {
            track.finishDate > 0
        }
        TrackDateSelector(
            title = if (start) {
                stringResource(MR.strings.track_started_reading_date)
            } else {
                stringResource(MR.strings.track_finished_reading_date)
            },
            initialSelectedDateMillis = screenModel.initialSelection,
            selectableDates = selectableDates,
            onConfirm = {
                screenModel.setDate(it)
                navigator.pop()
            },
            onRemove = { screenModel.confirmRemoveDate(navigator) }.takeIf { canRemove },
            onDismissRequest = navigator::pop,
        )
    }

    private class Model(
        private val track: Track,
        private val tracker: Tracker,
        private val start: Boolean,
    ) : ScreenModel {

        // In UTC
        val initialSelection: Long
            get() {
                val millis = (if (start) track.startDate else track.finishDate)
                    .takeIf { it != 0L }
                    ?: Instant.now().toEpochMilli()
                return millis.convertEpochMillisZone(ZoneOffset.systemDefault(), ZoneOffset.UTC)
            }

        // In UTC
        fun setDate(millis: Long) {
            // Convert to local time
            val localMillis = millis.convertEpochMillisZone(ZoneOffset.UTC, ZoneOffset.systemDefault())
            screenModelScope.launchNonCancellable {
                withTrackedWrite(
                    tracker = tracker,
                    track = track,
                    field = if (start) TrackWriteField.START_DATE else TrackWriteField.FINISH_DATE,
                    previousStartDate = track.startDate.takeIf { start },
                    previousFinishDate = track.finishDate.takeIf { !start },
                ) {
                    if (start) {
                        tracker.setRemoteStartDate(track.toDbTrack(), localMillis)
                    } else {
                        tracker.setRemoteFinishDate(track.toDbTrack(), localMillis)
                    }
                }
            }
        }

        fun confirmRemoveDate(navigator: Navigator) {
            navigator.push(TrackDateRemoverScreen(track, tracker.id, start))
        }
    }
}

private data class TrackDateRemoverScreen(
    private val track: Track,
    private val serviceId: Long,
    private val start: Boolean,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel {
            Model(
                track = track,
                tracker = Injekt.get<TrackerManager>().get(serviceId)!!,
                start = start,
            )
        }
        AlertDialogContent(
            modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                )
            },
            title = {
                Text(
                    text = stringResource(MR.strings.track_remove_date_conf_title),
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                val serviceName = screenModel.getServiceName()
                Text(
                    text = if (start) {
                        stringResource(MR.strings.track_remove_start_date_conf_text, serviceName)
                    } else {
                        stringResource(MR.strings.track_remove_finish_date_conf_text, serviceName)
                    },
                )
            },
            buttons = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small, Alignment.End),
                ) {
                    TextButton(onClick = navigator::pop) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                    FilledTonalButton(
                        onClick = {
                            screenModel.removeDate()
                            navigator.popUntil { it is TrackInfoDialogHomeScreen }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Text(text = stringResource(MR.strings.action_remove))
                    }
                }
            },
        )
    }

    private class Model(
        private val track: Track,
        private val tracker: Tracker,
        private val start: Boolean,
    ) : ScreenModel {

        fun getServiceName() = tracker.name

        fun removeDate() {
            screenModelScope.launchNonCancellable {
                withTrackedWrite(
                    tracker = tracker,
                    track = track,
                    field = if (start) TrackWriteField.START_DATE else TrackWriteField.FINISH_DATE,
                    previousStartDate = track.startDate.takeIf { start },
                    previousFinishDate = track.finishDate.takeIf { !start },
                ) {
                    if (start) {
                        tracker.setRemoteStartDate(track.toDbTrack(), 0)
                    } else {
                        tracker.setRemoteFinishDate(track.toDbTrack(), 0)
                    }
                }
            }
        }
    }
}

data class TrackerSearchScreen(
    private val mangaId: Long,
    private val initialQuery: String,
    private val currentUrl: String?,
    private val serviceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val screenModel = rememberScreenModel {
            Model(
                mangaId = mangaId,
                currentUrl = currentUrl,
                initialQuery = initialQuery,
                tracker = Injekt.get<TrackerManager>().get(serviceId)!!,
            )
        }

        val state by screenModel.state.collectAsState()

        val textFieldState = rememberTextFieldState(initialQuery)
        TrackerSearch(
            state = textFieldState,
            trackerName = screenModel.trackerName,
            onDispatchQuery = { screenModel.trackingSearch(textFieldState.text.toString()) },
            queryResult = state.queryResult,
            selected = state.selected,
            onSelectedChange = screenModel::updateSelection,
            onConfirmSelection = f@{ private: Boolean ->
                val selected = state.selected ?: return@f
                selected.private = private
                scope.launch {
                    if (screenModel.registerTracking(selected)) {
                        navigator.pop()
                    } else {
                        context.toast(MR.strings.unknown_error)
                    }
                }
            },
            onDismissRequest = navigator::pop,
            supportsPrivateTracking = screenModel.supportsPrivateTracking,
        )
    }

    private class Model(
        private val mangaId: Long,
        private val currentUrl: String? = null,
        initialQuery: String,
        private val tracker: Tracker,
    ) : StateScreenModel<Model.State>(State()) {

        val supportsPrivateTracking = tracker.supportsPrivateTracking
        val trackerName = tracker.name

        private val searchRequestGate = TrackerSearchRequestGate()
        private var searchJob: Job? = null

        init {
            // Run search on first launch
            if (initialQuery.isNotBlank()) {
                trackingSearch(initialQuery)
            }
        }

        fun trackingSearch(query: String) {
            val request = searchRequestGate.begin(query.sanitize()) ?: return
            searchJob?.cancel()
            searchJob = screenModelScope.launch {
                // To show loading state
                mutableState.update { it.copy(queryResult = null, selected = null) }

                try {
                    val result = withIOContext {
                        runTrackerSearch { tracker.search(request.query) }
                    }
                    if (!searchRequestGate.isCurrent(request)) return@launch
                    mutableState.update { oldState ->
                        oldState.copy(
                            queryResult = result,
                            selected = result.getOrNull()?.find { it.tracking_url == currentUrl },
                        )
                    }
                } finally {
                    searchRequestGate.finish(request)
                }
            }
        }

        suspend fun registerTracking(item: TrackSearch): Boolean = try {
            tracker.register(item, mangaId)
            recordSuccessfulTrackerBinding(
                evaluationModeEnabled = Injekt.get<SourcePreferences>().evaluationMode().get(),
                mangaId = mangaId,
                trackerId = item.tracker_id,
                remoteId = item.remote_id,
            )
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Tracker registration failed" }
            false
        }

        fun updateSelection(selected: TrackSearch) {
            mutableState.update { it.copy(selected = selected) }
        }

        @Immutable
        data class State(
            val queryResult: Result<List<TrackSearch>>? = null,
            val selected: TrackSearch? = null,
        )
    }
}

private data class TrackerRemoveScreen(
    private val mangaId: Long,
    private val track: Track,
    private val serviceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel {
            Model(
                mangaId = mangaId,
                track = track,
                tracker = Injekt.get<TrackerManager>().get(serviceId)!!,
            )
        }
        val serviceName = screenModel.getName()
        var removeRemoteTrack by remember { mutableStateOf(false) }
        AlertDialogContent(
            modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                )
            },
            title = {
                Text(
                    text = stringResource(MR.strings.track_delete_title, serviceName),
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    Text(
                        text = stringResource(MR.strings.track_delete_text, serviceName),
                    )

                    if (screenModel.isDeletable()) {
                        LabeledCheckbox(
                            label = stringResource(MR.strings.track_delete_remote_text, serviceName),
                            checked = removeRemoteTrack,
                            onCheckedChange = { removeRemoteTrack = it },
                        )
                    }
                }
            },
            buttons = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        MaterialTheme.padding.small,
                        Alignment.End,
                    ),
                ) {
                    TextButton(onClick = navigator::pop) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                    FilledTonalButton(
                        onClick = {
                            screenModel.unregisterTracking(serviceId)
                            if (removeRemoteTrack) screenModel.deleteMangaFromService()
                            navigator.pop()
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Text(text = stringResource(MR.strings.action_ok))
                    }
                }
            },
        )
    }

    private class Model(
        private val mangaId: Long,
        private val track: Track,
        private val tracker: Tracker,
        private val deleteTrack: DeleteTrack = Injekt.get(),
    ) : ScreenModel {

        fun getName() = tracker.name

        fun isDeletable() = tracker is DeletableTracker

        fun deleteMangaFromService() {
            screenModelScope.launchNonCancellable {
                try {
                    (tracker as DeletableTracker).delete(track)
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR) { "Remote tracker deletion failed" }
                }
            }
        }

        fun unregisterTracking(serviceId: Long) {
            screenModelScope.launchNonCancellable { deleteTrack.await(mangaId, serviceId) }
        }
    }
}

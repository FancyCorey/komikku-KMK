package eu.kanade.tachiyomi.ui.reader

import android.app.Application
import android.net.Uri
import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.chapter.model.toDbChapter
import eu.kanade.domain.manga.interactor.SetMangaViewerFlags
import eu.kanade.domain.manga.model.readerOrientation
import eu.kanade.domain.manga.model.readingMode
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.domain.track.interactor.RecordLocalTrackedChapterProgress
import eu.kanade.domain.track.interactor.TrackChapter
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.data.database.models.toDomainChapter
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.saver.Image
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.saver.Location
import eu.kanade.tachiyomi.data.sync.SyncDataJob
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.source.rethrowIfFatal
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderCandidateGateway
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderCandidateRequest
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderCandidateRow
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderChapterDiscovery
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderCommandResult
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderContextActions
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderCoordinator
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderEntryRequest
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderLoadState
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderMangaCandidate
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderNotice
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderOpaqueToken
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderPhase
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderPresentation
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderPresentationPolicy
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderRecoveryReason
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderResolvingPurpose
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderResultPresentation
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderRoute
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderRouteResolver
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderRouteRole
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderRouteSwitcher
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderSession
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderSessionStore
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderStateCodec
import eu.kanade.tachiyomi.ui.reader.bridge.DefaultAlternateSourceReaderCandidateReads
import eu.kanade.tachiyomi.ui.reader.chapter.ReaderChapterItem
import eu.kanade.tachiyomi.ui.reader.loader.ArchiveReaderDegradation
import eu.kanade.tachiyomi.ui.reader.loader.ChapterLoader
import eu.kanade.tachiyomi.ui.reader.loader.DownloadPageLoader
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.schedule.ReaderSchedule
import eu.kanade.tachiyomi.ui.reader.schedule.ReaderScheduleEntitlement
import eu.kanade.tachiyomi.ui.reader.schedule.ReaderScheduleMode
import eu.kanade.tachiyomi.ui.reader.schedule.ReaderSchedulePersistence
import eu.kanade.tachiyomi.ui.reader.schedule.ReaderScheduleResolver
import eu.kanade.tachiyomi.ui.reader.schedule.ReaderScheduleResult
import eu.kanade.tachiyomi.ui.reader.schedule.ReaderScheduleStore
import eu.kanade.tachiyomi.ui.reader.schedule.ReaderScheduleWindow
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.timer.ReaderTimerClock
import eu.kanade.tachiyomi.ui.reader.timer.ReaderTimerCoordinator
import eu.kanade.tachiyomi.ui.reader.timer.ReaderTimerGracePolicy
import eu.kanade.tachiyomi.ui.reader.timer.ReaderTimerPhase
import eu.kanade.tachiyomi.ui.reader.timer.ReaderTimerSession
import eu.kanade.tachiyomi.ui.reader.timer.ReaderTimerSessionStore
import eu.kanade.tachiyomi.ui.reader.timer.ReaderTimerStateCodec
import eu.kanade.tachiyomi.ui.reader.timer.ReaderTimerWarningPolicy
import eu.kanade.tachiyomi.ui.reader.timer.SystemReaderTimerClock
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.R2LPagerViewer
import eu.kanade.tachiyomi.util.chapter.filterDownloaded
import eu.kanade.tachiyomi.util.chapter.removeDuplicates
import eu.kanade.tachiyomi.util.editCover
import eu.kanade.tachiyomi.util.lang.byteSize
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.DiskUtil.MAX_FILE_NAME_BYTES
import eu.kanade.tachiyomi.util.storage.cacheImageDir
import exh.metadata.metadata.RaisedSearchMetadata
import exh.recs.bridge.AlternateSourceBridgeController
import exh.recs.matching.ConfirmedGroupLocalTrackingPropagator
import exh.recs.matching.ConfirmedMangaGroupTargets
import exh.recs.matching.CrossSourceIdentityAuthorizationResolver
import exh.recs.matching.CrossSourceIdentityDecisionController
import exh.recs.matching.SameMangaPreselectionMode
import exh.recs.matching.SameMangaPreselectionPolicy
import exh.source.MERGED_SOURCE_ID
import exh.source.getMainSource
import exh.source.isEhBasedManga
import exh.util.EvaluationModeFormatter
import exh.util.defaultReaderType
import exh.util.mangaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.toggle
import tachiyomi.core.common.storage.UniFileTempFileManager
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.decoder.ImageDecoder
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.GetMergedChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.service.getChapterSort
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.history.interactor.UpsertHistory
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.manga.interactor.GetMergedMangaById
import tachiyomi.domain.manga.interactor.GetMergedReferencesById
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.taste.interactor.GetAlternateSourceBridge
import tachiyomi.domain.taste.interactor.GetCrossSourceIdentityDecisions
import tachiyomi.domain.taste.interactor.SetMangaTasteBatch
import tachiyomi.domain.taste.model.AlternateSourceBridgeKey
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.tracker.repository.LocalTrackerRepository
import tachiyomi.i18n.MR
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.util.Date
import java.util.UUID

/**
 * Presenter used by the activity to perform background operations.
 */
private const val KEY_SCHEDULE_ENTITLEMENT = "reader_schedule_entitlement"

class ReaderViewModel @JvmOverloads constructor(
    private val savedState: SavedStateHandle,
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val downloadProvider: DownloadProvider = Injekt.get(),
    private val tempFileManager: UniFileTempFileManager = Injekt.get(),
    private val imageSaver: ImageSaver = Injekt.get(),
    val readerPreferences: ReaderPreferences = Injekt.get(),
    private val basePreferences: BasePreferences = Injekt.get(),
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
    private val trackPreferences: TrackPreferences = Injekt.get(),
    private val trackChapter: TrackChapter = Injekt.get(),
    private val recordLocalTrackedChapterProgress: RecordLocalTrackedChapterProgress = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val getChapter: GetChapter = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val getNextChapters: GetNextChapters = Injekt.get(),
    private val upsertHistory: UpsertHistory = Injekt.get(),
    private val updateChapter: UpdateChapter = Injekt.get(),
    private val setMangaViewerFlags: SetMangaViewerFlags = Injekt.get(),
    private val getIncognitoState: GetIncognitoState = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    // SY -->
    private val syncPreferences: SyncPreferences = Injekt.get(),
    private val uiPreferences: UiPreferences = Injekt.get(),
    private val getFlatMetadataById: GetFlatMetadataById = Injekt.get(),
    private val getMergedMangaById: GetMergedMangaById = Injekt.get(),
    private val getMergedReferencesById: GetMergedReferencesById = Injekt.get(),
    private val getMergedChaptersByMangaId: GetMergedChaptersByMangaId = Injekt.get(),
    // SY <--
    // KMK v0.8.4: test-only clock override for the reading timer; production always uses SystemReaderTimerClock
    private val readerTimerClockOverride: ReaderTimerClock? = null,
    // KMK v0.8.8: chapter-completion rating prompt — reuses the same exclusive-rating batch owner
    // and confirmed-group lookup as the detail and alternate-version rating surfaces.
    private val setMangaTasteBatch: SetMangaTasteBatch = Injekt.get(),
    private val getCrossSourceMangaLinks: tachiyomi.domain.taste.interactor.GetCrossSourceMangaLinks = Injekt.get(),
    private val crossSourceIdentityAuthorizationResolver: exh.recs.matching.CrossSourceIdentityAuthorizationResolver =
        exh.recs.matching.CrossSourceIdentityAuthorizationResolver(),
    private val sourcePreferencesForRatingPrompt: eu.kanade.domain.source.service.SourcePreferences = Injekt.get(),
    // KMK Confirmed Blocker Remediation Phase 2 2026-07-29: same build-before-write/commit-after-
    // success Evaluation Mode journal contract MangaScreenModel.setMangaTaste/clearMangaTaste use
    // (see exh.util.EvaluationModeJournalRecorder) -- the reader's own rating/Not-Interested prompt
    // previously bypassed the journal entirely, so its writes never appeared in Action History.
    private val getMangaTasteForRatingPrompt: tachiyomi.domain.taste.interactor.GetMangaTaste = Injekt.get(),
    private val confirmedMangaGroupTargets: ConfirmedMangaGroupTargets = Injekt.get(),
    private val localTrackerRepository: LocalTrackerRepository = Injekt.get(),
    private val confirmedGroupLocalTrackingPropagator: ConfirmedGroupLocalTrackingPropagator =
        ConfirmedGroupLocalTrackingPropagator(localTrackerRepository),
) : ViewModel() {

    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()

    private val eventChannel = Channel<Event>()
    val eventFlow = eventChannel.receiveAsFlow()

    @Volatile
    private var activeRoute: ReaderRouteState? = null

    private val routeOwner = SerializedReaderRouteOwner(
        scope = viewModelScope,
        buildDispatcher = Dispatchers.IO,
        commitDispatcher = Dispatchers.Main.immediate,
        releaseCandidate = ReaderRouteState::release,
    )

    private val alternateSourceReaderSessionStore = AlternateSourceReaderSessionStore(
        get = { key -> savedState.get<Any>(key) },
        set = { key, value -> savedState[key] = value },
        remove = { key -> savedState.remove<Any>(key) },
    )
    private val alternateSourceReaderCoordinator = AlternateSourceReaderCoordinator(
        getBridge = Injekt.get<GetAlternateSourceBridge>(),
        routeResolver = AlternateSourceReaderRouteResolver(getManga, getChapter),
        bridgeController = AlternateSourceBridgeController(),
        identityResolver = CrossSourceIdentityAuthorizationResolver(),
        identityController = CrossSourceIdentityDecisionController(),
        sessionStore = alternateSourceReaderSessionStore,
        routeSwitcher = AlternateSourceReaderRouteSwitcher(::switchReaderRoute),
    )
    val alternateSourceReaderState = alternateSourceReaderCoordinator.state

    private val alternateSourceReaderCandidateGateway = AlternateSourceReaderCandidateGateway(
        DefaultAlternateSourceReaderCandidateReads(
            getManga = getManga,
            getMangaByUrlAndSourceId = Injekt.get<GetMangaByUrlAndSourceId>(),
            getChapter = getChapter,
            getBridge = Injekt.get<GetAlternateSourceBridge>(),
            getLinks = getCrossSourceMangaLinks,
            getIdentityDecisions = Injekt.get<GetCrossSourceIdentityDecisions>(),
            identityResolver = crossSourceIdentityAuthorizationResolver,
            sourceManager = sourceManager,
            sourcePreferences = sourcePreferencesForRatingPrompt,
            networkToLocalManga = Injekt.get<NetworkToLocalManga>(),
            syncChaptersWithSource = Injekt.get<SyncChaptersWithSource>(),
            dispatcher = Dispatchers.IO,
        ),
    )
    private val mutableAlternateSourcePresentation = MutableStateFlow<AlternateSourceReaderPresentation>(
        AlternateSourceReaderPresentation.Hidden,
    )
    val alternateSourcePresentation: StateFlow<AlternateSourceReaderPresentation> =
        mutableAlternateSourcePresentation.asStateFlow()
    private val mutableAlternateSourceContextActions = MutableStateFlow(AlternateSourceReaderContextActions())
    val alternateSourceContextActions: StateFlow<AlternateSourceReaderContextActions> =
        mutableAlternateSourceContextActions.asStateFlow()
    private var alternateSourceCandidateJob: Job? = null
    private var alternateSourceCommandJob: Job? = null
    private var alternateSourceCandidateGeneration = 0L
    private var alternateSourceCommandGeneration = 0L
    private var lastAlternateSourceCandidateRequest: AlternateSourceReaderCandidateRequest? = null
    private val alternateSourceMangaTokens = mutableMapOf<AlternateSourceReaderOpaqueToken, AlternateSourceMangaSelection>()
    private val alternateSourceChapterTokens = mutableMapOf<AlternateSourceReaderOpaqueToken, AlternateSourceChapterSelection>()
    private var retainedAlternateSourceMangaSelection: AlternateSourceMangaSelection? = null
    private var retainedAlternateSourceSelection: AlternateSourceChapterSelection? = null
    private var selectedAlternateSourceToken: AlternateSourceReaderOpaqueToken? = null

    private data class AlternateSourceMangaSelection(
        val request: AlternateSourceReaderCandidateRequest,
        val candidate: AlternateSourceReaderMangaCandidate,
    )

    private data class AlternateSourceChapterSelection(
        val request: AlternateSourceReaderCandidateRequest,
        val candidate: AlternateSourceReaderMangaCandidate,
        val chapterUrl: String,
    )

    /**
     * The manga loaded in the reader. It can be null when instantiated for a short time.
     */
    val manga: Manga?
        get() = state.value.manga

    val currentChapter: Chapter?
        get() = state.value.currentChapter?.chapter?.toDomainChapter()

    /**
     * The chapter id of the currently loaded chapter. Used to restore from process kill.
     */
    private var chapterId = savedState.get<Long>("chapter_id") ?: -1L
        set(value) {
            savedState["chapter_id"] = value
            field = value
        }

    /**
     * The visible page index of the currently loaded chapter. Used to restore from process kill.
     */
    private var chapterPageIndex = savedState.get<Int>("page_index") ?: -1
        set(value) {
            savedState["page_index"] = value
            field = value
        }

    private var timerSessionKey: String? = null

    // KMK v0.8.4 -->
    /**
     * Active-reading timer. Lifecycle-bound to this ViewModel (survives rotation the same way
     * [chapterId]/[chapterPageIndex] do), mirrored to a manga/chapter session store for normal
     * reader reopen, and persisted as individual primitive [SavedStateHandle] entries via
     * [ReaderTimerStateCodec] — never as a single Parcelable/Serializable object.
     * Counts only while [ReaderTimerSession.isActivelyCounting] AND the reader is foregrounded; see
     * `onReaderForeground`/`onReaderBackground`, called from ReaderActivity's lifecycle callbacks.
     */
    private val timerCoordinator = ReaderTimerCoordinator(
        scope = viewModelScope,
        clock = readerTimerClockOverride ?: SystemReaderTimerClock,
        initialSession = ReaderTimerStateCodec.decode(
            phase = savedState.get<String>(ReaderTimerStateCodec.KEY_PHASE),
            totalMs = savedState.get<Long>(ReaderTimerStateCodec.KEY_TOTAL_MS),
            elapsedMs = savedState.get<Long>(ReaderTimerStateCodec.KEY_ELAPSED_MS),
            warningMinutesCsv = savedState.get<String>(ReaderTimerStateCodec.KEY_WARNING_MINUTES),
            finishChapter = savedState.get<Boolean>(ReaderTimerStateCodec.KEY_FINISH_CHAPTER),
            allowExtra = savedState.get<Boolean>(ReaderTimerStateCodec.KEY_ALLOW_EXTRA),
            firedWarningsCsv = savedState.get<String>(ReaderTimerStateCodec.KEY_FIRED_WARNINGS),
            extraUsed = savedState.get<Boolean>(ReaderTimerStateCodec.KEY_EXTRA_USED),
            pausedFrom = savedState.get<String>(ReaderTimerStateCodec.KEY_PAUSED_FROM),
            pauseReason = savedState.get<String>(ReaderTimerStateCodec.KEY_PAUSE_REASON),
        ),
        onPersist = { session ->
            val encoded = ReaderTimerStateCodec.encode(session)
            savedState[ReaderTimerStateCodec.KEY_PHASE] = encoded.phase
            savedState[ReaderTimerStateCodec.KEY_TOTAL_MS] = encoded.totalMs
            savedState[ReaderTimerStateCodec.KEY_ELAPSED_MS] = encoded.elapsedMs
            savedState[ReaderTimerStateCodec.KEY_WARNING_MINUTES] = encoded.warningMinutesCsv
            savedState[ReaderTimerStateCodec.KEY_FINISH_CHAPTER] = encoded.finishChapter
            savedState[ReaderTimerStateCodec.KEY_ALLOW_EXTRA] = encoded.allowExtra
            savedState[ReaderTimerStateCodec.KEY_FIRED_WARNINGS] = encoded.firedWarningsCsv
            savedState[ReaderTimerStateCodec.KEY_EXTRA_USED] = encoded.extraUsed
            savedState[ReaderTimerStateCodec.KEY_PAUSED_FROM] = encoded.pausedFrom
            savedState[ReaderTimerStateCodec.KEY_PAUSE_REASON] = encoded.pauseReason
            timerSessionKey?.let { ReaderTimerSessionStore.put(it, session) }
        },
    )
    val timerState: StateFlow<ReaderTimerSession> = timerCoordinator.state

    // KMK v0.8.4: set false immediately before a previous-chapter or manual dialog chapter change,
    // read (and reset to the natural-progression default) by the chapter-identity subscription
    // above. loadNextChapter() never sets this — an automatic forward transition is always natural.
    @Volatile
    private var pendingChapterChangeIsNatural = true

    fun startTimer(durationMs: Long, warningPolicy: ReaderTimerWarningPolicy, gracePolicy: ReaderTimerGracePolicy) {
        timerCoordinator.start(durationMs, warningPolicy, gracePolicy)
    }
    fun pauseTimer() = timerCoordinator.pause()
    fun resumeTimer() = timerCoordinator.resume()
    fun resetTimer() = timerCoordinator.reset()
    fun stopTimer() = timerCoordinator.stop()

    private fun setTimerSessionKey(mangaId: Long, chapterId: Long) {
        val key = "$mangaId:$chapterId"
        if (timerSessionKey == key) return
        timerSessionKey = key
        ReaderTimerSessionStore.get(key)?.let {
            timerCoordinator.restore(it)
            // The activity may have resumed before asynchronous reader initialization completed.
            // Replaying foreground here preserves the documented auto-resume behavior.
            timerCoordinator.onReaderForeground()
        }
    }

    /** Called from ReaderActivity.onResume. Idempotent. */
    fun onReaderForeground() {
        timerCoordinator.onReaderForeground()
        scheduleGraceCoordinator.onReaderForeground() // KMK v0.8.5
        state.value.viewerChapters
            ?.let { listOfNotNull(it.prevChapter, it.currChapter, it.nextChapter) }
            ?.forEach { it.pageLoader?.onReaderForeground() }
    }

    /** Called from ReaderActivity.onPause/onStop. Idempotent. */
    fun onReaderBackground() {
        timerCoordinator.onReaderBackground()
        scheduleGraceCoordinator.onReaderBackground() // KMK v0.8.5
        state.value.viewerChapters
            ?.let { listOfNotNull(it.prevChapter, it.currChapter, it.nextChapter) }
            ?.forEach { it.pageLoader?.onReaderBackground() }
    }
    // KMK <--

    // KMK v0.8.5 -->
    /**
     * Optional reading schedule enforcement. Deliberately a *second, independent* instance of the
     * same [ReaderTimerCoordinator]/[ReaderTimerReducer] used by the manual timer above — not a
     * schedule branch added to the reducer itself. When the schedule becomes RESTRICTED, this
     * coordinator is started with a zero-length duration and `finishCurrentChapter = true`; its
     * very first tick immediately (sub-second) drives it into CHAPTER_GRACE using the reducer's
     * already-tested, unmodified grace logic — the current chapter is never interrupted, and the
     * session ends non-destructively at the next chapter boundary. Kept fully separate from the
     * manual timer's session/state so a schedule restriction can never stomp a user-started timer,
     * and vice versa. The coordinator is restored from the same primitive SavedStateHandle contract
     * as the manual timer, so an interrupted grace period is not silently discarded on recreation.
     */
    private val scheduleGraceCoordinator = ReaderTimerCoordinator(
        scope = viewModelScope,
        clock = readerTimerClockOverride ?: SystemReaderTimerClock,
        initialSession = restoreScheduleGraceState(),
        onPersist = ::persistScheduleGraceState,
    )
    val scheduleGraceState: StateFlow<ReaderTimerSession> = scheduleGraceCoordinator.state

    // KMK v0.8.7-fix1 -->
    /**
     * This reader session's [ReaderScheduleEntitlement]. Owned directly by this ViewModel instance
     * (not the coordinator or a persisted preference) so it survives rotation/recreation exactly the
     * way this ViewModel itself does, but is never reused across a genuinely new reader session (a
     * new manga, or the same manga reopened after fully leaving the reader, always gets a fresh
     * `ReaderViewModel` and therefore starts back at [ReaderScheduleEntitlement.NotStarted]).
     *
     * Replaces the old, buggy `restricted && coordinator.state.value.phase == IDLE` check — see
     * [ReaderScheduleEntitlement]'s class doc for the confirmed defect this fixes.
     */
    @Volatile
    private var scheduleEntitlement: ReaderScheduleEntitlement = savedState
        .get<String>(KEY_SCHEDULE_ENTITLEMENT)
        ?.let { runCatching { ReaderScheduleEntitlement.valueOf(it) }.getOrNull() }
        ?: ReaderScheduleEntitlement.NotStarted

    private fun scheduleGraceKey(timerKey: String): String =
        timerKey.replace("reader_timer_", "reader_schedule_grace_")

    private fun restoreScheduleGraceState(): ReaderTimerSession = ReaderTimerStateCodec.decode(
        phase = savedState.get<String>(scheduleGraceKey(ReaderTimerStateCodec.KEY_PHASE)),
        totalMs = savedState.get<Long>(scheduleGraceKey(ReaderTimerStateCodec.KEY_TOTAL_MS)),
        elapsedMs = savedState.get<Long>(scheduleGraceKey(ReaderTimerStateCodec.KEY_ELAPSED_MS)),
        warningMinutesCsv = savedState.get<String>(scheduleGraceKey(ReaderTimerStateCodec.KEY_WARNING_MINUTES)),
        finishChapter = savedState.get<Boolean>(scheduleGraceKey(ReaderTimerStateCodec.KEY_FINISH_CHAPTER)),
        allowExtra = savedState.get<Boolean>(scheduleGraceKey(ReaderTimerStateCodec.KEY_ALLOW_EXTRA)),
        firedWarningsCsv = savedState.get<String>(scheduleGraceKey(ReaderTimerStateCodec.KEY_FIRED_WARNINGS)),
        extraUsed = savedState.get<Boolean>(scheduleGraceKey(ReaderTimerStateCodec.KEY_EXTRA_USED)),
        pausedFrom = savedState.get<String>(scheduleGraceKey(ReaderTimerStateCodec.KEY_PAUSED_FROM)),
        pauseReason = savedState.get<String>(scheduleGraceKey(ReaderTimerStateCodec.KEY_PAUSE_REASON)),
    )

    private fun persistScheduleGraceState(session: ReaderTimerSession) {
        val encoded = ReaderTimerStateCodec.encode(session)
        savedState[scheduleGraceKey(ReaderTimerStateCodec.KEY_PHASE)] = encoded.phase
        savedState[scheduleGraceKey(ReaderTimerStateCodec.KEY_TOTAL_MS)] = encoded.totalMs
        savedState[scheduleGraceKey(ReaderTimerStateCodec.KEY_ELAPSED_MS)] = encoded.elapsedMs
        savedState[scheduleGraceKey(ReaderTimerStateCodec.KEY_WARNING_MINUTES)] = encoded.warningMinutesCsv
        savedState[scheduleGraceKey(ReaderTimerStateCodec.KEY_FINISH_CHAPTER)] = encoded.finishChapter
        savedState[scheduleGraceKey(ReaderTimerStateCodec.KEY_ALLOW_EXTRA)] = encoded.allowExtra
        savedState[scheduleGraceKey(ReaderTimerStateCodec.KEY_FIRED_WARNINGS)] = encoded.firedWarningsCsv
        savedState[scheduleGraceKey(ReaderTimerStateCodec.KEY_EXTRA_USED)] = encoded.extraUsed
        savedState[scheduleGraceKey(ReaderTimerStateCodec.KEY_PAUSED_FROM)] = encoded.pausedFrom
        savedState[scheduleGraceKey(ReaderTimerStateCodec.KEY_PAUSE_REASON)] = encoded.pauseReason
    }

    private val mutableIsReadingBlockedBySchedule = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** True when this reader session must not display readable chapter content — see [ReaderScheduleEntitlement.blocksReading]. */
    val isReadingBlockedBySchedule: StateFlow<Boolean> = mutableIsReadingBlockedBySchedule.asStateFlow()

    /**
     * Re-evaluates the reading schedule against the current local time. Call from
     * ReaderActivity.onCreate/onResume and whenever the schedule settings change.
     *
     * Idempotent: calling this repeatedly (rotation, background/foreground, resume) with an
     * unchanged schedule result is a no-op on [scheduleEntitlement] beyond the pure transition table
     * in [ReaderScheduleEntitlement.next] already being stable for a repeated identical input, and
     * never starts a second grace timer (the underlying [scheduleGraceCoordinator] only ever starts
     * once per grace period — see its own idempotency guarantees).
     */
    fun evaluateSchedule() {
        val schedule = ReaderSchedule(
            enabled = readerPreferences.readingScheduleEnabled().get(),
            mode = ReaderScheduleStore.parseMode(readerPreferences.readingScheduleMode().get()),
            windows = ReaderScheduleStore.parseWindows(readerPreferences.readingScheduleWindows().get()),
        )
        val result = ReaderScheduleResolver.resolve(schedule, java.time.LocalDateTime.now())
        val graceConsumed = scheduleGraceCoordinator.state.value.phase == ReaderTimerPhase.EXPIRED
        val previousEntitlement = scheduleEntitlement
        val nextEntitlement = ReaderScheduleEntitlement.next(previousEntitlement, result, graceConsumed)
        scheduleEntitlement = nextEntitlement
        savedState[KEY_SCHEDULE_ENTITLEMENT] = nextEntitlement.name

        // KMK v0.8.7-fix1: only OpenedWhileAllowed -> RESTRICTED (a session that was already active
        // and allowed) may start the chapter-grace coordinator. A session that starts at
        // OpenedWhileRestricted never starts it — it is blocked immediately with no grace at all.
        if (
            nextEntitlement == ReaderScheduleEntitlement.CurrentChapterGrace &&
            scheduleGraceCoordinator.state.value.phase == ReaderTimerPhase.IDLE
        ) {
            scheduleGraceCoordinator.start(
                0L,
                ReaderTimerWarningPolicy.NONE,
                ReaderTimerGracePolicy(finishCurrentChapter = true, allowExtraChapter = false),
            )
        } else if (nextEntitlement == ReaderScheduleEntitlement.OpenedWhileAllowed && scheduleGraceCoordinator.state.value.phase != ReaderTimerPhase.IDLE) {
            // Schedule reverted to ALLOWED/DISABLED before grace ever started for this session (only
            // reachable from OpenedWhileAllowed -> OpenedWhileAllowed, i.e. never actually entered
            // grace) — reset defensively in case a prior evaluation left a stale coordinator state.
            scheduleGraceCoordinator.reset()
        }

        mutableIsReadingBlockedBySchedule.value = nextEntitlement.blocksReading
        if (nextEntitlement.blocksReading) {
            dismissAlternateSourcePresentation()
        }
    }

    /**
     * The real enforcement gate — checked directly by every chapter-loading entry point
     * ([init], [loadAdjacent]), not just the visual overlay in `ReaderActivity`. The overlay alone
     * only *covers* already-loaded content; without this check a manual chapter-dialog selection or
     * a deep-link-triggered initial load could still load a new chapter underneath it.
     *
     * `ReaderActivity.onCreate` calls [evaluateSchedule] before [init], so [scheduleEntitlement] is
     * always resolved before this is ever consulted — never defaults to "not blocked" by omission.
     */
    private fun isChapterNavigationBlockedBySchedule(): Boolean = scheduleEntitlement.blocksReading
    // KMK <--

    // KMK v0.8.8 -->
    /**
     * Dedup guard: the chapter id the completion prompt has already been shown (or evaluated and
     * intentionally not shown) for. `updateChapterProgress` can run more than once for the same
     * chapter/page (e.g. page-status recomposition), and process-restore/rotation replays the same
     * chapter identity — this ensures the prompt is offered at most once per genuine completion,
     * never twice for the same one.
     */
    @Volatile
    private var ratingPromptEvaluatedForChapterId: Long? = null

    // KMK v0.8.10: the prompt is deferred to reader-exit (see ChapterCompletionPromptReducer) —
    // this field replaces the old "set dialog immediately" behavior. Access is confined to the
    // main thread (maybeShowChapterCompletionRatingPrompt runs its state-touching tail via
    // withUIContext, and takePendingChapterCompletionRatingPromptForExit is only ever called from
    // ReaderActivity.finish(), itself always on the main thread), so no additional synchronization
    // is needed beyond @Volatile for cross-thread visibility.
    @Volatile
    private var chapterCompletionPromptState: ChapterCompletionPromptState = ChapterCompletionPromptState.None

    private suspend fun maybeShowChapterCompletionRatingPrompt(
        route: ReaderRouteState,
        readerChapter: ReaderChapter,
        pageIndex: Int,
        hasExtraPage: Boolean,
        isErrorPage: Boolean,
    ) {
        val chapterId = readerChapter.chapter.id ?: return
        if (ratingPromptEvaluatedForChapterId == chapterId) return
        ratingPromptEvaluatedForChapterId = chapterId

        // The prompt is optional and is owned by Recommendation Settings. Read the preference at
        // evaluation time so a settings change applies to a newly completed chapter immediately.
        if (!sourcePreferencesForRatingPrompt.chapterCompletionRatingPromptEnabled().get()) return

        // KMK v0.8.7-fix1: the prompt must never itself become a schedule-bypass vector — if this
        // session is currently blocked from reading, do not offer a rating prompt either. Reuses the
        // exact same gate every chapter-navigation entry point already checks, rather than adding a
        // second, possibly-divergent check.
        if (!canOfferAlternateSourceGapAction()) return

        val isGenuine = LatestChapterCompletionPolicy.isGenuineLatestChapterCompletion(
            pageIndex = pageIndex,
            lastPageIndex = readerChapter.pages?.lastIndex,
            hasExtraPage = hasExtraPage,
            hasNextChapter = route.viewerChapters?.nextChapter != null,
            isErrorPage = isErrorPage,
            currentChapterNumber = readerChapter.chapter.chapter_number,
            nextChapterNumber = route.viewerChapters?.nextChapter?.chapter?.chapter_number,
        )
        if (!isGenuine) return

        val mangaId = route.manga.id
        // KMK v0.8.10: record the pending completion only — do NOT interrupt the reader. The
        // dialog is shown later, when the reader is actually left (see
        // takePendingChapterCompletionRatingPromptForExit / ReaderActivity.finish()).
        withUIContext {
            chapterCompletionPromptState = ChapterCompletionPromptReducer.onGenuineCompletion(
                chapterCompletionPromptState,
                mangaId,
            )
        }
    }

    /**
     * Called by [ReaderActivity.finish] when the user is actually leaving the reader. Returns the
     * manga id to show the rating prompt for, or null if there is nothing pending (the caller
     * should proceed with a normal, immediate finish). Consuming is destructive: a second call in
     * the same lifecycle always returns null, so the prompt is offered at most once per genuine
     * completion, exactly like before this change — only the *timing* moved from mid-read to exit.
     */
    fun takePendingChapterCompletionRatingPromptForExit(): Long? {
        val (mangaId, next) = ChapterCompletionPromptReducer.takeOnExit(chapterCompletionPromptState)
        chapterCompletionPromptState = next
        return mangaId
    }

    /** Shows the rating prompt dialog now. Only ever called right after [takePendingChapterCompletionRatingPromptForExit] returns non-null. */
    fun showChapterCompletionRatingPromptNow(mangaId: Long) {
        mutableState.update { it.copy(dialog = Dialog.ChapterCompletionRating(mangaId)) }
    }

    /**
     * Fences a chapter-completion prompt action against duplicate taps and stale-dismiss races.
     * Returns true and synchronously marks the dialog `isProcessing` if this call may proceed;
     * returns false if an action for the same [mangaId] is already in flight (the caller must
     * then no-op). Every action must resolve through [resolveChapterCompletionPromptAction]
     * (success or failure) so the fence never gets stuck.
     */
    private fun beginChapterCompletionPromptAction(mangaId: Long): Boolean {
        var began = false
        mutableState.update { current ->
            if (ChapterCompletionRatingActionGate.canBegin(current.dialog, mangaId)) {
                began = true
                current.copy(dialog = (current.dialog as Dialog.ChapterCompletionRating).copy(isProcessing = true))
            } else {
                current
            }
        }
        return began
    }

    /**
     * Applies [onStillPending] only if the prompt for [mangaId] is still the exact dialog this
     * action started against (not dismissed, not superseded) -- otherwise the write already
     * happened and is preserved, but the now-stale dialog transition is dropped so a dismissed
     * or already-advanced prompt can never be resurrected by a delayed callback.
     */
    private fun resolveChapterCompletionPromptAction(mangaId: Long, onStillPending: () -> Dialog?) {
        mutableState.update { current ->
            if (ChapterCompletionRatingActionGate.isStillPending(current.dialog, mangaId)) {
                current.copy(dialog = onStillPending())
            } else {
                current
            }
        }
    }

    /** Commits [rating] via the same exclusive-rating mutation MangaScreenModel/LovedMangaScreenModel already use — no parallel rating path. */
    fun rateFromChapterCompletionPrompt(mangaId: Long, rating: MangaRating) {
        if (!beginChapterCompletionPromptAction(mangaId)) return
        val currentManga = manga
        viewModelScope.launchNonCancellable {
            if (currentManga != null && currentManga.id == mangaId) {
                // KMK v0.8.21-fix3: R1 correction -- Not Interested is a real MangaRating value;
                // this prompt still only ever offers Love/Like/Dislike, but the journal/write path
                // below is the same single-store rating change every other rating uses now, no
                // second legacy-preference write to keep in sync.
                val journalActionType = when (rating) {
                    MangaRating.LOVE -> exh.util.EvaluationJournalActionType.RATE_LOVE
                    MangaRating.LIKE -> exh.util.EvaluationJournalActionType.RATE_LIKE
                    MangaRating.DISLIKE -> exh.util.EvaluationJournalActionType.RATE_DISLIKE
                    MangaRating.NOT_INTERESTED -> exh.util.EvaluationJournalActionType.NOT_INTERESTED
                }
                val targets = ratingTargets(currentManga)
                val decision = ChapterCompletionRatingPolicy.decide(
                    existingRatings = targets.map { target ->
                        getMangaTasteForRatingPrompt.await(target.source, target.url)?.rating?.let(MangaRating::fromValue)
                    },
                    selectedRating = rating,
                    followUpEnabled = sourcePreferencesForRatingPrompt.chapterCompletionRatingOtherVersionsPromptEnabled().get(),
                )
                if (decision == ChapterCompletionRatingPolicy.Decision.NO_OP_ALREADY_RATED) {
                    withUIContext { resolveChapterCompletionPromptAction(mangaId) { null } }
                    return@launchNonCancellable
                }
                val journalEntries = exh.util.EvaluationModeJournalRecorder.buildRatingChange(
                    getMangaTasteForRatingPrompt,
                    targets,
                    rating.value,
                    journalActionType,
                )
                try {
                    setMangaTasteBatch.await(targets, rating)
                    exh.util.EvaluationModeJournalRecorder.commit(journalEntries)
                    propagateConfirmedLocalTracking(targets)
                } catch (e: Throwable) {
                    rethrowIfFatal(e)
                    resolveChapterCompletionPromptAction(mangaId) { Dialog.ChapterCompletionRating(mangaId, isProcessing = false) }
                    eventChannel.send(Event.ChapterCompletionActionFailed)
                    return@launchNonCancellable
                }
                // KMK v0.8.8: reusing the same GetCrossSourceMangaLinks lookup LovedMangaScreenModel
                // already relies on for hasConfirmedGroup, not a new duplicate check.
                // KMK v0.8.17-fix1: step 2 is now always offered after a successful rating -- a
                // confirmed group only changes which honest wording ReaderActivity shows (see
                // Dialog.ChapterCompletionRatingGroupOffer). Previously, no confirmed group meant no
                // offer at all, even though the same CrossExtensionMatchScreen route can search live
                // for other versions regardless of whether a group is already confirmed.
                if (decision != ChapterCompletionRatingPolicy.Decision.APPLY_WITH_FOLLOW_UP) {
                    withUIContext { resolveChapterCompletionPromptAction(mangaId) { null } }
                    return@launchNonCancellable
                }
                val groupLink = getCrossSourceMangaLinks.awaitBySourceUrl(currentManga.source, currentManga.url)
                val hasConfirmedGroup = groupLink?.let { link ->
                    getCrossSourceMangaLinks.awaitByGroupId(link.groupId).any { member ->
                        (member.source != currentManga.source || member.url != currentManga.url) &&
                            crossSourceIdentityAuthorizationResolver.isConfirmed(
                                currentManga.source,
                                currentManga.url,
                                member.source,
                                member.url,
                            )
                    }
                } == true
                withUIContext {
                    resolveChapterCompletionPromptAction(mangaId) {
                        Dialog.ChapterCompletionRatingGroupOffer(mangaId, rating.value, hasConfirmedGroup)
                    }
                }
            } else {
                withUIContext { mutableState.update { it.copy(dialog = null) } }
            }
        }
    }

    private suspend fun ratingTargets(manga: Manga): List<Manga> = if (
        sourcePreferencesForRatingPrompt.confirmedTrackedVersionRatingPropagationEnabled().get() ||
        sourcePreferencesForRatingPrompt.confirmedTrackedVersionLocalTrackingPropagationEnabled().get()
    ) {
        confirmedMangaGroupTargets.await(manga)
    } else {
        listOf(manga)
    }

    private suspend fun propagateConfirmedLocalTracking(targets: List<Manga>) {
        if (!sourcePreferencesForRatingPrompt.confirmedTrackedVersionLocalTrackingPropagationEnabled().get()) return
        confirmedGroupLocalTrackingPropagator.propagateIfAnyTracked(targets)
    }

    // KMK v0.8.21-fix3: R1 correction -- this previously wrote ONLY to the legacy
    // seenRecommendationMangaKeys preference and never touched MangaTaste at all, a genuine
    // dangling writer the corrected architecture cannot allow (manga-detail state trusts
    // MangaTaste.rating exclusively). Now routes through the same setMangaTasteInteractor/
    // buildRatingChange path every other rating action uses, with
    // MangaRating.NOT_INTERESTED.value as the target rating.
    fun markNotInterestedFromChapterCompletionPrompt(mangaId: Long) {
        if (!beginChapterCompletionPromptAction(mangaId)) return
        val currentManga = manga
        viewModelScope.launchNonCancellable {
            if (currentManga != null && currentManga.id == mangaId) {
                try {
                    val targets = ratingTargets(currentManga)
                    val decision = ChapterCompletionRatingPolicy.decide(
                        existingRatings = targets.map { target ->
                            getMangaTasteForRatingPrompt.await(target.source, target.url)?.rating?.let(MangaRating::fromValue)
                        },
                        selectedRating = MangaRating.NOT_INTERESTED,
                        followUpEnabled = sourcePreferencesForRatingPrompt.chapterCompletionRatingOtherVersionsPromptEnabled().get(),
                    )
                    if (decision == ChapterCompletionRatingPolicy.Decision.NO_OP_ALREADY_RATED) {
                        withUIContext { resolveChapterCompletionPromptAction(mangaId) { null } }
                        return@launchNonCancellable
                    }
                    val journalEntries = exh.util.EvaluationModeJournalRecorder.buildRatingChange(
                        getMangaTasteForRatingPrompt,
                        targets,
                        MangaRating.NOT_INTERESTED.value,
                        exh.util.EvaluationJournalActionType.NOT_INTERESTED,
                    )
                    setMangaTasteBatch.await(targets, MangaRating.NOT_INTERESTED)
                    exh.util.EvaluationModeJournalRecorder.commit(journalEntries)
                    propagateConfirmedLocalTracking(targets)
                    if (decision == ChapterCompletionRatingPolicy.Decision.APPLY_WITH_FOLLOW_UP) {
                        val groupLink = getCrossSourceMangaLinks.awaitBySourceUrl(currentManga.source, currentManga.url)
                        val hasConfirmedGroup = groupLink?.let { link ->
                            getCrossSourceMangaLinks.awaitByGroupId(link.groupId).any { member ->
                                (member.source != currentManga.source || member.url != currentManga.url) &&
                                    crossSourceIdentityAuthorizationResolver.isConfirmed(
                                        currentManga.source,
                                        currentManga.url,
                                        member.source,
                                        member.url,
                                    )
                            }
                        } == true
                        withUIContext {
                            resolveChapterCompletionPromptAction(mangaId) {
                                Dialog.ChapterCompletionRatingGroupOffer(
                                    mangaId,
                                    MangaRating.NOT_INTERESTED.value,
                                    hasConfirmedGroup,
                                )
                            }
                        }
                    } else {
                        withUIContext { resolveChapterCompletionPromptAction(mangaId) { null } }
                    }
                } catch (e: Throwable) {
                    // Rethrows CancellationException and genuine fatal VM errors; only a
                    // recoverable, non-fatal failure reaches the truthful-error path below.
                    rethrowIfFatal(e)
                    resolveChapterCompletionPromptAction(mangaId) { Dialog.ChapterCompletionRating(mangaId, isProcessing = false) }
                    eventChannel.send(Event.ChapterCompletionActionFailed)
                }
            } else {
                withUIContext { mutableState.update { it.copy(dialog = null) } }
            }
        }
    }

    /** Dismisses the prompt (either step) without rating. Retains any rating already committed in step 1 — this only ever clears the dialog, never undoes a prior successful setMangaTasteInteractor.await(...) call. */
    fun dismissChapterCompletionPrompt() {
        mutableState.update { it.copy(dialog = null) }
    }
    // KMK <--

    // KMK -->
    fun handleDownloadAction(chapter: Chapter, action: ChapterDownloadAction) {
        when (action) {
            ChapterDownloadAction.START -> downloadChapter(chapter)
            ChapterDownloadAction.START_NOW -> downloadManager.startDownloadNow(chapter.id)
            ChapterDownloadAction.CANCEL -> cancelDownload(chapter.id)
            ChapterDownloadAction.DELETE -> deleteChapter(chapter)
        }
    }

    /**
     * @param chapter the chapter to download.
     */
    private fun downloadChapter(chapter: Chapter) {
        viewModelScope.launch {
            val manga = manga?.let {
                if (it.source == MERGED_SOURCE_ID) {
                    state.value.mergedManga?.get(chapter.mangaId) ?: return@launch
                } else {
                    it
                }
            } ?: return@launch
            downloadManager.downloadChapters(manga, listOf(chapter))
            downloadManager.startDownloads()
        }
    }

    private fun cancelDownload(chapterId: Long) {
        viewModelScope.launch {
            val activeDownload = downloadManager.getQueuedDownloadOrNull(chapterId) ?: return@launch
            downloadManager.cancelQueuedDownloads(listOf(activeDownload))
            // TODO: updateDownloadState(activeDownload.apply { status = Download.State.NOT_DOWNLOADED })
        }
    }

    private fun deleteChapter(chapter: Chapter) {
        viewModelScope.launchNonCancellable {
            try {
                val manga = if (manga?.source == MERGED_SOURCE_ID) {
                    state.value.mergedManga?.get(chapter.mangaId) ?: return@launchNonCancellable
                } else {
                    manga ?: return@launchNonCancellable
                }
                val source = sourceManager.get(manga.source) ?: return@launchNonCancellable
                downloadManager.deleteChapters(
                    listOf(chapter),
                    manga,
                    source,
                    ignoreCategoryExclusion = true,
                )
//                // KMK -->
//                if (source.isLocal()) {
//                    // TODO: Refresh chapters state for Local source
//                    fetchChaptersFromSource()
//                }
//                // KMK <--
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }
    // KMK <--

    /**
     * The time the chapter was started reading
     */
    private var chapterReadStartTime: Long? = null

    private val unfilteredChapterList: List<Chapter>
        get() = activeRoute?.unfilteredChapterList.orEmpty()

    /**
     * Chapter list for the active manga. It's retrieved lazily and should be accessed for the first
     * time in a background thread to avoid blocking the UI.
     */
    private val chapterList: List<ReaderChapter>
        get() = activeRoute?.chapterList.orEmpty()

    val incognitoMode: Boolean
        get() = activeRoute?.incognitoMode ?: false
    private val downloadAheadAmount = downloadPreferences.autoDownloadWhileReading().get()

    init {
        alternateSourceReaderState
            .onEach { machineState ->
                mutableAlternateSourceContextActions.value =
                    AlternateSourceReaderPresentationPolicy.contextActions(machineState)
                if (machineState.session == null && machineState.phase != eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderPhase.RESOLVING_ENTRY) {
                    clearAlternateSourceSelectionTokens()
                }
            }
            .launchIn(viewModelScope)

        // To save state
        state.map { it.viewerChapters?.currChapter }
            .distinctUntilChanged()
            .filterNotNull()
            // SY -->
            .drop(1) // allow the loader to set the first page and chapter id
            // SY <-
            .onEach { currentChapter ->
                if (chapterPageIndex >= 0) {
                    // Restore from SavedState
                    currentChapter.requestedPage = chapterPageIndex
                } else if (!currentChapter.chapter.read) {
                    currentChapter.requestedPage = currentChapter.chapter.last_page_read
                }
                chapterId = currentChapter.chapter.id!!
                setTimerSessionKey(state.value.manga?.id ?: return@onEach, chapterId)
                // KMK v0.8.4: every real chapter-identity change feeds the reading timer's
                // chapter-grace boundary detection. Only an automatic forward "next chapter"
                // transition (pendingChapterChangeIsNatural) may consume the one-extra-chapter
                // allowance — previous-chapter navigation and manual ChapterListDialog selection
                // still end/reset grace but must never grant the extra (see loadNextChapter/
                // loadPreviousChapter/loadNewChapterFromDialog, which set this flag before calling
                // loadAdjacent()).
                val isNatural = pendingChapterChangeIsNatural
                pendingChapterChangeIsNatural = true // reset to the safe default for the next change
                timerCoordinator.onChapterChanged(chapterId.toString(), isNatural)
                // KMK v0.8.5: the schedule's independent grace coordinator gets the same signal.
                scheduleGraceCoordinator.onChapterChanged(chapterId.toString(), isNatural)
            }
            .launchIn(viewModelScope)

        // SY -->
        state.mapLatest { it.ehAutoscrollFreq }
            .distinctUntilChanged()
            .drop(1)
            .onEach { text ->
                val parsed = text.toDoubleOrNull()

                if (parsed == null || parsed <= 0 || parsed > 9999) {
                    readerPreferences.autoscrollInterval().set(-1f)
                    mutableState.update { it.copy(isAutoScrollEnabled = false) }
                } else {
                    readerPreferences.autoscrollInterval().set(parsed.toFloat())
                    mutableState.update { it.copy(isAutoScrollEnabled = true) }
                }
            }
            .launchIn(viewModelScope)
        // SY <--
    }

    override fun onCleared() {
        alternateSourceCandidateGeneration++
        alternateSourceCommandGeneration++
        alternateSourceCandidateJob?.cancel()
        alternateSourceCommandJob?.cancel()
        clearAlternateSourceSelectionTokens()
        alternateSourceReaderCoordinator.dismiss()
        routeOwner.close()
        val route = activeRoute
        activeRoute = null
        route?.release()
        // KMK v0.8.7-fix1: reader close/destroy clears this session's schedule entitlement. Since a
        // new reader always gets a brand-new ReaderViewModel instance (starting at NotStarted), this
        // is mostly documentation of intent rather than something a later call can observe — but it
        // guarantees this instance can never be reused to grant a second allowance if some future
        // caller path were to hold a reference to it past onCleared.
        scheduleEntitlement = ReaderScheduleEntitlement.Closed
    }

    /**
     * Called when the user pressed the back button and is going to leave the reader. Used to
     * trigger deletion of the downloaded chapters.
     */
    fun onActivityFinish() {
        deletePendingChapters()
    }

    /**
     * Whether this presenter is initialized yet.
     */
    fun needsInit(): Boolean {
        return activeRoute == null
    }

    /**
     * Initializes this presenter with the given [mangaId] and [initialChapterId]. This method will
     * fetch the manga from the database and initialize the initial chapter.
     */
    suspend fun init(mangaId: Long, initialChapterId: Long /* SY --> */, page: Int?/* SY <-- */): Result<Boolean> {
        if (!needsInit()) return Result.success(true)
        val requestedChapterId = chapterId.takeUnless { it == -1L } ?: initialChapterId
        return when (val replacement = replaceReaderRoute(mangaId, requestedChapterId, page)) {
            is ReaderRouteReplacementResult.Applied -> {
                restoreAlternateSourceReaderSession()
                Result.success(true)
            }
            is ReaderRouteReplacementResult.Failed -> {
                if (replacement.cause is ReaderMangaUnavailableException) {
                    Result.success(false)
                } else {
                    Result.failure(replacement.cause)
                }
            }
            ReaderRouteReplacementResult.Stale,
            ReaderRouteReplacementResult.Closed,
            ReaderRouteReplacementResult.GenerationExhausted,
            -> Result.success(false)
        }
    }

    private suspend fun replaceReaderRoute(
        mangaId: Long,
        requestedChapterId: Long,
        page: Int?,
    ): ReaderRouteReplacementResult = routeOwner.replace(
        build = { generation -> buildReaderRoute(mangaId, requestedChapterId, page, generation) },
        commit = ::commitReaderRoute,
    )

    private suspend fun switchReaderRoute(route: AlternateSourceReaderRoute): Boolean =
        when (replaceReaderRoute(route.mangaId, route.chapterId, route.pageIndex)) {
            is ReaderRouteReplacementResult.Applied -> true
            is ReaderRouteReplacementResult.Failed,
            ReaderRouteReplacementResult.Stale,
            ReaderRouteReplacementResult.Closed,
            ReaderRouteReplacementResult.GenerationExhausted,
            -> false
        }

    private suspend fun buildReaderRoute(
        mangaId: Long,
        requestedChapterId: Long,
        page: Int?,
        generation: Long,
    ): ReaderRouteState {
        val manga = getManga.await(mangaId) ?: throw ReaderMangaUnavailableException()
        sourceManager.isInitialized.first { it }
        val source = sourceManager.getOrStub(manga.source)
        val metadataSource = source.getMainSource<MetadataSource<*, *>>()
        val metadata = metadataSource?.let { getFlatMetadataById.await(mangaId)?.raise(it.metaClass) }
        val mergedReferences = if (source is MergedSource) {
            getMergedReferencesById.await(manga.id)
        } else {
            emptyList()
        }
        val mergedManga = if (source is MergedSource) {
            getMergedMangaById.await(manga.id).associateBy { it.id }
        } else {
            null
        }
        val unfilteredChapters = if (manga.source == MERGED_SOURCE_ID) {
            getMergedChaptersByMangaId.await(manga.id, dedupe = false, applyFilter = false)
        } else {
            getChaptersByMangaId.await(manga.id, applyFilter = false)
        }
        val chapters = if (manga.source == MERGED_SOURCE_ID) {
            getMergedChaptersByMangaId.await(manga.id, applyFilter = true)
        } else {
            getChaptersByMangaId.await(manga.id, applyFilter = true)
        }
        val selectedIndex = ReaderInitialChapterPolicy.resolveIndex(chapters.map { it.id }, requestedChapterId)
        val selectedChapter = chapters.getOrNull(selectedIndex ?: -1)
            ?: error("No chapters available for reader")
        if (selectedChapter.id != requestedChapterId) {
            logcat(LogPriority.WARN) { "Reader launch chapter was unavailable; using the first ordered chapter" }
        }

        fun isChapterDownloaded(chapter: Chapter): Boolean {
            val chapterManga = mergedManga?.get(chapter.mangaId) ?: manga
            return downloadManager.isChapterDownloaded(
                chapterName = chapter.name,
                chapterScanlator = chapter.scanlator,
                chapterUrl = chapter.url,
                mangaTitle = chapterManga.ogTitle,
                sourceId = chapterManga.source,
            )
        }

        val chaptersForReader = if (readerPreferences.skipRead().get() || readerPreferences.skipFiltered().get()) {
            val filteredChapters = chapters.filterNot {
                when {
                    readerPreferences.skipRead().get() && it.read -> true
                    readerPreferences.skipFiltered().get() -> {
                        (manga.unreadFilterRaw == Manga.CHAPTER_SHOW_READ && !it.read) ||
                            (manga.unreadFilterRaw == Manga.CHAPTER_SHOW_UNREAD && it.read) ||
                            (
                                manga.downloadedFilterRaw == Manga.CHAPTER_SHOW_DOWNLOADED &&
                                    !isChapterDownloaded(it)
                                ) ||
                            (
                                manga.downloadedFilterRaw == Manga.CHAPTER_SHOW_NOT_DOWNLOADED &&
                                    isChapterDownloaded(it)
                                ) ||
                            (manga.bookmarkedFilterRaw == Manga.CHAPTER_SHOW_BOOKMARKED && !it.bookmark) ||
                            (manga.bookmarkedFilterRaw == Manga.CHAPTER_SHOW_NOT_BOOKMARKED && it.bookmark)
                    }
                    else -> false
                }
            }
            if (filteredChapters.any { it.id == selectedChapter.id }) {
                filteredChapters
            } else {
                filteredChapters + selectedChapter
            }
        } else {
            chapters
        }
        val readerChapters = chaptersForReader
            .sortedWith(getChapterSort(manga, sortDescending = false))
            .run {
                if (readerPreferences.skipDupe().get()) removeDuplicates(selectedChapter) else this
            }
            .run {
                if (basePreferences.downloadedOnly().get()) filterDownloaded(manga, mergedManga) else this
            }
            .map { it.toDbChapter() }
            .map(::ReaderChapter)
        val selectedReaderChapter = readerChapters.firstOrNull { it.chapter.id == selectedChapter.id }
            ?: error("Selected chapter unavailable after reader filtering")
        val archiveDegradations = mutableListOf<ArchiveReaderDegradation>()
        val route = ReaderRouteState(
            generation = generation,
            manga = manga,
            metadata = metadata,
            mergedManga = mergedManga,
            unfilteredChapterList = unfilteredChapters,
            chapterList = readerChapters,
            loader = ChapterLoader(
                context = Injekt.get<Application>(),
                downloadManager = downloadManager,
                downloadProvider = downloadProvider,
                manga = manga,
                source = source,
                sourceManager = sourceManager,
                readerPrefs = readerPreferences,
                mergedReferences = mergedReferences,
                mergedManga = mergedManga,
                onArchiveDegraded = { degradation ->
                    if (activeRoute?.generation == generation) {
                        eventChannel.trySend(Event.ArchiveReaderDegraded(degradation))
                    } else {
                        archiveDegradations += degradation
                    }
                },
            ),
            incognitoMode = getIncognitoState.await(manga.source),
            selectedChapterId = selectedChapter.id,
            dateRelativeTime = uiPreferences.relativeTime().get(),
            autoScrollFrequency = readerPreferences.autoscrollInterval().get(),
            archiveDegradations = archiveDegradations,
            restoreQueuedDownload = { downloadManager.addDownloadsToStartOfQueue(listOf(it)) },
        )
        try {
            if (!isChapterNavigationBlockedBySchedule()) {
                route.viewerChapters = prepareViewerChapters(route, selectedReaderChapter, page)
            }
            return route
        } catch (e: Throwable) {
            route.release()
            throw e
        }
    }

    private fun commitReaderRoute(candidate: ReaderRouteState, generation: Long) {
        check(candidate.generation == generation)
        check(!candidate.isReleased())
        invalidateAlternateSourceCandidateStateForRouteChange()
        val previousRoute = activeRoute
        candidate.pendingDownload = candidate.viewerChapters?.currChapter?.let(::cancelQueuedDownloads)
        activeRoute = candidate
        chapterId = candidate.selectedChapterId
        setTimerSessionKey(candidate.manga.id, candidate.selectedChapterId)
        val autoScrollFrequency = candidate.autoScrollFrequency
        mutableState.update {
            it.copy(
                manga = candidate.manga,
                viewerChapters = candidate.viewerChapters,
                bookmarked = candidate.viewerChapters?.currChapter?.chapter?.bookmark ?: false,
                meta = candidate.metadata,
                mergedManga = candidate.mergedManga,
                dateRelativeTime = candidate.dateRelativeTime,
                ehAutoscrollFreq = if (autoScrollFrequency == -1f) "" else autoScrollFrequency.toString(),
                isAutoScrollEnabled = autoScrollFrequency != -1f,
            )
        }
        candidate.archiveDegradations.forEach { eventChannel.trySend(Event.ArchiveReaderDegraded(it)) }
        previousRoute?.release()
    }

    private suspend fun prepareViewerChapters(
        route: ReaderRouteState,
        chapter: ReaderChapter,
        page: Int? = null,
    ): ViewerChapters {
        route.loader.loadChapter(chapter, page)
        val chapterPosition = route.chapterList.indexOf(chapter)
        check(chapterPosition >= 0) { "Chapter does not belong to reader route" }
        return ViewerChapters(
            chapter,
            route.chapterList.getOrNull(chapterPosition - 1),
            route.chapterList.getOrNull(chapterPosition + 1),
        ).also(ViewerChapters::ref)
    }

    private class ReaderMangaUnavailableException : Exception()

    fun openAlternateSourceChooser(
        precedingChapterId: Long,
        followingChapterId: Long?,
        includeLiveSearch: Boolean = false,
    ) {
        if (isChapterNavigationBlockedBySchedule()) return
        val request = currentAlternateSourceCandidateRequest(precedingChapterId, followingChapterId)
            ?: run {
                mutableAlternateSourcePresentation.value = AlternateSourceReaderPresentation.RecoverableFailure(
                    AlternateSourceReaderRecoveryReason.ROUTE_CHANGED,
                    canRetry = false,
                    invalidateTokens = true,
                )
                clearAlternateSourceSelectionTokens()
                return
            }
        lastAlternateSourceCandidateRequest = request
        launchAlternateSourceCandidateWork {
            clearAlternateSourceSelectionTokens(keepRequest = true)
            mutableAlternateSourcePresentation.value = AlternateSourceReaderPresentation.ChoosingSource(
                AlternateSourceReaderLoadState.Loading,
            )
            val discovery = alternateSourceReaderCandidateGateway.discover(request, includeLiveSearch)
            val rows = discovery.candidates.map { candidate ->
                val token = newAlternateSourceToken()
                alternateSourceMangaTokens[token] = AlternateSourceMangaSelection(request, candidate)
                candidate.toPresentationRow(token)
            }
            val content = when {
                rows.isNotEmpty() -> AlternateSourceReaderLoadState.Content(
                    items = rows,
                    hasPartialFailure = discovery.failedSourceCount > 0,
                )
                discovery.failedSourceCount > 0 -> AlternateSourceReaderLoadState.Failed(
                    AlternateSourceReaderRecoveryReason.FAILED,
                )
                else -> AlternateSourceReaderLoadState.Empty
            }
            val originManga = getManga.await(request.primaryRoute.mangaId)
            val preselectionMode = SameMangaPreselectionMode.resolve(
                sourcePreferencesForRatingPrompt.sameMangaMatchPreselectionMode().get(),
                sourcePreferencesForRatingPrompt.sameMangaMatchPreselectResults().get(),
            )
            selectedAlternateSourceToken = discovery.candidates
                .firstOrNull { candidate ->
                    originManga?.let { origin ->
                        SameMangaPreselectionPolicy.shouldSelect(
                            preselectionMode,
                            origin,
                            candidate.manga,
                        )
                    } == true
                }
                ?.let { candidate ->
                    alternateSourceMangaTokens.entries.firstOrNull { it.value.candidate == candidate }?.key
                }
            mutableAlternateSourcePresentation.value = AlternateSourceReaderPresentation.ChoosingSource(
                content = content,
                selectedToken = selectedAlternateSourceToken,
            )
        }
    }

    fun openAlternateSourceChooserForCurrentChapter() {
        val route = activeRoute ?: return
        val current = route.viewerChapters?.currChapter ?: return
        openAlternateSourceChooser(
            precedingChapterId = current.chapter.id ?: return,
            followingChapterId = route.chapterList
                .getOrNull(route.chapterList.indexOf(current) + 1)
                ?.chapter
                ?.id,
            // Keep the initial chooser focused on known alternatives. The explicit
            // "Search installed sources" action owns the additional live lookup.
            includeLiveSearch = false,
        )
    }

    fun canOfferAlternateSourceChooser(): Boolean =
        !isChapterNavigationBlockedBySchedule() &&
            alternateSourceReaderState.value.phase == AlternateSourceReaderPhase.PRIMARY &&
            alternateSourceReaderState.value.session == null &&
            activeRoute?.viewerChapters?.currChapter?.chapter?.id != null

    fun searchAlternateSourceCandidates() {
        val request = lastAlternateSourceCandidateRequest ?: return
        openAlternateSourceChooser(
            request.precedingPrimaryChapterId,
            request.followingPrimaryChapterId,
            includeLiveSearch = true,
        )
    }

    fun acceptAlternateSourceSearchResult(mangaId: Long) {
        val request = lastAlternateSourceCandidateRequest ?: return
        launchAlternateSourceCandidateWork {
            val manga = getManga.await(mangaId)
            val candidate = manga?.let {
                alternateSourceReaderCandidateGateway.candidateFromSelectedManga(request, it)
            }
            val current = alternateSourcePresentation.value as? AlternateSourceReaderPresentation.ChoosingSource
            if (current == null) return@launchAlternateSourceCandidateWork
            if (candidate == null) {
                mutableAlternateSourcePresentation.value = AlternateSourceReaderPresentation.RecoverableFailure(
                    AlternateSourceReaderRecoveryReason.FAILED,
                    canRetry = true,
                    invalidateTokens = false,
                )
                return@launchAlternateSourceCandidateWork
            }
            val existing = (current.content as? AlternateSourceReaderLoadState.Content)?.items.orEmpty()
            val existingToken = alternateSourceMangaTokens.entries.firstOrNull { entry ->
                val selection = entry.value
                selection.candidate.manga.source == candidate.manga.source &&
                    selection.candidate.manga.url == candidate.manga.url &&
                    existing.any { row -> row.token == entry.key }
            }?.key
            if (existingToken != null) {
                selectedAlternateSourceToken = existingToken
                mutableAlternateSourcePresentation.value = current.copy(selectedToken = existingToken)
                return@launchAlternateSourceCandidateWork
            }
            val token = newAlternateSourceToken()
            alternateSourceMangaTokens[token] = AlternateSourceMangaSelection(request, candidate)
            val hadPartialFailure = (current.content as? AlternateSourceReaderLoadState.Content)?.hasPartialFailure == true
            mutableAlternateSourcePresentation.value = current.copy(
                content = AlternateSourceReaderLoadState.Content(
                    items = existing + candidate.toPresentationRow(token),
                    hasPartialFailure = hadPartialFailure,
                ),
                selectedToken = token,
            )
            selectedAlternateSourceToken = token
        }
    }

    fun toggleAlternateSourceCandidate(token: AlternateSourceReaderOpaqueToken) {
        if (token !in alternateSourceMangaTokens) return
        selectedAlternateSourceToken = token
        val current = alternateSourcePresentation.value as? AlternateSourceReaderPresentation.ChoosingSource ?: return
        mutableAlternateSourcePresentation.value = current.copy(selectedToken = token)
    }

    fun confirmAlternateSourceCandidate() {
        val token = selectedAlternateSourceToken ?: return
        selectAlternateSourceCandidate(token)
    }

    private fun selectAlternateSourceCandidate(token: AlternateSourceReaderOpaqueToken) {
        val selection = alternateSourceMangaTokens[token] ?: return
        retainedAlternateSourceMangaSelection = selection
        launchAlternateSourceCandidateWork {
            alternateSourceChapterTokens.clear()
            mutableAlternateSourcePresentation.value = AlternateSourceReaderPresentation.ChoosingChapter(
                sourceToken = token,
                content = AlternateSourceReaderLoadState.Loading,
            )
            val discovery = alternateSourceReaderCandidateGateway.chapters(selection.candidate)
            val content = when (discovery) {
                is AlternateSourceReaderChapterDiscovery.Available -> {
                    val rows = discovery.chapters.map { chapter ->
                        val chapterToken = newAlternateSourceToken()
                        alternateSourceChapterTokens[chapterToken] = AlternateSourceChapterSelection(
                            request = selection.request,
                            candidate = selection.candidate,
                            chapterUrl = chapter.url,
                        )
                        AlternateSourceReaderPresentationPolicy.chapterCandidateRow(
                            token = chapterToken,
                            name = chapter.name,
                            chapterNumber = chapter.chapterNumber,
                        )
                    }
                    AlternateSourceReaderLoadState.Content(rows, hasPartialFailure = false)
                }
                AlternateSourceReaderChapterDiscovery.Empty -> AlternateSourceReaderLoadState.Empty
                AlternateSourceReaderChapterDiscovery.SourceUnavailable -> AlternateSourceReaderLoadState.Failed(
                    AlternateSourceReaderRecoveryReason.ROUTE_UNAVAILABLE,
                )
                AlternateSourceReaderChapterDiscovery.Failed -> AlternateSourceReaderLoadState.Failed(
                    AlternateSourceReaderRecoveryReason.FAILED,
                )
            }
            mutableAlternateSourcePresentation.value = AlternateSourceReaderPresentation.ChoosingChapter(token, content)
        }
    }

    fun selectAlternateSourceChapter(token: AlternateSourceReaderOpaqueToken) {
        val selection = alternateSourceChapterTokens[token] ?: return
        retainedAlternateSourceSelection = selection
        val current = alternateSourcePresentation.value as? AlternateSourceReaderPresentation.ChoosingChapter ?: return
        mutableAlternateSourcePresentation.value = current.copy(selectedToken = token)
    }

    fun confirmAlternateSourceChapterSelection() {
        retainedAlternateSourceSelection?.let(::runAlternateSourceSelection)
    }

    fun confirmAlternateSourcePairSelection() {
        val selection = retainedAlternateSourceSelection ?: return
        launchAlternateSourceCommand(AlternateSourceReaderResolvingPurpose.ENTRY, explicitUserCommand = true) {
            val result = confirmAlternateSourcePair(selection.bridgeKey())
            if (result == AlternateSourceReaderCommandResult.PairConfirmed) {
                selectAlternateSourceChapter(selection)
            } else {
                result
            }
        }
    }

    fun confirmProvisionalAlternateSourceSelection() {
        val selection = retainedAlternateSourceSelection ?: return
        launchAlternateSourceCommand(AlternateSourceReaderResolvingPurpose.ENTRY, explicitUserCommand = true) {
            enterAlternateSourceBridge(
                bridgeKey = selection.bridgeKey(),
                precedingChapterId = selection.request.precedingPrimaryChapterId,
                followingChapterId = selection.request.followingPrimaryChapterId,
                confirmProvisional = true,
            )
        }
    }

    fun correctAlternateSourceMapping() {
        launchAlternateSourceCommand(AlternateSourceReaderResolvingPurpose.CORRECTION, explicitUserCommand = true) {
            correctAlternateSourceBridge()
        }
    }

    fun requestSkipAlternateSourceChapter() {
        if (!alternateSourceContextActions.value.skipChapter) return
        mutableAlternateSourcePresentation.value = AlternateSourceReaderPresentation.ConfirmSkip
    }

    fun confirmSkipAlternateSourceChapter() {
        launchAlternateSourceCommand(AlternateSourceReaderResolvingPurpose.CORRECTION, explicitUserCommand = true) {
            skipCurrentAlternateSourceChapter()
        }
    }

    fun requestReturnToPrimarySource() {
        launchAlternateSourceCommand(AlternateSourceReaderResolvingPurpose.RETURN, explicitUserCommand = true) {
            returnToPrimarySource()
        }
    }

    fun dismissAlternateSourcePresentation() {
        alternateSourceCandidateGeneration++
        alternateSourceCandidateJob?.cancel()
        alternateSourceCandidateJob = null
        mutableAlternateSourcePresentation.value = AlternateSourceReaderPresentation.Hidden
        clearAlternateSourceSelectionTokens()
    }

    fun retryAlternateSourcePresentation() {
        retainedAlternateSourceSelection?.let {
            runAlternateSourceSelection(it)
            return
        }
        retainedAlternateSourceMangaSelection?.let { selection ->
            alternateSourceMangaTokens.entries
                .firstOrNull { it.value == selection }
                ?.key
                ?.let {
                    selectAlternateSourceCandidate(it)
                    return
                }
        }
        val request = lastAlternateSourceCandidateRequest ?: return
        openAlternateSourceChooser(
            request.precedingPrimaryChapterId,
            request.followingPrimaryChapterId,
            includeLiveSearch = true,
        )
    }

    fun acknowledgeAlternateSourceUncertainAlignment() {
        viewModelScope.launch {
            alternateSourceReaderCoordinator.acknowledgeUncertainAlignmentNotice()
        }
    }

    private fun runAlternateSourceSelection(selection: AlternateSourceChapterSelection) {
        launchAlternateSourceCommand(AlternateSourceReaderResolvingPurpose.ENTRY, explicitUserCommand = true) {
            selectAlternateSourceChapter(selection)
        }
    }

    private suspend fun selectAlternateSourceChapter(
        selection: AlternateSourceChapterSelection,
    ): AlternateSourceReaderCommandResult = selectAlternateSourceChapter(
        bridgeKey = selection.bridgeKey(),
        precedingChapterId = selection.request.precedingPrimaryChapterId,
        followingChapterId = selection.request.followingPrimaryChapterId,
        alternateChapterUrl = selection.chapterUrl,
    )

    private fun launchAlternateSourceCandidateWork(block: suspend () -> Unit) {
        val generation = ++alternateSourceCandidateGeneration
        val previous = alternateSourceCandidateJob
        alternateSourceCandidateJob = viewModelScope.launch {
            previous?.cancelAndJoin()
            if (generation != alternateSourceCandidateGeneration) return@launch
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == alternateSourceCandidateGeneration) {
                    mutableAlternateSourcePresentation.value = AlternateSourceReaderPresentation.RecoverableFailure(
                        AlternateSourceReaderRecoveryReason.FAILED,
                        canRetry = true,
                        invalidateTokens = false,
                    )
                }
            }
        }
    }

    private fun launchAlternateSourceCommand(
        purpose: AlternateSourceReaderResolvingPurpose,
        explicitUserCommand: Boolean,
        block: suspend () -> AlternateSourceReaderCommandResult,
    ) {
        if (alternateSourceCommandJob?.isActive == true) return
        val generation = ++alternateSourceCommandGeneration
        alternateSourceCommandJob = viewModelScope.launch {
            if (generation != alternateSourceCommandGeneration) return@launch
            mutableAlternateSourcePresentation.value = AlternateSourceReaderPresentation.Resolving(purpose)
            val result = block()
            if (generation == alternateSourceCommandGeneration) {
                applyAlternateSourceCommandResult(result, explicitUserCommand)
            }
        }
    }

    private suspend fun applyAlternateSourceCommandResult(
        result: AlternateSourceReaderCommandResult,
        explicitUserCommand: Boolean,
    ) {
        when (val mapped = AlternateSourceReaderPresentationPolicy.result(result, explicitUserCommand)) {
            AlternateSourceReaderResultPresentation.Silent -> Unit
            is AlternateSourceReaderResultPresentation.ConfirmPair -> {
                val token = retainedAlternateSourceSelection?.let { newAlternateSourceToken() } ?: return
                mutableAlternateSourcePresentation.value = AlternateSourceReaderPresentation.ConfirmPair(token)
            }
            is AlternateSourceReaderResultPresentation.ConfirmProvisional -> {
                val token = retainedAlternateSourceSelection?.let { newAlternateSourceToken() } ?: return
                mutableAlternateSourcePresentation.value = AlternateSourceReaderPresentation.ConfirmProvisional(token)
            }
            is AlternateSourceReaderResultPresentation.Completed -> {
                mutableAlternateSourcePresentation.value = AlternateSourceReaderPresentation.Hidden
                mapped.notice?.let { eventChannel.send(Event.AlternateSourceNotice(it)) }
                if (
                    result is AlternateSourceReaderCommandResult.Entered &&
                    alternateSourceReaderCoordinator.hasUnacknowledgedUncertainAlignment()
                ) {
                    eventChannel.send(Event.AlternateSourceNotice(AlternateSourceReaderNotice.UNCERTAIN_ALIGNMENT))
                }
                if (result !is AlternateSourceReaderCommandResult.PairConfirmed) {
                    clearAlternateSourceSelectionTokens()
                }
            }
            is AlternateSourceReaderResultPresentation.Recoverable -> {
                if (mapped.invalidateTokens) clearAlternateSourceSelectionTokens(keepRequest = true)
                mutableAlternateSourcePresentation.value = AlternateSourceReaderPresentation.RecoverableFailure(
                    mapped.reason,
                    mapped.canRetry,
                    mapped.invalidateTokens,
                )
            }
        }
    }

    private fun currentAlternateSourceCandidateRequest(
        precedingChapterId: Long,
        followingChapterId: Long?,
    ): AlternateSourceReaderCandidateRequest? {
        val route = activeRoute ?: return null
        if (route.isReleased()) return null
        val preceding = route.chapterList.singleOrNull { it.chapter.id == precedingChapterId } ?: return null
        val following = followingChapterId?.let { id ->
            route.chapterList.singleOrNull { it.chapter.id == id } ?: return null
        }
        if (route.viewerChapters?.currChapter !== preceding) return null
        val precedingOwner = ownerManga(route, preceding) ?: return null
        val followingOwner = following?.let { ownerManga(route, it) } ?: precedingOwner
        if (following != null &&
            (
                precedingOwner.id != followingOwner.id ||
                    precedingOwner.source != followingOwner.source ||
                    precedingOwner.url != followingOwner.url
                )
        ) {
            return null
        }
        val primaryRoute = AlternateSourceReaderRoute(
            role = AlternateSourceReaderRouteRole.PRIMARY,
            record = tachiyomi.domain.taste.model.CrossSourceRecordKey(precedingOwner.source, precedingOwner.url),
            mangaId = precedingOwner.id,
            chapterUrl = preceding.chapter.url,
            chapterId = preceding.chapter.id ?: return null,
            pageIndex = chapterPageIndex.coerceAtLeast(0),
        )
        return AlternateSourceReaderCandidateRequest(primaryRoute, precedingChapterId, followingChapterId)
    }

    private fun AlternateSourceReaderMangaCandidate.toPresentationRow(
        token: AlternateSourceReaderOpaqueToken,
    ): AlternateSourceReaderCandidateRow = AlternateSourceReaderPresentationPolicy.mangaCandidateRow(
        token = token,
        title = manga.title,
        sourceLabel = sourceLabel,
        evaluationSourceLabel = EvaluationModeFormatter.sourceLabel(manga.source),
        evaluationModeEnabled = sourcePreferencesForRatingPrompt.evaluationMode().get(),
    )

    private fun AlternateSourceChapterSelection.bridgeKey() = AlternateSourceBridgeKey(
        primary = request.primaryRoute.record,
        alternate = tachiyomi.domain.taste.model.CrossSourceRecordKey(candidate.manga.source, candidate.manga.url),
    )

    private fun newAlternateSourceToken() = AlternateSourceReaderOpaqueToken(UUID.randomUUID().toString())

    private fun clearAlternateSourceSelectionTokens(keepRequest: Boolean = false) {
        alternateSourceMangaTokens.clear()
        alternateSourceChapterTokens.clear()
        retainedAlternateSourceMangaSelection = null
        retainedAlternateSourceSelection = null
        selectedAlternateSourceToken = null
        if (!keepRequest) lastAlternateSourceCandidateRequest = null
    }

    fun canOfferAlternateSourceGapAction(): Boolean {
        val machineState = alternateSourceReaderState.value
        return !isChapterNavigationBlockedBySchedule() &&
            machineState.phase == AlternateSourceReaderPhase.PRIMARY &&
            machineState.session == null
    }

    private fun invalidateAlternateSourceCandidateStateForRouteChange() {
        alternateSourceCandidateGeneration++
        alternateSourceCandidateJob?.cancel()
        alternateSourceCandidateJob = null
        clearAlternateSourceSelectionTokens()
        if (mutableAlternateSourcePresentation.value !is AlternateSourceReaderPresentation.Resolving) {
            mutableAlternateSourcePresentation.value = AlternateSourceReaderPresentation.Hidden
        }
    }

    suspend fun enterAlternateSourceBridge(
        bridgeKey: AlternateSourceBridgeKey,
        precedingChapterId: Long,
        followingChapterId: Long?,
        confirmProvisional: Boolean = false,
    ): AlternateSourceReaderCommandResult {
        val request = alternateSourceEntryRequest(bridgeKey, precedingChapterId, followingChapterId)
            ?: return AlternateSourceReaderCommandResult.Invalid
        return alternateSourceReaderCoordinator.enter(request, confirmProvisional)
    }

    suspend fun selectAlternateSourceChapter(
        bridgeKey: AlternateSourceBridgeKey,
        precedingChapterId: Long,
        followingChapterId: Long?,
        alternateChapterUrl: String,
    ): AlternateSourceReaderCommandResult {
        val request = alternateSourceEntryRequest(bridgeKey, precedingChapterId, followingChapterId)
            ?: return AlternateSourceReaderCommandResult.Invalid
        return alternateSourceReaderCoordinator.select(request, alternateChapterUrl)
    }

    suspend fun confirmAlternateSourcePair(
        bridgeKey: AlternateSourceBridgeKey,
    ): AlternateSourceReaderCommandResult = alternateSourceReaderCoordinator.confirmPair(bridgeKey)

    suspend fun correctAlternateSourceBridge(): AlternateSourceReaderCommandResult {
        val route = currentAlternateSourceRoute() ?: return AlternateSourceReaderCommandResult.NoSession
        return alternateSourceReaderCoordinator.correct(route)
    }

    suspend fun skipCurrentAlternateSourceChapter(): AlternateSourceReaderCommandResult {
        val route = currentAlternateSourceRoute() ?: return AlternateSourceReaderCommandResult.NoSession
        return alternateSourceReaderCoordinator.skip(route)
    }

    suspend fun returnToPrimarySource(
        preferContinuation: Boolean = true,
    ): AlternateSourceReaderCommandResult = alternateSourceReaderCoordinator.manualReturn(preferContinuation)

    fun dismissAlternateSourceSession() = alternateSourceReaderCoordinator.dismiss()

    private suspend fun restoreAlternateSourceReaderSession() {
        val session = when (val decoded = alternateSourceReaderSessionStore.read()) {
            is AlternateSourceReaderStateCodec.DecodeResult.Valid -> decoded.session
            AlternateSourceReaderStateCodec.DecodeResult.Invalid -> {
                if (alternateSourceReaderSessionStore.hasStoredState()) {
                    alternateSourceReaderCoordinator.dismiss()
                }
                return
            }
        }
        val route = activeRoute ?: run {
            alternateSourceReaderCoordinator.dismiss()
            return
        }
        val chapter = route.viewerChapters?.currChapter ?: run {
            alternateSourceReaderCoordinator.dismiss()
            return
        }
        val exactRoute = exactBridgeRoute(
            route = route,
            chapter = chapter,
            bridgeKey = session.bridgeKey,
            role = AlternateSourceReaderRouteRole.ALTERNATE,
            pageIndex = chapterPageIndex.coerceAtLeast(0),
        ) ?: run {
            alternateSourceReaderCoordinator.dismiss()
            return
        }
        alternateSourceReaderCoordinator.restore(exactRoute)
    }

    private fun alternateSourceEntryRequest(
        bridgeKey: AlternateSourceBridgeKey,
        precedingChapterId: Long,
        followingChapterId: Long?,
    ): AlternateSourceReaderEntryRequest? {
        val route = activeRoute ?: return null
        if (route.isReleased()) return null
        val preceding = route.chapterList.singleOrNull { it.chapter.id == precedingChapterId } ?: return null
        val following = followingChapterId?.let { id ->
            route.chapterList.singleOrNull { it.chapter.id == id } ?: return null
        }
        if (route.viewerChapters?.currChapter !== preceding) return null
        val primaryRoute = exactBridgeRoute(
            route = route,
            chapter = preceding,
            bridgeKey = bridgeKey,
            role = AlternateSourceReaderRouteRole.PRIMARY,
            pageIndex = chapterPageIndex.coerceAtLeast(0),
        ) ?: return null
        val followingOwner = following?.let { ownerManga(route, it) }
        if (followingOwner != null &&
            (
                followingOwner.source != bridgeKey.primary.source ||
                    followingOwner.url != bridgeKey.primary.url
                )
        ) {
            return null
        }
        return AlternateSourceReaderEntryRequest(
            bridgeKey = bridgeKey,
            primaryRoute = primaryRoute,
            precedingPrimaryChapterUrl = preceding.chapter.url,
            followingPrimaryChapterUrl = following?.chapter?.url,
        )
    }

    private fun currentAlternateSourceRoute(): AlternateSourceReaderRoute? {
        val session = alternateSourceReaderState.value.session ?: return null
        val route = activeRoute ?: return null
        val chapter = route.viewerChapters?.currChapter ?: return null
        return exactBridgeRoute(
            route = route,
            chapter = chapter,
            bridgeKey = session.bridgeKey,
            role = AlternateSourceReaderRouteRole.ALTERNATE,
            pageIndex = chapterPageIndex.coerceAtLeast(0),
        )
    }

    private fun exactBridgeRoute(
        route: ReaderRouteState,
        chapter: ReaderChapter,
        bridgeKey: AlternateSourceBridgeKey,
        role: AlternateSourceReaderRouteRole,
        pageIndex: Int,
    ): AlternateSourceReaderRoute? {
        val manga = ownerManga(route, chapter) ?: return null
        val record = when (role) {
            AlternateSourceReaderRouteRole.PRIMARY -> bridgeKey.primary
            AlternateSourceReaderRouteRole.ALTERNATE -> bridgeKey.alternate
        }
        if (manga.source != record.source || manga.url != record.url) return null
        val chapterId = chapter.chapter.id ?: return null
        return AlternateSourceReaderRoute(
            role = role,
            record = record,
            mangaId = manga.id,
            chapterUrl = chapter.chapter.url,
            chapterId = chapterId,
            pageIndex = pageIndex,
        ).takeIf { AlternateSourceReaderSession.isValidRoute(it, bridgeKey) }
    }

    private fun ownerManga(route: ReaderRouteState, chapter: ReaderChapter): Manga? =
        route.mergedManga?.get(chapter.chapter.manga_id)
            ?: route.manga.takeIf { it.id == chapter.chapter.manga_id }

    // SY -->
    fun getChapters(): List<ReaderChapterItem> {
        // KMK -->
        val manga = manga ?: return emptyList()
        val mangaList = state.value.mergedManga?.takeIf { it.isNotEmpty() } ?: mapOf(manga.id to manga)
        // KMK <--

        val currentChapter = getCurrentChapter()

        return chapterList.map {
            ReaderChapterItem(
                chapter = it.chapter.toDomainChapter()!!,
                // KMK -->
                manga = mangaList[it.chapter.manga_id] ?: manga,
                // KMK <--
                isCurrent = it.chapter.id == currentChapter?.chapter?.id,
                dateFormat = UiPreferences.dateFormat(uiPreferences.dateFormat().get()),
            )
        }
    }
    // SY <--

    /**
     * Loads the given [chapter] with this [loader] and updates the currently active chapters.
     * Callers must handle errors.
     */
    private suspend fun loadChapter(
        route: ReaderRouteState,
        chapter: ReaderChapter,
        // SY -->
        page: Int? = null,
        // SY <--
    ): Boolean {
        val newChapters = prepareViewerChapters(route, chapter, page)
        var transferred = false
        try {
            return withUIContext {
                if (activeRoute !== route || route.isReleased()) return@withUIContext false
                val previousChapters = route.viewerChapters
                route.viewerChapters = newChapters
                route.pendingDownload = cancelQueuedDownloads(newChapters.currChapter)
                mutableState.update {
                    it.copy(
                        viewerChapters = newChapters,
                        bookmarked = newChapters.currChapter.chapter.bookmark,
                    )
                }
                transferred = true
                previousChapters?.unref()
                true
            }
        } finally {
            if (!transferred) newChapters.unref()
        }
    }

    /**
     * Called when the user changed to the given [chapter] when changing pages from the viewer.
     * It's used only to set this chapter as active.
     */
    private fun loadNewChapter(route: ReaderRouteState, chapter: ReaderChapter) {
        if (chapter !in route.chapterList || route.isReleased()) return

        // KMK v0.8.7-fix1: this is the *natural* forward-paging transition (the viewer auto-advancing
        // past the last page into the next chapter) — it calls loadChapter directly, bypassing
        // loadAdjacent's gate, so it needs its own check. This is exactly the "no extra chapter"
        // case the plan calls out: the schedule-grace coordinator is started with
        // allowExtraChapter = false, so once the current chapter (the one grace was granted for)
        // finishes, natural forward progression into a *new* chapter must be blocked exactly like a
        // manual selection would be — blocksReading only becomes true once grace is actually
        // consumed, so this does not interrupt the in-progress chapter grace was granted for.
        if (isChapterNavigationBlockedBySchedule()) {
            logcat { "Blocked natural chapter transition by reading schedule" }
            return
        }

        viewModelScope.launchIO {
            logcat { "Loading reader chapter" }

            updateHistory(route)
            restartReadTimer()

            try {
                loadChapter(route, chapter)
            } catch (e: Throwable) {
                if (e is CancellationException) {
                    throw e
                }
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    fun loadNewChapterFromDialog(chapter: Chapter) {
        viewModelScope.launchIO {
            val route = activeRoute ?: return@launchIO
            val newChapter = route.chapterList.firstOrNull { it.chapter.id == chapter.id } ?: return@launchIO
            // KMK v0.8.4: manual selection must never consume the reading timer's one-extra-chapter allowance.
            pendingChapterChangeIsNatural = false
            loadAdjacent(route, newChapter)
        }
    }

    /**
     * Called when the user is going to load the prev/next chapter through the toolbar buttons.
     */
    private suspend fun loadAdjacent(chapter: ReaderChapter) {
        val route = activeRoute ?: return
        loadAdjacent(route, chapter)
    }

    private suspend fun loadAdjacent(route: ReaderRouteState, chapter: ReaderChapter) {
        if (activeRoute !== route || route.isReleased()) return

        // KMK v0.8.7-fix1: real enforcement gate, checked directly here rather than only relying on
        // the visual overlay — this is the single call site both natural forward/backward viewer
        // navigation (loadNewChapter) and manual chapter-dialog selection (loadNewChapterFromDialog)
        // funnel through, so gating it here covers both without duplicating the check. A blocked
        // session (opened while restricted, or its chapter-grace allowance already consumed) can
        // never load a *different* chapter — this does not affect an in-progress CurrentChapterGrace
        // session finishing the chapter it is already on, since blocksReading is false during grace.
        if (isChapterNavigationBlockedBySchedule()) {
            logcat { "Blocked adjacent chapter load by reading schedule" }
            return
        }

        logcat { "Loading adjacent reader chapter" }

        mutableState.update { it.copy(isLoadingAdjacentChapter = true) }
        try {
            withIOContext {
                loadChapter(route, chapter)
            }
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            logcat(LogPriority.ERROR, e)
        } finally {
            mutableState.update { it.copy(isLoadingAdjacentChapter = false) }
        }
    }

    /**
     * Called when the viewers decide it's a good time to preload a [chapter] and improve the UX so
     * that the user doesn't have to wait too long to continue reading.
     */
    suspend fun preload(chapter: ReaderChapter) {
        val route = activeRoute ?: return
        if (chapter !in route.chapterList || route.isReleased()) return
        if (chapter.state is ReaderChapter.State.Loaded || chapter.state == ReaderChapter.State.Loading) {
            return
        }

        /*
         * This code is likely deprecated since once `chapter.pageLoader` is initialized with [HttpPageLoader],
         * it would set `chapter.state` to `Loading` or `Loaded` and return early already.
         */
        if (chapter.pageLoader?.isLocal == false) {
            val manga = route.mergedManga?.get(chapter.chapter.manga_id) ?: route.manga
            val dbChapter = chapter.chapter
            val isDownloaded = downloadManager.isChapterDownloaded(
                dbChapter.name,
                dbChapter.scanlator,
                dbChapter.url,
                // SY -->
                manga.ogTitle,
                // SY <--
                manga.source,
                skipCache = true,
            )
            if (isDownloaded) {
                chapter.state = ReaderChapter.State.Wait
            }
        }

        if (chapter.state != ReaderChapter.State.Wait && chapter.state !is ReaderChapter.State.Error) {
            return
        }

        try {
            logcat { "Preloading reader chapter" }
            route.loader.loadChapter(chapter)
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            return
        }
        withUIContext {
            if (activeRoute === route && !route.isReleased()) {
                eventChannel.trySend(Event.ReloadViewerChapters)
            } else {
                chapter.pageLoader?.recycle()
                chapter.pageLoader = null
                chapter.state = ReaderChapter.State.Wait
            }
        }
    }

    fun onViewerLoaded(viewer: Viewer?) {
        mutableState.update {
            it.copy(viewer = viewer)
        }
    }

    /**
     * Called every time a page changes on the reader. Used to mark the flag of chapters being
     * read, update tracking services, enqueue downloaded chapter deletion, and updating the active chapter if this
     * [page]'s chapter is different from the currently active.
     */
    fun onPageSelected(page: ReaderPage, currentPageText: String /* SY --> */, hasExtraPage: Boolean /* SY <-- */) {
        // InsertPage doesn't change page progress
        if (page is InsertPage) {
            return
        }

        val route = activeRoute ?: return
        val selectedChapter = page.chapter
        if (selectedChapter !in route.chapterList || route.isReleased()) return
        alternateSourceReaderState.value.session?.let { session ->
            exactBridgeRoute(
                route = route,
                chapter = selectedChapter,
                bridgeKey = session.bridgeKey,
                role = AlternateSourceReaderRouteRole.ALTERNATE,
                pageIndex = page.index,
            )?.let { exactRoute ->
                viewModelScope.launch {
                    if (activeRoute === route && !route.isReleased()) {
                        alternateSourceReaderCoordinator.continueAlternate(exactRoute)
                    }
                }
            }
        }

        // SY -->
        mutableState.update { it.copy(currentPageText = currentPageText) }
        // SY <--

        val pages = selectedChapter.pages ?: return

        // Save last page read and mark as read if needed
        viewModelScope.launchNonCancellable {
            updateChapterProgress(route, selectedChapter, page/* SY --> */, hasExtraPage/* SY <-- */)
        }

        if (selectedChapter != getCurrentChapter()) {
            logcat { "Setting reader chapter as active" }
            loadNewChapter(route, selectedChapter)
        }

        val inDownloadRange = page.number.toDouble() / pages.size > 0.25
        if (inDownloadRange) {
            downloadNextChapters(route)
        }

        eventChannel.trySend(Event.PageChanged)
    }

    private fun downloadNextChapters(route: ReaderRouteState) {
        if (downloadAheadAmount == 0) return
        val manga = route.manga

        // Only download ahead if current + next chapter is already downloaded too to avoid jank
        if (route.viewerChapters?.currChapter?.pageLoader !is DownloadPageLoader) return
        val nextChapter = route.viewerChapters?.nextChapter?.chapter ?: return

        // KMK -->
        val mangas = route.mergedManga ?: mapOf(manga.id to manga)
        val nextChapterManga = mangas[nextChapter.manga_id] ?: return
        // KMK <--

        viewModelScope.launchIO {
            if (activeRoute !== route || route.isReleased()) return@launchIO
            val isNextChapterDownloaded = downloadManager.isChapterDownloaded(
                nextChapter.name,
                nextChapter.scanlator,
                nextChapter.url,
                // KMK -->
                nextChapterManga.ogTitle,
                nextChapterManga.source,
                // KMK <--
            )
            if (!isNextChapterDownloaded) return@launchIO

            val chaptersToDownload = getNextChapters.await(manga.id, nextChapter.id!!).run {
                if (readerPreferences.skipDupe().get()) {
                    removeDuplicates(nextChapter.toDomainChapter()!!)
                } else {
                    this
                }
            }.take(downloadAheadAmount)

            // KMK -->
            chaptersToDownload.groupBy { it.mangaId }.forEach { (mangaId, chapters) ->
                val chapterManga = mangas[mangaId] ?: return@forEach
                downloadManager.downloadChapters(
                    chapterManga,
                    chapters,
                )
            }
            // KMK <--
        }
    }

    /**
     * Removes [currentChapter] from download queue
     * if setting is enabled and [currentChapter] is queued for download
     */
    private fun cancelQueuedDownloads(currentChapter: ReaderChapter): Download? {
        return downloadManager.getQueuedDownloadOrNull(currentChapter.chapter.id!!)?.also {
            downloadManager.cancelQueuedDownloads(listOf(it))
        }
    }

    /**
     * Determines if deleting option is enabled and nth to last chapter actually exists.
     * If both conditions are satisfied enqueues chapter for delete.
     *
     * This deletes chapters from reading list (filtered, unduplicated if any set).
     *
     * @param currentChapter current chapter, which is going to be marked as read.
     */
    private fun deleteChapterIfNeeded(route: ReaderRouteState, currentChapter: ReaderChapter) {
        val removeAfterReadSlots = downloadPreferences.removeAfterReadSlots().get()
        if (removeAfterReadSlots == -1) return

        // Determine which chapter should be deleted and enqueue
        val currentChapterPosition = route.chapterList.indexOf(currentChapter)
        val chapterToDelete = route.chapterList.getOrNull(currentChapterPosition - removeAfterReadSlots)

        // If chapter is completely read, no need to download it
        route.pendingDownload = null

        if (chapterToDelete != null) {
            enqueueDeleteReadChapters(route, chapterToDelete)
        }
    }

    // KMK -->
    /**
     * Deletes duplicate chapters when `removeAfterReadSlots` = "Last read chapter" (0).
     *
     * Ignore the case where `removeAfterReadSlots` > 0 while `skipDupe` = true as we don't know
     * where the chapters to be deleted are in the filtered [chapterList].
     *
     * For the case where `skipDupe` = false, chapters at should be deleted normally by [deleteChapterIfNeeded]
     * based on the `removeAfterReadSlots` offset while the user is reading sequentially.
     */
    private fun deleteDupChapterIfNeeded(route: ReaderRouteState, chapterToDelete: ReaderChapter) {
        val removeAfterReadSlots = downloadPreferences.removeAfterReadSlots().get()
        if (removeAfterReadSlots != 0) return
        enqueueDeleteReadChapters(route, chapterToDelete)
    }
    // KMK <--

    /**
     * Saves the chapter progress (last read page and whether it's read)
     * if incognito mode isn't on.
     */
    private suspend fun updateChapterProgress(
        route: ReaderRouteState,
        readerChapter: ReaderChapter,
        page: Page,
        // SY -->
        hasExtraPage: Boolean,
        // SY <--
    ) {
        val pageIndex = page.index
        val syncTriggerOpt = syncPreferences.getSyncTriggerOptions()
        val isSyncEnabled = syncPreferences.isSyncEnabled()

        if (activeRoute !== route || route.isReleased()) return
        mutableState.update { it.copy(currentPage = pageIndex + 1) }
        readerChapter.requestedPage = pageIndex
        chapterPageIndex = pageIndex
        val chapterCompleted = page.status !is Page.State.Error &&
            (
                readerChapter.pages?.lastIndex == pageIndex ||
                    (hasExtraPage && readerChapter.pages?.lastIndex?.minus(1) == page.index)
                )

        if (!route.incognitoMode && page.status !is Page.State.Error) {
            readerChapter.chapter.last_page_read = pageIndex

            val completionEntries = if (chapterCompleted) {
                exh.util.ChapterUndoRecorder.buildReadEntries(
                    sourcePreferencesForRatingPrompt,
                    listOf(readerChapter.chapter.toDomainChapter()!!),
                    newRead = true,
                )
            } else {
                emptyList()
            }

            if (chapterCompleted) {
                updateChapterProgressOnComplete(route, readerChapter)

                // SY -->
                // Check if syncing is enabled for chapter read:
                if (isSyncEnabled && syncTriggerOpt.syncOnChapterRead) {
                    SyncDataJob.startNow(Injekt.get<Application>())
                }
                // SY <--

                // KMK v0.8.8: chapter-completion rating prompt — only on genuine completion of the
                // LATEST available chapter, using the exact same last-page check above, never a
                // string comparison of chapter names/numbers. See maybeShowChapterCompletionRatingPrompt.
                maybeShowChapterCompletionRatingPrompt(route, readerChapter, pageIndex, hasExtraPage, page.status is Page.State.Error)
            }

            val updated = updateChapter.await(
                ChapterUpdate(
                    id = readerChapter.chapter.id!!,
                    read = readerChapter.chapter.read,
                    lastPageRead = readerChapter.chapter.last_page_read.toLong(),
                ),
            )
            if (updated) {
                completionEntries.forEach(exh.util.ChapterUndoJournal::record)
            }

            // SY -->
            // Check if syncing is enabled for chapter open:
            if (isSyncEnabled && syncTriggerOpt.syncOnChapterOpen && readerChapter.chapter.last_page_read == 0) {
                SyncDataJob.startNow(Injekt.get<Application>())
            }
            // SY <--
        }
        if (chapterCompleted && activeRoute === route && !route.isReleased()) {
            val completedChapterUrl = readerChapter.chapter.url
            viewModelScope.launch {
                val result = alternateSourceReaderCoordinator.automaticReturn(completedChapterUrl)
                applyAlternateSourceCommandResult(result, explicitUserCommand = false)
            }
        }
    }

    private suspend fun updateChapterProgressOnComplete(route: ReaderRouteState, readerChapter: ReaderChapter) {
        readerChapter.chapter.read = true
        val localProgressManga = route.mergedManga?.get(readerChapter.chapter.manga_id) ?: route.manga
        runCatching {
            recordLocalTrackedChapterProgress.await(
                manga = localProgressManga,
                chapter = readerChapter.chapter.toDomainChapter() ?: return@runCatching,
            )
        }.onFailure { error ->
            logcat(LogPriority.WARN, error) { "Local tracker progress update failed" }
        }
        // SY -->
        if (route.manga.isEhBasedManga()) {
            val extraChapters = route.unfilteredChapterList
                .filter { it.sourceOrder > readerChapter.chapter.source_order }
            val chapterUpdates = extraChapters.map { chapter ->
                ChapterUpdate(id = chapter.id, read = true)
            }
            val undoEntries = exh.util.ChapterUndoRecorder.buildReadEntries(
                sourcePreferencesForRatingPrompt,
                extraChapters,
                newRead = true,
            )
            if (chapterUpdates.isNotEmpty() && updateChapter.awaitAll(chapterUpdates)) {
                undoEntries.forEach(exh.util.ChapterUndoJournal::record)
            }
        }
        // SY <--

        updateTrackChapterRead(route, readerChapter)
        deleteChapterIfNeeded(route, readerChapter)

        val markDuplicateAsRead = libraryPreferences.markDuplicateReadChapterAsRead().get()
            .contains(LibraryPreferences.MARK_DUPLICATE_CHAPTER_READ_EXISTING)
        if (!markDuplicateAsRead) return

        val duplicateUnreadChapters = route.unfilteredChapterList
            .mapNotNull { chapter ->
                if (
                    !chapter.read &&
                    chapter.isRecognizedNumber &&
                    chapter.chapterNumber.toFloat() == readerChapter.chapter.chapter_number
                ) {
                    // KMK -->
                    chapter.also { deleteDupChapterIfNeeded(route, ReaderChapter(it.copy(read = true))) }
                    // KMK <--
                } else {
                    null
                }
            }
        val duplicateUpdates = duplicateUnreadChapters.map { chapter ->
            ChapterUpdate(id = chapter.id, read = true)
        }
        val undoEntries = exh.util.ChapterUndoRecorder.buildReadEntries(
            sourcePreferencesForRatingPrompt,
            duplicateUnreadChapters,
            newRead = true,
        )
        if (duplicateUpdates.isNotEmpty() && updateChapter.awaitAll(duplicateUpdates)) {
            undoEntries.forEach(exh.util.ChapterUndoJournal::record)
        }
    }

    fun restartReadTimer() {
        chapterReadStartTime = Instant.now().toEpochMilli()
    }

    /**
     * Saves the chapter last read history if incognito mode isn't on.
     */
    suspend fun updateHistory() {
        activeRoute?.let { updateHistory(it) }
    }

    private suspend fun updateHistory(route: ReaderRouteState) {
        route.viewerChapters?.currChapter?.let { readerChapter ->
            if (route.incognitoMode) return@let

            val chapterId = readerChapter.chapter.id!!
            val endTime = Date()
            val sessionReadDuration = chapterReadStartTime?.let { endTime.time - it } ?: 0

            upsertHistory.await(HistoryUpdate(chapterId, endTime, sessionReadDuration))
            chapterReadStartTime = null
        }
    }

    /**
     * Called from the activity to load and set the next chapter as active.
     */
    suspend fun loadNextChapter() {
        val route = activeRoute ?: return
        val nextChapter = route.viewerChapters?.nextChapter ?: return
        loadAdjacent(route, nextChapter)
    }

    /**
     * Called from the activity to load and set the previous chapter as active.
     */
    suspend fun loadPreviousChapter() {
        val route = activeRoute ?: return
        val prevChapter = route.viewerChapters?.prevChapter ?: return
        // KMK v0.8.4: going backward must never consume the reading timer's one-extra-chapter allowance.
        pendingChapterChangeIsNatural = false
        loadAdjacent(route, prevChapter)
    }

    /**
     * Returns the currently active chapter.
     */
    private fun getCurrentChapter(): ReaderChapter? {
        return state.value.currentChapter
    }

    fun getSource() = manga?.source?.let { sourceManager.getOrStub(it) } as? HttpSource

    fun getChapterUrl(): String? {
        val sChapter = getCurrentChapter()?.chapter ?: return null
        val source = if (manga?.source == MERGED_SOURCE_ID) {
            state.value.mergedManga?.get(sChapter.manga_id)?.source?.let { sourceId ->
                sourceManager.getOrStub(sourceId) as? HttpSource
            }
        } else {
            getSource()
        } ?: return null

        return try {
            source.getChapterUrl(sChapter)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }

    /**
     * Bookmarks the currently active chapter.
     */
    fun toggleChapterBookmark() {
        val chapter = getCurrentChapter()?.chapter ?: return
        val bookmarked = !chapter.bookmark

        viewModelScope.launchNonCancellable {
            val undoEntries = exh.util.ChapterUndoRecorder.buildBookmarkEntries(
                sourcePreferencesForRatingPrompt,
                listOf(chapter.toDomainChapter()!!),
                bookmarked,
            )
            val updated = updateChapter.await(
                ChapterUpdate(
                    id = chapter.id!!,
                    bookmark = bookmarked,
                ),
            )
            if (updated) {
                chapter.bookmark = bookmarked
                undoEntries.forEach { exh.util.ChapterUndoJournal.record(it) }
                mutableState.update {
                    it.copy(
                        bookmarked = bookmarked,
                    )
                }
            }
        }
    }

    // SY -->
    fun toggleBookmark(chapterId: Long, bookmarked: Boolean) {
        val chapter = chapterList.find { it.chapter.id == chapterId }?.chapter ?: return
        viewModelScope.launchNonCancellable {
            val undoEntries = exh.util.ChapterUndoRecorder.buildBookmarkEntries(
                sourcePreferencesForRatingPrompt,
                listOf(chapter.toDomainChapter()!!),
                bookmarked,
            )
            val updated = updateChapter.await(
                ChapterUpdate(
                    id = chapterId,
                    bookmark = bookmarked,
                ),
            )
            if (updated) {
                chapter.bookmark = bookmarked
                undoEntries.forEach { exh.util.ChapterUndoJournal.record(it) }
            }
        }
    }
    // SY <--

    /**
     * Returns the viewer position used by this manga or the default one.
     */
    fun getMangaReadingMode(resolveDefault: Boolean = true): Int {
        val default = readerPreferences.defaultReadingMode().get()
        val manga = manga ?: return default
        val readingMode = ReadingMode.fromPreference(manga.readingMode.toInt())
        // SY -->
        return when {
            resolveDefault && readingMode == ReadingMode.DEFAULT && readerPreferences.useAutoWebtoon().get() -> {
                manga.defaultReaderType(manga.mangaType(sourceName = sourceManager.get(manga.source)?.name))
                    ?: default
            }
            resolveDefault && readingMode == ReadingMode.DEFAULT -> default
            else -> manga.readingMode.toInt()
        }
        // SY <--
    }

    /**
     * Updates the viewer position for the open manga.
     */
    fun setMangaReadingMode(readingMode: ReadingMode) {
        val manga = manga ?: return
        runBlocking(Dispatchers.IO) {
            setMangaViewerFlags.awaitSetReadingMode(manga.id, readingMode.flagValue.toLong())
            val currChapters = state.value.viewerChapters
            if (currChapters != null) {
                // Save current page
                val currChapter = currChapters.currChapter
                currChapter.requestedPage = currChapter.chapter.last_page_read

                mutableState.update {
                    it.copy(
                        manga = getManga.await(manga.id),
                        viewerChapters = currChapters,
                    )
                }
                eventChannel.send(Event.ReloadViewerChapters)
            }
        }
    }

    /**
     * Returns the orientation type used by this manga or the default one.
     */
    fun getMangaOrientation(resolveDefault: Boolean = true): Int {
        val default = readerPreferences.defaultOrientationType().get()
        val orientation = ReaderOrientation.fromPreference(manga?.readerOrientation?.toInt())
        return when {
            resolveDefault && orientation == ReaderOrientation.DEFAULT -> default
            else -> manga?.readerOrientation?.toInt() ?: default
        }
    }

    /**
     * Updates the orientation type for the open manga.
     */
    fun setMangaOrientationType(orientation: ReaderOrientation) {
        val manga = manga ?: return
        viewModelScope.launchIO {
            setMangaViewerFlags.awaitSetOrientation(manga.id, orientation.flagValue.toLong())
            val currChapters = state.value.viewerChapters
            if (currChapters != null) {
                // Save current page
                val currChapter = currChapters.currChapter
                currChapter.requestedPage = currChapter.chapter.last_page_read

                mutableState.update {
                    it.copy(
                        manga = getManga.await(manga.id),
                        viewerChapters = currChapters,
                    )
                }
                eventChannel.send(Event.SetOrientation(getMangaOrientation()))
                eventChannel.send(Event.ReloadViewerChapters)
            }
        }
    }

    // SY -->
    fun toggleCropBorders(): Boolean {
        val readingMode = getMangaReadingMode()
        val isPagerType = ReadingMode.isPagerType(readingMode)
        val isWebtoon = ReadingMode.WEBTOON.flagValue == readingMode
        return if (isPagerType) {
            readerPreferences.cropBorders().toggle()
        } else if (isWebtoon) {
            readerPreferences.cropBordersWebtoon().toggle()
        } else {
            readerPreferences.cropBordersContinuousVertical().toggle()
        }
    }
    // SY <--

    /**
     * Generate a filename for the given [manga] and [page]
     */
    private fun generateFilename(
        manga: Manga,
        page: ReaderPage,
    ): String {
        val chapter = page.chapter.chapter
        val filenameSuffix = " - ${page.number}"
        return DiskUtil.buildValidFilename(
            "${manga.title} - ${chapter.name}",
            MAX_FILE_NAME_BYTES - filenameSuffix.byteSize(),
        ) + filenameSuffix
    }

    fun showMenus(visible: Boolean) {
        mutableState.update { it.copy(menuVisible = visible) }
    }

    // SY -->
    fun showEhUtils(visible: Boolean) {
        mutableState.update { it.copy(ehUtilsVisible = visible) }
    }

    fun setIndexChapterToShift(index: Long?) {
        mutableState.update { it.copy(indexChapterToShift = index) }
    }

    fun setIndexPageToShift(index: Int?) {
        mutableState.update { it.copy(indexPageToShift = index) }
    }

    fun openChapterListDialog() {
        mutableState.update { it.copy(dialog = Dialog.ChapterList) }
    }

    fun setDoublePages(doublePages: Boolean) {
        mutableState.update { it.copy(doublePages = doublePages) }
    }

    fun openAutoScrollHelpDialog() {
        mutableState.update { it.copy(dialog = Dialog.AutoScrollHelp) }
    }

    fun openBoostPageHelp() {
        mutableState.update { it.copy(dialog = Dialog.BoostPageHelp) }
    }

    fun openRetryAllHelp() {
        mutableState.update { it.copy(dialog = Dialog.RetryAllHelp) }
    }

    fun toggleAutoScroll(enabled: Boolean) {
        mutableState.update { it.copy(autoScroll = enabled) }
    }

    fun setAutoScrollFrequency(frequency: String) {
        mutableState.update { it.copy(ehAutoscrollFreq = frequency) }
    }
    // SY <--

    fun showLoadingDialog() {
        mutableState.update { it.copy(dialog = Dialog.Loading) }
    }

    fun openReadingModeSelectDialog() {
        mutableState.update { it.copy(dialog = Dialog.ReadingModeSelect) }
    }

    fun openOrientationModeSelectDialog() {
        mutableState.update { it.copy(dialog = Dialog.OrientationModeSelect) }
    }

    fun openPageDialog(page: ReaderPage/* SY --> */, extraPage: ReaderPage? = null/* SY <-- */) {
        mutableState.update { it.copy(dialog = Dialog.PageActions(page, extraPage)) }
    }

    fun openSettingsDialog() {
        mutableState.update { it.copy(dialog = Dialog.Settings) }
    }

    // KMK v0.8.4
    fun openReadingTimerDialog() {
        mutableState.update { it.copy(dialog = Dialog.ReadingTimer) }
    }

    // KMK v0.8.21-fix2: keep schedule configuration in the reader so the active chapter/session
    // remains intact when the user returns from the editor.
    fun openReadingScheduleDialog() {
        mutableState.update { it.copy(dialog = Dialog.ReadingSchedule) }
    }

    // Keep the timer workflow in the same reader context when schedule editing is cancelled or
    // saved. The reader dialog is a modal stack, not a separate navigation destination.
    fun returnToReadingTimerDialog() {
        mutableState.update { current ->
            if (current.dialog is Dialog.ReadingSchedule) {
                current.copy(dialog = Dialog.ReadingTimer)
            } else {
                current
            }
        }
    }

    fun saveReadingSchedule(mode: ReaderScheduleMode, windows: List<ReaderScheduleWindow>) {
        ReaderSchedulePersistence.save(
            readerPreferences = readerPreferences,
            sourcePreferences = sourcePreferencesForRatingPrompt,
            newMode = mode,
            newWindows = windows,
        )
        evaluateSchedule()
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    fun setBrightnessOverlayValue(value: Int) {
        mutableState.update { it.copy(brightnessOverlayValue = value) }
    }

    /**
     * Saves the image of the selected page on the pictures directory and notifies the UI of the result.
     * There's also a notification to allow sharing the image somewhere else or deleting it.
     */
    fun saveImage(useExtraPage: Boolean) {
        // SY -->
        val page = if (useExtraPage) {
            (state.value.dialog as? Dialog.PageActions)?.extraPage
        } else {
            (state.value.dialog as? Dialog.PageActions)?.page
        }
        // SY <--
        if (page?.status != Page.State.Ready) return
        val manga = manga ?: return

        val context = Injekt.get<Application>()
        val notifier = SaveImageNotifier(context)
        notifier.onClear()

        val filename = generateFilename(manga, page)

        // Pictures directory.
        val relativePath = if (readerPreferences.folderPerManga().get()) {
            DiskUtil.buildValidFilename(manga.title)
        } else {
            ""
        }

        // Copy file in background.
        viewModelScope.launchNonCancellable {
            try {
                val uri = imageSaver.save(
                    image = Image.Page(
                        inputStream = page.stream!!,
                        name = filename,
                        location = Location.Pictures.create(relativePath),
                    ),
                )
                withUIContext {
                    notifier.onComplete(uri)
                    eventChannel.send(Event.SavedImage(SaveImageResult.Success(uri)))
                }
            } catch (e: Throwable) {
                rethrowIfFatal(e)
                notifier.onError(context.stringResource(MR.strings.unknown_error))
                eventChannel.send(Event.SavedImage(SaveImageResult.Error(e)))
            }
        }
    }

    // SY -->
    fun saveImages() {
        val (firstPage, secondPage) = (state.value.dialog as? Dialog.PageActions ?: return)
        val viewer = state.value.viewer as? PagerViewer ?: return
        val isLTR = (viewer !is R2LPagerViewer) xor (viewer.config.invertDoublePages)
        val bg = viewer.config.pageCanvasColor

        if (firstPage.status != Page.State.Ready) return
        if (secondPage?.status != Page.State.Ready) return

        val manga = manga ?: return

        val context = Injekt.get<Application>()
        val notifier = SaveImageNotifier(context)
        notifier.onClear()

        // Copy file in background.
        viewModelScope.launchNonCancellable {
            try {
                val uri = saveImages(
                    page1 = firstPage,
                    page2 = secondPage,
                    isLTR = isLTR,
                    bg = bg,
                    location = Location.Pictures.create(DiskUtil.buildValidFilename(manga.title)),
                    manga = manga,
                )
                eventChannel.send(Event.SavedImage(SaveImageResult.Success(uri)))
            } catch (e: Throwable) {
                rethrowIfFatal(e)
                notifier.onError(context.stringResource(MR.strings.unknown_error))
                eventChannel.send(Event.SavedImage(SaveImageResult.Error(e)))
            }
        }
    }

    private fun saveImages(
        page1: ReaderPage,
        page2: ReaderPage,
        isLTR: Boolean,
        @ColorInt bg: Int,
        location: Location,
        manga: Manga,
    ): Uri {
        val stream1 = page1.stream!!
        ImageUtil.findImageType(stream1) ?: throw Exception("Not an image")
        val stream2 = page2.stream!!
        ImageUtil.findImageType(stream2) ?: throw Exception("Not an image")
        val imageBitmap = ImageDecoder.newInstance(stream1())?.decode()!!
        val imageBitmap2 = ImageDecoder.newInstance(stream2())?.decode()!!

        val chapter = page1.chapter.chapter

        // Build destination file.
        val filenameSuffix = " - ${page1.number}-${page2.number}.jpg"
        val filename = DiskUtil.buildValidFilename(
            "${manga.title} - ${chapter.name}",
            MAX_FILE_NAME_BYTES - filenameSuffix.byteSize(),
        ) + filenameSuffix

        return imageSaver.save(
            image = Image.Page(
                inputStream = { ImageUtil.mergeBitmaps(imageBitmap, imageBitmap2, isLTR, 0, bg).inputStream() },
                name = filename,
                location = location,
            ),
        )
    }
    // SY <--

    /**
     * Shares the image of the selected page and notifies the UI with the path of the file to share.
     * The image must be first copied to the internal partition because there are many possible
     * formats it can come from, like a zipped chapter, in which case it's not possible to directly
     * get a path to the file and it has to be decompressed somewhere first. Only the last shared
     * image will be kept so it won't be taking lots of internal disk space.
     */
    fun shareImage(
        copyToClipboard: Boolean,
        // SY -->
        useExtraPage: Boolean,
        // SY <--
    ) {
        // SY -->
        val page = if (useExtraPage) {
            (state.value.dialog as? Dialog.PageActions)?.extraPage
        } else {
            (state.value.dialog as? Dialog.PageActions)?.page
        }
        // SY <--
        if (page?.status != Page.State.Ready) return
        val manga = manga ?: return

        val context = Injekt.get<Application>()
        val destDir = context.cacheImageDir

        val filename = generateFilename(manga, page)

        viewModelScope.launchNonCancellable {
            try {
                destDir.deleteRecursively()
                val uri = imageSaver.save(
                    image = Image.Page(
                        inputStream = page.stream!!,
                        name = filename,
                        location = Location.Cache,
                    ),
                )
                eventChannel.send(if (copyToClipboard) Event.CopyImage(uri) else Event.ShareImage(uri, page))
            } catch (e: Throwable) {
                rethrowIfFatal(e)
                logcat(LogPriority.ERROR, e)
                eventChannel.send(Event.ShareImageFailed)
            }
        }
    }

    // SY -->
    fun shareImages(copyToClipboard: Boolean) {
        val (firstPage, secondPage) = (state.value.dialog as? Dialog.PageActions ?: return)
        val viewer = state.value.viewer as? PagerViewer ?: return
        val isLTR = (viewer !is R2LPagerViewer) xor (viewer.config.invertDoublePages)
        val bg = viewer.config.pageCanvasColor

        if (firstPage.status != Page.State.Ready) return
        if (secondPage?.status != Page.State.Ready) return
        val manga = manga ?: return

        val context = Injekt.get<Application>()
        val destDir = context.cacheImageDir

        viewModelScope.launchNonCancellable {
            try {
                destDir.deleteRecursively()
                val uri = saveImages(
                    page1 = firstPage,
                    page2 = secondPage,
                    isLTR = isLTR,
                    bg = bg,
                    location = Location.Cache,
                    manga = manga,
                )
                eventChannel.send(if (copyToClipboard) Event.CopyImage(uri) else Event.ShareImage(uri, firstPage, secondPage))
            } catch (e: Throwable) {
                rethrowIfFatal(e)
                logcat(LogPriority.ERROR, e)
                eventChannel.send(Event.ShareImageFailed)
            }
        }
    }
    // SY <--

    /**
     * Sets the image of the selected page as cover and notifies the UI of the result.
     */
    fun setAsCover(useExtraPage: Boolean) {
        // SY -->
        val page = if (useExtraPage) {
            (state.value.dialog as? Dialog.PageActions)?.extraPage
        } else {
            (state.value.dialog as? Dialog.PageActions)?.page
        }
        // SY <--
        if (page?.status != Page.State.Ready) return
        val manga = manga ?: return
        val stream = page.stream ?: return

        viewModelScope.launchNonCancellable {
            val result = try {
                manga.editCover(Injekt.get(), stream())
                if (manga.isLocal() || manga.favorite) {
                    SetAsCoverResult.Success
                } else {
                    SetAsCoverResult.AddToLibraryFirst
                }
            } catch (_: Exception) {
                SetAsCoverResult.Error
            }
            eventChannel.send(Event.SetCoverResult(result))
        }
    }

    enum class SetAsCoverResult {
        Success,
        AddToLibraryFirst,
        Error,
    }

    sealed interface SaveImageResult {
        class Success(val uri: Uri) : SaveImageResult
        class Error(val error: Throwable) : SaveImageResult
    }

    /**
     * Starts the service that updates the last chapter read in sync services. This operation
     * will run in a background thread and errors are ignored.
     */
    private fun updateTrackChapterRead(route: ReaderRouteState, readerChapter: ReaderChapter) {
        if (route.incognitoMode) return
        if (!trackPreferences.autoUpdateTrack().get()) return

        val manga = route.manga
        val context = Injekt.get<Application>()

        viewModelScope.launchNonCancellable {
            trackChapter.await(context, manga.id, readerChapter.chapter.chapter_number.toDouble())
        }
    }

    /**
     * Enqueues this [chapter] to be deleted when [deletePendingChapters] is called. The download
     * manager handles persisting it across process deaths.
     */
    private fun enqueueDeleteReadChapters(route: ReaderRouteState, chapter: ReaderChapter) {
        if (!chapter.chapter.read) return
        val mergedManga = route.mergedManga
        // SY -->
        val manga = if (mergedManga.isNullOrEmpty()) {
            route.manga
        } else {
            mergedManga[chapter.chapter.manga_id]
        } ?: return
        // SY <--

        viewModelScope.launchNonCancellable {
            downloadManager.enqueueChaptersToDelete(listOf(chapter.chapter.toDomainChapter()!!), manga)
        }
    }

    /**
     * Deletes all the pending chapters. This operation will run in a background thread and errors
     * are ignored.
     */
    private fun deletePendingChapters() {
        viewModelScope.launchNonCancellable {
            downloadManager.deletePendingChapters()
            tempFileManager.deleteTempFiles()
        }
    }

    @Immutable
    data class State(
        val manga: Manga? = null,
        val viewerChapters: ViewerChapters? = null,
        val bookmarked: Boolean = false,
        val isLoadingAdjacentChapter: Boolean = false,
        val currentPage: Int = -1,

        /**
         * Viewer used to display the pages (pager, webtoon, ...).
         */
        val viewer: Viewer? = null,
        val dialog: Dialog? = null,
        val menuVisible: Boolean = false,
        @field:IntRange(from = -100, to = 100) val brightnessOverlayValue: Int = 0,

        // SY -->
        /** for display page number in double-page mode */
        val currentPageText: String = "",
        val meta: RaisedSearchMetadata? = null,
        val mergedManga: Map<Long, Manga>? = null,
        val ehUtilsVisible: Boolean = false,
        val lastShiftDoubleState: Boolean? = null,
        val indexPageToShift: Int? = null,
        val indexChapterToShift: Long? = null,
        val doublePages: Boolean = false,
        val dateRelativeTime: Boolean = true,
        val autoScroll: Boolean = false,
        val isAutoScrollEnabled: Boolean = false,
        val ehAutoscrollFreq: String = "",
        // SY <--
    ) {
        val currentChapter: ReaderChapter?
            get() = viewerChapters?.currChapter

        val totalPages: Int
            get() = currentChapter?.pages?.size ?: -1
    }

    sealed interface Dialog {
        data object Loading : Dialog
        data object Settings : Dialog
        data object ReadingModeSelect : Dialog
        data object OrientationModeSelect : Dialog

        // SY -->
        data object ChapterList : Dialog
        // SY <--

        data class PageActions(
            val page: ReaderPage,
            // SY -->
            val extraPage: ReaderPage? = null,
            // SY <--
        ) : Dialog

        // SY -->
        data object AutoScrollHelp : Dialog
        data object RetryAllHelp : Dialog
        data object BoostPageHelp : Dialog
        // SY <--

        // KMK v0.8.4
        data object ReadingTimer : Dialog
        data object ReadingSchedule : Dialog

        // KMK v0.8.8: chapter-completion rating prompt. mangaId only — never a screen or
        // match-mode object — matching this class's existing precedent (every other Dialog case is
        // either a primitive-only data class or a data object; none hold Parcelable-unsafe types
        // either, since Dialog is never routed through SavedStateHandle in this codebase).
        // isProcessing fences duplicate taps: true from the moment an action is dispatched until
        // its write resolves (success moves to the step-2 dialog; failure resets it to false so
        // retry is still offered on the same dialog instance).
        data class ChapterCompletionRating(val mangaId: Long, val isProcessing: Boolean = false) : Dialog

        // KMK v0.8.8: step 2 — offered after a rating was just committed. KMK v0.8.17-fix1: no
        // longer gated on a confirmed cross-source group already existing -- `hasConfirmedGroup`
        // now only picks which honest wording to show (see ReaderActivity), since
        // `CrossExtensionMatchScreen` performs its own live title search regardless of whether a
        // group is already confirmed, so there was no reason to withhold the offer entirely just
        // because no group happened to exist yet.
        data class ChapterCompletionRatingGroupOffer(val mangaId: Long, val ratingValue: Int, val hasConfirmedGroup: Boolean) : Dialog
    }

    sealed interface Event {
        data object ReloadViewerChapters : Event
        data object PageChanged : Event
        data class SetOrientation(val orientation: Int) : Event
        data class SetCoverResult(val result: SetAsCoverResult) : Event

        data class SavedImage(val result: SaveImageResult) : Event
        data class ShareImage(
            val uri: Uri,
            val page: ReaderPage,
            // SY -->
            val secondPage: ReaderPage? = null,
            // SY <--
        ) : Event
        data class CopyImage(val uri: Uri) : Event

        data object ShareImageFailed : Event

        // KMK Confirmed Blocker Remediation follow-up Phase 2: sent when
        // markNotInterestedFromChapterCompletionPrompt's preference write fails (non-fatal,
        // non-cancellation) -- the prompt stays open rather than closing as if the action succeeded.
        data object ChapterCompletionActionFailed : Event

        data class ArchiveReaderDegraded(val reason: ArchiveReaderDegradation) : Event
        data class AlternateSourceNotice(val notice: AlternateSourceReaderNotice) : Event
    }

    fun onMemoryPressure() {
        val chapters = state.value.viewerChapters ?: return
        listOfNotNull(chapters.prevChapter, chapters.currChapter, chapters.nextChapter)
            .forEach { it.pageLoader?.onMemoryPressure() }
    }
}

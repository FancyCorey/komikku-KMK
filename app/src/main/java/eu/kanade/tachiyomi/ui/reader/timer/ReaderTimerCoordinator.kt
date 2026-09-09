package eu.kanade.tachiyomi.ui.reader.timer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// KMK v0.8.4 -->
/**
 * Lifecycle-bound coordinator wrapping [ReaderTimerReducer] with a real ticking coroutine.
 *
 * Owned by `ReaderViewModel` (survives rotation/process-death the same way its other SavedState
 * fields do). The ticking job only exists while the session is actively counting
 * ([ReaderTimerSession.isActivelyCounting]) — it is started/stopped as a direct consequence of each
 * state transition, so there is never more than one active ticker and rotation can never spawn a
 * second one (the coordinator instance itself is retained across rotation, not recreated).
 */
class ReaderTimerCoordinator(
    private val scope: CoroutineScope,
    private val clock: ReaderTimerClock = SystemReaderTimerClock,
    initialSession: ReaderTimerSession = ReaderTimerSession(),
    private val onPersist: (ReaderTimerSession) -> Unit = {},
) {
    private val mutableState = MutableStateFlow(initialSession)
    val state: StateFlow<ReaderTimerSession> = mutableState.asStateFlow()

    private var tickerJob: Job? = null

    init {
        // A session restored mid-count must be frozen immediately (see ProcessRestored semantics)
        // before anything else can observe/tick it.
        if (initialSession.isActivelyCounting) {
            dispatch(ReaderTimerEvent.ProcessRestored)
        }
    }

    fun start(durationMs: Long, warningPolicy: ReaderTimerWarningPolicy, gracePolicy: ReaderTimerGracePolicy) =
        dispatch(ReaderTimerEvent.Start(durationMs, warningPolicy, gracePolicy))

    fun pause() = dispatch(ReaderTimerEvent.Pause)
    fun resume() = dispatch(ReaderTimerEvent.Resume)
    fun reset() = dispatch(ReaderTimerEvent.Reset)
    fun stop() = dispatch(ReaderTimerEvent.Stop)

    fun restore(session: ReaderTimerSession) = dispatch(ReaderTimerEvent.Restore(session))

    /** Call from ReaderActivity.onResume (idempotent — a no-op unless a background-pause is active). */
    fun onReaderForeground() = dispatch(ReaderTimerEvent.ReaderForeground)

    /** Call from ReaderActivity.onPause/onStop (idempotent — a no-op if already paused/idle/expired). */
    fun onReaderBackground() = dispatch(ReaderTimerEvent.ReaderBackground)

    /**
     * Call whenever the reader's current chapter identity changes.
     * @param isNaturalProgression true only for an automatic forward "next chapter" transition —
     * see [ReaderTimerEvent.ChapterChanged] for why this distinction matters for grace/extra-chapter handling.
     */
    fun onChapterChanged(chapterKey: String, isNaturalProgression: Boolean = true) =
        dispatch(ReaderTimerEvent.ChapterChanged(chapterKey, isNaturalProgression))

    private fun dispatch(event: ReaderTimerEvent) {
        val next = ReaderTimerReducer.reduce(mutableState.value, event, clock.nowMonotonicMs())
        if (next !== mutableState.value) {
            mutableState.value = next
            onPersist(next)
        }
        syncTicker(next)
    }

    private fun syncTicker(session: ReaderTimerSession) {
        if (session.isActivelyCounting) {
            if (tickerJob?.isActive != true) {
                tickerJob = scope.launch {
                    while (isActive) {
                        delay(TICK_INTERVAL_MS)
                        dispatch(ReaderTimerEvent.Tick(clock.nowMonotonicMs()))
                    }
                }
            }
        } else {
            tickerJob?.cancel()
            tickerJob = null
        }
    }

    companion object {
        private const val TICK_INTERVAL_MS = 1000L
    }
}
// KMK <--

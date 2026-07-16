package eu.kanade.tachiyomi.ui.reader.timer

// KMK v0.8.4 -->
/**
 * Pure data model for the active-reading timer. See [ReaderTimerReducer] for the state machine
 * and `docs/recommendations/KMK_RECS_V0_8_4_READING_TIMER_IMPLEMENTATION_PLAN.md`.
 *
 * The timer counts only monotonic elapsed time accumulated while [ReaderTimerPhase] is one of the
 * "actively counting" phases (RUNNING, CHAPTER_GRACE, EXTRA_CHAPTER_GRACE) and the reader is in the
 * foreground — never wall-clock time, so system clock/time-zone changes cannot affect the remaining
 * duration.
 */
enum class ReaderTimerPhase {
    IDLE,
    RUNNING,
    PAUSED,
    CHAPTER_GRACE,
    EXTRA_CHAPTER_GRACE,
    EXPIRED,
}

/** Why the timer is currently [ReaderTimerPhase.PAUSED] — distinguishes an explicit user pause from an automatic one. */
enum class ReaderTimerPauseReason {
    /** The user tapped Pause. Returning to the reader foreground must NOT auto-resume. */
    USER,

    /** The reader left the foreground (backgrounded, locked, activity stopped). Returning foreground auto-resumes. */
    BACKGROUND,
}

/** Which warning thresholds (minutes remaining) are enabled for this session. */
data class ReaderTimerWarningPolicy(
    val minutesBeforeExpiry: Set<Int> = emptySet(),
) {
    companion object {
        val SUPPORTED_MINUTES = setOf(15, 10, 5, 1)
        val NONE = ReaderTimerWarningPolicy(emptySet())

        /** Validates a raw/persisted set, dropping any value outside [SUPPORTED_MINUTES]. */
        fun validate(raw: Set<Int>): ReaderTimerWarningPolicy = ReaderTimerWarningPolicy(raw.intersect(SUPPORTED_MINUTES))
    }
}

/** Post-expiry behavior. */
data class ReaderTimerGracePolicy(
    val finishCurrentChapter: Boolean = true,
    val allowExtraChapter: Boolean = false,
)

/**
 * The full persisted/runtime state of one timer session. Every field is a primitive or a
 * primitive collection so it can be persisted as individual `SavedStateHandle` entries — no
 * Parcelable/Serializable object is ever stored (see [ReaderTimerSavedState]).
 */
data class ReaderTimerSession(
    val phase: ReaderTimerPhase = ReaderTimerPhase.IDLE,
    /** Configured countdown length in milliseconds. 0 while IDLE. */
    val totalDurationMs: Long = 0L,
    /** Accumulated active elapsed time, monotonic, frozen while not actively counting. */
    val elapsedActiveMs: Long = 0L,
    /** Monotonic timestamp (elapsedRealtime-style) the current actively-counting segment began; null when not counting. */
    val lastResumeMonotonicMs: Long? = null,
    val warningPolicy: ReaderTimerWarningPolicy = ReaderTimerWarningPolicy.NONE,
    val gracePolicy: ReaderTimerGracePolicy = ReaderTimerGracePolicy(),
    /** Warning thresholds already fired this session — each fires at most once. */
    val firedWarningMinutes: Set<Int> = emptySet(),
    /** True once the one allowed extra chapter has been granted. */
    val extraChapterUsed: Boolean = false,
    /** Set only while PAUSED; which phase to return to on [ReaderTimerEvent.Resume]/auto-resume. */
    val pausedFromPhase: ReaderTimerPhase? = null,
    val pauseReason: ReaderTimerPauseReason? = null,
) {
    val isActivelyCounting: Boolean
        get() = phase == ReaderTimerPhase.RUNNING || phase == ReaderTimerPhase.CHAPTER_GRACE || phase == ReaderTimerPhase.EXTRA_CHAPTER_GRACE

    /** Elapsed time as of [nowMonotonicMs], accounting for an in-progress counting segment. */
    fun elapsedAt(nowMonotonicMs: Long): Long =
        if (lastResumeMonotonicMs != null) elapsedActiveMs + (nowMonotonicMs - lastResumeMonotonicMs) else elapsedActiveMs

    /** Remaining countdown time as of [nowMonotonicMs]; never negative. Meaningless once EXPIRED. */
    fun remainingAt(nowMonotonicMs: Long): Long = (totalDurationMs - elapsedAt(nowMonotonicMs)).coerceAtLeast(0L)
}

sealed interface ReaderTimerEvent {
    data class Start(
        val durationMs: Long,
        val warningPolicy: ReaderTimerWarningPolicy,
        val gracePolicy: ReaderTimerGracePolicy,
    ) : ReaderTimerEvent
    data object Pause : ReaderTimerEvent
    data object Resume : ReaderTimerEvent
    data object Reset : ReaderTimerEvent
    data object Stop : ReaderTimerEvent
    data object ReaderForeground : ReaderTimerEvent
    data object ReaderBackground : ReaderTimerEvent
    /**
     * @param isNaturalProgression true only for an automatic forward "next chapter" transition.
     * False for previous-chapter navigation or a manual `ChapterListDialog` selection — per the
     * plan, manual selection must never consume the one-extra-chapter allowance, only reset grace.
     */
    data class ChapterChanged(val chapterKey: String, val isNaturalProgression: Boolean = true) : ReaderTimerEvent
    data class Tick(val nowMonotonicMs: Long) : ReaderTimerEvent
    /** Process was recreated; a previously-counting phase must be downgraded to a background pause since no ticks occurred while dead. */
    data object ProcessRestored : ReaderTimerEvent
    /** Persisted state failed to parse/validate; the session must reset to IDLE rather than crash. */
    data object InvalidPersistedState : ReaderTimerEvent
}
// KMK <--

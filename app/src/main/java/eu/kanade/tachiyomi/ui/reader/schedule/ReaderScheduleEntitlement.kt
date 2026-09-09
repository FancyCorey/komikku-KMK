package eu.kanade.tachiyomi.ui.reader.schedule

// KMK v0.8.7-fix1 -->
/**
 * Explicit, session-bound reading-schedule entitlement state, replacing the buggy
 * `restricted && coordinator.state.value.phase == IDLE` check that used to live inline in
 * `ReaderViewModel.evaluateSchedule()`.
 *
 * **Confirmed defect this replaces**: a brand-new `ReaderViewModel`'s schedule-grace
 * `ReaderTimerCoordinator` is *always* `IDLE` (nothing has started it yet) — so the old check could
 * not distinguish "this reader just opened while restricted" from "this reader was already open and
 * allowed, and the schedule just became restricted." Both looked identical (`RESTRICTED && IDLE`) and
 * both granted a full chapter-grace allowance. That meant leaving a reader during restricted hours
 * and opening a different manga (a fresh `ReaderViewModel`, `IDLE` by construction) granted a brand
 * new allowance every time — the schedule was effectively never enforced across reader sessions.
 *
 * State machine (exactly the behavior contract's two paths):
 * `NotStarted -> OpenedWhileAllowed -> CurrentChapterGrace -> GraceConsumed -> Closed`
 * `NotStarted -> OpenedWhileRestricted -> Closed`
 *
 * One instance is owned per `ReaderViewModel` (i.e. per reader session) and is never persisted to
 * preferences, database, navigation arguments, or `SavedStateHandle` — a fresh reader always
 * re-evaluates the schedule from scratch via [NotStarted], exactly as the plan requires ("Do not
 * persist the grace entitlement... A new reader always evaluates the current schedule from
 * scratch").
 */
enum class ReaderScheduleEntitlement {
    /** No schedule evaluation has happened yet for this reader session. */
    NotStarted,

    /** The schedule was ALLOWED or DISABLED the first time this session evaluated it. No grace has been granted — none is needed while this state holds. */
    OpenedWhileAllowed,

    /** This session was [OpenedWhileAllowed] and the schedule has since become RESTRICTED. The reader may finish only its currently open chapter (existing chapter-grace mechanism); no extra chapter. */
    CurrentChapterGrace,

    /** The [CurrentChapterGrace] allowance has been used (the chapter-grace timer reached its EXPIRED phase). No further reading is permitted this session. */
    GraceConsumed,

    /** The schedule was already RESTRICTED the very first time this session evaluated it. No grace was ever granted — this reader is blocked immediately and stays blocked for its entire session, per the behavior contract's explicit two-path diagram (there is no path back to an allowed state within the same session). */
    OpenedWhileRestricted,

    /** The reader has been closed/destroyed. Terminal; a new reader session always starts a fresh instance at [NotStarted] rather than reusing this one. */
    Closed,
    ;

    /** True whenever this session must not be allowed to display readable chapter content. */
    val blocksReading: Boolean
        get() = this == OpenedWhileRestricted || this == GraceConsumed

    companion object {
        /**
         * Pure transition function — the single source of truth [ReaderViewModel.evaluateSchedule]
         * delegates to, and the only thing under direct unit test for this state machine.
         *
         * @param current this session's entitlement before this evaluation.
         * @param scheduleResult the schedule resolver's verdict for *this* evaluation instant.
         * @param graceConsumed true when the schedule-grace timer coordinator has reached its
         * EXPIRED phase since the last evaluation (i.e. the granted chapter-grace allowance has now
         * been used up). Irrelevant unless [current] is [CurrentChapterGrace].
         */
        fun next(current: ReaderScheduleEntitlement, scheduleResult: ReaderScheduleResult, graceConsumed: Boolean): ReaderScheduleEntitlement {
            if (current == Closed) return Closed // terminal — a closed session is never revived by a later evaluation
            return when (current) {
                NotStarted -> when (scheduleResult) {
                    ReaderScheduleResult.RESTRICTED -> OpenedWhileRestricted
                    ReaderScheduleResult.ALLOWED, ReaderScheduleResult.DISABLED -> OpenedWhileAllowed
                }
                OpenedWhileAllowed -> when (scheduleResult) {
                    // The only legal path into grace: an active, previously-allowed session transitioning to restricted.
                    ReaderScheduleResult.RESTRICTED -> CurrentChapterGrace
                    ReaderScheduleResult.ALLOWED, ReaderScheduleResult.DISABLED -> OpenedWhileAllowed
                }
                CurrentChapterGrace -> when {
                    graceConsumed -> GraceConsumed
                    // Per the behavior contract's diagram there is no path back to OpenedWhileAllowed once grace has
                    // started, even if the schedule swings back to ALLOWED before the current chapter
                    // finishes — the grace period is bound to "finish this one chapter," not to the
                    // schedule's instantaneous state. It resolves to GraceConsumed only when the chapter
                    // genuinely finishes (graceConsumed == true); until then it stays in CurrentChapterGrace.
                    else -> CurrentChapterGrace
                }
                GraceConsumed -> GraceConsumed // terminal for this session; only Closed (a new reader) escapes it
                OpenedWhileRestricted -> OpenedWhileRestricted // terminal for this session; see class doc
                Closed -> Closed
            }
        }
    }
}
// KMK <--

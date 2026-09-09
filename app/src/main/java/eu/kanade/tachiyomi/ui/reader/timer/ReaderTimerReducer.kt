package eu.kanade.tachiyomi.ui.reader.timer

// KMK v0.8.4 -->
/**
 * Pure state machine for the active-reading timer. No Android dependencies, no side effects —
 * every transition is `(ReaderTimerSession, ReaderTimerEvent, now) -> ReaderTimerSession`, where
 * `now` is a monotonic timestamp supplied by the caller (see `ReaderTimerClock`), so the reducer is
 * fully deterministic and unit-testable without touching real elapsed time.
 *
 * `now` is threaded through every event, not only [ReaderTimerEvent.Tick], so any transition out of
 * an actively-counting phase (Pause, ReaderBackground, expiry, ProcessRestored) can correctly freeze
 * `elapsedActiveMs` at the exact moment of the transition rather than relying on the caller to have
 * ticked immediately beforehand.
 *
 * Warning thresholds are modeled as a monotonically-growing set ([ReaderTimerSession
 * .firedWarningMinutes]) rather than as a separate exclusive phase: a phase-based "Warning" state
 * risks being missed by a slow StateFlow collector between two rapid ticks, where a set-membership
 * check on the emitted state can never be missed. The UI layer diffs `firedWarningMinutes` against
 * its previous value to fire a one-shot warning exactly once per threshold, which satisfies the
 * plan's "fires once per session" requirement without that fragility. Documented as a deviation
 * from the plan's literal phase list in the implementation report.
 */
object ReaderTimerReducer {

    fun reduce(state: ReaderTimerSession, event: ReaderTimerEvent, now: Long): ReaderTimerSession = when (event) {
        is ReaderTimerEvent.Start -> ReaderTimerSession(
            phase = ReaderTimerPhase.RUNNING,
            totalDurationMs = event.durationMs.coerceAtLeast(0L),
            elapsedActiveMs = 0L,
            lastResumeMonotonicMs = now,
            warningPolicy = event.warningPolicy,
            gracePolicy = event.gracePolicy,
            firedWarningMinutes = emptySet(),
            extraChapterUsed = false,
            pausedFromPhase = null,
            pauseReason = null,
        )

        ReaderTimerEvent.Pause -> freeze(state, now)?.let {
            it.copy(phase = ReaderTimerPhase.PAUSED, pausedFromPhase = state.phase, pauseReason = ReaderTimerPauseReason.USER)
        } ?: state

        ReaderTimerEvent.Resume -> if (state.phase == ReaderTimerPhase.PAUSED && state.pausedFromPhase != null) {
            state.copy(
                phase = state.pausedFromPhase,
                pausedFromPhase = null,
                pauseReason = null,
                lastResumeMonotonicMs = now,
            )
        } else {
            state
        }

        ReaderTimerEvent.Reset, ReaderTimerEvent.Stop -> ReaderTimerSession(
            warningPolicy = state.warningPolicy,
            gracePolicy = state.gracePolicy,
        )

        ReaderTimerEvent.ReaderBackground -> freeze(state, now)?.let {
            it.copy(phase = ReaderTimerPhase.PAUSED, pausedFromPhase = state.phase, pauseReason = ReaderTimerPauseReason.BACKGROUND)
        } ?: state // Idempotent: already paused or not counting — no-op.

        ReaderTimerEvent.ReaderForeground -> if (
            state.phase == ReaderTimerPhase.PAUSED &&
            state.pauseReason == ReaderTimerPauseReason.BACKGROUND &&
            state.pausedFromPhase != null
        ) {
            state.copy(
                phase = state.pausedFromPhase,
                pausedFromPhase = null,
                pauseReason = null,
                lastResumeMonotonicMs = now,
            )
        } else {
            // A USER pause (or IDLE/EXPIRED) must not silently resume just because the reader came back.
            state
        }

        is ReaderTimerEvent.ChapterChanged -> when (state.phase) {
            // Only a natural forward "next chapter" transition may grant the one-extra-chapter
            // allowance. Previous-chapter navigation and manual ChapterListDialog selection still
            // reset/end grace (per the plan, "consistently"), but must never consume the allowance.
            ReaderTimerPhase.CHAPTER_GRACE -> if (event.isNaturalProgression && state.gracePolicy.allowExtraChapter && !state.extraChapterUsed) {
                state.copy(phase = ReaderTimerPhase.EXTRA_CHAPTER_GRACE, extraChapterUsed = true, lastResumeMonotonicMs = now)
            } else {
                (freeze(state, now) ?: state).copy(phase = ReaderTimerPhase.EXPIRED, lastResumeMonotonicMs = null)
            }
            ReaderTimerPhase.EXTRA_CHAPTER_GRACE -> (freeze(state, now) ?: state).copy(phase = ReaderTimerPhase.EXPIRED, lastResumeMonotonicMs = null)
            else -> state // RUNNING/PAUSED/IDLE/EXPIRED: chapter navigation doesn't affect the timer
        }

        is ReaderTimerEvent.Tick -> applyTick(state, now)

        ReaderTimerEvent.ProcessRestored -> if (state.isActivelyCounting) {
            // No ticks occurred while the process was dead; treat it the same as backgrounding.
            // elapsedActiveMs is already whatever was last persisted (frozen), just clear the stale
            // "resume" timestamp and mark it paused-by-background.
            state.copy(
                phase = ReaderTimerPhase.PAUSED,
                pausedFromPhase = state.phase,
                pauseReason = ReaderTimerPauseReason.BACKGROUND,
                lastResumeMonotonicMs = null,
            )
        } else {
            state.copy(lastResumeMonotonicMs = null)
        }

        is ReaderTimerEvent.Restore -> {
            // Persisted sessions intentionally do not retain a monotonic resume timestamp. Treat
            // an actively-counting session restored without one as background-paused so the
            // owning ReaderActivity can explicitly resume it after foregrounding. Returning a
            // RUNNING session with a null timestamp leaves the ticker unable to advance forever.
            if (event.session.isActivelyCounting && event.session.lastResumeMonotonicMs == null) {
                event.session.copy(
                    phase = ReaderTimerPhase.PAUSED,
                    pausedFromPhase = event.session.phase,
                    pauseReason = ReaderTimerPauseReason.BACKGROUND,
                )
            } else {
                event.session
            }
        }

        ReaderTimerEvent.InvalidPersistedState -> ReaderTimerSession()
    }

    /** Freezes elapsed time at [now] for a state currently actively counting; null if it wasn't. */
    private fun freeze(state: ReaderTimerSession, now: Long): ReaderTimerSession? {
        if (!state.isActivelyCounting) return null
        return state.copy(elapsedActiveMs = state.elapsedAt(now), lastResumeMonotonicMs = null)
    }

    private fun applyTick(state: ReaderTimerSession, now: Long): ReaderTimerSession {
        if (!state.isActivelyCounting || state.lastResumeMonotonicMs == null) return state

        val elapsed = state.elapsedAt(now)
        val remainingMs = state.totalDurationMs - elapsed

        // Warnings only apply to the primary countdown, never during grace (already past expiry).
        val newlyFired = if (state.phase == ReaderTimerPhase.RUNNING) {
            state.warningPolicy.minutesBeforeExpiry.filter { minute ->
                minute !in state.firedWarningMinutes && remainingMs in 0..(minute * 60_000L)
            }
        } else {
            emptyList()
        }
        val firedWarningMinutes = state.firedWarningMinutes + newlyFired

        if (state.phase == ReaderTimerPhase.RUNNING && elapsed >= state.totalDurationMs) {
            return if (state.gracePolicy.finishCurrentChapter) {
                state.copy(
                    phase = ReaderTimerPhase.CHAPTER_GRACE,
                    elapsedActiveMs = elapsed,
                    lastResumeMonotonicMs = now,
                    firedWarningMinutes = firedWarningMinutes,
                )
            } else {
                state.copy(
                    phase = ReaderTimerPhase.EXPIRED,
                    elapsedActiveMs = elapsed,
                    lastResumeMonotonicMs = null,
                    firedWarningMinutes = firedWarningMinutes,
                )
            }
        }

        return state.copy(
            elapsedActiveMs = elapsed,
            lastResumeMonotonicMs = now,
            firedWarningMinutes = firedWarningMinutes,
        )
    }
}
// KMK <--

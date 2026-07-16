package eu.kanade.tachiyomi.ui.reader.timer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK v0.8.4 -->
class ReaderTimerReducerTest {

    private val warnAll = ReaderTimerWarningPolicy(setOf(15, 10, 5, 1))
    private val graceFinishOnly = ReaderTimerGracePolicy(finishCurrentChapter = true, allowExtraChapter = false)
    private val graceWithExtra = ReaderTimerGracePolicy(finishCurrentChapter = true, allowExtraChapter = true)
    private val graceNone = ReaderTimerGracePolicy(finishCurrentChapter = false, allowExtraChapter = false)

    private fun reduce(state: ReaderTimerSession, event: ReaderTimerEvent, now: Long) =
        ReaderTimerReducer.reduce(state, event, now)

    // --- Start ---

    @Test
    fun `Start transitions Idle to Running with a fresh session`() {
        val result = reduce(ReaderTimerSession(), ReaderTimerEvent.Start(900_000L, warnAll, graceFinishOnly), now = 1000L)
        assertEquals(ReaderTimerPhase.RUNNING, result.phase)
        assertEquals(900_000L, result.totalDurationMs)
        assertEquals(0L, result.elapsedActiveMs)
        assertEquals(1000L, result.lastResumeMonotonicMs)
        assertTrue(result.firedWarningMinutes.isEmpty())
        assertFalse(result.extraChapterUsed)
    }

    @Test
    fun `Start from Expired restarts a fresh session (duration change supported)`() {
        val expired = ReaderTimerSession(phase = ReaderTimerPhase.EXPIRED, totalDurationMs = 900_000L, elapsedActiveMs = 900_000L, extraChapterUsed = true)
        val result = reduce(expired, ReaderTimerEvent.Start(1_800_000L, warnAll, graceFinishOnly), now = 5000L)
        assertEquals(ReaderTimerPhase.RUNNING, result.phase)
        assertEquals(1_800_000L, result.totalDurationMs)
        assertEquals(0L, result.elapsedActiveMs)
        assertFalse(result.extraChapterUsed)
    }

    // --- Pause / Resume ---

    @Test
    fun `Pause freezes elapsed time and remembers the phase to resume into`() {
        val running = ReaderTimerSession(phase = ReaderTimerPhase.RUNNING, totalDurationMs = 900_000L, lastResumeMonotonicMs = 0L)
        val result = reduce(running, ReaderTimerEvent.Pause, now = 5000L)
        assertEquals(ReaderTimerPhase.PAUSED, result.phase)
        assertEquals(5000L, result.elapsedActiveMs)
        assertNull(result.lastResumeMonotonicMs)
        assertEquals(ReaderTimerPhase.RUNNING, result.pausedFromPhase)
        assertEquals(ReaderTimerPauseReason.USER, result.pauseReason)
    }

    @Test
    fun `Pause on an already-idle session is a no-op`() {
        val idle = ReaderTimerSession()
        assertEquals(idle, reduce(idle, ReaderTimerEvent.Pause, now = 1000L))
    }

    @Test
    fun `Resume restores the phase and rebases the monotonic clock`() {
        val paused = ReaderTimerSession(
            phase = ReaderTimerPhase.PAUSED,
            totalDurationMs = 900_000L,
            elapsedActiveMs = 5000L,
            pausedFromPhase = ReaderTimerPhase.RUNNING,
            pauseReason = ReaderTimerPauseReason.USER,
        )
        val result = reduce(paused, ReaderTimerEvent.Resume, now = 20_000L)
        assertEquals(ReaderTimerPhase.RUNNING, result.phase)
        assertEquals(5000L, result.elapsedActiveMs)
        assertEquals(20_000L, result.lastResumeMonotonicMs)
        assertNull(result.pausedFromPhase)
        assertNull(result.pauseReason)
    }

    @Test
    fun `Resume on a non-paused session is a no-op`() {
        val idle = ReaderTimerSession()
        assertEquals(idle, reduce(idle, ReaderTimerEvent.Resume, now = 1000L))
    }

    // --- Reset / Stop ---

    @Test
    fun `Reset clears the active session back to Idle`() {
        val running = ReaderTimerSession(phase = ReaderTimerPhase.RUNNING, totalDurationMs = 900_000L, elapsedActiveMs = 5000L, lastResumeMonotonicMs = 5000L)
        val result = reduce(running, ReaderTimerEvent.Reset, now = 10_000L)
        assertEquals(ReaderTimerPhase.IDLE, result.phase)
        assertEquals(0L, result.totalDurationMs)
        assertEquals(0L, result.elapsedActiveMs)
        assertNull(result.lastResumeMonotonicMs)
    }

    @Test
    fun `Stop clears the active session the same as Reset`() {
        val running = ReaderTimerSession(phase = ReaderTimerPhase.CHAPTER_GRACE, totalDurationMs = 900_000L, elapsedActiveMs = 900_000L, lastResumeMonotonicMs = 900_000L)
        val result = reduce(running, ReaderTimerEvent.Stop, now = 950_000L)
        assertEquals(ReaderTimerPhase.IDLE, result.phase)
    }

    // --- Background / Foreground ---

    @Test
    fun `ReaderBackground pauses a running session with BACKGROUND reason`() {
        val running = ReaderTimerSession(phase = ReaderTimerPhase.RUNNING, totalDurationMs = 900_000L, lastResumeMonotonicMs = 0L)
        val result = reduce(running, ReaderTimerEvent.ReaderBackground, now = 3000L)
        assertEquals(ReaderTimerPhase.PAUSED, result.phase)
        assertEquals(ReaderTimerPauseReason.BACKGROUND, result.pauseReason)
        assertEquals(3000L, result.elapsedActiveMs)
    }

    @Test
    fun `ReaderForeground auto-resumes a background-paused session`() {
        val paused = ReaderTimerSession(
            phase = ReaderTimerPhase.PAUSED,
            totalDurationMs = 900_000L,
            elapsedActiveMs = 3000L,
            pausedFromPhase = ReaderTimerPhase.RUNNING,
            pauseReason = ReaderTimerPauseReason.BACKGROUND,
        )
        val result = reduce(paused, ReaderTimerEvent.ReaderForeground, now = 60_000L)
        assertEquals(ReaderTimerPhase.RUNNING, result.phase)
        assertEquals(60_000L, result.lastResumeMonotonicMs)
    }

    @Test
    fun `ReaderForeground does NOT resume a session the user explicitly paused`() {
        val paused = ReaderTimerSession(
            phase = ReaderTimerPhase.PAUSED,
            totalDurationMs = 900_000L,
            elapsedActiveMs = 3000L,
            pausedFromPhase = ReaderTimerPhase.RUNNING,
            pauseReason = ReaderTimerPauseReason.USER,
        )
        val result = reduce(paused, ReaderTimerEvent.ReaderForeground, now = 60_000L)
        assertEquals(ReaderTimerPhase.PAUSED, result.phase)
        assertEquals(ReaderTimerPauseReason.USER, result.pauseReason)
        assertNull(result.lastResumeMonotonicMs)
    }

    @Test
    fun `ReaderBackground on an already user-paused session does not downgrade the pause reason`() {
        val userPaused = ReaderTimerSession(
            phase = ReaderTimerPhase.PAUSED,
            pausedFromPhase = ReaderTimerPhase.RUNNING,
            pauseReason = ReaderTimerPauseReason.USER,
        )
        val result = reduce(userPaused, ReaderTimerEvent.ReaderBackground, now = 1000L)
        assertEquals(userPaused, result)
    }

    // --- Tick / monotonic accounting ---

    @Test
    fun `Tick accumulates elapsed time monotonically`() {
        val running = ReaderTimerSession(phase = ReaderTimerPhase.RUNNING, totalDurationMs = 900_000L, lastResumeMonotonicMs = 0L)
        val result = reduce(running, ReaderTimerEvent.Tick(30_000L), now = 30_000L)
        assertEquals(30_000L, result.elapsedActiveMs)
        assertEquals(30_000L, result.lastResumeMonotonicMs)
    }

    @Test
    fun `Tick on a paused session is a no-op`() {
        val paused = ReaderTimerSession(phase = ReaderTimerPhase.PAUSED, elapsedActiveMs = 5000L, pausedFromPhase = ReaderTimerPhase.RUNNING, pauseReason = ReaderTimerPauseReason.USER)
        val result = reduce(paused, ReaderTimerEvent.Tick(999_999L), now = 999_999L)
        assertEquals(paused, result)
    }

    // --- Warnings ---

    @Test
    fun `warning fires once when remaining time crosses the threshold`() {
        val running = ReaderTimerSession(phase = ReaderTimerPhase.RUNNING, totalDurationMs = 900_000L, warningPolicy = ReaderTimerWarningPolicy(setOf(15)), lastResumeMonotonicMs = 0L)
        // 15 minutes remaining == 0 elapsed for a 15-minute duration; use a 30-minute duration so the
        // 15-minute warning fires at the halfway point (at 900_000ms elapsed of an 1_800_000ms duration).
        val thirtyMin = running.copy(totalDurationMs = 1_800_000L)
        val atThreshold = reduce(thirtyMin, ReaderTimerEvent.Tick(900_000L), now = 900_000L)
        assertTrue(15 in atThreshold.firedWarningMinutes)
    }

    @Test
    fun `a fired warning never fires again on subsequent ticks`() {
        val thirtyMin = ReaderTimerSession(
            phase = ReaderTimerPhase.RUNNING,
            totalDurationMs = 1_800_000L,
            warningPolicy = ReaderTimerWarningPolicy(setOf(15)),
            firedWarningMinutes = setOf(15),
            lastResumeMonotonicMs = 900_000L,
            elapsedActiveMs = 900_000L,
        )
        val result = reduce(thirtyMin, ReaderTimerEvent.Tick(910_000L), now = 910_000L)
        assertEquals(setOf(15), result.firedWarningMinutes)
    }

    @Test
    fun `multiple warning thresholds each fire exactly once as remaining time crosses them`() {
        var session = ReaderTimerSession(
            phase = ReaderTimerPhase.RUNNING,
            totalDurationMs = 20 * 60_000L, // 20 minutes
            warningPolicy = ReaderTimerWarningPolicy(setOf(15, 10, 5, 1)),
            lastResumeMonotonicMs = 0L,
        )
        // Tick past the 5-minute-remaining mark (15 minutes elapsed of 20) — 15 and 10 min thresholds should have already passed too.
        session = reduce(session, ReaderTimerEvent.Tick(15 * 60_000L), now = 15 * 60_000L)
        assertEquals(setOf(15, 10, 5), session.firedWarningMinutes)
        // Advance to expiry - the 1-minute warning should fire too, and only once.
        session = reduce(session, ReaderTimerEvent.Tick(19 * 60_000L), now = 19 * 60_000L)
        assertEquals(setOf(15, 10, 5, 1), session.firedWarningMinutes)
    }

    @Test
    fun `warnings do not fire during grace periods`() {
        val grace = ReaderTimerSession(
            phase = ReaderTimerPhase.CHAPTER_GRACE,
            totalDurationMs = 900_000L,
            warningPolicy = ReaderTimerWarningPolicy(setOf(15)),
            lastResumeMonotonicMs = 900_000L,
            elapsedActiveMs = 900_000L,
        )
        val result = reduce(grace, ReaderTimerEvent.Tick(950_000L), now = 950_000L)
        assertTrue(result.firedWarningMinutes.isEmpty())
    }

    // --- Expiry / chapter grace / one-extra-chapter ---

    @Test
    fun `expiry with finish-current-chapter enabled enters ChapterGrace instead of ending immediately`() {
        val running = ReaderTimerSession(phase = ReaderTimerPhase.RUNNING, totalDurationMs = 900_000L, gracePolicy = graceFinishOnly, lastResumeMonotonicMs = 0L)
        val result = reduce(running, ReaderTimerEvent.Tick(900_000L), now = 900_000L)
        assertEquals(ReaderTimerPhase.CHAPTER_GRACE, result.phase)
    }

    @Test
    fun `expiry with finish-current-chapter disabled ends immediately as Expired`() {
        val running = ReaderTimerSession(phase = ReaderTimerPhase.RUNNING, totalDurationMs = 900_000L, gracePolicy = graceNone, lastResumeMonotonicMs = 0L)
        val result = reduce(running, ReaderTimerEvent.Tick(900_000L), now = 900_000L)
        assertEquals(ReaderTimerPhase.EXPIRED, result.phase)
        assertNull(result.lastResumeMonotonicMs)
    }

    @Test
    fun `chapter boundary during ChapterGrace ends the session when extra chapter is not allowed`() {
        val grace = ReaderTimerSession(phase = ReaderTimerPhase.CHAPTER_GRACE, totalDurationMs = 900_000L, gracePolicy = graceFinishOnly, elapsedActiveMs = 900_000L, lastResumeMonotonicMs = 900_000L)
        val result = reduce(grace, ReaderTimerEvent.ChapterChanged("ch2"), now = 905_000L)
        assertEquals(ReaderTimerPhase.EXPIRED, result.phase)
        assertNull(result.lastResumeMonotonicMs)
    }

    @Test
    fun `chapter boundary during ChapterGrace grants exactly one extra chapter when enabled`() {
        val grace = ReaderTimerSession(phase = ReaderTimerPhase.CHAPTER_GRACE, totalDurationMs = 900_000L, gracePolicy = graceWithExtra, elapsedActiveMs = 900_000L, lastResumeMonotonicMs = 900_000L)
        val result = reduce(grace, ReaderTimerEvent.ChapterChanged("ch2"), now = 905_000L)
        assertEquals(ReaderTimerPhase.EXTRA_CHAPTER_GRACE, result.phase)
        assertTrue(result.extraChapterUsed)
    }

    @Test
    fun `a second chapter boundary during ExtraChapterGrace always ends the session (one-extra cap)`() {
        val extraGrace = ReaderTimerSession(
            phase = ReaderTimerPhase.EXTRA_CHAPTER_GRACE,
            totalDurationMs = 900_000L,
            gracePolicy = graceWithExtra,
            extraChapterUsed = true,
            elapsedActiveMs = 905_000L,
            lastResumeMonotonicMs = 905_000L,
        )
        val result = reduce(extraGrace, ReaderTimerEvent.ChapterChanged("ch3"), now = 910_000L)
        assertEquals(ReaderTimerPhase.EXPIRED, result.phase)
    }

    @Test
    fun `natural next-chapter progression during grace grants the extra chapter when allowed`() {
        val grace = ReaderTimerSession(phase = ReaderTimerPhase.CHAPTER_GRACE, totalDurationMs = 900_000L, gracePolicy = graceWithExtra, elapsedActiveMs = 900_000L, lastResumeMonotonicMs = 900_000L)
        val result = reduce(grace, ReaderTimerEvent.ChapterChanged("ch2", isNaturalProgression = true), now = 905_000L)
        assertEquals(ReaderTimerPhase.EXTRA_CHAPTER_GRACE, result.phase)
        assertTrue(result.extraChapterUsed)
    }

    @Test
    fun `manual chapter selection during grace ends the session and never consumes the extra-chapter allowance`() {
        // Per the plan: manual ChapterListDialog selection (and previous-chapter navigation) must
        // reset/end grace consistently, but must never be treated as "an actual post-expiry
        // next-chapter transition" — so it must never grant the one-extra-chapter allowance.
        val grace = ReaderTimerSession(phase = ReaderTimerPhase.CHAPTER_GRACE, totalDurationMs = 900_000L, gracePolicy = graceWithExtra, elapsedActiveMs = 900_000L, lastResumeMonotonicMs = 900_000L)
        val result = reduce(grace, ReaderTimerEvent.ChapterChanged("ch5", isNaturalProgression = false), now = 905_000L)
        assertEquals(ReaderTimerPhase.EXPIRED, result.phase)
        assertFalse(result.extraChapterUsed)
    }

    @Test
    fun `previous-chapter navigation during grace also never consumes the extra-chapter allowance`() {
        val grace = ReaderTimerSession(phase = ReaderTimerPhase.CHAPTER_GRACE, totalDurationMs = 900_000L, gracePolicy = graceWithExtra, elapsedActiveMs = 900_000L, lastResumeMonotonicMs = 900_000L)
        val result = reduce(grace, ReaderTimerEvent.ChapterChanged("ch0", isNaturalProgression = false), now = 905_000L)
        assertEquals(ReaderTimerPhase.EXPIRED, result.phase)
        assertFalse(result.extraChapterUsed)
    }

    @Test
    fun `a manual selection while ExtraChapterGrace is already active still ends the session, not a second extra`() {
        val extraGrace = ReaderTimerSession(
            phase = ReaderTimerPhase.EXTRA_CHAPTER_GRACE,
            totalDurationMs = 900_000L,
            gracePolicy = graceWithExtra,
            extraChapterUsed = true,
            elapsedActiveMs = 905_000L,
            lastResumeMonotonicMs = 905_000L,
        )
        val result = reduce(extraGrace, ReaderTimerEvent.ChapterChanged("ch5", isNaturalProgression = false), now = 910_000L)
        assertEquals(ReaderTimerPhase.EXPIRED, result.phase)
    }

    @Test
    fun `chapter changes while Running do not affect the timer (mid-session navigation)`() {
        val running = ReaderTimerSession(phase = ReaderTimerPhase.RUNNING, totalDurationMs = 900_000L, elapsedActiveMs = 60_000L, lastResumeMonotonicMs = 60_000L)
        val result = reduce(running, ReaderTimerEvent.ChapterChanged("ch2"), now = 60_000L)
        assertEquals(running, result)
    }

    @Test
    fun `rapid chapter changes (race) during ChapterGrace only ever consume the extra chapter once`() {
        var session = ReaderTimerSession(phase = ReaderTimerPhase.CHAPTER_GRACE, totalDurationMs = 900_000L, gracePolicy = graceWithExtra, elapsedActiveMs = 900_000L, lastResumeMonotonicMs = 900_000L)
        session = reduce(session, ReaderTimerEvent.ChapterChanged("ch2"), now = 901_000L)
        assertEquals(ReaderTimerPhase.EXTRA_CHAPTER_GRACE, session.phase)
        // A second rapid change arrives before the user notices — must end, not grant a second extra.
        session = reduce(session, ReaderTimerEvent.ChapterChanged("ch3"), now = 901_500L)
        assertEquals(ReaderTimerPhase.EXPIRED, session.phase)
        // A third change after expiry is a no-op.
        val afterExpiry = reduce(session, ReaderTimerEvent.ChapterChanged("ch4"), now = 902_000L)
        assertEquals(session, afterExpiry)
    }

    // --- Process restoration / invalid state ---

    @Test
    fun `ProcessRestored freezes an actively-counting session as background-paused`() {
        val running = ReaderTimerSession(phase = ReaderTimerPhase.RUNNING, totalDurationMs = 900_000L, elapsedActiveMs = 30_000L, lastResumeMonotonicMs = 30_000L)
        val result = reduce(running, ReaderTimerEvent.ProcessRestored, now = 999_999L)
        assertEquals(ReaderTimerPhase.PAUSED, result.phase)
        assertEquals(ReaderTimerPauseReason.BACKGROUND, result.pauseReason)
        assertEquals(30_000L, result.elapsedActiveMs) // not advanced by the dead-process gap
        assertNull(result.lastResumeMonotonicMs)
    }

    @Test
    fun `ProcessRestored on an already-idle session is harmless`() {
        val idle = ReaderTimerSession()
        val result = reduce(idle, ReaderTimerEvent.ProcessRestored, now = 1000L)
        assertEquals(ReaderTimerPhase.IDLE, result.phase)
    }

    @Test
    fun `InvalidPersistedState always resets to a fresh Idle session`() {
        val corrupt = ReaderTimerSession(phase = ReaderTimerPhase.EXTRA_CHAPTER_GRACE, totalDurationMs = -1L, elapsedActiveMs = 99L)
        val result = reduce(corrupt, ReaderTimerEvent.InvalidPersistedState, now = 1000L)
        assertEquals(ReaderTimerSession(), result)
    }
}
// KMK <--

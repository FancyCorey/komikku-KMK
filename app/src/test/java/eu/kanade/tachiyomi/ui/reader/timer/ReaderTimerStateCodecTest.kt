package eu.kanade.tachiyomi.ui.reader.timer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// KMK v0.8.4 -->
class ReaderTimerStateCodecTest {

    private fun decode(e: ReaderTimerStateCodec.Encoded) = ReaderTimerStateCodec.decode(
        phase = e.phase,
        totalMs = e.totalMs,
        elapsedMs = e.elapsedMs,
        warningMinutesCsv = e.warningMinutesCsv,
        finishChapter = e.finishChapter,
        allowExtra = e.allowExtra,
        firedWarningsCsv = e.firedWarningsCsv,
        extraUsed = e.extraUsed,
        pausedFrom = e.pausedFrom,
        pauseReason = e.pauseReason,
    )

    @Test
    fun `round trip preserves a running session (minus the never-persisted resume timestamp)`() {
        val session = ReaderTimerSession(
            phase = ReaderTimerPhase.RUNNING,
            totalDurationMs = 1_800_000L,
            elapsedActiveMs = 300_000L,
            warningPolicy = ReaderTimerWarningPolicy(setOf(15, 5)),
            gracePolicy = ReaderTimerGracePolicy(finishCurrentChapter = true, allowExtraChapter = true),
            firedWarningMinutes = setOf(15),
            extraChapterUsed = false,
        )
        val restored = decode(ReaderTimerStateCodec.encode(session))
        assertEquals(session.copy(lastResumeMonotonicMs = null), restored)
    }

    @Test
    fun `round trip preserves a paused session with its pausedFrom and reason`() {
        val session = ReaderTimerSession(
            phase = ReaderTimerPhase.PAUSED,
            totalDurationMs = 900_000L,
            elapsedActiveMs = 60_000L,
            pausedFromPhase = ReaderTimerPhase.CHAPTER_GRACE,
            pauseReason = ReaderTimerPauseReason.BACKGROUND,
        )
        val restored = decode(ReaderTimerStateCodec.encode(session))
        assertEquals(session, restored)
    }

    @Test
    fun `missing phase falls back to a fresh Idle session`() {
        val result = ReaderTimerStateCodec.decode(
            phase = null, totalMs = 900_000L, elapsedMs = 0L,
            warningMinutesCsv = "", finishChapter = true, allowExtra = false,
            firedWarningsCsv = "", extraUsed = false, pausedFrom = null, pauseReason = null,
        )
        assertEquals(ReaderTimerSession(), result)
    }

    @Test
    fun `unrecognized phase name falls back to Idle rather than crashing`() {
        val result = ReaderTimerStateCodec.decode(
            phase = "SOME_FUTURE_PHASE_THAT_DOES_NOT_EXIST_YET", totalMs = 900_000L, elapsedMs = 0L,
            warningMinutesCsv = "", finishChapter = true, allowExtra = false,
            firedWarningsCsv = "", extraUsed = false, pausedFrom = null, pauseReason = null,
        )
        assertEquals(ReaderTimerSession(), result)
    }

    @Test
    fun `negative durations are treated as corrupt and fall back to Idle`() {
        val result = ReaderTimerStateCodec.decode(
            phase = "RUNNING", totalMs = -1L, elapsedMs = 0L,
            warningMinutesCsv = "", finishChapter = true, allowExtra = false,
            firedWarningsCsv = "", extraUsed = false, pausedFrom = null, pauseReason = null,
        )
        assertEquals(ReaderTimerSession(), result)
    }

    @Test
    fun `PAUSED phase without a recorded pausedFrom is inconsistent and falls back to Idle`() {
        val result = ReaderTimerStateCodec.decode(
            phase = "PAUSED", totalMs = 900_000L, elapsedMs = 30_000L,
            warningMinutesCsv = "", finishChapter = true, allowExtra = false,
            firedWarningsCsv = "", extraUsed = false, pausedFrom = null, pauseReason = "USER",
        )
        assertEquals(ReaderTimerSession(), result)
    }

    @Test
    fun `malformed warning minutes CSV is dropped rather than crashing`() {
        val result = ReaderTimerStateCodec.decode(
            phase = "RUNNING", totalMs = 900_000L, elapsedMs = 0L,
            warningMinutesCsv = "15,not-a-number,,5", finishChapter = true, allowExtra = false,
            firedWarningsCsv = "", extraUsed = false, pausedFrom = null, pauseReason = null,
        )
        assertEquals(setOf(15, 5), result.warningPolicy.minutesBeforeExpiry)
    }

    @Test
    fun `unsupported warning minute values are filtered out on decode`() {
        val result = ReaderTimerStateCodec.decode(
            phase = "RUNNING", totalMs = 900_000L, elapsedMs = 0L,
            warningMinutesCsv = "15,7,999", finishChapter = true, allowExtra = false,
            firedWarningsCsv = "", extraUsed = false, pausedFrom = null, pauseReason = null,
        )
        assertEquals(setOf(15), result.warningPolicy.minutesBeforeExpiry)
    }

    @Test
    fun `decoded session never carries a resume timestamp - restoration always freezes`() {
        val result = decode(
            ReaderTimerStateCodec.encode(
                ReaderTimerSession(phase = ReaderTimerPhase.RUNNING, totalDurationMs = 900_000L, lastResumeMonotonicMs = 12345L),
            ),
        )
        assertEquals(null, result.lastResumeMonotonicMs)
    }
}
// KMK <--

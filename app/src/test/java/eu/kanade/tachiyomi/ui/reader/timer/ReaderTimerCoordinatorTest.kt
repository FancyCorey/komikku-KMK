package eu.kanade.tachiyomi.ui.reader.timer

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK v0.8.4 -->
class ReaderTimerCoordinatorTest {

    @Test
    fun `start begins Running immediately and synchronously`() = runTest {
        val coordinator = ReaderTimerCoordinator(scope = this, clock = ReaderTimerClock { 0L })
        coordinator.start(5_000L, ReaderTimerWarningPolicy.NONE, ReaderTimerGracePolicy())
        assertEquals(ReaderTimerPhase.RUNNING, coordinator.state.value.phase)
        coordinator.stop() // cancels the internal ticker so runTest doesn't see a leaked child job
    }

    @Test
    fun `the internal ticker advances elapsed time and reaches Expired without user intervention`() = runTest {
        var now = 0L
        val coordinator = ReaderTimerCoordinator(scope = this, clock = ReaderTimerClock { now })
        coordinator.start(5_000L, ReaderTimerWarningPolicy.NONE, ReaderTimerGracePolicy(finishCurrentChapter = false))
        now = 5_000L
        advanceTimeBy(1_100L)
        runCurrent()
        assertEquals(ReaderTimerPhase.EXPIRED, coordinator.state.value.phase)
    }

    @Test
    fun `pause stops further ticking - elapsed time does not advance while paused`() = runTest {
        var now = 0L
        val coordinator = ReaderTimerCoordinator(scope = this, clock = ReaderTimerClock { now })
        coordinator.start(60_000L, ReaderTimerWarningPolicy.NONE, ReaderTimerGracePolicy())
        now = 3_000L
        advanceTimeBy(1_100L)
        runCurrent()
        coordinator.pause()
        val elapsedAtPause = coordinator.state.value.elapsedActiveMs
        now = 50_000L // simulate a large real-time gap while paused
        advanceTimeBy(5_000L)
        runCurrent()
        assertEquals(elapsedAtPause, coordinator.state.value.elapsedActiveMs)
        assertEquals(ReaderTimerPhase.PAUSED, coordinator.state.value.phase)
    }

    @Test
    fun `onReaderBackground then onReaderForeground resumes without losing elapsed progress`() = runTest {
        var now = 0L
        val coordinator = ReaderTimerCoordinator(scope = this, clock = ReaderTimerClock { now })
        coordinator.start(60_000L, ReaderTimerWarningPolicy.NONE, ReaderTimerGracePolicy())
        now = 2_000L
        advanceTimeBy(1_100L)
        runCurrent()
        coordinator.onReaderBackground()
        assertEquals(ReaderTimerPhase.PAUSED, coordinator.state.value.phase)
        val elapsedWhileBackgrounded = coordinator.state.value.elapsedActiveMs
        now = 500_000L // long real-time gap in the background must not count
        coordinator.onReaderForeground()
        assertEquals(ReaderTimerPhase.RUNNING, coordinator.state.value.phase)
        assertEquals(elapsedWhileBackgrounded, coordinator.state.value.elapsedActiveMs)
        coordinator.stop() // cancels the internal ticker so runTest doesn't see a leaked child job
    }

    @Test
    fun `user pause remains paused when the reader returns to the foreground`() = runTest {
        var now = 0L
        val coordinator = ReaderTimerCoordinator(scope = this, clock = ReaderTimerClock { now })
        coordinator.start(60_000L, ReaderTimerWarningPolicy.NONE, ReaderTimerGracePolicy())
        now = 2_000L
        advanceTimeBy(1_100L)
        runCurrent()
        coordinator.pause()
        val paused = coordinator.state.value

        now = 500_000L
        coordinator.onReaderForeground()
        advanceTimeBy(2_000L)
        runCurrent()

        assertEquals(ReaderTimerPhase.PAUSED, coordinator.state.value.phase)
        assertEquals(paused.elapsedActiveMs, coordinator.state.value.elapsedActiveMs)
        coordinator.stop()
    }

    @Test
    fun `persistence callback fires on every state change with the latest session`() = runTest {
        val persisted = mutableListOf<ReaderTimerSession>()
        val coordinator = ReaderTimerCoordinator(scope = this, clock = ReaderTimerClock { 0L }, onPersist = { persisted += it })
        coordinator.start(5_000L, ReaderTimerWarningPolicy.NONE, ReaderTimerGracePolicy())
        coordinator.pause()
        assertTrue(persisted.size >= 2)
        assertEquals(ReaderTimerPhase.PAUSED, persisted.last().phase)
    }

    @Test
    fun `a restored session is available after the owning activity is recreated`() = runTest {
        val first = ReaderTimerCoordinator(scope = this, clock = ReaderTimerClock { 0L })
        first.start(60_000L, ReaderTimerWarningPolicy.NONE, ReaderTimerGracePolicy())
        first.onReaderBackground()
        val retained = first.state.value

        val second = ReaderTimerCoordinator(scope = this, clock = ReaderTimerClock { 0L })
        second.restore(retained)

        assertEquals(ReaderTimerPhase.PAUSED, second.state.value.phase)
        assertEquals(retained.elapsedActiveMs, second.state.value.elapsedActiveMs)
    }

    @Test
    fun `restoring a persisted running session pauses until foreground then resumes ticking`() = runTest {
        var now = 10_000L
        val coordinator = ReaderTimerCoordinator(scope = this, clock = ReaderTimerClock { now })
        coordinator.restore(
            ReaderTimerSession(
                phase = ReaderTimerPhase.RUNNING,
                totalDurationMs = 5_000L,
                elapsedActiveMs = 1_000L,
                lastResumeMonotonicMs = null,
            ),
        )

        assertEquals(ReaderTimerPhase.PAUSED, coordinator.state.value.phase)
        assertEquals(ReaderTimerPauseReason.BACKGROUND, coordinator.state.value.pauseReason)

        coordinator.onReaderForeground()
        assertEquals(ReaderTimerPhase.RUNNING, coordinator.state.value.phase)
        now = 14_000L
        coordinator.state.value.let { }
        advanceTimeBy(1_100L)
        runCurrent()

        assertEquals(5_000L, coordinator.state.value.elapsedActiveMs)
        coordinator.stop()
    }

    @Test
    fun `reset stops the ticker - no further elapsed accumulation after reset`() = runTest {
        var now = 0L
        val coordinator = ReaderTimerCoordinator(scope = this, clock = ReaderTimerClock { now })
        coordinator.start(60_000L, ReaderTimerWarningPolicy.NONE, ReaderTimerGracePolicy())
        now = 1_000L
        advanceTimeBy(1_100L)
        runCurrent()
        coordinator.reset()
        assertEquals(ReaderTimerPhase.IDLE, coordinator.state.value.phase)
        now = 100_000L
        advanceTimeBy(5_000L)
        runCurrent()
        assertEquals(ReaderTimerPhase.IDLE, coordinator.state.value.phase)
        assertEquals(0L, coordinator.state.value.elapsedActiveMs)
    }
}
// KMK <--

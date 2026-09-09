package eu.kanade.tachiyomi.ui.reader.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlternateSourceReaderStateMachineTest {

    @Test
    fun `entry accepts only the latest generation and keeps stale completion inert`() {
        val started = AlternateSourceReaderStateMachine.reduce(
            AlternateSourceReaderMachineState(),
            AlternateSourceReaderEvent.BeginEntry,
        ).state
        assertEquals(AlternateSourceReaderPhase.RESOLVING_ENTRY, started.phase)
        assertEquals(1L, started.generation)

        val stale = AlternateSourceReaderStateMachine.reduce(
            started,
            AlternateSourceReaderEvent.EntryResolved(0L, readerSession()),
        )
        assertFalse(stale.applied)
        assertEquals(started, stale.state)

        val resolved = AlternateSourceReaderStateMachine.reduce(
            started,
            AlternateSourceReaderEvent.EntryResolved(started.generation, readerSession()),
        )
        assertTrue(resolved.applied)
        assertEquals(AlternateSourceReaderPhase.ALTERNATE, resolved.state.phase)
        assertEquals(readerSession(), resolved.state.session)
    }

    @Test
    fun `ordinary failure degrades with the readable session and successful return clears it`() {
        val alternate = AlternateSourceReaderMachineState(
            phase = AlternateSourceReaderPhase.ALTERNATE,
            generation = 1L,
            session = readerSession(),
        )
        val returning = AlternateSourceReaderStateMachine.reduce(
            alternate,
            AlternateSourceReaderEvent.BeginReturn,
        ).state
        val failed = AlternateSourceReaderStateMachine.reduce(
            returning,
            AlternateSourceReaderEvent.Failed(
                returning.generation,
                AlternateSourceReaderFailureReason.ROUTE_UNAVAILABLE,
            ),
        ).state
        assertEquals(AlternateSourceReaderPhase.DEGRADED, failed.phase)
        assertEquals(alternate.session, failed.session)

        val retry = AlternateSourceReaderStateMachine.reduce(failed, AlternateSourceReaderEvent.BeginReturn).state
        val ended = AlternateSourceReaderStateMachine.reduce(
            retry,
            AlternateSourceReaderEvent.ReturnResolved(retry.generation),
        ).state
        assertEquals(AlternateSourceReaderPhase.ENDED, ended.phase)
        assertNull(ended.session)
        assertNull(ended.failureReason)
    }

    @Test
    fun `invalid phase and exhausted generation do not start work`() {
        val ended = AlternateSourceReaderMachineState(
            phase = AlternateSourceReaderPhase.ENDED,
            generation = Long.MAX_VALUE,
        )
        val result = AlternateSourceReaderStateMachine.reduce(ended, AlternateSourceReaderEvent.BeginEntry)
        assertFalse(result.applied)
        assertEquals(ended, result.state)
    }
}

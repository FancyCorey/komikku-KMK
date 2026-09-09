package exh.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ActionHistoryDiagnosticTraceTest {

    @BeforeEach
    fun clearTrace() {
        ActionHistoryDiagnosticTrace.clear()
    }

    @Test
    fun `committed boundary records safe write metadata`() {
        ActionHistoryDiagnosticTrace.recordCommitted(
            rowKey = "row-1",
            family = "taste",
            operation = "RATE_LOVE",
            readCount = 1,
            writeCount = 1,
            affectedCount = 1,
            timestamp = 10L,
        )

        val event = ActionHistoryDiagnosticTrace.snapshotFor("row-1").single()
        assertEquals(DiagnosticTracePhase.COMMITTED, event.phase)
        assertEquals(DiagnosticTraceOutcome.SUCCESS, event.outcome)
        assertEquals(1, event.readCount)
        assertEquals(1, event.writeCount)
        assertTrue(event.writeCommitted)
    }

    @Test
    fun `attempt and cancellation remain separate events`() {
        val token = ActionHistoryDiagnosticTrace.begin("row-2", "action history", "undo", timestamp = 20L)
        ActionHistoryDiagnosticTrace.finish(
            token = token,
            phase = DiagnosticTracePhase.CANCELLED,
            outcome = DiagnosticTraceOutcome.CANCELLED,
            timestamp = 25L,
        )

        val events = ActionHistoryDiagnosticTrace.snapshotFor("row-2")
        assertEquals(listOf(DiagnosticTracePhase.ATTEMPTED, DiagnosticTracePhase.CANCELLED), events.map { it.phase })
        assertEquals(5L, events.last().durationMillis)
        assertFalse(events.last().writeCommitted)
    }

    @Test
    fun `failed and skipped outcomes do not claim a committed write`() {
        val failed = ActionHistoryDiagnosticTrace.begin("row-3", "action history", "undo")
        ActionHistoryDiagnosticTrace.finish(failed, DiagnosticTracePhase.FAILED, DiagnosticTraceOutcome.FAILED)
        val skipped = ActionHistoryDiagnosticTrace.begin("row-4", "action history", "follow up")
        ActionHistoryDiagnosticTrace.finish(skipped, DiagnosticTracePhase.SKIPPED, DiagnosticTraceOutcome.SKIPPED)

        assertFalse(ActionHistoryDiagnosticTrace.snapshotFor("row-3").last().writeCommitted)
        assertFalse(ActionHistoryDiagnosticTrace.snapshotFor("row-4").last().writeCommitted)
    }

    @Test
    fun `sensitive operation labels are redacted before storage`() {
        ActionHistoryDiagnosticTrace.recordCommitted(
            rowKey = "row-5",
            family = "https://private.example/source",
            operation = "https://private.example/manga?title=secret",
            readCount = 0,
            writeCount = 1,
            affectedCount = 1,
        )

        val event = ActionHistoryDiagnosticTrace.snapshotFor("row-5").single()
        assertEquals("redacted", event.family)
        assertEquals("redacted", event.operation)
        assertNotEquals("https://private.example/manga?title=secret", event.operation)
    }

    @Test
    fun `trace is bounded and clearable`() {
        repeat(ActionHistoryDiagnosticTrace.MAX_EVENTS + 3) { index ->
            ActionHistoryDiagnosticTrace.recordCommitted(
                rowKey = "row-$index",
                family = "event",
                operation = "EVENT",
                readCount = 0,
                writeCount = 1,
                affectedCount = 1,
            )
        }

        assertEquals(ActionHistoryDiagnosticTrace.MAX_EVENTS, ActionHistoryDiagnosticTrace.snapshot().size)
        assertTrue(ActionHistoryDiagnosticTrace.snapshotFor("row-0").isEmpty())
        ActionHistoryDiagnosticTrace.clear()
        assertTrue(ActionHistoryDiagnosticTrace.snapshot().isEmpty())
    }
}

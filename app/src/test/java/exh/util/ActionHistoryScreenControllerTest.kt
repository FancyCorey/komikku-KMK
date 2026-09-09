package exh.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class ActionHistoryScreenControllerTest {

    @BeforeEach
    fun clearDiagnostics() {
        ActionHistoryDiagnosticTrace.clear()
        EvaluationModeUndoJournal.clear()
    }

    @AfterEach
    fun clearJournal() {
        EvaluationModeUndoJournal.clear()
    }

    @Test
    fun `summary intent opens the exact live journal-owned manga from source`() = runTest {
        val row = descriptor(contextMangaId = 42L)
        val lookedUp = mutableListOf<Long>()

        val intent = ActionHistoryRowIntentPolicy.resolve(row, ActionHistoryRowTarget.SUMMARY) { mangaId ->
            lookedUp += mangaId
            true
        }

        assertEquals(listOf(42L), lookedUp)
        assertEquals(ActionHistoryRowIntent.OpenManga(mangaId = 42L, fromSource = true), intent)
    }

    @Test
    fun `missing invalid deleted or failed manga lookup never creates a navigation intent`() = runTest {
        val calls = AtomicInteger()
        val missing = ActionHistoryRowIntentPolicy.resolve(
            descriptor(contextMangaId = null),
            ActionHistoryRowTarget.SUMMARY,
        ) {
            calls.incrementAndGet()
            true
        }
        val invalid = ActionHistoryRowIntentPolicy.resolve(
            descriptor(contextMangaId = -1L),
            ActionHistoryRowTarget.SUMMARY,
        ) {
            calls.incrementAndGet()
            true
        }
        val deleted = ActionHistoryRowIntentPolicy.resolve(
            descriptor(contextMangaId = 7L),
            ActionHistoryRowTarget.SUMMARY,
        ) {
            calls.incrementAndGet()
            false
        }
        val failedLookup = ActionHistoryRowIntentPolicy.resolve(
            descriptor(contextMangaId = 8L),
            ActionHistoryRowTarget.SUMMARY,
        ) {
            calls.incrementAndGet()
            error("database unavailable")
        }

        assertEquals(ActionHistoryRowIntent.None, missing)
        assertEquals(ActionHistoryRowIntent.None, invalid)
        assertEquals(ActionHistoryRowIntent.ContextUnavailable, deleted)
        assertEquals(ActionHistoryRowIntent.ContextUnavailable, failedLookup)
        assertEquals(2, calls.get())
    }

    @Test
    fun `Undo and follow-up targets never resolve or open manga context`() = runTest {
        val calls = AtomicInteger()
        val undoRow = descriptor(
            contextMangaId = 42L,
            undo = { ActionHistoryUndoResult.Simple(GroupUndoResult.RESTORED) },
        )
        val followUpRow = descriptor(
            contextMangaId = 42L,
            followUp = ActionHistoryFollowUp(
                label = { "Forward" },
                trigger = { ActionHistoryFollowUpResult.Started },
            ),
        )

        val undo = ActionHistoryRowIntentPolicy.resolve(undoRow, ActionHistoryRowTarget.UNDO) {
            calls.incrementAndGet()
            true
        }
        val followUp = ActionHistoryRowIntentPolicy.resolve(followUpRow, ActionHistoryRowTarget.FOLLOW_UP) {
            calls.incrementAndGet()
            true
        }

        assertEquals(ActionHistoryRowIntent.ExecuteUndo, undo)
        assertEquals(ActionHistoryRowIntent.ExecuteFollowUp, followUp)
        assertEquals(0, calls.get())
    }

    @Test
    fun `summary lookup cancellation and fatal errors propagate`() {
        assertThrows(CancellationException::class.java) {
            runTest {
                ActionHistoryRowIntentPolicy.resolve(
                    descriptor(contextMangaId = 42L),
                    ActionHistoryRowTarget.SUMMARY,
                ) {
                    throw CancellationException("cancelled")
                }
            }
        }
        assertThrows(AssertionError::class.java) {
            runTest {
                ActionHistoryRowIntentPolicy.resolve(
                    descriptor(contextMangaId = 42L),
                    ActionHistoryRowTarget.SUMMARY,
                ) {
                    throw AssertionError("fatal")
                }
            }
        }
    }

    @Test
    fun `successful Undo refreshes after the journal removes its row`() = runTest {
        var rows = emptyList<ActionHistoryEntryDescriptor>()
        val row = descriptor(
            diagnosticKey = "undo-success",
            undo = {
                rows = emptyList()
                ActionHistoryUndoResult.Simple(GroupUndoResult.RESTORED)
            },
        )
        rows = listOf(row)
        val controller = ActionHistoryCommandController(snapshot = { rows })

        val result = controller.executeUndo(row)

        val completed = assertInstanceOf(ActionHistoryCommandResult.UndoCompleted::class.java, result)
        assertTrue(completed.rows.isEmpty())
        assertEquals(GroupUndoResult.RESTORED, (completed.outcome as ActionHistoryUndoResult.Simple).result)
        assertEquals(DiagnosticTraceOutcome.SUCCESS, ActionHistoryDiagnosticTrace.snapshotFor("undo-success").last().outcome)
    }

    @Test
    fun `conflict and ordinary failure retain the row and refresh the presentation`() = runTest {
        val conflictRow = descriptor(
            diagnosticKey = "undo-conflict",
            undo = { ActionHistoryUndoResult.Simple(GroupUndoResult.CONFLICT) },
        )
        val failedRow = descriptor(
            diagnosticKey = "undo-failed",
            undo = { error("write failed") },
        )
        val rows = listOf(conflictRow, failedRow)
        val controller = ActionHistoryCommandController(snapshot = { rows })

        val conflict = assertInstanceOf(
            ActionHistoryCommandResult.UndoCompleted::class.java,
            controller.executeUndo(conflictRow),
        )
        val failed = assertInstanceOf(
            ActionHistoryCommandResult.Failed::class.java,
            controller.executeUndo(failedRow),
        )

        assertEquals(rows, conflict.rows)
        assertEquals(rows, failed.rows)
        assertEquals(DiagnosticTraceOutcome.CONFLICT, ActionHistoryDiagnosticTrace.snapshotFor("undo-conflict").last().outcome)
        assertEquals(DiagnosticTraceOutcome.FAILED, ActionHistoryDiagnosticTrace.snapshotFor("undo-failed").last().outcome)
    }

    @Test
    fun `partial Undo remains typed and refreshes the retained route row`() = runTest {
        val row = descriptor(
            diagnosticKey = "undo-partial",
            undo = {
                ActionHistoryUndoResult.Taste(
                    outcome = EvaluationUndoOutcome(
                        requestedCount = 2,
                        restoredCount = 1,
                        conflictCount = 1,
                        missingCount = 0,
                        failedCount = 0,
                    ),
                    isBulk = true,
                )
            },
        )
        val controller = ActionHistoryCommandController(snapshot = { listOf(row) })

        val result = assertInstanceOf(
            ActionHistoryCommandResult.UndoCompleted::class.java,
            controller.executeUndo(row),
        )

        assertEquals(listOf(row), result.rows)
        assertEquals(DiagnosticTraceOutcome.PARTIAL, ActionHistoryDiagnosticTrace.snapshotFor("undo-partial").last().outcome)
    }

    @Test
    fun `Undo cancellation rethrows and records no committed write`() {
        val row = descriptor(
            diagnosticKey = "undo-cancel",
            undo = { throw CancellationException("cancelled") },
        )
        val controller = ActionHistoryCommandController(snapshot = { listOf(row) })

        assertThrows(CancellationException::class.java) {
            runTest { controller.executeUndo(row) }
        }

        val finalTrace = ActionHistoryDiagnosticTrace.snapshotFor("undo-cancel").last()
        assertEquals(DiagnosticTraceOutcome.CANCELLED, finalTrace.outcome)
        assertFalse(finalTrace.writeCommitted)
    }

    @Test
    fun `Undo fatal error propagates`() {
        val row = descriptor(undo = { throw AssertionError("fatal") })
        val controller = ActionHistoryCommandController(snapshot = { listOf(row) })

        assertThrows(AssertionError::class.java) {
            runTest { controller.executeUndo(row) }
        }
    }

    @Test
    fun `follow-up result refreshes and never becomes Undo`() = runTest {
        val row = descriptor(
            diagnosticKey = "follow-up",
            followUp = ActionHistoryFollowUp(
                label = { "Reinstall" },
                trigger = { ActionHistoryFollowUpResult.Started },
            ),
        )
        val controller = ActionHistoryCommandController(snapshot = { listOf(row) })

        val result = assertInstanceOf(
            ActionHistoryCommandResult.FollowUpCompleted::class.java,
            controller.executeFollowUp(row),
        )

        assertEquals(ActionHistoryFollowUpResult.Started, result.outcome)
        assertEquals(listOf(row), result.rows)
        assertEquals(DiagnosticTracePhase.FOLLOW_UP, ActionHistoryDiagnosticTrace.snapshotFor("follow-up").last().phase)
        assertEquals(null, row.undo)
    }

    @Test
    fun `follow-up cancellation rethrows and records no committed write`() {
        val row = descriptor(
            diagnosticKey = "follow-up-cancel",
            followUp = ActionHistoryFollowUp(
                label = { "Reinstall" },
                trigger = { throw CancellationException("cancelled") },
            ),
        )
        val controller = ActionHistoryCommandController(snapshot = { listOf(row) })

        assertThrows(CancellationException::class.java) {
            runTest { controller.executeFollowUp(row) }
        }

        val finalTrace = ActionHistoryDiagnosticTrace.snapshotFor("follow-up-cancel").last()
        assertEquals(DiagnosticTraceOutcome.CANCELLED, finalTrace.outcome)
        assertFalse(finalTrace.writeCommitted)
    }

    @Test
    fun `missing follow-up is refused without invoking or removing the row`() = runTest {
        val row = descriptor()
        val controller = ActionHistoryCommandController(snapshot = { listOf(row) })

        val result = controller.executeFollowUp(row)

        assertInstanceOf(ActionHistoryCommandResult.Unavailable::class.java, result)
        assertEquals(listOf(row), result.rows)
    }

    @Test
    fun `controller snapshot reflects bounded journal eviction`() {
        val first = evaluationEntry(id = "oldest", timestamp = 0L)
        EvaluationModeUndoJournal.record(first)
        repeat(EvaluationModeUndoJournal.MAX_ENTRIES) { index ->
            EvaluationModeUndoJournal.record(evaluationEntry(id = "new-$index", timestamp = index + 1L))
        }

        val rows = ActionHistoryCommandController().rows()

        assertFalse(rows.any { it.id == "taste:oldest" })
        assertEquals(EvaluationModeUndoJournal.MAX_ENTRIES, rows.count { it.id.startsWith("taste:") })
    }

    @Test
    fun `clear-all mutates only after explicit confirmation`() {
        var rows = listOf(descriptor())
        var clearCalls = 0
        val controller = ActionHistoryCommandController(
            snapshot = { rows },
            clearAll = {
                clearCalls += 1
                rows = emptyList()
            },
        )

        val cancelled = controller.clear(confirmed = false)
        assertInstanceOf(ActionHistoryCommandResult.ClearConfirmationRequired::class.java, cancelled)
        assertEquals(0, clearCalls)
        assertEquals(1, cancelled.rows.size)

        val cleared = controller.clear(confirmed = true)
        assertInstanceOf(ActionHistoryCommandResult.Cleared::class.java, cleared)
        assertEquals(1, clearCalls)
        assertTrue(cleared.rows.isEmpty())
    }

    private fun descriptor(
        contextMangaId: Long? = null,
        diagnosticKey: String? = null,
        undo: (suspend () -> ActionHistoryUndoResult)? = null,
        followUp: ActionHistoryFollowUp? = null,
    ) = ActionHistoryEntryDescriptor(
        id = "row",
        timestamp = 1L,
        summary = { "summary" },
        undo = undo,
        followUp = followUp,
        contextMangaId = contextMangaId,
        diagnosticKey = diagnosticKey,
    )

    private fun evaluationEntry(
        id: String,
        timestamp: Long,
    ) = EvaluationJournalEntry(
        id = id,
        timestamp = timestamp,
        actionType = EvaluationJournalActionType.RATE_LOVE,
        mangaId = 1L,
        source = 10L,
        url = "/manga/1",
        previousRating = null,
        newRating = 2,
        isBulk = false,
        bulkOperationId = null,
        changedFields = setOf(EvaluationJournalEntry.FIELD_RATING),
    )
}

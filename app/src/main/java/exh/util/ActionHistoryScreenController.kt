package exh.util

import kotlinx.coroutines.CancellationException

enum class ActionHistoryRowTarget {
    SUMMARY,
    UNDO,
    FOLLOW_UP,
}

sealed interface ActionHistoryRowIntent {
    data class OpenManga(
        val mangaId: Long,
        val fromSource: Boolean = true,
    ) : ActionHistoryRowIntent

    data object ExecuteUndo : ActionHistoryRowIntent
    data object ExecuteFollowUp : ActionHistoryRowIntent
    data object ContextUnavailable : ActionHistoryRowIntent
    data object None : ActionHistoryRowIntent
}

object ActionHistoryRowIntentPolicy {
    suspend fun resolve(
        row: ActionHistoryEntryDescriptor,
        target: ActionHistoryRowTarget,
        mangaExists: suspend (Long) -> Boolean,
    ): ActionHistoryRowIntent = when (target) {
        ActionHistoryRowTarget.UNDO -> {
            if (row.undo != null) ActionHistoryRowIntent.ExecuteUndo else ActionHistoryRowIntent.None
        }
        ActionHistoryRowTarget.FOLLOW_UP -> {
            if (row.followUp != null) ActionHistoryRowIntent.ExecuteFollowUp else ActionHistoryRowIntent.None
        }
        ActionHistoryRowTarget.SUMMARY -> {
            val mangaId = ActionHistoryContextNavigationPolicy.targetMangaId(row.contextMangaId)
                ?: return ActionHistoryRowIntent.None
            val exists = try {
                mangaExists(mangaId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            if (exists) {
                ActionHistoryRowIntent.OpenManga(mangaId)
            } else {
                ActionHistoryRowIntent.ContextUnavailable
            }
        }
    }
}

object ActionHistoryRowPresentationPolicy {
    fun summary(
        evaluationModeEnabled: Boolean,
        standardSummary: () -> String,
        evaluationModeSummary: (() -> String)?,
        redactedFallback: () -> String,
    ): String = if (evaluationModeEnabled) {
        evaluationModeSummary?.invoke() ?: redactedFallback()
    } else {
        standardSummary()
    }

    fun canExposeDiagnostics(
        developerOptionsEnabled: Boolean,
        evaluationModeEnabled: Boolean,
    ): Boolean = DeveloperOptionsGatePolicy.canExposeDiagnostics(
        developerOptionsEnabled = developerOptionsEnabled,
        evaluationModeEnabled = evaluationModeEnabled,
    )
}

sealed interface ActionHistoryCommandResult {
    val rows: List<ActionHistoryEntryDescriptor>

    data class UndoCompleted(
        val outcome: ActionHistoryUndoResult,
        override val rows: List<ActionHistoryEntryDescriptor>,
    ) : ActionHistoryCommandResult

    data class FollowUpCompleted(
        val outcome: ActionHistoryFollowUpResult,
        override val rows: List<ActionHistoryEntryDescriptor>,
    ) : ActionHistoryCommandResult

    data class Failed(
        override val rows: List<ActionHistoryEntryDescriptor>,
    ) : ActionHistoryCommandResult

    data class Unavailable(
        override val rows: List<ActionHistoryEntryDescriptor>,
    ) : ActionHistoryCommandResult

    data class ClearConfirmationRequired(
        override val rows: List<ActionHistoryEntryDescriptor>,
    ) : ActionHistoryCommandResult

    data class Cleared(
        override val rows: List<ActionHistoryEntryDescriptor>,
    ) : ActionHistoryCommandResult
}

internal class ActionHistoryCommandController(
    private val snapshot: () -> List<ActionHistoryEntryDescriptor> = ActionHistoryRegistry::snapshot,
    private val clearAll: () -> Unit = ActionHistoryRegistry::clearAll,
) {
    fun rows(): List<ActionHistoryEntryDescriptor> = snapshot()

    suspend fun executeUndo(row: ActionHistoryEntryDescriptor): ActionHistoryCommandResult {
        val undo = row.undo ?: return ActionHistoryCommandResult.Unavailable(snapshot())
        val token = row.diagnosticKey?.let {
            ActionHistoryDiagnosticTrace.begin(it, "action history", "undo")
        }
        return try {
            val outcome = undo()
            token?.let {
                val (phase, result, counts) = traceResolutionForUndo(outcome)
                ActionHistoryDiagnosticTrace.finish(
                    token = it,
                    phase = phase,
                    outcome = result,
                    readCount = counts.first,
                    writeCount = counts.second,
                    writeCommitted = counts.second > 0,
                    affectedCount = counts.first,
                )
            }
            ActionHistoryCommandResult.UndoCompleted(outcome, snapshot())
        } catch (cancelled: CancellationException) {
            token?.let {
                ActionHistoryDiagnosticTrace.finish(
                    it,
                    DiagnosticTracePhase.CANCELLED,
                    DiagnosticTraceOutcome.CANCELLED,
                )
            }
            throw cancelled
        } catch (_: Exception) {
            token?.let {
                ActionHistoryDiagnosticTrace.finish(
                    it,
                    DiagnosticTracePhase.FAILED,
                    DiagnosticTraceOutcome.FAILED,
                )
            }
            ActionHistoryCommandResult.Failed(snapshot())
        }
    }

    suspend fun executeFollowUp(row: ActionHistoryEntryDescriptor): ActionHistoryCommandResult {
        val followUp = row.followUp ?: return ActionHistoryCommandResult.Unavailable(snapshot())
        val token = row.diagnosticKey?.let {
            ActionHistoryDiagnosticTrace.begin(it, "action history", "follow up")
        }
        return try {
            val outcome = runActionHistoryFollowUpSafely(followUp.trigger)
            token?.let {
                if (outcome == ActionHistoryFollowUpResult.Started) {
                    ActionHistoryDiagnosticTrace.finish(
                        it,
                        DiagnosticTracePhase.FOLLOW_UP,
                        DiagnosticTraceOutcome.SUCCESS,
                        writeCommitted = true,
                        affectedCount = 1,
                    )
                } else {
                    ActionHistoryDiagnosticTrace.finish(
                        it,
                        DiagnosticTracePhase.FAILED,
                        DiagnosticTraceOutcome.FAILED,
                    )
                }
            }
            ActionHistoryCommandResult.FollowUpCompleted(outcome, snapshot())
        } catch (cancelled: CancellationException) {
            token?.let {
                ActionHistoryDiagnosticTrace.finish(
                    it,
                    DiagnosticTracePhase.CANCELLED,
                    DiagnosticTraceOutcome.CANCELLED,
                )
            }
            throw cancelled
        }
    }

    fun clear(confirmed: Boolean): ActionHistoryCommandResult {
        if (!confirmed) {
            return ActionHistoryCommandResult.ClearConfirmationRequired(snapshot())
        }
        clearAll()
        return ActionHistoryCommandResult.Cleared(snapshot())
    }
}

private fun traceResolutionForUndo(
    outcome: ActionHistoryUndoResult,
): Triple<DiagnosticTracePhase, DiagnosticTraceOutcome, Pair<Int, Int>> = when (outcome) {
    is ActionHistoryUndoResult.Taste -> when {
        outcome.outcome.allRestored -> Triple(
            DiagnosticTracePhase.COMMITTED,
            DiagnosticTraceOutcome.SUCCESS,
            outcome.outcome.requestedCount to outcome.outcome.restoredCount,
        )
        outcome.outcome.partial -> Triple(
            DiagnosticTracePhase.FAILED,
            DiagnosticTraceOutcome.PARTIAL,
            outcome.outcome.requestedCount to outcome.outcome.restoredCount,
        )
        else -> Triple(
            DiagnosticTracePhase.FAILED,
            DiagnosticTraceOutcome.CONFLICT,
            outcome.outcome.requestedCount to 0,
        )
    }
    is ActionHistoryUndoResult.Simple -> when (outcome.result) {
        GroupUndoResult.RESTORED -> Triple(
            DiagnosticTracePhase.COMMITTED,
            DiagnosticTraceOutcome.SUCCESS,
            1 to 1,
        )
        GroupUndoResult.CONFLICT -> Triple(
            DiagnosticTracePhase.FAILED,
            DiagnosticTraceOutcome.CONFLICT,
            1 to 0,
        )
        GroupUndoResult.FAILED -> Triple(
            DiagnosticTracePhase.FAILED,
            DiagnosticTraceOutcome.FAILED,
            1 to 0,
        )
    }
}

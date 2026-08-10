package exh.util

// KMK -->
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.KmkEmptyStateArtwork
import eu.kanade.presentation.components.KmkEmptyStateIllustration
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.core.common.i18n.stringResource as contextStringResource

internal suspend fun runActionHistoryFollowUpSafely(
    trigger: suspend () -> ActionHistoryFollowUpResult,
): ActionHistoryFollowUpResult = try {
    trigger()
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    ActionHistoryFollowUpResult.Failed
}

/**
 * Evaluation Mode-only screen listing every safe in-memory undo journal (taste, group, library,
 * preference, chapter, and private custom-cover receipts) and letting the user undo a single action,
 * a whole bulk action, or clear all history.
 * Reachable only from Settings > Advanced > Developer tools while Evaluation Mode is enabled (see the
 * nav row added there). Evaluation Mode can be toggled off in Settings while this screen is still on
 * the back stack (e.g. via the system back button returning to Settings then toggling, or a second
 * window/instance), so this screen also re-checks [rememberEvaluationModeEnabled] live and pops itself
 * the moment it goes false, rather than trusting the gate at its single entry point to hold for the
 * screen's whole lifetime.
 */
class EvaluationModeActionHistoryScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        var rows by remember { mutableStateOf(ActionHistoryRegistry.snapshot()) }
        var confirmClearAll by remember { mutableStateOf(false) }

        val evaluationModeEnabled = rememberEvaluationModeEnabled()
        LaunchedEffect(evaluationModeEnabled) {
            if (!evaluationModeEnabled) {
                navigator.pop()
            }
        }
        if (!evaluationModeEnabled) return

        fun refresh() {
            rows = ActionHistoryRegistry.snapshot()
        }

        fun showUndoOutcome(outcome: ActionHistoryUndoResult) {
            val message = when (outcome) {
                is ActionHistoryUndoResult.Taste -> when {
                    outcome.outcome.requestedCount == 0 -> return
                    outcome.outcome.allRestored -> context.contextStringResource(KMR.strings.eval_undo_restored, outcome.outcome.restoredCount)
                    outcome.outcome.noneRestored -> context.contextStringResource(
                        KMR.strings.eval_undo_conflict_all,
                        outcome.outcome.conflictCount + outcome.outcome.missingCount + outcome.outcome.failedCount,
                    )
                    else -> context.contextStringResource(
                        KMR.strings.eval_undo_restored_partial,
                        outcome.outcome.restoredCount,
                        outcome.outcome.conflictCount + outcome.outcome.missingCount + outcome.outcome.failedCount,
                    )
                }
                is ActionHistoryUndoResult.Simple -> when (outcome.result) {
                    GroupUndoResult.RESTORED -> context.contextStringResource(KMR.strings.rated_manga_group_undo_restored)
                    GroupUndoResult.CONFLICT -> context.contextStringResource(KMR.strings.rated_manga_group_undo_conflict)
                    GroupUndoResult.FAILED -> context.contextStringResource(KMR.strings.rated_manga_group_undo_failed)
                }
            }
            scope.launch { snackbarHostState.showSnackbar(message) }
            refresh()
        }

        // KMK Confirmed Blocker Remediation Corrective Completion Plan V2 2026-07-29: distinct from
        // showUndoOutcome() -- a follow-up is never labeled or treated as an Undo (see
        // ActionHistoryFollowUp's doc).
        fun showFollowUpResult(result: ActionHistoryFollowUpResult) {
            val message = when (result) {
                ActionHistoryFollowUpResult.Started -> context.contextStringResource(KMR.strings.eval_undo_followup_started)
                ActionHistoryFollowUpResult.Failed -> context.contextStringResource(KMR.strings.eval_undo_followup_failed)
            }
            scope.launch { snackbarHostState.showSnackbar(message) }
            refresh()
        }

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(KMR.strings.eval_undo_history_title),
                    navigateUp = navigator::pop,
                    actions = {
                        IconButton(onClick = { confirmClearAll = true }, enabled = rows.isNotEmpty()) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = stringResource(KMR.strings.eval_undo_clear_all))
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { contentPadding ->
            if (rows.isEmpty()) {
                // Keep the empty state visually distinct from the list of recorded actions.
                Surface(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        KmkEmptyStateIllustration(
                            artwork = KmkEmptyStateArtwork.ACTION_HISTORY,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(96.dp),
                        )
                        Text(
                            text = stringResource(KMR.strings.eval_undo_history_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                    items(rows, key = { it.id }) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(row.summary(context), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    relativeJournalTime(context, row.timestamp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            val undo = row.undo
                            val followUp = row.followUp
                            when {
                                undo != null -> {
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                showUndoOutcome(undo())
                                            }
                                        },
                                    ) {
                                        Text(stringResource(KMR.strings.eval_undo_action))
                                    }
                                }
                                // KMK Confirmed Blocker Remediation Corrective Completion Plan V2
                                // 2026-07-29: a follow-up is a fresh forward operation
                                // (uninstall/reinstall this exact package), never labeled "Undo" --
                                // see ActionHistoryFollowUp's doc.
                                followUp != null -> {
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                showFollowUpResult(runActionHistoryFollowUpSafely(followUp.trigger))
                                            }
                                        },
                                    ) {
                                        Text(followUp.label(context))
                                    }
                                }
                                else -> {
                                    Text(
                                        stringResource(KMR.strings.eval_undo_not_undoable),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (confirmClearAll) {
            AlertDialog(
                onDismissRequest = { confirmClearAll = false },
                title = { Text(stringResource(KMR.strings.eval_undo_clear_all)) },
                text = { Text(stringResource(KMR.strings.eval_undo_clear_all_confirm)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            ActionHistoryRegistry.clearAll()
                            confirmClearAll = false
                            refresh()
                        },
                    ) { Text(stringResource(KMR.strings.eval_undo_clear_all)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClearAll = false }) { Text(stringResource(tachiyomi.i18n.MR.strings.action_cancel)) }
                },
            )
        }
    }
}

private fun relativeJournalTime(context: android.content.Context, timestamp: Long): String {
    val seconds = (System.currentTimeMillis() - timestamp) / 1000
    return when {
        seconds < 60 -> context.contextStringResource(KMR.strings.eval_undo_time_just_now)
        seconds < 3600 -> context.contextStringResource(KMR.strings.eval_undo_time_minutes_ago, (seconds / 60).toInt())
        else -> context.contextStringResource(KMR.strings.eval_undo_time_hours_ago, (seconds / 3600).toInt())
    }
}
// KMK <--

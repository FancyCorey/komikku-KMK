package exh.util

// KMK -->
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Info
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
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.KmkEmptyStateArtwork
import eu.kanade.presentation.components.KmkEmptyStateIllustration
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale
import tachiyomi.core.common.i18n.pluralStringResource as contextPluralStringResource
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

private fun tracePhaseLabel(phase: DiagnosticTracePhase): String = phase.name.lowercase(Locale.ROOT).replace('_', ' ')
private fun traceOutcomeLabel(outcome: DiagnosticTraceOutcome): String = outcome.name.lowercase(Locale.ROOT).replace('_', ' ')

/**
 * Normal user-facing screen listing every safe in-memory journal (taste, group, library, preference,
 * chapter, custom-cover, and non-undoable event records). Rows retain their truthful undo/follow-up
 * state, and rows that own a stable manga identity open that manga without performing Undo.
 */
class ActionHistoryScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
        val getManga = remember { Injekt.get<GetManga>() }
        val commandController = remember { ActionHistoryCommandController() }

        var rows by remember { mutableStateOf(commandController.rows()) }
        var confirmClearAll by remember { mutableStateOf(false) }
        var diagnosticRow by remember { mutableStateOf<ActionHistoryEntryDescriptor?>(null) }
        val evaluationModeEnabled = sourcePreferences.evaluationMode().get()
        val diagnosticsVisible = ActionHistoryRowPresentationPolicy.canExposeDiagnostics(
            developerOptionsEnabled = sourcePreferences.developerOptionsEnabled().get(),
            evaluationModeEnabled = evaluationModeEnabled,
        )

        fun showUndoOutcome(outcome: ActionHistoryUndoResult) {
            val message = when (outcome) {
                is ActionHistoryUndoResult.Taste -> when {
                    outcome.outcome.requestedCount == 0 -> return
                    outcome.outcome.allRestored -> if (outcome.isBulk) {
                        context.contextPluralStringResource(KMR.plurals.eval_undo_restored_group, count = outcome.outcome.restoredCount, outcome.outcome.restoredCount)
                    } else {
                        context.contextStringResource(KMR.strings.eval_undo_restored_single)
                    }
                    outcome.outcome.noneRestored -> if (outcome.isBulk) {
                        context.contextPluralStringResource(KMR.plurals.eval_undo_conflict_group, count = outcome.outcome.conflictCount + outcome.outcome.missingCount + outcome.outcome.failedCount, outcome.outcome.conflictCount + outcome.outcome.missingCount + outcome.outcome.failedCount)
                    } else {
                        context.contextStringResource(KMR.strings.eval_undo_conflict_single)
                    }
                    else -> {
                        val unresolved = outcome.outcome.conflictCount + outcome.outcome.missingCount + outcome.outcome.failedCount
                        if (outcome.isBulk) {
                            context.contextStringResource(
                                KMR.strings.eval_undo_restored_partial_group,
                                context.contextPluralStringResource(
                                    KMR.plurals.eval_undo_restored_manga_fragment,
                                    count = outcome.outcome.restoredCount,
                                    outcome.outcome.restoredCount,
                                ),
                                context.contextPluralStringResource(
                                    KMR.plurals.eval_undo_unresolved_item_fragment,
                                    count = unresolved,
                                    unresolved,
                                ),
                            )
                        } else {
                            context.contextPluralStringResource(KMR.plurals.eval_undo_restored_partial_single, count = unresolved, unresolved)
                        }
                    }
                }
                is ActionHistoryUndoResult.Simple -> when (outcome.result) {
                    GroupUndoResult.RESTORED -> context.contextStringResource(KMR.strings.rated_manga_group_undo_restored)
                    GroupUndoResult.CONFLICT -> context.contextStringResource(KMR.strings.rated_manga_group_undo_conflict)
                    GroupUndoResult.FAILED -> context.contextStringResource(KMR.strings.rated_manga_group_undo_failed)
                }
            }
            scope.launch { snackbarHostState.showSnackbar(message) }
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
                        val targetMangaId = ActionHistoryContextNavigationPolicy.targetMangaId(row.contextMangaId)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Keep navigation on the summary/time target only. The Undo or
                            // follow-up button must never inherit the manga-navigation click.
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .then(
                                        if (targetMangaId != null) {
                                            Modifier.clickable {
                                                scope.launch {
                                                    when (
                                                        val intent = ActionHistoryRowIntentPolicy.resolve(
                                                            row = row,
                                                            target = ActionHistoryRowTarget.SUMMARY,
                                                            mangaExists = { mangaId -> getManga.await(mangaId) != null },
                                                        )
                                                    ) {
                                                        is ActionHistoryRowIntent.OpenManga -> {
                                                            navigator.push(MangaScreen(intent.mangaId, intent.fromSource))
                                                        }
                                                        ActionHistoryRowIntent.ContextUnavailable -> {
                                                            snackbarHostState.showSnackbar(
                                                                context.contextStringResource(KMR.strings.eval_undo_context_unavailable),
                                                            )
                                                        }
                                                        else -> Unit
                                                    }
                                                }
                                            }
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .padding(end = 8.dp),
                            ) {
                                Text(
                                    ActionHistoryRowPresentationPolicy.summary(
                                        evaluationModeEnabled = evaluationModeEnabled,
                                        standardSummary = { row.summary(context) },
                                        evaluationModeSummary = row.evaluationModeSummary?.let { safeSummary ->
                                            { safeSummary(context) }
                                        },
                                        redactedFallback = {
                                            context.contextStringResource(KMR.strings.eval_undo_summary_private_action)
                                        },
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    relativeJournalTime(context, row.timestamp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val undo = row.undo
                                val followUp = row.followUp
                                when {
                                    undo != null -> {
                                        TextButton(
                                            onClick = {
                                                scope.launch {
                                                    when (val result = commandController.executeUndo(row)) {
                                                        is ActionHistoryCommandResult.UndoCompleted -> {
                                                            rows = result.rows
                                                            showUndoOutcome(result.outcome)
                                                        }
                                                        is ActionHistoryCommandResult.Failed -> {
                                                            rows = result.rows
                                                            snackbarHostState.showSnackbar(
                                                                context.contextStringResource(KMR.strings.eval_undo_followup_failed),
                                                            )
                                                        }
                                                        else -> {
                                                            rows = result.rows
                                                        }
                                                    }
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
                                                    when (val result = commandController.executeFollowUp(row)) {
                                                        is ActionHistoryCommandResult.FollowUpCompleted -> {
                                                            rows = result.rows
                                                            showFollowUpResult(result.outcome)
                                                        }
                                                        else -> {
                                                            rows = result.rows
                                                        }
                                                    }
                                                }
                                            },
                                        ) {
                                            Text(followUp.label(context))
                                        }
                                    }
                                    else -> {
                                        Text(
                                            stringResource(KMR.strings.eval_undo_view_only),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (diagnosticsVisible && row.diagnosticKey != null) {
                                    IconButton(onClick = { diagnosticRow = row }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Info,
                                            contentDescription = stringResource(KMR.strings.eval_undo_diagnostic_details),
                                        )
                                    }
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
                            val result = commandController.clear(confirmed = true)
                            rows = result.rows
                            confirmClearAll = false
                        },
                    ) { Text(stringResource(KMR.strings.eval_undo_clear_all)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClearAll = false }) { Text(stringResource(tachiyomi.i18n.MR.strings.action_cancel)) }
                },
            )
        }

        diagnosticRow?.let { row ->
            val events = row.diagnosticKey?.let(ActionHistoryDiagnosticTrace::snapshotFor).orEmpty()
            AlertDialog(
                onDismissRequest = { diagnosticRow = null },
                title = { Text(stringResource(KMR.strings.eval_undo_diagnostic_details)) },
                text = {
                    if (events.isEmpty()) {
                        Text(stringResource(KMR.strings.eval_undo_diagnostic_empty))
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            events.forEach { event ->
                                Text(
                                    text = context.contextStringResource(
                                        KMR.strings.eval_undo_diagnostic_event,
                                        tracePhaseLabel(event.phase),
                                        traceOutcomeLabel(event.outcome),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = context.contextStringResource(
                                        KMR.strings.eval_undo_diagnostic_counts,
                                        event.readCount,
                                        event.writeCount,
                                        event.affectedCount,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = context.contextStringResource(
                                        KMR.strings.eval_undo_diagnostic_write,
                                        if (event.writeCommitted) "committed" else "not committed",
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { diagnosticRow = null }) {
                        Text(stringResource(tachiyomi.i18n.MR.strings.action_ok))
                    }
                },
            )
        }
    }
}

/** Source-compatibility alias for older internal callers; new navigation uses [ActionHistoryScreen]. */
typealias EvaluationModeActionHistoryScreen = ActionHistoryScreen

private fun relativeJournalTime(context: android.content.Context, timestamp: Long): String {
    val seconds = (System.currentTimeMillis() - timestamp) / 1000
    return when {
        seconds < 60 -> context.contextStringResource(KMR.strings.eval_undo_time_just_now)
        seconds < 3600 -> context.contextStringResource(KMR.strings.eval_undo_time_minutes_ago, (seconds / 60).toInt())
        else -> context.contextStringResource(KMR.strings.eval_undo_time_hours_ago, (seconds / 3600).toInt())
    }
}
// KMK <--

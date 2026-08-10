package eu.kanade.presentation.more.settings.screen.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.WarningBanner
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.util.export.BackupCleanupRecoveryStore
import eu.kanade.tachiyomi.util.export.SafArtifactOutcome
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.LazyColumnWithAction
import tachiyomi.presentation.core.components.SectionCard
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

class CreateBackupScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { CreateBackupScreenModel() }
        val state by model.state.collectAsState()

        // KMK: the operation is reserved
        // at the create-action click, before the picker launches -- see onClickAction below.
        var pendingOperationId by remember { mutableStateOf<String?>(null) }
        val chooseBackupDir = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/*"),
        ) {
            val operationId = pendingOperationId
            pendingOperationId = null
            if (it != null) {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                // KMK: the
                // picker already created the destination document at this point. Cleanup ownership
                // for it lives in BackupCleanupRecoveryStore (application-scoped), not in this screen
                // or its ScreenModel -- see CreateBackupScreenModel.createBackup and
                // BackupCleanupRecoveryStore's own KDoc for why a screen/model-scoped coordinator is
                // not safe here: the WorkManager job this triggers is designed to outlive this screen.
                // The shared cleanup dialog is rendered once, at the application composition root
                // (MainActivity's BackupCleanupRecoveryDialog()), not here, so it stays reachable even
                // after this screen is popped.
                if (operationId == null || !model.createBackup(context, operationId, it)) {
                    eu.kanade.tachiyomi.util.export.handleUnregisterableBackupUri(
                        context,
                        it,
                        tachiyomi.i18n.kmk.KMR.strings.saf_export_registration_failed,
                        tachiyomi.i18n.kmk.KMR.strings.saf_export_registration_failed_retained,
                        tachiyomi.i18n.kmk.KMR.strings.saf_export_registration_failed_unrecoverable,
                    )
                }
            } else {
                operationId?.let { id -> BackupCleanupRecoveryStore.cancelReservation(id) }
            }
        }

        LaunchedEffect(Unit) {
            model.navigateBack.collectLatest { navigator.pop() }
        }

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(MR.strings.pref_create_backup),
                    navigateUp = navigator::pop,
                    scrollBehavior = it,
                )
            },
        ) { contentPadding ->
            LazyColumnWithAction(
                contentPadding = contentPadding,
                actionLabel = stringResource(MR.strings.action_create),
                actionEnabled = state.options.canCreate(),
                onClickAction = {
                    if (!BackupCreateJob.isManualJobRunning(context)) {
                        val operationId = BackupCleanupRecoveryStore.beginOperation()
                        if (operationId == null) {
                            context.toast(tachiyomi.i18n.kmk.KMR.strings.saf_export_operation_pending)
                        } else {
                            try {
                                pendingOperationId = operationId
                                chooseBackupDir.launch(BackupCreator.getFilename())
                            } catch (e: ActivityNotFoundException) {
                                BackupCleanupRecoveryStore.cancelReservation(operationId)
                                pendingOperationId = null
                                context.toast(MR.strings.file_picker_error)
                            }
                        }
                    } else {
                        context.toast(MR.strings.backup_in_progress)
                    }
                },
            ) {
                if (DeviceUtil.isMiui && DeviceUtil.isMiuiOptimizationDisabled()) {
                    item {
                        WarningBanner(MR.strings.restore_miui_warning)
                    }
                }

                item {
                    SectionCard(MR.strings.label_library) {
                        Options(BackupOptions.libraryOptions, state, model)
                    }
                }

                item {
                    SectionCard(MR.strings.label_settings) {
                        Options(BackupOptions.settingsOptions, state, model)
                    }
                }
            }
        }
    }

    @Composable
    private fun Options(
        options: ImmutableList<BackupOptions.Entry>,
        state: CreateBackupScreenModel.State,
        model: CreateBackupScreenModel,
    ) {
        options.forEach { option ->
            LabeledCheckbox(
                label = stringResource(option.label),
                checked = option.getter(state.options),
                onCheckedChange = {
                    model.toggle(option.setter, it)
                },
                enabled = option.enabled(state.options),
            )
        }
    }
}

private class CreateBackupScreenModel : StateScreenModel<CreateBackupScreenModel.State>(State()) {

    private val _navigateBack = Channel<Unit>()
    val navigateBack: Flow<Unit> = _navigateBack.receiveAsFlow()

    fun toggle(setter: (BackupOptions, Boolean) -> BackupOptions, enabled: Boolean) {
        mutableState.update {
            it.copy(
                options = setter(it.options, enabled),
            )
        }
    }

    // KMK: this write is
    // launched in `ProcessLifecycleOwner`'s application-process-scoped `lifecycleScope`, not
    // `screenModelScope` -- if it ran in `screenModelScope`, navigating away from this screen would
    // cancel the coroutine that polls `BackupCreateJob` to a terminal state, even though the
    // underlying WorkManager job itself keeps running. `ProcessLifecycleOwner.lifecycleScope` is
    // cancelled only when the app process itself is torn down, matching the actual lifetime of the
    // WorkManager job this tracks. [options] is snapshotted from `state.value` here, before the write
    // starts, exactly like every other CreateDocument writer in this app snapshots its input before
    // the picker/write boundary.
    fun createBackup(context: Context, operationId: String, uri: Uri): Boolean {
        val options = state.value.options
        if (!BackupCleanupRecoveryStore.registerUri(operationId, uri)) return false
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            val outcome = BackupCleanupRecoveryStore.performWrite(operationId) {
                // Durably mark the enqueue attempt BEFORE startNow -- if the process dies between
                // this call and startNow actually enqueuing the WorkManager job, startup reconciliation
                // must never assume no backup was ever attempted (see BackupEnqueueState).
                check(BackupCleanupRecoveryStore.markEnqueueAttempted(operationId)) {
                    "Backup recovery state could not be persisted"
                }
                val id = BackupCreateJob.startNow(context, uri, options)
                BackupCleanupRecoveryStore.attachWorkRequest(operationId, id)
                backupJobOutcomeFor(BackupCreateJob.awaitManualJobTerminalState(context, id))
            }
            if (outcome == SafArtifactOutcome.SUCCESS) {
                // A successful backup is never offered for cleanup and never presented as reversible -- clear
                // the recovery-store entry immediately and, if this screen is still around to hear
                // it, navigate back. If the screen (and this model) were already disposed, this
                // Channel send is a harmless no-op -- nothing is listening anymore.
                BackupCleanupRecoveryStore.clear(operationId)
                _navigateBack.send(Unit)
            }
            // Any other outcome leaves the offer retained in BackupCleanupRecoveryStore (set by
            // performWrite above) so BackupCleanupRecoveryDialog -- rendered at the application
            // composition root, reachable regardless of which screen is currently active -- can offer
            // Remove/Keep. This screen does not force navigation away on a non-Success outcome.
        }
        return true
    }

    @Immutable
    data class State(
        val options: BackupOptions = BackupOptions(),
    )
}

// KMK: extracted so the WorkInfo.State ->
// SafArtifactOutcome mapping has direct, non-instrumented test coverage. A null state
// (WorkManager has no record at all, e.g. a pruned or KEEP-dropped enqueue) or any other
// non-terminal state observed at this point is honestly unresolved, not evidence that the
// picker-created destination is empty and safe to remove.
internal fun backupJobOutcomeFor(state: WorkInfo.State?): SafArtifactOutcome = when (state) {
    WorkInfo.State.SUCCEEDED -> SafArtifactOutcome.SUCCESS
    WorkInfo.State.FAILED -> SafArtifactOutcome.FAILED
    WorkInfo.State.CANCELLED -> SafArtifactOutcome.CANCELLED
    else -> SafArtifactOutcome.UNRESOLVED
}

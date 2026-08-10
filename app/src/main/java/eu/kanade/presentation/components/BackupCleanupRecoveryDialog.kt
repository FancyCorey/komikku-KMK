package eu.kanade.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import eu.kanade.tachiyomi.util.export.BackupCleanupRecoveryStore
import tachiyomi.i18n.kmk.KMR

// KMK -->
// KMK: renders the shared
// Remove/Keep cleanup dialog for a pending backup-creation SAF document from a host that stays
// reachable regardless of which screen is currently active -- unlike the other 6 CreateDocument
// routes' screen/model-scoped dialogs, this one is driven by [BackupCleanupRecoveryStore]'s
// application-scoped offer, not a screenModelScope-owned SafExportCoordinator, since the backup
// WorkManager job it tracks can genuinely outlive `CreateBackupScreen`. Call this once, from the
// application composition root (`MainActivity`), alongside its other always-reachable dialogs.
@Composable
fun BackupCleanupRecoveryDialog() {
    val context = LocalContext.current
    val entry by BackupCleanupRecoveryStore.offer.collectAsState()
    entry?.let { current ->
        SafArtifactCleanupDialog(
            context = context,
            offer = eu.kanade.tachiyomi.util.export.SafCleanupOffer(current.operationId, current.uri, current.outcome),
            successTitleRes = KMR.strings.extension_export_cleanup_title,
            successBodyRes = KMR.strings.generic_export_cleanup_success_body,
            incompleteTitleRes = KMR.strings.extension_export_cleanup_incomplete_title,
            incompleteBodyRes = KMR.strings.extension_export_cleanup_incomplete_body,
            removeRes = KMR.strings.extension_export_cleanup_remove,
            keepRes = KMR.strings.extension_export_cleanup_keep,
            removedRes = KMR.strings.extension_export_cleanup_removed,
            removeFailedRes = KMR.strings.extension_export_cleanup_failed,
            onRemoved = { BackupCleanupRecoveryStore.clear(current.operationId) },
            onKept = { BackupCleanupRecoveryStore.clear(current.operationId) },
            onDismissed = { BackupCleanupRecoveryStore.clear(current.operationId) },
        )
    }
}
// KMK <--

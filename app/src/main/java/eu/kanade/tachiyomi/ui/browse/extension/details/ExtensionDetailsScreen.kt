package eu.kanade.tachiyomi.ui.browse.extension.details

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.ExtensionDetailsScreen
import eu.kanade.presentation.components.SafArtifactCleanupDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.util.ExtensionApkExporter
import eu.kanade.tachiyomi.extension.util.ExtensionInstaller
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.export.handleUnregisterableUri
import eu.kanade.tachiyomi.util.system.toast
import exh.util.EvaluationModeFormatter
import exh.util.rememberEvaluationModeEnabled
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

data class ExtensionDetailsScreen(
    private val pkgName: String,
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val screenModel = rememberScreenModel { ExtensionDetailsScreenModel(pkgName = pkgName, context = context) }
        val state by screenModel.state.collectAsState()

        if (state.isLoading) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow
        // KMK -->
        val source = state.extension?.sources?.getOrNull(0)
        val evaluationModeEnabled = rememberEvaluationModeEnabled()
        // KMK <--

        // KMK v0.8.18: manual extension APK export -- confirmation dialog, then SAF document picker,
        // then a background copy of the raw installed APK/archive bytes. Never repackages/re-signs.
        var showExportConfirm by remember { mutableStateOf(false) }
        // The
        // coordinator now lives on ExtensionDetailsScreenModel (screenModelScope-owned), not
        // `remember`ed in this Composable, so navigation/recomposition of this screen alone cannot
        // destroy a retained cleanup offer. `extensionSnapshot` still captures `state.extension` at
        // confirm-dialog click time (before the picker launches), never read live from `state` again
        // inside the launcher callback.
        var extensionSnapshot by remember { mutableStateOf<Extension.Installed?>(null) }
        // The operation is reserved
        // at the confirm-click, before the picker launches -- see the confirm dialog's onClick below.
        var pendingOperationId by remember { mutableStateOf<String?>(null) }
        val exportCleanupOffer by screenModel.exportCoordinator.cleanupOffer.collectAsState()
        val exportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument(ExtensionInstaller.APK_MIME),
        ) { uri ->
            val extension = extensionSnapshot
            extensionSnapshot = null
            val operationId = pendingOperationId
            pendingOperationId = null
            if (uri == null) {
                // The user cancelled the system picker before it created anything -- no document
                // exists, so there is nothing to offer cleanup for. Release the reservation so a
                // later export attempt is not blocked by a reservation that will never be used.
                operationId?.let { screenModel.exportCoordinator.cancelReservation(it) }
                return@rememberLauncherForActivityResult
            }
            if (operationId == null || !screenModel.exportSingleExtension(context, operationId, uri, extension)) {
                // Defensive: the reservation was somehow lost between confirm-click and picker
                // return. The picker still created a real document -- it must not be silently
                // discarded.
                handleUnregisterableUri(
                    context,
                    uri,
                    screenModel.exportCoordinator,
                    KMR.strings.saf_export_registration_failed,
                    KMR.strings.saf_export_registration_failed_retained,
                    KMR.strings.saf_export_registration_failed_unrecoverable,
                )
            }
        }

        ExtensionDetailsScreen(
            navigateUp = navigator::pop,
            state = state,
            onClickSourcePreferences = { navigator.push(SourcePreferencesScreen(it)) },
            // KMK -->
            onOpenWebView = if (source != null && source is HttpSource) {
                {
                    navigator.push(
                        WebViewScreen(
                            url = source.baseUrl,
                            initialTitle = if (evaluationModeEnabled) {
                                EvaluationModeFormatter.sourceLabel(source.id)
                            } else {
                                source.name
                            },
                            sourceId = source.id,
                        ),
                    )
                }
            } else {
                null
            },
            // KMK <--
            onClickEnableAll = { screenModel.toggleSources(true) },
            onClickDisableAll = { screenModel.toggleSources(false) },
            onClickClearCookies = screenModel::clearCookies,
            onClickUninstall = screenModel::uninstallExtension,
            onClickSource = screenModel::toggleSource,
            onClickIncognito = screenModel::toggleIncognito,
            // KMK v0.8.18: manual extension APK export
            onClickExportApk = if (state.extension != null) {
                { showExportConfirm = true }
            } else {
                null
            },
        )

        if (showExportConfirm) {
            val extension = state.extension
            AlertDialog(
                onDismissRequest = { showExportConfirm = false },
                title = { Text(stringResource(KMR.strings.extension_export_confirm_title)) },
                text = { Text(stringResource(KMR.strings.extension_export_confirm_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showExportConfirm = false
                            if (extension != null) {
                                val operationId = screenModel.exportCoordinator.beginOperation()
                                if (operationId == null) {
                                    context.toast(KMR.strings.saf_export_operation_pending)
                                } else {
                                    extensionSnapshot = extension
                                    pendingOperationId = operationId
                                    exportLauncher.launch(ExtensionApkExporter.suggestedApkFileName(extension))
                                }
                            }
                        },
                    ) {
                        Text(stringResource(KMR.strings.extension_export_confirm_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExportConfirm = false }) {
                        Text(stringResource(tachiyomi.i18n.MR.strings.action_cancel))
                    }
                },
            )
        }

        // Cleanup is
        // now offered via the shared SafArtifactCleanupDialog, driven by the coordinator's retained
        // offer, for ANY outcome that leaves a real document behind -- never automatic.
        exportCleanupOffer?.let { offer ->
            SafArtifactCleanupDialog(
                context = context,
                offer = offer,
                successTitleRes = KMR.strings.extension_export_cleanup_title,
                successBodyRes = KMR.strings.extension_export_cleanup_body,
                incompleteTitleRes = KMR.strings.extension_export_cleanup_incomplete_title,
                incompleteBodyRes = KMR.strings.extension_export_cleanup_incomplete_body,
                removeRes = KMR.strings.extension_export_cleanup_remove,
                keepRes = KMR.strings.extension_export_cleanup_keep,
                removedRes = KMR.strings.extension_export_cleanup_removed,
                removeFailedRes = KMR.strings.extension_export_cleanup_failed,
                onRemoved = { screenModel.exportCoordinator.clear(offer.operationId) },
                onKept = { screenModel.exportCoordinator.clear(offer.operationId) },
                onDismissed = { screenModel.exportCoordinator.clear(offer.operationId) },
            )
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                if (event is ExtensionDetailsEvent.Uninstalled) {
                    navigator.pop()
                }
            }
        }
    }
}

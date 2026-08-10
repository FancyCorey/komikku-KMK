package eu.kanade.tachiyomi.ui.browse.extension

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined._18UpRating
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.ExtensionScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.SafArtifactCleanupDialog
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.more.settings.screen.browse.ExtensionStoresScreen
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.ui.browse.extension.details.ExtensionDetailsScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.isPackageInstalled
import eu.kanade.tachiyomi.util.system.toast
import exh.util.EvaluationModeFormatter
import exh.util.rememberEvaluationModeEnabled
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun extensionsTab(
    extensionsScreenModel: ExtensionsScreenModel,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current

    val state by extensionsScreenModel.state.collectAsState()
    var privateExtensionToUninstall by remember { mutableStateOf<Extension?>(null) }
    // KMK -->
    var showBulkUninstallConfirmDialog by remember { mutableStateOf(false) }
    // KMK v0.8.18: manual extension APK export -- bulk export of the current selection as one zip
    // (raw APK/archive bytes + non-sensitive manifest.json). Never repackages/re-signs anything.
    var showBulkExportConfirmDialog by remember { mutableStateOf(false) }
    // KMK:
    // retrofitted onto the shared SafExportCoordinator, owned by ExtensionsScreenModel
    // (screenModelScope-scoped, not this Composable's `remember`), and rendered via the shared
    // SafArtifactCleanupDialog -- which only clears the retained offer on a *successful* deletion,
    // fixing the previous bug where a failed Remove still cleared `bulkExportCleanupUri` and silently
    // lost the only handle on the still-orphaned document.
    val bulkExportCleanupOffer by extensionsScreenModel.bulkExportCoordinator.cleanupOffer.collectAsState()
    // KMK: the selection must
    // be captured at the same user-confirmation boundary that starts the picker, not reconstructed
    // from live `state` inside the launcher callback -- `ActivityResultContracts.CreateDocument` is an
    // external lifecycle boundary (the system picker UI, possibly a cross-process/cross-activity trip)
    // and `state.selectedExtensionKeys`/`state.items` can change (or the user could, in principle,
    // re-enter/exit selection mode) while that UI is in front. Cleared on every terminal path --
    // null Uri (cancelled), successful handoff to the model, and never left stale across dialog
    // reopens.
    var selectedExtensionsForExport by remember { mutableStateOf<List<Extension.Installed>>(emptyList()) }
    // KMK: the operation is reserved at
    // the confirm-click, before the picker launches -- see the confirm dialog's onClick below.
    var bulkExportPendingOperationId by remember { mutableStateOf<String?>(null) }
    val bulkExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val selected = selectedExtensionsForExport
        selectedExtensionsForExport = emptyList()
        val operationId = bulkExportPendingOperationId
        bulkExportPendingOperationId = null
        // A null `uri` means the user cancelled the system picker before it created anything -- no
        // document exists, so there is nothing to offer cleanup for.
        if (uri == null) {
            operationId?.let { extensionsScreenModel.bulkExportCoordinator.cancelReservation(it) }
            return@rememberLauncherForActivityResult
        }
        if (operationId == null || !extensionsScreenModel.exportSelectedExtensions(context, operationId, uri, selected)) {
            eu.kanade.tachiyomi.util.export.handleUnregisterableUri(
                context,
                uri,
                extensionsScreenModel.bulkExportCoordinator,
                KMR.strings.saf_export_registration_failed,
                KMR.strings.saf_export_registration_failed_retained,
                KMR.strings.saf_export_registration_failed_unrecoverable,
            )
        }
    }
    // KMK <--

    return TabContent(
        titleRes = MR.strings.label_extensions,
        badgeNumber = state.updates.takeIf { it > 0 },
        searchEnabled = true,
        actions = persistentListOf(
            // KMK -->
            AppBar.Action(
                title = stringResource(KMR.strings.action_toggle_nsfw_only),
                icon = Icons.Outlined._18UpRating,
                iconTint = if (state.nsfwOnly) MaterialTheme.colorScheme.error else LocalContentColor.current,
                onClick = { extensionsScreenModel.toggleNsfwOnly() },
            ),
            AppBar.OverflowAction(
                title = stringResource(MR.strings.action_webview_refresh),
                onClick = extensionsScreenModel::findAvailableExtensions,
            ),
            AppBar.OverflowAction(
                title = stringResource(KMR.strings.extension_select_extensions),
                onClick = { if (!state.isExtensionSelectionMode) extensionsScreenModel.enterExtensionSelectionMode() },
            ),
            // KMK <--
            AppBar.OverflowAction(
                title = stringResource(MR.strings.action_filter),
                onClick = { navigator.push(ExtensionFilterScreen()) },
            ),
            AppBar.OverflowAction(
                title = stringResource(MR.strings.extensionStores),
                onClick = { navigator.push(ExtensionStoresScreen()) },
            ),
        ),
        content = { contentPadding, _ ->
            // KMK -->
            BackHandler(enabled = state.isExtensionSelectionMode || state.searchQuery != null) {
                if (state.isExtensionSelectionMode) {
                    extensionsScreenModel.exitExtensionSelectionMode()
                } else {
                    extensionsScreenModel.search(null)
                }
            }
            // KMK <--
            ExtensionScreen(
                state = state,
                contentPadding = contentPadding,
                searchQuery = state.searchQuery,
                onLongClickItem = { extension ->
                    when (extension) {
                        is Extension.Available -> extensionsScreenModel.installExtension(extension)
                        else -> {
                            if (context.isPackageInstalled(extension.pkgName)) {
                                extensionsScreenModel.uninstallExtension(extension)
                            } else {
                                privateExtensionToUninstall = extension
                            }
                        }
                    }
                },
                onClickItemCancel = extensionsScreenModel::cancelInstallUpdateExtension,
                onClickUpdateAll = extensionsScreenModel::updateAllExtensions,
                onOpenWebView = { extension ->
                    extension.sources.getOrNull(0)?.let {
                        navigator.push(
                            WebViewScreen(
                                url = it.baseUrl,
                                initialTitle = it.name,
                                sourceId = it.id,
                            ),
                        )
                    }
                },
                onInstallExtension = extensionsScreenModel::installExtension,
                onOpenExtension = { navigator.push(ExtensionDetailsScreen(it.pkgName)) },
                onTrustExtension = { extensionsScreenModel.trustExtension(it) },
                onUninstallExtension = { extensionsScreenModel.uninstallExtension(it) },
                onUpdateExtension = extensionsScreenModel::updateExtension,
                onRefresh = extensionsScreenModel::findAvailableExtensions,
                // KMK -->
                onToggleExtensionSelected = extensionsScreenModel::toggleExtensionSelected,
                onRequestUninstallSelected = { showBulkUninstallConfirmDialog = true },
                onRequestExportSelected = { showBulkExportConfirmDialog = true },
                onExitSelectionMode = extensionsScreenModel::exitExtensionSelectionMode,
                // KMK <--
            )

            privateExtensionToUninstall?.let { extension ->
                ExtensionUninstallConfirmation(
                    // KMK -->
                    extensionName = if (rememberEvaluationModeEnabled()) {
                        EvaluationModeFormatter.sourceLabel(extension.pkgName)
                    } else {
                        extension.name
                    },
                    // KMK <--
                    onClickConfirm = {
                        extensionsScreenModel.uninstallExtension(extension)
                    },
                    onDismissRequest = {
                        privateExtensionToUninstall = null
                    },
                )
            }

            // KMK -->
            if (showBulkUninstallConfirmDialog) {
                val selectedCount = state.selectedExtensionKeys.size
                ExtensionBulkUninstallConfirmation(
                    count = selectedCount,
                    onClickConfirm = {
                        extensionsScreenModel.uninstallSelectedExtensions()
                        showBulkUninstallConfirmDialog = false
                    },
                    onDismissRequest = {
                        showBulkUninstallConfirmDialog = false
                    },
                )
            }
            // KMK v0.8.18: manual extension APK export confirmation
            if (showBulkExportConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showBulkExportConfirmDialog = false },
                    title = { Text(stringResource(KMR.strings.extension_export_confirm_title)) },
                    text = { Text(stringResource(KMR.strings.extension_export_confirm_body)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showBulkExportConfirmDialog = false
                                // KMK:
                                // reserve the operation before the picker launches; if another
                                // operation is already pending, do not launch a second picker.
                                val operationId = extensionsScreenModel.bulkExportCoordinator.beginOperation()
                                if (operationId == null) {
                                    context.toast(KMR.strings.saf_export_operation_pending)
                                } else {
                                    // KMK
                                    // Phase 1: snapshot the selection here, at the confirm click -- the
                                    // last point before the external picker boundary -- not inside the
                                    // launcher callback after the picker returns.
                                    selectedExtensionsForExport = state.items.values.flatten()
                                        .map { it.extension }
                                        .filterIsInstance<Extension.Installed>()
                                        .filter { "${it.pkgName}_${it.signatureHash}" in state.selectedExtensionKeys }
                                    bulkExportPendingOperationId = operationId
                                    bulkExportLauncher.launch(eu.kanade.tachiyomi.extension.util.ExtensionApkExporter.suggestedZipFileName())
                                }
                            },
                        ) {
                            Text(stringResource(KMR.strings.extension_export_confirm_action))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBulkExportConfirmDialog = false }) {
                            Text(stringResource(MR.strings.action_cancel))
                        }
                    },
                )
            }
            // KMK: cleanup is
            // offered for the exact SAF `Uri` the system picker returned, for ANY outcome that leaves
            // a real document behind -- never automatic. No extension/package/repository name or
            // filesystem path is ever shown; the dialog is fully generic.
            bulkExportCleanupOffer?.let { offer ->
                SafArtifactCleanupDialog(
                    context = context,
                    offer = offer,
                    successTitleRes = KMR.strings.extension_export_cleanup_bulk_title,
                    successBodyRes = KMR.strings.extension_export_cleanup_bulk_body,
                    incompleteTitleRes = KMR.strings.extension_export_cleanup_bulk_incomplete_title,
                    incompleteBodyRes = KMR.strings.extension_export_cleanup_bulk_incomplete_body,
                    removeRes = KMR.strings.extension_export_cleanup_remove,
                    keepRes = KMR.strings.extension_export_cleanup_keep,
                    removedRes = KMR.strings.extension_export_cleanup_removed,
                    removeFailedRes = KMR.strings.extension_export_cleanup_failed,
                    onRemoved = { extensionsScreenModel.bulkExportCoordinator.clear(offer.operationId) },
                    onKept = { extensionsScreenModel.bulkExportCoordinator.clear(offer.operationId) },
                    onDismissed = { extensionsScreenModel.bulkExportCoordinator.clear(offer.operationId) },
                )
            }
            // KMK <--
        },
    )
}

// KMK -->
@Composable
private fun ExtensionBulkUninstallConfirmation(
    count: Int,
    onClickConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        title = {
            Text(text = stringResource(KMR.strings.extension_uninstall_selected_title))
        },
        text = {
            Text(text = stringResource(KMR.strings.extension_uninstall_selected_message, count))
        },
        confirmButton = {
            TextButton(onClick = onClickConfirm) {
                Text(text = stringResource(MR.strings.ext_uninstall))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        onDismissRequest = onDismissRequest,
    )
}
// KMK <--

@Composable
private fun ExtensionUninstallConfirmation(
    extensionName: String,
    onClickConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        title = {
            Text(text = stringResource(MR.strings.ext_confirm_remove))
        },
        text = {
            Text(text = stringResource(MR.strings.remove_private_extension_message, extensionName))
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onClickConfirm()
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.ext_remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        onDismissRequest = onDismissRequest,
    )
}

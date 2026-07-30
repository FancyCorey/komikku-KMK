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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.ExtensionScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.more.settings.screen.browse.ExtensionStoresScreen
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.util.ExtensionApkExporter
import eu.kanade.tachiyomi.ui.browse.extension.details.ExtensionDetailsScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.isPackageInstalled
import eu.kanade.tachiyomi.util.system.toast
import exh.recs.KmkRecsReleaseNotes
import exh.util.EvaluationModeFormatter
import exh.util.rememberEvaluationModeEnabled
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.core.common.i18n.stringResource as contextStringResource

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
    val scope = rememberCoroutineScope()
    val bulkExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val selected = state.items.values.flatten()
            .map { it.extension }
            .filterIsInstance<Extension.Installed>()
            .filter { "${it.pkgName}_${it.signatureHash}" in state.selectedExtensionKeys }
        if (selected.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val result = ExtensionApkExporter.exportMultiple(
                context = context,
                extensions = selected,
                destUri = uri,
                appVersion = eu.kanade.tachiyomi.BuildConfig.VERSION_NAME,
                kmkVersion = KmkRecsReleaseNotes.VERSION_NAME,
            )
            result.fold(
                onSuccess = { summary ->
                    val message = if (summary.skippedPkgNames.isEmpty()) {
                        context.contextStringResource(KMR.strings.extension_export_multi_success, summary.exportedCount)
                    } else {
                        context.contextStringResource(
                            KMR.strings.extension_export_multi_partial,
                            summary.exportedCount,
                            summary.skippedPkgNames.size,
                        )
                    }
                    context.toast(message)
                },
                onFailure = { context.toast(context.contextStringResource(KMR.strings.extension_export_failed)) },
            )
            extensionsScreenModel.exitExtensionSelectionMode()
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
                title = stringResource(MR.strings.label_extension_repos),
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
                                bulkExportLauncher.launch(eu.kanade.tachiyomi.extension.util.ExtensionApkExporter.suggestedZipFileName())
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

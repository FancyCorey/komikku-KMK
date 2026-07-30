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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.ExtensionDetailsScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.extension.util.ExtensionApkExporter
import eu.kanade.tachiyomi.extension.util.ExtensionInstaller
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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
        // KMK <--

        // KMK v0.8.18: manual extension APK export -- confirmation dialog, then SAF document picker,
        // then a background copy of the raw installed APK/archive bytes. Never repackages/re-signs.
        var showExportConfirm by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val exportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument(ExtensionInstaller.APK_MIME),
        ) { uri ->
            val extension = state.extension
            if (uri == null || extension == null) return@rememberLauncherForActivityResult
            scope.launch {
                when (ExtensionApkExporter.exportSingle(context, extension, uri)) {
                    ExtensionApkExporter.ExportResult.Success ->
                        context.toast(context.stringResource(KMR.strings.extension_export_success))
                    ExtensionApkExporter.ExportResult.SourceFileMissing ->
                        context.toast(context.stringResource(KMR.strings.extension_export_source_missing))
                    is ExtensionApkExporter.ExportResult.WriteFailed ->
                        context.toast(context.stringResource(KMR.strings.extension_export_failed))
                }
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
                            initialTitle = source.name,
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
                                exportLauncher.launch(ExtensionApkExporter.suggestedApkFileName(extension))
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

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                if (event is ExtensionDetailsEvent.Uninstalled) {
                    navigator.pop()
                }
            }
        }
    }
}

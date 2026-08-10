package eu.kanade.tachiyomi.ui.browse.extension.details

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.extension.interactor.ExtensionSourceItem
import eu.kanade.domain.extension.interactor.GetExtensionSources
import eu.kanade.domain.source.interactor.ToggleIncognito
import eu.kanade.domain.source.interactor.ToggleSource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.util.ExtensionApkExporter
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.export.SafArtifactOutcome
import eu.kanade.tachiyomi.util.export.SafExportCoordinator
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import okhttp3.HttpUrl.Companion.toHttpUrl
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ExtensionDetailsScreenModel(
    pkgName: String,
    context: Context,
    private val network: NetworkHelper = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val getExtensionSources: GetExtensionSources = Injekt.get(),
    private val toggleSource: ToggleSource = Injekt.get(),
    private val toggleIncognito: ToggleIncognito = Injekt.get(),
    private val preferences: SourcePreferences = Injekt.get(),
    // KMK Confirmed Blocker Remediation Corrective Completion Plan V3 2026-07-29 (post-report follow-up):
    // uninstallExtension()'s verification coroutine previously used the top-level `launchIO {}` extension,
    // hardcoded to real Dispatchers.IO with no injection seam -- the same class of gap already fixed for
    // ExtensionsScreenModel/BestVersionCompareScreenModel in the V2/V3 dispatcher-injection passes, but
    // left unfixed here because this pass's own scope was only "add a direct test," not a dispatcher
    // refactor. That forced the test to poll real wall-clock time with a short bound instead of proving
    // the real 10s production timeout via virtual time. Injectable now, defaulting to the exact same
    // Dispatchers.IO in production.
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : StateScreenModel<ExtensionDetailsScreenModel.State>(State()) {

    private val _events: Channel<ExtensionDetailsEvent> = Channel()
    val events: Flow<ExtensionDetailsEvent> = _events.receiveAsFlow()

    // KMK_CLAUDE_CORRECTIVE_COMPLETION_PLAN_2026-08-03 second corrective re-pass (finding #2):
    // screenModelScope-owned, not Composable-`remember`-owned -- survives recomposition/navigation
    // for as long as this screen model stays alive on the back stack.
    // A successful export is removable only in the debug fixture build. Release-derived builds
    // retain the established behavior: a successful user export is never offered for deletion.
    val exportCoordinator = SafExportCoordinator(allowSuccessfulRemoval = BuildConfig.DEBUG)

    /**
     * [extension] is captured by the caller (ExtensionDetailsScreen's confirm-dialog onClick) at the
     * moment the export is requested, before the picker opens -- never re-read from live state here.
     * [operationId] must come from a prior successful [SafExportCoordinator.beginOperation] call made
     * by the caller before the picker was launched. Returns `false` if [operationId] could not be
     * registered (defensive; should not happen in normal operation -- see
     * [SafExportCoordinator]'s KDoc) so the caller can fall back to a truthful cleanup path for [uri]
     * instead of silently discarding it.
     */
    fun exportSingleExtension(context: Context, operationId: String, uri: Uri, extension: Extension.Installed?): Boolean {
        if (!exportCoordinator.registerUri(operationId, uri)) return false
        screenModelScope.launch {
            exportCoordinator.performWrite(operationId) {
                if (extension == null) {
                    SafArtifactOutcome.PARTIAL_OR_EMPTY
                } else {
                    when (val result = ExtensionApkExporter.exportSingle(context, extension, uri)) {
                        ExtensionApkExporter.ExportResult.Success -> {
                            context.toast(context.stringResource(KMR.strings.extension_export_success))
                            SafArtifactOutcome.SUCCESS
                        }
                        ExtensionApkExporter.ExportResult.SourceFileMissing -> {
                            context.toast(context.stringResource(KMR.strings.extension_export_source_missing))
                            SafArtifactOutcome.PARTIAL_OR_EMPTY
                        }
                        is ExtensionApkExporter.ExportResult.WriteFailed -> {
                            context.toast(context.stringResource(KMR.strings.extension_export_failed))
                            SafArtifactOutcome.FAILED
                        }
                    }
                }
            }
        }
        return true
    }

    init {
        screenModelScope.launch {
            launch {
                extensionManager.installedExtensionsFlow
                    .map { it.firstOrNull { extension -> extension.pkgName == pkgName } }
                    .collectLatest { extension ->
                        if (extension == null) {
                            _events.send(ExtensionDetailsEvent.Uninstalled)
                            return@collectLatest
                        }
                        mutableState.update { state ->
                            state.copy(extension = extension)
                        }
                    }
            }
            launch {
                state.collectLatest { state ->
                    if (state.extension == null) return@collectLatest
                    getExtensionSources.subscribe(state.extension)
                        .map {
                            it.sortedWith(
                                compareBy(
                                    { !it.enabled },
                                    { item ->
                                        item.source.name.takeIf { item.labelAsName }
                                            ?: LocaleHelper.getSourceDisplayName(item.source.lang, context).lowercase()
                                    },
                                ),
                            )
                        }
                        .catch {
                            logcat(LogPriority.ERROR) { "Failed to load extension source list" }
                            mutableState.update { it.copy(_sources = persistentListOf()) }
                        }
                        .collectLatest { sources ->
                            mutableState.update { it.copy(_sources = sources.toImmutableList()) }
                        }
                }
            }
            launch {
                preferences.incognitoExtensions()
                    .changes()
                    .map { pkgName in it }
                    .distinctUntilChanged()
                    .collectLatest { isIncognito ->
                        mutableState.update { it.copy(isIncognito = isIncognito) }
                    }
            }
        }
    }

    fun clearCookies() {
        val extension = state.value.extension ?: return

        val urls = extension.sources
            .filterIsInstance<HttpSource>()
            .flatMap { listOf(it.baseUrl, it.getHomeUrl()) }
            .filter { it.isNotEmpty() }
            .distinct()

        val cleared = urls.sumOf {
            try {
                network.cookieJar.remove(it.toHttpUrl())
            } catch (_: Exception) {
                logcat(LogPriority.ERROR) { "Failed to clear extension cookies" }
                0
            }
        }

        logcat { "Extension cookies cleared: $cleared" }
    }

    fun uninstallExtension() {
        val extension = state.value.extension ?: return
        extensionManager.uninstallExtension(extension)
        // KMK Confirmed Blocker Remediation Corrective Completion Plan V2 2026-07-29: this screen is a
        // separate uninstall call site from ExtensionsScreenModel.uninstallExtension() -- without this,
        // uninstalling from Extension Details produced no Action History entry and no
        // PackageOperationReceipt (no verified-removal confirmation, no reinstall follow-up eligibility),
        // unlike every other uninstall path in the app.
        screenModelScope.launch(ioDispatcher) {
            exh.util.verifyAndRecordUninstall(
                installedPackageNames = extensionManager.installedExtensionsFlow.map { installed -> installed.map { it.pkgName } },
                pkgName = extension.pkgName,
                isEvaluationModeEnabled = { preferences.evaluationMode().get() },
                signatureHash = extension.signatureHash,
                versionCode = extension.versionCode,
            )
        }
    }

    fun toggleSource(sourceId: Long) {
        toggleSource.await(sourceId)
    }

    fun toggleSources(enable: Boolean) {
        state.value.extension?.sources
            ?.map { it.id }
            ?.let { toggleSource.await(it, enable) }
    }

    fun toggleIncognito(enable: Boolean) {
        state.value.extension?.pkgName?.let { packageName ->
            toggleIncognito.await(packageName, enable)
        }
    }

    @Immutable
    data class State(
        val extension: Extension.Installed? = null,
        val isIncognito: Boolean = false,
        private val _sources: ImmutableList<ExtensionSourceItem>? = null,
    ) {

        val sources: ImmutableList<ExtensionSourceItem>
            get() = _sources ?: persistentListOf()

        val isLoading: Boolean
            get() = extension == null || _sources == null
    }
}

sealed interface ExtensionDetailsEvent {
    data object Uninstalled : ExtensionDetailsEvent
}

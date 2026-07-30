package eu.kanade.tachiyomi.ui.browse.extension

import android.app.Application
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.extension.interactor.GetExtensionsByType
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.system.LocaleHelper
import exh.util.NonUndoableEventType
import exh.util.PackageOperationKind
import exh.util.recordPackageOperationReceipt
import exh.util.recordUserInitiatedInstall
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

class ExtensionsScreenModel(
    private val preferences: SourcePreferences = Injekt.get(),
    basePreferences: BasePreferences = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val getExtensions: GetExtensionsByType = Injekt.get(),
    // KMK Confirmed Blocker Remediation Corrective Completion Plan V2 2026-07-29: every background
    // operation in this class previously used the top-level launchIO {} extension, which is hardcoded
    // to the real Dispatchers.IO with no way for a test to redirect it (unlike Dispatchers.Main, which
    // kotlinx-coroutines-test can swap via Dispatchers.setMain()). Injecting the dispatcher here --
    // defaulting to the exact same Dispatchers.IO in production -- lets tests substitute a deterministic
    // TestDispatcher while every real caller keeps identical behavior.
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : StateScreenModel<ExtensionsScreenModel.State>(State()) {

    private val currentDownloads = MutableStateFlow<Map<String, InstallStep>>(hashMapOf())

    init {
        val context = Injekt.get<Application>()
        val extensionMapper: (Map<String, InstallStep>) -> ((Extension) -> ExtensionUiModel.Item) = { map ->
            {
                ExtensionUiModel.Item(
                    it,
                    map[
                        it.pkgName +
                            // KMK -->
                            "_${it.signatureHash}",
                        // KMK <--
                    ] ?: InstallStep.Idle,
                )
            }
        }

        screenModelScope.launch(ioDispatcher) {
            combine(
                state.map { it.searchQuery }
                    .distinctUntilChanged()
                    .debounce(SEARCH_DEBOUNCE_MILLIS)
                    .map { searchQueryPredicate(it ?: "") },
                // KMK -->
                state.map { it.nsfwOnly }
                    .distinctUntilChanged()
                    .debounce(SEARCH_DEBOUNCE_MILLIS),
                // KMK <--
                currentDownloads,
                getExtensions.subscribe(),
            ) { predicate, nsfwOnly, downloads, (_updates, _installed, _available, _untrusted) ->
                buildMap {
                    val updates = _updates.filter(predicate).map(extensionMapper(downloads))
                        // KMK -->
                        .filter { !nsfwOnly || it.extension.isNsfw }
                    // KMK <--
                    if (updates.isNotEmpty()) {
                        put(ExtensionUiModel.Header.Resource(MR.strings.ext_updates_pending), updates)
                    }

                    val installed = _installed.filter(predicate).map(extensionMapper(downloads))
                        // KMK -->
                        .filter { !nsfwOnly || it.extension.isNsfw }
                    // KMK <--
                    val untrusted = _untrusted.filter(predicate).map(extensionMapper(downloads))
                        // KMK -->
                        .filter { !nsfwOnly || it.extension.isNsfw }
                    // KMK <--
                    if (installed.isNotEmpty() || untrusted.isNotEmpty()) {
                        put(ExtensionUiModel.Header.Resource(MR.strings.ext_installed), installed + untrusted)
                    }

                    val languagesWithExtensions = _available
                        .filter(predicate)
                        // KMK -->
                        .filter { !nsfwOnly || it.isNsfw }
                        // KMK <--
                        .groupBy { it.lang }
                        .toSortedMap(LocaleHelper.comparator)
                        .map { (lang, exts) ->
                            ExtensionUiModel.Header.Text(LocaleHelper.getSourceDisplayName(lang, context)) to
                                exts.map(extensionMapper(downloads))
                        }
                    if (languagesWithExtensions.isNotEmpty()) {
                        putAll(languagesWithExtensions)
                    }

                    // KMK -->
                    // Show "More..." header if no available extensions
                    if (_available.isEmpty()) {
                        put(ExtensionUiModel.Header.Resource(KMR.strings.extensions_page_more), emptyList())
                    }
                    // KMK <--
                }
            }
                .collectLatest { items ->
                    mutableState.update { state ->
                        state.copy(
                            isLoading = false,
                            items = items,
                        )
                    }
                }
        }

        screenModelScope.launch(ioDispatcher) { findAvailableExtensions() }

        preferences.extensionUpdatesCount().changes()
            .onEach { mutableState.update { state -> state.copy(updates = it) } }
            .launchIn(screenModelScope)

        basePreferences.extensionInstaller().changes()
            .onEach { mutableState.update { state -> state.copy(installer = it) } }
            .launchIn(screenModelScope)
    }

    fun searchQueryPredicate(query: String): (Extension) -> Boolean {
        val subqueries = query.split(",")
            .map { it.trim() }
            .filterNot { it.isBlank() }

        if (subqueries.isEmpty()) return { true }

        return { extension ->
            subqueries.any { subquery ->
                if (extension.name.contains(subquery, ignoreCase = true)) return@any true

                when (extension) {
                    is Extension.Installed -> extension.sources.any { source ->
                        source.name.contains(subquery, ignoreCase = true) ||
                            (source as? HttpSource)?.getHomeUrl()?.contains(subquery, ignoreCase = true) == true ||
                            source.id == subquery.toLongOrNull()
                    }

                    is Extension.Available -> extension.sources.any {
                        it.name.contains(subquery, ignoreCase = true) ||
                            it.baseUrl.contains(subquery, ignoreCase = true) ||
                            it.id == subquery.toLongOrNull()
                    }

                    else -> false
                }
            }
        }
    }

    fun search(query: String?) {
        mutableState.update {
            it.copy(searchQuery = query)
        }
    }

    fun updateAllExtensions() {
        screenModelScope.launch(ioDispatcher) {
            state.value.items.values.flatten()
                .map { it.extension }
                .filterIsInstance<Extension.Installed>()
                .filter { it.hasUpdate }
                .forEach(::updateExtension)
        }
    }

    fun installExtension(extension: Extension.Available) {
        screenModelScope.launch(ioDispatcher) {
            // KMK Confirmed Blocker Remediation Corrective Completion Plan V2 2026-07-29: one shared id
            // for both the visibility-only NonUndoableEvent and the private PackageOperationReceipt --
            // EvaluationModeActionHistoryScreen correlates the two by this id to decide whether a
            // follow-up action can be offered for this row.
            val receiptId = exh.util.NonUndoableEvent.newId()
            extensionManager.installExtension(extension)
                .recordUserInitiatedInstall(id = receiptId) { preferences.evaluationMode().get() }
                .recordPackageOperationReceipt(
                    kind = PackageOperationKind.INSTALL,
                    packageName = extension.pkgName,
                    signatureHash = extension.signatureHash,
                    versionCode = extension.versionCode,
                    artifactUri = extension.apkUrl,
                    id = receiptId,
                ) { preferences.evaluationMode().get() }
                .collectToInstallUpdate(extension)
        }
    }

    // KMK Confirmed Blocker Remediation Corrective Completion Plan V2 2026-07-29: previously reused
    // recordUserInitiatedInstall()'s default EXTENSION_INSTALLED event type unmodified, so every
    // update from this screen was misrecorded in Action History as a fresh install rather than an
    // update. Now passes EXTENSION_UPDATED explicitly.
    fun updateExtension(extension: Extension.Installed) {
        screenModelScope.launch(ioDispatcher) {
            val availableExt = extensionManager.getAvailableExtension(extension)
            val receiptId = exh.util.NonUndoableEvent.newId()
            extensionManager.updateExtension(extension)
                .recordUserInitiatedInstall(eventType = NonUndoableEventType.EXTENSION_UPDATED, id = receiptId) {
                    preferences.evaluationMode().get()
                }
                .recordPackageOperationReceipt(
                    kind = PackageOperationKind.UPDATE,
                    packageName = extension.pkgName,
                    signatureHash = availableExt?.signatureHash ?: extension.signatureHash,
                    versionCode = availableExt?.versionCode,
                    artifactUri = availableExt?.apkUrl,
                    id = receiptId,
                ) { preferences.evaluationMode().get() }
                .collectToInstallUpdate(extension)
        }
    }

    fun cancelInstallUpdateExtension(extension: Extension) {
        extensionManager.cancelInstallUpdateExtension(extension)
        removeDownloadState(extension)
    }

    private fun addDownloadState(extension: Extension, installStep: InstallStep) {
        currentDownloads.update {
            it + Pair(
                extension.pkgName +
                    // KMK -->
                    "_${extension.signatureHash}",
                // KMK <--
                installStep,
            )
        }
    }

    private fun removeDownloadState(extension: Extension) {
        currentDownloads.update {
            it - (
                extension.pkgName +
                    // KMK -->
                    "_${extension.signatureHash}"
                // KMK <--
                )
        }
    }

    private suspend fun Flow<InstallStep>.collectToInstallUpdate(extension: Extension) =
        this
            .onEach { installStep -> addDownloadState(extension, installStep) }
            .takeWhile { installStep -> installStep != InstallStep.Installed }
            .onCompletion { removeDownloadState(extension) }
            .collect()

    // KMK Confirmed Blocker Remediation Corrective Pass 2026-07-29: previously called
    // extensionManager.uninstallExtension() (fire-and-forget, no completion signal -- see
    // ExtensionManager.uninstallExtension()'s own doc) and recorded nothing at all. This is the
    // main Browse > Extensions uninstall path, distinct from SourceEvaluationScreenModel's
    // runtime-health uninstall which already used exh.util.verifyAndRecordUninstall -- that fix
    // never covered this call site. Reuses the same verified-removal-before-recording contract.
    fun uninstallExtension(extension: Extension) {
        extensionManager.uninstallExtension(extension)
        screenModelScope.launch(ioDispatcher) {
            exh.util.verifyAndRecordUninstall(
                installedPackageNames = extensionManager.installedExtensionsFlow.map { installed -> installed.map { it.pkgName } },
                pkgName = extension.pkgName,
                isEvaluationModeEnabled = { preferences.evaluationMode().get() },
                // KMK Confirmed Blocker Remediation Corrective Completion Plan V2 2026-07-29: signature/
                // version recorded so a later reinstall follow-up can confirm the currently-available
                // catalogue entry (resolved live, not stored here) still matches what was uninstalled.
                signatureHash = extension.signatureHash,
                versionCode = extension.versionCode,
            )
        }
    }

    fun findAvailableExtensions() {
        screenModelScope.launch(ioDispatcher) {
            mutableState.update { it.copy(isRefreshing = true) }

            extensionManager.findAvailableExtensions()

            // Fake slower refresh so it doesn't seem like it's not doing anything
            delay(1.seconds)

            mutableState.update { it.copy(isRefreshing = false) }
        }
    }

    fun trustExtension(extension: Extension.Untrusted) {
        screenModelScope.launch {
            extensionManager.trust(extension)
        }
    }

    // KMK -->
    fun toggleNsfwOnly() {
        mutableState.update {
            it.copy(nsfwOnly = !it.nsfwOnly)
        }
    }

    private fun Extension.selectionKey(): String = pkgName + "_$signatureHash"

    fun enterExtensionSelectionMode() {
        mutableState.update { it.copy(isExtensionSelectionMode = true) }
    }

    fun exitExtensionSelectionMode() {
        mutableState.update { it.copy(isExtensionSelectionMode = false, selectedExtensionKeys = emptySet()) }
    }

    fun toggleExtensionSelected(extension: Extension) {
        if (extension !is Extension.Installed && extension !is Extension.Untrusted) return
        val key = extension.selectionKey()
        mutableState.update { s ->
            val keys = s.selectedExtensionKeys
            s.copy(selectedExtensionKeys = if (key in keys) keys - key else keys + key)
        }
    }

    fun uninstallSelectedExtensions() {
        if (state.value.isBulkUninstallingExtensions) return
        val selectedKeys = state.value.selectedExtensionKeys
        val toUninstall = state.value.items.values.flatten()
            .filter { item ->
                (item.extension is Extension.Installed || item.extension is Extension.Untrusted) &&
                    item.extension.selectionKey() in selectedKeys &&
                    item.installStep.isCompleted()
            }
            .map { it.extension }
        if (toUninstall.isEmpty()) return
        mutableState.update { it.copy(isBulkUninstallingExtensions = true) }
        screenModelScope.launch(ioDispatcher) {
            try {
                for (extension in toUninstall) {
                    uninstallExtension(extension)
                    // Small delay between uninstall intents to avoid rapid-fire Android prompt stacking.
                    // Android may show one confirmation dialog per extension.
                    delay(300L)
                }
            } finally {
                mutableState.update {
                    it.copy(
                        isBulkUninstallingExtensions = false,
                        isExtensionSelectionMode = false,
                        selectedExtensionKeys = emptySet(),
                    )
                }
            }
        }
    }
    // KMK <--

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val items: ItemGroups = mutableMapOf(),
        val updates: Int = 0,
        val installer: BasePreferences.ExtensionInstaller? = null,
        val searchQuery: String? = null,
        // KMK -->
        val nsfwOnly: Boolean = false,
        val isExtensionSelectionMode: Boolean = false,
        val selectedExtensionKeys: Set<String> = emptySet(),
        val isBulkUninstallingExtensions: Boolean = false,
        // KMK <--
    ) {
        val isEmpty = items.isEmpty()
    }
}

typealias ItemGroups = Map<ExtensionUiModel.Header, List<ExtensionUiModel.Item>>

object ExtensionUiModel {
    sealed interface Header {
        data class Resource(val textRes: StringResource) : Header
        data class Text(val text: String) : Header
    }

    data class Item(
        val extension: Extension,
        val installStep: InstallStep,
    )
}

package exh.recs.share

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.source.model.SManga
import exh.recs.RecommendationErrorClassifier
import exh.util.PackageOperationKind
import exh.util.recordPackageOperationReceipt
import exh.util.recordUserInitiatedInstall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

sealed interface LoadErrorKey {
    data class WrongSchema(val found: String) : LoadErrorKey
    data class UnsupportedVersion(val found: Int) : LoadErrorKey
    data object TooManyItems : LoadErrorKey
    data object TooManySources : LoadErrorKey
    data object FileTooLarge : LoadErrorKey
    data class MalformedJson(val detail: String?) : LoadErrorKey
}

sealed interface RecommendationImportItemState {
    data class ReadyToAdd(val localManga: Manga? = null) : RecommendationImportItemState
    data class AlreadyInLibrary(val localManga: Manga) : RecommendationImportItemState
    data class MissingSource(val availableExt: Extension.Available? = null) : RecommendationImportItemState
    data class AmbiguousSource(val candidates: List<Extension.Available>) : RecommendationImportItemState
    data class SourceInstalledNeedsResolve(val resolvedSourceId: Long) : RecommendationImportItemState
    data object NeedsManualMatch : RecommendationImportItemState
    data object Unsupported : RecommendationImportItemState
    data class Error(val message: String) : RecommendationImportItemState
}

@Immutable
data class ImportItemEntry(
    val bundleItem: RecommendationBundleItem,
    val itemState: RecommendationImportItemState,
    val localManga: Manga? = null,
)

data class AddSummary(val added: Int, val alreadyInLibrary: Int, val failed: Int)

class RecommendationBundleImportScreenModel(
    private val uriString: String,
    private val context: Context,
    private val sourceManager: SourceManager = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val libraryAdder: RecommendationBundleLibraryAdder = RecommendationBundleLibraryAdder(),
) : StateScreenModel<RecommendationBundleImportScreenModel.State>(State.Loading) {

    sealed interface State {
        data object Loading : State
        data class LoadError(val error: LoadErrorKey) : State

        @Immutable
        data class Preview(
            val bundle: RecommendationBundle,
            val items: List<ImportItemEntry>,
            val selectedIndices: Set<Int> = items.indices.filter { idx ->
                items[idx].itemState is RecommendationImportItemState.ReadyToAdd ||
                    items[idx].itemState is RecommendationImportItemState.SourceInstalledNeedsResolve
            }.toSet(),
            val isAdding: Boolean = false,
            val addSummary: AddSummary? = null,
            val installingPkgName: String? = null,
        ) : State {
            val selectableCount: Int get() = items.count { entry ->
                entry.itemState is RecommendationImportItemState.ReadyToAdd ||
                    entry.itemState is RecommendationImportItemState.SourceInstalledNeedsResolve
            }
        }
    }

    init {
        screenModelScope.launch { load() }
    }

    private suspend fun load() {
        mutableState.value = State.Loading
        val readResult = RecommendationBundleImporter.readFromUri(context, Uri.parse(uriString))
        val bundle = when (val v = readResult.validation) {
            is RecommendationBundleValidator.ValidationResult.Valid -> v.bundle
            is RecommendationBundleValidator.ValidationResult.WrongSchema ->
                run {
                    mutableState.value = State.LoadError(LoadErrorKey.WrongSchema(v.found))
                    return
                }
            is RecommendationBundleValidator.ValidationResult.UnsupportedVersion ->
                run {
                    mutableState.value = State.LoadError(LoadErrorKey.UnsupportedVersion(v.found))
                    return
                }
            RecommendationBundleValidator.ValidationResult.TooManyItems ->
                run {
                    mutableState.value = State.LoadError(LoadErrorKey.TooManyItems)
                    return
                }
            RecommendationBundleValidator.ValidationResult.TooManySources ->
                run {
                    mutableState.value = State.LoadError(LoadErrorKey.TooManySources)
                    return
                }
            RecommendationBundleValidator.ValidationResult.FileTooLarge ->
                run {
                    mutableState.value = State.LoadError(LoadErrorKey.FileTooLarge)
                    return
                }
            is RecommendationBundleValidator.ValidationResult.MalformedJson ->
                run {
                    mutableState.value = State.LoadError(LoadErrorKey.MalformedJson(v.message.ifBlank { null }))
                    return
                }
        }

        val items = resolveItems(bundle)
        val defaultSelected = items.indices.filter { idx ->
            val s = items[idx].itemState
            s is RecommendationImportItemState.ReadyToAdd ||
                s is RecommendationImportItemState.SourceInstalledNeedsResolve
        }.toSet()

        mutableState.value = State.Preview(bundle = bundle, items = items, selectedIndices = defaultSelected)
    }

    private suspend fun resolveItems(bundle: RecommendationBundle): List<ImportItemEntry> {
        val installedSnapshots = buildInstalledSnapshots()
        return bundle.items.map { item ->
            try {
                resolveItem(item, installedSnapshots)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN) { "KMK recommendation bundle: failed to resolve imported item" }
                ImportItemEntry(item, RecommendationImportItemState.Error(RecommendationErrorClassifier.classifyToStorageKey(e)))
            }
        }
    }

    private suspend fun resolveItem(
        item: RecommendationBundleItem,
        installedSnapshots: List<RecommendationBundleSourceResolver.InstalledSourceSnapshot>,
    ): ImportItemEntry {
        // Local source (id=0) cannot be exported to other devices: unsupported
        if (item.sourceId == 0L) {
            return ImportItemEntry(item, RecommendationImportItemState.Unsupported)
        }

        val resolved = RecommendationBundleSourceResolver.resolveSource(item, installedSnapshots)

        return when (resolved) {
            is RecommendationBundleSourceResolver.ResolvedSource.Missing -> {
                val resolution = RecommendationBundleSourceResolver.resolveAvailableExtension(
                    item,
                    extensionManager.availableExtensionsFlow.value,
                )
                val state = when (resolution) {
                    is RecommendationBundleSourceResolver.AvailableExtensionResolution.Unambiguous ->
                        RecommendationImportItemState.MissingSource(resolution.ext)
                    is RecommendationBundleSourceResolver.AvailableExtensionResolution.Ambiguous ->
                        RecommendationImportItemState.AmbiguousSource(resolution.candidates)
                    RecommendationBundleSourceResolver.AvailableExtensionResolution.NotFound ->
                        RecommendationImportItemState.MissingSource(null)
                }
                ImportItemEntry(item, state)
            }

            is RecommendationBundleSourceResolver.ResolvedSource.FoundExact,
            is RecommendationBundleSourceResolver.ResolvedSource.FoundByMetadata,
            -> {
                val resolvedSourceId = when (resolved) {
                    is RecommendationBundleSourceResolver.ResolvedSource.FoundExact -> resolved.sourceId
                    is RecommendationBundleSourceResolver.ResolvedSource.FoundByMetadata -> resolved.sourceId
                    else -> item.sourceId
                }

                // Check if manga is already in local DB
                val localManga = getManga.await(item.url, resolvedSourceId)
                when {
                    localManga != null && localManga.favorite ->
                        ImportItemEntry(item, RecommendationImportItemState.AlreadyInLibrary(localManga), localManga)
                    localManga != null ->
                        ImportItemEntry(item, RecommendationImportItemState.ReadyToAdd(localManga), localManga)
                    else ->
                        ImportItemEntry(item, RecommendationImportItemState.SourceInstalledNeedsResolve(resolvedSourceId))
                }
            }
        }
    }

    private fun buildInstalledSnapshots(): List<RecommendationBundleSourceResolver.InstalledSourceSnapshot> =
        extensionManager.installedExtensionsFlow.value.flatMap { ext ->
            ext.sources.map { src ->
                RecommendationBundleSourceResolver.InstalledSourceSnapshot(
                    sourceId = src.id,
                    pkgName = ext.pkgName,
                    name = src.name,
                    lang = src.lang,
                    signatureHash = ext.signatureHash,
                )
            }
        }

    fun toggleSelection(index: Int) {
        val preview = mutableState.value as? State.Preview ?: return
        val entry = preview.items.getOrNull(index) ?: return
        val selectable = entry.itemState is RecommendationImportItemState.ReadyToAdd ||
            entry.itemState is RecommendationImportItemState.SourceInstalledNeedsResolve
        if (!selectable) return

        mutableState.update { s ->
            val p = s as? State.Preview ?: return@update s
            val newSelected = if (index in p.selectedIndices) {
                p.selectedIndices - index
            } else {
                p.selectedIndices + index
            }
            p.copy(selectedIndices = newSelected)
        }
    }

    fun selectAll() {
        mutableState.update { s ->
            val p = s as? State.Preview ?: return@update s
            val selectable = p.items.indices.filter { idx ->
                val state = p.items[idx].itemState
                state is RecommendationImportItemState.ReadyToAdd ||
                    state is RecommendationImportItemState.SourceInstalledNeedsResolve
            }.toSet()
            p.copy(selectedIndices = selectable)
        }
    }

    fun deselectAll() {
        mutableState.update { s ->
            val p = s as? State.Preview ?: return@update s
            p.copy(selectedIndices = emptySet())
        }
    }

    fun addSelected() {
        val preview = mutableState.value as? State.Preview ?: return
        if (preview.selectedIndices.isEmpty() || preview.isAdding) return

        screenModelScope.launch {
            mutableState.update { s -> (s as? State.Preview)?.copy(isAdding = true) ?: s }

            var added = 0
            var alreadyInLibrary = 0
            var failed = 0

            for (idx in preview.selectedIndices.sorted()) {
                val entry = preview.items.getOrNull(idx) ?: continue

                val manga: Manga? = when (val itemState = entry.itemState) {
                    is RecommendationImportItemState.ReadyToAdd ->
                        itemState.localManga
                            ?: entry.localManga
                    is RecommendationImportItemState.SourceInstalledNeedsResolve -> {
                        try {
                            val sManga = SManga.create().apply {
                                title = entry.bundleItem.title
                                url = entry.bundleItem.url
                                thumbnail_url = entry.bundleItem.thumbnailUrl
                                author = entry.bundleItem.author
                                artist = entry.bundleItem.artist
                                description = entry.bundleItem.description
                                genre = entry.bundleItem.genres.joinToString(", ").takeIf { it.isNotBlank() }
                                status = entry.bundleItem.status ?: SManga.UNKNOWN
                            }
                            networkToLocalManga(sManga.toDomainManga(itemState.resolvedSourceId))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            logcat(LogPriority.WARN) { "KMK recommendation bundle: imported item conversion failed" }
                            null
                        }
                    }
                    else -> null
                }

                if (manga == null) {
                    failed++
                    continue
                }

                val result = libraryAdder.addToLibrary(manga, skipDuplicates = true)
                when (result.outcome) {
                    is RecommendationBundleLibraryAdder.Outcome.Added -> added++
                    is RecommendationBundleLibraryAdder.Outcome.AlreadyFavorite -> alreadyInLibrary++
                    is RecommendationBundleLibraryAdder.Outcome.Duplicate -> alreadyInLibrary++
                    is RecommendationBundleLibraryAdder.Outcome.Error -> failed++
                }
                // KMK --> SEC-09 v0.7.16: throttle network requests during bulk import to avoid hammering sources
                delay(200)
                // KMK <--
            }

            mutableState.update { s ->
                (s as? State.Preview)?.copy(
                    isAdding = false,
                    addSummary = AddSummary(added, alreadyInLibrary, failed),
                ) ?: s
            }
        }
    }

    fun installMissingExtension(ext: Extension.Available) {
        val preview = mutableState.value as? State.Preview ?: return

        screenModelScope.launch {
            mutableState.update { s ->
                (s as? State.Preview)?.copy(installingPkgName = ext.pkgName) ?: s
            }

            try {
                val receiptId = exh.util.NonUndoableEvent.newId()
                extensionManager.installExtension(ext)
                    .recordUserInitiatedInstall(id = receiptId) { sourcePreferences.evaluationMode().get() }
                    // KMK: typed
                    // PackageOperationReceipt alongside the visibility-only event above.
                    .recordPackageOperationReceipt(
                        kind = PackageOperationKind.INSTALL,
                        packageName = ext.pkgName,
                        signatureHash = ext.signatureHash,
                        versionCode = ext.versionCode,
                        artifactUri = ext.apkUrl,
                        id = receiptId,
                    ) { sourcePreferences.evaluationMode().get() }
                    .collectLatest { step ->
                        if (step == InstallStep.Installed) {
                            // Re-resolve items after install
                            val currentPreview = mutableState.value as? State.Preview ?: return@collectLatest
                            val newItems = resolveItems(currentPreview.bundle)
                            mutableState.update { s ->
                                (s as? State.Preview)?.copy(
                                    items = newItems,
                                    installingPkgName = null,
                                    selectedIndices = newItems.indices.filter { idx ->
                                        val st = newItems[idx].itemState
                                        st is RecommendationImportItemState.ReadyToAdd ||
                                            st is RecommendationImportItemState.SourceInstalledNeedsResolve
                                    }.toSet(),
                                ) ?: s
                            }
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN) { "KMK recommendation bundle: extension installation failed" }
                mutableState.update { s ->
                    (s as? State.Preview)?.copy(installingPkgName = null) ?: s
                }
            }
        }
    }

    fun dismissSummary() {
        mutableState.update { s ->
            (s as? State.Preview)?.copy(addSummary = null) ?: s
        }
    }
}

// KMK <--

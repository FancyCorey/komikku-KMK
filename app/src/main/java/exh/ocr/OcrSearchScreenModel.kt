package exh.ocr

import android.content.Context
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK --> OCR v0.1.1 (updated from v0.1.0)

class OcrSearchScreenModel(
    private val context: Context,
    private val repository: OcrIndexRepository = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
) : StateScreenModel<OcrSearchScreenModel.State>(State()) {

    private val engineVersion = MlKitLatinOcrTextRecognizer.ENGINE_VERSION

    data class State(
        val query: String = "",
        val results: List<OcrSearchResult> = emptyList(),
        val isSearching: Boolean = false,
        val indexStats: OcrIndexStats = OcrIndexStats(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L),
        val indexProgress: OcrIndexProgress? = null,
        val isIndexRunning: Boolean = false,
        val showClearConfirm: Boolean = false,
        val showIndexAllConfirm: Boolean = false,
        val showForceReindexConfirm: Boolean = false,
        val maxPages: Int = 0,
        // KMK v0.7.46: typed key instead of raw exception text — OcrSearchScreen maps this to a
        // KMR string at render time.
        val errorKey: OcrErrorKey? = null,
    )

    private var searchJob: Job? = null

    init {
        screenModelScope.launchIO {
            OcrJobState.isRunning.collectLatest { running ->
                mutableState.update { it.copy(isIndexRunning = running) }
            }
        }
        screenModelScope.launchIO {
            OcrJobState.activeProgress.collectLatest { progress ->
                mutableState.update { it.copy(indexProgress = progress) }
                if (progress?.isComplete == true || progress?.isCancelled == true || progress?.isFailed == true) {
                    refreshStats()
                }
            }
        }
        refreshStats()
    }

    fun refreshStats() {
        screenModelScope.launchIO {
            try {
                val stats = repository.getStats(engineVersion)
                mutableState.update { it.copy(indexStats = stats) }
            } catch (_: Exception) {
                // non-fatal
            }
        }
    }

    fun onQueryChange(query: String) {
        mutableState.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            mutableState.update { it.copy(results = emptyList(), isSearching = false) }
            return
        }
        searchJob = screenModelScope.launchIO {
            mutableState.update { it.copy(isSearching = true) }
            try {
                val rawResults = repository.searchSmart(query, engineVersion)
                // Resolve source names
                val results = rawResults.map { result ->
                    val name = sourceManager.get(result.sourceId)?.name
                    result.copy(sourceName = name)
                }
                mutableState.update { it.copy(results = results, isSearching = false) }
            } catch (e: Exception) {
                mutableState.update { it.copy(isSearching = false, errorKey = OcrErrorClassifier.classify(e)) }
            }
        }
    }

    // Index all (retry empty/failed by default in v0.1.1)
    fun requestIndexAll() {
        mutableState.update { it.copy(showIndexAllConfirm = true) }
    }

    fun dismissIndexAllConfirm() {
        mutableState.update { it.copy(showIndexAllConfirm = false) }
    }

    fun startIndexAll() {
        mutableState.update { it.copy(showIndexAllConfirm = false) }
        if (!OcrIndexWorker.isRunning(context)) {
            OcrIndexWorker.start(
                context,
                OcrScope.AllDownloaded,
                retryMode = OcrRetryMode.SKIP_SUCCESS_RETRY_EMPTY_FAILED,
                maxPages = state.value.maxPages,
            )
        }
    }

    // Force re-index all
    fun requestForceReindex() {
        mutableState.update { it.copy(showForceReindexConfirm = true) }
    }

    fun dismissForceReindexConfirm() {
        mutableState.update { it.copy(showForceReindexConfirm = false) }
    }

    fun startForceReindex() {
        mutableState.update { it.copy(showForceReindexConfirm = false) }
        if (!OcrIndexWorker.isRunning(context)) {
            OcrIndexWorker.start(
                context,
                OcrScope.AllDownloaded,
                retryMode = OcrRetryMode.FORCE_ALL,
                maxPages = state.value.maxPages,
            )
        }
    }

    fun cancelIndexing() {
        OcrIndexWorker.cancel(context)
    }

    // Page limit
    fun setMaxPages(limit: Int) {
        mutableState.update { it.copy(maxPages = limit) }
    }

    // Clear index
    fun requestClearIndex() {
        mutableState.update { it.copy(showClearConfirm = true) }
    }

    fun dismissClearConfirm() {
        mutableState.update { it.copy(showClearConfirm = false) }
    }

    fun clearIndex() {
        mutableState.update { it.copy(showClearConfirm = false) }
        screenModelScope.launchIO {
            try {
                repository.deleteAll()
                mutableState.update {
                    it.copy(
                        results = emptyList(),
                        indexStats = OcrIndexStats(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L),
                    )
                }
            } catch (e: Exception) {
                mutableState.update { it.copy(errorKey = OcrErrorClassifier.classify(e)) }
            }
        }
    }

    fun clearOldEngineRows() {
        screenModelScope.launchIO {
            try {
                repository.deleteOldEngineRows(engineVersion)
                refreshStats()
            } catch (e: Exception) {
                mutableState.update { it.copy(errorKey = OcrErrorClassifier.classify(e)) }
            }
        }
    }

    // KMK --> v0.7.46 Phase 3: per-manga/per-chapter OCR deletion, reachable from the result row menu
    fun deleteOcrForManga(mangaId: Long) {
        screenModelScope.launchIO {
            try {
                repository.deleteByManga(mangaId)
                mutableState.update { it.copy(results = it.results.filterNot { r -> r.mangaId == mangaId }) }
                refreshStats()
            } catch (e: Exception) {
                mutableState.update { it.copy(errorKey = OcrErrorClassifier.classify(e)) }
            }
        }
    }

    fun deleteOcrForChapter(chapterId: Long) {
        screenModelScope.launchIO {
            try {
                repository.deleteByChapter(chapterId)
                mutableState.update { it.copy(results = it.results.filterNot { r -> r.chapterId == chapterId }) }
                refreshStats()
            } catch (e: Exception) {
                mutableState.update { it.copy(errorKey = OcrErrorClassifier.classify(e)) }
            }
        }
    }

    fun clearEmptyAndFailed() {
        screenModelScope.launchIO {
            try {
                repository.deleteEmptyAndFailed()
                refreshStats()
            } catch (e: Exception) {
                mutableState.update { it.copy(errorKey = OcrErrorClassifier.classify(e)) }
            }
        }
    }
    // KMK <--

    fun clearError() {
        mutableState.update { it.copy(errorKey = null) }
    }
}

// KMK <--

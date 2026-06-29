package exh.recs.evaluation

import android.content.Context
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.isOnline
import exh.recs.KmkRecsReleaseNotes
import exh.recs.RecommendationSourceFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.taste.interactor.ClearSourceEvaluationProbeMarker
import tachiyomi.domain.taste.interactor.ClearSourceEvaluationUnsafe
import tachiyomi.domain.taste.interactor.ClearSourceEvaluations
import tachiyomi.domain.taste.interactor.ClearUnsafeExtensionPackages
import tachiyomi.domain.taste.interactor.DeleteSourceEvaluationUnsafe
import tachiyomi.domain.taste.interactor.DeleteUnsafeExtensionPackage
import tachiyomi.domain.taste.interactor.GetSourceEvaluationProbeMarker
import tachiyomi.domain.taste.interactor.GetSourceEvaluationUnsafeSources
import tachiyomi.domain.taste.interactor.GetSourceEvaluations
import tachiyomi.domain.taste.interactor.GetTagAliases
import tachiyomi.domain.taste.interactor.GetTasteProfile
import tachiyomi.domain.taste.interactor.GetUnsafeExtensionPackages
import tachiyomi.domain.taste.interactor.MarkSourceEvaluationUnsafe
import tachiyomi.domain.taste.interactor.UpsertSourceRecommendationFit
import tachiyomi.domain.taste.model.RecommendationQualityVerdict
import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationUnsafeSource
import tachiyomi.domain.taste.model.SourceRecommendationFit
import tachiyomi.domain.taste.model.TasteProfile
import tachiyomi.domain.taste.model.TasteProfileConfidence
import tachiyomi.domain.taste.model.UnsafeExtensionPackage
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

// KMK --> v0.7.18: typed screen error keys — avoids hardcoded English in State
sealed interface ScreenErrorKey {
    /** Device was offline when evaluation was attempted. */
    data object Offline : ScreenErrorKey
    /** The candidate subscription flow threw during initialization. */
    data class CandidateLoadFailed(val detail: String?) : ScreenErrorKey
    /** Crash-quarantine recovery: an extension was marked unsafe on this or a previous run. */
    data class CrashRecovery(val extensionName: String, val phase: String) : ScreenErrorKey
}
// KMK <--

class SourceEvaluationScreenModel(
    private val context: Context,
    private val basePreferences: BasePreferences = Injekt.get(),
    // KMK --> v0.7.6: for installed extension key set
    private val extensionManager: ExtensionManager = Injekt.get(),
    // KMK <--
    private val getSourceEvaluations: GetSourceEvaluations = Injekt.get(),
    private val clearSourceEvaluations: ClearSourceEvaluations = Injekt.get(),
    private val getSourceEvaluationCandidates: GetSourceEvaluationCandidates = Injekt.get(),
    // KMK --> v0.6.16: crash quarantine
    private val getSourceEvaluationProbeMarker: GetSourceEvaluationProbeMarker = Injekt.get(),
    private val clearSourceEvaluationProbeMarker: ClearSourceEvaluationProbeMarker = Injekt.get(),
    private val markSourceEvaluationUnsafe: MarkSourceEvaluationUnsafe = Injekt.get(),
    private val getSourceEvaluationUnsafeSources: GetSourceEvaluationUnsafeSources = Injekt.get(),
    private val deleteSourceEvaluationUnsafe: DeleteSourceEvaluationUnsafe = Injekt.get(),
    private val clearSourceEvaluationUnsafe: ClearSourceEvaluationUnsafe = Injekt.get(),
    // KMK <--
    // KMK --> v0.6.18: package-level load quarantine
    private val getUnsafeExtensionPackages: GetUnsafeExtensionPackages = Injekt.get(),
    private val deleteUnsafeExtensionPackage: DeleteUnsafeExtensionPackage = Injekt.get(),
    private val clearUnsafeExtensionPackages: ClearUnsafeExtensionPackages = Injekt.get(),
    // KMK <--
    // KMK --> v0.6.19: taste-profile confidence + background execution
    private val getTasteProfile: GetTasteProfile = Injekt.get(),
    // KMK <--
    // KMK --> v0.6.20: reassessment baseline + management
    private val getMangaTaste: tachiyomi.domain.taste.interactor.GetMangaTaste = Injekt.get(),
    private val sourcePreferences: eu.kanade.domain.source.service.SourcePreferences = Injekt.get(),
    // KMK --> v0.7.6: rec-quality probe results
    private val getSourceRecommendationFit: tachiyomi.domain.taste.interactor.GetSourceRecommendationFit = Injekt.get(),
    // KMK --> v0.7.7: rec-quality in-screen evaluation action
    private val upsertSourceRecommendationFit: UpsertSourceRecommendationFit = Injekt.get(),
    private val getTagAliases: GetTagAliases = Injekt.get(),
    // KMK <--
    // KMK <--
    // KMK <--
) : StateScreenModel<SourceEvaluationScreenModel.State>(State()) {

    data class CandidateDiagnostics(
        val totalEligible: Int = 0,
        val evaluatedHiddenCount: Int = 0,
        val explicitHiddenCount: Int = 0,
        val dislikedHiddenCount: Int = 0,
        // KMK --> v0.6.16: crash quarantine
        val unsafeHiddenCount: Int = 0,
        // KMK <--
    )

    data class State(
        val candidates: List<EvaluationCandidate> = emptyList(),
        val evaluations: List<SourceEvaluation> = emptyList(),
        val queueState: SourceEvaluationQueueState = SourceEvaluationQueueState(),
        val options: SourceEvaluationOptions = SourceEvaluationOptions(),
        val installerPolicy: SourceEvaluationInstallerPolicy.PolicyResult? = null,
        val isLoadingCandidates: Boolean = true,
        val candidateDiagnostics: CandidateDiagnostics = CandidateDiagnostics(),
        // KMK --> v0.6.11: real Shizuku state
        val shizukuState: ShizukuSetupHelper.State = ShizukuSetupHelper.State(
            installed = false,
            binderAlive = false,
            permissionGranted = false,
        ),
        // KMK <--
        // KMK --> v0.6.13: prompt-heavy warning dialog + privateAvailable for dialog actions
        val showPromptHeavyWarningDialog: Boolean = false,
        val privateAvailable: Boolean = false,
        // KMK <--
        // KMK --> v0.6.15: sort mode for past results, clear confirmation dialog
        val resultSortMode: SourceEvaluationResultList.SortMode = SourceEvaluationResultList.SortMode.BEST_FIT,
        val showClearEvaluationsDialog: Boolean = false,
        // KMK <--
        // KMK --> v0.6.16: crash quarantine
        // KMK --> v0.7.18: typed ScreenErrorKey replaces hardcoded String
        val screenError: ScreenErrorKey? = null,
        // KMK <--
        val unsafeSources: List<SourceEvaluationUnsafeSource> = emptyList(),
        val showClearUnsafeDialog: Boolean = false,
        val showUnsafeSourcesDialog: Boolean = false,
        // KMK <--
        // KMK --> v0.6.18: package-level load quarantine
        val blockedPackages: List<UnsafeExtensionPackage> = emptyList(),
        val showBlockedPackagesDialog: Boolean = false,
        val showClearBlockedPackagesDialog: Boolean = false,
        // KMK --> v0.7.18: which blocked packages have a newer available version (pkgName set)
        val blockedPackagesWithNewerAvailable: Set<String> = emptySet(),
        // KMK <--
        // KMK <--
        // KMK --> v0.7.18: non-zero when user has dismissed/disliked suggestions that could be cleared
        val dismissedSuggestionCount: Int = 0,
        val dislikedSuggestionCount: Int = 0,
        // KMK --> v0.7.18: true when available-extension list is empty after candidates loaded
        val repoUnavailableWarning: Boolean = false,
        // KMK <--
        // KMK --> v0.6.19: taste-profile confidence + Shizuku UX
        val tasteConfidence: TasteProfileConfidence = TasteProfileConfidence.EMPTY,
        val showShizukuSetup: Boolean = false,
        // KMK <--
        // KMK --> v0.6.20: reassessment baseline state
        val currentRatedCount: Int = 0,
        val reassessmentBaselineCount: Int = 0,
        val showManagementSection: Boolean = false,
        val managementConfirmAction: ManagementAction? = null,
        // KMK <--
        // KMK --> v0.7.4: count of evaluated extensions that have a newer available version
        val updatedEvaluatedExtensionCount: Int = 0,
        // KMK <--
        // KMK --> v0.7.6: continuation cursor + installed-source display filter + rec-quality
        val continuationCursor: SourceEvaluationCursor? = null,
        val canContinue: Boolean = false,
        val remainingCandidateCount: Int = 0,
        val showInstalled: Boolean = false,
        val installedExtensionKeys: Set<String> = emptySet(),
        val filteredEvaluations: List<SourceEvaluation> = emptyList(),
        val hiddenInstalledCount: Int = 0,
        val recommendationFitsByEvalKey: Map<String, SourceRecommendationFit> = emptyMap(),
        // KMK --> v0.7.13: compact diagnostics summary for rec-quality section
        val recQualityDiagnostics: SourceRecommendationQualityDiagnostics.Summary? = null,
        // KMK <--
        // KMK <--
        // KMK --> v0.7.7: rec-quality in-screen action progress
        val recQualityRunning: Boolean = false,
        val recQualityProgress: Int = 0,
        val recQualityTotal: Int = 0,
        // KMK <--
        // KMK --> v0.7.11: pre-run consent dialog and pending action
        val showConsentDialog: Boolean = false,
        val pendingConsentAction: PendingConsentAction? = null,
        // KMK <--
        // KMK --> SEC-01 v0.7.16: leftover extension pkg name (process-death survivor, needs manual uninstall)
        val leftoverPkgName: String? = null,
        // KMK <--
    )

    // KMK --> v0.6.20: typed management action for confirmation dialogs
    enum class ManagementAction {
        RESET_DISLIKED_SOURCES,
        RESET_REASSESSMENT_BASELINE,
        CLEAR_SEEN_MANGA,
        // KMK --> v0.7.18
        CLEAR_DISMISSED_SUGGESTIONS,
        CLEAR_DISLIKED_SUGGESTIONS,
        // KMK <--
    }
    // KMK <--

    // KMK --> v0.7.11: typed pending action for consent dialog — distinguishes WHY the dialog was opened
    // so that confirming "View evaluation warning" never accidentally starts evaluation.
    enum class PendingConsentAction {
        START_EVALUATION,
        REASSESS_UPDATED,
        CONTINUE_EVALUATION,
        VIEW_ONLY,
    }
    // KMK <--

    // KMK --> v0.6.19: runner field replaced by SourceEvaluationJob (WorkManager)
    // KMK <--

    // KMK --> v0.6.12: last-emitted candidate pool for option-reactive filtering
    private val lastCandidatePool = MutableStateFlow<SourceEvaluationCandidateFilter.CandidatePoolResult?>(null)
    // KMK <--

    init {
        // KMK --> v0.6.16/v0.6.17: crash recovery — screen-open fallback using shared startup helper.
        // Startup recovery in App.kt normally runs first; if the marker was already cleared there,
        // this call becomes DoNothing. Both paths share the same policy and reason strings.
        screenModelScope.launch {
            val result = SourceEvaluationStartupRecovery(
                getProbeMarker = getSourceEvaluationProbeMarker,
                clearProbeMarker = clearSourceEvaluationProbeMarker,
                markUnsafe = markSourceEvaluationUnsafe,
                getUnsafeSources = getSourceEvaluationUnsafeSources,
            ).run()
            if (result.markerRecovered && result.recoveredExtensionName != null) {
                // KMK --> v0.7.18: typed ScreenErrorKey.CrashRecovery
                val phase = result.recoveredPhase ?: "unknown"
                mutableState.update {
                    it.copy(screenError = ScreenErrorKey.CrashRecovery(result.recoveredExtensionName, phase))
                }
                // KMK <--
            }
        }
        // KMK <--

        // Observe stored evaluations
        // KMK --> v0.6.9: catch DB errors so the screen opens even if source_evaluation is absent
        getSourceEvaluations.subscribeAll()
            .catch { e ->
                logcat(LogPriority.ERROR, e) { "source_evaluation table unavailable in SourceEvaluationScreen" }
                emit(emptyList())
            }
            // KMK <--
            .onEach { evals ->
                // KMK --> v0.6.15: sanitize before storing (drops blank/duplicate evaluationKey rows)
                mutableState.update { it.copy(evaluations = SourceEvaluationResultList.sanitize(evals)) }
                // KMK <--
                // KMK --> v0.7.6: reapply display filter when evaluations change
                applyDisplayFilter()
                // KMK <--
            }
            .launchIn(screenModelScope)

        // KMK --> v0.6.16: observe unsafe sources for UI
        getSourceEvaluationUnsafeSources.subscribeAll()
            .catch { e ->
                logcat(LogPriority.ERROR, e) { "source_evaluation_unsafe_source table unavailable in SourceEvaluationScreen" }
                emit(emptyList())
            }
            .onEach { unsafe ->
                mutableState.update { it.copy(unsafeSources = unsafe) }
            }
            .launchIn(screenModelScope)
        // KMK <--

        // KMK --> v0.6.18: observe package-level blocked packages for UI
        getUnsafeExtensionPackages.subscribeAll()
            .catch { e ->
                logcat(LogPriority.ERROR, e) { "unsafe_extension_package table unavailable in SourceEvaluationScreen" }
                emit(emptyList())
            }
            .onEach { blocked ->
                // KMK --> v0.7.18: cross-reference with available extensions to flag packages with newer versions
                val availablePkgNames = extensionManager.availableExtensionsFlow.value.map { it.pkgName }.toSet()
                val withNewer = blocked.filter { it.pkgName in availablePkgNames }.map { it.pkgName }.toSet()
                // KMK <--
                mutableState.update { it.copy(blockedPackages = blocked, blockedPackagesWithNewerAvailable = withNewer) }
            }
            .launchIn(screenModelScope)
        // KMK <--

        // KMK --> v0.6.19: load taste-profile confidence for low-evidence warning
        screenModelScope.launch {
            try {
                val profile = getTasteProfile.await()
                mutableState.update { it.copy(tasteConfidence = TasteProfileConfidence.from(profile)) }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "KMK SourceEvaluation: failed to load taste profile for confidence check" }
            }
        }
        // KMK <--

        // KMK --> v0.6.19: reconnect to any active background evaluation job
        SourceEvaluationJobState.activeQueueState
            .onEach { jobQueueState ->
                mutableState.update { it.copy(queueState = jobQueueState ?: SourceEvaluationQueueState()) }
                // KMK --> v0.6.20: update reassessment baseline when evaluation completes
                if (jobQueueState?.status == SourceEvaluationQueueState.Status.Completed) {
                    updateReassessmentBaseline()
                    // KMK --> v0.7.6: advance cursor + reload rec-quality fits
                    updateContinuationCursor()
                    loadRecommendationFits()
                    // KMK <--
                }
                // KMK <--
            }
            .launchIn(screenModelScope)
        // KMK <--

        // KMK --> v0.7.6: load persisted cursor
        screenModelScope.launch {
            try {
                val raw = sourcePreferences.sourceEvaluationContinuationCursor().get()
                val cursor = SourceEvaluationContinuationPolicy.deserialize(raw)
                mutableState.update { it.copy(continuationCursor = cursor) }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "KMK SourceEvaluation: failed to load cursor" }
            }
        }
        loadRecommendationFits()
        // KMK <--

        // KMK --> SEC-01 v0.7.16: surface leftover-extension warning if startup recovery detected one
        screenModelScope.launch {
            try {
                val pkg = sourcePreferences.sourceEvaluationLeftoverPkg().get()
                if (pkg.isNotBlank()) {
                    mutableState.update { it.copy(leftoverPkgName = pkg) }
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "KMK SourceEvaluation: failed to load leftover pkg preference" }
            }
        }
        // KMK <--

        // KMK --> v0.7.7 follow-up: keep installedExtensionKeys in sync with extension installs/uninstalls
        extensionManager.installedExtensionsFlow
            .onEach { installed ->
                val keys = installed.map { "${it.signatureHash}|${it.pkgName}" }.toSet()
                mutableState.update { it.copy(installedExtensionKeys = keys) }
                applyDisplayFilter()
            }
            .launchIn(screenModelScope)
        // KMK <--

        // KMK --> v0.7.18: load dismissed/disliked suggestion counts for management section display
        screenModelScope.launch {
            try {
                val dismissedRaw = sourcePreferences.dismissedNonInstalledRecommendationSources().get()
                val dislikedRaw = sourcePreferences.dislikedRecommendationSourceKeys().get()
                val dismissedCount = dismissedRaw.split(";").count { it.isNotBlank() }
                val dislikedCount = dislikedRaw.split(";").count { it.isNotBlank() }
                mutableState.update {
                    it.copy(dismissedSuggestionCount = dismissedCount, dislikedSuggestionCount = dislikedCount)
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "KMK SourceEvaluation: failed to load suggestion counts" }
            }
        }
        // KMK <--

        // KMK --> v0.6.20: load reassessment state
        screenModelScope.launch {
            try {
                val allTastes = getMangaTaste.awaitAll()
                val baseline = sourcePreferences.sourceEvaluationLastReassessmentRatingCount().get()
                mutableState.update {
                    it.copy(
                        currentRatedCount = allTastes.size,
                        reassessmentBaselineCount = baseline,
                    )
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "KMK SourceEvaluation: failed to load reassessment state" }
            }
        }
        // KMK <--

        // KMK --> v0.6.12: load candidates from broad available extension pool
        getSourceEvaluationCandidates.subscribe()
            .catch { e ->
                logcat(LogPriority.ERROR, e) { "Failed to load source evaluation candidates" }
                mutableState.update {
                    it.copy(
                        candidates = emptyList(),
                        isLoadingCandidates = false,
                        // KMK --> v0.7.18
                        screenError = ScreenErrorKey.CandidateLoadFailed(e.message),
                        // KMK <--
                    )
                }
                emit(
                    SourceEvaluationCandidateFilter.CandidatePoolResult(
                        allEligible = emptyList(),
                        evaluationsByExtensionKey = emptyMap(),
                        explicitExtensionKeys = emptySet(),
                        dislikedHiddenCount = 0,
                        blockExplicit = false,
                    ),
                )
            }
            .onEach { pool ->
                lastCandidatePool.value = pool
                applyOptionsAndUpdateState()
            }
            .launchIn(screenModelScope)
        // KMK <--

        try {
            refreshInstallerPolicy()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "KMK SourceEvaluation: refreshInstallerPolicy failed in init" }
        }
    }

    fun setInstallerMode(mode: SourceEvaluationInstallerPolicy.InstallerMode) {
        // KMK --> v0.6.19: auto-manage Shizuku card visibility when mode changes
        val currentInstaller = basePreferences.extensionInstaller().get()
        val autoShowShizuku = mode == SourceEvaluationInstallerPolicy.InstallerMode.SHIZUKU ||
            (
                mode == SourceEvaluationInstallerPolicy.InstallerMode.CURRENT &&
                    currentInstaller == BasePreferences.ExtensionInstaller.SHIZUKU
                )
        mutableState.update {
            it.copy(
                options = it.options.copy(installerMode = mode),
                showShizukuSetup = autoShowShizuku,
            )
        }
        // KMK <--
        // KMK --> SEC-03 v0.7.16: reset consent when switching to a non-PRIVATE mode so the risk notice re-surfaces
        if (mode != SourceEvaluationInstallerPolicy.InstallerMode.PRIVATE) {
            sourcePreferences.sourceEvaluationConsentGiven().set(false)
        }
        // KMK <--
        refreshInstallerPolicy()
    }

    // KMK --> v0.6.19: manual Shizuku card toggle
    fun showShizukuSetup() {
        mutableState.update { it.copy(showShizukuSetup = true) }
    }

    fun hideShizukuSetup() {
        mutableState.update { it.copy(showShizukuSetup = false) }
    }
    // KMK <--

    fun setBatchSize(size: Int) {
        mutableState.update { it.copy(options = it.options.copy(batchSize = size)) }
    }

    fun setSkipAlreadyEvaluated(skip: Boolean) {
        mutableState.update { it.copy(options = it.options.copy(skipAlreadyEvaluated = skip)) }
        applyOptionsAndUpdateState()
    }

    fun setIncludeExplicit(include: Boolean) {
        mutableState.update { it.copy(options = it.options.copy(includeExplicitCandidates = include)) }
        applyOptionsAndUpdateState()
    }

    fun setReEvaluateStale(reEval: Boolean) {
        mutableState.update { it.copy(options = it.options.copy(reEvaluateStale = reEval)) }
        applyOptionsAndUpdateState()
    }

    // KMK --> v0.7.4: start evaluation restricted to extensions updated since last evaluation
    fun startReassessUpdated() {
        if (!context.isOnline()) {
            mutableState.update {
                it.copy(screenError = ScreenErrorKey.Offline) // KMK v0.7.18
            }
            return
        }
        // KMK --> v0.7.11: require pre-run consent (same gate as startEvaluation)
        if (SourceEvaluationConsentPolicy.isConsentRequired(sourcePreferences.sourceEvaluationConsentGiven().get())) {
            mutableState.update { it.copy(showConsentDialog = true, pendingConsentAction = PendingConsentAction.REASSESS_UPDATED) }
            return
        }
        // KMK <--
        doReassessUpdated()
    }

    private fun doReassessUpdated() {
        mutableState.update { it.copy(options = it.options.copy(onlyUpdatedEvaluated = true, skipAlreadyEvaluated = false)) }
        applyOptionsAndUpdateState()
        val s = state.value
        if (s.candidates.isEmpty()) {
            mutableState.update { it.copy(options = it.options.copy(onlyUpdatedEvaluated = false, skipAlreadyEvaluated = true)) }
            applyOptionsAndUpdateState()
            return
        }
        launchEvaluation(promptHeavyCleanupAllowed = false)
        mutableState.update { it.copy(options = it.options.copy(onlyUpdatedEvaluated = false, skipAlreadyEvaluated = true)) }
    }
    // KMK <--

    fun startEvaluation() {
        // KMK --> v0.6.12: candidates are already filtered by applyOptionsAndUpdateState()
        val s = state.value
        val candidates = s.candidates
        if (candidates.isEmpty()) return
        // KMK <--

        // KMK --> v0.7.11: require pre-run consent before any connectivity or dialog checks
        if (SourceEvaluationConsentPolicy.isConsentRequired(sourcePreferences.sourceEvaluationConsentGiven().get())) {
            mutableState.update { it.copy(showConsentDialog = true, pendingConsentAction = PendingConsentAction.START_EVALUATION) }
            return
        }
        // KMK <--

        // KMK --> v0.6.19 follow-up: guard against starting without internet connectivity
        if (!context.isOnline()) {
            // KMK --> v0.7.11
            mutableState.update {
                it.copy(screenError = ScreenErrorKey.Offline) // KMK v0.7.18
            }
            // KMK <--
            return
        }
        // KMK <--

        // KMK --> v0.6.13: show warning dialog before prompt-heavy modes with batch > 1
        val policy = s.installerPolicy
        if (policy != null && policy.requiresPromptWarning && s.options.batchSize > 1) {
            mutableState.update { it.copy(showPromptHeavyWarningDialog = true) }
            return
        }
        // KMK <--

        launchEvaluation(promptHeavyCleanupAllowed = false)
    }

    // KMK --> v0.6.13: dialog actions for prompt-heavy mode warning

    /** User confirmed continuing with prompt-heavy mode. */
    fun confirmAndStartWithPrompts() {
        // KMK --> v0.6.19 follow-up: re-check connectivity at dialog confirmation time
        if (!context.isOnline()) {
            // KMK --> v0.8.0
            mutableState.update {
                it.copy(
                    showPromptHeavyWarningDialog = false,
                    screenError = ScreenErrorKey.Offline, // KMK v0.7.18
                )
            }
            // KMK <--
            return
        }
        // KMK <--
        mutableState.update { it.copy(showPromptHeavyWarningDialog = false) }
        launchEvaluation(promptHeavyCleanupAllowed = true)
    }

    /** User chose to switch to Private instead of continuing with prompt-heavy mode. */
    fun switchToPrivateAndStart() {
        mutableState.update {
            it.copy(
                showPromptHeavyWarningDialog = false,
                options = it.options.copy(installerMode = SourceEvaluationInstallerPolicy.InstallerMode.PRIVATE),
                showShizukuSetup = false, // KMK --> v0.6.19
            )
        }
        refreshInstallerPolicy()
        launchEvaluation(promptHeavyCleanupAllowed = false)
    }

    /** Switch to Private installer without starting evaluation. Used from Shizuku card. */
    fun switchToPrivate() {
        mutableState.update {
            it.copy(
                options = it.options.copy(installerMode = SourceEvaluationInstallerPolicy.InstallerMode.PRIVATE),
                showShizukuSetup = false, // KMK --> v0.6.19
            )
        }
        refreshInstallerPolicy()
    }

    fun dismissPromptWarningDialog() {
        mutableState.update { it.copy(showPromptHeavyWarningDialog = false) }
    }

    // KMK --> v0.7.11: consent dialog actions

    /** Show the consent dialog in view-only mode — never starts evaluation. */
    fun showConsentWarning() {
        mutableState.update { it.copy(showConsentDialog = true, pendingConsentAction = PendingConsentAction.VIEW_ONLY) }
    }

    fun dismissConsent() {
        mutableState.update { it.copy(showConsentDialog = false, pendingConsentAction = null) }
    }

    /**
     * User confirmed the pre-run consent.
     * Persists consent, then dispatches the original pending action.
     * VIEW_ONLY closes the dialog without starting any evaluation.
     */
    fun confirmConsent() {
        sourcePreferences.sourceEvaluationConsentGiven().set(true)
        val pendingAction = state.value.pendingConsentAction
        mutableState.update { it.copy(showConsentDialog = false, pendingConsentAction = null) }
        when (pendingAction) {
            PendingConsentAction.START_EVALUATION -> startEvaluation()
            PendingConsentAction.REASSESS_UPDATED -> doReassessUpdated()
            PendingConsentAction.CONTINUE_EVALUATION -> continueEvaluation()
            PendingConsentAction.VIEW_ONLY, null -> { /* view-only — never starts evaluation */ }
        }
    }

    // KMK <--

    private fun launchEvaluation(promptHeavyCleanupAllowed: Boolean, isContinuation: Boolean = false) {
        val s = state.value
        val opts = s.options.copy(promptHeavyCleanupAllowed = promptHeavyCleanupAllowed)
        // KMK --> v0.7.6: slice via continuation policy before passing to runner
        val allCandidates = s.candidates
        val recLanguages = RecommendationSourceFilter.normalizeLanguages(
            sourcePreferences.recommendationSourceLanguages().get(),
        )
        val blockExplicit = sourcePreferences.blockExplicitPornHentaiSources().get()
        val fingerprint = SourceEvaluationContinuationPolicy.buildFilterFingerprint(
            languages = recLanguages,
            includeExplicit = opts.includeExplicitCandidates,
            skipAlreadyEvaluated = opts.skipAlreadyEvaluated,
            reEvaluateStale = opts.reEvaluateStale,
            onlyUpdatedEvaluated = opts.onlyUpdatedEvaluated,
            blockExplicit = blockExplicit,
        )
        val cursor = if (isContinuation) s.continuationCursor else null
        val slice = SourceEvaluationContinuationPolicy.sliceForRun(
            candidates = allCandidates,
            batchSize = opts.batchSize,
            cursor = cursor,
            currentFingerprint = fingerprint,
        )
        // KMK <--
        // KMK --> v0.6.19: launch as WorkManager foreground job so evaluation continues after leaving screen
        SourceEvaluationJobState.pendingCandidates = slice
        SourceEvaluationJobState.pendingOptions = opts
        // KMK --> v0.7.6: store fingerprint and full candidate list so completion can advance cursor
        SourceEvaluationJobState.pendingCursorFingerprint = fingerprint
        SourceEvaluationJobState.pendingAllCandidates = allCandidates
        // KMK <--
        SourceEvaluationJobState.activeQueueState.value = SourceEvaluationQueueState(
            status = SourceEvaluationQueueState.Status.Running,
            totalCount = slice.size,
            installerMode = opts.installerMode,
            batchSize = opts.batchSize,
        )
        SourceEvaluationJob.start(context)
        // KMK <--
    }
    // KMK <--

    fun cancelEvaluation() {
        // KMK --> v0.6.19: cancel WorkManager job; onStopped() cancels the runner
        SourceEvaluationJob.cancel(context)
        SourceEvaluationJobState.activeQueueState.value =
            SourceEvaluationJobState.activeQueueState.value
                ?.copy(status = SourceEvaluationQueueState.Status.Cancelled)
                ?: SourceEvaluationQueueState(status = SourceEvaluationQueueState.Status.Cancelled)
        // KMK <--
    }

    // KMK --> SEC-02 v0.7.16: trigger system uninstall prompts for extensions left behind by Shizuku/Current-mode runs
    fun cleanupPromptRequiredExtensions() {
        if (mutableState.value.queueState.isRunning) return
        val promptRequired = mutableState.value.queueState.results
            .filter { it.cleanupStatus == SourceEvaluationQueueState.CleanupStatus.PromptRequired }
            .distinctBy { it.pkgName to it.signatureHash }
        promptRequired.forEach { result ->
            val installedExt = extensionManager.installedExtensionsFlow.value.find {
                it.pkgName == result.pkgName && it.signatureHash == result.signatureHash
            }
            if (installedExt != null) {
                extensionManager.uninstallExtension(installedExt)
            }
        }
    }
    // KMK <--

    // KMK --> SEC-01 v0.7.16: uninstall a leftover extension that survived process death
    fun dismissLeftoverExtension() {
        val pkgName = mutableState.value.leftoverPkgName ?: return
        val ext = extensionManager.installedExtensionsFlow.value.find { it.pkgName == pkgName }
        if (ext != null) {
            extensionManager.uninstallExtension(ext)
        }
        sourcePreferences.sourceEvaluationLeftoverPkg().set("")
        mutableState.update { it.copy(leftoverPkgName = null) }
    }
    // KMK <--

    fun resetEvaluation() {
        // KMK --> v0.6.19: clear job state singleton on reset
        SourceEvaluationJobState.reset()
        // KMK <--
        mutableState.update { it.copy(queueState = SourceEvaluationQueueState()) }
    }

    // KMK --> v0.6.15: three-step clear (request → confirm/dismiss) to prevent accidental deletion
    fun requestClearAllEvaluations() {
        mutableState.update { it.copy(showClearEvaluationsDialog = true) }
    }

    fun dismissClearAllEvaluations() {
        mutableState.update { it.copy(showClearEvaluationsDialog = false) }
    }

    fun confirmClearAllEvaluations() {
        mutableState.update { it.copy(showClearEvaluationsDialog = false) }
        screenModelScope.launch {
            clearSourceEvaluations.await()
        }
    }
    // KMK <--

    // KMK --> v0.6.15: sort mode for past evaluation results
    fun setResultSortMode(mode: SourceEvaluationResultList.SortMode) {
        mutableState.update { it.copy(resultSortMode = mode) }
    }
    // KMK <--

    // KMK --> v0.7.6: continuation and display filter actions

    fun continueEvaluation() {
        val s = state.value
        if (!s.canContinue) return
        // KMK --> v0.7.11: require pre-run consent (same gate as startEvaluation)
        if (SourceEvaluationConsentPolicy.isConsentRequired(sourcePreferences.sourceEvaluationConsentGiven().get())) {
            mutableState.update { it.copy(showConsentDialog = true, pendingConsentAction = PendingConsentAction.CONTINUE_EVALUATION) }
            return
        }
        // KMK <--
        if (!context.isOnline()) {
            mutableState.update {
                it.copy(screenError = ScreenErrorKey.Offline) // KMK v0.7.18
            }
            return
        }
        val policy = s.installerPolicy
        if (policy != null && policy.requiresPromptWarning && s.options.batchSize > 1) {
            mutableState.update { it.copy(showPromptHeavyWarningDialog = true) }
            return
        }
        launchEvaluation(promptHeavyCleanupAllowed = false, isContinuation = true)
    }

    fun setShowInstalled(show: Boolean) {
        mutableState.update { it.copy(showInstalled = show) }
        applyDisplayFilter()
    }

    private fun applyDisplayFilter() {
        val s = state.value
        val result = SourceEvaluationDisplayFilter.filter(
            evaluations = s.evaluations,
            installedExtensionKeys = s.installedExtensionKeys,
            showInstalled = s.showInstalled,
        )
        mutableState.update {
            it.copy(
                filteredEvaluations = result.visible,
                hiddenInstalledCount = result.hiddenInstalledCount,
            )
        }
    }

    // KMK --> v0.7.6: load rec-quality fits into state for display
    private fun loadRecommendationFits() {
        screenModelScope.launch {
            try {
                val fits = getSourceRecommendationFit.awaitAll()
                val fitsByKey = fits.associateBy { it.evaluationKey }
                // KMK --> v0.7.13: compute diagnostics summary whenever fits are refreshed
                val diagnostics = SourceRecommendationQualityDiagnostics.compute(
                    state.value.evaluations,
                    fitsByKey,
                )
                // KMK <--
                mutableState.update {
                    it.copy(
                        recommendationFitsByEvalKey = fitsByKey,
                        recQualityDiagnostics = diagnostics,
                    )
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "KMK SourceEvaluation: failed to load recommendation fits" }
            }
        }
    }
    // KMK <--

    // KMK --> v0.7.7: evaluate recommendation quality for promising sources from this screen
    fun evaluateRecommendationQualityForPromising(reCheckAll: Boolean = false) {
        if (state.value.recQualityRunning) return
        val s = state.value
        val queue = SourceRecommendationQualityQueue.compute(s.evaluations, s.recommendationFitsByEvalKey)
        val targets = if (reCheckAll) {
            queue.missingPromising + queue.checkedPromising
        } else {
            queue.missingPromising
        }
        if (targets.isEmpty()) return

        mutableState.update {
            it.copy(recQualityRunning = true, recQualityTotal = targets.size, recQualityProgress = 0)
        }

        screenModelScope.launch {
            try {
                val tasteProfile = try {
                    getTasteProfile.await()
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "KMK SourceEvaluation: failed to load taste profile for rec-quality action" }
                    mutableState.update { it.copy(recQualityRunning = false) }
                    return@launch
                }
                val probe = SourceRecommendationFitProbe(getTagAliases)
                // KMK --> v0.7.10: load full available extension list, not just the current candidate pool
                val availableExtensions = loadAvailableExtensionsForRecQuality()
                // KMK <--
                val installerOverride = SourceEvaluationInstallerPolicy.effectiveInstallerOverride(
                    mode = state.value.options.installerMode,
                    currentGlobalInstaller = basePreferences.extensionInstaller().get(),
                    privateAvailable = state.value.privateAvailable,
                )

                for ((index, evaluation) in targets.withIndex()) {
                    try {
                        evaluateOneForRecQuality(evaluation, probe, tasteProfile, availableExtensions, installerOverride)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logcat(LogPriority.WARN, e) { "KMK SourceEvaluation: rec-quality probe failed for ${evaluation.sourceName}" }
                        writeRecQualityErrorFit(evaluation, e.message ?: "Probe error")
                    }
                    mutableState.update { it.copy(recQualityProgress = index + 1) }
                }
            } finally {
                loadRecommendationFits()
                mutableState.update { it.copy(recQualityRunning = false) }
            }
        }
    }

    // KMK --> v0.7.7 follow-up: per-source rec-quality evaluation with temporary install support
    // KMK --> v0.7.10: use robust installed/source resolvers; guard empty available list
    private suspend fun evaluateOneForRecQuality(
        evaluation: SourceEvaluation,
        probe: SourceRecommendationFitProbe,
        tasteProfile: TasteProfile,
        availableExtensions: List<Extension.Available>,
        installerOverride: BasePreferences.ExtensionInstaller?,
    ) {
        // Installed path: probe directly without any install/cleanup
        val installedList = extensionManager.installedExtensionsFlow.value
        val installedResolve = SourceRecommendationQualityInstalledResolver.resolve(evaluation, installedList)
        if (installedResolve is SourceRecommendationQualityInstalledResolver.ResolveResult.Found) {
            val alreadyInstalled = installedResolve.extension
            when (val sr = SourceRecommendationQualitySourceResolver.resolve(alreadyInstalled, evaluation)) {
                is SourceRecommendationQualitySourceResolver.ResolveResult.Found -> {
                    val outcome = probe.probe(sr.source, tasteProfile)
                    upsertSourceRecommendationFit.await(buildRecQualityFitFromOutcome(evaluation, outcome))
                }
                is SourceRecommendationQualitySourceResolver.ResolveResult.Ambiguous -> {
                    writeRecQualityErrorFit(evaluation, "Source match ambiguous in installed extension: ${sr.reason}")
                }
                is SourceRecommendationQualitySourceResolver.ResolveResult.NotFound -> {
                    writeRecQualityErrorFit(evaluation, "Source not found in installed extension")
                }
            }
            return
        }
        if (installedResolve is SourceRecommendationQualityInstalledResolver.ResolveResult.Ambiguous) {
            writeRecQualityErrorFit(evaluation, "Installed extension match ambiguous: ${installedResolve.reason}")
            return
        }

        // Non-installed path: resolve from available pool, install temporarily, probe, cleanup
        if (availableExtensions.isEmpty()) {
            writeRecQualityErrorFit(evaluation, "Available extension list unavailable")
            return
        }
        val resolved = SourceRecommendationQualityExtensionResolver.resolve(evaluation, availableExtensions)
        val availableExt = when (resolved) {
            is SourceRecommendationQualityExtensionResolver.ResolveResult.NotFound -> {
                writeRecQualityErrorFit(evaluation, "Extension not found in available sources")
                return
            }
            is SourceRecommendationQualityExtensionResolver.ResolveResult.Ambiguous -> {
                writeRecQualityErrorFit(evaluation, "Extension match ambiguous: ${resolved.reason}")
                return
            }
            is SourceRecommendationQualityExtensionResolver.ResolveResult.Found -> resolved.extension
        }

        val installSuccess = withTimeoutOrNull(90_000L) {
            try {
                extensionManager.installExtension(availableExt, installerOverride)
                    .first { it == InstallStep.Installed || it == InstallStep.Error } == InstallStep.Installed
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                false
            }
        } ?: false

        if (!installSuccess) {
            writeRecQualityErrorFit(evaluation, "Install failed or timed out")
            return
        }

        val installedExt = withTimeoutOrNull(20_000L) {
            extensionManager.installedExtensionsFlow.first { installed ->
                installed.any { it.pkgName == availableExt.pkgName && it.signatureHash == availableExt.signatureHash }
            }.find { it.pkgName == availableExt.pkgName && it.signatureHash == availableExt.signatureHash }
        }

        if (installedExt == null) {
            writeRecQualityErrorFit(evaluation, "Installed extension did not load")
            cleanupRecQualityExtension(availableExt)
            return
        }

        try {
            when (val sr = SourceRecommendationQualitySourceResolver.resolve(installedExt, evaluation)) {
                is SourceRecommendationQualitySourceResolver.ResolveResult.Found -> {
                    val outcome = probe.probe(sr.source, tasteProfile)
                    upsertSourceRecommendationFit.await(buildRecQualityFitFromOutcome(evaluation, outcome))
                }
                is SourceRecommendationQualitySourceResolver.ResolveResult.Ambiguous -> {
                    writeRecQualityErrorFit(evaluation, "Source match ambiguous after install: ${sr.reason}")
                }
                is SourceRecommendationQualitySourceResolver.ResolveResult.NotFound -> {
                    writeRecQualityErrorFit(evaluation, "Source not found after install")
                }
            }
        } finally {
            cleanupRecQualityExtension(availableExt)
        }
    }
    // KMK <--

    // KMK --> v0.7.10: prefer full available extension list over the screen-local candidate pool
    private fun loadAvailableExtensionsForRecQuality(): List<Extension.Available> {
        val fromManager = extensionManager.availableExtensionsFlow.value
        if (fromManager.isNotEmpty()) return fromManager
        val fromPool = lastCandidatePool.value?.allEligible?.map { it.extension }
        if (!fromPool.isNullOrEmpty()) return fromPool
        return emptyList()
    }
    // KMK <--

    private fun cleanupRecQualityExtension(ext: Extension.Available) {
        try {
            val installedExt = extensionManager.installedExtensionsFlow.value.find {
                it.pkgName == ext.pkgName && it.signatureHash == ext.signatureHash
            }
            val decision = SourceEvaluationCleanupPolicy.cleanupDecision(
                preExistingInstalled = false,
                installedAfterEvaluation = installedExt != null,
                isShared = installedExt?.isShared ?: false,
            )
            when (decision) {
                SourceEvaluationCleanupPolicy.CleanupDecision.RemovePrivateSilently -> {
                    extensionManager.uninstallExtension(installedExt!!)
                }
                SourceEvaluationCleanupPolicy.CleanupDecision.PromptRequired -> {
                    logcat(LogPriority.INFO) {
                        "KMK SourceEvaluation rec-quality: skipping system-installed cleanup for ${ext.name}"
                    }
                }
                else -> { /* SkipPreExisting or NotNeeded */ }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logcat(LogPriority.WARN, e) { "KMK SourceEvaluation rec-quality: cleanup failed for ${ext.name}" }
        }
    }

    private suspend fun writeRecQualityErrorFit(evaluation: SourceEvaluation, message: String) {
        try {
            upsertSourceRecommendationFit.await(buildRecQualityErrorFit(evaluation, message))
        } catch (_: Exception) {}
    }

    private fun buildRecQualityFitFromOutcome(
        evaluation: SourceEvaluation,
        outcome: SourceRecommendationFitProbeOutcome,
    ): SourceRecommendationFit {
        val qualityScore = SourceRecommendationFitScorer.score(outcome.toScorerOutcome())
        val label = outcome.label()
        val verdict = RecommendationQualityVerdict.fromSerialized(label.name.lowercase())
        // KMK --> v0.7.12: populate errorMessage so the UI can display why the probe failed
        // KMK --> v0.7.13: extend to WEAK and NO_MATCHES so user sees reason, not just label
        val probeErrorMessage = when (label) {
            RecommendationQualityLabel.ERROR -> {
                if (outcome.reasons.isNotEmpty()) {
                    outcome.reasons.take(2).joinToString("; ").take(200)
                } else {
                    null
                }
            }
            RecommendationQualityLabel.NO_MATCHES -> {
                if (outcome.weakMetadataCandidateCount > 0) {
                    "Search returned results but none had usable genre metadata"
                } else {
                    "Search returned no results for taste profile tags"
                }
            }
            RecommendationQualityLabel.WEAK -> {
                buildString {
                    if (outcome.weakMetadataCandidateCount > 0) {
                        append("${outcome.weakMetadataCandidateCount} result(s) had no genre even after enrichment")
                    }
                    if (outcome.blockedTagCandidateCount > 0) {
                        if (isNotEmpty()) append("; ")
                        append("${outcome.blockedTagCandidateCount} blocked by tag filter")
                    }
                    if (isEmpty()) {
                        append("Results did not match taste profile tags")
                    }
                }.take(200)
            }
            else -> null
        }
        // KMK <--
        // KMK <--
        return SourceRecommendationFit(
            fitKey = SourceRecommendationFit.fitKeyFor(evaluation.evaluationKey),
            evaluationKey = evaluation.evaluationKey,
            sourceId = evaluation.sourceId,
            extensionPkgName = evaluation.extensionPkgName,
            signatureHash = evaluation.signatureHash,
            extensionName = evaluation.extensionName,
            sourceName = evaluation.sourceName,
            lang = evaluation.lang,
            evaluatedAt = System.currentTimeMillis(),
            queryCount = outcome.queryCount,
            querySuccessCount = outcome.querySuccessCount,
            rawResultCount = outcome.rawResultCount,
            visibleCandidateCount = outcome.visibleCandidateCount,
            filteredOutCount = outcome.filteredOutCount,
            blockedTagCandidateCount = outcome.blockedTagCandidateCount,
            matchedGroupCount = outcome.matchedGroupCount,
            topPicksContribution = outcome.topPicksContribution,
            noMatchesCount = outcome.noMatchesCount,
            errorCount = outcome.errorCount,
            avgCandidateScore = outcome.avgCandidateScore,
            recommendationQualityScore = qualityScore,
            verdict = verdict,
            reasonsJson = "[${outcome.reasons.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }}]",
            errorMessage = probeErrorMessage,
        )
    }
    // KMK <--

    private fun buildRecQualityErrorFit(
        evaluation: SourceEvaluation,
        errorMessage: String,
    ) = SourceRecommendationFit(
        fitKey = SourceRecommendationFit.fitKeyFor(evaluation.evaluationKey),
        evaluationKey = evaluation.evaluationKey,
        sourceId = evaluation.sourceId,
        extensionPkgName = evaluation.extensionPkgName,
        signatureHash = evaluation.signatureHash,
        extensionName = evaluation.extensionName,
        sourceName = evaluation.sourceName,
        lang = evaluation.lang,
        evaluatedAt = System.currentTimeMillis(),
        queryCount = 0,
        querySuccessCount = 0,
        rawResultCount = 0,
        visibleCandidateCount = 0,
        filteredOutCount = 0,
        blockedTagCandidateCount = 0,
        matchedGroupCount = 0,
        topPicksContribution = 0,
        noMatchesCount = 0,
        errorCount = 1,
        avgCandidateScore = 0.0,
        recommendationQualityScore = 0.0,
        verdict = RecommendationQualityVerdict.ERROR,
        reasonsJson = "[]",
        errorMessage = errorMessage,
    )

    private fun updateContinuationCursor() {
        screenModelScope.launch {
            try {
                val s = state.value
                val runner = SourceEvaluationJobState.activeRunner ?: return@launch
                val completedKeys = runner.completedCandidateKeys
                if (completedKeys.isEmpty()) return@launch
                val fingerprint = SourceEvaluationJobState.pendingCursorFingerprint ?: return@launch
                val allCandidates = SourceEvaluationJobState.pendingAllCandidates ?: s.candidates
                val newCursor = SourceEvaluationContinuationPolicy.advanceCursor(
                    current = s.continuationCursor,
                    completedKeys = completedKeys,
                    allCandidates = allCandidates,
                    currentFingerprint = fingerprint,
                )
                val serialized = SourceEvaluationContinuationPolicy.serialize(newCursor)
                sourcePreferences.sourceEvaluationContinuationCursor().set(serialized)

                val recLanguages = RecommendationSourceFilter.normalizeLanguages(
                    sourcePreferences.recommendationSourceLanguages().get(),
                )
                val blockExplicit = sourcePreferences.blockExplicitPornHentaiSources().get()
                val currentFingerprint = SourceEvaluationContinuationPolicy.buildFilterFingerprint(
                    languages = recLanguages,
                    includeExplicit = s.options.includeExplicitCandidates,
                    skipAlreadyEvaluated = s.options.skipAlreadyEvaluated,
                    reEvaluateStale = s.options.reEvaluateStale,
                    onlyUpdatedEvaluated = s.options.onlyUpdatedEvaluated,
                    blockExplicit = blockExplicit,
                )
                val canContinue = SourceEvaluationContinuationPolicy.canContinue(
                    candidates = allCandidates,
                    cursor = newCursor,
                    currentFingerprint = currentFingerprint,
                )
                val remaining = SourceEvaluationContinuationPolicy.remainingCount(
                    candidates = allCandidates,
                    cursor = newCursor,
                    currentFingerprint = currentFingerprint,
                )
                mutableState.update {
                    it.copy(
                        continuationCursor = newCursor,
                        canContinue = canContinue,
                        remainingCandidateCount = remaining,
                    )
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "KMK SourceEvaluation: failed to update cursor" }
            }
        }
    }
    // KMK <--

    // KMK --> v0.6.20: reassessment + management controls

    private fun updateReassessmentBaseline() {
        screenModelScope.launch {
            try {
                val allTastes = getMangaTaste.awaitAll()
                val now = System.currentTimeMillis()
                sourcePreferences.sourceEvaluationLastReassessmentRatingCount().set(allTastes.size)
                sourcePreferences.sourceEvaluationLastReassessmentAt().set(now)
                mutableState.update {
                    it.copy(
                        currentRatedCount = allTastes.size,
                        reassessmentBaselineCount = allTastes.size,
                    )
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "KMK SourceEvaluation: failed to update reassessment baseline" }
            }
        }
    }

    fun toggleManagementSection() {
        mutableState.update { it.copy(showManagementSection = !it.showManagementSection) }
    }

    fun requestManagementAction(action: ManagementAction) {
        mutableState.update { it.copy(managementConfirmAction = action) }
    }

    fun dismissManagementConfirm() {
        mutableState.update { it.copy(managementConfirmAction = null) }
    }

    fun confirmManagementAction() {
        val action = state.value.managementConfirmAction ?: return
        mutableState.update { it.copy(managementConfirmAction = null) }
        screenModelScope.launch {
            when (action) {
                ManagementAction.RESET_DISLIKED_SOURCES -> {
                    sourcePreferences.dislikedRecommendationSourceKeys().set("")
                }
                ManagementAction.RESET_REASSESSMENT_BASELINE -> {
                    sourcePreferences.sourceEvaluationLastReassessmentRatingCount().set(0)
                    sourcePreferences.sourceEvaluationLastReassessmentAt().set(0L)
                    mutableState.update { it.copy(reassessmentBaselineCount = 0) }
                }
                ManagementAction.CLEAR_SEEN_MANGA -> {
                    sourcePreferences.seenRecommendationMangaKeys().set("")
                }
                // KMK --> v0.7.18
                ManagementAction.CLEAR_DISMISSED_SUGGESTIONS -> {
                    sourcePreferences.dismissedNonInstalledRecommendationSources().set("")
                    mutableState.update { it.copy(dismissedSuggestionCount = 0) }
                }
                ManagementAction.CLEAR_DISLIKED_SUGGESTIONS -> {
                    sourcePreferences.dislikedRecommendationSourceKeys().set("")
                    mutableState.update { it.copy(dislikedSuggestionCount = 0) }
                }
                // KMK <--
            }
        }
    }
    // KMK <--

    // KMK --> v0.6.16: crash quarantine screen actions

    fun clearScreenError() {
        mutableState.update { it.copy(screenError = null) } // KMK v0.7.18
    }

    fun showUnsafeSourcesDialog() {
        mutableState.update { it.copy(showUnsafeSourcesDialog = true) }
    }

    fun dismissUnsafeSourcesDialog() {
        mutableState.update { it.copy(showUnsafeSourcesDialog = false) }
    }

    fun requestClearUnsafeSources() {
        mutableState.update { it.copy(showClearUnsafeDialog = true) }
    }

    fun dismissClearUnsafeDialog() {
        mutableState.update { it.copy(showClearUnsafeDialog = false) }
    }

    fun confirmClearUnsafeSources() {
        mutableState.update { it.copy(showClearUnsafeDialog = false, showUnsafeSourcesDialog = false) }
        screenModelScope.launch {
            try {
                clearSourceEvaluationUnsafe.await()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "KMK SourceEvaluation: clearUnsafe failed" }
            }
        }
    }

    fun deleteUnsafeSource(key: String) {
        screenModelScope.launch {
            try {
                deleteSourceEvaluationUnsafe.await(key)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "KMK SourceEvaluation: deleteUnsafe($key) failed" }
            }
        }
    }

    // KMK --> v0.6.18: package-level blocked extension actions

    fun showBlockedPackagesDialog() {
        mutableState.update { it.copy(showBlockedPackagesDialog = true) }
    }

    fun dismissBlockedPackagesDialog() {
        mutableState.update { it.copy(showBlockedPackagesDialog = false) }
    }

    fun requestClearBlockedPackages() {
        mutableState.update { it.copy(showClearBlockedPackagesDialog = true) }
    }

    fun dismissClearBlockedPackagesDialog() {
        mutableState.update { it.copy(showClearBlockedPackagesDialog = false) }
    }

    fun confirmClearBlockedPackages() {
        mutableState.update { it.copy(showClearBlockedPackagesDialog = false, showBlockedPackagesDialog = false) }
        screenModelScope.launch {
            try {
                clearUnsafeExtensionPackages.await()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "KMK SourceEvaluation: clearBlockedPackages failed" }
            }
        }
    }

    fun allowBlockedPackage(pkgName: String) {
        screenModelScope.launch {
            try {
                deleteUnsafeExtensionPackage.await(pkgName)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "KMK SourceEvaluation: allowBlockedPackage($pkgName) failed" }
            }
        }
    }
    // KMK <--

    fun copyDiagnosticsToClipboard() {
        val s = state.value
        val text = SourceEvaluationDiagnosticsBuilder.build(
            kmkVersion = KmkRecsReleaseNotes.VERSION_NAME,
            installerMode = s.options.installerMode.name,
            batchSize = s.options.batchSize,
            skipAlreadyEvaluated = s.options.skipAlreadyEvaluated,
            includeExplicit = s.options.includeExplicitCandidates,
            candidateCount = s.candidates.size,
            evalResultCount = s.evaluations.size,
            unsafeCount = s.unsafeSources.size,
            unsafeHiddenCount = s.candidateDiagnostics.unsafeHiddenCount,
            // KMK --> v0.6.18
            blockedPackageCount = s.blockedPackages.size,
            dcmIsStaticallyBlocked = eu.kanade.tachiyomi.extension.util.KnownUnsafeExtensionPackages.isKnownUnsafe(
                eu.kanade.tachiyomi.extension.util.KnownUnsafeExtensionPackages.DIGITAL_COMIC_MUSEUM.pkgName,
            ),
            // KMK <--
            queuePhase = s.queueState.currentPhase?.name ?: "Idle",
            currentExtension = s.queueState.currentExtensionName,
            currentSource = s.queueState.currentSourceName,
            shizukuInstalled = s.shizukuState.installed,
            shizukuBinderAlive = s.shizukuState.binderAlive,
            shizukuPermGranted = s.shizukuState.permissionGranted,
            lastError = s.queueState.errorMessage,
            lastProbeMarker = null,
        )
        context.copyToClipboard("Source Evaluation Diagnostics", text)
    }

    // KMK <--

    // KMK --> v0.6.11: Shizuku temporary-use actions

    /** Switch Source Evaluation to Shizuku installer mode for this run. */
    fun useShizukuForEvaluation() {
        mutableState.update { it.copy(options = it.options.copy(installerMode = SourceEvaluationInstallerPolicy.InstallerMode.SHIZUKU)) }
        refreshInstallerPolicy()
    }

    /**
     * Switch Source Evaluation away from Shizuku to a safe fallback.
     * Prefers PRIVATE when available, otherwise CURRENT.
     * Does NOT change the global extension installer preference.
     */
    fun stopUsingShizukuForEvaluation() {
        val privateAvailable = BasePreferences.ExtensionInstaller.PRIVATE in
            basePreferences.extensionInstaller().entries
        val fallback = ShizukuSetupHelper.stopUsingFallbackMode(privateAvailable)
        mutableState.update { it.copy(options = it.options.copy(installerMode = fallback)) }
        refreshInstallerPolicy()
    }

    /** Open the Shizuku download/setup page. */
    fun openShizukuSetup() {
        ShizukuSetupHelper.openDownload(context)
    }

    /**
     * Open the installed Shizuku app; falls back to setup/download page if not installed.
     */
    fun openShizukuApp() {
        val opened = ShizukuSetupHelper.openApp(context)
        if (!opened) ShizukuSetupHelper.openDownload(context)
    }

    /**
     * Launch Android's uninstall confirmation for Shizuku.
     * Does nothing if evaluation is currently running.
     * After the intent is dispatched, refresh Shizuku/policy state.
     */
    fun uninstallShizuku() {
        if (state.value.queueState.isRunning) return
        ShizukuSetupHelper.openUninstall(context)
        // Refresh after dispatch; actual uninstall outcome is unknown until user returns
        refreshInstallerPolicy()
    }

    /** Re-read Shizuku state and recompute policy. Call after returning from Shizuku setup. */
    fun refreshShizukuState() {
        refreshInstallerPolicy()
    }

    // KMK <--

    // KMK --> v0.6.12: apply option-based candidate filters reactively
    private fun applyOptionsAndUpdateState() {
        val pool = lastCandidatePool.value ?: return
        val opts = state.value.options
        val now = System.currentTimeMillis()
        val result = SourceEvaluationCandidateFilter.applyOptions(
            pool = pool,
            includeExplicit = opts.includeExplicitCandidates,
            skipAlreadyEvaluated = opts.skipAlreadyEvaluated,
            reEvaluateStale = opts.reEvaluateStale,
            now = now,
            // KMK --> v0.7.4
            onlyUpdatedEvaluated = opts.onlyUpdatedEvaluated,
            // KMK <--
        )
        // KMK --> v0.7.4: compute how many eligible evaluated extensions have a newer available version
        val updatedCount = pool.allEligible.count { c ->
            val extKey = "${c.extension.signatureHash}|${c.extension.pkgName}"
            val evals = pool.evaluationsByExtensionKey[extKey]
            if (evals.isNullOrEmpty()) return@count false
            val snapshots = evals.map { exh.recs.evaluation.SourceEvaluationUpdatePolicy.fromEvaluation(it) }
            val available = exh.recs.evaluation.SourceEvaluationUpdatePolicy.AvailableExtensionSnapshot(
                versionCode = c.extension.versionCode,
                signatureHash = c.extension.signatureHash,
                pkgName = c.extension.pkgName,
            )
            exh.recs.evaluation.SourceEvaluationUpdatePolicy.detectForPool(snapshots, available) ==
                exh.recs.evaluation.SourceEvaluationUpdatePolicy.UpdateStatus.UPDATED
        }
        // KMK <--
        // KMK --> v0.7.6: compute canContinue and remaining count
        val recLanguages = RecommendationSourceFilter.normalizeLanguages(
            sourcePreferences.recommendationSourceLanguages().get(),
        )
        val blockExplicit = sourcePreferences.blockExplicitPornHentaiSources().get()
        val fingerprint = SourceEvaluationContinuationPolicy.buildFilterFingerprint(
            languages = recLanguages,
            includeExplicit = opts.includeExplicitCandidates,
            skipAlreadyEvaluated = opts.skipAlreadyEvaluated,
            reEvaluateStale = opts.reEvaluateStale,
            onlyUpdatedEvaluated = opts.onlyUpdatedEvaluated,
            blockExplicit = blockExplicit,
        )
        val currentCursor = state.value.continuationCursor
        val canContinue = SourceEvaluationContinuationPolicy.canContinue(result.candidates, currentCursor, fingerprint)
        val remaining = SourceEvaluationContinuationPolicy.remainingCount(result.candidates, currentCursor, fingerprint)
        // KMK <--

        // KMK --> v0.7.18: warn when candidates empty AND available extensions list is also empty
        val repoUnavailable = result.candidates.isEmpty() &&
            pool.allEligible.isEmpty() &&
            extensionManager.availableExtensionsFlow.value.isEmpty()
        // KMK <--

        mutableState.update { s ->
            s.copy(
                candidates = result.candidates,
                isLoadingCandidates = false,
                candidateDiagnostics = CandidateDiagnostics(
                    totalEligible = pool.allEligible.size,
                    evaluatedHiddenCount = result.evaluatedHiddenCount,
                    explicitHiddenCount = result.explicitHiddenCount,
                    dislikedHiddenCount = pool.dislikedHiddenCount,
                    // KMK --> v0.6.16: crash quarantine
                    unsafeHiddenCount = pool.unsafeHiddenCount,
                    // KMK <--
                ),
                // KMK --> v0.7.4
                updatedEvaluatedExtensionCount = updatedCount,
                // KMK <--
                // KMK --> v0.7.6: continuation state
                canContinue = canContinue,
                remainingCandidateCount = remaining,
                // KMK <--
                // KMK --> v0.7.18
                repoUnavailableWarning = repoUnavailable && s.screenError == null,
                // KMK <--
            )
        }
        // KMK --> v0.7.6: reapply display filter when candidates/evaluations change
        applyDisplayFilter()
        // KMK <--
    }
    // KMK <--

    private fun refreshInstallerPolicy() {
        val mode = state.value.options.installerMode
        val currentInstaller = basePreferences.extensionInstaller().get()
        val privateAvailable = BasePreferences.ExtensionInstaller.PRIVATE in
            basePreferences.extensionInstaller().entries

        // KMK --> v0.6.11: read real Shizuku state instead of hardcoded false values
        val shizukuState = ShizukuSetupHelper.readState(context)
        // KMK <--

        val policy = SourceEvaluationInstallerPolicy.validate(
            mode = mode,
            currentGlobalInstaller = currentInstaller,
            privateAvailable = privateAvailable,
            shizukuInstalled = shizukuState.installed,
            shizukuBinderAlive = shizukuState.binderAlive,
            shizukuPermissionGranted = shizukuState.permissionGranted,
        )
        // KMK --> v0.6.13: expose privateAvailable for dialog actions and Shizuku card
        // KMK --> v0.6.19: auto-show Shizuku card when SHIZUKU mode or CURRENT+global Shizuku
        val autoShowShizuku = mode == SourceEvaluationInstallerPolicy.InstallerMode.SHIZUKU ||
            (
                mode == SourceEvaluationInstallerPolicy.InstallerMode.CURRENT &&
                    currentInstaller == BasePreferences.ExtensionInstaller.SHIZUKU
                )
        mutableState.update { s ->
            s.copy(
                installerPolicy = policy,
                shizukuState = shizukuState,
                privateAvailable = privateAvailable,
                showShizukuSetup = s.showShizukuSetup || autoShowShizuku,
            )
        }
        // KMK <--
    }
}
// KMK <--

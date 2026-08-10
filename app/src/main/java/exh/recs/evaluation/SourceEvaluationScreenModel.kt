package exh.recs.evaluation

import android.content.Context
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.SourceRuntimeFailureRegistry
import eu.kanade.tachiyomi.source.SourceRuntimeHealthIssue
import eu.kanade.tachiyomi.source.SourceRuntimeHealthReporter
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.isOnline
import exh.recs.KmkRecsReleaseNotes
import exh.recs.RecommendationSourceFilter
import exh.util.NonUndoableEvent
import exh.util.NonUndoableEventJournal
import exh.util.NonUndoableEventType
import exh.util.installAndRecordUserInitiated
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
import tachiyomi.domain.taste.interactor.GetTasteProfile
import tachiyomi.domain.taste.interactor.GetUnsafeExtensionPackages
import tachiyomi.domain.taste.interactor.MarkSourceEvaluationUnsafe
import tachiyomi.domain.taste.interactor.UpsertUnsafeExtensionPackage
import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationUnsafeSource
import tachiyomi.domain.taste.model.SourceRecommendationFit
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
    // KMK --> v0.7.43: Source Evaluation and For You compatibility checks may not install
    // extensions at the same time; surface which job is already running.
    enum class ActiveJobKind { SOURCE_EVALUATION, RECOMMENDATION_QUALITY }
    data class JobConflict(val activeJob: ActiveJobKind) : ScreenErrorKey
    // KMK <--
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
    // KMK <--
    // KMK v0.8.10-fix5: user-confirmed "disable extension loading" writes here, same infrastructure
    // package-level crash-quarantine already uses.
    private val upsertUnsafeExtensionPackage: UpsertUnsafeExtensionPackage = Injekt.get(),
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
        // KMK v0.8.14: Phase E visual-fog reduction -- "skip evaluated"/"include explicit"/candidate
        // diagnostics and the installer-mode selector/Shizuku toggle are secondary setup detail, not
        // primary actions; collapsed behind disclosures by default so the first screen reads as
        // status + primary action. Batch size stays visible (it's a search anchor target and a common
        // first adjustment). See SourceEvaluationScreen's class doc / itemKeysInOrder.
        val showSetupOptions: Boolean = false,
        val showInstallerDetails: Boolean = false,
        // KMK --> v0.7.4: count of evaluated extensions that have a newer available version
        val updatedEvaluatedExtensionCount: Int = 0,
        // KMK <--
        // KMK v0.8.10-fix5: non-blocking source-runtime health warning + details dialog.
        val runtimeHealthIssues: List<SourceRuntimeHealthIssue> = emptyList(),
        val showRuntimeHealthDialog: Boolean = false,
        /** Package names with a matching available extension -- "Reinstall" is only valid for these. */
        val runtimeHealthReinstallablePackages: Set<String> = emptySet(),
        // KMK --> v0.7.6: continuation cursor + installed-source display filter + rec-quality
        val continuationCursor: SourceEvaluationCursor? = null,
        val canContinue: Boolean = false,
        val remainingCandidateCount: Int = 0,
        val showInstalled: Boolean = false,
        val installedExtensionKeys: Set<String> = emptySet(),
        // KMK v0.8.15-fix1: extension keys ("sig|pkg") currently present in the available-extensions
        // repository listing -- used to decide per-row Install eligibility (an extension can
        // disappear from a repo after being evaluated). See SourceEvaluationRowActionPolicy.
        val availableExtensionKeys: Set<String> = emptySet(),
        // KMK v0.8.15-fix1: pkgNames currently installing from a Source Evaluation row's Install
        // action, so the button can show progress and avoid duplicate taps.
        val installingPkgNames: Set<String> = emptySet(),
        val filteredEvaluations: List<SourceEvaluation> = emptyList(),
        val hiddenInstalledCount: Int = 0,
        val recommendationFitsByEvalKey: Map<String, SourceRecommendationFit> = emptyMap(),
        // KMK --> v0.7.13: compact diagnostics summary for rec-quality section
        val recQualityDiagnostics: SourceRecommendationQualityDiagnostics.Summary? = null,
        // KMK <--
        // KMK <--
        // KMK --> v0.7.7: rec-quality in-screen action progress
        // KMK --> v0.7.43: now driven by SourceRecommendationQualityJobState (background job),
        // not screenModelScope — see SourceRecommendationQualityJob / SourceRecommendationQualityRunner.
        val recQualityRunning: Boolean = false,
        val recQualityProgress: Int = 0,
        val recQualityTotal: Int = 0,
        val recQualityCurrentSourceName: String? = null,
        // KMK <--
        // KMK --> v0.7.45: non-installed probes skipped because Private installer was unavailable
        val recQualityNonInstalledSkippedCount: Int = 0,
        // KMK <--
        // KMK <--
        // KMK --> v0.7.11: pre-run consent dialog and pending action
        val showConsentDialog: Boolean = false,
        val pendingConsentAction: PendingConsentAction? = null,
        // KMK <--
        // KMK --> SEC-01 v0.7.16: leftover extension pkg name (process-death survivor, needs manual uninstall)
        val leftoverPkgName: String? = null,
        // KMK <--
        // KMK --> v0.7.31: C3 — true when profile changed notably since last eval run
        val profileChangedSinceLastEval: Boolean = false,
        // KMK <--
        // KMK --> v0.8.1-fix3: separate first-class stale/outdated reassessment queue, independent
        // of the unassessed queue's candidates/cursor/canContinue. See
        // SOURCE_EVALUATION_CONTINUATION_FIX_PLAN — "stale/outdated rows must remain actionable
        // until reassessed, not merely counted as already-evaluated-hidden."
        val staleCandidates: List<EvaluationCandidate> = emptyList(),
        val continuationCursorStale: SourceEvaluationCursor? = null,
        val canContinueStale: Boolean = false,
        val remainingStaleCandidateCount: Int = 0,
        val pendingStaleReassessmentAfterPromptWarning: Boolean = false,
        // KMK <--
        // KMK v0.8.8: reconciles visible "Outdated" rows against actual reassessment-queue
        // eligibility — see SourceEvaluationOutdatedReconciliation.
        val outdatedReconciliation: SourceEvaluationOutdatedReconciliation.Result = SourceEvaluationOutdatedReconciliation.Result(emptySet(), emptySet()),
        // KMK v0.8.1-fix4: source/library-quality preference axis on Source Evaluation rows
        val qualityDislikedSourceKeys: Set<String> = emptySet(),
        val qualityExplicitSourceKeys: Set<String> = emptySet(),
        val showSourceQualityDisliked: Boolean = false,
        val hiddenSourceQualityCount: Int = 0,
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
        STALE_REASSESSMENT, // KMK v0.8.1-fix3
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
                logcat(LogPriority.ERROR) { "source_evaluation table unavailable in SourceEvaluationScreen" }
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
                logcat(LogPriority.ERROR) { "source_evaluation_unsafe_source table unavailable in SourceEvaluationScreen" }
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
                logcat(LogPriority.ERROR) { "unsafe_extension_package table unavailable in SourceEvaluationScreen" }
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN) { "KMK SourceEvaluation: failed to load taste profile for confidence check" }
            }
        }
        // KMK <--

        // KMK v0.8.10-fix5: observe process-lifetime source-runtime failures for the non-blocking
        // health warning.
        SourceRuntimeHealthReporter.issuesFlow(extensionManager)
            .onEach { issues ->
                val availablePkgNames = extensionManager.availableExtensionsFlow.value.map { it.pkgName }.toSet()
                val reinstallable = issues.mapNotNull { it.packageName }.filter { it in availablePkgNames }.toSet()
                mutableState.update {
                    it.copy(runtimeHealthIssues = issues, runtimeHealthReinstallablePackages = reinstallable)
                }
            }
            .launchIn(screenModelScope)
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

        // KMK --> v0.7.43: reconnect to any active background For You compatibility job
        SourceRecommendationQualityJobState.activeQueueState
            .onEach { jobQueueState ->
                mutableState.update {
                    it.copy(
                        recQualityRunning = jobQueueState?.isRunning == true,
                        recQualityProgress = jobQueueState?.completedCount ?: 0,
                        recQualityTotal = jobQueueState?.totalCount ?: 0,
                        recQualityCurrentSourceName = jobQueueState?.currentSourceName,
                        recQualityNonInstalledSkippedCount = jobQueueState?.nonInstalledSkippedCount ?: 0,
                    )
                }
                if (jobQueueState?.isTerminal == true) {
                    loadRecommendationFits()
                }
            }
            .launchIn(screenModelScope)
        // KMK <--

        // KMK --> v0.7.6: load persisted cursor
        screenModelScope.launch {
            try {
                val raw = sourcePreferences.sourceEvaluationContinuationCursor().get()
                val cursor = SourceEvaluationContinuationPolicy.deserialize(raw)
                // KMK --> v0.8.1-fix3: load stale-queue cursor from its own preference slot
                val rawStale = sourcePreferences.sourceEvaluationContinuationCursorStale().get()
                val cursorStale = SourceEvaluationContinuationPolicy.deserialize(rawStale)
                mutableState.update { it.copy(continuationCursor = cursor, continuationCursorStale = cursorStale) }
                // KMK <--
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN) { "KMK SourceEvaluation: failed to load cursor" }
            }
        }
        loadRecommendationFits()
        // KMK <--

        // KMK v0.8.1-fix4: live-update the source/library-quality axis and re-derive display state
        kotlinx.coroutines.flow.combine(
            sourcePreferences.dislikedSourceQualityKeys().changes(),
            sourcePreferences.explicitSourceQualityKeys().changes(),
        ) { dislikedRaw, explicitRaw ->
            exh.recs.sourceprefs.RecommendationSourcePreferenceStore.parse(dislikedRaw) to
                exh.recs.sourceprefs.RecommendationSourcePreferenceStore.parse(explicitRaw)
        }.onEach { (disliked, explicit) ->
            mutableState.update { it.copy(qualityDislikedSourceKeys = disliked, qualityExplicitSourceKeys = explicit) }
            applyDisplayFilter()
        }.launchIn(screenModelScope)
        // KMK <--

        // KMK --> SEC-01 v0.7.16: surface leftover-extension warning if startup recovery detected one
        screenModelScope.launch {
            try {
                val pkg = sourcePreferences.sourceEvaluationLeftoverPkg().get()
                if (pkg.isNotBlank()) {
                    mutableState.update { it.copy(leftoverPkgName = pkg) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN) { "KMK SourceEvaluation: failed to load leftover pkg preference" }
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

        // KMK v0.8.15-fix1: keep availableExtensionKeys in sync -- used for per-row Install
        // eligibility (SourceEvaluationRowActionPolicy).
        extensionManager.availableExtensionsFlow
            .onEach { available ->
                val keys = available.map { "${it.signatureHash}|${it.pkgName}" }.toSet()
                mutableState.update { it.copy(availableExtensionKeys = keys) }
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN) { "KMK SourceEvaluation: failed to load suggestion counts" }
            }
        }
        // KMK <--

        // KMK --> v0.6.20: load reassessment state
        screenModelScope.launch {
            try {
                val allTastes = getMangaTaste.awaitAll()
                val baseline = sourcePreferences.sourceEvaluationLastReassessmentRatingCount().get()
                // KMK --> v0.7.31: C3 — detect profile change since last eval run
                val lastRunCount = sourcePreferences.sourceEvaluationLastRunRatingCount().get()
                val profileChanged = lastRunCount >= 0 && kotlin.math.abs(allTastes.size - lastRunCount) >= 5
                // KMK <--
                mutableState.update {
                    it.copy(
                        currentRatedCount = allTastes.size,
                        reassessmentBaselineCount = baseline,
                        profileChangedSinceLastEval = profileChanged,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN) { "KMK SourceEvaluation: failed to load reassessment state" }
            }
        }
        // KMK <--

        // KMK --> v0.6.12: load candidates from broad available extension pool
        getSourceEvaluationCandidates.subscribe()
            .catch { e ->
                logcat(LogPriority.ERROR) { "Failed to load source evaluation candidates" }
                mutableState.update {
                    it.copy(
                        candidates = emptyList(),
                        isLoadingCandidates = false,
                        // KMK --> v0.7.18
                        screenError = ScreenErrorKey.CandidateLoadFailed(SourceEvaluationProbeErrorClassifier.classifyToStorageKey(e)),
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
            logcat(LogPriority.ERROR) { "KMK SourceEvaluation: refreshInstallerPolicy failed in init" }
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
        // KMK --> v0.8.1-fix3
        val staleRun = state.value.pendingStaleReassessmentAfterPromptWarning
        val isContinuation = if (staleRun) state.value.canContinueStale else false
        mutableState.update { it.copy(showPromptHeavyWarningDialog = false, pendingStaleReassessmentAfterPromptWarning = false) }
        launchEvaluation(promptHeavyCleanupAllowed = true, isContinuation = isContinuation, staleRun = staleRun)
        // KMK <--
    }

    /** User chose to switch to Private instead of continuing with prompt-heavy mode. */
    fun switchToPrivateAndStart() {
        // KMK --> v0.8.1-fix3
        val staleRun = state.value.pendingStaleReassessmentAfterPromptWarning
        val isContinuation = if (staleRun) state.value.canContinueStale else false
        // KMK <--
        mutableState.update {
            it.copy(
                showPromptHeavyWarningDialog = false,
                pendingStaleReassessmentAfterPromptWarning = false, // KMK v0.8.1-fix3
                options = it.options.copy(installerMode = SourceEvaluationInstallerPolicy.InstallerMode.PRIVATE),
                showShizukuSetup = false, // KMK --> v0.6.19
            )
        }
        refreshInstallerPolicy()
        launchEvaluation(promptHeavyCleanupAllowed = false, isContinuation = isContinuation, staleRun = staleRun)
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
        mutableState.update { it.copy(showPromptHeavyWarningDialog = false, pendingStaleReassessmentAfterPromptWarning = false) }
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
            PendingConsentAction.STALE_REASSESSMENT -> startOrContinueStaleReassessment() // KMK v0.8.1-fix3
            PendingConsentAction.VIEW_ONLY, null -> { /* view-only — never starts evaluation */ }
        }
    }

    // KMK <--

    // KMK --> v0.8.1-fix3: staleRun selects the stale/outdated reassessment queue instead of the
    // normal unassessed queue — separate candidate list, cursor slot, and fingerprint, so neither
    // queue's continuation progress can clobber the other's. See SOURCE_EVALUATION_CONTINUATION_FIX_PLAN.
    private fun launchEvaluation(promptHeavyCleanupAllowed: Boolean, isContinuation: Boolean = false, staleRun: Boolean = false) {
        // KMK --> v0.7.43/v0.7.44: Source Evaluation and For You compatibility checks both may
        // temporarily install/uninstall extensions and must not run at the same time. The decision
        // itself is a pure, tested policy (SourceRecommendationQualityJobConflictPolicyTest);
        // only the isRunning() reads here are Android/WorkManager-backed.
        val evaluationConflict = SourceRecommendationQualityJobConflictPolicy.conflictFor(
            starting = ScreenErrorKey.ActiveJobKind.SOURCE_EVALUATION,
            sourceEvaluationRunning = false,
            recommendationQualityRunning = SourceRecommendationQualityJob.isRunning(context),
        )
        if (evaluationConflict != null) {
            mutableState.update { it.copy(screenError = ScreenErrorKey.JobConflict(evaluationConflict)) }
            return
        }
        // KMK <--
        val s = state.value
        // KMK --> v0.8.1-fix3: stale reassessment always treats candidates as already-evaluated-but-
        // stale, regardless of the user's persisted skipAlreadyEvaluated/reEvaluateStale toggles —
        // those toggles govern the *unassessed* queue only.
        val opts = if (staleRun) {
            s.options.copy(
                promptHeavyCleanupAllowed = promptHeavyCleanupAllowed,
                skipAlreadyEvaluated = false,
                reEvaluateStale = true,
                onlyUpdatedEvaluated = false,
            )
        } else {
            s.options.copy(promptHeavyCleanupAllowed = promptHeavyCleanupAllowed)
        }
        // KMK --> v0.7.6: slice via continuation policy before passing to runner
        val allCandidates = if (staleRun) s.staleCandidates else s.candidates
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
        ) + if (staleRun) "|queue=stale" else ""
        val cursor = if (isContinuation) {
            if (staleRun) s.continuationCursorStale else s.continuationCursor
        } else {
            null
        }
        val slice = SourceEvaluationContinuationPolicy.sliceForRun(
            candidates = allCandidates,
            batchSize = opts.batchSize,
            cursor = cursor,
            currentFingerprint = fingerprint,
        )
        // KMK <--
        // KMK --> v0.7.31: C3 — persist rated count so we can detect profile changes after this run
        sourcePreferences.sourceEvaluationLastRunRatingCount().set(s.currentRatedCount)
        mutableState.update { it.copy(profileChangedSinceLastEval = false) }
        // KMK <--
        // KMK --> v0.6.19: launch as WorkManager foreground job so evaluation continues after leaving screen
        SourceEvaluationJobState.pendingCandidates = slice
        SourceEvaluationJobState.pendingOptions = opts
        // KMK --> v0.7.6: store fingerprint and full candidate list so completion can advance cursor
        SourceEvaluationJobState.pendingCursorFingerprint = fingerprint
        SourceEvaluationJobState.pendingAllCandidates = allCandidates
        // KMK <--
        // KMK --> v0.8.1-fix3
        SourceEvaluationJobState.pendingIsStaleRun = staleRun
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

    // KMK --> v0.8.1-fix3: start or continue the stale/outdated reassessment queue. Explicit and
    // separate from startEvaluation()/continueEvaluation() (which only ever operate on the
    // unassessed queue), keeping restart/reassessment explicit and separate from continue.
    fun startOrContinueStaleReassessment() {
        val s = state.value
        if (s.staleCandidates.isEmpty()) return
        if (SourceEvaluationConsentPolicy.isConsentRequired(sourcePreferences.sourceEvaluationConsentGiven().get())) {
            mutableState.update { it.copy(showConsentDialog = true, pendingConsentAction = PendingConsentAction.STALE_REASSESSMENT) }
            return
        }
        if (!context.isOnline()) {
            mutableState.update { it.copy(screenError = ScreenErrorKey.Offline) }
            return
        }
        val policy = s.installerPolicy
        if (policy != null && policy.requiresPromptWarning && s.options.batchSize > 1) {
            mutableState.update { it.copy(showPromptHeavyWarningDialog = true, pendingStaleReassessmentAfterPromptWarning = true) }
            return
        }
        launchEvaluation(promptHeavyCleanupAllowed = false, isContinuation = s.canContinueStale, staleRun = true)
    }

    /** Explicitly restart the stale/outdated reassessment queue from the beginning, discarding its cursor. */
    fun restartStaleReassessment() {
        sourcePreferences.sourceEvaluationContinuationCursorStale().set("")
        mutableState.update { it.copy(continuationCursorStale = null, canContinueStale = false) }
        startOrContinueStaleReassessment()
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

    // KMK v0.8.10-fix5 -->
    /**
     * Source-runtime health diagnostics and recovery actions. Only offers
     * actions that are actually possible for a given issue -- never silently uninstalls, reinstalls,
     * or permanently blocks an extension.
     */
    fun showRuntimeHealthDialog() {
        mutableState.update { it.copy(showRuntimeHealthDialog = true) }
    }

    fun dismissRuntimeHealthDialog() {
        mutableState.update { it.copy(showRuntimeHealthDialog = false) }
    }

    /** Clears the process-lifetime runtime failure for [sourceId] so the next request tries again. */
    fun retryRuntimeHealthSource(sourceId: Long) {
        SourceRuntimeFailureRegistry.clear(sourceId)
    }

    fun updateRuntimeHealthExtension(pkgName: String) {
        val installedExt = extensionManager.installedExtensionsFlow.value.find { it.pkgName == pkgName }
            ?: return
        if (!installedExt.hasUpdate) return
        screenModelScope.launch {
            try {
                extensionManager.updateExtension(installedExt)
                    .takeWhile { !it.isCompleted() }
                    .collect()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN) { "KMK SourceEvaluation: runtime-health update failed" }
            }
        }
    }

    // KMK v0.8.15-fix1 -->
    /**
     * Installs a source directly from a Source Evaluation row's `Install` action, reusing the same
     * `extensionManager.installExtension(...)` path (and its existing installer safety/prompt
     * behavior) as everywhere else in the app -- no new installer. Only ever called for a row where
     * [SourceEvaluationRowActionPolicy.canOfferInstall] returned true, so this does not attempt to
     * re-install an already-installed, blocked/quarantined, or no-longer-available extension.
     */
    fun installEvaluatedSource(pkgName: String, signatureHash: String) {
        val availableExt = extensionManager.availableExtensionsFlow.value.find {
            it.pkgName == pkgName && it.signatureHash == signatureHash
        } ?: return
        mutableState.update { it.copy(installingPkgNames = it.installingPkgNames + pkgName) }
        screenModelScope.launch {
            try {
                // KMK v0.8.20-fix1: verify the terminal step is actually Installed (not just "completed",
                // which .isCompleted() also returns for Error/Idle) before recording a non-undoable
                // Action History event -- see exh.util.NonUndoableEventJournal's doc for why this
                // operation can never have an Undo action. Same terminal-step check already used by
                // SourceEvaluationRunner.installAndCheck/SourceRecommendationQualityRunner.
                // KMK: routed through the shared
                // installAndRecordUserInitiated() helper -- see its doc for why this was extracted.
                extensionManager.installAndRecordUserInitiated(availableExt) { sourcePreferences.evaluationMode().get() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN) { "KMK SourceEvaluation: install failed" }
            } finally {
                mutableState.update { it.copy(installingPkgNames = it.installingPkgNames - pkgName) }
            }
        }
    }
    // KMK <--

    // KMK: this is a real user-initiated install action (the
    // runtime-health recovery card's "Reinstall" button, wired from SourceEvaluationScreen.kt) --
    // previously it called extensionManager.installExtension(...) directly, bypassing the shared
    // recordUserInitiatedInstall()/recordPackageOperationReceipt() chain every other user-initiated
    // install call site uses (installEvaluatedSource() above, ExtensionsScreenModel.installExtension(),
    // RecommendationBundleImportScreenModel's install path). That meant a user recovering a broken
    // source via Reinstall got no Action History visibility at all -- unlike every sibling install
    // path. Fixed to route through the same shared exh.util.installAndRecordUserInitiated() helper
    // installEvaluatedSource() above now also uses -- records only on a verified InstallStep.Installed,
    // records nothing on cancellation/failure, and shares an id between the event and receipt for
    // follow-up eligibility.
    fun reinstallRuntimeHealthExtension(pkgName: String) {
        val availableExt = extensionManager.availableExtensionsFlow.value.find { it.pkgName == pkgName }
            ?: return
        screenModelScope.launch {
            try {
                extensionManager.installAndRecordUserInitiated(availableExt) { sourcePreferences.evaluationMode().get() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN) { "KMK SourceEvaluation: runtime-health reinstall failed" }
            }
        }
    }

    // KMK: previously fire-and-forget with no completion
    // signal at all, so uninstall was entirely unrepresented in Action History (a real, unsafe-by-
    // omission gap -- not a fake Undo, but a silent one). Now verifies removal via
    // extensionManager.installedExtensionsFlow before recording a non-undoable event -- see
    // exh.util.verifyAndRecordUninstall's doc for why the event is never recorded on the mere fact
    // that uninstall was requested.
    fun uninstallRuntimeHealthExtension(pkgName: String) {
        val installedExt = extensionManager.installedExtensionsFlow.value.find { it.pkgName == pkgName }
            ?: return
        extensionManager.uninstallExtension(installedExt)
        screenModelScope.launch {
            exh.util.verifyAndRecordUninstall(
                installedPackageNames = extensionManager.installedExtensionsFlow.map { installed -> installed.map { it.pkgName } },
                pkgName = pkgName,
                isEvaluationModeEnabled = { sourcePreferences.evaluationMode().get() },
                // KMK: typed
                // receipt identity for a future reinstall follow-up -- see ExtensionsScreenModel's
                // uninstallExtension() for the same pattern.
                signatureHash = installedExt.signatureHash,
                versionCode = installedExt.versionCode,
            )
        }
    }

    /** User-confirmed: blocks [pkgName] from loading again, using the existing unsafe-package store. */
    fun disableRuntimeHealthExtension(pkgName: String) {
        val installedExt = extensionManager.installedExtensionsFlow.value.find { it.pkgName == pkgName }
        screenModelScope.launch {
            val now = System.currentTimeMillis()
            upsertUnsafeExtensionPackage.await(
                UnsafeExtensionPackage(
                    pkgName = pkgName,
                    extensionName = installedExt?.name,
                    reason = "Repeated source-runtime failure (extension incompatible or missing a dependency)",
                    source = "manual",
                    removable = true,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }
    // KMK <--

    // KMK v0.8.10 -->
    /**
     * Called when the Source Evaluation screen is left (Composable dispose). If the run reached a
     * terminal status, this clears it exactly like [resetEvaluation] so the "Evaluation completed"
     * summary card does not persist stale across future visits -- see
     * [SourceEvaluationCompletionLifecyclePolicy]'s class doc. A still-Running/Cancelling job is left
     * completely untouched: it keeps reporting progress via the same background-job singleton as
     * before, exactly as v0.6.19 intended.
     */
    fun clearCompletionOnLeave() {
        val status = SourceEvaluationJobState.activeQueueState.value?.status ?: return
        if (SourceEvaluationCompletionLifecyclePolicy.shouldClearOnLeave(status)) {
            resetEvaluation()
        }
    }
    // KMK <--

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
            try {
                clearSourceEvaluations.await()
                recordDataClearedEventIfEligible()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "KMK SourceEvaluation: clearEvaluations failed" }
            }
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
            // KMK v0.8.1-fix4
            qualityDislikedExtensionKeys = s.qualityDislikedSourceKeys,
            showSourceQualityDisliked = s.showSourceQualityDisliked,
        )
        mutableState.update {
            it.copy(
                filteredEvaluations = result.visible,
                hiddenInstalledCount = result.hiddenInstalledCount,
                hiddenSourceQualityCount = result.hiddenSourceQualityCount, // KMK v0.8.1-fix4
            )
        }
    }

    // KMK v0.8.1-fix4: source/library-quality actions on Source Evaluation rows -- separate axis
    // from recommendation-behavior dislike. History is preserved (no row deletion); marked rows are
    // hidden from future candidate pools and, by default, from the past-results list (recoverable
    // via setShowSourceQualityDisliked(true)).

    fun setShowSourceQualityDisliked(show: Boolean) {
        mutableState.update { it.copy(showSourceQualityDisliked = show) }
        applyDisplayFilter()
    }

    private fun currentSourceQualityState(): exh.recs.sourceprefs.SourceQualityMarkPolicy.State =
        exh.recs.sourceprefs.SourceQualityMarkPolicy.State(
            liked = exh.recs.sourceprefs.RecommendationSourcePreferenceStore.parse(sourcePreferences.likedSourceQualityKeys().get()),
            disliked = exh.recs.sourceprefs.RecommendationSourcePreferenceStore.parse(sourcePreferences.dislikedSourceQualityKeys().get()),
            explicit = exh.recs.sourceprefs.RecommendationSourcePreferenceStore.parse(sourcePreferences.explicitSourceQualityKeys().get()),
        )

    private fun writeSourceQualityState(state: exh.recs.sourceprefs.SourceQualityMarkPolicy.State) {
        sourcePreferences.likedSourceQualityKeys().set(
            exh.recs.sourceprefs.RecommendationSourcePreferenceStore.serialize(state.liked),
        )
        sourcePreferences.dislikedSourceQualityKeys().set(
            exh.recs.sourceprefs.RecommendationSourcePreferenceStore.serialize(state.disliked),
        )
        sourcePreferences.explicitSourceQualityKeys().set(
            exh.recs.sourceprefs.RecommendationSourcePreferenceStore.serialize(state.explicit),
        )
    }

    private fun journalSourceQualityChange(
        previous: exh.recs.sourceprefs.SourceQualityMarkPolicy.State,
        next: exh.recs.sourceprefs.SourceQualityMarkPolicy.State,
        identityKey: String,
    ) {
        if (!sourcePreferences.evaluationMode().get() || previous == next) return
        exh.util.PreferenceUndoJournal.record(
            exh.util.PreferenceUndoEntry(
                id = exh.util.PreferenceUndoEntry.newId(),
                timestamp = System.currentTimeMillis(),
                actionType = exh.util.PreferenceJournalActionType.SOURCE_QUALITY_MARK,
                identityKey = identityKey,
                previousValue = previous,
                expectedPostValue = next,
                readCurrent = ::currentSourceQualityState,
                restore = ::writeSourceQualityState,
            ),
        )
    }

    private fun applySourceQualityMark(evaluation: SourceEvaluation, poor: Boolean) {
        val key = "a|${evaluation.extensionKey}"
        val likedPref = sourcePreferences.likedSourceQualityKeys()
        val dislikedPref = sourcePreferences.dislikedSourceQualityKeys()
        val explicitPref = sourcePreferences.explicitSourceQualityKeys()
        val current = exh.recs.sourceprefs.SourceQualityMarkPolicy.State(
            liked = exh.recs.sourceprefs.RecommendationSourcePreferenceStore.parse(likedPref.get()),
            disliked = exh.recs.sourceprefs.RecommendationSourcePreferenceStore.parse(dislikedPref.get()),
            explicit = exh.recs.sourceprefs.RecommendationSourcePreferenceStore.parse(explicitPref.get()),
        )
        val next = if (poor) {
            exh.recs.sourceprefs.SourceQualityMarkPolicy.markPoor(current, key)
        } else {
            exh.recs.sourceprefs.SourceQualityMarkPolicy.markExplicit(current, key)
        }
        likedPref.set(exh.recs.sourceprefs.RecommendationSourcePreferenceStore.serialize(next.liked))
        dislikedPref.set(exh.recs.sourceprefs.RecommendationSourcePreferenceStore.serialize(next.disliked))
        explicitPref.set(exh.recs.sourceprefs.RecommendationSourcePreferenceStore.serialize(next.explicit))
        journalSourceQualityChange(current, next, key)
        mutableState.update { it.copy(qualityDislikedSourceKeys = next.disliked, qualityExplicitSourceKeys = next.explicit) }
        applyDisplayFilter()
        applyOptionsAndUpdateState()
    }

    fun markSourceQualityPoor(evaluation: SourceEvaluation) = applySourceQualityMark(evaluation, poor = true)

    fun markSourceQualityExplicit(evaluation: SourceEvaluation) = applySourceQualityMark(evaluation, poor = false)

    fun clearSourceQualityMark(evaluation: SourceEvaluation) {
        val key = "a|${evaluation.extensionKey}"
        val likedPref = sourcePreferences.likedSourceQualityKeys()
        val dislikedPref = sourcePreferences.dislikedSourceQualityKeys()
        val explicitPref = sourcePreferences.explicitSourceQualityKeys()
        val current = exh.recs.sourceprefs.SourceQualityMarkPolicy.State(
            liked = exh.recs.sourceprefs.RecommendationSourcePreferenceStore.parse(likedPref.get()),
            disliked = exh.recs.sourceprefs.RecommendationSourcePreferenceStore.parse(dislikedPref.get()),
            explicit = exh.recs.sourceprefs.RecommendationSourcePreferenceStore.parse(explicitPref.get()),
        )
        val next = exh.recs.sourceprefs.SourceQualityMarkPolicy.clear(current, key)
        likedPref.set(exh.recs.sourceprefs.RecommendationSourcePreferenceStore.serialize(next.liked))
        dislikedPref.set(exh.recs.sourceprefs.RecommendationSourcePreferenceStore.serialize(next.disliked))
        explicitPref.set(exh.recs.sourceprefs.RecommendationSourcePreferenceStore.serialize(next.explicit))
        journalSourceQualityChange(current, next, key)
        mutableState.update { it.copy(qualityDislikedSourceKeys = next.disliked, qualityExplicitSourceKeys = next.explicit) }
        applyDisplayFilter()
        applyOptionsAndUpdateState()
    }
    // KMK <--

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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN) { "KMK SourceEvaluation: failed to load recommendation fits" }
            }
        }
    }
    // KMK <--

    // KMK --> v0.7.7: evaluate recommendation quality for promising sources from this screen
    // KMK --> v0.7.42-fix2: reCheckAll now targets missing + outdated + checked (all eligible rows),
    // matching the behavior contract's "Recheck all is the only action targeting all eligible rows" requirement —
    // previously it targeted missing + checked only, silently skipping outdated rows because the
    // pre-fix2 queue conflated missing and outdated into one bucket.
    fun evaluateRecommendationQualityForPromising(reCheckAll: Boolean = false) {
        val s = state.value
        val queue = SourceRecommendationQualityQueue.compute(s.evaluations, s.recommendationFitsByEvalKey)
        val targets = if (reCheckAll) {
            queue.missingPromising + queue.outdatedPromising + queue.checkedPromising
        } else {
            queue.missingPromising
        }
        startRecQualityJob(targets)
    }
    // KMK <--

    // KMK --> v0.7.42-fix2: targeted recheck for stale-only fits — distinct from the normal "missing
    // only" check and from "Recheck all". Never touches missing or current rows.
    fun recheckOutdatedRecommendationQuality() {
        val s = state.value
        val queue = SourceRecommendationQualityQueue.compute(s.evaluations, s.recommendationFitsByEvalKey)
        startRecQualityJob(queue.outdatedPromising)
    }
    // KMK <--

    // KMK --> v0.7.43: enqueue SourceRecommendationQualityJob (WorkManager) instead of running the
    // probe loop in screenModelScope, so the check survives navigating away from this screen. Guards
    // concurrency (recQualityRunning), empty targets, and conflict with a running full Source
    // Evaluation job (both may temporarily install extensions and must not overlap).
    private fun startRecQualityJob(targets: List<SourceEvaluation>) {
        if (state.value.recQualityRunning) return
        if (targets.isEmpty()) return
        val qualityConflict = SourceRecommendationQualityJobConflictPolicy.conflictFor(
            starting = ScreenErrorKey.ActiveJobKind.RECOMMENDATION_QUALITY,
            sourceEvaluationRunning = SourceEvaluationJob.isRunning(context),
            recommendationQualityRunning = false,
        )
        if (qualityConflict != null) {
            mutableState.update { it.copy(screenError = ScreenErrorKey.JobConflict(qualityConflict)) }
            return
        }

        SourceRecommendationQualityJobState.pendingTargets = targets
        SourceRecommendationQualityJobState.activeQueueState.value = SourceRecommendationQualityQueueState(
            status = SourceRecommendationQualityQueueState.Status.Running,
            totalCount = targets.size,
        )
        SourceRecommendationQualityJob.start(context, state.value.options.installerMode)
    }
    // KMK <--

    // KMK --> v0.7.43: cancel a running background For You compatibility check
    fun cancelRecommendationQualityCheck() {
        SourceRecommendationQualityJob.cancel(context)
        SourceRecommendationQualityJobState.activeQueueState.value =
            SourceRecommendationQualityJobState.activeQueueState.value
                ?.copy(status = SourceRecommendationQualityQueueState.Status.Cancelled)
                ?: SourceRecommendationQualityQueueState(status = SourceRecommendationQualityQueueState.Status.Cancelled)
    }
    // KMK <--

    private fun updateContinuationCursor() {
        screenModelScope.launch {
            try {
                val s = state.value
                val completedKeys = SourceEvaluationJobState.lastCompletedCandidateKeys
                if (completedKeys.isEmpty()) return@launch
                val fingerprint = SourceEvaluationJobState.pendingCursorFingerprint ?: return@launch
                // KMK --> v0.8.1-fix3: route to the stale-queue cursor slot when this run was a
                // stale/outdated reassessment batch, so it never overwrites the unassessed cursor.
                val staleRun = SourceEvaluationJobState.pendingIsStaleRun
                val allCandidates = SourceEvaluationJobState.pendingAllCandidates
                    ?: if (staleRun) s.staleCandidates else s.candidates
                val priorCursor = if (staleRun) s.continuationCursorStale else s.continuationCursor
                val newCursor = SourceEvaluationContinuationPolicy.advanceCursor(
                    current = priorCursor,
                    completedKeys = completedKeys,
                    allCandidates = allCandidates,
                    currentFingerprint = fingerprint,
                )
                val serialized = SourceEvaluationContinuationPolicy.serialize(newCursor)
                if (staleRun) {
                    sourcePreferences.sourceEvaluationContinuationCursorStale().set(serialized)
                } else {
                    sourcePreferences.sourceEvaluationContinuationCursor().set(serialized)
                }

                val recLanguages = RecommendationSourceFilter.normalizeLanguages(
                    sourcePreferences.recommendationSourceLanguages().get(),
                )
                val blockExplicit = sourcePreferences.blockExplicitPornHentaiSources().get()
                val currentFingerprint = if (staleRun) {
                    fingerprint
                } else {
                    SourceEvaluationContinuationPolicy.buildFilterFingerprint(
                        languages = recLanguages,
                        includeExplicit = s.options.includeExplicitCandidates,
                        skipAlreadyEvaluated = s.options.skipAlreadyEvaluated,
                        reEvaluateStale = s.options.reEvaluateStale,
                        onlyUpdatedEvaluated = s.options.onlyUpdatedEvaluated,
                        blockExplicit = blockExplicit,
                    )
                }
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
                    if (staleRun) {
                        it.copy(
                            continuationCursorStale = newCursor,
                            canContinueStale = canContinue,
                            remainingStaleCandidateCount = remaining,
                        )
                    } else {
                        it.copy(
                            continuationCursor = newCursor,
                            canContinue = canContinue,
                            remainingCandidateCount = remaining,
                        )
                    }
                }
                // KMK <--
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN) { "KMK SourceEvaluation: failed to update cursor" }
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN) { "KMK SourceEvaluation: failed to update reassessment baseline" }
            }
        }
    }

    fun toggleManagementSection() {
        mutableState.update { it.copy(showManagementSection = !it.showManagementSection) }
    }

    // KMK v0.8.14 -->
    fun toggleSetupOptions() {
        mutableState.update { it.copy(showSetupOptions = !it.showSetupOptions) }
    }

    fun toggleInstallerDetails() {
        mutableState.update { it.copy(showInstallerDetails = !it.showInstallerDetails) }
    }
    // KMK <--

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
                recordDataClearedEventIfEligible()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "KMK SourceEvaluation: clearUnsafe failed" }
            }
        }
    }

    fun deleteUnsafeSource(key: String) {
        screenModelScope.launch {
            try {
                deleteSourceEvaluationUnsafe.await(key)
                recordDataClearedEventIfEligible()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "KMK SourceEvaluation: deleteUnsafe failed" }
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
                recordDataClearedEventIfEligible()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "KMK SourceEvaluation: clearBlockedPackages failed" }
            }
        }
    }

    fun allowBlockedPackage(pkgName: String) {
        screenModelScope.launch {
            try {
                deleteUnsafeExtensionPackage.await(pkgName)
                recordDataClearedEventIfEligible()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "KMK SourceEvaluation: allowBlockedPackage failed" }
            }
        }
    }

    private fun recordDataClearedEventIfEligible() {
        if (SourceEvaluationHistoryPolicy.shouldRecordDataClearedEvent(
                operationSucceeded = true,
                evaluationModeEnabled = sourcePreferences.evaluationMode().get(),
            )
        ) {
            NonUndoableEventJournal.record(
                NonUndoableEvent(
                    id = NonUndoableEvent.newId(),
                    timestamp = System.currentTimeMillis(),
                    eventType = NonUndoableEventType.SOURCE_EVALUATION_DATA_CLEARED,
                ),
            )
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
            evaluationModeEnabled = sourcePreferences.evaluationMode().get(),
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

        // KMK --> v0.8.1-fix3: compute the stale/outdated reassessment queue independently of the
        // unassessed-queue options above — see SourceEvaluationCandidateQueuePolicy doc.
        // KMK v0.8.15-fix1: actionable-only -- see SourceEvaluationCandidateQueuePolicy.staleCandidates
        // doc for the root-cause this closes.
        val staleCandidates = SourceEvaluationCandidateQueuePolicy.staleCandidates(
            pool,
            now,
            includeExplicit = opts.includeExplicitCandidates,
        )
        val staleFingerprint = SourceEvaluationContinuationPolicy.buildFilterFingerprint(
            languages = recLanguages,
            includeExplicit = opts.includeExplicitCandidates,
            skipAlreadyEvaluated = false,
            reEvaluateStale = true,
            onlyUpdatedEvaluated = false,
            blockExplicit = blockExplicit,
        ) + "|queue=stale"
        val currentCursorStale = state.value.continuationCursorStale
        val canContinueStale = SourceEvaluationContinuationPolicy.canContinue(staleCandidates, currentCursorStale, staleFingerprint)
        val remainingStale = SourceEvaluationContinuationPolicy.remainingCount(staleCandidates, currentCursorStale, staleFingerprint)
        // KMK <--

        // KMK v0.8.8: root-cause fix for "visible Outdated rows but Continue reassessing outdated
        // reports zero candidates" — see SourceEvaluationOutdatedReconciliation's class doc for the
        // full traced pipeline and confirmed cause. Reconciles the display-only "Outdated" row
        // classification against actual candidate-pool eligibility so the UI can explain a
        // zero-candidate outdated queue truthfully instead of it looking like a silent failure.
        val outdatedReconciliation = SourceEvaluationOutdatedReconciliation.reconcile(
            allEvaluations = state.value.evaluations,
            pool = pool,
            now = now,
            includeExplicit = opts.includeExplicitCandidates,
        )

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
                // KMK --> v0.8.1-fix3: stale/outdated reassessment queue state
                staleCandidates = staleCandidates,
                canContinueStale = canContinueStale,
                remainingStaleCandidateCount = remainingStale,
                // KMK <--
                // KMK v0.8.8
                outdatedReconciliation = outdatedReconciliation,
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

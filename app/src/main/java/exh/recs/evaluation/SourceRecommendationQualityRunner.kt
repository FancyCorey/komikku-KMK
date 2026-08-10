package exh.recs.evaluation

import android.content.Context
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.taste.interactor.GetTagAliases
import tachiyomi.domain.taste.interactor.GetTasteProfile
import tachiyomi.domain.taste.interactor.UpsertSourceRecommendationFit
import tachiyomi.domain.taste.model.RecommendationQualityVerdict
import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceRecommendationFit
import tachiyomi.domain.taste.model.TasteProfile
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK v0.7.44: bounded wait for availableExtensionsFlow to warm up — see loadAvailableExtensions()
private const val AVAILABLE_EXTENSIONS_WAIT_MS = 5_000L

// KMK v0.7.45: stable, non-exception-derived storage keys for SourceRecommendationFit.errorMessage
// (see the outer batch catch in start(), and the non-installed refusal branch in evaluateOne()).
// Recognized by SourceRecommendationFitFailureClassifier for badge display.
internal const val PROBE_ERROR_KEY = "Probe error"
internal const val NON_INSTALLED_REFUSED_KEY = "Private installer required for non-installed source checks"

// KMK --> v0.7.43
/**
 * Orchestrates one-at-a-time For You search compatibility probing for a fixed list of
 * [SourceEvaluation] targets.
 *
 * Extracted from `SourceEvaluationScreenModel.runRecQualityCheck`/`evaluateOneForRecQuality` so it
 * can run inside [SourceRecommendationQualityJob] (a WorkManager job) instead of `screenModelScope`,
 * the same way [SourceEvaluationRunner] backs [SourceEvaluationJob]. Behavior (installed-vs-temporary
 * install resolution, cleanup, per-source error isolation) is unchanged from the screen-model version.
 */
class SourceRecommendationQualityRunner(
    private val context: Context,
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val basePreferences: BasePreferences = Injekt.get(),
    private val getTasteProfile: GetTasteProfile = Injekt.get(),
    private val getTagAliases: GetTagAliases = Injekt.get(),
    private val upsertSourceRecommendationFit: UpsertSourceRecommendationFit = Injekt.get(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var runJob: Job? = null

    private val _state = MutableStateFlow(SourceRecommendationQualityQueueState())
    val state: StateFlow<SourceRecommendationQualityQueueState> = _state.asStateFlow()

    fun start(targets: List<SourceEvaluation>, installerMode: SourceEvaluationInstallerPolicy.InstallerMode) {
        if (runJob?.isActive == true) return
        if (targets.isEmpty()) return

        val privateAvailable = BasePreferences.ExtensionInstaller.PRIVATE in
            basePreferences.extensionInstaller().entries
        // KMK --> v0.7.45: public-safety fix — this job temporarily installs non-installed
        // extensions the same way Source Evaluation does, but its PromptRequired cleanup path was
        // log-only (see cleanupExtension below), so a Shizuku/Current-mode temp install could be
        // left behind with only a logcat line. Rather than fully replicating Source Evaluation's
        // cleanup-status/leftover-warning UX for a secondary background feature, non-installed
        // probes are now unconditionally forced to Private (which cleans up silently and
        // reliably) regardless of the user's chosen installerMode; if Private isn't available,
        // those targets are refused entirely instead of risking a Shizuku/Current leftover — see
        // evaluateOne's non-installed branch. Installed-source checks are unaffected since they
        // never install/uninstall anything.
        val installerOverride: BasePreferences.ExtensionInstaller? =
            if (privateAvailable) BasePreferences.ExtensionInstaller.PRIVATE else null
        // KMK <--

        _state.value = SourceRecommendationQualityQueueState(
            status = SourceRecommendationQualityQueueState.Status.Running,
            totalCount = targets.size,
        )

        runJob = scope.launch {
            try {
                val tasteProfile = getTasteProfile.await()
                val probe = SourceRecommendationFitProbe(getTagAliases)
                val availableExtensions = loadAvailableExtensions()

                for ((index, evaluation) in targets.withIndex()) {
                    if (_state.value.status == SourceRecommendationQualityQueueState.Status.Cancelling) break
                    _state.update { it.copy(currentSourceName = evaluation.sourceName) }
                    try {
                        evaluateOne(evaluation, probe, tasteProfile, availableExtensions, installerOverride, privateAvailable)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logcat(LogPriority.WARN) { "KMK SourceRecommendationQualityRunner: probe failed" }
                        writeErrorFit(evaluation, PROBE_ERROR_KEY)
                    }
                    _state.update { it.copy(completedCount = index + 1) }
                }

                if (_state.value.status == SourceRecommendationQualityQueueState.Status.Running) {
                    _state.update { it.copy(status = SourceRecommendationQualityQueueState.Status.Completed) }
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(status = SourceRecommendationQualityQueueState.Status.Cancelled) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "KMK SourceRecommendationQualityRunner: batch failed" }
                _state.update {
                    it.copy(
                        status = SourceRecommendationQualityQueueState.Status.Failed,
                        errorMessage = SourceEvaluationProbeErrorClassifier.classifyToStorageKey(e),
                    )
                }
            }
        }
    }

    fun cancel() {
        _state.update { it.copy(status = SourceRecommendationQualityQueueState.Status.Cancelling) }
        runJob?.cancel()
    }

    // KMK --> v0.7.44 Phase F: availableExtensionsFlow can still be empty this early if the job
    // starts right after app/extension-manager startup, before the repo list has loaded. A bounded
    // wait (existing extensionManager.availableExtensionsFlow, no new API) avoids writing a
    // misleading "Extension not found in available sources" error fit for every target just
    // because the check ran before the flow warmed up. Falls back to empty (existing behavior) if
    // it never populates within the timeout — non-installed targets then correctly get an error
    // fit instead of hanging.
    private suspend fun loadAvailableExtensions(): List<Extension.Available> {
        val fromManager = extensionManager.availableExtensionsFlow.value
        if (fromManager.isNotEmpty()) return fromManager
        return withTimeoutOrNull(AVAILABLE_EXTENSIONS_WAIT_MS) {
            extensionManager.availableExtensionsFlow.first { it.isNotEmpty() }
        } ?: emptyList()
    }
    // KMK <--

    private suspend fun evaluateOne(
        evaluation: SourceEvaluation,
        probe: SourceRecommendationFitProbe,
        tasteProfile: TasteProfile,
        availableExtensions: List<Extension.Available>,
        installerOverride: BasePreferences.ExtensionInstaller?,
        privateAvailable: Boolean,
    ) {
        // Installed path: probe directly without any install/cleanup
        val installedList = extensionManager.installedExtensionsFlow.value
        val installedResolve = SourceRecommendationQualityInstalledResolver.resolve(evaluation, installedList)
        if (installedResolve is SourceRecommendationQualityInstalledResolver.ResolveResult.Found) {
            val alreadyInstalled = installedResolve.extension
            when (val sr = SourceRecommendationQualitySourceResolver.resolve(alreadyInstalled, evaluation)) {
                is SourceRecommendationQualitySourceResolver.ResolveResult.Found -> {
                    val outcome = probe.probe(sr.source, tasteProfile)
                    upsertSourceRecommendationFit.await(buildFitFromOutcome(evaluation, outcome))
                }
                is SourceRecommendationQualitySourceResolver.ResolveResult.Ambiguous -> {
                    writeErrorFit(evaluation, "Source match ambiguous in installed extension: ${sr.reason}")
                }
                is SourceRecommendationQualitySourceResolver.ResolveResult.NotFound -> {
                    writeErrorFit(evaluation, "Source not found in installed extension")
                }
            }
            return
        }
        if (installedResolve is SourceRecommendationQualityInstalledResolver.ResolveResult.Ambiguous) {
            writeErrorFit(evaluation, "Installed extension match ambiguous: ${installedResolve.reason}")
            return
        }

        // KMK --> v0.7.45: non-installed probes require Private (see start()'s comment) — refuse
        // rather than temp-installing via Shizuku/Current, where a PromptRequired cleanup failure
        // could leave the extension behind with only a log line.
        if (!privateAvailable) {
            writeErrorFit(evaluation, NON_INSTALLED_REFUSED_KEY)
            _state.update { it.copy(nonInstalledSkippedCount = it.nonInstalledSkippedCount + 1) }
            return
        }
        // KMK <--

        // Non-installed path: resolve from available pool, install temporarily, probe, cleanup
        if (availableExtensions.isEmpty()) {
            writeErrorFit(evaluation, "Available extension list unavailable")
            return
        }
        val resolved = SourceRecommendationQualityExtensionResolver.resolve(evaluation, availableExtensions)
        val availableExt = when (resolved) {
            is SourceRecommendationQualityExtensionResolver.ResolveResult.NotFound -> {
                writeErrorFit(evaluation, "Extension not found in available sources")
                return
            }
            is SourceRecommendationQualityExtensionResolver.ResolveResult.Ambiguous -> {
                writeErrorFit(evaluation, "Extension match ambiguous: ${resolved.reason}")
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
            writeErrorFit(evaluation, "Install failed or timed out")
            return
        }

        val installedExt = withTimeoutOrNull(20_000L) {
            extensionManager.installedExtensionsFlow.first { installed ->
                installed.any { it.pkgName == availableExt.pkgName && it.signatureHash == availableExt.signatureHash }
            }.find { it.pkgName == availableExt.pkgName && it.signatureHash == availableExt.signatureHash }
        }

        if (installedExt == null) {
            writeErrorFit(evaluation, "Installed extension did not load")
            cleanupExtension(availableExt)
            return
        }

        try {
            when (val sr = SourceRecommendationQualitySourceResolver.resolve(installedExt, evaluation)) {
                is SourceRecommendationQualitySourceResolver.ResolveResult.Found -> {
                    val outcome = probe.probe(sr.source, tasteProfile)
                    upsertSourceRecommendationFit.await(buildFitFromOutcome(evaluation, outcome))
                }
                is SourceRecommendationQualitySourceResolver.ResolveResult.Ambiguous -> {
                    writeErrorFit(evaluation, "Source match ambiguous after install: ${sr.reason}")
                }
                is SourceRecommendationQualitySourceResolver.ResolveResult.NotFound -> {
                    writeErrorFit(evaluation, "Source not found after install")
                }
            }
        } finally {
            cleanupExtension(availableExt)
        }
    }

    private fun cleanupExtension(ext: Extension.Available) {
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
                        "KMK SourceRecommendationQualityRunner: skipping system-installed cleanup"
                    }
                }
                else -> { /* SkipPreExisting or NotNeeded */ }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logcat(LogPriority.WARN) { "KMK SourceRecommendationQualityRunner: cleanup failed" }
        }
    }

    private suspend fun writeErrorFit(evaluation: SourceEvaluation, message: String) {
        try {
            upsertSourceRecommendationFit.await(buildErrorFit(evaluation, message))
        } catch (_: Exception) {}
    }

    private fun buildFitFromOutcome(
        evaluation: SourceEvaluation,
        outcome: SourceRecommendationFitProbeOutcome,
    ): SourceRecommendationFit {
        val qualityScore = SourceRecommendationFitScorer.score(outcome.toScorerOutcome())
        val label = outcome.label()
        val verdict = RecommendationQualityVerdict.fromSerialized(label.name.lowercase())
        val probeErrorMessage = when (label) {
            RecommendationQualityLabel.ERROR -> {
                if (outcome.reasons.isNotEmpty()) outcome.reasons.take(2).joinToString("; ").take(200) else null
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
                    if (isEmpty()) append("Results did not match taste profile tags")
                }.take(200)
            }
            else -> null
        }
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

    private fun buildErrorFit(evaluation: SourceEvaluation, errorMessage: String) = SourceRecommendationFit(
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
}
// KMK <--

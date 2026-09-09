package exh.recs.evaluation

import android.content.Context
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceRuntime
import eu.kanade.tachiyomi.source.SourceRuntimeOperation
import eu.kanade.tachiyomi.source.isRecoverableSourceRuntimeFailure
import eu.kanade.tachiyomi.source.unwrapSourceRuntimeCause
import eu.kanade.tachiyomi.util.system.isOnline
import exh.recs.RecommendationSourceFilter
import exh.source.ExplicitSourceClassifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.taste.interactor.ClearSourceEvaluationProbeMarker
import tachiyomi.domain.taste.interactor.GetTagAliases
import tachiyomi.domain.taste.interactor.GetTasteProfile
import tachiyomi.domain.taste.interactor.ReplaceSourceEvaluation
import tachiyomi.domain.taste.interactor.UpsertSourceEvaluation
import tachiyomi.domain.taste.interactor.UpsertSourceEvaluationProbeMarker
import tachiyomi.domain.taste.interactor.UpsertSourceRecommendationFit
import tachiyomi.domain.taste.model.RecommendationQualityVerdict
import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationProbeMarker
import tachiyomi.domain.taste.model.SourceEvaluationVerdict
import tachiyomi.domain.taste.model.SourceRecommendationFit
import tachiyomi.domain.taste.model.TasteProfile
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale

// KMK -->
// Minimal contract
// extracted from SourceEvaluationRunner's existing public surface so SourceEvaluationJob can type
// -substitute a deterministic debug-only fixture implementation
// (SourceEvaluationDebugFixtureRunner) without touching production runner internals or requiring a
// separate debug source set. No behavior change for the real runner.
interface SourceEvaluationRunnerContract {
    val state: StateFlow<SourceEvaluationQueueState>
    val completedCandidateKeys: Set<String>
    fun start(candidates: List<EvaluationCandidate>, options: SourceEvaluationOptions)
    fun cancel()
}

/**
 * Orchestrates one-at-a-time evaluation of non-installed extension candidates.
 *
 * Design principles:
 * - One extension is installed, probed, scored, and cleaned up before the next begins.
 * - Uses [installerOverride] passed to [ExtensionManager.installExtension] so the user's
 *   global installer preference is never mutated (Option A from the installer plan).
 * - Cancellation is cooperative: the coroutine is cancelled and cleanup is attempted.
 * - Errors in one source do not abort the whole extension; errors in one extension do not
 *   abort the whole batch unless the installer is unavailable.
 */
class SourceEvaluationRunner(
    private val context: Context,
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val getTasteProfile: GetTasteProfile = Injekt.get(),
    private val upsertSourceEvaluation: UpsertSourceEvaluation = Injekt.get(),
    // KMK v0.8.19: atomic package-level stale-row replacement for extension-level failures -- see
    // recordExtensionError(). Replaces the separate delete+upsert pair (v0.8.15) with one
    // transaction (SourceEvaluationRepositoryImpl.replaceByPackage()).
    private val replaceSourceEvaluation: ReplaceSourceEvaluation = Injekt.get(),
    // KMK --> v0.6.16: crash quarantine probe marker
    private val upsertProbeMarker: UpsertSourceEvaluationProbeMarker = Injekt.get(),
    private val clearProbeMarker: ClearSourceEvaluationProbeMarker = Injekt.get(),
    // KMK <--
    // KMK --> v0.7.6: rec-quality probe
    private val upsertSourceRecommendationFit: UpsertSourceRecommendationFit = Injekt.get(),
    private val getTagAliases: GetTagAliases = Injekt.get(),
    // KMK <--
) : SourceEvaluationRunnerContract {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var runJob: Job? = null

    private val _state = MutableStateFlow(SourceEvaluationQueueState())
    override val state: StateFlow<SourceEvaluationQueueState> = _state.asStateFlow()

    // KMK --> v0.7.6: keys of candidates handed to the runner in this batch (for cursor advance)
    // KMK v0.8.15-fix1: narrowed contract -- this set (and therefore both stale-cursor advancement
    // and SourceEvaluationRunCompletionPolicy's durable-write count) now contains ONLY candidates
    // for which evaluateExtension() confirmed a durable source_evaluation write, never a candidate
    // that was skipped before any write was attempted. Fix A (SourceEvaluationCandidateQueuePolicy
    // .staleCandidates) already keeps non-actionable (blocked/explicit) candidates out of the stale
    // queue entirely, so the deliberate-skip branch below should never fire during a stale run in
    // practice -- but it deliberately no longer adds to this set even if it does, as a second,
    // independent guarantee that a no-write skip can never advance the cursor or count as durable
    // work.
    private val _completedCandidateKeys = mutableSetOf<String>()
    override val completedCandidateKeys: Set<String> get() = _completedCandidateKeys.toSet()
    // KMK <--

    override fun start(candidates: List<EvaluationCandidate>, options: SourceEvaluationOptions) {
        if (runJob?.isActive == true) return

        // KMK --> v0.6.13: compute privateAvailable once for diagnostics logging
        val basePrefs = Injekt.get<BasePreferences>()
        val privateAvailable = BasePreferences.ExtensionInstaller.PRIVATE in
            basePrefs.extensionInstaller().entries
        // KMK <--

        val installerOverride = SourceEvaluationInstallerPolicy.effectiveInstallerOverride(
            mode = options.installerMode,
            currentGlobalInstaller = basePrefs.extensionInstaller().get(),
            privateAvailable = privateAvailable,
        )

        // KMK --> v0.6.16: crash quarantine — unique ID for this batch, used in probe markers
        val batchId = java.util.UUID.randomUUID().toString()
        // KMK <--

        _state.value = SourceEvaluationQueueState(
            status = SourceEvaluationQueueState.Status.Running,
            // KMK --> v0.7.6: candidates list is pre-sliced by the continuation policy
            totalCount = candidates.size,
            // KMK <--
            installerMode = options.installerMode,
            batchSize = options.batchSize,
        )

        runJob = scope.launch {
            try {
                // KMK --> v0.6.16: crash quarantine — clear any stale probe marker before batch begins
                try {
                    clearProbeMarker.await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) { /* ignore */ }
                // KMK <--
                // KMK --> v0.7.6: reset completed keys for this run
                _completedCandidateKeys.clear()
                // KMK <--
                val tasteProfile = getTasteProfile.await()
                val nsfwEnabled = sourcePreferences.showNsfwSource().get()
                val blockExplicit = sourcePreferences.blockExplicitPornHentaiSources().get()
                val recLanguages = RecommendationSourceFilter.normalizeLanguages(
                    sourcePreferences.recommendationSourceLanguages().get(),
                )
                // KMK --> v0.7.42: loaded once per batch — catalogue-fit scoring now resolves tags
                // through the same alias map PersonalRecommendationScorer uses (decision D4)
                val aliasMap = try {
                    getTagAliases.awaitAliasMap()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyMap()
                }
                // KMK <--

                supervisorScope {
                    for (candidate in candidates) {
                        if (_state.value.status == SourceEvaluationQueueState.Status.Cancelling) break

                        // KMK --> v0.7.18: abort batch on mid-run connectivity loss
                        if (!context.isOnline()) {
                            logcat(LogPriority.WARN) { "KMK SourceEvaluation: connectivity lost mid-run after ${_state.value.completedCount} extensions" }
                            _state.update { it.copy(status = SourceEvaluationQueueState.Status.ConnectivityLost) }
                            return@supervisorScope
                        }
                        // KMK <--

                        val ext = candidate.extension

                        // Skip if explicitly disliked and blockExplicit is on. KMK v0.8.15-fix1: this
                        // candidate is intentionally excluded from the run -- it must NOT be treated
                        // as durably handled or advance the cursor (see SourceEvaluationCandidateQueuePolicy
                        // .staleCandidates, which now filters these out of the stale queue before it
                        // ever reaches here; this branch is a defensive backstop for the unassessed
                        // queue / any future caller, not the primary fix).
                        if (!options.includeExplicitCandidates &&
                            blockExplicit &&
                            ExplicitSourceClassifier.isExplicitExtension(ext)
                        ) {
                            _state.update { it.copy(skippedCount = it.skippedCount + 1) }
                            continue
                        }

                        // KMK v0.8.15 / v0.8.15-fix1: root-cause fix for the live-device "Reassess
                        // outdated" false no-op -- the key used to be added unconditionally, before
                        // evaluateExtension() knew whether it durably wrote anything. Now it's only
                        // added once evaluateExtension() confirms a durable write happened, so the
                        // stale cursor and end-of-run "durable handled" check both reflect reality.
                        // KMK --> v0.6.13: pass options and privateAvailable for diagnostics + cleanup
                        val durablyHandled = evaluateExtension(ext, installerOverride, tasteProfile, aliasMap, recLanguages, options, privateAvailable, batchId)
                        // KMK <--
                        if (durablyHandled) {
                            _completedCandidateKeys.add("${ext.signatureHash}|${ext.pkgName}")
                        }

                        // Respectful inter-extension delay
                        delay(1500L)
                    }
                }

                // KMK --> v0.7.18: only mark Completed if no mid-run terminal status (e.g. ConnectivityLost) was set
                // KMK v0.8.15: do not report generic "Evaluation completed" when candidates existed
                // but none of them durably wrote anything (e.g. every candidate was cancelled before
                // evaluateExtension() returned, or the batch was empty of anything actionable) -- this
                // is exactly the live-device "Reassess outdated (25)" / "Evaluation completed" /
                // no DB change bug. With the durable-write fix above, a non-empty candidates list
                // will normally always produce at least one durable write; NoActionableWork is the
                // honest fallback for the remaining edge cases (see SourceEvaluationQueueState.kt).
                if (_state.value.status == SourceEvaluationQueueState.Status.Running) {
                    val newStatus = SourceEvaluationRunCompletionPolicy.resolveStatus(
                        candidatesCount = candidates.size,
                        durablyHandledCount = _completedCandidateKeys.size,
                    )
                    _state.update { it.copy(status = newStatus) }
                }
                // KMK <--
            } catch (e: CancellationException) {
                _state.update { it.copy(status = SourceEvaluationQueueState.Status.Cancelled) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "KMK SourceEvaluation: evaluation batch failed" }
                _state.update {
                    it.copy(
                        status = SourceEvaluationQueueState.Status.Failed,
                        errorMessage = SourceEvaluationProbeErrorClassifier.classifyToStorageKey(e),
                    )
                }
            }
        }
    }

    override fun cancel() {
        _state.update { it.copy(status = SourceEvaluationQueueState.Status.Cancelling) }
        runJob?.cancel()
    }

    fun reset() {
        runJob?.cancel()
        _state.value = SourceEvaluationQueueState()
    }

    // KMK v0.8.15: returns true exactly when this candidate durably wrote a source_evaluation row
    // (a real evaluation, a per-source error, or -- via recordExtensionError() -- a reconciled
    // extension-level error) before returning. The caller only advances the stale-reassessment
    // cursor for candidates that return true, so a candidate that fails before any write is
    // attempted (should that ever happen) is correctly left un-advanced instead of being silently
    // treated as handled.
    private suspend fun evaluateExtension(
        ext: Extension.Available,
        installerOverride: BasePreferences.ExtensionInstaller?,
        tasteProfile: TasteProfile,
        // KMK --> v0.7.42
        aliasMap: Map<String, String>,
        // KMK <--
        recLanguages: Set<String>,
        // KMK --> v0.6.13
        options: SourceEvaluationOptions,
        privateAvailable: Boolean,
        // KMK <--
        // KMK --> v0.6.16: crash quarantine
        batchId: String,
        // KMK <--
    ): Boolean {
        setPhase(ext.name, null, SourceEvaluationQueueState.Phase.Downloading)

        // KMK --> v0.6.13: detect pre-existing installation to avoid accidental cleanup
        val preExistingInstalled = extensionManager.installedExtensionsFlow.value.find {
            it.pkgName == ext.pkgName && it.signatureHash == ext.signatureHash
        }
        logcat(LogPriority.DEBUG) {
            "KMK SourceEvaluation install: begin" +
                " mode=${options.installerMode} privateAvailable=$privateAvailable" +
                " wasPreExisting=${preExistingInstalled != null}"
        }
        if (preExistingInstalled != null) {
            logcat(LogPriority.INFO) { "KMK SourceEvaluation install: skipped pre-existing extension" }
            return recordExtensionError(ext, "Already installed before evaluation", SourceEvaluationQueueState.CleanupStatus.SkippedPreExisting)
        }
        // KMK <--

        // KMK --> v0.6.16: crash quarantine — capture start time for probe marker
        val extStartedAt = System.currentTimeMillis()
        // KMK <--

        try {
            // Install phase
            setPhase(ext.name, null, SourceEvaluationQueueState.Phase.Downloading)
            // KMK --> v0.6.16: crash quarantine — write marker before risky install
            writeProbeMarker(ext, null, null, ext.lang, SourceEvaluationQueueState.Phase.Downloading, batchId, extStartedAt)
            // KMK <--
            // KMK --> v0.6.14: withTimeoutOrNull so install timeout is a local failure, not batch cancel
            val installResult = withTimeoutOrNull(90_000L) {
                installAndWait(ext, installerOverride)
            }
            if (installResult == null) {
                logcat(LogPriority.INFO) { "KMK SourceEvaluation timeout: install phase exceeded 90000ms" }
                return recordExtensionError(ext, "Install timed out after 90s")
            }
            if (!installResult) {
                return recordExtensionError(ext, "Install failed")
            }
            // KMK <--

            // Wait for extension manager to expose the installed extension
            setPhase(ext.name, null, SourceEvaluationQueueState.Phase.LoadingSources)
            // KMK --> v0.6.16: crash quarantine — write marker before waiting for extension load
            writeProbeMarker(ext, null, null, ext.lang, SourceEvaluationQueueState.Phase.LoadingSources, batchId, extStartedAt)
            // KMK <--
            // KMK --> v0.6.14: withTimeoutOrNull so load timeout is a local failure, not batch cancel
            val installedExt = withTimeoutOrNull(20_000L) {
                extensionManager.installedExtensionsFlow.first { installed ->
                    installed.any {
                        it.pkgName == ext.pkgName && it.signatureHash == ext.signatureHash
                    }
                }.find { it.pkgName == ext.pkgName && it.signatureHash == ext.signatureHash }
            }

            // KMK --> v0.6.13: log isShared after install for diagnostics
            logcat(LogPriority.DEBUG) {
                "KMK SourceEvaluation install: post-install isShared=${installedExt?.isShared}"
            }
            // KMK <--

            if (installedExt == null) {
                logcat(LogPriority.INFO) { "KMK SourceEvaluation timeout: source loading exceeded 20000ms" }
                return recordExtensionError(ext, "Loading sources timed out after 20s")
            }
            // KMK <-- (closes v0.6.14 load timeout block)

            if (!evaluateCatalogueSources(ext, installedExt, tasteProfile, aliasMap, batchId, extStartedAt)) {
                return recordExtensionError(ext, "No catalogue sources found in extension")
            }
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "KMK SourceEvaluation: extension evaluation failed" }
            // KMK v0.7.46: classified key, not raw exception text — same UI path (EvaluationResultRow)
            // as the per-source probe catch above; see SourceEvaluationProbeErrorClassifier.
            return recordExtensionError(ext, SourceEvaluationProbeErrorClassifier.classifyToStorageKey(e))
        } catch (e: Error) {
            // KMK v0.8.10-fix3: same reasoning as the per-source probe catch above -- a recoverable
            // extension LinkageError outside the per-source loop (e.g. during extension setup) must
            // not abort the whole evaluation run.
            val unwrapped = e.unwrapSourceRuntimeCause()
            if (!unwrapped.isRecoverableSourceRuntimeFailure()) throw e
            logcat(LogPriority.ERROR) { "KMK SourceEvaluation: extension evaluation failed due to linkage" }
            return recordExtensionError(ext, SourceEvaluationProbeErrorClassifier.classifyToStorageKey(unwrapped))
        } finally {
            // KMK --> v0.6.16: crash quarantine — write marker before cleanup, then clear after
            writeProbeMarker(ext, null, null, ext.lang, SourceEvaluationQueueState.Phase.Cleanup, batchId, extStartedAt)
            // KMK <--
            // KMK --> v0.6.13: private-aware cleanup
            // KMK --> SEC-02 v0.7.16: capture cleanup status and patch results so promptRequiredCleanupCount is accurate
            val cleanupStatus = cleanupExtension(ext, wasPreExisting = false, options.promptHeavyCleanupAllowed)
            _state.update { s ->
                s.copy(
                    results = s.results.map {
                        if (it.pkgName == ext.pkgName && it.signatureHash == ext.signatureHash) {
                            it.copy(cleanupStatus = cleanupStatus)
                        } else {
                            it
                        }
                    },
                )
            }
            // KMK <--
            // KMK <--
            // KMK --> v0.6.16: crash quarantine — clear marker after normal completion
            try {
                clearProbeMarker.await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { /* ignore DB errors in finally */ }
            // KMK <--
            // KMK --> v0.6.14: advance completedCount for all outcomes (success, fail, timeout) except pre-existing skip
            _state.update { it.copy(completedCount = it.completedCount + 1) }
            // KMK <--
        }
    }

    private suspend fun evaluateCatalogueSources(
        ext: Extension.Available,
        installedExt: Extension.Installed,
        tasteProfile: TasteProfile,
        aliasMap: Map<String, String>,
        batchId: String,
        startedAt: Long,
    ): Boolean {
        val catalogueSources = installedExt.sources.filterIsInstance<CatalogueSource>()
        if (catalogueSources.isEmpty()) return false

        for (source in catalogueSources) {
            if (_state.value.status == SourceEvaluationQueueState.Status.Cancelling) break
            try {
                val evaluation = probeAndScore(ext, installedExt, source, tasteProfile, aliasMap, batchId, startedAt)
                upsertSourceEvaluation.await(evaluation)
                addResult(ext, source, evaluation.verdict)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                recordSourceProbeError(ext, source, e, "KMK SourceEvaluation: source probe failed")
            } catch (e: Error) {
                // Recoverable extension linkage failures are source-level incompatibilities.
                // Genuinely fatal VM errors must still abort the evaluation.
                val unwrapped = e.unwrapSourceRuntimeCause()
                if (!unwrapped.isRecoverableSourceRuntimeFailure()) throw e
                recordSourceProbeError(
                    ext,
                    source,
                    unwrapped,
                    "KMK SourceEvaluation: source probe failed due to extension linkage",
                )
            }
            delay(500L) // inter-source delay
        }
        return true
    }

    private suspend fun recordSourceProbeError(
        ext: Extension.Available,
        source: CatalogueSource,
        error: Throwable,
        logMessage: String,
    ) {
        logcat(LogPriority.WARN) { logMessage }
        val errRecord = SourceEvaluationScorer.errorRecord(
            extensionName = ext.name,
            pkgName = ext.pkgName,
            signatureHash = ext.signatureHash,
            sourceId = source.id,
            sourceName = source.name,
            lang = (source as? eu.kanade.tachiyomi.source.online.HttpSource)?.lang ?: ext.lang,
            repoName = ext.storeName,
            isNsfw = ext.isNsfw,
            // Store a classified key rather than a raw exception message.
            errorMessage = SourceEvaluationProbeErrorClassifier.classifyToStorageKey(error),
            extensionVersionName = ext.versionName,
            extensionVersionCode = ext.versionCode,
            extensionApkName = ext.apkUrl,
        )
        upsertSourceEvaluation.await(errRecord)
        addResult(ext, source, SourceEvaluationVerdict.ERROR)
    }

    // KMK --> v0.6.16: crash quarantine — write probe marker to DB before risky operations
    private suspend fun writeProbeMarker(
        ext: Extension.Available,
        sourceId: Long?,
        sourceName: String?,
        lang: String,
        phase: SourceEvaluationQueueState.Phase,
        batchId: String,
        startedAt: Long,
    ) {
        try {
            upsertProbeMarker.await(
                SourceEvaluationProbeMarker(
                    evaluationKey = batchId,
                    extensionPkgName = ext.pkgName,
                    signatureHash = ext.signatureHash,
                    extensionName = ext.name,
                    sourceId = sourceId,
                    sourceName = sourceName,
                    lang = lang,
                    phase = phase.name,
                    startedAt = startedAt,
                    updatedAt = System.currentTimeMillis(),
                    batchId = batchId,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "KMK SourceEvaluation: failed to write probe marker phase=${phase.name}" }
        }
    }
    // KMK <--

    private suspend fun installAndWait(
        ext: Extension.Available,
        installerOverride: BasePreferences.ExtensionInstaller?,
    ): Boolean {
        return try {
            setPhase(ext.name, null, SourceEvaluationQueueState.Phase.Installing)
            val flow = extensionManager.installExtension(ext, installerOverride)
            flow.first { step ->
                step == InstallStep.Installed || step == InstallStep.Error
            } == InstallStep.Installed
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            false
        }
    }

    private suspend fun probeAndScore(
        ext: Extension.Available,
        installedExt: Extension.Installed,
        source: CatalogueSource,
        tasteProfile: TasteProfile,
        // KMK --> v0.7.42: catalogue-fit taste matching now uses the same alias map as For You
        aliasMap: Map<String, String>,
        // KMK <--
        // KMK --> v0.6.16: crash quarantine
        batchId: String,
        startedAt: Long,
        // KMK <--
    ): SourceEvaluation {
        // KMK --> v0.7.42: catalogue samples only (Popular + Latest) — the scorer's own search probe
        // was removed; search compatibility is measured separately by SourceRecommendationFitProbe
        // below (decision D1). Each sample keeps its own genre list for per-item taste matching.
        val rawCatalogueItems = mutableListOf<eu.kanade.tachiyomi.source.model.SManga>()
        // KMK <--
        var popularCount = 0
        var latestCount = 0
        var errorCount = 0

        val sourceLang = (source as? eu.kanade.tachiyomi.source.online.HttpSource)?.lang ?: ext.lang

        // Popular probe
        setPhase(ext.name, source.name, SourceEvaluationQueueState.Phase.ProbingPopular)
        // KMK --> v0.6.16: crash quarantine — write marker before network call that may crash the process
        writeProbeMarker(ext, source.id, source.name, sourceLang, SourceEvaluationQueueState.Phase.ProbingPopular, batchId, startedAt)
        // KMK <--
        // KMK --> v0.6.14: withTimeoutOrNull so probe timeout is a local failure, not batch cancel
        // KMK v0.8.10-fix4: routed through SourceRuntime instead of a raw call inside
        // withTimeoutOrNull/catch(Exception) -- so a recoverable extension LinkageError is recorded
        // in SourceRuntimeFailureRegistry, and always rethrows CancellationException/genuinely fatal
        // Error, instead of only being caught by the outer per-source catch(Error) one frame up
        // (which still exists as a defensive backstop, not the structural boundary).
        val popularResult = withTimeoutOrNull(30_000L) {
            SourceRuntime.run(source, SourceRuntimeOperation.Popular) { getPopularManga(1) }
        }
        if (popularResult == null) {
            logcat(LogPriority.INFO) { "KMK SourceEvaluation timeout: popular probe exceeded 30000ms" }
            errorCount++
        } else {
            popularResult.fold(
                onSuccess = { page ->
                    val items = page.mangas.take(15)
                    popularCount = items.size
                    rawCatalogueItems += items
                },
                onFailure = { errorCount++ },
            )
        }
        // KMK <--

        // Latest probe
        if (source.supportsLatest) {
            setPhase(ext.name, source.name, SourceEvaluationQueueState.Phase.ProbingLatest)
            // KMK --> v0.6.16: crash quarantine — write marker before network call that may crash the process
            writeProbeMarker(ext, source.id, source.name, sourceLang, SourceEvaluationQueueState.Phase.ProbingLatest, batchId, startedAt)
            // KMK <--
            // KMK --> v0.6.14: withTimeoutOrNull so probe timeout is a local failure, not batch cancel
            // KMK v0.8.10-fix4: routed through SourceRuntime -- same reasoning as the popular probe
            // above.
            val latestResult = withTimeoutOrNull(30_000L) {
                SourceRuntime.run(source, SourceRuntimeOperation.Latest) { getLatestUpdates(1) }
            }
            if (latestResult == null) {
                logcat(LogPriority.INFO) { "KMK SourceEvaluation timeout: latest probe exceeded 30000ms" }
                errorCount++
            } else {
                latestResult.fold(
                    onSuccess = { page ->
                        val items = page.mangas.take(10)
                        latestCount = items.size
                        rawCatalogueItems += items
                    },
                    onFailure = { errorCount++ },
                )
            }
            // KMK <--
        }

        // KMK --> v0.7.47: bounded getMangaDetails() enrichment for list entries missing genre
        // metadata, before scoring — see SourceEvaluationCatalogueEnricher and the tag-enrichment
        // fix plan. Sequential, timeout-bounded, cancellation-aware; does not fetch chapters/pages
        // and does not write to the app manga table.
        setPhase(ext.name, source.name, SourceEvaluationQueueState.Phase.EnrichingDetails)
        val enrichResult = SourceEvaluationCatalogueEnricher.enrich(
            source = source,
            rawItems = rawCatalogueItems,
            sourceId = source.id,
        )
        val catalogueSamples = enrichResult.samples
        // KMK <--

        // Score
        setPhase(ext.name, source.name, SourceEvaluationQueueState.Phase.Scoring)
        val evaluation = SourceEvaluationScorer.score(
            extensionName = ext.name,
            pkgName = ext.pkgName,
            signatureHash = ext.signatureHash,
            sourceId = source.id,
            sourceName = source.name,
            lang = sourceLang,
            baseUrl = (source as? eu.kanade.tachiyomi.source.online.HttpSource)?.baseUrl,
            repoName = ext.storeName,
            sourceCount = installedExt.sources.size,
            isNsfw = ext.isNsfw,
            catalogueSamples = catalogueSamples,
            popularCount = popularCount,
            latestCount = latestCount,
            errorCount = errorCount,
            tasteProfile = tasteProfile,
            aliasMap = aliasMap,
            // KMK --> v0.7.47: enrichment evidence
            detailEnrichmentAttemptCount = enrichResult.detailAttempts,
            detailEnrichmentSuccessCount = enrichResult.detailSuccesses,
            // KMK <--
            // KMK --> v0.7.4: record extension version for update-reassessment detection
            extensionVersionName = ext.versionName,
            extensionVersionCode = ext.versionCode,
            extensionApkName = ext.apkUrl,
            // KMK <--
        )

        // KMK --> v0.7.6: bounded rec-quality probe for eligible sources
        if (SourceRecommendationFitEligibility.check(evaluation) ==
            SourceRecommendationFitEligibility.EligibilityResult.ELIGIBLE
        ) {
            try {
                val probe = SourceRecommendationFitProbe(getTagAliases)
                val outcome = probe.probe(source, tasteProfile)
                val qualityScore = SourceRecommendationFitScorer.score(outcome.toScorerOutcome())
                val label = outcome.label()
                val verdict = RecommendationQualityVerdict.fromSerialized(label.name.lowercase(Locale.ROOT))
                val fit = SourceRecommendationFit(
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
                    errorMessage = null,
                )
                upsertSourceRecommendationFit.await(fit)
                logcat(LogPriority.DEBUG) {
                    "KMK SourceEvaluation rec-fit probe completed verdict=${verdict.serialized} score=$qualityScore"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN) { "KMK SourceEvaluation rec-fit probe failed" }
                // Probe failure does not affect the source evaluation verdict
            }
        }
        // KMK <--

        return evaluation
    }

    // KMK --> v0.6.13: private-aware cleanup using SourceEvaluationCleanupPolicy
    private fun cleanupExtension(
        ext: Extension.Available,
        wasPreExisting: Boolean,
        promptHeavyCleanupAllowed: Boolean,
    ): SourceEvaluationQueueState.CleanupStatus {
        setPhaseBackground(ext.name, null, SourceEvaluationQueueState.Phase.Cleanup)
        return try {
            val installedExt = extensionManager.installedExtensionsFlow.value.find {
                it.pkgName == ext.pkgName && it.signatureHash == ext.signatureHash
            }

            val decision = SourceEvaluationCleanupPolicy.cleanupDecision(
                preExistingInstalled = wasPreExisting,
                installedAfterEvaluation = installedExt != null,
                isShared = installedExt?.isShared ?: false,
            )

            logcat(LogPriority.DEBUG) {
                "KMK SourceEvaluation install: cleanup decision=$decision" +
                    " isShared=${installedExt?.isShared} promptAllowed=$promptHeavyCleanupAllowed"
            }

            when (decision) {
                SourceEvaluationCleanupPolicy.CleanupDecision.RemovePrivateSilently -> {
                    extensionManager.uninstallExtension(installedExt!!)
                    SourceEvaluationQueueState.CleanupStatus.PrivateRemoved
                }
                SourceEvaluationCleanupPolicy.CleanupDecision.SkipPreExisting -> {
                    SourceEvaluationQueueState.CleanupStatus.SkippedPreExisting
                }
                SourceEvaluationCleanupPolicy.CleanupDecision.PromptRequired -> {
                    if (promptHeavyCleanupAllowed) {
                        extensionManager.uninstallExtension(installedExt!!)
                    } else {
                        logcat(LogPriority.INFO) {
                            "KMK SourceEvaluation install: skipping cleanup for system-installed extension; prompt not allowed"
                        }
                    }
                    SourceEvaluationQueueState.CleanupStatus.PromptRequired
                }
                SourceEvaluationCleanupPolicy.CleanupDecision.NotNeeded -> {
                    SourceEvaluationQueueState.CleanupStatus.NotNeeded
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "KMK SourceEvaluation: cleanup failed" }
            SourceEvaluationQueueState.CleanupStatus.Failed
        }
    }
    // KMK <--

    // KMK v0.8.15: root-cause fix -- was `private fun` with a fire-and-forget `scope.launch { ... }`
    // write, so the runner (and the WorkManager job waiting on it) could reach a terminal state
    // before the database was actually updated. Now `suspend` and awaited directly by every caller
    // (all already inside a suspend context in evaluateExtension()).
    //
    // Also reconciles stale per-source rows for this package: this is an extension-level failure --
    // no source-specific probe ran, so the only durable record possible is one row with
    // `sourceId = null`. SourceEvaluationScorer.errorRecord() builds its key from
    // (signatureHash, pkgName, sourceId), so that extension-level key does NOT match -- and
    // therefore does not replace -- any existing stale rows that have a real `sourceId`. Confirmed
    // live-device root cause: 44 of 48 stale rows had a non-null source_id, so an extension-level
    // error upsert alone left them untouched even after this fix's durable-write change. Deleting
    // every existing row for (pkgName, signatureHash) before the upsert (SourceEvaluationRepository
    // .deleteByPackage, already-existing API, no schema change) collapses them into the one current
    // extension-level row, so `SourceEvaluationCandidateQueuePolicy.staleCandidates` -- which is
    // recomputed reactively from the DB -- correctly stops counting this package as outdated.
    // This delete+upsert pair is only reached from extension-level failure paths (already-installed,
    // install timeout/failure, source-loading timeout, no catalogue sources, and the outer
    // recoverable-failure catches in evaluateExtension()) -- never after a successful per-source
    // probe, which upserts its own source-specific row directly and never calls this function.
    //
    // KMK v0.8.15-fix1: hardened the delete-failure edge case (plan Fix C). If
    // deleteSourceEvaluation.awaitByPackage(...) fails, old source-specific stale rows for this
    // package can remain beside the new extension-level error row -- exactly the stale-count bug
    // this whole mechanism exists to prevent, just triggered by a delete failure instead of the
    // original key-mismatch bug. Previously this function logged the failure and silently continued,
    // implicitly reporting the candidate as durably handled (the caller always returned `true` after
    // calling it). It now returns `false` when the delete failed, so the caller does not advance the
    // stale cursor or count this candidate as durable work for a run that might still leave stale
    // rows behind -- the candidate stays actionable and will be retried on the next reassessment run.
    // The upsert still happens either way (the new extension-level row is itself correct and useful),
    // this only affects whether the *candidate* counts as fully reconciled.
    private suspend fun recordExtensionError(
        ext: Extension.Available,
        message: String,
        // KMK --> v0.6.13
        cleanupStatus: SourceEvaluationQueueState.CleanupStatus = SourceEvaluationQueueState.CleanupStatus.NotNeeded,
        // KMK <--
    ): Boolean {
        val errRecord = SourceEvaluationScorer.errorRecord(
            extensionName = ext.name,
            pkgName = ext.pkgName,
            signatureHash = ext.signatureHash,
            sourceId = null,
            sourceName = ext.name,
            lang = ext.lang,
            repoName = ext.storeName,
            isNsfw = ext.isNsfw,
            errorMessage = message,
            // KMK --> v0.7.4: record extension version
            extensionVersionName = ext.versionName,
            extensionVersionCode = ext.versionCode,
            extensionApkName = ext.apkUrl,
            // KMK <--
        )
        // KMK v0.8.16: the durable-handled/reconciliation-count decision itself is now a pure,
        // directly-tested policy (SourceEvaluationExtensionErrorReconciliationPolicyTest) instead of
        // only inline if/else here.
        // KMK v0.8.19: delete-then-upsert is now one atomic repository transaction
        // (ReplaceSourceEvaluation/SourceEvaluationRepositoryImpl.replaceByPackage()) instead of two
        // separate interactor calls -- a failure partway through can no longer leave stale rows
        // deleted without their replacement written. Cancellation still propagates unchanged; any
        // other failure is logged and reported as a non-durable reconciliation via the same policy.
        val replaceSucceeded = try {
            replaceSourceEvaluation.await(ext.pkgName, ext.signatureHash, errRecord)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "KMK SourceEvaluation: failed to atomically reconcile stale package rows" }
            false
        }
        val outcome = SourceEvaluationExtensionErrorReconciliationPolicy.resolve(deleteSucceeded = replaceSucceeded)
        _state.update { s ->
            s.copy(
                failedCount = s.failedCount + 1,
                // KMK v0.8.15-fix1: surfaced non-fatal so the user sees an honest result instead of a
                // silent retry-forever loop -- see EvaluationSummaryCard's reconciliation-failed line.
                reconciliationFailedCount = s.reconciliationFailedCount + outcome.reconciliationFailedCountDelta,
                results = s.results + SourceEvaluationQueueState.EvaluationResult(
                    extensionName = ext.name,
                    sourceName = ext.name,
                    pkgName = ext.pkgName,
                    signatureHash = ext.signatureHash,
                    sourceId = null,
                    verdict = SourceEvaluationVerdict.ERROR,
                    errorMessage = message,
                    // KMK --> v0.6.13
                    cleanupStatus = cleanupStatus,
                    // KMK <--
                ),
            )
        }
        return outcome.countsAsDurablyHandled
    }

    private fun addResult(
        ext: Extension.Available,
        source: CatalogueSource,
        verdict: SourceEvaluationVerdict,
    ) {
        _state.update { s ->
            s.copy(
                results = s.results + SourceEvaluationQueueState.EvaluationResult(
                    extensionName = ext.name,
                    sourceName = source.name,
                    pkgName = ext.pkgName,
                    signatureHash = ext.signatureHash,
                    sourceId = source.id,
                    verdict = verdict,
                ),
            )
        }
    }

    private fun setPhase(extName: String, sourceName: String?, phase: SourceEvaluationQueueState.Phase) {
        _state.update { it.copy(currentExtensionName = extName, currentSourceName = sourceName, currentPhase = phase) }
    }

    private fun setPhaseBackground(extName: String, sourceName: String?, phase: SourceEvaluationQueueState.Phase) {
        _state.update { it.copy(currentExtensionName = extName, currentSourceName = sourceName, currentPhase = phase) }
    }
}
// KMK <--

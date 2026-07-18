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

// KMK -->
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
    // KMK --> v0.6.16: crash quarantine probe marker
    private val upsertProbeMarker: UpsertSourceEvaluationProbeMarker = Injekt.get(),
    private val clearProbeMarker: ClearSourceEvaluationProbeMarker = Injekt.get(),
    // KMK <--
    // KMK --> v0.7.6: rec-quality probe
    private val upsertSourceRecommendationFit: UpsertSourceRecommendationFit = Injekt.get(),
    private val getTagAliases: GetTagAliases = Injekt.get(),
    // KMK <--
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var runJob: Job? = null

    private val _state = MutableStateFlow(SourceEvaluationQueueState())
    val state: StateFlow<SourceEvaluationQueueState> = _state.asStateFlow()

    // KMK --> v0.7.6: keys of candidates handed to the runner in this batch (for cursor advance)
    private val _completedCandidateKeys = mutableSetOf<String>()
    val completedCandidateKeys: Set<String> get() = _completedCandidateKeys.toSet()
    // KMK <--

    fun start(candidates: List<EvaluationCandidate>, options: SourceEvaluationOptions) {
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
                val aliasMap = runCatching { getTagAliases.awaitAliasMap() }.getOrDefault(emptyMap())
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
                        // KMK --> v0.7.6: record that this candidate was handed to the runner
                        _completedCandidateKeys.add("${ext.signatureHash}|${ext.pkgName}")
                        // KMK <--

                        // Skip if explicitly disliked and blockExplicit is on
                        if (!options.includeExplicitCandidates &&
                            blockExplicit &&
                            ExplicitSourceClassifier.isExplicitExtension(ext)
                        ) {
                            _state.update { it.copy(skippedCount = it.skippedCount + 1) }
                            continue
                        }

                        // KMK --> v0.6.13: pass options and privateAvailable for diagnostics + cleanup
                        evaluateExtension(ext, installerOverride, tasteProfile, aliasMap, recLanguages, options, privateAvailable, batchId)
                        // KMK <--

                        // Respectful inter-extension delay
                        delay(1500L)
                    }
                }

                // KMK --> v0.7.18: only mark Completed if no mid-run terminal status (e.g. ConnectivityLost) was set
                if (_state.value.status == SourceEvaluationQueueState.Status.Running) {
                    _state.update { it.copy(status = SourceEvaluationQueueState.Status.Completed) }
                }
                // KMK <--
            } catch (e: CancellationException) {
                _state.update { it.copy(status = SourceEvaluationQueueState.Status.Cancelled) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Evaluation batch failed" }
                _state.update {
                    it.copy(
                        status = SourceEvaluationQueueState.Status.Failed,
                        errorMessage = e.message,
                    )
                }
            }
        }
    }

    fun cancel() {
        _state.update { it.copy(status = SourceEvaluationQueueState.Status.Cancelling) }
        runJob?.cancel()
    }

    fun reset() {
        runJob?.cancel()
        _state.value = SourceEvaluationQueueState()
    }

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
    ) {
        setPhase(ext.name, null, SourceEvaluationQueueState.Phase.Downloading)

        // KMK --> v0.6.13: detect pre-existing installation to avoid accidental cleanup
        val preExistingInstalled = extensionManager.installedExtensionsFlow.value.find {
            it.pkgName == ext.pkgName && it.signatureHash == ext.signatureHash
        }
        logcat(LogPriority.DEBUG) {
            "KMK SourceEvaluation install: ext=${ext.name} pkg=${ext.pkgName} sig=${ext.signatureHash}" +
                " mode=${options.installerMode} override=$installerOverride" +
                " privateAvailable=$privateAvailable wasPreExisting=${preExistingInstalled != null}"
        }
        if (preExistingInstalled != null) {
            logcat(LogPriority.INFO) { "KMK SourceEvaluation install: skipping ${ext.name} — already installed before evaluation" }
            recordExtensionError(ext, "Already installed before evaluation", SourceEvaluationQueueState.CleanupStatus.SkippedPreExisting)
            return
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
                logcat(LogPriority.INFO) { "KMK SourceEvaluation timeout: ${ext.name} install timed out after 90000ms" }
                recordExtensionError(ext, "Install timed out after 90s")
                return
            }
            if (!installResult) {
                recordExtensionError(ext, "Install failed")
                return
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
                "KMK SourceEvaluation install: post-install ${ext.name} isShared=${installedExt?.isShared}"
            }
            // KMK <--

            if (installedExt == null) {
                logcat(LogPriority.INFO) { "KMK SourceEvaluation timeout: ${ext.name} loading sources timed out after 20000ms" }
                recordExtensionError(ext, "Loading sources timed out after 20s")
                return
            }
            // KMK <-- (closes v0.6.14 load timeout block)

            val catalogueSources = installedExt.sources.filterIsInstance<CatalogueSource>()

            if (catalogueSources.isEmpty()) {
                recordExtensionError(ext, "No catalogue sources found in extension")
                return
            }

            // Probe each source
            for (source in catalogueSources) {
                if (_state.value.status == SourceEvaluationQueueState.Status.Cancelling) break
                try {
                    val evaluation = probeAndScore(ext, installedExt, source, tasteProfile, aliasMap, batchId, extStartedAt)
                    upsertSourceEvaluation.await(evaluation)
                    addResult(ext, source, evaluation.verdict)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    logcat(LogPriority.WARN, e) { "Probe failed for source ${source.name}" }
                    val errRecord = SourceEvaluationScorer.errorRecord(
                        extensionName = ext.name,
                        pkgName = ext.pkgName,
                        signatureHash = ext.signatureHash,
                        sourceId = source.id,
                        sourceName = source.name,
                        lang = (source as? eu.kanade.tachiyomi.source.online.HttpSource)?.lang ?: ext.lang,
                        repoName = ext.storeName,
                        isNsfw = ext.isNsfw,
                        // KMK v0.7.45: classified key, not the raw exception message — see
                        // SourceEvaluationProbeErrorClassifier and EvaluationResultRow's rendering.
                        errorMessage = SourceEvaluationProbeErrorClassifier.classifyToStorageKey(e),
                        // KMK --> v0.7.4: record extension version
                        extensionVersionName = ext.versionName,
                        extensionVersionCode = ext.versionCode,
                        extensionApkName = ext.apkUrl,
                        // KMK <--
                    )
                    upsertSourceEvaluation.await(errRecord)
                    addResult(ext, source, SourceEvaluationVerdict.ERROR)
                } catch (e: Error) {
                    // KMK v0.8.10-fix3: a broken/incompletely-packaged extension can throw a
                    // LinkageError (e.g. NoClassDefFoundError) from Popular/Latest/Search/detail
                    // calls inside probeAndScore() -- previously uncaught here, aborting the whole
                    // evaluation batch instead of recording this one source as a technical
                    // incompatibility and continuing to the next source. Genuinely fatal VM errors
                    // still rethrow.
                    val unwrapped = e.unwrapSourceRuntimeCause()
                    if (!unwrapped.isRecoverableSourceRuntimeFailure()) throw e
                    logcat(LogPriority.WARN, unwrapped) { "Probe failed for source ${source.name} (extension linkage failure)" }
                    val errRecord = SourceEvaluationScorer.errorRecord(
                        extensionName = ext.name,
                        pkgName = ext.pkgName,
                        signatureHash = ext.signatureHash,
                        sourceId = source.id,
                        sourceName = source.name,
                        lang = (source as? eu.kanade.tachiyomi.source.online.HttpSource)?.lang ?: ext.lang,
                        repoName = ext.storeName,
                        isNsfw = ext.isNsfw,
                        errorMessage = SourceEvaluationProbeErrorClassifier.classifyToStorageKey(unwrapped),
                        extensionVersionName = ext.versionName,
                        extensionVersionCode = ext.versionCode,
                        extensionApkName = ext.apkUrl,
                    )
                    upsertSourceEvaluation.await(errRecord)
                    addResult(ext, source, SourceEvaluationVerdict.ERROR)
                }
                delay(500L) // inter-source delay
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Extension evaluation failed: ${ext.name}" }
            // KMK v0.7.46: classified key, not raw exception text — same UI path (EvaluationResultRow)
            // as the per-source probe catch above; see SourceEvaluationProbeErrorClassifier.
            recordExtensionError(ext, SourceEvaluationProbeErrorClassifier.classifyToStorageKey(e))
        } catch (e: Error) {
            // KMK v0.8.10-fix3: same reasoning as the per-source probe catch above -- a recoverable
            // extension LinkageError outside the per-source loop (e.g. during extension setup) must
            // not abort the whole evaluation run.
            val unwrapped = e.unwrapSourceRuntimeCause()
            if (!unwrapped.isRecoverableSourceRuntimeFailure()) throw e
            logcat(LogPriority.ERROR, unwrapped) { "Extension evaluation failed: ${ext.name} (extension linkage failure)" }
            recordExtensionError(ext, SourceEvaluationProbeErrorClassifier.classifyToStorageKey(unwrapped))
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
            } catch (e: Exception) { /* ignore DB errors in finally */ }
            // KMK <--
            // KMK --> v0.6.14: advance completedCount for all outcomes (success, fail, timeout) except pre-existing skip
            _state.update { it.copy(completedCount = it.completedCount + 1) }
            // KMK <--
        }
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
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "KMK SourceEvaluation: failed to write probe marker for ${ext.name} phase=${phase.name}" }
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
            logcat(LogPriority.INFO) { "KMK SourceEvaluation timeout: ${ext.name} / ${source.name} popular probe timed out after 30000ms" }
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
                logcat(LogPriority.INFO) { "KMK SourceEvaluation timeout: ${ext.name} / ${source.name} latest probe timed out after 30000ms" }
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
                val verdict = RecommendationQualityVerdict.fromSerialized(label.name.lowercase())
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
                    "KMK SourceEvaluation rec-fit probe: ${source.name} verdict=${verdict.serialized} score=$qualityScore"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "KMK SourceEvaluation rec-fit probe failed for ${source.name}" }
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
                "KMK SourceEvaluation install: cleanup ${ext.name} decision=$decision" +
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
                            "KMK SourceEvaluation install: skipping cleanup for ${ext.name} — system-installed, prompt not allowed"
                        }
                    }
                    SourceEvaluationQueueState.CleanupStatus.PromptRequired
                }
                SourceEvaluationCleanupPolicy.CleanupDecision.NotNeeded -> {
                    SourceEvaluationQueueState.CleanupStatus.NotNeeded
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) return SourceEvaluationQueueState.CleanupStatus.Failed
            logcat(LogPriority.WARN, e) { "Cleanup failed for ${ext.name}" }
            SourceEvaluationQueueState.CleanupStatus.Failed
        }
    }
    // KMK <--

    private fun recordExtensionError(
        ext: Extension.Available,
        message: String,
        // KMK --> v0.6.13
        cleanupStatus: SourceEvaluationQueueState.CleanupStatus = SourceEvaluationQueueState.CleanupStatus.NotNeeded,
        // KMK <--
    ) {
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
        scope.launch {
            upsertSourceEvaluation.await(errRecord)
        }
        _state.update { s ->
            s.copy(
                failedCount = s.failedCount + 1,
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

package exh.recs.evaluation

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.taste.interactor.ClearSourceEvaluationProbeMarker
import tachiyomi.domain.taste.interactor.GetSourceEvaluationProbeMarker
import tachiyomi.domain.taste.interactor.GetSourceEvaluationUnsafeSources
import tachiyomi.domain.taste.interactor.GetUnsafeExtensionPackages
import tachiyomi.domain.taste.interactor.MarkSourceEvaluationUnsafe
import tachiyomi.domain.taste.interactor.UpsertUnsafeExtensionPackage
import tachiyomi.domain.taste.model.SourceEvaluationProbeMarker
import tachiyomi.domain.taste.model.UnsafeExtensionPackage
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/**
 * Shared crash-recovery helper that runs the probe-marker quarantine check and applies known-unsafe
 * seeds to both the Source Evaluation candidate filter and the package-level extension load
 * quarantine. Designed to be called both at app startup (before the user opens Source Evaluation)
 * and as a safety-net fallback when [SourceEvaluationScreenModel] opens.
 *
 * All exceptions are caught internally so callers never need to handle failures.
 */
class SourceEvaluationStartupRecovery(
    private val getProbeMarker: GetSourceEvaluationProbeMarker = Injekt.get(),
    private val clearProbeMarker: ClearSourceEvaluationProbeMarker = Injekt.get(),
    private val markUnsafe: MarkSourceEvaluationUnsafe = Injekt.get(),
    private val getUnsafeSources: GetSourceEvaluationUnsafeSources = Injekt.get(),
    private val getUnsafePackages: GetUnsafeExtensionPackages = Injekt.get(),
    private val upsertUnsafePackage: UpsertUnsafeExtensionPackage = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    // KMK --> SEC-01 v0.7.16
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    // KMK <--
) {

    data class Result(
        val markerRecovered: Boolean = false,
        val markerClearedStale: Boolean = false,
        val recoveredExtensionName: String? = null,
        val recoveredPhase: String? = null,
        val seededUnsafeCount: Int = 0,
        val errorMessage: String? = null,
    )

    /**
     * Runs probe-marker recovery and known-unsafe seeding.
     * Safe to call from any coroutine context; never throws.
     */
    suspend fun run(): Result {
        return try {
            val now = System.currentTimeMillis()
            val marker = getProbeMarker.await()

            var markerRecovered = false
            var markerClearedStale = false
            var recoveredExtensionName: String? = null
            var recoveredPhase: String? = null

            when (val decision = SourceEvaluationCrashRecoveryPolicy.decide(marker, now)) {
                is SourceEvaluationCrashRecoveryPolicy.Decision.MarkUnsafe -> {
                    markUnsafe.await(
                        marker = decision.marker,
                        reason = "Suspected fatal crash (SIGSEGV/stack-overflow) during ${decision.marker.phase} phase",
                        now = now,
                    )
                    clearProbeMarker.await()
                    markerRecovered = true
                    recoveredExtensionName = decision.marker.extensionName
                    recoveredPhase = decision.marker.phase
                    logcat(LogPriority.WARN) {
                        "KMK SourceEvaluation startup recovery: quarantined marker" +
                            " (phase=${decision.marker.phase})"
                    }
                    // KMK --> SEC-01 v0.7.16: if extension is still physically installed, record it so the UI can prompt uninstall
                    val isStillInstalled = extensionManager.installedExtensionsFlow.value
                        .any { it.pkgName == decision.marker.extensionPkgName }
                    if (isStillInstalled) {
                        sourcePreferences.sourceEvaluationLeftoverPkg().set(decision.marker.extensionPkgName)
                        logcat(LogPriority.WARN) {
                            "KMK SourceEvaluation startup recovery: leftover extension detected"
                        }
                    }
                    // KMK <--
                }

                is SourceEvaluationCrashRecoveryPolicy.Decision.ClearStale -> {
                    clearProbeMarker.await()
                    markerClearedStale = true
                    logcat(LogPriority.INFO) {
                        "KMK SourceEvaluation startup recovery: cleared stale probe marker" +
                            " for current evaluation"
                    }
                }

                SourceEvaluationCrashRecoveryPolicy.Decision.DoNothing -> {
                    logcat(LogPriority.DEBUG) {
                        "KMK SourceEvaluation startup recovery: no probe marker"
                    }
                }
            }

            val seededCount = applyKnownUnsafeSeeds(now)

            logcat(LogPriority.INFO) {
                "KMK SourceEvaluation startup recovery: complete" +
                    " markerRecovered=$markerRecovered" +
                    " markerClearedStale=$markerClearedStale" +
                    " seededCount=$seededCount"
            }

            Result(
                markerRecovered = markerRecovered,
                markerClearedStale = markerClearedStale,
                recoveredExtensionName = recoveredExtensionName,
                recoveredPhase = recoveredPhase,
                seededUnsafeCount = seededCount,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "KMK SourceEvaluation startup recovery: failed" }
            Result(errorMessage = SourceEvaluationProbeErrorClassifier.classifyToStorageKey(e))
        }
    }

    /** Launches [run] on [Dispatchers.IO] in [scope]. Non-blocking; never throws. */
    fun runAsync(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) { run() }
    }

    private suspend fun applyKnownUnsafeSeeds(now: Long): Int {
        // Read existing package-level blocks (skip if already present)
        val existingBlockedPkgs = try {
            getUnsafePackages.awaitAll().map { it.pkgName }.toSet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "KMK SourceEvaluation startup recovery: could not read blocked packages for seed check"
            }
            emptySet()
        }

        // Read existing source-eval unsafe keys (for SE candidate filter seeding)
        val existingUnsafeKeys = try {
            getUnsafeSources.awaitAll().map { it.extensionKey }.toSet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "KMK SourceEvaluation startup recovery: could not read existing unsafe sources for seed check"
            }
            emptySet()
        }

        val installed = extensionManager.installedExtensionsFlow.value
        val available = extensionManager.availableExtensionsFlow.value

        var seededCount = 0
        for (seed in SourceEvaluationKnownUnsafeSeeds.ALL_SEEDS) {
            // Step 1: Write package-level load block (requires only pkg name, not signature hash)
            if (seed.pkgName !in existingBlockedPkgs) {
                try {
                    upsertUnsafePackage.await(
                        UnsafeExtensionPackage(
                            pkgName = seed.pkgName,
                            extensionName = seed.extensionName,
                            reason = seed.reason,
                            source = "known_seed",
                            removable = true,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                    seededCount++
                    logcat(LogPriority.INFO) {
                        "KMK SourceEvaluation startup recovery: seeded package-level block"
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR) {
                        "KMK SourceEvaluation startup recovery: package-level seed failed"
                    }
                }
            } else {
                logcat(LogPriority.DEBUG) {
                    "KMK SourceEvaluation startup recovery: package-level block already present"
                }
            }

            // Step 2: Also write to Source Eval unsafe table if signature hash is available
            val signatureHash = findSignatureHash(seed.pkgName, installed, available)
            if (signatureHash == null) {
                logcat(LogPriority.INFO) {
                    "KMK SourceEvaluation startup recovery: SE seed skipped" +
                        " — extension not found in installed/available metadata (package-level block still applied)"
                }
                continue
            }

            val extensionKey = "$signatureHash|${seed.pkgName}"
            if (extensionKey in existingUnsafeKeys) {
                logcat(LogPriority.DEBUG) {
                    "KMK SourceEvaluation startup recovery: SE seed already present"
                }
                continue
            }

            try {
                val syntheticMarker = SourceEvaluationProbeMarker(
                    evaluationKey = null,
                    extensionPkgName = seed.pkgName,
                    signatureHash = signatureHash,
                    extensionName = seed.extensionName,
                    sourceId = null,
                    sourceName = null,
                    lang = null,
                    phase = "KnownUnsafeSeed",
                    startedAt = now,
                    updatedAt = now,
                    batchId = null,
                )
                markUnsafe.await(marker = syntheticMarker, reason = seed.reason, now = now)
                logcat(LogPriority.INFO) {
                    "KMK SourceEvaluation startup recovery: SE seeded unsafe"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) {
                    "KMK SourceEvaluation startup recovery: SE seed failed"
                }
            }
        }
        return seededCount
    }

    private fun findSignatureHash(
        pkgName: String,
        installed: List<Extension.Installed>,
        available: List<Extension.Available>,
    ): String? {
        installed.find { it.pkgName == pkgName }?.let { return it.signatureHash }
        available.find { it.pkgName == pkgName }?.let { return it.signatureHash }
        return null
    }
}
// KMK <--

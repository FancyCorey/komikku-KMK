package exh.recs.bestversion.fixture

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

interface BestVersionPairedFixtureDataStore {
    suspend fun inspect(spec: BestVersionPairedFixtureSpec, manifest: BestVersionPairedFixtureManifest?): BestVersionPairedFixtureObservedState

    suspend fun captureSideEffectBaseline(): BestVersionPairedFixtureSideEffectBaseline

    /** Applies exactly one idempotent fixture step and returns its updated typed ownership manifest. */
    suspend fun applyStep(
        spec: BestVersionPairedFixtureSpec,
        manifest: BestVersionPairedFixtureManifest,
        step: BestVersionPairedFixtureStep,
    ): BestVersionPairedFixtureManifest

    /** Deletes/restores only rows proven to be owned by [manifest]. */
    suspend fun cleanup(spec: BestVersionPairedFixtureSpec, manifest: BestVersionPairedFixtureManifest): Boolean
}

sealed interface BestVersionPairedFixtureSeedResult {
    data class Seeded(val manifest: BestVersionPairedFixtureManifest) : BestVersionPairedFixtureSeedResult
    data object NotAuthorized : BestVersionPairedFixtureSeedResult
    data object Collision : BestVersionPairedFixtureSeedResult
    data object RecoveryRecordCorrupt : BestVersionPairedFixtureSeedResult
    data object RecoveryRequired : BestVersionPairedFixtureSeedResult
    data class Failed(val cleanupCompleted: Boolean) : BestVersionPairedFixtureSeedResult
}

sealed interface BestVersionPairedFixtureCleanupResult {
    data object NothingToClean : BestVersionPairedFixtureCleanupResult
    data object Cleaned : BestVersionPairedFixtureCleanupResult
    data object RecoveryRecordCorrupt : BestVersionPairedFixtureCleanupResult
    data object ConflictOrFailure : BestVersionPairedFixtureCleanupResult
}

class BestVersionPairedFixtureCoordinator(
    private val recoveryStore: BestVersionPairedFixtureRecoveryStore,
    private val dataStore: BestVersionPairedFixtureDataStore,
    private val spec: BestVersionPairedFixtureSpec = BestVersionPairedFixtureSpec(),
) {
    private val mutex = Mutex()

    suspend fun seed(activation: BestVersionPairedFixtureActivation): BestVersionPairedFixtureSeedResult = mutex.withLock {
        if (!BestVersionPairedFixtureGate.isAllowed(activation)) {
            return@withLock BestVersionPairedFixtureSeedResult.NotAuthorized
        }

        when (val loaded = recoveryStore.load()) {
            BestVersionPairedFixtureManifestLoad.Corrupt ->
                return@withLock BestVersionPairedFixtureSeedResult.RecoveryRecordCorrupt
            is BestVersionPairedFixtureManifestLoad.Present -> {
                val current = try {
                    dataStore.inspect(spec, loaded.manifest)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    return@withLock BestVersionPairedFixtureSeedResult.RecoveryRequired
                }
                val isVerifiedComplete = loaded.manifest.pendingStep == null &&
                    loaded.manifest.completedSteps == BestVersionPairedFixtureStep.entries.toSet() &&
                    loaded.manifest.seededStateHash == current.sha256() &&
                    !current.isAbsent
                if (isVerifiedComplete) {
                    return@withLock BestVersionPairedFixtureSeedResult.Seeded(loaded.manifest)
                }
                if (!cleanupInternal(loaded.manifest)) {
                    return@withLock BestVersionPairedFixtureSeedResult.RecoveryRequired
                }
            }
            BestVersionPairedFixtureManifestLoad.Missing -> Unit
        }

        val preflight = try {
            dataStore.inspect(spec, null)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return@withLock BestVersionPairedFixtureSeedResult.Failed(cleanupCompleted = true)
        }
        if (!preflight.isAbsent) {
            return@withLock BestVersionPairedFixtureSeedResult.Collision
        }

        val baseline = try {
            dataStore.captureSideEffectBaseline()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return@withLock BestVersionPairedFixtureSeedResult.Failed(cleanupCompleted = true)
        }
        if (!baseline.isStructurallyValid()) {
            return@withLock BestVersionPairedFixtureSeedResult.Failed(cleanupCompleted = true)
        }
        var manifest = BestVersionPairedFixtureManifest(
            operationId = UUID.randomUUID().toString(),
            sideEffectBaseline = baseline,
        )
        if (!recoveryStore.save(manifest)) {
            return@withLock BestVersionPairedFixtureSeedResult.Failed(cleanupCompleted = true)
        }

        try {
            for (step in BestVersionPairedFixtureStep.entries) {
                manifest = manifest.copy(pendingStep = step)
                check(recoveryStore.save(manifest)) { "fixture-checkpoint-write-failed" }
                manifest = dataStore.applyStep(spec, manifest, step).copy(
                    pendingStep = null,
                    completedSteps = manifest.completedSteps + step,
                )
                check(recoveryStore.save(manifest)) { "fixture-checkpoint-write-failed" }
            }
            val hash = dataStore.inspect(spec, manifest).sha256()
            manifest = manifest.copy(seededStateHash = hash)
            check(recoveryStore.save(manifest)) { "fixture-checkpoint-write-failed" }
            BestVersionPairedFixtureSeedResult.Seeded(manifest)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { cleanupInternal(manifest) }
            throw e
        } catch (_: Exception) {
            val cleaned = withContext(NonCancellable) { cleanupInternal(manifest) }
            BestVersionPairedFixtureSeedResult.Failed(cleanupCompleted = cleaned)
        }
    }

    suspend fun cleanup(): BestVersionPairedFixtureCleanupResult = mutex.withLock {
        when (val loaded = recoveryStore.load()) {
            BestVersionPairedFixtureManifestLoad.Missing -> BestVersionPairedFixtureCleanupResult.NothingToClean
            BestVersionPairedFixtureManifestLoad.Corrupt -> BestVersionPairedFixtureCleanupResult.RecoveryRecordCorrupt
            is BestVersionPairedFixtureManifestLoad.Present -> {
                if (cleanupInternal(loaded.manifest)) {
                    BestVersionPairedFixtureCleanupResult.Cleaned
                } else {
                    BestVersionPairedFixtureCleanupResult.ConflictOrFailure
                }
            }
        }
    }

    private suspend fun cleanupInternal(manifest: BestVersionPairedFixtureManifest): Boolean {
        if (!manifest.isStructurallyValid()) return false
        val cleaned = try {
            dataStore.cleanup(spec, manifest)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        if (!cleaned) return false
        val absent = try {
            dataStore.inspect(spec, manifest).isAbsent
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        if (!absent) return false
        val reconciled = try {
            dataStore.inspect(spec, manifest).sideEffectsMatch(manifest.sideEffectBaseline)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        if (!reconciled) return false
        return recoveryStore.clear()
    }
}

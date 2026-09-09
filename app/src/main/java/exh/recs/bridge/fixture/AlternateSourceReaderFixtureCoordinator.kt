package exh.recs.bridge.fixture

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

data class AlternateSourceReaderPairedGraph(
    val operationId: String,
    val primaryMangaId: Long,
    val alternateMangaId: Long,
) {
    fun isStructurallyValid(): Boolean =
        runCatching { UUID.fromString(operationId) }.isSuccess &&
            primaryMangaId > 0L &&
            alternateMangaId > 0L &&
            primaryMangaId != alternateMangaId
}

sealed interface AlternateSourceReaderPairedGraphPreparation {
    data class Ready(val graph: AlternateSourceReaderPairedGraph) : AlternateSourceReaderPairedGraphPreparation
    data object NotAuthorized : AlternateSourceReaderPairedGraphPreparation
    data object Collision : AlternateSourceReaderPairedGraphPreparation
    data object RecoveryRequired : AlternateSourceReaderPairedGraphPreparation
    data object Failed : AlternateSourceReaderPairedGraphPreparation
}

data class AlternateSourceReaderFixtureBaseline(
    val primaryGapChapter: AlternateSourceReaderFixtureChapterSnapshot,
    val bridgeHash: String,
    val identityHash: String,
    val actionHistoryIds: Set<String>,
)

data class AlternateSourceReaderFixtureObservedState(
    val overlayRows: List<String> = emptyList(),
    val bridgeHash: String,
    val identityHash: String,
    val actionHistoryIds: Set<String>,
    val routeReady: Boolean = false,
) {
    val overlayAbsent: Boolean get() = overlayRows.isEmpty() && !routeReady

    fun baselinesMatch(manifest: AlternateSourceReaderFixtureManifest): Boolean =
        bridgeHash == manifest.bridgeBaselineHash &&
            identityHash == manifest.identityBaselineHash &&
            actionHistoryIds == manifest.actionHistoryBaselineIds

    fun sha256(): String {
        val canonical = buildList {
            addAll(overlayRows.sorted())
            add("bridge=$bridgeHash")
            add("identity=$identityHash")
            add("history=${actionHistoryIds.sorted().joinToString(",")}")
            add("ready=$routeReady")
        }.joinToString("\n")
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02X".format(it) }
    }
}

interface AlternateSourceReaderFixtureDataStore {
    suspend fun preparePairedGraph(): AlternateSourceReaderPairedGraphPreparation
    suspend fun captureBaseline(graph: AlternateSourceReaderPairedGraph): AlternateSourceReaderFixtureBaseline
    suspend fun inspect(manifest: AlternateSourceReaderFixtureManifest): AlternateSourceReaderFixtureObservedState

    suspend fun applyStep(
        manifest: AlternateSourceReaderFixtureManifest,
        step: AlternateSourceReaderFixtureStep,
    ): AlternateSourceReaderFixtureManifest

    suspend fun cleanupOverlay(manifest: AlternateSourceReaderFixtureManifest): Boolean
    suspend fun cleanupPairedGraph(operationId: String): Boolean
    suspend fun cleanupUnrecordedPairedGraph(): Boolean
    suspend fun isPairedGraphAbsent(operationId: String): Boolean
}

sealed interface AlternateSourceReaderFixtureSeedResult {
    data class Seeded(val manifest: AlternateSourceReaderFixtureManifest) : AlternateSourceReaderFixtureSeedResult
    data object NotAuthorized : AlternateSourceReaderFixtureSeedResult
    data object Collision : AlternateSourceReaderFixtureSeedResult
    data object RecoveryRecordCorrupt : AlternateSourceReaderFixtureSeedResult
    data object RecoveryRequired : AlternateSourceReaderFixtureSeedResult
    data class Failed(val cleanupCompleted: Boolean) : AlternateSourceReaderFixtureSeedResult
}

sealed interface AlternateSourceReaderFixtureCleanupResult {
    data object NothingToClean : AlternateSourceReaderFixtureCleanupResult
    data object Cleaned : AlternateSourceReaderFixtureCleanupResult
    data object RecoveryRecordCorrupt : AlternateSourceReaderFixtureCleanupResult
    data object ConflictOrFailure : AlternateSourceReaderFixtureCleanupResult
}

class AlternateSourceReaderFixtureCoordinator(
    private val recoveryStore: AlternateSourceReaderFixtureRecoveryStore,
    private val dataStore: AlternateSourceReaderFixtureDataStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    suspend fun seed(
        activation: AlternateSourceReaderFixtureActivation,
    ): AlternateSourceReaderFixtureSeedResult = mutex.withLock {
        if (!AlternateSourceReaderFixtureGate.isAllowed(activation)) {
            return@withLock AlternateSourceReaderFixtureSeedResult.NotAuthorized
        }

        when (val loaded = recoveryStore.load(now())) {
            AlternateSourceReaderFixtureManifestLoad.Corrupt ->
                return@withLock AlternateSourceReaderFixtureSeedResult.RecoveryRecordCorrupt
            is AlternateSourceReaderFixtureManifestLoad.Present -> {
                if (loaded.manifest.scenario != activation.scenario) {
                    return@withLock AlternateSourceReaderFixtureSeedResult.RecoveryRequired
                }
                val current = inspectOrNull(loaded.manifest)
                    ?: return@withLock AlternateSourceReaderFixtureSeedResult.RecoveryRequired
                val complete = loaded.manifest.pendingStep == null &&
                    loaded.manifest.completedSteps == AlternateSourceReaderFixtureStep.entries.toSet() &&
                    loaded.manifest.seededOverlayHash == current.sha256() &&
                    current.routeReady
                if (complete) return@withLock AlternateSourceReaderFixtureSeedResult.Seeded(loaded.manifest)
                if (!cleanupInternal(loaded.manifest)) {
                    return@withLock AlternateSourceReaderFixtureSeedResult.RecoveryRequired
                }
            }
            AlternateSourceReaderFixtureManifestLoad.Missing -> Unit
        }

        val graph = when (val prepared = preparePairedGraph()) {
            is AlternateSourceReaderPairedGraphPreparation.Ready -> prepared.graph
            AlternateSourceReaderPairedGraphPreparation.NotAuthorized ->
                return@withLock AlternateSourceReaderFixtureSeedResult.NotAuthorized
            AlternateSourceReaderPairedGraphPreparation.Collision ->
                return@withLock AlternateSourceReaderFixtureSeedResult.Collision
            AlternateSourceReaderPairedGraphPreparation.RecoveryRequired ->
                return@withLock AlternateSourceReaderFixtureSeedResult.RecoveryRequired
            AlternateSourceReaderPairedGraphPreparation.Failed ->
                return@withLock AlternateSourceReaderFixtureSeedResult.Failed(cleanupCompleted = false)
        }
        if (!graph.isStructurallyValid()) {
            return@withLock AlternateSourceReaderFixtureSeedResult.Failed(cleanupCompleted = false)
        }
        val baseline = try {
            dataStore.captureBaseline(graph)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { cleanupUnrecordedPairedGraph(graph.operationId) }
            throw e
        } catch (e: Exception) {
            diagnostic("captureBaseline failed: ${e::class.java.simpleName}: ${e.message}", e)
            val cleaned = withContext(NonCancellable) { cleanupUnrecordedPairedGraph(graph.operationId) }
            return@withLock AlternateSourceReaderFixtureSeedResult.Failed(cleanupCompleted = cleaned)
        }
        var manifest = AlternateSourceReaderFixtureManifest(
            operationId = UUID.randomUUID().toString(),
            pairedFixtureOperationId = graph.operationId,
            scenario = activation.scenario,
            createdAt = now(),
            completedSteps = setOf(
                AlternateSourceReaderFixtureStep.PAIRED_GRAPH,
                AlternateSourceReaderFixtureStep.BASELINES,
            ),
            primaryMangaId = graph.primaryMangaId,
            alternateMangaId = graph.alternateMangaId,
            primaryGapChapter = baseline.primaryGapChapter,
            bridgeBaselineHash = baseline.bridgeHash,
            identityBaselineHash = baseline.identityHash,
            actionHistoryBaselineIds = baseline.actionHistoryIds,
        )
        if (!recoveryStore.save(manifest, now())) {
            val cleaned = withContext(NonCancellable) { cleanupUnrecordedPairedGraph(graph.operationId) }
            return@withLock AlternateSourceReaderFixtureSeedResult.Failed(cleanupCompleted = cleaned)
        }

        try {
            for (step in MUTATION_STEPS) {
                manifest = manifest.copy(pendingStep = step)
                check(recoveryStore.save(manifest, now())) { "fixture-checkpoint-write-failed" }
                manifest = dataStore.applyStep(manifest, step).copy(
                    pendingStep = null,
                    completedSteps = manifest.completedSteps + step,
                )
                check(recoveryStore.save(manifest, now())) { "fixture-checkpoint-write-failed" }
            }
            val observed = dataStore.inspect(manifest)
            check(observed.routeReady) { "fixture-route-not-ready" }
            manifest = manifest.copy(seededOverlayHash = observed.sha256())
            check(recoveryStore.save(manifest, now())) { "fixture-checkpoint-write-failed" }
            AlternateSourceReaderFixtureSeedResult.Seeded(manifest)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { cleanupInternal(manifest) }
            throw e
        } catch (e: Exception) {
            diagnostic("overlay mutation failed: ${e::class.java.simpleName}: ${e.message}", e)
            val cleaned = withContext(NonCancellable) { cleanupInternal(manifest) }
            AlternateSourceReaderFixtureSeedResult.Failed(cleanupCompleted = cleaned)
        }
    }

    suspend fun cleanup(): AlternateSourceReaderFixtureCleanupResult = mutex.withLock {
        when (val loaded = recoveryStore.load(now())) {
            AlternateSourceReaderFixtureManifestLoad.Missing -> {
                if (cleanupUnrecordedPairedGraph()) {
                    AlternateSourceReaderFixtureCleanupResult.Cleaned
                } else {
                    AlternateSourceReaderFixtureCleanupResult.ConflictOrFailure
                }
            }
            AlternateSourceReaderFixtureManifestLoad.Corrupt -> AlternateSourceReaderFixtureCleanupResult.RecoveryRecordCorrupt
            is AlternateSourceReaderFixtureManifestLoad.Present -> {
                if (cleanupInternal(loaded.manifest)) {
                    AlternateSourceReaderFixtureCleanupResult.Cleaned
                } else {
                    AlternateSourceReaderFixtureCleanupResult.ConflictOrFailure
                }
            }
        }
    }

    private suspend fun preparePairedGraph(): AlternateSourceReaderPairedGraphPreparation = try {
        dataStore.preparePairedGraph()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        diagnostic("preparePairedGraph failed: ${e::class.java.simpleName}: ${e.message}", e)
        AlternateSourceReaderPairedGraphPreparation.Failed
    }

    private suspend fun inspectOrNull(
        manifest: AlternateSourceReaderFixtureManifest,
    ): AlternateSourceReaderFixtureObservedState? = try {
        dataStore.inspect(manifest)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private suspend fun cleanupInternal(manifest: AlternateSourceReaderFixtureManifest): Boolean {
        if (!manifest.isStructurallyValid(now())) return false
        val overlayCleaned = try {
            dataStore.cleanupOverlay(manifest)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        if (!overlayCleaned) return false
        val overlayState = inspectOrNull(manifest) ?: return false
        if (!overlayState.overlayAbsent || !overlayState.baselinesMatch(manifest)) return false
        val pairedCleaned = try {
            dataStore.cleanupPairedGraph(manifest.pairedFixtureOperationId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        if (!pairedCleaned) return false
        val pairedAbsent = try {
            dataStore.isPairedGraphAbsent(manifest.pairedFixtureOperationId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        if (!pairedAbsent) return false
        return recoveryStore.clear()
    }

    private suspend fun cleanupUnrecordedPairedGraph(operationId: String): Boolean {
        val cleaned = try {
            dataStore.cleanupPairedGraph(operationId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        if (!cleaned) return false
        return try {
            dataStore.isPairedGraphAbsent(operationId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun cleanupUnrecordedPairedGraph(): Boolean {
        val cleaned = try {
            dataStore.cleanupUnrecordedPairedGraph()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        if (!cleaned) return false
        return try {
            dataStore.isPairedGraphAbsent("")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    private fun diagnostic(message: String, throwable: Throwable? = null) {
        runCatching { Log.w(TAG, message, throwable) }
    }

    private companion object {
        const val TAG = "KMKFixture"

        val MUTATION_STEPS = listOf(
            AlternateSourceReaderFixtureStep.PRIMARY_GAP,
            AlternateSourceReaderFixtureStep.IDENTITY,
            AlternateSourceReaderFixtureStep.BRIDGE,
            AlternateSourceReaderFixtureStep.SESSION,
            AlternateSourceReaderFixtureStep.ROUTE,
        )
    }
}

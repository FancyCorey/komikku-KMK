package exh.recs.bestversion.fixture

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class BestVersionPairedFixtureCoordinatorTest {
    private val activation = BestVersionPairedFixtureActivation(
        isDebugBuild = true,
        mode = BestVersionPairedFixtureMode.PAIRED_RECORDS,
        evaluationModeEnabled = true,
        fixtureProfile = BestVersionPairedFixtureGate.ISOLATED_PROFILE,
        expectedSignerSha256 = "a".repeat(64),
        installedSources = setOf(
            BestVersionPairedFixtureSourceIdentity(
                BestVersionPairedFixtureGate.ALPHA_PACKAGE,
                BestVersionPairedFixtureGate.ALPHA_SOURCE_ID,
                "a".repeat(64),
                "Fixture Source Alpha",
                "Fixture Source",
                "1.6.0",
                1L,
            ),
            BestVersionPairedFixtureSourceIdentity(
                BestVersionPairedFixtureGate.BETA_PACKAGE,
                BestVersionPairedFixtureGate.BETA_SOURCE_ID,
                "a".repeat(64),
                "Fixture Source Beta",
                "Fixture Source",
                "1.6.0",
                1L,
            ),
        ),
    )

    @Test
    fun `seed checkpoints every step and retains a hash until explicit cleanup`() = runTest {
        val recovery = MemoryRecoveryStore()
        val data = FakeDataStore()
        val coordinator = BestVersionPairedFixtureCoordinator(recovery, data)

        val result = coordinator.seed(activation) as BestVersionPairedFixtureSeedResult.Seeded

        assertEquals(BestVersionPairedFixtureStep.entries.toSet(), result.manifest.completedSteps)
        assertTrue(result.manifest.seededStateHash?.matches(Regex("[0-9A-F]{64}")) == true)
        assertEquals(BestVersionPairedFixtureStep.entries.toList(), data.appliedSteps)
        assertTrue(recovery.saved.any { it.pendingStep != null })
        assertInstanceOf(BestVersionPairedFixtureManifestLoad.Present::class.java, recovery.load())

        assertEquals(BestVersionPairedFixtureCleanupResult.Cleaned, coordinator.cleanup())
        assertTrue(data.state.isAbsent)
        assertInstanceOf(BestVersionPairedFixtureManifestLoad.Missing::class.java, recovery.load())
    }

    @Test
    fun `seed persists and cleanup restores the exact side effect baseline`() = runTest {
        val baseline = BestVersionPairedFixtureSideEffectBaseline(
            qualitySignalIds = setOf(11L),
            migrationEventIds = setOf(UUID.randomUUID().toString()),
            migrationReceiptIds = setOf(UUID.randomUUID().toString()),
        )
        val recovery = MemoryRecoveryStore()
        val data = FakeDataStore().apply {
            state = BestVersionPairedFixtureObservedState(
                qualitySignalIds = baseline.qualitySignalIds,
                migrationEventIds = baseline.migrationEventIds,
                migrationReceiptIds = baseline.migrationReceiptIds,
            )
        }
        val coordinator = BestVersionPairedFixtureCoordinator(recovery, data)

        val seeded = coordinator.seed(activation) as BestVersionPairedFixtureSeedResult.Seeded

        assertEquals(baseline, seeded.manifest.sideEffectBaseline)
        assertEquals(BestVersionPairedFixtureCleanupResult.Cleaned, coordinator.cleanup())
        assertTrue(data.state.sideEffectsMatch(baseline))
    }

    @Test
    fun `unauthorized and colliding seeds perform no mutation`() = runTest {
        val recovery = MemoryRecoveryStore()
        val data = FakeDataStore()
        val coordinator = BestVersionPairedFixtureCoordinator(recovery, data)

        assertEquals(
            BestVersionPairedFixtureSeedResult.NotAuthorized,
            coordinator.seed(activation.copy(evaluationModeEnabled = false)),
        )
        assertTrue(data.appliedSteps.isEmpty())

        data.state = BestVersionPairedFixtureObservedState(mangaRows = listOf("foreign"))
        assertEquals(BestVersionPairedFixtureSeedResult.Collision, coordinator.seed(activation))
        assertTrue(data.appliedSteps.isEmpty())
    }

    @Test
    fun `invalid side effect baseline fails before the recovery manifest or fixture writes`() = runTest {
        val recovery = MemoryRecoveryStore()
        val data = FakeDataStore().apply {
            state = BestVersionPairedFixtureObservedState(qualitySignalIds = setOf(0L))
        }
        val coordinator = BestVersionPairedFixtureCoordinator(recovery, data)

        assertEquals(BestVersionPairedFixtureSeedResult.Failed(cleanupCompleted = true), coordinator.seed(activation))
        assertTrue(data.appliedSteps.isEmpty())
        assertInstanceOf(BestVersionPairedFixtureManifestLoad.Missing::class.java, recovery.load())
    }

    @Test
    fun `existing recovery is cleaned before a fresh seed`() = runTest {
        val prior = BestVersionPairedFixtureManifest(operationId = UUID.randomUUID().toString())
        val recovery = MemoryRecoveryStore(prior)
        val data = FakeDataStore().apply {
            state = BestVersionPairedFixtureObservedState(mangaRows = listOf("owned"))
        }
        val coordinator = BestVersionPairedFixtureCoordinator(recovery, data)

        assertInstanceOf(BestVersionPairedFixtureSeedResult.Seeded::class.java, coordinator.seed(activation))
        assertEquals(1, data.cleanupCalls)
    }

    @Test
    fun `repeated seed reuses a hash-identical complete fixture without mutation`() = runTest {
        val recovery = MemoryRecoveryStore()
        val data = FakeDataStore()
        val coordinator = BestVersionPairedFixtureCoordinator(recovery, data)
        val first = coordinator.seed(activation) as BestVersionPairedFixtureSeedResult.Seeded
        val firstApplyCount = data.appliedSteps.size

        val second = coordinator.seed(activation) as BestVersionPairedFixtureSeedResult.Seeded

        assertEquals(first.manifest, second.manifest)
        assertEquals(firstApplyCount, data.appliedSteps.size)
        assertEquals(0, data.cleanupCalls)
    }

    @Test
    fun `corrupt recovery blocks seed and cleanup`() = runTest {
        val recovery = MemoryRecoveryStore(corrupt = true)
        val coordinator = BestVersionPairedFixtureCoordinator(recovery, FakeDataStore())

        assertEquals(BestVersionPairedFixtureSeedResult.RecoveryRecordCorrupt, coordinator.seed(activation))
        assertEquals(BestVersionPairedFixtureCleanupResult.RecoveryRecordCorrupt, coordinator.cleanup())
    }

    @Test
    fun `ordinary failure performs exact cleanup and reports whether it succeeded`() = runTest {
        val recovery = MemoryRecoveryStore()
        val data = FakeDataStore(failAt = BestVersionPairedFixtureStep.CATEGORY)
        val coordinator = BestVersionPairedFixtureCoordinator(recovery, data)

        assertEquals(BestVersionPairedFixtureSeedResult.Failed(cleanupCompleted = true), coordinator.seed(activation))
        assertEquals(1, data.cleanupCalls)
        assertTrue(data.state.isAbsent)
    }

    @Test
    fun `cancellation at every write boundary is rethrown after cleanup`() {
        BestVersionPairedFixtureStep.entries.forEach { cancelledStep ->
            lateinit var recovery: MemoryRecoveryStore
            lateinit var data: FakeDataStore
            assertThrows(CancellationException::class.java) {
                runTest {
                    recovery = MemoryRecoveryStore()
                    data = FakeDataStore(cancelAt = cancelledStep)
                    val coordinator = BestVersionPairedFixtureCoordinator(recovery, data)
                    coordinator.seed(activation)
                }
            }
            assertEquals(1, data.cleanupCalls)
            assertTrue(data.state.isAbsent)
            assertInstanceOf(BestVersionPairedFixtureManifestLoad.Missing::class.java, recovery.load())
        }
    }

    @Test
    fun `checkpoint persistence failure after a write triggers cleanup`() = runTest {
        val recovery = MemoryRecoveryStore(failSaveNumber = 3)
        val data = FakeDataStore()
        val coordinator = BestVersionPairedFixtureCoordinator(recovery, data)

        assertEquals(BestVersionPairedFixtureSeedResult.Failed(cleanupCompleted = true), coordinator.seed(activation))
        assertEquals(listOf(BestVersionPairedFixtureStep.ORIGIN_MANGA), data.appliedSteps)
        assertEquals(1, data.cleanupCalls)
        assertTrue(data.state.isAbsent)
    }

    @Test
    fun `failed cleanup retains recovery record and blocks reseed`() = runTest {
        val prior = BestVersionPairedFixtureManifest(operationId = UUID.randomUUID().toString())
        val recovery = MemoryRecoveryStore(prior)
        val data = FakeDataStore(cleanupSucceeds = false).apply {
            state = BestVersionPairedFixtureObservedState(mangaRows = listOf("owned"))
        }
        val coordinator = BestVersionPairedFixtureCoordinator(recovery, data)

        assertEquals(BestVersionPairedFixtureSeedResult.RecoveryRequired, coordinator.seed(activation))
        assertInstanceOf(BestVersionPairedFixtureManifestLoad.Present::class.java, recovery.load())
    }

    @Test
    fun `side effect baseline mismatch retains recovery record after graph cleanup`() = runTest {
        val recovery = MemoryRecoveryStore()
        val data = FakeDataStore()
        val coordinator = BestVersionPairedFixtureCoordinator(recovery, data)
        coordinator.seed(activation)
        data.state = data.state.copy(migrationEventIds = setOf("uncorrelated"))

        assertEquals(BestVersionPairedFixtureCleanupResult.ConflictOrFailure, coordinator.cleanup())
        assertTrue(data.state.isAbsent)
        assertInstanceOf(BestVersionPairedFixtureManifestLoad.Present::class.java, recovery.load())
    }

    @Test
    fun `single flight prevents overlapping data-store mutations`() = runTest {
        val recovery = MemoryRecoveryStore()
        val data = FakeDataStore(stepDelayMillis = 5L)
        val coordinator = BestVersionPairedFixtureCoordinator(recovery, data)

        val first = async { coordinator.seed(activation) }
        val second = async { coordinator.cleanup() }
        first.await()
        second.await()

        assertEquals(1, data.maxConcurrentCalls.get())
    }

    private class MemoryRecoveryStore(
        initial: BestVersionPairedFixtureManifest? = null,
        private val corrupt: Boolean = false,
        private val failSaveNumber: Int? = null,
    ) : BestVersionPairedFixtureRecoveryStore {
        private var current = initial
        val saved = mutableListOf<BestVersionPairedFixtureManifest>()
        private var saveCount = 0

        override fun load(): BestVersionPairedFixtureManifestLoad = when {
            corrupt -> BestVersionPairedFixtureManifestLoad.Corrupt
            current == null -> BestVersionPairedFixtureManifestLoad.Missing
            else -> BestVersionPairedFixtureManifestLoad.Present(requireNotNull(current))
        }

        override fun save(manifest: BestVersionPairedFixtureManifest): Boolean {
            saveCount++
            if (saveCount == failSaveNumber) return false
            current = manifest
            saved += manifest
            return true
        }

        override fun clear(): Boolean {
            current = null
            return true
        }
    }

    private class FakeDataStore(
        private val failAt: BestVersionPairedFixtureStep? = null,
        private val cancelAt: BestVersionPairedFixtureStep? = null,
        private val cleanupSucceeds: Boolean = true,
        private val stepDelayMillis: Long = 0L,
    ) : BestVersionPairedFixtureDataStore {
        var state = BestVersionPairedFixtureObservedState()
        val appliedSteps = mutableListOf<BestVersionPairedFixtureStep>()
        var cleanupCalls = 0
        private val concurrentCalls = AtomicInteger()
        val maxConcurrentCalls = AtomicInteger()

        override suspend fun inspect(
            spec: BestVersionPairedFixtureSpec,
            manifest: BestVersionPairedFixtureManifest?,
        ) = state

        override suspend fun captureSideEffectBaseline() = BestVersionPairedFixtureSideEffectBaseline(
            qualitySignalIds = state.qualitySignalIds,
            migrationEventIds = state.migrationEventIds,
            migrationReceiptIds = state.migrationReceiptIds,
        )

        override suspend fun applyStep(
            spec: BestVersionPairedFixtureSpec,
            manifest: BestVersionPairedFixtureManifest,
            step: BestVersionPairedFixtureStep,
        ): BestVersionPairedFixtureManifest = guarded {
            if (stepDelayMillis > 0) delay(stepDelayMillis)
            if (step == cancelAt) throw CancellationException("test cancellation")
            if (step == failAt) error("test failure")
            appliedSteps += step
            state = state.copy(mangaRows = appliedSteps.map { it.name })
            when (step) {
                BestVersionPairedFixtureStep.ORIGIN_MANGA -> manifest.copy(originMangaId = 41L)
                BestVersionPairedFixtureStep.TARGET_MANGA -> manifest.copy(targetMangaId = 42L)
                BestVersionPairedFixtureStep.CHAPTERS_AND_HISTORY -> manifest.copy(
                    originChapterIds = listOf(51L, 52L),
                    targetChapterIds = listOf(61L, 62L),
                )
                BestVersionPairedFixtureStep.CATEGORY -> manifest.copy(categoryId = 71L)
                BestVersionPairedFixtureStep.TASTES,
                BestVersionPairedFixtureStep.GROUP_LINKS,
                -> manifest
            }
        }

        override suspend fun cleanup(
            spec: BestVersionPairedFixtureSpec,
            manifest: BestVersionPairedFixtureManifest,
        ): Boolean = guarded {
            cleanupCalls++
            if (cleanupSucceeds) {
                state = state.copy(
                    mangaRows = emptyList(),
                    chapterRows = emptyList(),
                    historyRows = emptyList(),
                    categoryRows = emptyList(),
                    tasteRows = emptyList(),
                    groupRows = emptyList(),
                )
            }
            cleanupSucceeds
        }

        private suspend fun <T> guarded(block: suspend () -> T): T {
            val current = concurrentCalls.incrementAndGet()
            maxConcurrentCalls.updateAndGet { maxOf(it, current) }
            return try {
                block()
            } finally {
                concurrentCalls.decrementAndGet()
            }
        }
    }
}

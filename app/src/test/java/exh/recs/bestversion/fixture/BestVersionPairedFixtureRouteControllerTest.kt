package exh.recs.bestversion.fixture

import exh.recs.bestversion.BestVersionCompareScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BestVersionPairedFixtureRouteControllerTest {
    @Test
    fun `prepare returns an exact descriptor only after a complete seed`() = runTest {
        val recovery = RouteMemoryRecoveryStore()
        val coordinator = BestVersionPairedFixtureCoordinator(recovery, RouteDataStore())
        val controller = BestVersionPairedFixtureRouteController(coordinator) { allowedActivation() }

        val result = controller.prepare() as BestVersionPairedFixtureRoutePreparation.Ready

        assertEquals(101L, result.descriptor.originMangaId)
        assertTrue(result.descriptor.operationId.isNotBlank())
        val screen = result.descriptor.toCanonicalScreen()
        assertEquals(result.descriptor.originMangaId, screen.originMangaId)
        assertEquals(result.descriptor.operationId, screen.fixtureOperationId)
        val stored = recovery.load() as BestVersionPairedFixtureManifestLoad.Present
        assertEquals(result.descriptor.operationId, stored.manifest.operationId)
    }

    @Test
    fun `command opens the canonical screen only after a ready preparation`() = runTest {
        val controller = BestVersionPairedFixtureRouteController(
            BestVersionPairedFixtureCoordinator(RouteMemoryRecoveryStore(), RouteDataStore()),
        ) { allowedActivation() }
        var openedScreen: BestVersionCompareScreen? = null
        val command = BestVersionPairedFixtureCommandController(controller)

        val result = command.prepareAndOpen { openedScreen = it } as BestVersionPairedFixtureRoutePreparation.Ready

        assertEquals(result.descriptor.originMangaId, openedScreen?.originMangaId)
        assertEquals(result.descriptor.operationId, openedScreen?.fixtureOperationId)
        assertEquals(BestVersionPairedFixtureCommandState.Opened, command.state.value)
    }

    @Test
    fun `command does not navigate when preparation is unauthorized`() = runTest {
        val controller = BestVersionPairedFixtureRouteController(
            BestVersionPairedFixtureCoordinator(RouteMemoryRecoveryStore(), RouteDataStore()),
        ) { allowedActivation().copy(evaluationModeEnabled = false) }
        var navigationCount = 0
        val command = BestVersionPairedFixtureCommandController(controller)

        assertEquals(BestVersionPairedFixtureRoutePreparation.NotAuthorized, command.prepareAndOpen { navigationCount += 1 })
        assertEquals(0, navigationCount)
        assertEquals(BestVersionPairedFixtureCommandState.NotAuthorized, command.state.value)
    }

    @Test
    fun `command exposes collision and failure terminals`() = runTest {
        val collision = BestVersionPairedFixtureCommandController(
            BestVersionPairedFixtureRouteController(
                BestVersionPairedFixtureCoordinator(
                    RouteMemoryRecoveryStore(),
                    RouteDataStore(initialState = BestVersionPairedFixtureObservedState(mangaRows = listOf("foreign"))),
                ),
            ) { allowedActivation() },
        )
        assertEquals(BestVersionPairedFixtureRoutePreparation.Collision, collision.prepareAndOpen {})
        assertEquals(BestVersionPairedFixtureCommandState.Collision, collision.state.value)

        val failed = BestVersionPairedFixtureCommandController(
            BestVersionPairedFixtureRouteController(
                BestVersionPairedFixtureCoordinator(RouteMemoryRecoveryStore(), RouteDataStore(fail = true)),
            ) { allowedActivation() },
        )
        assertEquals(BestVersionPairedFixtureRoutePreparation.Failed, failed.prepareAndOpen {})
        assertEquals(BestVersionPairedFixtureCommandState.Failed, failed.state.value)
    }

    @Test
    fun `command resets observable state when preparation is cancelled`() {
        val command = BestVersionPairedFixtureCommandController(
            BestVersionPairedFixtureRouteController(
                BestVersionPairedFixtureCoordinator(RouteMemoryRecoveryStore(), RouteDataStore(cancel = true)),
            ) { allowedActivation() },
        )

        assertThrows(CancellationException::class.java) { runTest { command.prepareAndOpen {} } }
        assertEquals(BestVersionPairedFixtureCommandState.Idle, command.state.value)
    }

    @Test
    fun `prepare preserves unauthorized collision and ordinary failure terminals`() = runTest {
        val unauthorized = BestVersionPairedFixtureRouteController(
            BestVersionPairedFixtureCoordinator(RouteMemoryRecoveryStore(), RouteDataStore()),
        ) { allowedActivation().copy(evaluationModeEnabled = false) }
        assertEquals(BestVersionPairedFixtureRoutePreparation.NotAuthorized, unauthorized.prepare())

        val collision = BestVersionPairedFixtureRouteController(
            BestVersionPairedFixtureCoordinator(
                RouteMemoryRecoveryStore(),
                RouteDataStore(initialState = BestVersionPairedFixtureObservedState(mangaRows = listOf("foreign"))),
            ),
        ) { allowedActivation() }
        assertEquals(BestVersionPairedFixtureRoutePreparation.Collision, collision.prepare())

        val failure = BestVersionPairedFixtureRouteController(
            BestVersionPairedFixtureCoordinator(RouteMemoryRecoveryStore(), RouteDataStore(fail = true)),
        ) { allowedActivation() }
        assertEquals(BestVersionPairedFixtureRoutePreparation.Failed, failure.prepare())
    }

    @Test
    fun `prepare rethrows cancellation after coordinator cleanup`() {
        val recovery = RouteMemoryRecoveryStore()
        val dataStore = RouteDataStore(cancel = true)
        val controller = BestVersionPairedFixtureRouteController(
            BestVersionPairedFixtureCoordinator(recovery, dataStore),
        ) { allowedActivation() }

        assertThrows(CancellationException::class.java) { runTest { controller.prepare() } }
        assertTrue(dataStore.cleaned)
        assertTrue(recovery.load() is BestVersionPairedFixtureManifestLoad.Missing)
    }

    private fun allowedActivation() = BestVersionPairedFixtureActivation(
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

    private class RouteMemoryRecoveryStore : BestVersionPairedFixtureRecoveryStore {
        private var manifest: BestVersionPairedFixtureManifest? = null

        override fun load(): BestVersionPairedFixtureManifestLoad = manifest?.let {
            BestVersionPairedFixtureManifestLoad.Present(it)
        } ?: BestVersionPairedFixtureManifestLoad.Missing

        override fun save(manifest: BestVersionPairedFixtureManifest): Boolean {
            this.manifest = manifest
            return true
        }

        override fun clear(): Boolean {
            manifest = null
            return true
        }
    }

    private class RouteDataStore(
        initialState: BestVersionPairedFixtureObservedState = BestVersionPairedFixtureObservedState(),
        private val fail: Boolean = false,
        private val cancel: Boolean = false,
    ) : BestVersionPairedFixtureDataStore {
        private var state = initialState
        var cleaned = false

        override suspend fun inspect(
            spec: BestVersionPairedFixtureSpec,
            manifest: BestVersionPairedFixtureManifest?,
        ) = state

        override suspend fun captureSideEffectBaseline() = BestVersionPairedFixtureSideEffectBaseline()

        override suspend fun applyStep(
            spec: BestVersionPairedFixtureSpec,
            manifest: BestVersionPairedFixtureManifest,
            step: BestVersionPairedFixtureStep,
        ): BestVersionPairedFixtureManifest {
            if (cancel) throw CancellationException("fixture route cancelled")
            if (fail) error("fixture route failed")
            state = BestVersionPairedFixtureObservedState(mangaRows = listOf(step.name))
            return when (step) {
                BestVersionPairedFixtureStep.ORIGIN_MANGA -> manifest.copy(originMangaId = 101L)
                BestVersionPairedFixtureStep.TARGET_MANGA -> manifest.copy(targetMangaId = 102L)
                BestVersionPairedFixtureStep.CHAPTERS_AND_HISTORY -> manifest.copy(
                    originChapterIds = listOf(201L),
                    targetChapterIds = listOf(202L),
                )
                BestVersionPairedFixtureStep.CATEGORY -> manifest.copy(categoryId = 301L)
                BestVersionPairedFixtureStep.TASTES,
                BestVersionPairedFixtureStep.GROUP_LINKS,
                -> manifest
            }
        }

        override suspend fun cleanup(
            spec: BestVersionPairedFixtureSpec,
            manifest: BestVersionPairedFixtureManifest,
        ): Boolean {
            cleaned = true
            state = BestVersionPairedFixtureObservedState()
            return true
        }
    }
}

package exh.recs.bridge.fixture

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlternateSourceReaderFixtureCommandControllerTest {
    @Test
    fun `prepare snapshots scenario serializes commands and launches exactly once`() = runTest {
        var selected = AlternateSourceReaderFixtureScenario.EXACT_GAP
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val prepared = mutableListOf<Pair<AlternateSourceReaderFixtureScenario, AlternateSourceReaderFixtureDestination>>()
        val launches = mutableListOf<AlternateSourceReaderFixtureLaunch>()
        val gateway = object : AlternateSourceReaderFixtureCommandGateway {
            override suspend fun prepare(
                scenario: AlternateSourceReaderFixtureScenario,
                destination: AlternateSourceReaderFixtureDestination,
            ): AlternateSourceReaderFixturePrepareOutcome {
                prepared += scenario to destination
                entered.complete(Unit)
                release.await()
                return AlternateSourceReaderFixturePrepareOutcome.Ready(route(destination))
            }

            override suspend fun cleanup() = AlternateSourceReaderFixtureCleanupOutcome.Cleaned
        }
        val controller = AlternateSourceReaderFixtureCommandController(
            scenarioProvider = { selected },
            gateway = gateway,
            launcher = { launches += it },
            scope = backgroundScope,
        )

        assertTrue(controller.prepareAndOpenReader())
        entered.await()
        selected = AlternateSourceReaderFixtureScenario.STALE
        assertFalse(controller.prepareAndOpenManga())
        assertFalse(controller.cleanup())
        release.complete(Unit)
        runCurrent()

        assertEquals(
            listOf(AlternateSourceReaderFixtureScenario.EXACT_GAP to AlternateSourceReaderFixtureDestination.READER),
            prepared,
        )
        assertEquals(listOf(route(AlternateSourceReaderFixtureDestination.READER)), launches)
        assertEquals(
            AlternateSourceReaderFixtureCommandState.Succeeded(AlternateSourceReaderFixtureCommand.OPEN_READER),
            controller.state.value,
        )
    }

    @Test
    fun `off scenario refuses prepare without calling gateway`() = runTest {
        var calls = 0
        val controller = AlternateSourceReaderFixtureCommandController(
            scenarioProvider = { AlternateSourceReaderFixtureScenario.OFF },
            gateway = gateway(prepareAction = {
                calls += 1
                AlternateSourceReaderFixturePrepareOutcome.Failed
            }),
            launcher = { error("must-not-launch") },
            scope = backgroundScope,
        )

        assertFalse(controller.prepareAndOpenManga())
        runCurrent()

        assertEquals(0, calls)
        assertEquals(AlternateSourceReaderFixtureCommandState.Idle, controller.state.value)
    }

    @Test
    fun `prepare preserves partial and ordinary failure terminals`() = runTest {
        val outcomes = ArrayDeque<AlternateSourceReaderFixturePrepareOutcome>().apply {
            add(AlternateSourceReaderFixturePrepareOutcome.RecoveryRequired)
            add(AlternateSourceReaderFixturePrepareOutcome.Failed)
        }
        val controller = AlternateSourceReaderFixtureCommandController(
            scenarioProvider = { AlternateSourceReaderFixtureScenario.EXACT_GAP },
            gateway = gateway(prepareAction = { outcomes.removeFirst() }),
            launcher = { error("must-not-launch") },
            scope = backgroundScope,
        )

        assertTrue(controller.prepareAndOpenManga())
        runCurrent()
        assertEquals(
            AlternateSourceReaderFixtureCommandState.Partial(AlternateSourceReaderFixtureCommand.OPEN_MANGA),
            controller.state.value,
        )
        assertTrue(controller.dismissResult())

        assertTrue(controller.prepareAndOpenManga())
        runCurrent()
        assertEquals(
            AlternateSourceReaderFixtureCommandState.Failed(AlternateSourceReaderFixtureCommand.OPEN_MANGA),
            controller.state.value,
        )
    }

    @Test
    fun `launcher ordinary failure is partial and never launches twice`() = runTest {
        var launches = 0
        val controller = AlternateSourceReaderFixtureCommandController(
            scenarioProvider = { AlternateSourceReaderFixtureScenario.EXACT_GAP },
            gateway = gateway(prepareAction = {
                AlternateSourceReaderFixturePrepareOutcome.Ready(route(AlternateSourceReaderFixtureDestination.MANGA))
            }),
            launcher = {
                launches += 1
                error("activity-unavailable")
            },
            scope = backgroundScope,
        )

        assertTrue(controller.prepareAndOpenManga())
        runCurrent()

        assertEquals(1, launches)
        assertEquals(
            AlternateSourceReaderFixtureCommandState.Partial(AlternateSourceReaderFixtureCommand.OPEN_MANGA),
            controller.state.value,
        )
    }

    @Test
    fun `prepare cancellation resets state and does not launch`() = runTest {
        var launches = 0
        val controller = AlternateSourceReaderFixtureCommandController(
            scenarioProvider = { AlternateSourceReaderFixtureScenario.PROCESS_RECREATED },
            gateway = gateway(prepareAction = { throw CancellationException("cancelled") }),
            launcher = { launches += 1 },
            scope = backgroundScope,
        )

        assertTrue(controller.prepareAndOpenReader())
        runCurrent()

        assertEquals(0, launches)
        assertEquals(AlternateSourceReaderFixtureCommandState.Idle, controller.state.value)
    }

    @Test
    fun `cleanup remains owned by application scope and reports completion`() = runTest {
        val release = CompletableDeferred<Unit>()
        var cleanups = 0
        val controller = AlternateSourceReaderFixtureCommandController(
            scenarioProvider = { AlternateSourceReaderFixtureScenario.EXACT_GAP },
            gateway = gateway(cleanupAction = {
                release.await()
                cleanups += 1
                AlternateSourceReaderFixtureCleanupOutcome.Cleaned
            }),
            launcher = { error("must-not-launch") },
            scope = backgroundScope,
        )

        assertTrue(controller.cleanup())
        runCurrent()
        assertEquals(
            AlternateSourceReaderFixtureCommandState.Running(AlternateSourceReaderFixtureCommand.CLEANUP),
            controller.state.value,
        )

        release.complete(Unit)
        runCurrent()

        assertEquals(1, cleanups)
        assertEquals(
            AlternateSourceReaderFixtureCommandState.Succeeded(AlternateSourceReaderFixtureCommand.CLEANUP),
            controller.state.value,
        )
    }

    @Test
    fun `cleanup preserves partial failure and cancellation terminals`() = runTest {
        val outcomes = ArrayDeque<suspend () -> AlternateSourceReaderFixtureCleanupOutcome>().apply {
            add { AlternateSourceReaderFixtureCleanupOutcome.Partial }
            add { AlternateSourceReaderFixtureCleanupOutcome.Failed }
            add { throw CancellationException("cleanup-cancelled") }
        }
        val controller = AlternateSourceReaderFixtureCommandController(
            scenarioProvider = { AlternateSourceReaderFixtureScenario.EXACT_GAP },
            gateway = gateway(cleanupAction = { outcomes.removeFirst().invoke() }),
            launcher = { error("must-not-launch") },
            scope = backgroundScope,
        )

        assertTrue(controller.cleanup())
        runCurrent()
        assertEquals(
            AlternateSourceReaderFixtureCommandState.Partial(AlternateSourceReaderFixtureCommand.CLEANUP),
            controller.state.value,
        )
        assertTrue(controller.dismissResult())

        assertTrue(controller.cleanup())
        runCurrent()
        assertEquals(
            AlternateSourceReaderFixtureCommandState.Failed(AlternateSourceReaderFixtureCommand.CLEANUP),
            controller.state.value,
        )
        assertTrue(controller.dismissResult())

        assertTrue(controller.cleanup())
        runCurrent()
        assertEquals(AlternateSourceReaderFixtureCommandState.Idle, controller.state.value)
    }

    @Test
    fun `fatal launcher errors are not converted into recoverable fixture state`() {
        assertThrows(AssertionError::class.java) {
            runTest {
                val controller = AlternateSourceReaderFixtureCommandController(
                    scenarioProvider = { AlternateSourceReaderFixtureScenario.EXACT_GAP },
                    gateway = gateway(prepareAction = {
                        AlternateSourceReaderFixturePrepareOutcome.Ready(
                            route(AlternateSourceReaderFixtureDestination.MANGA),
                        )
                    }),
                    launcher = { throw AssertionError("fatal") },
                    scope = this,
                )

                assertTrue(controller.prepareAndOpenManga())
                runCurrent()
            }
        }
    }

    private fun gateway(
        prepareAction: suspend () -> AlternateSourceReaderFixturePrepareOutcome = {
            AlternateSourceReaderFixturePrepareOutcome.Failed
        },
        cleanupAction: suspend () -> AlternateSourceReaderFixtureCleanupOutcome = {
            AlternateSourceReaderFixtureCleanupOutcome.Cleaned
        },
    ) = object : AlternateSourceReaderFixtureCommandGateway {
        override suspend fun prepare(
            scenario: AlternateSourceReaderFixtureScenario,
            destination: AlternateSourceReaderFixtureDestination,
        ) = prepareAction()

        override suspend fun cleanup() = cleanupAction()
    }

    private fun route(destination: AlternateSourceReaderFixtureDestination) = AlternateSourceReaderFixtureLaunch(
        destination = destination,
        mangaId = 101L,
        chapterId = if (destination == AlternateSourceReaderFixtureDestination.READER) 201L else null,
    )
}

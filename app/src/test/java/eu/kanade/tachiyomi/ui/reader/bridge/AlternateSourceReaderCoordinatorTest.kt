package eu.kanade.tachiyomi.ui.reader.bridge

import exh.recs.bridge.AlternateSourceBridgeController
import exh.recs.bridge.AlternateSourceBridgeMutationResult
import exh.recs.matching.CrossSourceIdentityAuthorizationResolver
import exh.recs.matching.CrossSourceIdentityDecisionController
import exh.recs.matching.CrossSourceIdentityMutation
import exh.recs.matching.CrossSourceIdentityMutationResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.interactor.GetAlternateSourceBridge
import tachiyomi.domain.taste.model.AlternateSourceBridge
import tachiyomi.domain.taste.model.AlternateSourceBridgeMapping
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingKey
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingRelation
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingState
import tachiyomi.domain.taste.model.AlternateSourceBridgePolicy
import tachiyomi.domain.taste.model.AlternateSourceBridgeReviewState
import tachiyomi.domain.taste.model.AlternateSourceBridgeState

class AlternateSourceReaderCoordinatorTest {

    private val getBridge = mockk<GetAlternateSourceBridge>()
    private val routeResolver = mockk<AlternateSourceReaderRouteResolver>()
    private val bridgeController = mockk<AlternateSourceBridgeController>()
    private val identityResolver = mockk<CrossSourceIdentityAuthorizationResolver>()
    private val identityController = mockk<CrossSourceIdentityDecisionController>()

    @Test
    fun `confirmed entry persists pending session before switching and commits afterward`() = runTest {
        val values = linkedMapOf<String, Any?>()
        val store = sessionStore(values)
        val state = bridgeState()
        coEvery { identityResolver.isConfirmed(any(), any(), any(), any()) } returns true
        coEvery { getBridge.await(readerBridgeKey()) } returns state
        coEvery {
            routeResolver.resolve(
                readerBridgeKey(),
                AlternateSourceReaderRouteRole.ALTERNATE,
                "/chapter/alternate",
                0,
            )
        } returns AlternateSourceReaderRouteResolver.Result.Resolved(
            readerRoute(AlternateSourceReaderRouteRole.ALTERNATE),
        )
        var observedPending = false
        val coordinator = coordinator(store) { route ->
            val pending = (store.read() as AlternateSourceReaderStateCodec.DecodeResult.Valid).session
            observedPending = pending.currentRoute.role == AlternateSourceReaderRouteRole.PRIMARY &&
                pending.pendingRouteFingerprint == AlternateSourceReaderRouteFingerprint.of(route)
            true
        }

        assertEquals(
            AlternateSourceReaderCommandResult.Entered(READER_TARGET_ID),
            coordinator.enter(entryRequest()),
        )

        val committed = (store.read() as AlternateSourceReaderStateCodec.DecodeResult.Valid).session
        assertTrue(observedPending)
        assertEquals(AlternateSourceReaderRouteRole.ALTERNATE, committed.currentRoute.role)
        assertEquals(null, committed.pendingRouteFingerprint)
        assertEquals(1, committed.transitionCount)
        assertEquals(AlternateSourceReaderPhase.ALTERNATE, coordinator.state.value.phase)
    }

    @Test
    fun `provisional entry requires explicit per-use confirmation and does not resolve or switch`() = runTest {
        val store = sessionStore()
        coEvery { identityResolver.isConfirmed(any(), any(), any(), any()) } returns true
        coEvery { getBridge.await(readerBridgeKey()) } returns bridgeState(
            mappingState = AlternateSourceBridgeMappingState.PROVISIONAL,
        )
        var switched = false
        val coordinator = coordinator(store) {
            switched = true
            true
        }

        assertEquals(
            AlternateSourceReaderCommandResult.RequiresProvisionalConfirmation(READER_TARGET_ID),
            coordinator.enter(entryRequest()),
        )
        assertFalse(switched)
        coVerify(exactly = 0) { routeResolver.resolve(any(), any(), any(), any()) }
        assertTrue(store.read() is AlternateSourceReaderStateCodec.DecodeResult.Invalid)
    }

    @Test
    fun `provisional uncertainty notice is acknowledged exactly once in stored session`() = runTest {
        val store = sessionStore()
        coEvery { identityResolver.isConfirmed(any(), any(), any(), any()) } returns true
        coEvery { getBridge.await(readerBridgeKey()) } returns bridgeState(
            mappingState = AlternateSourceBridgeMappingState.PROVISIONAL,
        )
        coEvery { routeResolver.resolve(any(), any(), any(), any()) } returns
            AlternateSourceReaderRouteResolver.Result.Resolved(
                readerRoute(AlternateSourceReaderRouteRole.ALTERNATE),
            )
        val coordinator = coordinator(store) { true }

        assertEquals(
            AlternateSourceReaderCommandResult.Entered(READER_TARGET_ID),
            coordinator.enter(entryRequest(), confirmProvisional = true),
        )
        assertTrue(coordinator.hasUnacknowledgedUncertainAlignment())
        assertTrue(coordinator.acknowledgeUncertainAlignmentNotice())
        assertFalse(coordinator.hasUnacknowledgedUncertainAlignment())
        assertFalse(coordinator.acknowledgeUncertainAlignmentNotice())
        val stored = (store.read() as AlternateSourceReaderStateCodec.DecodeResult.Valid).session
        assertTrue(stored.uncertainAlignmentNoticeEmitted)
    }

    @Test
    fun `selection requires explicit pair confirmation before creating a provisional mapping`() = runTest {
        val store = sessionStore()
        val created = bridgeState(
            bridgeUpdatedAt = 3_000L,
            mappingUpdatedAt = 3_000L,
            mappingState = AlternateSourceBridgeMappingState.PROVISIONAL,
        )
        coEvery { identityResolver.isConfirmed(any(), any(), any(), any()) } returnsMany
            listOf(false, false, true, true)
        coEvery { identityController.mutate(any(), CrossSourceIdentityMutation.CONFIRM) } returns
            CrossSourceIdentityMutationResult.APPLIED
        coEvery { getBridge.await(readerBridgeKey()) } returnsMany listOf(null, created)
        coEvery { bridgeController.apply(any(), any()) } returns AlternateSourceBridgeMutationResult.APPLIED
        coEvery {
            routeResolver.resolve(
                readerBridgeKey(),
                AlternateSourceReaderRouteRole.ALTERNATE,
                "/chapter/alternate",
                0,
            )
        } returns AlternateSourceReaderRouteResolver.Result.Resolved(
            readerRoute(AlternateSourceReaderRouteRole.ALTERNATE),
        )
        var switchCount = 0
        val coordinator = coordinator(store) {
            switchCount++
            true
        }

        assertEquals(
            AlternateSourceReaderCommandResult.RequiresPairConfirmation,
            coordinator.select(entryRequest(), "/chapter/alternate"),
        )
        assertEquals(
            AlternateSourceReaderCommandResult.PairConfirmed,
            coordinator.confirmPair(readerBridgeKey()),
        )
        assertEquals(
            AlternateSourceReaderCommandResult.RequiresProvisionalConfirmation(READER_TARGET_ID),
            coordinator.select(entryRequest(), "/chapter/alternate"),
        )
        assertEquals(0, switchCount)
        assertEquals(
            AlternateSourceReaderCommandResult.Entered(READER_TARGET_ID),
            coordinator.enter(entryRequest(), confirmProvisional = true),
        )
        assertEquals(1, switchCount)
        coVerify(exactly = 1) { identityController.mutate(any(), CrossSourceIdentityMutation.CONFIRM) }
        coVerify(exactly = 1) { bridgeController.apply(any(), any()) }
    }

    @Test
    fun `selection reuses an exact confirmed mapping without mutating it`() = runTest {
        val store = sessionStore()
        coEvery { identityResolver.isConfirmed(any(), any(), any(), any()) } returns true
        coEvery { getBridge.await(readerBridgeKey()) } returns bridgeState()
        coEvery {
            routeResolver.resolve(
                readerBridgeKey(),
                AlternateSourceReaderRouteRole.ALTERNATE,
                "/chapter/alternate",
                0,
            )
        } returns AlternateSourceReaderRouteResolver.Result.Resolved(
            readerRoute(AlternateSourceReaderRouteRole.ALTERNATE),
        )
        var switchCount = 0
        val coordinator = coordinator(store) {
            switchCount++
            true
        }

        assertEquals(
            AlternateSourceReaderCommandResult.Entered(READER_TARGET_ID),
            coordinator.select(entryRequest(), "/chapter/alternate"),
        )
        assertEquals(1, switchCount)
        coVerify(exactly = 0) { bridgeController.apply(any(), any()) }
    }

    @Test
    fun `stale restored session is cleared without guessing a return route`() = runTest {
        val values = linkedMapOf<String, Any?>()
        val store = sessionStore(values)
        store.write(readerSession())
        coEvery { identityResolver.isConfirmed(any(), any(), any(), any()) } returns true
        coEvery { getBridge.await(readerBridgeKey()) } returns bridgeState(bridgeUpdatedAt = 2_001L)
        val coordinator = coordinator(store) { error("must not switch") }

        assertEquals(
            AlternateSourceReaderCommandResult.Stale,
            coordinator.restore(readerRoute(AlternateSourceReaderRouteRole.ALTERNATE)),
        )
        assertTrue(store.read() is AlternateSourceReaderStateCodec.DecodeResult.Invalid)
        assertEquals(AlternateSourceReaderPhase.DEGRADED, coordinator.state.value.phase)
        coVerify(exactly = 0) { routeResolver.resolve(any(), any(), any(), any()) }
    }

    @Test
    fun `partial restored state is cleared while an absent session remains a no-op`() = runTest {
        val values = linkedMapOf<String, Any?>(AlternateSourceReaderStateCodec.KEY_SCHEMA_VERSION to 2)
        val store = sessionStore(values)
        val coordinator = coordinator(store) { error("must not switch") }

        assertEquals(
            AlternateSourceReaderCommandResult.Invalid,
            coordinator.restore(readerRoute(AlternateSourceReaderRouteRole.ALTERNATE)),
        )
        assertFalse(store.hasStoredState())
        assertEquals(AlternateSourceReaderPhase.DEGRADED, coordinator.state.value.phase)

        val empty = coordinator(sessionStore()) { error("must not switch") }
        assertEquals(
            AlternateSourceReaderCommandResult.NoSession,
            empty.restore(readerRoute(AlternateSourceReaderRouteRole.ALTERNATE)),
        )
        assertEquals(AlternateSourceReaderPhase.PRIMARY, empty.state.value.phase)
    }

    @Test
    fun `failed entry switch preserves the exact readable primary route and clears pending evidence`() = runTest {
        val store = sessionStore()
        coEvery { identityResolver.isConfirmed(any(), any(), any(), any()) } returns true
        coEvery { getBridge.await(readerBridgeKey()) } returns bridgeState()
        coEvery { routeResolver.resolve(any(), any(), any(), any()) } returns
            AlternateSourceReaderRouteResolver.Result.Resolved(
                readerRoute(AlternateSourceReaderRouteRole.ALTERNATE),
            )
        val coordinator = coordinator(store) { false }

        assertEquals(
            AlternateSourceReaderCommandResult.Failed,
            coordinator.enter(entryRequest()),
        )
        val retained = (store.read() as AlternateSourceReaderStateCodec.DecodeResult.Valid).session
        assertEquals(AlternateSourceReaderRouteRole.PRIMARY, retained.currentRoute.role)
        assertEquals("/chapter/primary", retained.currentRoute.chapterUrl)
        assertEquals(null, retained.pendingRouteFingerprint)
        assertEquals(1, retained.failedResolutionCount)
        assertEquals(AlternateSourceReaderPhase.DEGRADED, coordinator.state.value.phase)
    }

    @Test
    fun `entry cancellation clears pending state restores primary phase and propagates`() {
        val store = sessionStore()
        coEvery { identityResolver.isConfirmed(any(), any(), any(), any()) } returns true
        coEvery { getBridge.await(readerBridgeKey()) } returns bridgeState()
        coEvery { routeResolver.resolve(any(), any(), any(), any()) } returns
            AlternateSourceReaderRouteResolver.Result.Resolved(
                readerRoute(AlternateSourceReaderRouteRole.ALTERNATE),
            )
        val coordinator = coordinator(store) { throw CancellationException("stop") }

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { coordinator.enter(entryRequest()) }
        }
        assertFalse(store.hasStoredState())
        assertEquals(AlternateSourceReaderPhase.PRIMARY, coordinator.state.value.phase)
        assertEquals(null, coordinator.state.value.session)
    }

    @Test
    fun `dismissal during route replacement cannot resurrect persisted session state`() = runTest {
        val store = sessionStore()
        coEvery { identityResolver.isConfirmed(any(), any(), any(), any()) } returns true
        coEvery { getBridge.await(readerBridgeKey()) } returns bridgeState()
        coEvery { routeResolver.resolve(any(), any(), any(), any()) } returns
            AlternateSourceReaderRouteResolver.Result.Resolved(
                readerRoute(AlternateSourceReaderRouteRole.ALTERNATE),
            )
        lateinit var coordinator: AlternateSourceReaderCoordinator
        coordinator = coordinator(store) {
            coordinator.dismiss()
            true
        }

        assertEquals(
            AlternateSourceReaderCommandResult.NoSession,
            coordinator.enter(entryRequest()),
        )
        assertFalse(store.hasStoredState())
        assertEquals(AlternateSourceReaderPhase.ENDED, coordinator.state.value.phase)
        assertEquals(null, coordinator.state.value.session)
    }

    @Test
    fun `route resolution retries one transient failure and no more`() = runTest {
        val store = sessionStore()
        coEvery { identityResolver.isConfirmed(any(), any(), any(), any()) } returns true
        coEvery { getBridge.await(readerBridgeKey()) } returns bridgeState()
        coEvery { routeResolver.resolve(any(), any(), any(), any()) } returnsMany listOf(
            AlternateSourceReaderRouteResolver.Result.Failed,
            AlternateSourceReaderRouteResolver.Result.Resolved(
                readerRoute(AlternateSourceReaderRouteRole.ALTERNATE),
            ),
        )
        val coordinator = coordinator(store) { true }

        assertEquals(
            AlternateSourceReaderCommandResult.Entered(READER_TARGET_ID),
            coordinator.enter(entryRequest()),
        )
        coVerify(exactly = 2) { routeResolver.resolve(any(), any(), any(), any()) }
    }

    @Test
    fun `pair confirmation conflict and ordinary failure create no bridge state`() = runTest {
        val store = sessionStore()
        coEvery { identityResolver.isConfirmed(any(), any(), any(), any()) } returns false
        coEvery { identityController.mutate(any(), CrossSourceIdentityMutation.CONFIRM) } returnsMany listOf(
            CrossSourceIdentityMutationResult.CONFLICT,
            CrossSourceIdentityMutationResult.FAILED,
        )
        val coordinator = coordinator(store) { error("must not switch") }

        assertEquals(AlternateSourceReaderCommandResult.Conflict, coordinator.confirmPair(readerBridgeKey()))
        assertEquals(AlternateSourceReaderCommandResult.Failed, coordinator.confirmPair(readerBridgeKey()))
        assertFalse(store.hasStoredState())
        coVerify(exactly = 0) { getBridge.await(any()) }
        coVerify(exactly = 0) { bridgeController.apply(any(), any()) }
    }

    @Test
    fun `correction conflict records no session mutation and preserves alternate reading`() = runTest {
        val store = sessionStore().also { it.write(readerSession()) }
        val state = bridgeState()
        coEvery { identityResolver.isConfirmed(any(), any(), any(), any()) } returns true
        coEvery { getBridge.await(readerBridgeKey()) } returns state
        coEvery { bridgeController.apply(any(), any()) } returns AlternateSourceBridgeMutationResult.CONFLICT
        val coordinator = coordinator(store) { error("must not switch") }
        assertEquals(
            AlternateSourceReaderCommandResult.Restored,
            coordinator.restore(readerRoute(AlternateSourceReaderRouteRole.ALTERNATE)),
        )

        assertEquals(
            AlternateSourceReaderCommandResult.Conflict,
            coordinator.correct(
                readerRoute(
                    AlternateSourceReaderRouteRole.ALTERNATE,
                    chapterUrl = "/chapter/alternate-corrected",
                    chapterId = 23L,
                ),
            ),
        )
        val preserved = (store.read() as AlternateSourceReaderStateCodec.DecodeResult.Valid).session
        assertEquals(READER_TARGET_ID, preserved.activeTargetId)
        assertEquals(AlternateSourceReaderPhase.ALTERNATE, coordinator.state.value.phase)
    }

    @Test
    fun `automatic return fires only at exact boundary and clears session after route commit`() = runTest {
        val store = sessionStore().also { it.write(readerSession()) }
        val state = bridgeState(automaticReturn = true, continuationUrl = "/chapter/primary-next")
        coEvery { identityResolver.isConfirmed(any(), any(), any(), any()) } returns true
        coEvery { getBridge.await(readerBridgeKey()) } returns state
        val destination = readerRoute(
            AlternateSourceReaderRouteRole.PRIMARY,
            chapterUrl = "/chapter/primary-next",
            chapterId = 12L,
        )
        coEvery {
            routeResolver.resolve(
                readerBridgeKey(),
                AlternateSourceReaderRouteRole.PRIMARY,
                "/chapter/primary-next",
                0,
            )
        } returns AlternateSourceReaderRouteResolver.Result.Resolved(destination)
        val switched = mutableListOf<AlternateSourceReaderRoute>()
        val coordinator = coordinator(store) {
            switched += it
            true
        }
        coordinator.restore(readerRoute(AlternateSourceReaderRouteRole.ALTERNATE))

        assertEquals(
            AlternateSourceReaderCommandResult.NotAtBoundary,
            coordinator.automaticReturn("/chapter/nearby"),
        )
        assertEquals(
            AlternateSourceReaderCommandResult.Returned,
            coordinator.automaticReturn("/chapter/alternate"),
        )
        assertEquals(listOf(destination), switched)
        assertTrue(store.read() is AlternateSourceReaderStateCodec.DecodeResult.Invalid)
        assertEquals(AlternateSourceReaderPhase.ENDED, coordinator.state.value.phase)
    }

    @Test
    fun `automatic return stays silent for provisional mapping instead of showing route unavailable`() = runTest {
        val store = sessionStore().also { it.write(readerSession()) }
        coEvery { getBridge.await(readerBridgeKey()) } returns bridgeState(
            automaticReturn = true,
            mappingState = AlternateSourceBridgeMappingState.PROVISIONAL,
        )
        coEvery { identityResolver.isConfirmed(any(), any(), any(), any()) } returns true
        val coordinator = coordinator(store) { error("must not switch") }
        coordinator.restore(readerRoute(AlternateSourceReaderRouteRole.ALTERNATE))

        assertEquals(
            AlternateSourceReaderCommandResult.NotAtBoundary,
            coordinator.automaticReturn("/chapter/alternate"),
        )
        assertEquals(AlternateSourceReaderPhase.ALTERNATE, coordinator.state.value.phase)
        assertEquals(
            AlternateSourceReaderRouteRole.ALTERNATE,
            (store.read() as AlternateSourceReaderStateCodec.DecodeResult.Valid).session.currentRoute.role,
        )
    }

    @Test
    fun `ordinary automatic return failure degrades and consumes the bounded retry`() = runTest {
        val store = sessionStore().also { it.write(readerSession()) }
        val state = bridgeState(automaticReturn = true)
        var bridgeReads = 0
        coEvery { identityResolver.isConfirmed(any(), any(), any(), any()) } returns true
        coEvery { getBridge.await(readerBridgeKey()) } answers {
            if (bridgeReads++ == 0) state else throw IllegalStateException("unavailable")
        }
        val coordinator = coordinator(store) { error("must not switch") }
        assertEquals(
            AlternateSourceReaderCommandResult.Restored,
            coordinator.restore(readerRoute(AlternateSourceReaderRouteRole.ALTERNATE)),
        )

        assertEquals(
            AlternateSourceReaderCommandResult.Failed,
            coordinator.automaticReturn("/chapter/alternate"),
        )
        val retained = (store.read() as AlternateSourceReaderStateCodec.DecodeResult.Valid).session
        assertEquals(1, retained.failedResolutionCount)
        assertEquals(AlternateSourceReaderPhase.DEGRADED, coordinator.state.value.phase)
        assertEquals(AlternateSourceReaderRouteRole.ALTERNATE, retained.currentRoute.role)
    }

    @Test
    fun `manual return falls back to exact stored primary route when continuation is unavailable`() = runTest {
        val store = sessionStore().also { it.write(readerSession()) }
        val state = bridgeState(continuationUrl = "/chapter/unavailable")
        coEvery { identityResolver.isConfirmed(any(), any(), any(), any()) } returns true
        coEvery { getBridge.await(readerBridgeKey()) } returns state
        coEvery {
            routeResolver.resolve(
                readerBridgeKey(),
                AlternateSourceReaderRouteRole.PRIMARY,
                "/chapter/unavailable",
                0,
            )
        } returns AlternateSourceReaderRouteResolver.Result.ChapterUnavailable
        coEvery {
            routeResolver.resolve(
                readerBridgeKey(),
                AlternateSourceReaderRouteRole.PRIMARY,
                "/chapter/primary",
                0,
            )
        } returns AlternateSourceReaderRouteResolver.Result.Resolved(
            readerRoute(AlternateSourceReaderRouteRole.PRIMARY),
        )
        val coordinator = coordinator(store) { true }
        coordinator.restore(readerRoute(AlternateSourceReaderRouteRole.ALTERNATE))

        assertEquals(
            AlternateSourceReaderCommandResult.Returned,
            coordinator.manualReturn(preferContinuation = true),
        )
        assertTrue(store.read() is AlternateSourceReaderStateCodec.DecodeResult.Invalid)
    }

    @Test
    fun `cancellation and fatal identity failures propagate`() {
        val store = sessionStore()
        coEvery { identityResolver.isConfirmed(any(), any(), any(), any()) } throws CancellationException("stop")
        val cancelled = coordinator(store) { true }
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { cancelled.confirmPair(readerBridgeKey()) }
        }

        coEvery { identityResolver.isConfirmed(any(), any(), any(), any()) } throws AssertionError("fatal")
        val fatal = coordinator(store) { true }
        assertThrows(AssertionError::class.java) {
            kotlinx.coroutines.runBlocking { fatal.confirmPair(readerBridgeKey()) }
        }
    }

    private fun coordinator(
        store: AlternateSourceReaderSessionStore,
        switcher: suspend (AlternateSourceReaderRoute) -> Boolean,
    ) = AlternateSourceReaderCoordinator(
        getBridge = getBridge,
        routeResolver = routeResolver,
        bridgeController = bridgeController,
        identityResolver = identityResolver,
        identityController = identityController,
        sessionStore = store,
        routeSwitcher = AlternateSourceReaderRouteSwitcher(switcher),
        clock = { 3_000L },
        targetId = { READER_TARGET_ID },
    )

    private fun entryRequest() = AlternateSourceReaderEntryRequest(
        bridgeKey = readerBridgeKey(),
        primaryRoute = readerRoute(AlternateSourceReaderRouteRole.PRIMARY),
        precedingPrimaryChapterUrl = "/chapter/primary",
        followingPrimaryChapterUrl = "/chapter/after",
    )

    private fun sessionStore(
        values: MutableMap<String, Any?> = linkedMapOf(),
    ) = AlternateSourceReaderSessionStore(
        get = values::get,
        set = values::set,
        remove = { values.remove(it) },
    )

    private fun bridgeState(
        bridgeUpdatedAt: Long = 2_000L,
        mappingUpdatedAt: Long = 2_100L,
        mappingState: AlternateSourceBridgeMappingState = AlternateSourceBridgeMappingState.CONFIRMED,
        automaticReturn: Boolean = false,
        continuationUrl: String = "/chapter/after",
    ) = AlternateSourceBridgeState(
        bridge = AlternateSourceBridge(
            key = readerBridgeKey(),
            version = AlternateSourceBridgePolicy.CURRENT_VERSION,
            continuationPrimaryChapterUrl = continuationUrl,
            returnAfterAlternateChapterUrl = "/chapter/alternate",
            automaticReturn = automaticReturn,
            reviewState = AlternateSourceBridgeReviewState.CURRENT,
            createdAt = 1_000L,
            updatedAt = bridgeUpdatedAt,
        ),
        mappings = listOf(
            AlternateSourceBridgeMapping(
                key = AlternateSourceBridgeMappingKey(readerBridgeKey(), READER_TARGET_ID),
                alternateChapterUrl = "/chapter/alternate",
                precedingPrimaryChapterUrl = "/chapter/primary",
                followingPrimaryChapterUrl = "/chapter/after",
                relation = AlternateSourceBridgeMappingRelation.PRIMARY_MISSING,
                state = mappingState,
                version = AlternateSourceBridgePolicy.CURRENT_VERSION,
                createdAt = 1_000L,
                updatedAt = mappingUpdatedAt,
            ),
        ),
    )
}

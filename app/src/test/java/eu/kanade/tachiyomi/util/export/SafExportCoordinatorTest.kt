package eu.kanade.tachiyomi.util.export

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.util.system.toast
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// KMK -->
// Real boundary tests for the reworked,
// operation-keyed, atomic SafExportCoordinator contract (beginOperation/registerUri/performWrite/
// clear, all keyed by an opaque operationId; IN_PROGRESS as a real non-terminal state).
class SafExportCoordinatorTest {

    @AfterEach
    fun tearDown() {
        unmockkStatic(DocumentsContract::class)
        unmockkStatic("eu.kanade.tachiyomi.util.system.ToastExtensionsKt")
    }

    private fun uri(): Uri = mockk<Uri>()

    /** Stubs the `Context.toast(StringResource, ...)` extension so it never touches the real Android
     * Toast framework (unstubbed in these pure JVM tests) -- the toast call itself is not this
     * class's concern, only that the right message is chosen for each outcome. */
    private fun mockToastExtension(context: Context) {
        mockkStatic("eu.kanade.tachiyomi.util.system.ToastExtensionsKt")
        every { context.toast(any<StringResource>(), any(), any()) } returns mockk(relaxed = true)
    }

    @Test
    fun `beginOperation returns a fresh id when nothing is pending`() {
        val coordinator = SafExportCoordinator()
        val id = coordinator.beginOperation()
        assertNotNull(id)
        assertNull(coordinator.cleanupOffer.value, "beginOperation alone must not create a visible offer")
    }

    @Test
    fun `registerUri makes the offer visible as IN_PROGRESS, not a terminal state`() {
        val coordinator = SafExportCoordinator()
        val id = coordinator.beginOperation()!!
        val u = uri()

        val registered = coordinator.registerUri(id, u)

        assertTrue(registered, "registration must succeed for the operationId that reserved it")
        val offer = coordinator.cleanupOffer.value
        assertNotNull(offer, "URI registration must be visible immediately, before any write is attempted")
        assertSame(u, offer!!.uri)
        assertSame(id, offer.operationId)
        assertSame(
            SafArtifactOutcome.IN_PROGRESS,
            offer.outcome,
            "a freshly-registered URI with no write yet must be IN_PROGRESS -- never a terminal outcome, " +
                "so a cleanup dialog can never offer Remove for a document that may still be mid-write",
        )
        assertFalse(TERMINAL_SAF_OUTCOMES.contains(SafArtifactOutcome.IN_PROGRESS))
    }

    @Test
    fun `registerUri without a prior beginOperation is rejected and reports false`() {
        val coordinator = SafExportCoordinator()
        val registered = coordinator.registerUri("not-a-real-operation-id", uri())

        assertFalse(registered, "registerUri must refuse an operationId that was never reserved")
        assertNull(coordinator.cleanupOffer.value)
    }

    @Test
    fun `cancelReservation releases a reservation without creating an offer`() {
        val coordinator = SafExportCoordinator()
        val id = coordinator.beginOperation()!!

        coordinator.cancelReservation(id)

        // The reservation slot is now free again -- a fresh beginOperation must succeed.
        val nextId = coordinator.beginOperation()
        assertNotNull(nextId, "cancelReservation must free the reservation slot for a later attempt")
        assertNull(coordinator.cleanupOffer.value)
    }

    @Test
    fun `two simultaneous beginOperation calls -- the second is rejected while the first is pending`() {
        // In practice a
        // second beginOperation call cannot happen through the UI while an offer is pending (see this
        // class's KDoc for why: SafArtifactCleanupDialog is modal and blocks the trigger control).
        // This test proves the coordinator itself also refuses a second concurrent reservation if
        // that modality assumption is ever bypassed -- defense-in-depth, not the primary safeguard.
        val coordinator = SafExportCoordinator()
        val firstId = coordinator.beginOperation()

        val secondId = coordinator.beginOperation()

        assertNotNull(firstId)
        assertNull(secondId, "a second beginOperation call must be rejected while the first reservation is outstanding")
    }

    @Test
    fun `a rejected registration never performs a write`() = runTest {
        val coordinator = SafExportCoordinator()
        val id = coordinator.beginOperation()!!
        val u = uri()
        assertTrue(coordinator.registerUri(id, u))

        var wroteAnything = false
        // Simulate a caller that (incorrectly) tries to register a second, unrelated uri for a
        // stale/foreign operationId -- registerUri must reject it, and the caller must never invoke
        // performWrite for a registration that failed.
        val rejected = coordinator.registerUri("some-other-id", uri())
        assertFalse(rejected)
        if (rejected) {
            coordinator.performWrite("some-other-id") {
                wroteAnything = true
                SafArtifactOutcome.SUCCESS
            }
        }

        assertFalse(wroteAnything, "a caller must never write for an operationId whose registration was rejected")
        // The original, legitimately-registered offer must be completely unaffected.
        assertSame(u, coordinator.cleanupOffer.value?.uri)
        assertSame(SafArtifactOutcome.IN_PROGRESS, coordinator.cleanupOffer.value?.outcome)
    }

    @Test
    fun `performWrite updates the offer to SUCCESS only after the write lambda reports it`() = runTest {
        val coordinator = SafExportCoordinator()
        val id = coordinator.beginOperation()!!
        coordinator.registerUri(id, uri())

        val outcome = coordinator.performWrite(id) { SafArtifactOutcome.SUCCESS }

        assertSame(SafArtifactOutcome.SUCCESS, outcome)
        assertSame(SafArtifactOutcome.SUCCESS, coordinator.cleanupOffer.value?.outcome)
        assertTrue(TERMINAL_SAF_OUTCOMES.contains(SafArtifactOutcome.SUCCESS))
    }

    @Test
    fun `performWrite updates the offer to PARTIAL_OR_EMPTY when the write lambda reports it`() = runTest {
        val coordinator = SafExportCoordinator()
        val id = coordinator.beginOperation()!!
        coordinator.registerUri(id, uri())

        val outcome = coordinator.performWrite(id) { SafArtifactOutcome.PARTIAL_OR_EMPTY }

        assertSame(SafArtifactOutcome.PARTIAL_OR_EMPTY, outcome)
        assertSame(SafArtifactOutcome.PARTIAL_OR_EMPTY, coordinator.cleanupOffer.value?.outcome)
    }

    @Test
    fun `performWrite updates the offer to FAILED when the write lambda reports failure`() = runTest {
        val coordinator = SafExportCoordinator()
        val id = coordinator.beginOperation()!!
        coordinator.registerUri(id, uri())

        val outcome = coordinator.performWrite(id) { SafArtifactOutcome.FAILED }

        assertSame(SafArtifactOutcome.FAILED, outcome)
        assertSame(SafArtifactOutcome.FAILED, coordinator.cleanupOffer.value?.outcome)
    }

    @Test
    fun `performWrite catches an ordinary exception thrown by the write lambda and reports FAILED`() = runTest {
        val coordinator = SafExportCoordinator()
        val id = coordinator.beginOperation()!!
        coordinator.registerUri(id, uri())

        val outcome = coordinator.performWrite(id) { throw java.io.IOException("disk full") }

        assertSame(SafArtifactOutcome.FAILED, outcome, "an ordinary write exception must be reported as a truthful FAILED outcome")
        assertSame(SafArtifactOutcome.FAILED, coordinator.cleanupOffer.value?.outcome)
    }

    @Test
    fun `cancellation during performWrite retains the offer as CANCELLED and rethrows`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val coordinator = SafExportCoordinator()
        val id = coordinator.beginOperation()!!
        coordinator.registerUri(id, uri())

        var caughtCancellation = false
        val job: Job = scope.launch {
            try {
                coordinator.performWrite(id) {
                    // Suspends forever until this job is cancelled -- proves the coordinator reacts
                    // to real coroutine cancellation, not just a lambda returning a value.
                    awaitCancellation()
                }
            } catch (e: CancellationException) {
                caughtCancellation = true
            }
        }
        scope.advanceUntilIdle()
        job.cancel()
        scope.advanceUntilIdle()

        assertTrue(caughtCancellation, "CancellationException must propagate out of performWrite, never be swallowed")
        assertSame(
            SafArtifactOutcome.CANCELLED,
            coordinator.cleanupOffer.value?.outcome,
            "the retained offer must reflect CANCELLED, not silently disappear or report a false outcome",
        )
        assertTrue(coordinator.cleanupOffer.value != null, "the Uri must remain retained after cancellation, not be discarded")
    }

    @Test
    fun `a stale performWrite callback for an operationId that was already cleared does not resurrect an offer`() = runTest {
        val coordinator = SafExportCoordinator()
        val id = coordinator.beginOperation()!!
        coordinator.registerUri(id, uri())
        coordinator.clear(id)
        assertNull(coordinator.cleanupOffer.value)

        // Simulates a stale/late write callback completing after the user already dismissed/cleared
        // the offer -- must not resurrect a cleared offer.
        coordinator.performWrite(id) { SafArtifactOutcome.SUCCESS }

        assertNull(coordinator.cleanupOffer.value, "a stale callback for an already-cleared operationId must not resurrect an offer")
    }

    @Test
    fun `a stale performWrite callback cannot corrupt a second, unrelated operation`() = runTest {
        val coordinator = SafExportCoordinator()
        val firstId = coordinator.beginOperation()!!
        coordinator.registerUri(firstId, uri())
        coordinator.clear(firstId)

        val secondId = coordinator.beginOperation()!!
        val secondUri = uri()
        coordinator.registerUri(secondId, secondUri)

        // The stale first-operation callback arrives after a second, unrelated operation has already
        // started -- it must not be able to touch the second operation's retained offer.
        coordinator.performWrite(firstId) { SafArtifactOutcome.SUCCESS }

        assertSame(secondUri, coordinator.cleanupOffer.value?.uri, "the second operation's offer must be untouched by the first operation's stale callback")
        assertSame(SafArtifactOutcome.IN_PROGRESS, coordinator.cleanupOffer.value?.outcome)
    }

    @Test
    fun `clear only removes the offer for the matching operationId`() = runTest {
        val coordinator = SafExportCoordinator()
        val id = coordinator.beginOperation()!!
        coordinator.registerUri(id, uri())
        coordinator.performWrite(id) { SafArtifactOutcome.SUCCESS }
        assertTrue(coordinator.cleanupOffer.value != null)

        // A clear() call for an unrelated (or stale/foreign) operationId must be a no-op.
        coordinator.clear("some-unrelated-operation-id")
        assertTrue(coordinator.cleanupOffer.value != null, "clear() for a non-matching operationId must not remove the real offer")

        coordinator.clear(id)
        assertNull(coordinator.cleanupOffer.value, "clear() for the matching operationId must remove the offer")
    }

    @Test
    fun `concurrent beginOperation calls under contention -- exactly one wins`() {
        // BeginOperation is guarded
        // by a `synchronized` critical section, not a `MutableStateFlow.update{}` lambda whose body
        // can run more than once under contention with a local-variable side effect disagreeing with
        // the committed state. Drive real concurrent threads at it and assert the invariant holds.
        val coordinator = SafExportCoordinator()
        val threadCount = 16
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val results = java.util.Collections.synchronizedList(mutableListOf<String?>())
        try {
            val futures = (1..threadCount).map {
                executor.submit {
                    startLatch.await()
                    results.add(coordinator.beginOperation())
                }
            }
            startLatch.countDown()
            futures.forEach { it.get(5, TimeUnit.SECONDS) }
        } finally {
            executor.shutdown()
        }

        val winners = results.filterNotNull()
        assertEquals(1, winners.size, "exactly one concurrent beginOperation call must win the reservation")
        assertEquals(threadCount - 1, results.count { it == null }, "every losing caller must be reported as rejected (null), never silently succeed")
    }

    @Test
    fun `concurrent registerUri calls for the same reservation under contention -- exactly one wins and no Uri is overwritten`() {
        val coordinator = SafExportCoordinator()
        val id = coordinator.beginOperation()!!
        val threadCount = 16
        val uris = (1..threadCount).map { uri() }
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val results = java.util.Collections.synchronizedList(mutableListOf<Boolean>())
        try {
            val futures = uris.map { candidateUri ->
                executor.submit {
                    startLatch.await()
                    results.add(coordinator.registerUri(id, candidateUri))
                }
            }
            startLatch.countDown()
            futures.forEach { it.get(5, TimeUnit.SECONDS) }
        } finally {
            executor.shutdown()
        }

        assertEquals(1, results.count { it }, "exactly one concurrent registerUri call for the same reservation must win")
        val finalUri = coordinator.cleanupOffer.value?.uri
        assertNotNull(finalUri, "the winning registration must leave a real, non-null retained Uri")
        assertTrue(uris.any { it === finalUri }, "the retained Uri must be exactly one of the candidates, never null/corrupted")
    }

    // --- deleteSafDocument: shared exact-Uri-only deletion boundary ---

    @Test
    fun `deleteSafDocument returns true when DocumentsContract deletion succeeds`() {
        mockkStatic(DocumentsContract::class)
        val context = mockk<Context>()
        val resolver = mockk<ContentResolver>()
        val u = uri()
        every { context.contentResolver } returns resolver
        every { DocumentsContract.deleteDocument(resolver, u) } returns true

        assertTrue(deleteSafDocument(context, u))
    }

    @Test
    fun `deleteSafDocument returns false, not a crash, when deletion fails`() {
        mockkStatic(DocumentsContract::class)
        val context = mockk<Context>()
        val resolver = mockk<ContentResolver>()
        val u = uri()
        every { context.contentResolver } returns resolver
        every { DocumentsContract.deleteDocument(resolver, u) } throws SecurityException("permission revoked")

        assertFalse(deleteSafDocument(context, u))
    }

    @Test
    fun `deleteSafDocument rethrows CancellationException instead of reporting failure`() {
        mockkStatic(DocumentsContract::class)
        val context = mockk<Context>()
        val resolver = mockk<ContentResolver>()
        val u = uri()
        every { context.contentResolver } returns resolver
        every { DocumentsContract.deleteDocument(resolver, u) } throws CancellationException("cancelled")

        var thrown: CancellationException? = null
        try {
            deleteSafDocument(context, u)
        } catch (e: CancellationException) {
            thrown = e
        }

        assertTrue(thrown != null)
    }

    @Test
    fun `SafArtifactOutcome has exactly the six required states, with exactly five terminal`() {
        // Structural guard against a future silent addition/removal; also documents the exhaustive
        // set every adapter's `when` must handle, and the terminal/non-terminal split the dialog
        // gate and every recovery-reconciliation path depends on.
        assertEquals(
            setOf("IN_PROGRESS", "SUCCESS", "PARTIAL_OR_EMPTY", "FAILED", "CANCELLED", "UNRESOLVED"),
            SafArtifactOutcome.entries.map { it.name }.toSet(),
        )
        assertEquals(
            setOf(
                SafArtifactOutcome.SUCCESS,
                SafArtifactOutcome.PARTIAL_OR_EMPTY,
                SafArtifactOutcome.FAILED,
                SafArtifactOutcome.CANCELLED,
                SafArtifactOutcome.UNRESOLVED,
            ),
            TERMINAL_SAF_OUTCOMES,
        )
        assertFalse(TERMINAL_SAF_OUTCOMES.contains(SafArtifactOutcome.IN_PROGRESS))
    }

    // --- required test 1: UNRESOLVED exists and is not terminal-removable ---

    @Test
    fun `UNRESOLVED is terminal but never removable -- only PARTIAL_OR_EMPTY, FAILED, and CANCELLED are`() {
        assertTrue(TERMINAL_SAF_OUTCOMES.contains(SafArtifactOutcome.UNRESOLVED), "UNRESOLVED is a concluded classification, not an in-progress one")
        assertFalse(REMOVABLE_SAF_OUTCOMES.contains(SafArtifactOutcome.UNRESOLVED), "an outcome that could not be verified must never be offered for deletion")
        assertFalse(REMOVABLE_SAF_OUTCOMES.contains(SafArtifactOutcome.SUCCESS), "a verified-successful artifact must never be offered for deletion")
        assertFalse(REMOVABLE_SAF_OUTCOMES.contains(SafArtifactOutcome.IN_PROGRESS))
        assertEquals(
            setOf(SafArtifactOutcome.PARTIAL_OR_EMPTY, SafArtifactOutcome.FAILED, SafArtifactOutcome.CANCELLED),
            REMOVABLE_SAF_OUTCOMES,
        )
    }

    @Test
    fun `successful cleanup opt-in is retained only on the coordinator offer`() {
        val coordinator = SafExportCoordinator(allowSuccessfulRemoval = true)
        val operationId = coordinator.beginOperation()!!
        val uri = mockk<Uri>()
        assertTrue(coordinator.registerUri(operationId, uri))

        runBlocking {
            coordinator.performWrite(operationId) { SafArtifactOutcome.SUCCESS }
        }

        assertTrue(coordinator.cleanupOffer.value?.allowSuccessfulRemoval == true)
        assertEquals(SafArtifactOutcome.SUCCESS, coordinator.cleanupOffer.value?.outcome)
    }

    // --- adoptUnregisterableUri ---

    @Test
    fun `adoptUnregisterableUri creates a terminal PARTIAL_OR_EMPTY offer when the coordinator is idle`() {
        val coordinator = SafExportCoordinator()
        val u = uri()

        val adopted = coordinator.adoptUnregisterableUri(u)

        assertTrue(adopted)
        assertSame(u, coordinator.cleanupOffer.value?.uri)
        assertSame(SafArtifactOutcome.PARTIAL_OR_EMPTY, coordinator.cleanupOffer.value?.outcome)
    }

    @Test
    fun `adoptUnregisterableUri refuses to overwrite an already-retained offer`() {
        val coordinator = SafExportCoordinator()
        val id = coordinator.beginOperation()!!
        val registeredUri = uri()
        coordinator.registerUri(id, registeredUri)

        val adopted = coordinator.adoptUnregisterableUri(uri())

        assertFalse(adopted)
        assertSame(registeredUri, coordinator.cleanupOffer.value?.uri, "the real retained offer must survive a refused adoption attempt")
    }

    @Test
    fun `adoptUnregisterableUri refuses to adopt while a reservation is outstanding`() {
        val coordinator = SafExportCoordinator()
        coordinator.beginOperation()!! // reservation outstanding, never registered

        val adopted = coordinator.adoptUnregisterableUri(uri())

        assertFalse(adopted, "adopting while a reservation is outstanding could be clobbered by that reservation's own late registerUri call")
        assertNull(coordinator.cleanupOffer.value)
    }

    // --- handleUnregisterableUri: typed, truthful fallback outcome ---

    private fun context(): Context = mockk<Context>()

    private val deletedMessage: StringResource = mockk()
    private val retainedMessage: StringResource = mockk()
    private val unrecoverableMessage: StringResource = mockk()

    @Test
    fun `handleUnregisterableUri reports DELETED and shows only the deleted message when deletion succeeds`() {
        mockkStatic(DocumentsContract::class)
        val context = context()
        val resolver = mockk<ContentResolver>()
        val u = uri()
        every { context.contentResolver } returns resolver
        every { DocumentsContract.deleteDocument(resolver, u) } returns true
        mockToastExtension(context)
        val coordinator = SafExportCoordinator()

        val outcome = handleUnregisterableUri(context, u, coordinator, deletedMessage, retainedMessage, unrecoverableMessage)

        assertSame(UnregisterableUriOutcome.DELETED, outcome)
        assertNull(coordinator.cleanupOffer.value, "a successfully deleted document must not also be adopted into the coordinator's offer")
        verify(exactly = 1) { context.toast(deletedMessage, any(), any()) }
        verify(exactly = 0) { context.toast(retainedMessage, any(), any()) }
        verify(exactly = 0) { context.toast(unrecoverableMessage, any(), any()) }
    }

    @Test
    fun `handleUnregisterableUri retains the Uri for cleanup and shows the retained message when deletion fails`() {
        mockkStatic(DocumentsContract::class)
        val context = context()
        val resolver = mockk<ContentResolver>()
        val u = uri()
        every { context.contentResolver } returns resolver
        every { DocumentsContract.deleteDocument(resolver, u) } returns false
        mockToastExtension(context)
        val coordinator = SafExportCoordinator()

        val outcome = handleUnregisterableUri(context, u, coordinator, deletedMessage, retainedMessage, unrecoverableMessage)

        assertSame(UnregisterableUriOutcome.RETAINED_FOR_CLEANUP, outcome)
        assertSame(u, coordinator.cleanupOffer.value?.uri, "a failed deletion must never lose the Uri -- it must be retained for a retry")
        assertSame(SafArtifactOutcome.PARTIAL_OR_EMPTY, coordinator.cleanupOffer.value?.outcome)
        verify(exactly = 1) { context.toast(retainedMessage, any(), any()) }
        verify(exactly = 0) { context.toast(deletedMessage, any(), any()) }
    }

    @Test
    fun `handleUnregisterableUri reports UNRECOVERABLE truthfully when deletion fails and adoption is also refused`() {
        mockkStatic(DocumentsContract::class)
        val context = context()
        val resolver = mockk<ContentResolver>()
        val u = uri()
        every { context.contentResolver } returns resolver
        every { DocumentsContract.deleteDocument(resolver, u) } returns false
        mockToastExtension(context)
        val coordinator = SafExportCoordinator()
        val id = coordinator.beginOperation()!!
        val alreadyRetainedUri = uri()
        coordinator.registerUri(id, alreadyRetainedUri)

        val outcome = handleUnregisterableUri(context, u, coordinator, deletedMessage, retainedMessage, unrecoverableMessage)

        assertSame(UnregisterableUriOutcome.UNRECOVERABLE, outcome)
        assertSame(alreadyRetainedUri, coordinator.cleanupOffer.value?.uri, "the pre-existing offer must not be clobbered")
        verify(exactly = 1) { context.toast(unrecoverableMessage, any(), any()) }
        verify(exactly = 0) { context.toast(deletedMessage, any(), any()) }
        verify(exactly = 0) { context.toast(retainedMessage, any(), any()) }
    }

    @Test
    fun `handleUnregisterableUri propagates CancellationException from deleteSafDocument instead of reporting an outcome`() {
        mockkStatic(DocumentsContract::class)
        val context = context()
        val resolver = mockk<ContentResolver>()
        val u = uri()
        every { context.contentResolver } returns resolver
        every { DocumentsContract.deleteDocument(resolver, u) } throws CancellationException("cancelled")
        mockToastExtension(context)
        val coordinator = SafExportCoordinator()

        var thrown: CancellationException? = null
        try {
            handleUnregisterableUri(context, u, coordinator, deletedMessage, retainedMessage, unrecoverableMessage)
        } catch (e: CancellationException) {
            thrown = e
        }

        assertTrue(thrown != null, "CancellationException must propagate out of handleUnregisterableUri untouched")
        assertNull(coordinator.cleanupOffer.value, "no offer should be created when the fallback itself was cancelled")
    }
}
// KMK <--

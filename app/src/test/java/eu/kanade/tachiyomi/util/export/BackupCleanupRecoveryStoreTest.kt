package eu.kanade.tachiyomi.util.export

import android.content.Context
import android.net.Uri
import androidx.work.WorkInfo
import eu.kanade.presentation.more.settings.screen.data.backupJobOutcomeFor
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import eu.kanade.tachiyomi.util.system.toast
import exh.util.FakePreferenceStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.util.UUID

// KMK -->
// KMK_CLAUDE_SAF_EXPORT_LIFECYCLE_CORRECTIONS_2026-08-05: real boundary tests for
// BackupCleanupRecoveryStore -- the application-scoped (not screen/model-scoped), now atomic and
// durably-persisted SAF cleanup store the backup-creation route uses. Mirrors
// SafExportCoordinatorTest's coverage (beginOperation/registerUri/performWrite/clear, IN_PROGRESS as
// non-terminal, atomic reservation under contention, stale-callback safety), plus the
// persistence/reconciliation behavior specific to this store (Finding 4): a durable record survives
// process recreation and is reconciled against real WorkManager state on [reconcileOnStartup].
//
// BackupCleanupRecoveryStore is a plain singleton object -- its in-memory state persists across test
// methods sharing a JVM/classloader, so every test resets it via resetForTesting() in tearDown().
// The durable record is backed by a fresh [FakePreferenceStore] per test (bound into Injekt), so
// "process recreation" is simulated by resetting only the in-memory state while leaving the
// FakePreferenceStore's backing map untouched -- exactly the persistence boundary a real process
// death crosses.
class BackupCleanupRecoveryStoreTest {

    companion object {
        private const val RECORD_PREFERENCE_KEY = "__APP_STATE_backup_cleanup_recovery_record"
    }

    private lateinit var fakePreferenceStore: FakePreferenceStore

    @BeforeEach
    fun setUp() {
        // KMK note: Injekt is a process-global singleton registry shared across every test method in
        // this class (and the whole test JVM). `addSingletonFactory` only takes effect the first time
        // `PreferenceStore` is ever resolved in this run -- a fresh `FakePreferenceStore()` created
        // here on the 2nd+ test would be registered but never actually used by
        // `BackupCleanupRecoveryStore` (which resolves via `Injekt.get<PreferenceStore>()` internally).
        // So: register once, then always read back *the instance Injekt is actually handing out* and
        // explicitly clear only the one key this store touches, rather than assuming a fresh instance.
        Injekt.importModule(
            object : InjektModule {
                override fun InjektRegistrar.registerInjectables() {
                    addSingletonFactory<PreferenceStore> { FakePreferenceStore() }
                }
            },
        )
        fakePreferenceStore = Injekt.get<PreferenceStore>() as FakePreferenceStore
        fakePreferenceStore.failWrites = false
        fakePreferenceStore.getString(RECORD_PREFERENCE_KEY, "").delete()
        BackupCleanupRecoveryStore.resetForTesting()
        // reconcileOnStartup() and loadRecord()'s Finding-3 structural validation both call
        // `.toUri()` (androidx.core.net's `Uri.parse(this)` wrapper) on the persisted URI string --
        // android.net.Uri.parse is an unstubbed Android-framework method in a pure JVM unit test, so
        // it must be mocked here for every test that exercises a seeded record. Rather than a single
        // fixed relaxed mock (which would fail Finding 3's shape validation for every test, including
        // ones that don't care about URI shape), this answers with a Uri double whose
        // scheme/authority/pathSegments are derived from actually parsing the input string --
        // realistic enough for both the happy-path tests and the dedicated invalid-URI tests below.
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers { parseUriForTest(firstArg()) }
    }

    @AfterEach
    fun tearDown() {
        BackupCleanupRecoveryStore.resetForTesting()
        unmockkObject(BackupCreateJob)
        unmockkStatic(Uri::class)
    }

    private fun uri(): Uri {
        val u = mockk<Uri>()
        every { u.toString() } returns "content://com.example.test/document/${UUID.randomUUID()}"
        return u
    }

    /**
     * A lightweight, deterministic stand-in for `android.net.Uri.parse` (unavailable in a pure JVM
     * unit test) that derives `scheme`/`authority`/`pathSegments` by actually parsing [raw] --
     * realistic enough for [BackupCleanupRecoveryStore]'s Finding-3 structural URI validation to
     * behave the same way it would against a real `content://.../document/...` string, a `file://`
     * string, or a non-document `content://` string, without hand-stubbing every individual case.
     */
    private fun parseUriForTest(raw: String): Uri {
        val u = mockk<Uri>(relaxed = true)
        val schemeSplit = raw.split("://", limit = 2)
        val scheme = if (schemeSplit.size == 2) schemeSplit[0] else null
        val rest = if (schemeSplit.size == 2) schemeSplit[1] else raw
        val slashIndex = rest.indexOf('/')
        val authority = if (slashIndex >= 0) rest.substring(0, slashIndex) else rest
        val path = if (slashIndex >= 0) rest.substring(slashIndex + 1) else ""
        val segments = path.split('/').filter { it.isNotEmpty() }
        every { u.scheme } returns scheme
        every { u.authority } returns authority.ifBlank { null }
        every { u.pathSegments } returns segments
        every { u.toString() } returns raw
        return u
    }

    // --- beginOperation / registerUri / cancelReservation ---

    @Test
    fun `beginOperation reserves without creating a visible offer`() {
        val id = BackupCleanupRecoveryStore.beginOperation()
        assertTrue(id != null)
        assertNull(BackupCleanupRecoveryStore.offer.value)
    }

    @Test
    fun `registerUri makes the offer visible as IN_PROGRESS before any write starts`() {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        val u = uri()

        val registered = BackupCleanupRecoveryStore.registerUri(id, u)

        assertTrue(registered, "registration must succeed for the operationId that reserved it")
        val entry = BackupCleanupRecoveryStore.offer.value
        assertTrue(entry != null, "URI registration must be visible immediately, before any write is attempted")
        assertEquals(id, entry!!.operationId)
        assertEquals(u, entry.uri)
        assertEquals(
            SafArtifactOutcome.IN_PROGRESS,
            entry.outcome,
            "a freshly-registered URI with no write yet must be IN_PROGRESS, never a terminal outcome",
        )
    }

    @Test
    fun `registerUri without a prior beginOperation is rejected`() {
        val registered = BackupCleanupRecoveryStore.registerUri("never-reserved", uri())
        assertFalse(registered)
        assertNull(BackupCleanupRecoveryStore.offer.value)
    }

    @Test
    fun `registerUri fails closed when the durable record cannot be written`() {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        fakePreferenceStore.failWrites = true

        assertFalse(BackupCleanupRecoveryStore.registerUri(id, uri()))
        assertNull(BackupCleanupRecoveryStore.offer.value)
        assertTrue(
            BackupCleanupRecoveryStore.beginOperation() != null,
            "a failed durable registration must release the reservation rather than leave a phantom operation",
        )
    }

    @Test
    fun `a second beginOperation is rejected while the first reservation or offer is pending`() {
        val firstId = BackupCleanupRecoveryStore.beginOperation()
        assertTrue(firstId != null)

        val secondId = BackupCleanupRecoveryStore.beginOperation()
        assertNull(secondId, "a second beginOperation must be rejected while the first reservation is outstanding")
    }

    @Test
    fun `cancelReservation frees the slot without creating an offer`() {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        BackupCleanupRecoveryStore.cancelReservation(id)

        val nextId = BackupCleanupRecoveryStore.beginOperation()
        assertTrue(nextId != null, "cancelReservation must free the reservation slot for a later attempt")
        assertNull(BackupCleanupRecoveryStore.offer.value)
    }

    // --- performWrite ---

    @Test
    fun `performWrite updates the offer to SUCCESS only after the write lambda reports it, then clears the durable record`() = runTest {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        BackupCleanupRecoveryStore.registerUri(id, uri())

        val outcome = BackupCleanupRecoveryStore.performWrite(id) { SafArtifactOutcome.SUCCESS }

        assertEquals(SafArtifactOutcome.SUCCESS, outcome)
        assertEquals(SafArtifactOutcome.SUCCESS, BackupCleanupRecoveryStore.offer.value?.outcome)
        // A successful backup is never offered for cleanup or presented as reversible -- the durable
        // record must be cleared immediately, not merely marked SUCCESS.
        assertTrue(fakePreferenceStore.getString(RECORD_PREFERENCE_KEY, "").get().isBlank())
    }

    @Test
    fun `performWrite updates the offer to FAILED when the write lambda reports failure`() = runTest {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        BackupCleanupRecoveryStore.registerUri(id, uri())

        val outcome = BackupCleanupRecoveryStore.performWrite(id) { SafArtifactOutcome.FAILED }

        assertEquals(SafArtifactOutcome.FAILED, outcome)
        assertEquals(SafArtifactOutcome.FAILED, BackupCleanupRecoveryStore.offer.value?.outcome)
    }

    @Test
    fun `performWrite marks FAILED (not a fabricated success) when the write lambda throws an ordinary exception`() = runTest {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        BackupCleanupRecoveryStore.registerUri(id, uri())

        val outcome = BackupCleanupRecoveryStore.performWrite(id) { error("boom") }

        assertEquals(SafArtifactOutcome.FAILED, outcome)
        assertEquals(SafArtifactOutcome.FAILED, BackupCleanupRecoveryStore.offer.value?.outcome)
    }

    @Test
    fun `cancellation during performWrite retains the offer as CANCELLED and rethrows`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        BackupCleanupRecoveryStore.registerUri(id, uri())

        var caughtCancellation = false
        val job: Job = scope.launch {
            try {
                BackupCleanupRecoveryStore.performWrite(id) {
                    kotlinx.coroutines.awaitCancellation()
                }
            } catch (e: CancellationException) {
                caughtCancellation = true
            }
        }
        scope.advanceUntilIdle()
        job.cancel()
        scope.advanceUntilIdle()

        assertTrue(caughtCancellation, "CancellationException must propagate out of performWrite, never be swallowed")
        assertEquals(SafArtifactOutcome.CANCELLED, BackupCleanupRecoveryStore.offer.value?.outcome)
        assertTrue(BackupCleanupRecoveryStore.offer.value != null, "the Uri must remain retained after cancellation, not be discarded")
    }

    @Test
    fun `a stale performWrite callback for an operationId this store no longer tracks does not corrupt the retained offer`() = runTest {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        val staleId = "stale-unrelated-operation-id"
        BackupCleanupRecoveryStore.registerUri(id, uri())

        BackupCleanupRecoveryStore.performWrite(staleId) { SafArtifactOutcome.SUCCESS }

        assertEquals(id, BackupCleanupRecoveryStore.offer.value?.operationId)
        assertEquals(SafArtifactOutcome.IN_PROGRESS, BackupCleanupRecoveryStore.offer.value?.outcome)
    }

    // --- clear ---

    @Test
    fun `clear removes the retained offer only for a matching operationId`() {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        BackupCleanupRecoveryStore.registerUri(id, uri())
        assertTrue(BackupCleanupRecoveryStore.offer.value != null)

        BackupCleanupRecoveryStore.clear("some-unrelated-operation-id")
        assertTrue(BackupCleanupRecoveryStore.offer.value != null, "clear() with a non-matching id must not remove an unrelated offer")

        BackupCleanupRecoveryStore.clear(id)
        assertNull(BackupCleanupRecoveryStore.offer.value)
    }

    @Test
    fun `clear then beginOperation starts an independent operation with no residue from the prior one`() = runTest {
        val firstId = BackupCleanupRecoveryStore.beginOperation()!!
        BackupCleanupRecoveryStore.registerUri(firstId, uri())
        BackupCleanupRecoveryStore.performWrite(firstId) { SafArtifactOutcome.FAILED }
        BackupCleanupRecoveryStore.clear(firstId)
        assertNull(BackupCleanupRecoveryStore.offer.value)

        val secondId = BackupCleanupRecoveryStore.beginOperation()!!
        val secondUri = uri()
        assertTrue(BackupCleanupRecoveryStore.registerUri(secondId, secondUri))
        val outcome = BackupCleanupRecoveryStore.performWrite(secondId) { SafArtifactOutcome.SUCCESS }

        assertEquals(SafArtifactOutcome.SUCCESS, outcome)
    }

    // --- concurrency (Finding 3) ---

    @Test
    fun `concurrent beginOperation calls under contention -- exactly one wins`() {
        val threadCount = 16
        val executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount)
        val startLatch = java.util.concurrent.CountDownLatch(1)
        val results = java.util.Collections.synchronizedList(mutableListOf<String?>())
        try {
            val futures = (1..threadCount).map {
                executor.submit {
                    startLatch.await()
                    results.add(BackupCleanupRecoveryStore.beginOperation())
                }
            }
            startLatch.countDown()
            futures.forEach { it.get(5, java.util.concurrent.TimeUnit.SECONDS) }
        } finally {
            executor.shutdown()
        }

        assertEquals(1, results.count { it != null }, "exactly one concurrent beginOperation call must win the reservation")
    }

    // --- persistence / reconciliation (Finding 4) ---

    @Test
    fun `process recreation with an in-progress backup and an attached work request re-polls WorkManager to a terminal state`() = runTest {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        val u = uri()
        BackupCleanupRecoveryStore.registerUri(id, u)
        val workId = UUID.randomUUID()
        BackupCleanupRecoveryStore.attachWorkRequest(id, workId)

        // The process dies here -- in-memory state is gone, but the FakePreferenceStore (standing in
        // for real on-disk SharedPreferences) still has the durable record.
        simulateProcessDeathKeepingDurableRecord(id, u, workId, SafArtifactOutcome.IN_PROGRESS)

        mockkObject(BackupCreateJob)
        coEvery { BackupCreateJob.awaitManualJobTerminalState(any(), workId) } returns WorkInfo.State.SUCCEEDED

        val context = mockk<Context>()
        BackupCleanupRecoveryStore.reconcileOnStartup(context)

        // backupJobOutcomeFor(SUCCEEDED) == SUCCESS -- a successful backup is never offered cleanup,
        // so reconciliation must clear the record rather than restore a visible offer.
        assertEquals(SafArtifactOutcome.SUCCESS, backupJobOutcomeFor(WorkInfo.State.SUCCEEDED))
        assertNull(BackupCleanupRecoveryStore.offer.value)
    }

    @Test
    fun `process recreation after a failed backup restores a visible cleanup offer`() = runTest {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        val u = uri()
        BackupCleanupRecoveryStore.registerUri(id, u)
        val workId = UUID.randomUUID()
        BackupCleanupRecoveryStore.attachWorkRequest(id, workId)

        simulateProcessDeathKeepingDurableRecord(id, u, workId, SafArtifactOutcome.IN_PROGRESS)

        mockkObject(BackupCreateJob)
        coEvery { BackupCreateJob.awaitManualJobTerminalState(any(), workId) } returns WorkInfo.State.FAILED

        val context = mockk<Context>()
        BackupCleanupRecoveryStore.reconcileOnStartup(context)

        val restored = BackupCleanupRecoveryStore.offer.value
        assertTrue(restored != null, "a failed backup discovered on restart must produce a visible cleanup offer")
        assertEquals(id, restored?.operationId)
        assertEquals(SafArtifactOutcome.FAILED, restored?.outcome)
    }

    @Test
    fun `process recreation after a successful backup does not restore an offer -- WorkManager terminal-state reconciliation`() = runTest {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        val u = uri()
        BackupCleanupRecoveryStore.registerUri(id, u)
        val workId = UUID.randomUUID()
        BackupCleanupRecoveryStore.attachWorkRequest(id, workId)

        simulateProcessDeathKeepingDurableRecord(id, u, workId, SafArtifactOutcome.IN_PROGRESS)

        mockkObject(BackupCreateJob)
        coEvery { BackupCreateJob.awaitManualJobTerminalState(any(), workId) } returns WorkInfo.State.SUCCEEDED

        val context = mockk<Context>()
        BackupCleanupRecoveryStore.reconcileOnStartup(context)

        assertNull(BackupCleanupRecoveryStore.offer.value)
    }

    // --- Finding 2: process-death race -- no attached workRequestId ---

    @Test
    fun `case F -- UNKNOWN legacy record with no attached id and no matching WorkManager job is UNRESOLVED, never PARTIAL_OR_EMPTY`() = runTest {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        val u = uri()
        BackupCleanupRecoveryStore.registerUri(id, u)
        // Simulate the process dying between registerUri and BackupCreateJob.startNow ever being
        // called, persisted as a legacy (pre-enqueue-state) v1 record -- migrates to UNKNOWN, never
        // NOT_ATTEMPTED. No workRequestId was ever persisted, and WorkManager genuinely has no record
        // of any job for this uri (findManualJobIdForUri returns null) -- this must never be treated as
        // proof the backup was never attempted.
        simulateProcessDeathKeepingDurableRecord(id, u, workRequestId = null, outcome = SafArtifactOutcome.IN_PROGRESS)
        mockkObject(BackupCreateJob)
        coEvery { BackupCreateJob.findManualJobIdForUri(any(), any()) } returns null

        val context = mockk<Context>()
        BackupCleanupRecoveryStore.reconcileOnStartup(context)

        val restored = BackupCleanupRecoveryStore.offer.value
        assertTrue(restored != null, "an unresolved in-progress record with no matching job must still surface a cleanup offer")
        assertEquals(
            SafArtifactOutcome.UNRESOLVED,
            restored?.outcome,
            "must not fabricate SUCCESS/PARTIAL_OR_EMPTY or silently drop the record when no WorkManager job was ever found for a legacy (UNKNOWN-enqueue-state) record",
        )
        coVerify(exactly = 0) { BackupCreateJob.awaitManualJobTerminalState(any(), any()) }
    }

    @Test
    fun `process death after enqueue but before attachWorkRequest -- the real job is discovered via its uri tag and awaited to a terminal state`() = runTest {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        val u = uri()
        BackupCleanupRecoveryStore.registerUri(id, u)
        // The process died after BackupCreateJob.startNow already enqueued the real WorkManager job,
        // but before attachWorkRequest persisted its id -- no workRequestId in the durable record.
        simulateProcessDeathKeepingDurableRecord(id, u, workRequestId = null, outcome = SafArtifactOutcome.IN_PROGRESS)
        val discoveredId = UUID.randomUUID()
        mockkObject(BackupCreateJob)
        coEvery { BackupCreateJob.findManualJobIdForUri(any(), any()) } returns discoveredId
        coEvery { BackupCreateJob.awaitManualJobTerminalState(any(), discoveredId) } returns WorkInfo.State.FAILED

        val context = mockk<Context>()
        BackupCleanupRecoveryStore.reconcileOnStartup(context)

        val restored = BackupCleanupRecoveryStore.offer.value
        assertTrue(restored != null, "the real job, once discovered by its uri tag, must still produce a truthful terminal offer")
        assertEquals(SafArtifactOutcome.FAILED, restored?.outcome)
        coVerify(exactly = 1) { BackupCreateJob.awaitManualJobTerminalState(any(), discoveredId) }
    }

    @Test
    fun `matching job still running -- reconcileOnStartup does not expose any offer (Remove) until it actually finishes`() = runTest {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        val u = uri()
        BackupCleanupRecoveryStore.registerUri(id, u)
        simulateProcessDeathKeepingDurableRecord(id, u, workRequestId = null, outcome = SafArtifactOutcome.IN_PROGRESS)
        val discoveredId = UUID.randomUUID()
        mockkObject(BackupCreateJob)
        coEvery { BackupCreateJob.findManualJobIdForUri(any(), any()) } returns discoveredId
        val jobStillRunning = kotlinx.coroutines.CompletableDeferred<WorkInfo.State?>()
        coEvery { BackupCreateJob.awaitManualJobTerminalState(any(), discoveredId) } coAnswers { jobStillRunning.await() }

        val context = mockk<Context>()
        val reconcileJob = launch { BackupCleanupRecoveryStore.reconcileOnStartup(context) }
        advanceUntilIdle()

        // While the real job is still active (awaitManualJobTerminalState has not returned), no
        // offer -- not even a non-terminal one -- must be exposed. This is what makes Remove
        // structurally unreachable during this window: the root cleanup dialog renders nothing at
        // all when `offer` is null, exactly like the `IN_PROGRESS` gate for the in-process routes.
        assertNull(BackupCleanupRecoveryStore.offer.value, "no offer may be visible while a matching job may still be writing")

        jobStillRunning.complete(WorkInfo.State.SUCCEEDED)
        advanceUntilIdle()
        reconcileJob.join()

        // The job succeeded -- a successful backup is never offered for cleanup.
        assertNull(BackupCleanupRecoveryStore.offer.value)
    }

    @Test
    fun `no matching job anywhere -- honestly UNRESOLVED, never a fabricated success or PARTIAL_OR_EMPTY`() = runTest {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        val u = uri()
        BackupCleanupRecoveryStore.registerUri(id, u)
        simulateProcessDeathKeepingDurableRecord(id, u, workRequestId = null, outcome = SafArtifactOutcome.IN_PROGRESS)
        mockkObject(BackupCreateJob)
        coEvery { BackupCreateJob.findManualJobIdForUri(any(), any()) } returns null

        val context = mockk<Context>()
        BackupCleanupRecoveryStore.reconcileOnStartup(context)

        assertEquals(SafArtifactOutcome.UNRESOLVED, BackupCleanupRecoveryStore.offer.value?.outcome)
    }

    @Test
    fun `a persisted record with a version this build does not recognize is discarded, not trusted`() = runTest {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        BackupCleanupRecoveryStore.registerUri(id, uri())
        BackupCleanupRecoveryStore.resetForTesting()
        // Re-seed a record whose version does not match CURRENT_RECORD_VERSION.
        val badVersionJson = """{"version":999,"operationId":"$id","uriString":"content://x/1","operationType":"backup_create","outcomeName":"IN_PROGRESS","createdAtEpochMillis":0}"""
        fakePreferenceStore.getString(RECORD_PREFERENCE_KEY, "").set(badVersionJson)

        val context = mockk<Context>()
        BackupCleanupRecoveryStore.reconcileOnStartup(context)

        assertNull(BackupCleanupRecoveryStore.offer.value, "a record with an unrecognized version must never drive a deletion decision")
    }

    @Test
    fun `a corrupted (unparseable) persisted record is discarded without crashing startup`() = runTest {
        fakePreferenceStore.getString(RECORD_PREFERENCE_KEY, "").set("{ this is not valid json at all")

        val context = mockk<Context>()
        BackupCleanupRecoveryStore.reconcileOnStartup(context)

        assertNull(BackupCleanupRecoveryStore.offer.value, "a corrupted record must be discarded, not crash reconciliation")
    }

    // --- Finding 3: structural validation of the durable record ---

    private fun seedRawRecord(json: String) {
        fakePreferenceStore.getString(RECORD_PREFERENCE_KEY, "").set(json)
    }

    private fun recordJson(
        operationId: String = UUID.randomUUID().toString(),
        uriString: String = "content://com.example.test/document/abc",
        operationType: String = BackupCleanupRecoveryStore.OPERATION_TYPE_BACKUP_CREATE,
        outcomeName: String = "IN_PROGRESS",
        version: Int = BackupCleanupRecoveryStore.CURRENT_RECORD_VERSION,
    ): String = """{"version":$version,"operationId":"$operationId","uriString":"$uriString","operationType":"$operationType","outcomeName":"$outcomeName","createdAtEpochMillis":0}"""

    @Test
    fun `a record with an unknown operationType is rejected`() = runTest {
        seedRawRecord(recordJson(operationType = "some_future_export_kind"))

        BackupCleanupRecoveryStore.reconcileOnStartup(mockk<Context>())

        assertNull(BackupCleanupRecoveryStore.offer.value, "an unrecognized operationType must never drive a deletion decision")
    }

    @Test
    fun `a record with the wrong (non-backup) operationType is rejected even if otherwise well-formed`() = runTest {
        seedRawRecord(recordJson(operationType = "extension_export"))

        BackupCleanupRecoveryStore.reconcileOnStartup(mockk<Context>())

        assertNull(BackupCleanupRecoveryStore.offer.value)
    }

    @Test
    fun `a record with an empty operationId is rejected`() = runTest {
        seedRawRecord(recordJson(operationId = ""))

        BackupCleanupRecoveryStore.reconcileOnStartup(mockk<Context>())

        assertNull(BackupCleanupRecoveryStore.offer.value, "an empty operationId is not a value newOperationId() could ever have produced")
    }

    @Test
    fun `a record with an operationId that is not a well-formed UUID is rejected`() = runTest {
        seedRawRecord(recordJson(operationId = "not-a-uuid-at-all"))

        BackupCleanupRecoveryStore.reconcileOnStartup(mockk<Context>())

        assertNull(BackupCleanupRecoveryStore.offer.value)
    }

    @Test
    fun `a record whose uriString is empty is rejected`() = runTest {
        seedRawRecord(recordJson(uriString = ""))

        BackupCleanupRecoveryStore.reconcileOnStartup(mockk<Context>())

        assertNull(BackupCleanupRecoveryStore.offer.value)
    }

    @Test
    fun `a record whose uriString is a file URI (not a SAF content document URI) is rejected`() = runTest {
        seedRawRecord(recordJson(uriString = "file:///storage/emulated/0/backup/komikku.tachibk"))

        BackupCleanupRecoveryStore.reconcileOnStartup(mockk<Context>())

        assertNull(BackupCleanupRecoveryStore.offer.value, "a file:// URI must never be restored into a cleanup offer that drives DocumentsContract.deleteDocument")
    }

    @Test
    fun `a record whose uriString is a non-document content URI is rejected`() = runTest {
        // A raw media-provider row -- a real content:// URI, but not a SAF *document* URI, and not
        // something this store should ever hand to DocumentsContract.deleteDocument.
        seedRawRecord(recordJson(uriString = "content://media/external/images/media/123"))

        BackupCleanupRecoveryStore.reconcileOnStartup(mockk<Context>())

        assertNull(BackupCleanupRecoveryStore.offer.value)
    }

    @Test
    fun `a record whose uriString is a valid tree-document SAF URI is accepted`() = runTest {
        val id = UUID.randomUUID().toString()
        seedRawRecord(
            recordJson(
                operationId = id,
                uriString = "content://com.android.externalstorage.documents/tree/primary%3A/document/primary%3ADownload%2Fkomikku.tachibk",
                outcomeName = "FAILED",
            ),
        )

        BackupCleanupRecoveryStore.reconcileOnStartup(mockk<Context>())

        val restored = BackupCleanupRecoveryStore.offer.value
        assertTrue(restored != null, "a structurally valid record must be restored")
        assertEquals(id, restored?.operationId)
        assertEquals(SafArtifactOutcome.FAILED, restored?.outcome)
    }

    @Test
    fun `a fully valid record with a plain document-form URI passes structural validation`() = runTest {
        val id = UUID.randomUUID().toString()
        seedRawRecord(recordJson(operationId = id, uriString = "content://com.example.test/document/abc123", outcomeName = "PARTIAL_OR_EMPTY"))

        BackupCleanupRecoveryStore.reconcileOnStartup(mockk<Context>())

        val restored = BackupCleanupRecoveryStore.offer.value
        assertTrue(restored != null)
        assertEquals(id, restored?.operationId)
        assertEquals(SafArtifactOutcome.PARTIAL_OR_EMPTY, restored?.outcome)
    }

    // --- Finding 1: handleUnregisterableBackupUri / adoptUnregisterableUri ---

    private val deletedMessage: dev.icerock.moko.resources.StringResource = mockk()
    private val retainedMessage: dev.icerock.moko.resources.StringResource = mockk()
    private val unrecoverableMessage: dev.icerock.moko.resources.StringResource = mockk()

    private fun mockToastExtension(context: Context) {
        mockkStatic("eu.kanade.tachiyomi.util.system.ToastExtensionsKt")
        every { context.toast(any<dev.icerock.moko.resources.StringResource>(), any(), any()) } returns mockk(relaxed = true)
    }

    @Test
    fun `adoptUnregisterableUri creates a durable, terminal offer when the store is idle`() {
        val u = uri()

        val adopted = BackupCleanupRecoveryStore.adoptUnregisterableUri(u)

        assertTrue(adopted)
        assertEquals(u, BackupCleanupRecoveryStore.offer.value?.uri)
        assertEquals(SafArtifactOutcome.PARTIAL_OR_EMPTY, BackupCleanupRecoveryStore.offer.value?.outcome)
        val raw = fakePreferenceStore.getString(RECORD_PREFERENCE_KEY, "").get()
        assertTrue(raw.isNotBlank(), "the adopted offer must be durably persisted too, not only kept in memory")
    }

    @Test
    fun `adoptUnregisterableUri refuses to overwrite an already-retained offer`() {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        val registeredUri = uri()
        BackupCleanupRecoveryStore.registerUri(id, registeredUri)

        val adopted = BackupCleanupRecoveryStore.adoptUnregisterableUri(uri())

        assertFalse(adopted)
        assertEquals(registeredUri, BackupCleanupRecoveryStore.offer.value?.uri)
    }

    @Test
    fun `adoptUnregisterableUri fails closed when its durable write fails`() {
        fakePreferenceStore.failWrites = true

        assertFalse(BackupCleanupRecoveryStore.adoptUnregisterableUri(uri()))
        assertNull(BackupCleanupRecoveryStore.offer.value)
        assertTrue(
            BackupCleanupRecoveryStore.beginOperation() != null,
            "a failed adoption must release the single-offer reservation",
        )
    }

    @Test
    fun `handleUnregisterableBackupUri reports DELETED when deletion succeeds`() {
        mockkStatic(android.provider.DocumentsContract::class)
        val context = mockk<Context>()
        val resolver = mockk<android.content.ContentResolver>()
        val u = uri()
        every { context.contentResolver } returns resolver
        every { android.provider.DocumentsContract.deleteDocument(resolver, u) } returns true
        mockToastExtension(context)

        val outcome = handleUnregisterableBackupUri(context, u, deletedMessage, retainedMessage, unrecoverableMessage)

        assertEquals(UnregisterableUriOutcome.DELETED, outcome)
        assertNull(BackupCleanupRecoveryStore.offer.value)
        unmockkStatic(android.provider.DocumentsContract::class)
    }

    @Test
    fun `handleUnregisterableBackupUri retains the Uri durably when deletion fails`() {
        mockkStatic(android.provider.DocumentsContract::class)
        val context = mockk<Context>()
        val resolver = mockk<android.content.ContentResolver>()
        val u = uri()
        every { context.contentResolver } returns resolver
        every { android.provider.DocumentsContract.deleteDocument(resolver, u) } returns false
        mockToastExtension(context)

        val outcome = handleUnregisterableBackupUri(context, u, deletedMessage, retainedMessage, unrecoverableMessage)

        assertEquals(UnregisterableUriOutcome.RETAINED_FOR_CLEANUP, outcome)
        assertEquals(u, BackupCleanupRecoveryStore.offer.value?.uri)
        val raw = fakePreferenceStore.getString(RECORD_PREFERENCE_KEY, "").get()
        assertTrue(raw.isNotBlank())
        unmockkStatic(android.provider.DocumentsContract::class)
    }

    @Test
    fun `handleUnregisterableBackupUri propagates CancellationException instead of reporting an outcome`() {
        mockkStatic(android.provider.DocumentsContract::class)
        val context = mockk<Context>()
        val resolver = mockk<android.content.ContentResolver>()
        val u = uri()
        every { context.contentResolver } returns resolver
        every { android.provider.DocumentsContract.deleteDocument(resolver, u) } throws CancellationException("cancelled")
        mockToastExtension(context)

        var thrown: CancellationException? = null
        try {
            handleUnregisterableBackupUri(context, u, deletedMessage, retainedMessage, unrecoverableMessage)
        } catch (e: CancellationException) {
            thrown = e
        }

        assertTrue(thrown != null)
        assertNull(BackupCleanupRecoveryStore.offer.value)
        unmockkStatic(android.provider.DocumentsContract::class)
    }

    @Test
    fun `the persisted record never contains backup contents, exception text, or a raw filesystem path -- only the minimal privacy-safe fields`() = runTest {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        BackupCleanupRecoveryStore.registerUri(id, uri())
        val workId = UUID.randomUUID()
        BackupCleanupRecoveryStore.attachWorkRequest(id, workId)

        val raw = fakePreferenceStore.getString(RECORD_PREFERENCE_KEY, "").get()
        assertTrue(raw.isNotBlank())
        // The record is a flat JSON object with exactly these keys -- nothing else must ever appear
        // in it (no manga titles, no source names, no stack traces, no on-disk paths beyond the SAF
        // Uri string itself, which the OS already grants this app access to).
        val allowedKeys = setOf("version", "operationId", "uriString", "operationType", "workRequestId", "outcomeName", "enqueueStateName", "createdAtEpochMillis")
        val record = Json.parseToJsonElement(raw).jsonObject
        assertTrue(record.keys.all { it in allowedKeys }, "unexpected key(s) in persisted record: ${record.keys - allowedKeys}")
    }

    @Test
    fun `reconcileOnStartup is a no-op when there is nothing to reconcile`() = runTest {
        val context = mockk<Context>()
        BackupCleanupRecoveryStore.reconcileOnStartup(context)
        assertNull(BackupCleanupRecoveryStore.offer.value)
    }

    @Test
    fun `reconcileOnStartup does not clobber an offer that is already live in memory`() = runTest {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        val u = uri()
        BackupCleanupRecoveryStore.registerUri(id, u)

        val context = mockk<Context>()
        BackupCleanupRecoveryStore.reconcileOnStartup(context)

        // The in-memory offer from this same process run must be untouched.
        assertEquals(id, BackupCleanupRecoveryStore.offer.value?.operationId)
        assertEquals(SafArtifactOutcome.IN_PROGRESS, BackupCleanupRecoveryStore.offer.value?.outcome)
    }

    /**
     * Re-seeds the FakePreferenceStore's durable record directly (bypassing the in-memory API) to
     * simulate exactly what a real process death leaves behind: the on-disk record from before the
     * process died, with no in-memory state at all. [resetForTesting] is not used here because it
     * also clears the durable record, which would defeat the simulation.
     */
    private fun simulateProcessDeathKeepingDurableRecord(
        operationId: String,
        uri: Uri,
        workRequestId: UUID?,
        outcome: SafArtifactOutcome,
        // null -- a legacy (pre-KMK_CLAUDE_SAF_BACKUP_RECOVERY_CORRECTIVE_PASS_2026-08-07) v1 record
        // with no enqueueStateName field at all, exactly what a record persisted by an older build
        // looks like on disk; loadRecord() migrates this to BackupEnqueueState.UNKNOWN. Pass an
        // explicit state to simulate a record already written by *this* build (v2, ATTEMPTED/
        // NOT_ATTEMPTED/UNKNOWN as persisted).
        enqueueState: BackupCleanupRecoveryStore.BackupEnqueueState? = null,
    ) {
        val json = if (enqueueState == null) {
            buildString {
                append("""{"version":1,"operationId":"$operationId","uriString":"$uri","operationType":"backup_create",""")
                append(if (workRequestId != null) """"workRequestId":"$workRequestId",""" else "")
                append(""""outcomeName":"${outcome.name}","createdAtEpochMillis":0}""")
            }
        } else {
            buildString {
                append("""{"version":2,"operationId":"$operationId","uriString":"$uri","operationType":"backup_create",""")
                append(if (workRequestId != null) """"workRequestId":"$workRequestId",""" else "")
                append(""""outcomeName":"${outcome.name}","enqueueStateName":"${enqueueState.name}","createdAtEpochMillis":0}""")
            }
        }
        // Drop only the in-memory state (as a real process death would), leaving the
        // FakePreferenceStore's record intact -- but resetForTesting() also clears the durable
        // record, so re-seed it immediately after.
        BackupCleanupRecoveryStore.resetForTesting()
        fakePreferenceStore.getString(RECORD_PREFERENCE_KEY, "").set(json)
    }

    // --- KMK_CLAUDE_SAF_BACKUP_RECOVERY_ACTUAL_FINAL_PASS_2026-08-07: enqueue-state reconciliation matrix ---

    @Test
    fun `case A -- NOT_ATTEMPTED with no matching job is PARTIAL_OR_EMPTY without ever consulting WorkManager`() = runTest {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        val u = uri()
        BackupCleanupRecoveryStore.registerUri(id, u)
        simulateProcessDeathKeepingDurableRecord(
            id,
            u,
            workRequestId = null,
            outcome = SafArtifactOutcome.IN_PROGRESS,
            enqueueState = BackupCleanupRecoveryStore.BackupEnqueueState.NOT_ATTEMPTED,
        )
        mockkObject(BackupCreateJob)

        BackupCleanupRecoveryStore.reconcileOnStartup(mockk<Context>())

        assertEquals(SafArtifactOutcome.PARTIAL_OR_EMPTY, BackupCleanupRecoveryStore.offer.value?.outcome)
        coVerify(exactly = 0) { BackupCreateJob.findManualJobIdForUri(any(), any()) }
        coVerify(exactly = 0) { BackupCreateJob.awaitManualJobTerminalState(any(), any()) }
    }

    @Test
    fun `case B -- ATTEMPTED with an active job exposes no offer until it actually finishes`() = runTest {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        val u = uri()
        BackupCleanupRecoveryStore.registerUri(id, u)
        val workId = UUID.randomUUID()
        simulateProcessDeathKeepingDurableRecord(
            id,
            u,
            workRequestId = workId,
            outcome = SafArtifactOutcome.IN_PROGRESS,
            enqueueState = BackupCleanupRecoveryStore.BackupEnqueueState.ATTEMPTED,
        )
        mockkObject(BackupCreateJob)
        val jobStillRunning = CompletableDeferred<WorkInfo.State?>()
        coEvery { BackupCreateJob.awaitManualJobTerminalState(any(), workId) } coAnswers { jobStillRunning.await() }

        val reconcileJob = launch { BackupCleanupRecoveryStore.reconcileOnStartup(mockk<Context>()) }
        advanceUntilIdle()

        assertNull(BackupCleanupRecoveryStore.offer.value, "no offer may be visible while a matching ATTEMPTED job may still be writing")

        jobStillRunning.complete(WorkInfo.State.RUNNING)
        advanceUntilIdle()
        reconcileJob.join()
    }

    @Test
    fun `case C -- ATTEMPTED with a successful job is SUCCESS and the durable record is cleared`() = runTest {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        val u = uri()
        BackupCleanupRecoveryStore.registerUri(id, u)
        val workId = UUID.randomUUID()
        simulateProcessDeathKeepingDurableRecord(
            id,
            u,
            workRequestId = workId,
            outcome = SafArtifactOutcome.IN_PROGRESS,
            enqueueState = BackupCleanupRecoveryStore.BackupEnqueueState.ATTEMPTED,
        )
        mockkObject(BackupCreateJob)
        coEvery { BackupCreateJob.awaitManualJobTerminalState(any(), workId) } returns WorkInfo.State.SUCCEEDED

        BackupCleanupRecoveryStore.reconcileOnStartup(mockk<Context>())

        assertNull(BackupCleanupRecoveryStore.offer.value, "a successful backup must never be offered for cleanup")
        assertTrue(fakePreferenceStore.getString(RECORD_PREFERENCE_KEY, "").get().isBlank(), "the durable record must be cleared on SUCCESS")
    }

    @Test
    fun `case D -- ATTEMPTED with a failed job offers Remove-Keep as FAILED`() = runTest {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        val u = uri()
        BackupCleanupRecoveryStore.registerUri(id, u)
        val workId = UUID.randomUUID()
        simulateProcessDeathKeepingDurableRecord(
            id,
            u,
            workRequestId = workId,
            outcome = SafArtifactOutcome.IN_PROGRESS,
            enqueueState = BackupCleanupRecoveryStore.BackupEnqueueState.ATTEMPTED,
        )
        mockkObject(BackupCreateJob)
        coEvery { BackupCreateJob.awaitManualJobTerminalState(any(), workId) } returns WorkInfo.State.FAILED

        BackupCleanupRecoveryStore.reconcileOnStartup(mockk<Context>())

        assertEquals(SafArtifactOutcome.FAILED, BackupCleanupRecoveryStore.offer.value?.outcome)
    }

    @Test
    fun `case D -- ATTEMPTED with a cancelled job offers Remove-Keep as CANCELLED`() = runTest {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        val u = uri()
        BackupCleanupRecoveryStore.registerUri(id, u)
        val workId = UUID.randomUUID()
        simulateProcessDeathKeepingDurableRecord(
            id,
            u,
            workRequestId = workId,
            outcome = SafArtifactOutcome.IN_PROGRESS,
            enqueueState = BackupCleanupRecoveryStore.BackupEnqueueState.ATTEMPTED,
        )
        mockkObject(BackupCreateJob)
        coEvery { BackupCreateJob.awaitManualJobTerminalState(any(), workId) } returns WorkInfo.State.CANCELLED

        BackupCleanupRecoveryStore.reconcileOnStartup(mockk<Context>())

        assertEquals(SafArtifactOutcome.CANCELLED, BackupCleanupRecoveryStore.offer.value?.outcome)
    }

    @Test
    fun `case E -- ATTEMPTED with no matching (pruned) job is UNRESOLVED, never PARTIAL_OR_EMPTY`() = runTest {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        val u = uri()
        BackupCleanupRecoveryStore.registerUri(id, u)
        simulateProcessDeathKeepingDurableRecord(
            id,
            u,
            workRequestId = null,
            outcome = SafArtifactOutcome.IN_PROGRESS,
            enqueueState = BackupCleanupRecoveryStore.BackupEnqueueState.ATTEMPTED,
        )
        mockkObject(BackupCreateJob)
        coEvery { BackupCreateJob.findManualJobIdForUri(any(), any()) } returns null

        BackupCleanupRecoveryStore.reconcileOnStartup(mockk<Context>())

        assertEquals(SafArtifactOutcome.UNRESOLVED, BackupCleanupRecoveryStore.offer.value?.outcome)
    }

    @Test
    fun `case G -- a known request id that WorkManager no longer has any record of is UNRESOLVED`() = runTest {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        val u = uri()
        BackupCleanupRecoveryStore.registerUri(id, u)
        val workId = UUID.randomUUID()
        simulateProcessDeathKeepingDurableRecord(
            id,
            u,
            workRequestId = workId,
            outcome = SafArtifactOutcome.IN_PROGRESS,
            enqueueState = BackupCleanupRecoveryStore.BackupEnqueueState.ATTEMPTED,
        )
        mockkObject(BackupCreateJob)
        coEvery { BackupCreateJob.awaitManualJobTerminalState(any(), workId) } returns null

        BackupCleanupRecoveryStore.reconcileOnStartup(mockk<Context>())

        assertEquals(SafArtifactOutcome.UNRESOLVED, BackupCleanupRecoveryStore.offer.value?.outcome)
        coVerify(exactly = 0) { BackupCreateJob.findManualJobIdForUri(any(), any()) }
    }

    @Test
    fun `case H -- a WorkManager job-discovery exception resolves to UNRESOLVED and retains the durable record`() = runTest {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        val u = uri()
        BackupCleanupRecoveryStore.registerUri(id, u)
        simulateProcessDeathKeepingDurableRecord(
            id,
            u,
            workRequestId = null,
            outcome = SafArtifactOutcome.IN_PROGRESS,
            enqueueState = BackupCleanupRecoveryStore.BackupEnqueueState.ATTEMPTED,
        )
        mockkObject(BackupCreateJob)
        coEvery { BackupCreateJob.findManualJobIdForUri(any(), any()) } throws IllegalStateException("WorkManager not initialized")

        BackupCleanupRecoveryStore.reconcileOnStartup(mockk<Context>())

        assertEquals(SafArtifactOutcome.UNRESOLVED, BackupCleanupRecoveryStore.offer.value?.outcome)
        assertTrue(fakePreferenceStore.getString(RECORD_PREFERENCE_KEY, "").get().isNotBlank(), "an ordinary WorkManager exception must never lose the durable record")
    }

    @Test
    fun `case H -- a WorkManager terminal-state polling exception resolves to UNRESOLVED and retains the durable record`() = runTest {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        val u = uri()
        BackupCleanupRecoveryStore.registerUri(id, u)
        val workId = UUID.randomUUID()
        simulateProcessDeathKeepingDurableRecord(
            id,
            u,
            workRequestId = workId,
            outcome = SafArtifactOutcome.IN_PROGRESS,
            enqueueState = BackupCleanupRecoveryStore.BackupEnqueueState.ATTEMPTED,
        )
        mockkObject(BackupCreateJob)
        coEvery { BackupCreateJob.awaitManualJobTerminalState(any(), workId) } throws RuntimeException("database error")

        BackupCleanupRecoveryStore.reconcileOnStartup(mockk<Context>())

        assertEquals(SafArtifactOutcome.UNRESOLVED, BackupCleanupRecoveryStore.offer.value?.outcome)
        assertTrue(fakePreferenceStore.getString(RECORD_PREFERENCE_KEY, "").get().isNotBlank(), "an ordinary WorkManager exception must never lose the durable record")
    }

    @Test
    fun `case I -- CancellationException during reconciliation rethrows and never mutates the durable record`() = runTest {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        val u = uri()
        BackupCleanupRecoveryStore.registerUri(id, u)
        val workId = UUID.randomUUID()
        simulateProcessDeathKeepingDurableRecord(
            id,
            u,
            workRequestId = workId,
            outcome = SafArtifactOutcome.IN_PROGRESS,
            enqueueState = BackupCleanupRecoveryStore.BackupEnqueueState.ATTEMPTED,
        )
        val rawBefore = fakePreferenceStore.getString(RECORD_PREFERENCE_KEY, "").get()
        mockkObject(BackupCreateJob)
        coEvery { BackupCreateJob.awaitManualJobTerminalState(any(), workId) } throws CancellationException("cancelled")

        var thrown: CancellationException? = null
        try {
            BackupCleanupRecoveryStore.reconcileOnStartup(mockk<Context>())
        } catch (e: CancellationException) {
            thrown = e
        }

        assertTrue(thrown != null, "CancellationException must propagate out of reconcileOnStartup, never be swallowed")
        assertNull(BackupCleanupRecoveryStore.offer.value, "a cancelled reconciliation must never restore an in-memory offer")
        assertEquals(rawBefore, fakePreferenceStore.getString(RECORD_PREFERENCE_KEY, "").get(), "a cancelled reconciliation must never mutate the durable record")
    }

    @Test
    fun `markEnqueueAttempted reports false when its durable write fails`() {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        BackupCleanupRecoveryStore.registerUri(id, uri())
        fakePreferenceStore.failWrites = true

        assertFalse(BackupCleanupRecoveryStore.markEnqueueAttempted(id))
        assertTrue(
            fakePreferenceStore.getString(RECORD_PREFERENCE_KEY, "").get()
                .contains(""""enqueueStateName":"NOT_ATTEMPTED""""),
            "a failed ATTEMPTED write must not claim that enqueue certainty was persisted",
        )
    }

    @Test
    fun `attachWorkRequest reports false but preserves the attempted marker when its write fails`() {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        BackupCleanupRecoveryStore.registerUri(id, uri())
        assertTrue(BackupCleanupRecoveryStore.markEnqueueAttempted(id))
        val rawAfterMark = fakePreferenceStore.getString(RECORD_PREFERENCE_KEY, "").get()
        fakePreferenceStore.failWrites = true

        assertFalse(BackupCleanupRecoveryStore.attachWorkRequest(id, UUID.randomUUID()))
        assertEquals(rawAfterMark, fakePreferenceStore.getString(RECORD_PREFERENCE_KEY, "").get())
    }

    @Test
    fun `markEnqueueAttempted persists ATTEMPTED before startNow is ever called, so reconciliation consults WorkManager instead of short-circuiting to PARTIAL_OR_EMPTY`() = runTest {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        val u = uri()
        BackupCleanupRecoveryStore.registerUri(id, u)
        // registerUri alone leaves the record NOT_ATTEMPTED (case A) -- markEnqueueAttempted is what
        // production calls immediately before BackupCreateJob.startNow(), and must durably flip the
        // enqueue state so a process death after this point is never mistaken for "never attempted".
        BackupCleanupRecoveryStore.markEnqueueAttempted(id)
        val rawAfterMark = fakePreferenceStore.getString(RECORD_PREFERENCE_KEY, "").get()
        assertTrue(
            rawAfterMark.contains(""""enqueueStateName":"ATTEMPTED""""),
            "markEnqueueAttempted must durably persist ATTEMPTED before startNow is ever called",
        )

        // Simulate a process death keeping exactly this durable record, and prove reconciliation now
        // consults WorkManager (case E path) instead of short-circuiting to PARTIAL_OR_EMPTY (case A),
        // which is what would happen if the enqueue state had stayed NOT_ATTEMPTED.
        BackupCleanupRecoveryStore.resetForTesting()
        fakePreferenceStore.getString(RECORD_PREFERENCE_KEY, "").set(rawAfterMark)
        mockkObject(BackupCreateJob)
        coEvery { BackupCreateJob.findManualJobIdForUri(any(), any()) } returns null

        BackupCleanupRecoveryStore.reconcileOnStartup(mockk<Context>())

        assertEquals(SafArtifactOutcome.UNRESOLVED, BackupCleanupRecoveryStore.offer.value?.outcome)
        coVerify(exactly = 1) { BackupCreateJob.findManualJobIdForUri(any(), any()) }
    }

    @Test
    fun `attachWorkRequest preserves the ATTEMPTED enqueue state set by markEnqueueAttempted`() = runTest {
        val id = BackupCleanupRecoveryStore.beginOperation()!!
        val u = uri()
        BackupCleanupRecoveryStore.registerUri(id, u)
        BackupCleanupRecoveryStore.markEnqueueAttempted(id)
        val workId = UUID.randomUUID()
        BackupCleanupRecoveryStore.attachWorkRequest(id, workId)

        val raw = fakePreferenceStore.getString(RECORD_PREFERENCE_KEY, "").get()
        assertTrue(raw.contains(""""enqueueStateName":"ATTEMPTED""""), "attachWorkRequest must preserve the ATTEMPTED state markEnqueueAttempted already persisted, not reset it")
        assertTrue(raw.contains(""""workRequestId":"$workId""""))

        // Now simulate a process death keeping this exact durable record, and prove reconciliation
        // consults WorkManager directly via the persisted request id -- never re-deriving via
        // findManualJobIdForUri, and never short-circuiting via case A (NOT_ATTEMPTED).
        BackupCleanupRecoveryStore.resetForTesting()
        fakePreferenceStore.getString(RECORD_PREFERENCE_KEY, "").set(raw)
        mockkObject(BackupCreateJob)
        coEvery { BackupCreateJob.awaitManualJobTerminalState(any(), workId) } returns WorkInfo.State.FAILED

        BackupCleanupRecoveryStore.reconcileOnStartup(mockk<Context>())

        assertEquals(SafArtifactOutcome.FAILED, BackupCleanupRecoveryStore.offer.value?.outcome)
        coVerify(exactly = 0) { BackupCreateJob.findManualJobIdForUri(any(), any()) }
    }
}
// KMK <--

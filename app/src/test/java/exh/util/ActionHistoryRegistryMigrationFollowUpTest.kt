package exh.util

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.Source
import exh.recs.TestInjektSupport
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import mihon.domain.migration.usecases.MigrateMangaUseCase
import mihon.domain.migration.usecases.MigrationOutcome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK Universal Action History Recovery Plan 2026-07-31 -->
/**
 * Direct tests for [ActionHistoryRegistry]'s `migrationFollowUpFor` -- the migration compensating
 * action ("Migrate back") described in the Universal Action History Recovery Plan. Mirrors
 * [ActionHistoryRegistryPackageFollowUpTest]'s own seam-swap pattern (`internal var *Provider`s bound
 * directly, bypassing Injekt's process-wide singleton cache) and restores real Injekt-backed defaults
 * in [tearDown].
 */
class ActionHistoryRegistryMigrationFollowUpTest {

    companion object {
        // KMK v0.7.44: see TestInjektSupport -- this test constructs favorite=true Manga instances.
        @JvmStatic
        @BeforeAll
        fun registerCustomMangaInfoBinding() = TestInjektSupport.ensureCustomMangaInfoBound()
    }

    private val getManga = mockk<GetManga>()
    private val sourceManager = mockk<SourceManager>()
    private val sourcePreferences = SourcePreferences(FakePreferenceStore())
    private val migrateMangaUseCase = mockk<MigrateMangaUseCase>()

    @AfterEach
    fun tearDown() {
        NonUndoableEventJournal.clear()
        MigrationReceiptJournal.clear()
        actionHistoryFollowUpGetMangaProvider = { Injekt.get() }
        actionHistoryFollowUpSourceManagerProvider = { Injekt.get() }
        actionHistoryFollowUpSourcePreferencesProvider = { Injekt.get() }
        actionHistoryFollowUpMigrateMangaUseCaseProvider = { Injekt.get() }
    }

    private fun bindFakes() {
        sourcePreferences.evaluationMode().set(true)
        actionHistoryFollowUpGetMangaProvider = { getManga }
        actionHistoryFollowUpSourceManagerProvider = { sourceManager }
        actionHistoryFollowUpSourcePreferencesProvider = { sourcePreferences }
        actionHistoryFollowUpMigrateMangaUseCaseProvider = { migrateMangaUseCase }
    }

    private fun manga(id: Long, sourceId: Long, url: String, favorite: Boolean = false) =
        Manga.create().copy(id = id, source = sourceId, url = url, ogTitle = "Manga $id", favorite = favorite)

    private fun seedReceipt(
        origin: Manga = manga(id = 1L, sourceId = 10L, url = "/origin"),
        target: Manga = manga(id = 2L, sourceId = 20L, url = "/target", favorite = true),
        replace: Boolean = false,
    ): String {
        val id = NonUndoableEvent.newId()
        NonUndoableEventJournal.record(
            NonUndoableEvent(id = id, timestamp = System.currentTimeMillis(), eventType = NonUndoableEventType.MIGRATION_COMPLETED),
        )
        MigrationReceiptJournal.record(
            MigrationReceipt(
                id = id,
                timestamp = System.currentTimeMillis(),
                originMangaId = origin.id,
                originSourceId = origin.source,
                targetMangaId = target.id,
                targetSourceId = target.source,
                replace = replace,
            ),
        )
        return id
    }

    private fun stubEligible(origin: Manga, target: Manga) {
        coEvery { getManga.await(origin.id) } returns origin
        coEvery { getManga.await(target.id) } returns target
        every { sourceManager.get(origin.source) } returns mockk<Source>(relaxed = true)
    }

    @Test
    fun `an event with no matching receipt gets no follow-up`() {
        bindFakes()
        NonUndoableEventJournal.record(
            NonUndoableEvent(id = NonUndoableEvent.newId(), timestamp = System.currentTimeMillis(), eventType = NonUndoableEventType.MIGRATION_COMPLETED),
        )

        val row = ActionHistoryRegistry.snapshot().first()

        assertNull(row.followUp, "no MigrationReceipt correlates with this event id -- no follow-up should be offered")
    }

    @Test
    fun `a non-migration event type never gets a migration follow-up`() {
        bindFakes()
        NonUndoableEventJournal.record(
            NonUndoableEvent(id = NonUndoableEvent.newId(), timestamp = System.currentTimeMillis(), eventType = NonUndoableEventType.EXTENSION_INSTALLED),
        )

        val row = ActionHistoryRegistry.snapshot().first()

        assertNull(row.followUp)
    }

    @Test
    fun `a fully eligible migration receipt offers a Migrate back follow-up`() {
        bindFakes()
        val origin = manga(id = 1L, sourceId = 10L, url = "/origin")
        val target = manga(id = 2L, sourceId = 20L, url = "/target", favorite = true)
        stubEligible(origin, target)
        seedReceipt(origin, target)

        val row = ActionHistoryRegistry.snapshot().first()

        assertTrue(row.followUp != null, "an eligible receipt must offer a follow-up")
    }

    // Eligibility cannot be pre-checked at snapshot() time (it's synchronous; GetManga.await is
    // suspend -- see the migrationFollowUpFor doc comment), so the follow-up is always offered once a
    // receipt exists. The four cases below assert the authoritative refusal happens inside trigger()
    // instead: Failed, and no new event/receipt recorded.

    @Test
    fun `trigger refuses and records nothing when the origin manga is no longer found`() = runTest {
        bindFakes()
        val origin = manga(id = 1L, sourceId = 10L, url = "/origin")
        val target = manga(id = 2L, sourceId = 20L, url = "/target", favorite = true)
        coEvery { getManga.await(origin.id) } returns null
        coEvery { getManga.await(target.id) } returns target
        every { sourceManager.get(origin.source) } returns mockk<Source>(relaxed = true)
        seedReceipt(origin, target)
        val followUp = ActionHistoryRegistry.snapshot().first().followUp ?: error("expected a follow-up to be offered")

        val result = followUp.trigger()

        assertEquals(ActionHistoryFollowUpResult.Failed, result)
        assertEquals(1, NonUndoableEventJournal.snapshot().count { it.eventType == NonUndoableEventType.MIGRATION_COMPLETED })
        assertEquals(1, MigrationReceiptJournal.snapshot().size)
    }

    @Test
    fun `trigger refuses and records nothing when the origin source is not installed`() = runTest {
        bindFakes()
        val origin = manga(id = 1L, sourceId = 10L, url = "/origin")
        val target = manga(id = 2L, sourceId = 20L, url = "/target", favorite = true)
        coEvery { getManga.await(origin.id) } returns origin
        coEvery { getManga.await(target.id) } returns target
        every { sourceManager.get(origin.source) } returns null
        seedReceipt(origin, target)
        val followUp = ActionHistoryRegistry.snapshot().first().followUp ?: error("expected a follow-up to be offered")

        val result = followUp.trigger()

        assertEquals(ActionHistoryFollowUpResult.Failed, result)
        assertEquals(1, NonUndoableEventJournal.snapshot().count { it.eventType == NonUndoableEventType.MIGRATION_COMPLETED })
        assertEquals(1, MigrationReceiptJournal.snapshot().size)
    }

    @Test
    fun `trigger refuses and records nothing when the target manga is no longer found`() = runTest {
        bindFakes()
        val origin = manga(id = 1L, sourceId = 10L, url = "/origin")
        val target = manga(id = 2L, sourceId = 20L, url = "/target", favorite = true)
        coEvery { getManga.await(origin.id) } returns origin
        coEvery { getManga.await(target.id) } returns null
        every { sourceManager.get(origin.source) } returns mockk<Source>(relaxed = true)
        seedReceipt(origin, target)
        val followUp = ActionHistoryRegistry.snapshot().first().followUp ?: error("expected a follow-up to be offered")

        val result = followUp.trigger()

        assertEquals(ActionHistoryFollowUpResult.Failed, result)
        assertEquals(1, NonUndoableEventJournal.snapshot().count { it.eventType == NonUndoableEventType.MIGRATION_COMPLETED })
        assertEquals(1, MigrationReceiptJournal.snapshot().size)
    }

    @Test
    fun `trigger refuses and records nothing when the origin is already favorite -- something else already changed its state`() = runTest {
        bindFakes()
        val origin = manga(id = 1L, sourceId = 10L, url = "/origin", favorite = true)
        val target = manga(id = 2L, sourceId = 20L, url = "/target", favorite = true)
        stubEligible(origin, target)
        seedReceipt(origin, target)
        val followUp = ActionHistoryRegistry.snapshot().first().followUp ?: error("expected a follow-up to be offered")

        val result = followUp.trigger()

        assertEquals(ActionHistoryFollowUpResult.Failed, result)
        assertEquals(1, NonUndoableEventJournal.snapshot().count { it.eventType == NonUndoableEventType.MIGRATION_COMPLETED })
        assertEquals(1, MigrationReceiptJournal.snapshot().size)
    }

    @Test
    fun `trigger calls MigrateMangaUseCase in the reverse direction and records a new correlated receipt on success`() = runTest {
        bindFakes()
        val origin = manga(id = 1L, sourceId = 10L, url = "/origin")
        val target = manga(id = 2L, sourceId = 20L, url = "/target", favorite = true)
        stubEligible(origin, target)
        seedReceipt(origin, target, replace = true)
        coEvery {
            migrateMangaUseCase(current = eq(target), target = eq(origin), replace = eq(true), presetFlags = any(), throttleFunc = any())
        } returns MigrationOutcome.Success(requestedFlags = emptySet(), completedFlags = emptySet(), skippedFlags = emptySet())
        val followUp = ActionHistoryRegistry.snapshot().first().followUp ?: error("expected a follow-up to be offered")

        val result = followUp.trigger()

        assertEquals(ActionHistoryFollowUpResult.Started, result)
        assertEquals(2, NonUndoableEventJournal.snapshot().count { it.eventType == NonUndoableEventType.MIGRATION_COMPLETED })
        val newReceipt = MigrationReceiptJournal.snapshot().first()
        assertEquals(target.id, newReceipt.originMangaId)
        assertEquals(origin.id, newReceipt.targetMangaId)
    }

    @Test
    fun `trigger returns Failed and records nothing on PartialFailure`() = runTest {
        bindFakes()
        val origin = manga(id = 1L, sourceId = 10L, url = "/origin")
        val target = manga(id = 2L, sourceId = 20L, url = "/target", favorite = true)
        stubEligible(origin, target)
        seedReceipt(origin, target)
        coEvery {
            migrateMangaUseCase(current = eq(target), target = eq(origin), replace = eq(false), presetFlags = any(), throttleFunc = any())
        } returns MigrationOutcome.PartialFailure(
            requestedFlags = emptySet(),
            completedFlags = emptySet(),
            skippedFlags = emptySet(),
            failedAt = null,
            finalUpdateFailed = true,
            cause = RuntimeException("boom"),
        )
        val followUp = ActionHistoryRegistry.snapshot().first().followUp ?: error("expected a follow-up to be offered")

        val result = followUp.trigger()

        assertEquals(ActionHistoryFollowUpResult.Failed, result)
        assertEquals(1, NonUndoableEventJournal.snapshot().count { it.eventType == NonUndoableEventType.MIGRATION_COMPLETED })
        assertEquals(1, MigrationReceiptJournal.snapshot().size)
    }

    @Test
    fun `trigger returns Failed and records nothing on NotStarted`() = runTest {
        bindFakes()
        val origin = manga(id = 1L, sourceId = 10L, url = "/origin")
        val target = manga(id = 2L, sourceId = 20L, url = "/target", favorite = true)
        stubEligible(origin, target)
        seedReceipt(origin, target)
        coEvery {
            migrateMangaUseCase(current = eq(target), target = eq(origin), replace = eq(false), presetFlags = any(), throttleFunc = any())
        } returns MigrationOutcome.NotStarted(cause = null)
        val followUp = ActionHistoryRegistry.snapshot().first().followUp ?: error("expected a follow-up to be offered")

        val result = followUp.trigger()

        assertEquals(ActionHistoryFollowUpResult.Failed, result)
        assertEquals(1, NonUndoableEventJournal.snapshot().count { it.eventType == NonUndoableEventType.MIGRATION_COMPLETED })
        assertEquals(1, MigrationReceiptJournal.snapshot().size)
    }

    @Test
    fun `trigger records nothing when Evaluation Mode is off, even on a successful migration`() = runTest {
        bindFakes()
        sourcePreferences.evaluationMode().set(false)
        val origin = manga(id = 1L, sourceId = 10L, url = "/origin")
        val target = manga(id = 2L, sourceId = 20L, url = "/target", favorite = true)
        stubEligible(origin, target)
        seedReceipt(origin, target)
        coEvery {
            migrateMangaUseCase(current = eq(target), target = eq(origin), replace = eq(false), presetFlags = any(), throttleFunc = any())
        } returns MigrationOutcome.Success(requestedFlags = emptySet(), completedFlags = emptySet(), skippedFlags = emptySet())
        val followUp = ActionHistoryRegistry.snapshot().first().followUp ?: error("expected a follow-up to be offered")

        val result = followUp.trigger()

        assertEquals(ActionHistoryFollowUpResult.Started, result, "the migration itself must still truthfully report success")
        assertEquals(1, NonUndoableEventJournal.snapshot().count { it.eventType == NonUndoableEventType.MIGRATION_COMPLETED })
        assertEquals(1, MigrationReceiptJournal.snapshot().size)
    }

    @Test
    fun `trigger re-resolves eligibility fresh -- a conflict that appeared since rendering is still caught`() = runTest {
        bindFakes()
        val origin = manga(id = 1L, sourceId = 10L, url = "/origin")
        val target = manga(id = 2L, sourceId = 20L, url = "/target", favorite = true)
        stubEligible(origin, target)
        seedReceipt(origin, target)
        val followUp = ActionHistoryRegistry.snapshot().first().followUp ?: error("expected a follow-up to be offered")
        // Simulates the origin becoming favorited again through some other path between render and trigger.
        coEvery { getManga.await(origin.id) } returns origin.copy(favorite = true)

        val result = followUp.trigger()

        assertEquals(ActionHistoryFollowUpResult.Failed, result)
        assertEquals(1, NonUndoableEventJournal.snapshot().count { it.eventType == NonUndoableEventType.MIGRATION_COMPLETED })
    }

    @Test
    fun `a repeated trigger after a successful back-migration offers a new eligible follow-up in turn`() = runTest {
        bindFakes()
        val origin = manga(id = 1L, sourceId = 10L, url = "/origin")
        val target = manga(id = 2L, sourceId = 20L, url = "/target", favorite = true)
        stubEligible(origin, target)
        seedReceipt(origin, target)
        coEvery {
            migrateMangaUseCase(current = eq(target), target = eq(origin), replace = eq(false), presetFlags = any(), throttleFunc = any())
        } returns MigrationOutcome.Success(requestedFlags = emptySet(), completedFlags = emptySet(), skippedFlags = emptySet())
        val firstFollowUp = ActionHistoryRegistry.snapshot().first().followUp ?: error("expected a follow-up to be offered")

        firstFollowUp.trigger()
        // The new correlated pair swapped origin/target -- to offer a symmetric back-again follow-up,
        // the (now-reversed) origin must still resolve as not-favorite and its source installed.
        coEvery { getManga.await(target.id) } returns target.copy(favorite = false)
        every { sourceManager.get(target.source) } returns mockk<Source>(relaxed = true)

        val newRow = ActionHistoryRegistry.snapshot().first()

        assertEquals(NonUndoableEventType.MIGRATION_COMPLETED, newRow.let { NonUndoableEventJournal.snapshot().first().eventType })
        assertTrue(newRow.followUp != null, "the freshly recorded back-migration receipt must itself be eligible for a further follow-up")
    }

    @Test
    fun `CancellationException from GetManga during trigger propagates instead of being swallowed as Failed`() = runTest {
        bindFakes()
        val origin = manga(id = 1L, sourceId = 10L, url = "/origin")
        val target = manga(id = 2L, sourceId = 20L, url = "/target", favorite = true)
        stubEligible(origin, target)
        seedReceipt(origin, target)
        val followUp = ActionHistoryRegistry.snapshot().first().followUp ?: error("expected a follow-up to be offered")
        coEvery { getManga.await(origin.id) } throws CancellationException("cancelled")

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { followUp.trigger() }
        }
    }
}
// KMK <--

package exh.recs.matching

import exh.util.CrossSourceIdentityUndoJournal
import exh.util.CrossSourceIdentityUndoService
import exh.util.FakeTasteRepository
import exh.util.GroupUndoResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.interactor.GetCrossSourceIdentityDecisions
import tachiyomi.domain.taste.interactor.ReplaceCrossSourceIdentityDecisions
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionPolicy
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionValue
import tachiyomi.domain.taste.model.CrossSourceRecordKey

class CrossSourceIdentityDecisionControllerTest {
    private val repository = FakeTasteRepository()
    private val get = GetCrossSourceIdentityDecisions(repository)
    private val replace = ReplaceCrossSourceIdentityDecisions(repository)
    private var now = 10_000L
    private val controller = CrossSourceIdentityDecisionController(get, replace) { now++ }

    @AfterEach
    fun tearDown() {
        CrossSourceIdentityUndoJournal.clear()
    }

    @Test
    fun `confirm writes current authoritative decision and records after success`() = runTest {
        val pair = pair(1, "/a", 2, "/b")
        assertEquals(CrossSourceIdentityMutationResult.APPLIED, controller.mutate(pair, CrossSourceIdentityMutation.CONFIRM))
        val stored = get.await(pair)
        assertTrue(CrossSourceIdentityDecisionPolicy.isAuthoritativeConfirmation(stored))
        assertEquals(1, CrossSourceIdentityUndoJournal.snapshot().size)
    }

    @Test
    fun `rejection is current suppression and deselection has no implicit write`() = runTest {
        val pair = pair(1, "/a", 2, "/b")
        assertNull(get.await(pair))
        assertTrue(CrossSourceIdentityUndoJournal.isEmpty())
        assertEquals(CrossSourceIdentityMutationResult.APPLIED, controller.mutate(pair, CrossSourceIdentityMutation.REJECT))
        assertTrue(CrossSourceIdentityDecisionPolicy.isCurrentRejection(get.await(pair)))
    }

    @Test
    fun `multi-pair confirm is one atomic journal entry and one undo`() = runTest {
        val pairs = listOf(pair(1, "/a", 2, "/b"), pair(1, "/a", 3, "/c"))
        assertEquals(CrossSourceIdentityMutationResult.APPLIED, controller.mutateAll(pairs, CrossSourceIdentityMutation.CONFIRM))
        assertEquals(2, get.awaitAll().size)
        val entry = CrossSourceIdentityUndoJournal.snapshot().single()
        assertEquals(2, entry.replacements.size)
        assertEquals(GroupUndoResult.RESTORED, CrossSourceIdentityUndoService(replace).undo(entry.id))
        assertTrue(get.awaitAll().isEmpty())
        assertTrue(CrossSourceIdentityUndoJournal.isEmpty())
    }

    @Test
    fun `undo refuses to overwrite a later decision`() = runTest {
        val pair = pair(1, "/a", 2, "/b")
        controller.mutate(pair, CrossSourceIdentityMutation.CONFIRM)
        val entry = CrossSourceIdentityUndoJournal.snapshot().single()
        controller.mutate(pair, CrossSourceIdentityMutation.REJECT)
        assertEquals(GroupUndoResult.CONFLICT, CrossSourceIdentityUndoService(replace).undo(entry.id))
        assertTrue(CrossSourceIdentityDecisionPolicy.isCurrentRejection(get.await(pair)))
        assertFalse(CrossSourceIdentityUndoJournal.isEmpty())
    }

    @Test
    fun `clear creates syncable tombstone and undo restores exact prior state`() = runTest {
        val pair = pair(1, "/a", 2, "/b")
        controller.mutate(pair, CrossSourceIdentityMutation.CONFIRM)
        CrossSourceIdentityUndoJournal.clear()
        assertEquals(CrossSourceIdentityMutationResult.APPLIED, controller.mutate(pair, CrossSourceIdentityMutation.CLEAR))
        val tombstone = get.await(pair)
        assertTrue(tombstone?.deletedAt != null)
        val clearEntry = CrossSourceIdentityUndoJournal.snapshot().single()
        assertEquals(GroupUndoResult.RESTORED, CrossSourceIdentityUndoService(replace).undo(clearEntry.id))
        assertEquals(CrossSourceIdentityDecisionValue.USER_CONFIRMED, get.await(pair)?.decision)
    }

    @Test
    fun `cancellation propagates without a write or journal entry`() {
        repository.transactionFailure = CancellationException("cancelled")

        assertThrows(CancellationException::class.java) {
            runBlocking { controller.mutate(pair(1, "/a", 2, "/b"), CrossSourceIdentityMutation.CONFIRM) }
        }
        assertTrue(runBlocking { get.awaitAll() }.isEmpty())
        assertTrue(CrossSourceIdentityUndoJournal.isEmpty())
    }

    @Test
    fun `ordinary replacement failure reports failure without a write or journal entry`() = runTest {
        repository.transactionFailure = IllegalStateException("database unavailable")

        assertEquals(
            CrossSourceIdentityMutationResult.FAILED,
            controller.mutate(pair(1, "/a", 2, "/b"), CrossSourceIdentityMutation.CONFIRM),
        )
        assertTrue(get.awaitAll().isEmpty())
        assertTrue(CrossSourceIdentityUndoJournal.isEmpty())
    }

    private fun pair(firstSource: Long, firstUrl: String, secondSource: Long, secondUrl: String) =
        CrossSourceIdentityDecisionPolicy.canonicalPair(
            CrossSourceRecordKey(firstSource, firstUrl),
            CrossSourceRecordKey(secondSource, secondUrl),
        )
}

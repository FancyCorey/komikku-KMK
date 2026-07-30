package exh.util

import eu.kanade.domain.source.service.SourcePreferences
import exh.recs.loved.RatedGroupMergePlanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.interactor.DeleteCrossSourceGroupCompletely
import tachiyomi.domain.taste.interactor.DeleteCrossSourceMangaLink
import tachiyomi.domain.taste.interactor.GetCrossSourceGroupPrimary
import tachiyomi.domain.taste.interactor.GetCrossSourceMangaLinks
import tachiyomi.domain.taste.interactor.RestoreCrossSourceGroupState
import tachiyomi.domain.taste.interactor.UpsertCrossSourceMangaLinks
import tachiyomi.domain.taste.model.CrossSourceGroupPrimary
import tachiyomi.domain.taste.model.CrossSourceMangaLink

// KMK v0.8.20 -->
/**
 * Interactor-level coverage for [GroupUndoService.undo] (merge/remove-from-group/ungroup restore) and
 * [GroupUndoRecorder]'s snapshot-building contract, using [FakePreferenceStore]/[FakeTasteRepository]
 * instead of a database -- same pattern as [EvaluationModeUndoServiceRestoreTest].
 */
class GroupUndoServiceRestoreTest {

    private val preferenceStore = FakePreferenceStore()
    private val sourcePreferences = SourcePreferences(preferenceStore)
    private val repo = FakeTasteRepository()
    private val getCrossSourceMangaLinks = GetCrossSourceMangaLinks(repo)
    private val upsertCrossSourceMangaLinks = UpsertCrossSourceMangaLinks(repo)
    private val deleteCrossSourceMangaLink = DeleteCrossSourceMangaLink(repo)
    private val deleteCrossSourceGroupCompletely = DeleteCrossSourceGroupCompletely(repo)
    private val getCrossSourceGroupPrimary = GetCrossSourceGroupPrimary(repo)
    private val restoreCrossSourceGroupState = RestoreCrossSourceGroupState(repo)
    private val service = GroupUndoService(getCrossSourceMangaLinks, getCrossSourceGroupPrimary, restoreCrossSourceGroupState)

    @AfterEach
    fun tearDown() {
        GroupUndoJournal.clear()
        preferenceStore.getBoolean("evaluation_mode", false).delete()
    }

    private fun enableEvaluationMode() {
        sourcePreferences.evaluationMode().set(true)
    }

    private fun link(source: Long, url: String, groupId: String) =
        CrossSourceMangaLink(source = source, url = url, groupId = groupId, title = "t-$url", createdAt = 0L, updatedAt = 0L)

    @Test
    fun `merge is only recorded after the write succeeds, then undo restores the pre-merge ungrouped state`() = runTest {
        enableEvaluationMode()
        val plan = RatedGroupMergePlanner.MergePlan(
            targetGroupId = "g-new",
            isNewGroup = true,
            mergedGroupIds = emptySet(),
            writes = listOf(link(1L, "/a", "g-new"), link(1L, "/b", "g-new")),
        )
        val undoEntry = GroupUndoRecorder.buildMergeEntry(sourcePreferences, plan, existingGroupMembers = emptyMap())
        assertNotNull(undoEntry)
        // Build-before-write: nothing committed to the journal yet.
        assertTrue(GroupUndoJournal.isEmpty())

        upsertCrossSourceMangaLinks.await(plan.writes)
        GroupUndoJournal.record(undoEntry!!)
        assertEquals(1, GroupUndoJournal.snapshot().size)

        val outcome = service.undo(undoEntry.id)
        assertEquals(GroupUndoResult.RESTORED, outcome.result)
        assertNull(getCrossSourceMangaLinks.awaitBySourceUrl(1L, "/a"))
        assertNull(getCrossSourceMangaLinks.awaitBySourceUrl(1L, "/b"))
        assertTrue(GroupUndoJournal.isEmpty())
    }

    @Test
    fun `remove-from-group undo restores the previous group membership`() = runTest {
        enableEvaluationMode()
        upsertCrossSourceMangaLinks.await(listOf(link(1L, "/a", "g1")))
        val previous = getCrossSourceMangaLinks.awaitBySourceUrl(1L, "/a")!!
        deleteCrossSourceMangaLink.awaitBySourceUrl(1L, "/a")
        val undoEntry = GroupUndoRecorder.buildRemoveFromGroupEntry(sourcePreferences, listOf(RatedLinkKey(1L, "/a") to previous))
        assertNotNull(undoEntry)
        GroupUndoJournal.record(undoEntry!!)

        val outcome = service.undo(undoEntry.id)
        assertEquals(GroupUndoResult.RESTORED, outcome.result)
        assertEquals("g1", getCrossSourceMangaLinks.awaitBySourceUrl(1L, "/a")?.groupId)
    }

    @Test
    fun `ungroup undo restores every link and the primary version atomically`() = runTest {
        enableEvaluationMode()
        upsertCrossSourceMangaLinks.await(listOf(link(1L, "/a", "g1"), link(1L, "/b", "g1")))
        repo.upsertCrossSourceGroupPrimary(CrossSourceGroupPrimary("g1", 1L, "/a", 0L))
        val previousLinks = getCrossSourceMangaLinks.awaitByGroupId("g1")
        val previousPrimary = getCrossSourceGroupPrimary.awaitByGroupId("g1")

        deleteCrossSourceGroupCompletely.await("g1")
        assertTrue(getCrossSourceMangaLinks.awaitByGroupId("g1").isEmpty())
        assertNull(getCrossSourceGroupPrimary.awaitByGroupId("g1"))

        val undoEntry = GroupUndoRecorder.buildUngroupEntry(sourcePreferences, "g1", previousLinks, previousPrimary)
        assertNotNull(undoEntry)
        GroupUndoJournal.record(undoEntry!!)

        val outcome = service.undo(undoEntry.id)
        assertEquals(GroupUndoResult.RESTORED, outcome.result)
        assertEquals(2, getCrossSourceMangaLinks.awaitByGroupId("g1").size)
        assertEquals("/a", getCrossSourceGroupPrimary.awaitByGroupId("g1")?.url)
    }

    @Test
    fun `undo refuses to restore and leaves state untouched when the grouping changed after journaling`() = runTest {
        enableEvaluationMode()
        upsertCrossSourceMangaLinks.await(listOf(link(1L, "/a", "g1")))
        val previous = getCrossSourceMangaLinks.awaitBySourceUrl(1L, "/a")!!
        deleteCrossSourceMangaLink.awaitBySourceUrl(1L, "/a")
        val undoEntry = GroupUndoRecorder.buildRemoveFromGroupEntry(sourcePreferences, listOf(RatedLinkKey(1L, "/a") to previous))!!
        GroupUndoJournal.record(undoEntry)

        // Someone (or another undo) re-grouped this manga into a different group after journaling.
        upsertCrossSourceMangaLinks.await(listOf(link(1L, "/a", "g2")))

        val outcome = service.undo(undoEntry.id)
        assertEquals(GroupUndoResult.CONFLICT, outcome.result)
        assertEquals("g2", getCrossSourceMangaLinks.awaitBySourceUrl(1L, "/a")?.groupId)
        // Conflicting entry stays in the journal, not silently dropped.
        assertEquals(1, GroupUndoJournal.snapshot().size)
    }

    @Test
    fun `a failed restore transaction changes nothing and keeps the entry for retry`() = runTest {
        enableEvaluationMode()
        upsertCrossSourceMangaLinks.await(listOf(link(1L, "/a", "g1")))
        val previous = getCrossSourceMangaLinks.awaitBySourceUrl(1L, "/a")!!
        deleteCrossSourceMangaLink.awaitBySourceUrl(1L, "/a")
        val undoEntry = GroupUndoRecorder.buildRemoveFromGroupEntry(sourcePreferences, listOf(RatedLinkKey(1L, "/a") to previous))!!
        GroupUndoJournal.record(undoEntry)

        repo.transactionFailure = RuntimeException("simulated transaction failure")
        val outcome = service.undo(undoEntry.id)

        assertEquals(GroupUndoResult.FAILED, outcome.result)
        assertNull(getCrossSourceMangaLinks.awaitBySourceUrl(1L, "/a")) // nothing was partially restored
        assertEquals(1, GroupUndoJournal.snapshot().size) // entry survives for a retry
    }

    @Test
    fun `cancellation is propagated, never swallowed as a failure`() = runTest {
        enableEvaluationMode()
        upsertCrossSourceMangaLinks.await(listOf(link(1L, "/a", "g1")))
        val previous = getCrossSourceMangaLinks.awaitBySourceUrl(1L, "/a")!!
        deleteCrossSourceMangaLink.awaitBySourceUrl(1L, "/a")
        val undoEntry = GroupUndoRecorder.buildRemoveFromGroupEntry(sourcePreferences, listOf(RatedLinkKey(1L, "/a") to previous))!!
        GroupUndoJournal.record(undoEntry)

        repo.transactionFailure = CancellationException("cancelled")
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { service.undo(undoEntry.id) }
        }
    }

    @Test
    fun `remove-from-group snapshot only captures items that actually succeeded, not the full original selection`() {
        enableEvaluationMode()
        // Simulates LovedMangaScreenModel.removeSelectedFromGroup(): of 2 selected items, only 1
        // delete succeeded -- the recorder must only snapshot the one that actually changed.
        val onlySucceeded = listOf(RatedLinkKey(1L, "/a") to link(1L, "/a", "g1"))
        val entry = GroupUndoRecorder.buildRemoveFromGroupEntry(sourcePreferences, onlySucceeded)
        assertNotNull(entry)
        assertEquals(setOf(RatedLinkKey(1L, "/a")), entry!!.touchedKeys)
    }

    @Test
    fun `no journal entry is built when Evaluation Mode is disabled`() {
        sourcePreferences.evaluationMode().set(false)
        val plan = RatedGroupMergePlanner.MergePlan("g1", true, emptySet(), listOf(link(1L, "/a", "g1")))
        assertNull(GroupUndoRecorder.buildMergeEntry(sourcePreferences, plan, emptyMap()))
        assertNull(GroupUndoRecorder.buildRemoveFromGroupEntry(sourcePreferences, listOf(RatedLinkKey(1L, "/a") to link(1L, "/a", "g1"))))
        assertNull(GroupUndoRecorder.buildUngroupEntry(sourcePreferences, "g1", listOf(link(1L, "/a", "g1")), null))
    }
}
// KMK <--

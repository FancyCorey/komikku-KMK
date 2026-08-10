package exh.util

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK Confirmed Blocker Remediation Corrective Completion Plan V2 2026-07-29 -->
/**
 * Direct tests for the [ActionHistoryRegistry] mechanism introduced this pass to replace
 * `EvaluationModeActionHistoryScreen`'s previous pattern of enumerating all 7 journal families by
 * name in two separate, hand-kept-in-sync places (row building and Clear All). The pure merge/clear
 * logic ([mergeHistorySources]/[clearHistorySources]) is tested here against small fake
 * [ActionHistorySource] implementations rather than the real 7 journals, since
 * [ActionHistoryRegistry.sources] is a fixed list of private adapter objects that can't be swapped
 * out for a test double -- the fakes prove the registry's own merge/sort/clear-all mechanism is
 * correct, independent of any single journal's implementation. [wiring real journals reach
 * ActionHistoryRegistry.clearAll] then separately proves the real, hardcoded [ActionHistoryRegistry]
 * actually reaches the real journals, not just that the abstract mechanism works.
 */
class ActionHistoryRegistryTest {

    @AfterEach
    fun tearDown() {
        ActionHistoryRegistry.clearAll()
    }

    private class FakeSource(
        private var entries: List<ActionHistoryEntryDescriptor>,
        override val familyId: String = "fake",
    ) : ActionHistorySource {
        var cleared = false
            private set

        override fun snapshot(): List<ActionHistoryEntryDescriptor> = entries
        override fun clear() {
            cleared = true
            entries = emptyList()
        }
    }

    private fun descriptor(id: String, timestamp: Long, undoable: Boolean = true) = ActionHistoryEntryDescriptor(
        id = id,
        timestamp = timestamp,
        summary = { "summary-$id" },
        undo = if (undoable) {
            { ActionHistoryUndoResult.Simple(GroupUndoResult.RESTORED) }
        } else {
            null
        },
    )

    @Test
    fun `ActionHistoryRegistry registers exactly the 7 known journal families by identity, with no duplicates or omissions`() {
        // KMK Confirmed Blocker Remediation Corrective Completion Plan V3 2026-07-29 Phase C (gap
        // closure): the prior version of this test only asserted `sources.size == 7`, which cannot
        // distinguish "7 correct, distinct families" from e.g. "the taste adapter registered twice and
        // the chapter adapter is missing" -- both produce a count of 7. `ActionHistorySource.familyId`
        // gives every adapter a stable identity a test can actually assert against, even though the
        // adapter objects themselves are private and can't be referenced or `is`-checked from this test
        // module.
        //
        // ActionHistoryRegistry.kt's registration mechanism was further strengthened after this test was
        // first written: `allActionHistorySources()` now derives its list directly from a private
        // `JournalFamily` enum's own `entries` via an exhaustive, no-`else` `when` (`sourceForFamily()`)
        // -- there is no longer a second, separately hand-kept list literal an already-declared-and-
        // `when`-matched adapter could be left out of. See that file's `sourceForFamily()` doc comment
        // for the precise, honestly-stated boundary of what this now does and does not guarantee (in
        // short: an already-declared family can no longer be silently un-registered; a brand-new family
        // that never touches the `JournalFamily` mechanism at all is still a documentation/review
        // concern, not something compile-time or runtime checks here can catch). This test's own
        // assertions -- the exact expected family-id set, and no duplicates -- remain the right runtime
        // check for that mechanism's actual output.
        val familyIds = ActionHistoryRegistry.sources.map { it.familyId }
        assertEquals(
            setOf("taste", "group", "library", "preference", "chapter", "cover", "nonundoable"),
            familyIds.toSet(),
        )
        assertEquals(familyIds.size, familyIds.distinct().size, "no two registered families may share a familyId")
        assertEquals(7, ActionHistoryRegistry.sources.size)
    }

    @Test
    fun `mergeHistorySources combines entries from every source`() {
        val a = FakeSource(listOf(descriptor("a1", timestamp = 100L)))
        val b = FakeSource(listOf(descriptor("b1", timestamp = 200L)))

        val merged = mergeHistorySources(listOf(a, b))

        assertEquals(setOf("a1", "b1"), merged.map { it.id }.toSet())
    }

    @Test
    fun `mergeHistorySources sorts every source's entries together, most recent first`() {
        val a = FakeSource(listOf(descriptor("old", timestamp = 100L), descriptor("newest", timestamp = 500L)))
        val b = FakeSource(listOf(descriptor("middle", timestamp = 300L)))

        val merged = mergeHistorySources(listOf(a, b))

        assertEquals(listOf("newest", "middle", "old"), merged.map { it.id })
    }

    @Test
    fun `a source with no entries contributes nothing to the merged result`() {
        val empty = FakeSource(emptyList())
        val nonEmpty = FakeSource(listOf(descriptor("only", timestamp = 1L)))

        val merged = mergeHistorySources(listOf(empty, nonEmpty))

        assertEquals(listOf("only"), merged.map { it.id })
    }

    @Test
    fun `clearHistorySources clears every registered source, not just some`() {
        val a = FakeSource(listOf(descriptor("a1", timestamp = 1L)))
        val b = FakeSource(listOf(descriptor("b1", timestamp = 2L)))
        val c = FakeSource(emptyList())

        clearHistorySources(listOf(a, b, c))

        assertTrue(a.cleared)
        assertTrue(b.cleared)
        assertTrue(c.cleared, "a source with nothing to clear must still receive the clear() call")
        assertTrue(mergeHistorySources(listOf(a, b, c)).isEmpty())
    }

    @Test
    fun `undo-eligible and non-undoable entries both pass through the merge unchanged`() {
        val source = FakeSource(
            listOf(
                descriptor("undoable", timestamp = 1L, undoable = true),
                descriptor("non-undoable", timestamp = 2L, undoable = false),
            ),
        )

        val merged = mergeHistorySources(listOf(source))

        assertNotNull(merged.first { it.id == "non-undoable" }.let { it }, "sanity: entry exists")
        assertNull(merged.first { it.id == "non-undoable" }.undo, "a non-undoable entry must render without an Undo action")
        assertNotNull(merged.first { it.id == "undoable" }.undo, "an undoable entry must carry a real undo callback")
    }

    @Test
    fun `Source Evaluation management event is visible without an Undo action`() {
        NonUndoableEventJournal.record(
            NonUndoableEvent(
                id = NonUndoableEvent.newId(),
                timestamp = 10L,
                eventType = NonUndoableEventType.SOURCE_EVALUATION_DATA_CLEARED,
            ),
        )

        val descriptor = ActionHistoryRegistry.snapshot().single()

        assertNull(descriptor.undo)
    }

    @Test
    fun `wiring real journals reach ActionHistoryRegistry clearAll`() = runTest {
        // Seed real entries in 3 of the 7 real journals (mirroring the construction patterns already
        // used in their own dedicated *JournalTest.kt files) to prove the real, hardcoded
        // ActionHistoryRegistry actually reaches real production journals, not just the fakes above.
        EvaluationModeUndoJournal.record(
            EvaluationJournalEntry(
                id = EvaluationJournalEntry.newId(),
                timestamp = System.currentTimeMillis(),
                actionType = EvaluationJournalActionType.RATE_LOVE,
                mangaId = 1L,
                source = 10L,
                url = "/manga/1",
                previousRating = null,
                newRating = 2,
                previousNotInterested = false,
                newNotInterested = false,
                isBulk = false,
                bulkOperationId = null,
                changedFields = setOf(EvaluationJournalEntry.FIELD_RATING),
            ),
        )
        NonUndoableEventJournal.record(
            NonUndoableEvent(
                id = NonUndoableEvent.newId(),
                timestamp = System.currentTimeMillis(),
                eventType = NonUndoableEventType.EXTENSION_INSTALLED,
            ),
        )
        ChapterUndoJournal.record(
            ChapterJournalEntry(
                id = ChapterJournalEntry.newId(),
                timestamp = System.currentTimeMillis(),
                actionType = ChapterJournalActionType.READ,
                chapterId = 1L,
                previousRead = false,
                expectedPostRead = true,
                previousBookmark = false,
                expectedPostBookmark = false,
            ),
        )

        assertTrue(ActionHistoryRegistry.snapshot().isNotEmpty(), "sanity: the seeded entries must actually appear via the registry")

        ActionHistoryRegistry.clearAll()

        assertTrue(EvaluationModeUndoJournal.isEmpty())
        assertTrue(NonUndoableEventJournal.isEmpty())
        assertTrue(ChapterUndoJournal.isEmpty())
        assertTrue(ActionHistoryRegistry.snapshot().isEmpty())
    }
}
// KMK <--

package exh.util

import exh.recs.sourceprefs.SourceQualityMarkPolicy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// KMK Undo Expansion Phase 3 -->
/**
 * Covers the composite source-quality-mark restore shape used by
 * `RecommendationsSettingsScreenModel`'s `journalSourceQualityChange()`: liked/disliked/explicit key
 * sets are journaled and restored together as one [SourceQualityMarkPolicy.State], through the same
 * generic [PreferenceUndoService] every other Phase 1/3 preference uses.
 */
class SourceQualityMarkUndoTest {

    private val service = PreferenceUndoService()

    @AfterEach
    fun tearDown() {
        PreferenceUndoJournal.clear()
    }

    @Test
    fun `undo restores the complete previous liked-disliked-explicit state`() = runTest {
        var live = SourceQualityMarkPolicy.State(liked = emptySet(), disliked = emptySet(), explicit = emptySet())
        val previous = live
        val next = SourceQualityMarkPolicy.markPoor(previous, "installed:1")
        live = next

        val entry = PreferenceUndoEntry(
            id = PreferenceUndoEntry.newId(),
            timestamp = 0L,
            actionType = PreferenceJournalActionType.SOURCE_QUALITY_MARK,
            identityKey = "installed:1",
            previousValue = previous,
            expectedPostValue = next,
            readCurrent = { live },
            restore = { live = it },
        )
        PreferenceUndoJournal.record(entry)

        val outcome = service.undo(entry.id)

        assertEquals(GroupUndoResult.RESTORED, outcome)
        assertEquals(previous, live)
        assertEquals(emptySet<String>(), live.disliked)
    }

    @Test
    fun `undo refuses when the mark set changed after journaling (an unrelated key was also marked)`() = runTest {
        val previous = SourceQualityMarkPolicy.State(liked = emptySet(), disliked = emptySet(), explicit = emptySet())
        val expectedPost = SourceQualityMarkPolicy.markPoor(previous, "installed:1")
        var live = SourceQualityMarkPolicy.markPoor(expectedPost, "installed:2") // diverged

        val entry = PreferenceUndoEntry(
            id = PreferenceUndoEntry.newId(),
            timestamp = 0L,
            actionType = PreferenceJournalActionType.SOURCE_QUALITY_MARK,
            identityKey = "installed:1",
            previousValue = previous,
            expectedPostValue = expectedPost,
            readCurrent = { live },
            restore = { live = it },
        )
        PreferenceUndoJournal.record(entry)

        val outcome = service.undo(entry.id)

        assertEquals(GroupUndoResult.CONFLICT, outcome)
        assertEquals(setOf("installed:1", "installed:2"), live.disliked)
    }

    @Test
    fun `clear-all is restorable as one composite entry`() = runTest {
        val previous = SourceQualityMarkPolicy.State(liked = setOf("a"), disliked = setOf("b"), explicit = setOf("c"))
        var live = SourceQualityMarkPolicy.State(emptySet(), emptySet(), emptySet())

        val entry = PreferenceUndoEntry(
            id = PreferenceUndoEntry.newId(),
            timestamp = 0L,
            actionType = PreferenceJournalActionType.SOURCE_QUALITY_CLEAR_ALL,
            identityKey = "all",
            previousValue = previous,
            expectedPostValue = live,
            readCurrent = { live },
            restore = { live = it },
        )
        PreferenceUndoJournal.record(entry)

        assertEquals(GroupUndoResult.RESTORED, service.undo(entry.id))
        assertEquals(previous, live)
    }
}
// KMK <--

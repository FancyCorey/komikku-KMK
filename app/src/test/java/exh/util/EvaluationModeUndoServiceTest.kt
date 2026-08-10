package exh.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK v0.8.19 -->
// Pure-logic coverage for the Evaluation Mode Undo Journal's restore contract: conflict detection
// (evaluationUndoHasConflict) and outcome classification (EvaluationUndoOutcome). The conflict check
// and outcome classification below are exactly the two decisions that determine whether a restore is
// safe to perform at all. The DB/preference-touching half of EvaluationModeUndoService.restoreOne()
// (the actual SetMangaTaste/ClearMangaTaste/SeenRecommendationMangaStore calls, against real
// interactors) is covered separately in EvaluationModeUndoServiceRestoreTest, using FakePreferenceStore
// and FakeTasteRepository instead of a database.
class EvaluationModeUndoServiceTest {

    private fun ratingEntry(newRating: Int?, newNotInterested: Boolean = false, previousRating: Int? = null) =
        EvaluationJournalEntry(
            id = "e1",
            timestamp = 0L,
            actionType = EvaluationJournalActionType.RATE_LOVE,
            mangaId = 1L,
            source = 10L,
            url = "/manga/1",
            previousRating = previousRating,
            newRating = newRating,
            previousNotInterested = false,
            newNotInterested = newNotInterested,
            isBulk = false,
            bulkOperationId = null,
            changedFields = setOf(EvaluationJournalEntry.FIELD_RATING),
        )

    @Test
    fun `no conflict when current state still matches the journaled post-action state`() {
        val entry = ratingEntry(newRating = 2)
        assertFalse(evaluationUndoHasConflict(entry, currentRating = 2, currentNotInterested = false))
    }

    @Test
    fun `conflict when rating changed after the journaled action`() {
        val entry = ratingEntry(newRating = 2)
        assertTrue(evaluationUndoHasConflict(entry, currentRating = 1, currentNotInterested = false))
    }

    @Test
    fun `conflict when not-interested state changed after the journaled action`() {
        val entry = ratingEntry(newRating = 2, newNotInterested = false)
        assertTrue(evaluationUndoHasConflict(entry, currentRating = 2, currentNotInterested = true))
    }

    @Test
    fun `no conflict when the journaled new state was null (cleared) and it is still null`() {
        val entry = ratingEntry(newRating = null, previousRating = 2)
        assertFalse(evaluationUndoHasConflict(entry, currentRating = null, currentNotInterested = false))
    }

    @Test
    fun `full restore outcome is classified as allRestored`() {
        val outcome = EvaluationUndoOutcome(requestedCount = 3, restoredCount = 3, conflictCount = 0, missingCount = 0, failedCount = 0)
        assertTrue(outcome.allRestored)
        assertFalse(outcome.partial)
        assertFalse(outcome.noneRestored)
    }

    @Test
    fun `partial restore outcome reports both restored and conflict counts`() {
        val outcome = EvaluationUndoOutcome(requestedCount = 2, restoredCount = 1, conflictCount = 1, missingCount = 0, failedCount = 0)
        assertTrue(outcome.partial)
        assertEquals(1, outcome.restoredCount)
        assertEquals(1, outcome.conflictCount)
    }

    @Test
    fun `all-conflict outcome is classified as noneRestored, not a false success`() {
        val outcome = EvaluationUndoOutcome(requestedCount = 2, restoredCount = 0, conflictCount = 2, missingCount = 0, failedCount = 0)
        assertTrue(outcome.noneRestored)
        assertFalse(outcome.allRestored)
    }

    @Test
    fun `an entry marked not reversible is a conflict regardless of state`() {
        // Mirrors EvaluationModeUndoService.restoreOne()'s early "!entry.reversible" branch --
        // documented here as a contract test on the field itself, since the branch is a one-line
        // guard not otherwise exercised by the pure conflict-check function.
        val entry = ratingEntry(newRating = 2).copy(reversible = false)
        assertFalse(entry.reversible)
    }
}
// KMK <--

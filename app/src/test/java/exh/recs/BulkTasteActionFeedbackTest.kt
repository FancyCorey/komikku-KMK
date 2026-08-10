package exh.recs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.MangaRating

// KMK v0.8.19 -->
// Bulk-action result feedback pass: pure-model coverage for BulkTasteOutcome's classification
// logic and the action-type mapping. The message-text-building half of BulkTasteActionFeedback.kt
// (bulkTasteActionMessage()) requires an Android Context to resolve string resources and is not
// covered by this JVM unit-test module -- consistent with this codebase's existing pattern of
// keeping Context-dependent string resolution untested at the unit level (e.g. showBulkActionFeedback
// in BrowsePersonalRecommendationsTab.kt was never unit-tested either) and covered instead by the
// classification/outcome logic feeding it, which is what actually decides *which* message template
// is chosen.
class BulkTasteActionFeedbackTest {

    @Test
    fun `single item success is not a no-op and is not a failure`() {
        val outcome = BulkTasteOutcome.success(1)
        assertFalse(outcome.isNoOp)
        assertTrue(outcome.allSuccess)
        assertFalse(outcome.partialFailure)
        assertFalse(outcome.allFailed)
    }

    @Test
    fun `multiple item success reports the full requested count as successful`() {
        val outcome = BulkTasteOutcome.success(5)
        assertEquals(5, outcome.requestedCount)
        assertEquals(5, outcome.successCount)
        assertEquals(0, outcome.failureCount)
        assertTrue(outcome.allSuccess)
    }

    @Test
    fun `partial success is neither allSuccess nor allFailed`() {
        val outcome = BulkTasteOutcome(requestedCount = 4, successCount = 3, failureCount = 1)
        assertTrue(outcome.partialFailure)
        assertFalse(outcome.allSuccess)
        assertFalse(outcome.allFailed)
    }

    @Test
    fun `complete failure is allFailed and not partial`() {
        val outcome = BulkTasteOutcome.failed(3)
        assertTrue(outcome.allFailed)
        assertFalse(outcome.partialFailure)
        assertFalse(outcome.allSuccess)
    }

    @Test
    fun `complete skip with zero requested is a no-op`() {
        val outcome = BulkTasteOutcome(requestedCount = 0, successCount = 0, failureCount = 0)
        assertTrue(outcome.isNoOp)
    }

    @Test
    fun `all items skipped is reported as a failure result, not a false success`() {
        // Per spec: "If all selected items are skipped or fail, show a failure/non-action result
        // instead of a success message" -- a fully-skipped batch must NOT be silent (isNoOp) and
        // must NOT read as allSuccess; it falls into allFailed so the caller shows an honest message.
        val outcome = BulkTasteOutcome(requestedCount = 5, successCount = 0, failureCount = 0, skippedCount = 5)
        assertFalse(outcome.isNoOp)
        assertFalse(outcome.allSuccess)
        assertTrue(outcome.allFailed)
    }

    @Test
    fun `skipped items count toward failure classification alongside real failures`() {
        val outcome = BulkTasteOutcome(requestedCount = 5, successCount = 2, failureCount = 1, skippedCount = 2)
        assertTrue(outcome.partialFailure)
        assertEquals(3, outcome.failureCount + outcome.skippedCount)
    }

    @Test
    fun `success does not infer from requestedCount alone`() {
        // A caller must never construct BulkTasteOutcome.success(requestedCount) without having
        // actually confirmed that many successes -- this test documents the contract by asserting
        // the fields are independent, not derived from one another implicitly.
        val outcome = BulkTasteOutcome(requestedCount = 10, successCount = 0, failureCount = 10)
        assertEquals(10, outcome.requestedCount)
        assertFalse(outcome.allSuccess)
    }

    @Test
    fun `each MangaRating maps to the correct BulkTasteActionType`() {
        assertEquals(BulkTasteActionType.RATE_LOVE, bulkTasteActionRatingType(MangaRating.LOVE))
        assertEquals(BulkTasteActionType.RATE_LIKE, bulkTasteActionRatingType(MangaRating.LIKE))
        assertEquals(BulkTasteActionType.RATE_DISLIKE, bulkTasteActionRatingType(MangaRating.DISLIKE))
    }
}
// KMK <--

package tachiyomi.domain.taste.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CrossSourceIdentityDecisionPolicyTest {

    private val now = 10_000L

    private fun row(
        first: CrossSourceRecordKey = CrossSourceRecordKey(2, "/b"),
        second: CrossSourceRecordKey = CrossSourceRecordKey(1, "/a"),
        decision: CrossSourceIdentityDecisionValue = CrossSourceIdentityDecisionValue.USER_CONFIRMED,
        reviewState: CrossSourceIdentityReviewState = CrossSourceIdentityReviewState.CURRENT,
        reasons: Set<CrossSourceIdentityReasonCode> = setOf(CrossSourceIdentityReasonCode.USER_CONFIRMATION),
        createdAt: Long = 1_000L,
        updatedAt: Long = 2_000L,
        deletedAt: Long? = null,
    ) = CrossSourceIdentityDecision(
        pair = CrossSourceIdentityDecisionPolicy.canonicalPair(first, second),
        decision = decision,
        decisionVersion = CrossSourceIdentityDecisionPolicy.CURRENT_DECISION_VERSION,
        evidenceVersion = CrossSourceIdentityDecisionPolicy.CURRENT_EVIDENCE_VERSION,
        reasonCodes = reasons,
        reviewState = reviewState,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    @Test
    fun `pair ordering is canonical and stable`() {
        val pair = row().pair
        assertEquals(CrossSourceRecordKey(1, "/a"), pair.left)
        assertEquals(CrossSourceRecordKey(2, "/b"), pair.right)
        assertEquals(pair, CrossSourceIdentityDecisionPolicy.canonicalPair(pair.right, pair.left))
    }

    @Test
    fun `validation rejects malformed keys versions times and reason sets`() {
        assertTrue(CrossSourceIdentityDecisionPolicy.isValid(row(), now))
        assertFalse(CrossSourceIdentityDecisionPolicy.isValid(row(first = CrossSourceRecordKey(0, "/a")), now))
        assertFalse(CrossSourceIdentityDecisionPolicy.isValid(row(first = CrossSourceRecordKey(1, "")), now))
        assertFalse(
            CrossSourceIdentityDecisionPolicy.isValid(
                row(first = CrossSourceRecordKey(1, "x".repeat(CrossSourceIdentityDecisionPolicy.MAX_URL_LENGTH + 1))),
                now,
            ),
        )
        val self = CrossSourceRecordKey(1, "/same")
        assertFalse(CrossSourceIdentityDecisionPolicy.isValid(row(first = self, second = self), now))
        assertFalse(CrossSourceIdentityDecisionPolicy.isValid(row().copy(decisionVersion = 99), now))
        assertFalse(CrossSourceIdentityDecisionPolicy.isValid(row(reasons = emptySet()), now))
        assertFalse(CrossSourceIdentityDecisionPolicy.isValid(row(createdAt = 3_000, updatedAt = 2_000), now))
        assertFalse(
            CrossSourceIdentityDecisionPolicy.isValid(
                row(updatedAt = now + CrossSourceIdentityDecisionPolicy.MAX_FUTURE_SKEW_MS + 1),
                now,
            ),
        )
        assertFalse(CrossSourceIdentityDecisionPolicy.isValid(row(deletedAt = 3_000), now))
    }

    @Test
    fun `only current live positive decisions are authoritative`() {
        assertTrue(CrossSourceIdentityDecisionPolicy.isAuthoritativeConfirmation(row()))
        assertFalse(
            CrossSourceIdentityDecisionPolicy.isAuthoritativeConfirmation(
                row(decision = CrossSourceIdentityDecisionValue.USER_REJECTED),
            ),
        )
        assertFalse(
            CrossSourceIdentityDecisionPolicy.isAuthoritativeConfirmation(
                row(reviewState = CrossSourceIdentityReviewState.NEEDS_REVIEW),
            ),
        )
        assertFalse(CrossSourceIdentityDecisionPolicy.isAuthoritativeConfirmation(row(deletedAt = 2_000)))
        assertFalse(CrossSourceIdentityDecisionPolicy.isAuthoritativeConfirmation(null))
    }

    @Test
    fun `newer row wins including a tombstone`() {
        val active = row(updatedAt = 2_000)
        val deleted = CrossSourceIdentityDecisionPolicy.tombstone(active, 3_000)
        assertEquals(deleted, CrossSourceIdentityDecisionPolicy.merge(active, deleted))
        assertEquals(deleted, CrossSourceIdentityDecisionPolicy.merge(deleted, active))
    }

    @Test
    fun `equal-time disagreement is deterministic review state and not authoritative`() {
        val confirmed = row(updatedAt = 4_000)
        val rejected = row(
            decision = CrossSourceIdentityDecisionValue.USER_REJECTED,
            reasons = setOf(CrossSourceIdentityReasonCode.USER_REJECTION),
            updatedAt = 4_000,
        )
        val first = CrossSourceIdentityDecisionPolicy.merge(confirmed, rejected)
        val second = CrossSourceIdentityDecisionPolicy.merge(rejected, confirmed)
        assertEquals(first, second)
        assertEquals(CrossSourceIdentityReviewState.NEEDS_REVIEW, first.reviewState)
        assertTrue(CrossSourceIdentityReasonCode.SYNC_CONFLICT in first.reasonCodes)
        assertFalse(CrossSourceIdentityDecisionPolicy.isAuthoritativeConfirmation(first))
    }

    @Test
    fun `equal-time equivalent payload with different creation time is not a conflict`() {
        val first = row(createdAt = 1_000, updatedAt = 4_000)
        val second = row(createdAt = 2_000, updatedAt = 4_000)
        val merged = CrossSourceIdentityDecisionPolicy.merge(first, second)
        assertEquals(CrossSourceIdentityReviewState.CURRENT, merged.reviewState)
        assertEquals(1_000L, merged.createdAt)
        assertFalse(CrossSourceIdentityReasonCode.SYNC_CONFLICT in merged.reasonCodes)
    }

    @Test
    fun `reason codec rejects unknown or empty input`() {
        val reasons = setOf(
            CrossSourceIdentityReasonCode.USER_CONFIRMATION,
            CrossSourceIdentityReasonCode.BRIDGE_ALTERNATE_SELECTION,
        )
        assertEquals(reasons, CrossSourceIdentityDecisionPolicy.decodeReasonCodes(CrossSourceIdentityDecisionPolicy.encodeReasonCodes(reasons)))
        assertNull(CrossSourceIdentityDecisionPolicy.decodeReasonCodes(""))
        assertNull(CrossSourceIdentityDecisionPolicy.decodeReasonCodes("NOT_A_REASON"))
    }
}

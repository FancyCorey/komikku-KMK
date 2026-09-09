package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCrossSourceIdentityDecision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionPolicy
import tachiyomi.domain.taste.model.CrossSourceIdentityReviewState

class CrossSourceIdentityBackupPolicyTest {

    private val now = 10_000L

    private fun row(
        leftSource: Long = 1,
        leftUrl: String = "/a",
        rightSource: Long = 2,
        rightUrl: String = "/b",
        decision: String = "USER_CONFIRMED",
        reasonCodes: List<String> = listOf("USER_CONFIRMATION"),
        reviewState: String = "CURRENT",
        updatedAt: Long = 2_000,
        deletedAt: Long = 0,
    ) = BackupCrossSourceIdentityDecision(
        leftSource = leftSource,
        leftUrl = leftUrl,
        rightSource = rightSource,
        rightUrl = rightUrl,
        decision = decision,
        decisionVersion = 1,
        evidenceVersion = 1,
        reasonCodes = reasonCodes,
        reviewState = reviewState,
        createdAt = 1_000,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    @Test
    fun `decode canonicalizes unordered endpoints and encode round trips`() {
        val decoded = requireNotNull(CrossSourceIdentityBackupPolicy.decode(row(leftSource = 2, rightSource = 1), now))
        assertEquals(1L, decoded.pair.left.source)
        assertEquals(decoded, CrossSourceIdentityBackupPolicy.decode(CrossSourceIdentityBackupPolicy.encode(decoded), now))
    }

    @Test
    fun `malformed rows are rejected independently`() {
        assertNull(CrossSourceIdentityBackupPolicy.decode(row(leftSource = 0), now))
        assertNull(CrossSourceIdentityBackupPolicy.decode(row(decision = "UNKNOWN"), now))
        assertNull(CrossSourceIdentityBackupPolicy.decode(row(reasonCodes = listOf("RAW_TEXT")), now))
        assertNull(CrossSourceIdentityBackupPolicy.decode(row(updatedAt = now + CrossSourceIdentityDecisionPolicy.MAX_FUTURE_SKEW_MS + 1), now))
        val merged = CrossSourceIdentityBackupPolicy.merge(
            listOf(row(), row(leftSource = 0)),
            emptyList(),
            now,
        )
        assertEquals(1, merged.size)
    }

    @Test
    fun `sync merge propagates newer tombstone`() {
        val merged = CrossSourceIdentityBackupPolicy.merge(
            listOf(row(updatedAt = 2_000)),
            listOf(row(updatedAt = 3_000, deletedAt = 3_000)),
            now,
        )
        assertEquals(3_000L, merged.single().deletedAt)
    }

    @Test
    fun `equal-time disagreement becomes stable needs-review regardless of side`() {
        val positive = row(updatedAt = 4_000)
        val negative = row(decision = "USER_REJECTED", reasonCodes = listOf("USER_REJECTION"), updatedAt = 4_000)
        val first = CrossSourceIdentityBackupPolicy.merge(listOf(positive), listOf(negative), now)
        val second = CrossSourceIdentityBackupPolicy.merge(listOf(negative), listOf(positive), now)
        assertEquals(first, second)
        assertEquals(CrossSourceIdentityReviewState.NEEDS_REVIEW.name, first.single().reviewState)
        assertTrue("SYNC_CONFLICT" in first.single().reasonCodes)
    }

    @Test
    fun `same pair duplicated in one payload cannot create duplicate output`() {
        val merged = CrossSourceIdentityBackupPolicy.merge(
            listOf(row(updatedAt = 2_000), row(updatedAt = 3_000)),
            null,
            now,
        )
        assertEquals(1, merged.size)
        assertEquals(3_000L, merged.single().updatedAt)
    }
}

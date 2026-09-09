package eu.kanade.tachiyomi.data.sync.service

import eu.kanade.tachiyomi.data.backup.models.BackupCrossSourceIdentityDecision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SyncServiceCrossSourceIdentityDecisionMergeTest {

    private fun row(decision: String, updatedAt: Long) = BackupCrossSourceIdentityDecision(
        leftSource = 1,
        leftUrl = "/a",
        rightSource = 2,
        rightUrl = "/b",
        decision = decision,
        decisionVersion = 1,
        evidenceVersion = 1,
        reasonCodes = listOf(if (decision == "USER_CONFIRMED") "USER_CONFIRMATION" else "USER_REJECTION"),
        reviewState = "CURRENT",
        createdAt = 1_000,
        updatedAt = updatedAt,
    )

    @Test
    fun `service merge boundary delegates equal-time conflict policy`() {
        val result = SyncService.mergeCrossSourceIdentityDecisionsPure(
            listOf(row("USER_CONFIRMED", 2_000)),
            listOf(row("USER_REJECTED", 2_000)),
            now = 10_000,
        )
        assertEquals("NEEDS_REVIEW", result.single().reviewState)
    }
}

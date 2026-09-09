package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCrossSourceIdentityDecision
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.repository.TasteRepository

class CrossSourceIdentityDecisionRestorerTest {

    private fun row(source: Long = 1) = BackupCrossSourceIdentityDecision(
        leftSource = source,
        leftUrl = "/a",
        rightSource = 2,
        rightUrl = "/b",
        decision = "USER_CONFIRMED",
        decisionVersion = 1,
        evidenceVersion = 1,
        reasonCodes = listOf("USER_CONFIRMATION"),
        reviewState = "CURRENT",
        createdAt = 1_000,
        updatedAt = 2_000,
    )

    @Test
    fun `malformed row is isolated while valid row is restored`() = runTest {
        val repository = mockk<TasteRepository>()
        coEvery { repository.getCrossSourceIdentityDecision(any()) } returns null
        coEvery { repository.upsertCrossSourceIdentityDecisions(any()) } returns Unit
        val errors = CrossSourceIdentityDecisionRestorer(repository).restore(listOf(row(0), row()), 10_000)
        assertEquals(listOf("Identity decision: malformed row skipped"), errors)
        coVerify(exactly = 1) { repository.upsertCrossSourceIdentityDecisions(any()) }
    }

    @Test
    fun `cancellation propagates and no write follows it`() {
        val repository = mockk<TasteRepository>()
        coEvery { repository.getCrossSourceIdentityDecision(any()) } throws CancellationException("stop")
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                CrossSourceIdentityDecisionRestorer(repository).restore(listOf(row()), 10_000)
            }
        }
        coVerify(exactly = 0) { repository.upsertCrossSourceIdentityDecisions(any()) }
    }
}

package tachiyomi.domain.taste.interactor

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.repository.SourceEvaluationRepository

// KMK -->
// KMK_CLAUDE_REMAINING_FIXTURE_BLOCKER_IMPLEMENTATION_PLAN_2026-08-03 Phase 7: direct coverage for
// ClearSourceEvaluations -- the interactor behind SourceEvaluationScreenModel's
// requestClearAllEvaluations()/confirmClearAllEvaluations() dialog-guarded UI action (a real,
// already-existing, supported in-app "Clear all evaluations" affordance in
// exh/recs/evaluation/SourceEvaluationScreen.kt). This is the exact mechanism that reaches the B04
// diagnostics empty state through the app itself -- no database edit is needed, and none is used
// here. Previously untested despite backing a real destructive UI action.
class ClearSourceEvaluationsTest {

    @Test
    fun `await delegates to the repository's deleteAll, the same full-clear the repository contract exposes`() = runTest {
        val repository = mockk<SourceEvaluationRepository>()
        coEvery { repository.deleteAll() } returns Unit
        val clearSourceEvaluations = ClearSourceEvaluations(repository)

        clearSourceEvaluations.await()

        coVerify(exactly = 1) { repository.deleteAll() }
    }
}
// KMK <--

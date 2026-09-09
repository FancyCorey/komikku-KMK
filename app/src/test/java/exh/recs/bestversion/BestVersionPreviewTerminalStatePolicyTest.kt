package exh.recs.bestversion

import exh.recs.RecommendationErrorKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class BestVersionPreviewTerminalStatePolicyTest {
    @Test
    fun `a missing fetch result becomes a terminal internal error`() {
        val result = BestVersionPreviewTerminalStatePolicy.resolve(null)

        assertEquals(
            CandidatePreviewState.PreviewError(
                BestVersionErrorReason.Recommendation(RecommendationErrorKind.Internal),
            ),
            result,
        )
        assertNotEquals(CandidatePreviewState.Loading, result)
    }

    @Test
    fun `a real preview result is preserved`() {
        val loaded = CandidatePreviewState.Loaded(emptyList())

        assertEquals(loaded, BestVersionPreviewTerminalStatePolicy.resolve(loaded))
    }
}

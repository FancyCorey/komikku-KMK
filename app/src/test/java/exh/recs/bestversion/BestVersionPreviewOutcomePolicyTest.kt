package exh.recs.bestversion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK v0.8.16-fix1 -->
class BestVersionPreviewOutcomePolicyTest {

    @Test
    fun `at least one sampled page is a usable preview`() {
        assertTrue(BestVersionPreviewOutcomePolicy.hasUsablePreview(1))
        assertTrue(BestVersionPreviewOutcomePolicy.hasUsablePreview(5))
    }

    @Test
    fun `zero sampled pages is not a usable preview -- must not read as a false success`() {
        assertFalse(BestVersionPreviewOutcomePolicy.hasUsablePreview(0))
    }

    @Test
    fun `image URL classification keeps absent and incompatible input distinct`() {
        assertEquals(
            BestVersionImageOutcome.SourceAbsent,
            BestVersionImageOutcomePolicy.classifyResolvedUrl(null),
        )
        assertEquals(
            BestVersionImageOutcome.Incompatible,
            BestVersionImageOutcomePolicy.classifyResolvedUrl("relative/image.jpg"),
        )
        assertEquals(
            BestVersionImageOutcome.Usable,
            BestVersionImageOutcomePolicy.classifyResolvedUrl("https://example.com/image.jpg"),
        )
    }

    @Test
    fun `source fallback recovers a present but unusable primary URL`() {
        assertEquals(
            "https://example.com/recovered.jpg",
            BestVersionImageOutcomePolicy.selectUsableUrl(
                primaryUrl = "relative/image.jpg",
                fallbackUrl = "https://example.com/recovered.jpg",
            ),
        )
    }

    @Test
    fun `usable primary URL wins without requiring a fallback`() {
        assertEquals(
            "https://example.com/primary.jpg",
            BestVersionImageOutcomePolicy.selectUsableUrl(
                primaryUrl = "https://example.com/primary.jpg",
                fallbackUrl = "https://example.com/fallback.jpg",
            ),
        )
    }

    @Test
    fun `source failures are retryable while incompatible extensions stay incompatible`() {
        assertEquals(
            BestVersionImageOutcome.Retryable,
            BestVersionImageOutcomePolicy.fromRecommendationError(exh.recs.RecommendationErrorKind.Timeout),
        )
        assertEquals(
            BestVersionImageOutcome.Incompatible,
            BestVersionImageOutcomePolicy.fromRecommendationError(exh.recs.RecommendationErrorKind.ExtensionIncompatible),
        )
    }

    @Test
    fun `render failure cannot be mistaken for a usable image`() {
        assertEquals(BestVersionImageOutcome.RenderFailed, BestVersionImageOutcomePolicy.renderFailure())
    }

    // KMK v0.8.21-fix2 -->
    @Test
    fun `rate limit and authentication errors are classified distinctly, not folded into Internal`() {
        assertEquals(
            BestVersionImageOutcome.Retryable,
            BestVersionImageOutcomePolicy.fromRecommendationError(exh.recs.RecommendationErrorKind.RateLimit),
        )
        assertEquals(
            BestVersionImageOutcome.AuthenticationRequired,
            BestVersionImageOutcomePolicy.fromRecommendationError(exh.recs.RecommendationErrorKind.Authentication),
        )
    }

    @Test
    fun `authentication outcome round-trips to the same error reason kind, not Network`() {
        val reason = BestVersionImageOutcomePolicy.toErrorReason(BestVersionImageOutcome.AuthenticationRequired)
        assertEquals(
            BestVersionErrorReason.Recommendation(exh.recs.RecommendationErrorKind.Authentication),
            reason,
        )
    }
    // KMK <--
}
// KMK <--

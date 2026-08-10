package exh.recs.bestversion

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
}
// KMK <--

package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK --> v0.7.11
class SourceEvaluationConsentPolicyTest {

    @Test
    fun `isConsentRequired returns true when consent not yet given`() {
        assertTrue(SourceEvaluationConsentPolicy.isConsentRequired(consentGiven = false))
    }

    @Test
    fun `isConsentRequired returns false when consent already given`() {
        assertFalse(SourceEvaluationConsentPolicy.isConsentRequired(consentGiven = true))
    }

    @Test
    fun `default preference value false means consent is required`() {
        val defaultPreferenceValue = false
        assertTrue(SourceEvaluationConsentPolicy.isConsentRequired(defaultPreferenceValue))
    }
}
// KMK <--

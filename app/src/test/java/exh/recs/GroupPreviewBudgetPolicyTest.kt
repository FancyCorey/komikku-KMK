package exh.recs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK v0.8.6 -->
class GroupPreviewBudgetPolicyTest {

    @Test
    fun `default value is 10`() {
        assertEquals(10, GroupPreviewBudgetPolicy.DEFAULT)
    }

    @Test
    fun `SUPPORTED_VALUES matches the shared five-option contract`() {
        assertEquals(listOf(5, 10, 15, 20, 30), GroupPreviewBudgetPolicy.SUPPORTED_VALUES)
    }

    @Test
    fun `validate returns supported values unchanged`() {
        listOf(5, 10, 15, 20, 30).forEach { value ->
            assertEquals(value, GroupPreviewBudgetPolicy.validate(value))
        }
    }

    @Test
    fun `validate falls back to default for corrupt, migrated, or unsupported values`() {
        listOf(0, -1, -100, 1, 6, 11, 25, 50, 100, Int.MAX_VALUE, Int.MIN_VALUE).forEach { value ->
            assertEquals(
                GroupPreviewBudgetPolicy.DEFAULT,
                GroupPreviewBudgetPolicy.validate(value),
                "expected fallback for corrupt value $value",
            )
        }
    }

    @Test
    fun `previewCandidateBudget mirrors validate`() {
        listOf(5, 10, 15, 20, 30).forEach { value ->
            assertEquals(value, GroupPreviewBudgetPolicy.previewCandidateBudget(value))
        }
        assertEquals(GroupPreviewBudgetPolicy.DEFAULT, GroupPreviewBudgetPolicy.previewCandidateBudget(-1))
    }

    @Test
    fun `previewEnrichmentBudget never exceeds the preview candidate budget`() {
        listOf(5, 10, 15, 20, 30).forEach { value ->
            assertTrue(GroupPreviewBudgetPolicy.previewEnrichmentBudget(value) <= GroupPreviewBudgetPolicy.previewCandidateBudget(value))
        }
    }

    @Test
    fun `isExpansionEligible is true only when candidates exceed the configured budget`() {
        assertFalse(GroupPreviewBudgetPolicy.isExpansionEligible(10, candidateCount = 10))
        assertFalse(GroupPreviewBudgetPolicy.isExpansionEligible(10, candidateCount = 4))
        assertTrue(GroupPreviewBudgetPolicy.isExpansionEligible(10, candidateCount = 11))
    }

    @Test
    fun `isExpansionEligible normalizes a corrupt configured value before comparing`() {
        // corrupt -> DEFAULT (10)
        assertFalse(GroupPreviewBudgetPolicy.isExpansionEligible(-1, candidateCount = 10))
        assertTrue(GroupPreviewBudgetPolicy.isExpansionEligible(9999, candidateCount = 11))
    }
}
// KMK <--

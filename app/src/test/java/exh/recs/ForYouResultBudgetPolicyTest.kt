package exh.recs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// KMK v0.8.2 -->
class ForYouResultBudgetPolicyTest {

    // --- validate() ---

    @Test
    fun `default value is 10`() {
        assertEquals(10, ForYouResultBudgetPolicy.DEFAULT)
    }

    @Test
    fun `validate returns supported values unchanged`() {
        listOf(5, 10, 15, 20, 30).forEach { value ->
            assertEquals(value, ForYouResultBudgetPolicy.validate(value))
        }
    }

    @Test
    fun `validate falls back to default for corrupt or unsupported values`() {
        listOf(0, -1, -100, 1, 6, 11, 25, 50, 100, Int.MAX_VALUE, Int.MIN_VALUE).forEach { value ->
            assertEquals(
                ForYouResultBudgetPolicy.DEFAULT,
                ForYouResultBudgetPolicy.validate(value),
                "expected fallback for corrupt value $value",
            )
        }
    }

    // --- resolve(): normal rows ---

    @Test
    fun `resolve for a normal row uses the validated configured value`() {
        listOf(5, 10, 15, 20, 30).forEach { value ->
            assertEquals(value, ForYouResultBudgetPolicy.resolve(value, isBoosted = false))
        }
    }

    @Test
    fun `resolve for a normal row falls back to default on corrupt input`() {
        assertEquals(ForYouResultBudgetPolicy.DEFAULT, ForYouResultBudgetPolicy.resolve(-7, isBoosted = false))
        assertEquals(ForYouResultBudgetPolicy.DEFAULT, ForYouResultBudgetPolicy.resolve(999, isBoosted = false))
    }

    // --- resolve(): boosted rows ---

    @Test
    fun `resolve for a boosted row never drops below 20 regardless of a smaller configured value`() {
        listOf(5, 10, 15).forEach { value ->
            assertEquals(20, ForYouResultBudgetPolicy.resolve(value, isBoosted = true))
        }
    }

    @Test
    fun `resolve for a boosted row stays at 20 when the configured value is exactly 20`() {
        assertEquals(20, ForYouResultBudgetPolicy.resolve(20, isBoosted = true))
    }

    @Test
    fun `resolve for a boosted row rises to 30 when 30 is selected`() {
        assertEquals(30, ForYouResultBudgetPolicy.resolve(30, isBoosted = true))
    }

    @Test
    fun `resolve for a boosted row falls back to the 20 floor on corrupt input`() {
        // corrupt -> DEFAULT (10) -> boosted floor still applies -> 20
        assertEquals(20, ForYouResultBudgetPolicy.resolve(-1, isBoosted = true))
        assertEquals(20, ForYouResultBudgetPolicy.resolve(9999, isBoosted = true))
    }

    // --- unrelated limits must never be touched by this policy ---

    @Test
    fun `SUPPORTED_VALUES contains exactly the five documented options`() {
        assertEquals(listOf(5, 10, 15, 20, 30), ForYouResultBudgetPolicy.SUPPORTED_VALUES)
    }

    @Test
    fun `BOOSTED_MINIMUM matches the historical boosted-row contract`() {
        assertEquals(20, ForYouResultBudgetPolicy.BOOSTED_MINIMUM)
    }
}
// KMK <--

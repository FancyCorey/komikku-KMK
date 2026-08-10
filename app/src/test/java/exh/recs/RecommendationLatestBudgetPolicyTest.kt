package exh.recs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK
/**
 * Budget tests proving the Latest lane stays bounded and that personalized search remains dominant:
 * Latest is capped both as a share of attempted sources and by an absolute per-refresh ceiling, and
 * can be switched off entirely from settings (the documented behavior kill switch).
 */
class RecommendationLatestBudgetPolicyTest {

    @Test
    fun `the default exploration share is the packet's proposed 20 percent`() {
        assertEquals(20, RecommendationLatestBudgetPolicy.DEFAULT)
    }

    @Test
    fun `supported values include an explicit off switch`() {
        assertEquals(listOf(0, 10, 20, 30, 50), RecommendationLatestBudgetPolicy.SUPPORTED_VALUES)
        assertTrue(RecommendationLatestBudgetPolicy.isDisabled(0))
        assertFalse(RecommendationLatestBudgetPolicy.isDisabled(20))
    }

    @Test
    fun `a malformed persisted percentage falls back to the default`() {
        listOf(-10, 1, 15, 25, 99, 100, 1000, Int.MIN_VALUE, Int.MAX_VALUE).forEach {
            assertEquals(20, RecommendationLatestBudgetPolicy.validate(it), "percent $it must fall back")
        }
    }

    @Test
    fun `a zero percentage disables the lane so no Latest call is ever attempted`() {
        assertEquals(0, RecommendationLatestBudgetPolicy.resolveAttempts(0, attemptedSourceCount = 40))
    }

    @Test
    fun `attempts scale with the configured share of attempted sources`() {
        assertEquals(2, RecommendationLatestBudgetPolicy.resolveAttempts(10, 20))
        assertEquals(4, RecommendationLatestBudgetPolicy.resolveAttempts(20, 20))
        assertEquals(6, RecommendationLatestBudgetPolicy.resolveAttempts(30, 20))
    }

    @Test
    fun `the absolute per-refresh ceiling prevents a large source list becoming a crawl`() {
        assertEquals(
            RecommendationLatestBudgetPolicy.MAX_ATTEMPTS_PER_REFRESH,
            RecommendationLatestBudgetPolicy.resolveAttempts(50, attemptedSourceCount = 200),
        )
        assertTrue(RecommendationLatestBudgetPolicy.resolveAttempts(50, 1000) <= 6)
    }

    @Test
    fun `a small source list still earns at least one exploration probe`() {
        assertEquals(1, RecommendationLatestBudgetPolicy.resolveAttempts(20, attemptedSourceCount = 1))
        assertEquals(1, RecommendationLatestBudgetPolicy.resolveAttempts(10, attemptedSourceCount = 3))
    }

    @Test
    fun `no sources means no attempts`() {
        assertEquals(0, RecommendationLatestBudgetPolicy.resolveAttempts(20, 0))
        assertEquals(0, RecommendationLatestBudgetPolicy.resolveAttempts(20, -5))
    }

    @Test
    fun `personalized search stays dominant -- Latest never probes a majority of sources`() {
        // At every supported non-zero share and a realistic source count, Latest touches a small
        // minority of the sources the refresh attempts.
        listOf(10, 20, 30, 50).forEach { percent ->
            val attempts = RecommendationLatestBudgetPolicy.resolveAttempts(percent, attemptedSourceCount = 30)
            assertTrue(attempts < 30 / 2, "percent $percent produced $attempts attempts, which is not a minority")
        }
    }

    // KMK -->
    // resolveAdditiveSlotsPerSource: bounds how many Latest candidates may compete for one source's
    // row alongside already-found personalized results (Domain A structural fix -- Latest must be
    // additive, not fallback-only).

    @Test
    fun `additive slots are zero when the lane is disabled or the display limit is non-positive`() {
        assertEquals(0, RecommendationLatestBudgetPolicy.resolveAdditiveSlotsPerSource(displayLimit = 10, configuredPercent = 0))
        assertEquals(0, RecommendationLatestBudgetPolicy.resolveAdditiveSlotsPerSource(displayLimit = 0, configuredPercent = 20))
        assertEquals(0, RecommendationLatestBudgetPolicy.resolveAdditiveSlotsPerSource(displayLimit = -5, configuredPercent = 20))
    }

    @Test
    fun `a small display limit still earns at least one additive slot once the lane is enabled`() {
        assertEquals(1, RecommendationLatestBudgetPolicy.resolveAdditiveSlotsPerSource(displayLimit = 5, configuredPercent = 10))
    }

    @Test
    fun `additive slots never exceed the absolute per-source ceiling`() {
        for (displayLimit in listOf(5, 10, 15, 20, 30)) {
            for (percent in listOf(10, 20, 30, 50)) {
                val slots = RecommendationLatestBudgetPolicy.resolveAdditiveSlotsPerSource(displayLimit, percent)
                assertTrue(
                    slots <= RecommendationLatestBudgetPolicy.MAX_ADDITIVE_SLOTS_PER_SOURCE,
                    "displayLimit=$displayLimit percent=$percent produced $slots slots, exceeding the ceiling",
                )
            }
        }
    }

    @Test
    fun `personalized results remain the majority of every row across every supported combination`() {
        // Exhaustive proof for the Domain A requirement "Latest must never replace the majority of
        // personalized results" -- every supported display limit crossed with every supported
        // percentage keeps Latest's slot count strictly under half the row.
        for (displayLimit in listOf(5, 10, 15, 20, 30)) {
            for (percent in listOf(10, 20, 30, 50)) {
                val slots = RecommendationLatestBudgetPolicy.resolveAdditiveSlotsPerSource(displayLimit, percent)
                assertTrue(
                    slots.toDouble() / displayLimit < 0.5,
                    "displayLimit=$displayLimit percent=$percent produced $slots slots -- not a personalized majority",
                )
            }
        }
    }

    @Test
    fun `the worst-case additive share is 40 percent of the smallest supported row`() {
        val slots = RecommendationLatestBudgetPolicy.resolveAdditiveSlotsPerSource(displayLimit = 5, configuredPercent = 50)
        assertEquals(2, slots)
        assertTrue(slots.toDouble() / 5 < 0.5)
    }

    @Test
    fun `additive slots scale with a larger display limit but stay capped`() {
        val small = RecommendationLatestBudgetPolicy.resolveAdditiveSlotsPerSource(displayLimit = 5, configuredPercent = 10)
        val large = RecommendationLatestBudgetPolicy.resolveAdditiveSlotsPerSource(displayLimit = 30, configuredPercent = 10)
        assertTrue(large > small, "expected a larger display limit to earn more slots: small=$small large=$large")
        assertEquals(RecommendationLatestBudgetPolicy.MAX_ADDITIVE_SLOTS_PER_SOURCE, large)
    }
    // KMK <--
}
// KMK <--

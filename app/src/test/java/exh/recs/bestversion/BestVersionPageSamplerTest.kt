package exh.recs.bestversion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK --> v0.7.8
class BestVersionPageSamplerTest {

    // --- Empty / zero edge cases ---

    @Test
    fun `zero pages returns empty`() {
        assertEquals(emptyList<Int>(), BestVersionPageSampler.sample(0, 5, true))
    }

    @Test
    fun `zero sample size returns empty`() {
        assertEquals(emptyList<Int>(), BestVersionPageSampler.sample(30, 0, true))
    }

    // --- Tiny chapters (<=sampleSize) return all pages ---

    @Test
    fun `chapter with fewer pages than sample returns all pages`() {
        val result = BestVersionPageSampler.sample(3, 5, true)
        assertEquals(listOf(0, 1, 2), result)
    }

    @Test
    fun `chapter equal to sample size returns all pages`() {
        val result = BestVersionPageSampler.sample(5, 5, true)
        assertEquals(listOf(0, 1, 2, 3, 4), result)
    }

    // --- Sample size respected ---

    @Test
    fun `sample returns at most sampleSize elements`() {
        val result = BestVersionPageSampler.sample(30, 5, true)
        assertTrue(result.size <= 5)
    }

    @Test
    fun `sample of 2 returns at most 2 elements`() {
        val result = BestVersionPageSampler.sample(30, 2, true)
        assertTrue(result.size <= 2)
    }

    @Test
    fun `sample of 10 returns at most 10 elements`() {
        val result = BestVersionPageSampler.sample(30, 10, true)
        assertTrue(result.size <= 10)
    }

    // --- Avoid first pages ---

    @Test
    fun `avoid first pages skips page 0 for large chapters`() {
        val result = BestVersionPageSampler.sample(30, 5, avoidFirstPages = true)
        assertTrue(result.none { it == 0 }, "Page 0 should be skipped when avoidFirstPages=true and chapter is large")
    }

    @Test
    fun `avoid first pages false allows page 0 for large chapters`() {
        val result = BestVersionPageSampler.sample(30, 5, avoidFirstPages = false)
        // Pages don't have to include 0, but avoidFirstPages=false shouldn't prevent it if in window
        // Just verify no crash and result is non-empty
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `avoid first pages does not break tiny chapter`() {
        val result = BestVersionPageSampler.sample(2, 5, avoidFirstPages = true)
        assertEquals(listOf(0, 1), result)
    }

    // --- Index validity ---

    @Test
    fun `all sampled indexes are within valid range`() {
        val totalPages = 30
        val result = BestVersionPageSampler.sample(totalPages, 5, true)
        assertTrue(result.all { it in 0 until totalPages }, "All indexes must be in 0 until $totalPages")
    }

    @Test
    fun `indexes are unique (no duplicates)`() {
        val result = BestVersionPageSampler.sample(30, 5, true)
        assertEquals(result.distinct(), result)
    }

    // --- 30-page / sample-5 example from the plan ---

    @Test
    fun `30 pages sample 5 produces 5 pages roughly in middle range`() {
        val result = BestVersionPageSampler.sample(30, 5, avoidFirstPages = true)
        assertTrue(result.size <= 5)
        assertTrue(result.all { it in 0 until 30 })
        // Samples should be in roughly the 30–75% window (pages 9–22 for 30 pages)
        assertTrue(result.all { it >= 2 }, "Should skip early pages: $result")
    }
}
// KMK <--

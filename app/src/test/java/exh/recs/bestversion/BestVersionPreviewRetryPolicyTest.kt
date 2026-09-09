package exh.recs.bestversion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK R2-AUG-05-ACTUAL-BEST-VERSION-FAILURE-PATH -->
class BestVersionPreviewRetryPolicyTest {

    @Test
    fun `currentGeneration is 0 for a page that has never been retried`() {
        assertEquals(0, BestVersionPreviewRetryPolicy.currentGeneration(emptyMap(), pageIndex = 3))
    }

    @Test
    fun `requestRetry bumps only the requested page's generation`() {
        val tokens = mapOf(0 to 2)
        val updated = BestVersionPreviewRetryPolicy.requestRetry(tokens, pageIndex = 1)

        assertEquals(2, BestVersionPreviewRetryPolicy.currentGeneration(updated, pageIndex = 0))
        assertEquals(1, BestVersionPreviewRetryPolicy.currentGeneration(updated, pageIndex = 1))
    }

    @Test
    fun `repeated retries on the same page keep incrementing`() {
        var tokens = emptyMap<Int, Int>()
        repeat(3) { tokens = BestVersionPreviewRetryPolicy.requestRetry(tokens, pageIndex = 5) }

        assertEquals(3, BestVersionPreviewRetryPolicy.currentGeneration(tokens, pageIndex = 5))
    }

    // --- stale-generation / cancellation protection ---

    @Test
    fun `a result from the current generation is accepted`() {
        val tokens = mapOf(2 to 1)
        assertTrue(BestVersionPreviewRetryPolicy.isCurrent(tokens, pageIndex = 2, resultGeneration = 1))
    }

    @Test
    fun `a result from a superseded (stale) generation must be rejected`() {
        // Page 2 was retried twice (now at generation 2), but a slow in-flight load started under
        // generation 0 (the original, un-retried load) finally resolves -- it must never be applied,
        // since a newer retry has already superseded it.
        val tokens = mapOf(2 to 2)
        assertFalse(BestVersionPreviewRetryPolicy.isCurrent(tokens, pageIndex = 2, resultGeneration = 0))
        assertFalse(BestVersionPreviewRetryPolicy.isCurrent(tokens, pageIndex = 2, resultGeneration = 1))
        assertTrue(BestVersionPreviewRetryPolicy.isCurrent(tokens, pageIndex = 2, resultGeneration = 2))
    }

    @Test
    fun `isCurrent for an untouched page only accepts generation 0`() {
        assertTrue(BestVersionPreviewRetryPolicy.isCurrent(emptyMap(), pageIndex = 9, resultGeneration = 0))
        assertFalse(BestVersionPreviewRetryPolicy.isCurrent(emptyMap(), pageIndex = 9, resultGeneration = 1))
    }

    // --- clampPageIndex ---

    @Test
    fun `clampPageIndex bounds a requested index into range`() {
        assertEquals(0, BestVersionPreviewRetryPolicy.clampPageIndex(-5, pageCount = 10))
        assertEquals(9, BestVersionPreviewRetryPolicy.clampPageIndex(50, pageCount = 10))
        assertEquals(4, BestVersionPreviewRetryPolicy.clampPageIndex(4, pageCount = 10))
    }

    @Test
    fun `clampPageIndex returns -1 for an empty page set`() {
        assertEquals(-1, BestVersionPreviewRetryPolicy.clampPageIndex(0, pageCount = 0))
    }
}
// KMK <--

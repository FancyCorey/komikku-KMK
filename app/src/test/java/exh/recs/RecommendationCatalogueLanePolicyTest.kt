package exh.recs

import exh.recs.RecommendationCatalogueLanePolicy.LatestOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

// KMK
/**
 * Source/capability tests for the bounded Latest lane: success, unsupported (both at capability and
 * at runtime), empty, malformed, offline/recoverable failure, budget exhaustion, and provenance
 * separation from Popular.
 */
class RecommendationCatalogueLanePolicyTest {

    // --- Eligibility / boundedness ---

    @Test
    fun `attempts Latest for an eligible source that declares support and has budget`() {
        assertTrue(
            RecommendationCatalogueLanePolicy.shouldAttempt(
                supportsLatest = true,
                sourceIsEligible = true,
                latestAttemptsUsed = 0,
                latestBudget = 3,
            ),
        )
    }

    @Test
    fun `never attempts Latest for a source that does not declare support`() {
        assertFalse(
            RecommendationCatalogueLanePolicy.shouldAttempt(
                supportsLatest = false,
                sourceIsEligible = true,
                latestAttemptsUsed = 0,
                latestBudget = 3,
            ),
        )
        assertEquals(
            LatestOutcome.UNSUPPORTED_CAPABILITY,
            RecommendationCatalogueLanePolicy.declinedOutcome(false, true, 0, 3),
        )
    }

    @Test
    fun `never resurrects a source the normal pipeline already excluded`() {
        assertFalse(
            RecommendationCatalogueLanePolicy.shouldAttempt(
                supportsLatest = true,
                sourceIsEligible = false,
                latestAttemptsUsed = 0,
                latestBudget = 3,
            ),
        )
        assertEquals(
            LatestOutcome.NOT_ELIGIBLE,
            RecommendationCatalogueLanePolicy.declinedOutcome(true, false, 0, 3),
        )
    }

    @Test
    fun `stops once the refresh budget is spent`() {
        assertTrue(RecommendationCatalogueLanePolicy.shouldAttempt(true, true, 2, 3))
        assertFalse(RecommendationCatalogueLanePolicy.shouldAttempt(true, true, 3, 3))
        assertFalse(RecommendationCatalogueLanePolicy.shouldAttempt(true, true, 4, 3))
        assertEquals(LatestOutcome.BUDGET_EXHAUSTED, RecommendationCatalogueLanePolicy.declinedOutcome(true, true, 3, 3))
    }

    @Test
    fun `a zero budget disables the lane entirely`() {
        assertFalse(RecommendationCatalogueLanePolicy.shouldAttempt(true, true, 0, 0))
        assertEquals(LatestOutcome.BUDGET_EXHAUSTED, RecommendationCatalogueLanePolicy.declinedOutcome(true, true, 0, 0))
    }

    @Test
    fun `declinedOutcome returns null when the attempt should proceed`() {
        assertNull(RecommendationCatalogueLanePolicy.declinedOutcome(true, true, 0, 3))
    }

    @Test
    fun `Latest only ever requests page 1`() {
        assertEquals(1, RecommendationCatalogueLanePolicy.LATEST_PAGE)
    }

    // --- Outcome classification ---

    @Test
    fun `a successful call with usable entries is SUCCESS`() {
        assertEquals(LatestOutcome.SUCCESS, RecommendationCatalogueLanePolicy.classify(failure = null, usableEntryCount = 5))
    }

    @Test
    fun `a successful call with no usable entries is EMPTY`() {
        assertEquals(LatestOutcome.EMPTY, RecommendationCatalogueLanePolicy.classify(failure = null, usableEntryCount = 0))
    }

    @Test
    fun `a source that declared support but rejects the call is UNSUPPORTED_AT_RUNTIME`() {
        // CatalogueSource.getLatestUpdates delegates to the deprecated fetchLatestUpdates, whose
        // default implementation throws exactly this.
        assertEquals(
            LatestOutcome.UNSUPPORTED_AT_RUNTIME,
            RecommendationCatalogueLanePolicy.classify(UnsupportedOperationException("no latest"), 0),
        )
    }

    @Test
    fun `offline and other recoverable failures are RECOVERABLE_ERROR`() {
        assertEquals(LatestOutcome.RECOVERABLE_ERROR, RecommendationCatalogueLanePolicy.classify(IOException("offline"), 0))
        assertEquals(
            LatestOutcome.RECOVERABLE_ERROR,
            RecommendationCatalogueLanePolicy.classify(java.net.UnknownHostException("dns"), 0),
        )
        assertEquals(
            LatestOutcome.RECOVERABLE_ERROR,
            RecommendationCatalogueLanePolicy.classify(java.net.SocketTimeoutException("timeout"), 0),
        )
    }

    @Test
    fun `a failure always wins over a stale entry count`() {
        // Defensive: a caller must never report both a failure and surviving entries, but if it did,
        // the failure classification must not be masked by the count.
        assertEquals(LatestOutcome.RECOVERABLE_ERROR, RecommendationCatalogueLanePolicy.classify(IOException("x"), 10))
    }

    // --- Malformed entry handling ---

    @Test
    fun `malformed entries with a blank url or title are not usable`() {
        assertFalse(RecommendationCatalogueLanePolicy.isUsableEntry(null, "Title"))
        assertFalse(RecommendationCatalogueLanePolicy.isUsableEntry("", "Title"))
        assertFalse(RecommendationCatalogueLanePolicy.isUsableEntry("   ", "Title"))
        assertFalse(RecommendationCatalogueLanePolicy.isUsableEntry("/manga/1", null))
        assertFalse(RecommendationCatalogueLanePolicy.isUsableEntry("/manga/1", ""))
        assertFalse(RecommendationCatalogueLanePolicy.isUsableEntry("/manga/1", "  "))
    }

    @Test
    fun `a well-formed entry is usable`() {
        assertTrue(RecommendationCatalogueLanePolicy.isUsableEntry("/manga/1", "Title"))
    }

    @Test
    fun `a page of only malformed entries classifies as EMPTY, not SUCCESS`() {
        val entries = listOf("" to "A", "/m/1" to "", null to null)
        val usable = entries.count { RecommendationCatalogueLanePolicy.isUsableEntry(it.first, it.second) }
        assertEquals(0, usable)
        assertEquals(LatestOutcome.EMPTY, RecommendationCatalogueLanePolicy.classify(null, usable))
    }

    // --- Budget accounting ---

    @Test
    fun `only outcomes that actually made a call consume budget`() {
        listOf(
            LatestOutcome.SUCCESS,
            LatestOutcome.EMPTY,
            LatestOutcome.RECOVERABLE_ERROR,
            LatestOutcome.UNSUPPORTED_AT_RUNTIME,
        ).forEach { assertTrue(RecommendationCatalogueLanePolicy.consumesBudget(it), "$it should consume budget") }

        listOf(
            LatestOutcome.UNSUPPORTED_CAPABILITY,
            LatestOutcome.BUDGET_EXHAUSTED,
            LatestOutcome.NOT_ELIGIBLE,
        ).forEach { assertFalse(RecommendationCatalogueLanePolicy.consumesBudget(it), "$it should not consume budget") }
    }

    // --- Provenance separation ---

    @Test
    fun `Latest and Popular provenance remain distinct storage keys`() {
        assertEquals("LATEST_CATALOGUE", RecommendationDiscoveryLane.LATEST_CATALOGUE.storageKey)
        assertEquals(
            RecommendationCatalogueFallbackPolicy.QUERY_STRATEGY,
            RecommendationDiscoveryLane.POPULAR_CATALOGUE.storageKey,
        )
        assertTrue(
            RecommendationDiscoveryLane.LATEST_CATALOGUE.storageKey !=
                RecommendationDiscoveryLane.POPULAR_CATALOGUE.storageKey,
        )
    }

    @Test
    fun `Popular keeps its pre-existing storage key so rows already on disk keep their meaning`() {
        assertEquals("CATALOGUE_FALLBACK", RecommendationDiscoveryLane.POPULAR_CATALOGUE.storageKey)
        assertEquals(
            RecommendationDiscoveryLane.POPULAR_CATALOGUE,
            RecommendationDiscoveryLane.fromStorageKey("CATALOGUE_FALLBACK"),
        )
    }

    @Test
    fun `missing or legacy provenance resolves to unknown, never to Latest`() {
        assertNull(RecommendationDiscoveryLane.fromStorageKey(null))
        assertNull(RecommendationDiscoveryLane.fromStorageKey(""))
        assertNull(RecommendationDiscoveryLane.fromStorageKey("TAG_AND_TEXT"))
        assertNull(RecommendationDiscoveryLane.fromStorageKey("TEXT_ONLY_TOP_TAGS"))
        assertNull(RecommendationDiscoveryLane.fromStorageKey("something-from-a-future-build"))
    }
}
// KMK <--

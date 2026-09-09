package exh.recs.memory

// KMK --> v0.7.39: For You rolling discovery planner tests
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.RecommendationDiscoveryProgress

/**
 * Unit tests for [RecommendationDiscoveryPlanner].
 *
 * v0.7.39: planner uses progress table (not candidate memory) so empty/filtered pages count.
 * v0.7.40: planner accepts full progress records so it can classify retryable vs permanent
 * failures and apply bounded exponential backoff.
 *
 * Run with: ./gradlew :app:testDebugUnitTest --tests "*.RecommendationDiscoveryPlannerTest"
 */
class RecommendationDiscoveryPlannerTest {

    private val now = System.currentTimeMillis()

    private fun successRecord(page: Int) = RecommendationDiscoveryProgress(
        sourceId = 1L, querySignature = "sig", queryTagsJson = "", queryStrategy = null,
        page = page, evaluatedAt = now, rawCount = 5, localizedCount = 5,
        scoredCount = 3, visibleCount = 3, filteredCount = 0,
        status = RecommendationDiscoveryProgress.STATUS_SUCCESS,
        errorMessage = null, profileFingerprint = null,
    )

    private fun emptyRecord(page: Int) = successRecord(page).copy(
        rawCount = 0,
        localizedCount = 0,
        scoredCount = 0,
        visibleCount = 0,
        status = RecommendationDiscoveryProgress.STATUS_EMPTY,
    )

    private fun retryableErrorRecord(page: Int, attemptCount: Int = 1, nextRetryAt: Long? = null) =
        successRecord(page).copy(
            status = RecommendationDiscoveryProgress.STATUS_ERROR,
            failureKind = RecommendationDiscoveryProgress.FAILURE_KIND_RETRYABLE,
            attemptCount = attemptCount,
            nextRetryAt = nextRetryAt,
        )

    private fun permanentErrorRecord(page: Int) = successRecord(page).copy(
        status = RecommendationDiscoveryProgress.STATUS_ERROR,
        failureKind = RecommendationDiscoveryProgress.FAILURE_KIND_PERMANENT,
    )

    // KMK --> v0.7.41: terminal exhausted-retry record (advanceable)
    private fun exhaustedRecord(page: Int) = successRecord(page).copy(
        status = RecommendationDiscoveryProgress.STATUS_EXHAUSTED,
        failureKind = RecommendationDiscoveryProgress.FAILURE_KIND_RETRYABLE,
        attemptCount = RecommendationRetryClassifier.MAX_ATTEMPTS,
        nextRetryAt = null,
    )
    // KMK <--

    private fun probe(records: List<RecommendationDiscoveryProgress>, nowMs: Long = now) =
        RecommendationDiscoveryPlanner.nextPageToProbe(records, nowMs)

    // ---- Advance behavior ----

    @Test
    fun `empty records returns null — page 1 not yet evaluated`() {
        assertNull(probe(emptyList()))
    }

    @Test
    fun `page 1 success record returns page 2`() {
        assertEquals(2, probe(listOf(successRecord(1))))
    }

    @Test
    fun `pages 1 and 2 success records return page 3`() {
        assertEquals(3, probe(listOf(successRecord(1), successRecord(2))))
    }

    @Test
    fun `empty page 2 record still causes planner to advance to page 3`() {
        assertEquals(3, probe(listOf(successRecord(1), emptyRecord(2))))
    }

    @Test
    fun `permanent failure on page 2 causes planner to advance to page 3`() {
        assertEquals(3, probe(listOf(successRecord(1), permanentErrorRecord(2))))
    }

    @Test
    fun `page at MAX cap returns null`() {
        val cap = RecommendationDiscoveryPlanner.MAX_DISCOVERY_PAGE_PER_SOURCE_QUERY
        assertNull(probe(listOf(successRecord(cap))))
    }

    @Test
    fun `page just below cap returns cap`() {
        val cap = RecommendationDiscoveryPlanner.MAX_DISCOVERY_PAGE_PER_SOURCE_QUERY
        assertEquals(cap, probe(listOf(successRecord(cap - 1))))
    }

    // ---- Retry behavior ----

    @Test
    fun `retryable failure with past nextRetryAt returns same page for retry`() {
        val pastRetryAt = now - 1000L
        val record = retryableErrorRecord(page = 2, attemptCount = 1, nextRetryAt = pastRetryAt)
        assertEquals(2, probe(listOf(successRecord(1), record)))
    }

    @Test
    fun `retryable failure with future nextRetryAt returns null — not yet due`() {
        val futureRetryAt = now + 60_000L
        val record = retryableErrorRecord(page = 2, attemptCount = 1, nextRetryAt = futureRetryAt)
        assertNull(probe(listOf(successRecord(1), record)))
    }

    @Test
    fun `retryable failure at max attempts is not retried — advances to next page`() {
        val pastRetryAt = now - 1000L
        val record = retryableErrorRecord(
            page = 2,
            attemptCount = RecommendationRetryClassifier.MAX_ATTEMPTS,
            nextRetryAt = pastRetryAt,
        )
        assertEquals(3, probe(listOf(successRecord(1), record)))
    }

    // ---- v0.7.41 corrections: exhausted persistence + cap boundary ----

    @Test
    fun `exhausted retry record advances to next page`() {
        // A terminal STATUS_EXHAUSTED record is advanceable, just like a permanent failure.
        assertEquals(3, probe(listOf(successRecord(1), exhaustedRecord(2))))
    }

    @Test
    fun `retryable failure before its due time blocks advancement`() {
        // Even though page 3+ could be probed, a not-yet-due retryable error pins discovery to page 2.
        val futureRetryAt = now + 60_000L
        val record = retryableErrorRecord(page = 2, attemptCount = 1, nextRetryAt = futureRetryAt)
        assertNull(probe(listOf(successRecord(1), record)))
    }

    @Test
    fun `due retryable failure on cap page 20 is selected for retry`() {
        val cap = RecommendationDiscoveryPlanner.MAX_DISCOVERY_PAGE_PER_SOURCE_QUERY
        val pastRetryAt = now - 1000L
        val record = retryableErrorRecord(page = cap, attemptCount = 1, nextRetryAt = pastRetryAt)
        // A due retry of the final page is allowed; the cap only blocks creating a NEW page past it.
        assertEquals(cap, probe(listOf(successRecord(cap - 1), record)))
    }

    @Test
    fun `new page past cap 21 is never created after exhausted cap page`() {
        val cap = RecommendationDiscoveryPlanner.MAX_DISCOVERY_PAGE_PER_SOURCE_QUERY
        // Page 20 exhausted its retries → advanceable, but page 21 exceeds the cap → null.
        assertNull(probe(listOf(successRecord(cap - 1), exhaustedRecord(cap))))
    }

    @Test
    fun `retryable failure with null nextRetryAt is treated as due`() {
        val record = retryableErrorRecord(page = 2, attemptCount = 1, nextRetryAt = null)
        assertEquals(2, probe(listOf(successRecord(1), record)))
    }

    // ---- isExhausted ----

    @Test
    fun `isExhausted returns true for empty newPageUrls`() {
        assert(RecommendationDiscoveryPlanner.isExhausted(emptySet(), setOf("url1", "url2")))
    }

    @Test
    fun `isExhausted returns true when all new urls are already known`() {
        val known = setOf("url1", "url2", "url3")
        assert(RecommendationDiscoveryPlanner.isExhausted(setOf("url1", "url2"), known))
    }

    @Test
    fun `isExhausted returns false when at least one new url is not known`() {
        val known = setOf("url1", "url2")
        assert(!RecommendationDiscoveryPlanner.isExhausted(setOf("url1", "url3"), known))
    }

    // ---- EC-04 2026-09-01: planAdditionalPages (configurable discovery-effort policy) ----

    private fun plan(records: List<RecommendationDiscoveryProgress>, maxAdditionalPages: Int, nowMs: Long = now) =
        RecommendationDiscoveryPlanner.planAdditionalPages(records, maxAdditionalPages, nowMs)

    @Test
    fun `zero or negative maxAdditionalPages plans nothing — the Off effort level`() {
        assertEquals(emptyList<Int>(), plan(listOf(successRecord(1)), maxAdditionalPages = 0))
        assertEquals(emptyList<Int>(), plan(listOf(successRecord(1)), maxAdditionalPages = -1))
    }

    @Test
    fun `single-page plan matches nextPageToProbe exactly — preserves the pre-existing effort level`() {
        val records = listOf(successRecord(1))
        assertEquals(listOf(probe(records)), plan(records, maxAdditionalPages = 1))
    }

    @Test
    fun `multi-page plan advances the simulated frontier sequentially`() {
        // Only page 1 evaluated so far -> planning 3 additional pages should sequentially plan 2, 3, 4.
        val records = listOf(successRecord(1))
        assertEquals(listOf(2, 3, 4), plan(records, maxAdditionalPages = 3))
    }

    @Test
    fun `multi-page plan starting mid-frontier continues from the real frontier, not from 1`() {
        val records = listOf(successRecord(1), successRecord(2), successRecord(3))
        assertEquals(listOf(4, 5), plan(records, maxAdditionalPages = 2))
    }

    @Test
    fun `plan stops early when the lifetime cap is reached, never planning past it`() {
        val cap = RecommendationDiscoveryPlanner.MAX_DISCOVERY_PAGE_PER_SOURCE_QUERY
        val records = listOf(successRecord(cap - 1))
        // Requesting 5 additional pages from one page before the cap can only yield the cap page itself.
        assertEquals(listOf(cap), plan(records, maxAdditionalPages = 5))
    }

    @Test
    fun `plan is empty when there are no progress records yet — page 1 must be evaluated first`() {
        assertEquals(emptyList<Int>(), plan(emptyList(), maxAdditionalPages = 3))
    }

    @Test
    fun `a due retryable page can only be the first planned page, then the plan advances past it`() {
        val pastRetryAt = now - 1000L
        val records = listOf(successRecord(1), retryableErrorRecord(page = 2, nextRetryAt = pastRetryAt))
        // Page 2 is retried first; the simulated post-retry frontier then advances to 3.
        assertEquals(listOf(2, 3), plan(records, maxAdditionalPages = 2))
    }

    @Test
    fun `a not-yet-due retryable page halts planning entirely, matching nextPageToProbe`() {
        val futureRetryAt = now + 60_000L
        val records = listOf(successRecord(1), retryableErrorRecord(page = 2, nextRetryAt = futureRetryAt))
        assertEquals(emptyList<Int>(), plan(records, maxAdditionalPages = 3))
    }

    // ---- EC-04 2026-09-01: DiscoveryEffortLevel ----

    @Test
    fun `OFF plans zero additional pages`() {
        assertEquals(0, DiscoveryEffortLevel.OFF.additionalPagesPerRefresh)
    }

    @Test
    fun `STANDARD reproduces the pre-existing hardcoded page count exactly`() {
        assertEquals(
            RecommendationDiscoveryPlanner.MAX_NEW_PAGES_PER_SOURCE_REFRESH,
            DiscoveryEffortLevel.STANDARD.additionalPagesPerRefresh,
        )
    }

    @Test
    fun `STANDARD is the default, not OFF -- introducing configurability must not silently change existing behavior`() {
        assertEquals(DiscoveryEffortLevel.STANDARD, DiscoveryEffortLevel.DEFAULT)
    }

    @Test
    fun `EXTENDED plans more pages than STANDARD but stays a small bounded value`() {
        assert(DiscoveryEffortLevel.EXTENDED.additionalPagesPerRefresh > DiscoveryEffortLevel.STANDARD.additionalPagesPerRefresh)
        assert(DiscoveryEffortLevel.EXTENDED.additionalPagesPerRefresh <= RecommendationDiscoveryPlanner.MAX_DISCOVERY_PAGE_PER_SOURCE_QUERY)
    }

    @Test
    fun `resolve recovers every stored value exactly`() {
        DiscoveryEffortLevel.entries.forEach { level ->
            assertEquals(level, DiscoveryEffortLevel.resolve(level.storedValue))
        }
    }

    @Test
    fun `resolve falls back to the default for an unknown or blank stored value`() {
        assertEquals(DiscoveryEffortLevel.DEFAULT, DiscoveryEffortLevel.resolve(""))
        assertEquals(DiscoveryEffortLevel.DEFAULT, DiscoveryEffortLevel.resolve("nonsense"))
    }

    @Test
    fun `planAdditionalPages driven by every effort level behaves as documented`() {
        val records = listOf(successRecord(1))
        DiscoveryEffortLevel.entries.forEach { level ->
            val planned = plan(records, maxAdditionalPages = level.additionalPagesPerRefresh)
            assertEquals(level.additionalPagesPerRefresh, planned.size)
        }
    }
}
// KMK <--

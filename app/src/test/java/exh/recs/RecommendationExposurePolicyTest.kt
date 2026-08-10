package exh.recs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK_CLAUDE_LATEST_CATALOGUE_AND_EXPOSURE_PLAN_2026-08-08 -->
/**
 * Exposure-policy tests: window boundaries, malformed timestamps, repeated exposure, expiration,
 * and -- most importantly -- the product rule that an ignored card is not negative feedback.
 */
class RecommendationExposurePolicyTest {

    private val day = 24L * 60L * 60L * 1000L
    private val now = 1_800_000_000_000L

    // --- Window configuration ---

    @Test
    fun `the default exposure window is 14 days per the recorded product decision`() {
        assertEquals(14, RecommendationExposurePolicy.DEFAULT_WINDOW_DAYS)
    }

    @Test
    fun `supported windows are 7, 14 and 30 days`() {
        assertEquals(listOf(7, 14, 30), RecommendationExposurePolicy.SUPPORTED_WINDOW_DAYS)
    }

    @Test
    fun `a malformed window value falls back to the 14-day default`() {
        listOf(-1, 0, 1, 13, 15, 365, Int.MAX_VALUE).forEach {
            assertEquals(14, RecommendationExposurePolicy.validateWindowDays(it), "window $it must fall back")
        }
    }

    // --- Visible exposure event ---

    @Test
    fun `only a loaded, non-loading state with visible cards is an exposure event`() {
        assertTrue(RecommendationExposurePolicy.isVisibleExposureEvent(isLoading = false, hasLoadedState = true, visibleCandidateCount = 3))
    }

    @Test
    fun `loading, empty, and not-yet-loaded states are never exposure events`() {
        assertFalse(RecommendationExposurePolicy.isVisibleExposureEvent(isLoading = true, hasLoadedState = true, visibleCandidateCount = 3))
        assertFalse(RecommendationExposurePolicy.isVisibleExposureEvent(isLoading = false, hasLoadedState = false, visibleCandidateCount = 3))
        assertFalse(RecommendationExposurePolicy.isVisibleExposureEvent(isLoading = false, hasLoadedState = true, visibleCandidateCount = 0))
    }

    // --- Window boundary ---

    @Test
    fun `an exposure inside the window is in-window and one outside is not`() {
        assertTrue(RecommendationExposurePolicy.isWithinWindow(now, now - 13 * day, 14))
        assertFalse(RecommendationExposurePolicy.isWithinWindow(now, now - 15 * day, 14))
    }

    @Test
    fun `the 14-day boundary itself is exclusive`() {
        assertTrue(RecommendationExposurePolicy.isWithinWindow(now, now - (14 * day - 1), 14))
        assertFalse(RecommendationExposurePolicy.isWithinWindow(now, now - 14 * day, 14))
    }

    @Test
    fun `a non-positive stored timestamp is treated as no usable exposure data`() {
        assertFalse(RecommendationExposurePolicy.isWithinWindow(now, 0L, 14))
        assertFalse(RecommendationExposurePolicy.isWithinWindow(now, -1L, 14))
    }

    @Test
    fun `a future timestamp from a clock change or restored backup is treated as in-window`() {
        assertTrue(RecommendationExposurePolicy.isWithinWindow(now, now + 5 * day, 14))
    }

    // --- Ignored is not dislike ---

    @Test
    fun `a candidate in the library, rated, or tracked is never untouched`() {
        assertFalse(RecommendationExposurePolicy.isUntouched(isInLibrary = true, isRated = false, isTracked = false, lastInteractionAt = null))
        assertFalse(RecommendationExposurePolicy.isUntouched(isInLibrary = false, isRated = true, isTracked = false, lastInteractionAt = null))
        assertFalse(RecommendationExposurePolicy.isUntouched(isInLibrary = false, isRated = false, isTracked = true, lastInteractionAt = null))
        assertFalse(RecommendationExposurePolicy.isUntouched(isInLibrary = false, isRated = false, isTracked = false, lastInteractionAt = now))
    }

    @Test
    fun `no interaction signal at all means untouched -- an unknown outcome, not a dislike`() {
        assertTrue(RecommendationExposurePolicy.isUntouched(isInLibrary = false, isRated = false, isTracked = false, lastInteractionAt = null))
        assertTrue(RecommendationExposurePolicy.isUntouched(isInLibrary = false, isRated = false, isTracked = false, lastInteractionAt = 0L))
    }

    @Test
    fun `an interacted candidate never receives any penalty, however often it was shown`() {
        val penalty = RecommendationExposurePolicy.softPenalty(
            now = now,
            lastExposedAt = now,
            exposureCount = 50,
            windowDays = 14,
            isUntouched = false,
        )
        assertEquals(0.0, penalty)
    }

    // --- Penalty shape ---

    @Test
    fun `a single sighting is not repetition and earns no penalty`() {
        assertEquals(
            0.0,
            RecommendationExposurePolicy.softPenalty(now, now, exposureCount = 1, windowDays = 14, isUntouched = true),
        )
        assertEquals(
            0.0,
            RecommendationExposurePolicy.softPenalty(now, now, exposureCount = 0, windowDays = 14, isUntouched = true),
        )
    }

    @Test
    fun `repeated recent sightings increase the penalty monotonically`() {
        val p2 = RecommendationExposurePolicy.softPenalty(now, now, 2, 14, true)
        val p3 = RecommendationExposurePolicy.softPenalty(now, now, 3, 14, true)
        val p4 = RecommendationExposurePolicy.softPenalty(now, now, 4, 14, true)
        assertTrue(p2 > 0.0, "two sightings should earn a penalty")
        assertTrue(p3 > p2, "three sightings should exceed two")
        assertTrue(p4 > p3, "four sightings should exceed three")
    }

    @Test
    fun `the penalty saturates and never exceeds the documented ceiling`() {
        val p4 = RecommendationExposurePolicy.softPenalty(now, now, 4, 14, true)
        val p100 = RecommendationExposurePolicy.softPenalty(now, now, 100, 14, true)
        assertEquals(p4, p100, "penalty must saturate at SATURATION_EXPOSURE_COUNT")
        assertTrue(p100 <= RecommendationExposurePolicy.MAX_PENALTY)
    }

    @Test
    fun `an older sighting inside the window is penalised less than a fresh one`() {
        val fresh = RecommendationExposurePolicy.softPenalty(now, now, 4, 14, true)
        val older = RecommendationExposurePolicy.softPenalty(now, now - 13 * day, 4, 14, true)
        assertTrue(older < fresh, "recency damping should reduce the penalty as the sighting ages")
        assertTrue(older >= 0.0)
    }

    @Test
    fun `the penalty expires after the 14-day window -- only the penalty, not the candidate`() {
        assertEquals(
            0.0,
            RecommendationExposurePolicy.softPenalty(now, now - 15 * day, exposureCount = 10, windowDays = 14, isUntouched = true),
        )
    }

    @Test
    fun `a shorter configured window expires the penalty sooner`() {
        val exposedAt = now - 10 * day
        assertTrue(RecommendationExposurePolicy.softPenalty(now, exposedAt, 4, 14, true) > 0.0)
        assertEquals(0.0, RecommendationExposurePolicy.softPenalty(now, exposedAt, 4, 7, true))
    }

    @Test
    fun `missing exposure data fails open to no penalty`() {
        assertEquals(0.0, RecommendationExposurePolicy.softPenalty(now, lastExposedAt = 0L, exposureCount = 5, windowDays = 14, isUntouched = true))
    }

    // --- Pruning ---

    @Test
    fun `prune cutoff includes a grace period beyond the configured window`() {
        val cutoff = RecommendationExposurePolicy.pruneBefore(now, 14)
        assertEquals(now - (14 + RecommendationExposurePolicy.PRUNE_GRACE_DAYS) * day, cutoff)
        // A row still inside the window is never eligible for pruning.
        assertTrue(now - 13 * day > cutoff)
    }

    @Test
    fun `prune cutoff uses the validated window for a malformed value`() {
        assertEquals(
            RecommendationExposurePolicy.pruneBefore(now, 14),
            RecommendationExposurePolicy.pruneBefore(now, 999),
        )
    }
}
// KMK <--

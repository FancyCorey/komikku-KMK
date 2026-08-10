package exh.recs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// KMK -->
class AdjustStatusesForDedupeTest {

    private fun shown(sourceId: Long, visibleCount: Int = 5) = sourceId to RecommendationSourceRunStatus(
        sourceId = sourceId,
        status = RecommendationSourceStatus.Shown,
        visibleCount = visibleCount,
        updatedAt = 1000L,
    )

    private fun status(sourceId: Long, s: RecommendationSourceStatus) = sourceId to RecommendationSourceRunStatus(
        sourceId = sourceId,
        status = s,
        visibleCount = 0,
        updatedAt = 1000L,
    )

    @Test
    fun `source with visible results keeps Shown status`() {
        val statuses = mapOf(shown(1L))
        val result = adjustStatusesForDedupe(statuses, mapOf(1L to true))
        assertEquals(RecommendationSourceStatus.Shown, result[1L]!!.status)
        assertEquals(5, result[1L]!!.visibleCount)
    }

    @Test
    fun `source with no post-dedupe results becomes HiddenByDuplicateHandling`() {
        val statuses = mapOf(shown(1L, visibleCount = 10))
        val result = adjustStatusesForDedupe(statuses, mapOf(1L to false))
        assertEquals(RecommendationSourceStatus.HiddenByDuplicateHandling, result[1L]!!.status)
        assertEquals(0, result[1L]!!.visibleCount)
    }

    @Test
    fun `source not in dedupe map keeps Shown status`() {
        val statuses = mapOf(shown(1L))
        val result = adjustStatusesForDedupe(statuses, emptyMap())
        assertEquals(RecommendationSourceStatus.Shown, result[1L]!!.status)
    }

    @Test
    fun `NoMatches source is not changed by dedupe adjustment`() {
        val statuses = mapOf(status(2L, RecommendationSourceStatus.NoMatches))
        val result = adjustStatusesForDedupe(statuses, mapOf(2L to false))
        assertEquals(RecommendationSourceStatus.NoMatches, result[2L]!!.status)
    }

    @Test
    fun `FilteredOut source is not changed by dedupe adjustment`() {
        val statuses = mapOf(status(3L, RecommendationSourceStatus.FilteredOut))
        val result = adjustStatusesForDedupe(statuses, mapOf(3L to false))
        assertEquals(RecommendationSourceStatus.FilteredOut, result[3L]!!.status)
    }

    @Test
    fun `Error source is not changed by dedupe adjustment`() {
        val statuses = mapOf(status(4L, RecommendationSourceStatus.Error))
        val result = adjustStatusesForDedupe(statuses, mapOf(4L to false))
        assertEquals(RecommendationSourceStatus.Error, result[4L]!!.status)
    }

    @Test
    fun `mixed sources adjust only the Shown ones with no visible results`() {
        val statuses = mapOf(
            shown(1L, 8), // visible post-dedupe → stays Shown
            shown(2L, 5), // hidden post-dedupe → HiddenByDuplicateHandling
            status(3L, RecommendationSourceStatus.NoMatches), // unchanged
            status(4L, RecommendationSourceStatus.Error), // unchanged
        )
        val visible = mapOf(1L to true, 2L to false, 3L to false, 4L to false)
        val result = adjustStatusesForDedupe(statuses, visible)
        assertEquals(RecommendationSourceStatus.Shown, result[1L]!!.status)
        assertEquals(8, result[1L]!!.visibleCount)
        assertEquals(RecommendationSourceStatus.HiddenByDuplicateHandling, result[2L]!!.status)
        assertEquals(0, result[2L]!!.visibleCount)
        assertEquals(RecommendationSourceStatus.NoMatches, result[3L]!!.status)
        assertEquals(RecommendationSourceStatus.Error, result[4L]!!.status)
    }

    @Test
    fun `empty statuses returns empty map`() {
        val result = adjustStatusesForDedupe(emptyMap(), mapOf(1L to false))
        assertEquals(emptyMap<Long, RecommendationSourceRunStatus>(), result)
    }

    @Test
    fun `updatedAt is preserved on adjustment`() {
        val original = RecommendationSourceRunStatus(1L, RecommendationSourceStatus.Shown, 5, 99999L)
        val result = adjustStatusesForDedupe(mapOf(1L to original), mapOf(1L to false))
        assertEquals(99999L, result[1L]!!.updatedAt)
    }
}
// KMK <--

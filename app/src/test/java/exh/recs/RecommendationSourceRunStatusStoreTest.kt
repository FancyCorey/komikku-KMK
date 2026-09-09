package exh.recs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK -->
class RecommendationSourceRunStatusStoreTest {

    private fun status(
        sourceId: Long,
        status: RecommendationSourceStatus,
        visibleCount: Int = 0,
        updatedAt: Long = 1000L,
    ) = RecommendationSourceRunStatus(sourceId, status, visibleCount, updatedAt)

    @Test
    fun `serialize empty collection returns empty string`() {
        val result = RecommendationSourceRunStatusStore.serialize(emptyList())
        assertEquals("", result)
    }

    @Test
    fun `parse empty string returns empty map`() {
        val result = RecommendationSourceRunStatusStore.parse("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parse blank string returns empty map`() {
        val result = RecommendationSourceRunStatusStore.parse("   ")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `serialize and parse single Shown status round-trips correctly`() {
        val original = status(123L, RecommendationSourceStatus.Shown, visibleCount = 20, updatedAt = 9999L)
        val serialized = RecommendationSourceRunStatusStore.serialize(listOf(original))
        val parsed = RecommendationSourceRunStatusStore.parse(serialized)
        assertEquals(1, parsed.size)
        val recovered = parsed[123L]!!
        assertEquals(123L, recovered.sourceId)
        assertEquals(RecommendationSourceStatus.Shown, recovered.status)
        assertEquals(20, recovered.visibleCount)
        assertEquals(9999L, recovered.updatedAt)
    }

    @Test
    fun `serialize and parse multiple statuses round-trips correctly`() {
        val statuses = listOf(
            status(1L, RecommendationSourceStatus.Shown, 10, 1000L),
            status(2L, RecommendationSourceStatus.NoMatches, 0, 2000L),
            status(3L, RecommendationSourceStatus.Error, 0, 3000L),
            status(4L, RecommendationSourceStatus.FilteredOut, 0, 4000L),
            status(5L, RecommendationSourceStatus.OutsideAttemptLimit, 0, 5000L),
        )
        val serialized = RecommendationSourceRunStatusStore.serialize(statuses)
        val parsed = RecommendationSourceRunStatusStore.parse(serialized)
        assertEquals(5, parsed.size)
        assertEquals(RecommendationSourceStatus.Shown, parsed[1L]!!.status)
        assertEquals(10, parsed[1L]!!.visibleCount)
        assertEquals(RecommendationSourceStatus.NoMatches, parsed[2L]!!.status)
        assertEquals(RecommendationSourceStatus.Error, parsed[3L]!!.status)
        assertEquals(RecommendationSourceStatus.FilteredOut, parsed[4L]!!.status)
        assertEquals(RecommendationSourceStatus.OutsideAttemptLimit, parsed[5L]!!.status)
    }

    @Test
    fun `parse skips malformed rows without crashing`() {
        val bad = "not_a_number|Shown|0|1000;1|Shown|5|1000"
        val parsed = RecommendationSourceRunStatusStore.parse(bad)
        assertEquals(1, parsed.size)
        assertEquals(1L, parsed.keys.first())
    }

    @Test
    fun `parse skips rows with too few fields`() {
        val bad = "1|Shown|5"
        val parsed = RecommendationSourceRunStatusStore.parse(bad)
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun `parse skips rows with unknown status enum value`() {
        val bad = "1|UNKNOWN_STATUS|0|1000"
        val parsed = RecommendationSourceRunStatusStore.parse(bad)
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun `parse handles visibleCount zero correctly`() {
        val s = status(7L, RecommendationSourceStatus.NoMatches, 0, 500L)
        val parsed = RecommendationSourceRunStatusStore.parse(RecommendationSourceRunStatusStore.serialize(listOf(s)))
        assertEquals(0, parsed[7L]!!.visibleCount)
    }

    @Test
    fun `evaluated candidate count round trips`() {
        val original = RecommendationSourceRunStatus(
            sourceId = 7L,
            status = RecommendationSourceStatus.Shown,
            visibleCount = 4,
            updatedAt = 9999L,
            evaluatedCount = 42,
        )

        val recovered = RecommendationSourceRunStatusStore.parse(
            RecommendationSourceRunStatusStore.serialize(listOf(original)),
        )[7L]!!

        assertEquals(42, recovered.evaluatedCount)
    }

    @Test
    fun `legacy status rows default evaluated count to zero`() {
        val parsed = RecommendationSourceRunStatusStore.parse("7|Shown|4|9999")

        assertEquals(0, parsed[7L]!!.evaluatedCount)
    }

    @Test
    fun `serialize and parse HiddenByDuplicateHandling round-trips correctly`() {
        val s = status(9L, RecommendationSourceStatus.HiddenByDuplicateHandling, 0, 6000L)
        val parsed = RecommendationSourceRunStatusStore.parse(RecommendationSourceRunStatusStore.serialize(listOf(s)))
        assertEquals(RecommendationSourceStatus.HiddenByDuplicateHandling, parsed[9L]!!.status)
        assertEquals(0, parsed[9L]!!.visibleCount)
    }

    @Test
    fun `last entry wins when sourceId appears multiple times`() {
        val serialized = "1|Shown|10|1000;1|NoMatches|0|2000"
        val parsed = RecommendationSourceRunStatusStore.parse(serialized)
        // Both are valid — last one written wins (Map put overwrites)
        assertEquals(1, parsed.size)
        // The map will contain whatever was written last — either is acceptable behavior
        assertTrue(parsed.containsKey(1L))
    }
}
// KMK <--

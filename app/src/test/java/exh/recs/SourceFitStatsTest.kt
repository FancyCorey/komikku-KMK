package exh.recs

// KMK --> v0.7.45: Final Feature B — SourceFitStats serialization coverage for topPicksContributionCount
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SourceFitStatsTest {

    private fun stats(sourceId: Long = 1L, topPicksContributionCount: Int = 0) = SourceFitStats(
        sourceId = sourceId,
        runCount = 5,
        shownCount = 3,
        noMatchCount = 1,
        filteredOutCount = 1,
        errorCount = 0,
        hiddenByDuplicateCount = 0,
        totalVisibleCandidates = 10,
        lastSuccessAt = 1000L,
        lastErrorAt = 0L,
        updatedAt = 2000L,
        topPicksContributionCount = topPicksContributionCount,
    )

    @Test
    fun `serialize then parse preserves zero topPicksContributionCount`() {
        val original = mapOf(1L to stats(topPicksContributionCount = 0))
        val serialized = SourceFitStatsStore.serialize(original.values)
        val parsed = SourceFitStatsStore.parse(serialized)
        assertEquals(0, parsed[1L]?.topPicksContributionCount)
    }

    @Test
    fun `serialize then parse preserves nonzero topPicksContributionCount`() {
        val original = mapOf(1L to stats(topPicksContributionCount = 42))
        val serialized = SourceFitStatsStore.serialize(original.values)
        val parsed = SourceFitStatsStore.parse(serialized)
        assertEquals(42, parsed[1L]?.topPicksContributionCount)
    }

    @Test
    fun `parse defaults topPicksContributionCount to zero for legacy rows without the field`() {
        // Legacy serialized row with only the original 11 fields (pre-v0.7.32 D1/D2 fields)
        val legacyRow = "1|5|3|1|1|0|0|10|1000|0|2000"
        val parsed = SourceFitStatsStore.parse(legacyRow)
        assertEquals(0, parsed[1L]?.topPicksContributionCount)
    }

    @Test
    fun `mergeRun increments topPicksContributionCount only for contributors`() {
        val statuses = listOf(
            RecommendationSourceRunStatus(sourceId = 1L, status = RecommendationSourceStatus.Shown, visibleCount = 3),
            RecommendationSourceRunStatus(sourceId = 2L, status = RecommendationSourceStatus.Shown, visibleCount = 2),
        )
        val result = SourceFitStatsStore.mergeRun(emptyMap(), statuses, topPicksContributors = setOf(1L))
        assertEquals(1, result[1L]?.topPicksContributionCount)
        assertEquals(0, result[2L]?.topPicksContributionCount)
    }

    @Test
    fun `round trip with multiple sources preserves each contribution count independently`() {
        val original = mapOf(
            1L to stats(sourceId = 1L, topPicksContributionCount = 3),
            2L to stats(sourceId = 2L, topPicksContributionCount = 0),
            3L to stats(sourceId = 3L, topPicksContributionCount = 17),
        )
        val serialized = SourceFitStatsStore.serialize(original.values)
        val parsed = SourceFitStatsStore.parse(serialized)
        assertEquals(3, parsed[1L]?.topPicksContributionCount)
        assertEquals(0, parsed[2L]?.topPicksContributionCount)
        assertEquals(17, parsed[3L]?.topPicksContributionCount)
    }
}
// KMK <--

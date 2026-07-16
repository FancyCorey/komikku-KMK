package exh.recs.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.TagPreference
import tachiyomi.domain.taste.model.TagTaste

// KMK v0.8.7 -->
class RecommendationSettingsSectionSummariesTest {

    private fun tag(name: String, preference: TagPreference) =
        TagTaste(normalizedTag = name.lowercase(), displayName = name, preference = preference.value, createdAt = 0L, updatedAt = 0L)

    @Test
    fun `an empty tag list produces zero counts`() {
        val counts = RecommendationSettingsSectionSummaries.tagCounts(emptyList())
        assertEquals(0, counts.preferred)
        assertEquals(0, counts.blocked)
    }

    @Test
    fun `preferred and blocked tags are counted separately`() {
        val tags = listOf(
            tag("Action", TagPreference.PREFER),
            tag("Adventure", TagPreference.PREFER),
            tag("Ecchi", TagPreference.BLOCK),
        )
        val counts = RecommendationSettingsSectionSummaries.tagCounts(tags)
        assertEquals(2, counts.preferred)
        assertEquals(1, counts.blocked)
    }

    @Test
    fun `disliked tags are not counted as preferred or blocked`() {
        val tags = listOf(tag("Slice of Life", TagPreference.DISLIKE))
        val counts = RecommendationSettingsSectionSummaries.tagCounts(tags)
        assertEquals(0, counts.preferred)
        assertEquals(0, counts.blocked)
    }

    @Test
    fun `counts update when the tag list changes - not a fixed snapshot`() {
        val before = RecommendationSettingsSectionSummaries.tagCounts(listOf(tag("Action", TagPreference.PREFER)))
        val after = RecommendationSettingsSectionSummaries.tagCounts(
            listOf(tag("Action", TagPreference.PREFER), tag("Romance", TagPreference.PREFER)),
        )
        assertEquals(1, before.preferred)
        assertEquals(2, after.preferred)
    }

    // --- sourcePriorityCounts ---

    private val orderedSources = listOf(1L to "MangaDex", 2L to "Comick", 3L to "Batoto")

    @Test
    fun `with nothing disabled, every source is enabled and the first in order is the top source`() {
        val counts = RecommendationSettingsSectionSummaries.sourcePriorityCounts(orderedSources, emptySet())
        assertEquals(3, counts.enabledCount)
        assertEquals("MangaDex", counts.topSourceName)
    }

    @Test
    fun `disabling the top source promotes the next enabled source to top`() {
        val counts = RecommendationSettingsSectionSummaries.sourcePriorityCounts(orderedSources, setOf(1L))
        assertEquals(2, counts.enabledCount)
        assertEquals("Comick", counts.topSourceName)
    }

    @Test
    fun `disabling every source reports zero enabled and no top source`() {
        val counts = RecommendationSettingsSectionSummaries.sourcePriorityCounts(orderedSources, setOf(1L, 2L, 3L))
        assertEquals(0, counts.enabledCount)
        assertEquals(null, counts.topSourceName)
    }

    @Test
    fun `an empty source list reports zero enabled and no top source`() {
        val counts = RecommendationSettingsSectionSummaries.sourcePriorityCounts(emptyList(), emptySet())
        assertEquals(0, counts.enabledCount)
        assertEquals(null, counts.topSourceName)
    }
}
// KMK <--

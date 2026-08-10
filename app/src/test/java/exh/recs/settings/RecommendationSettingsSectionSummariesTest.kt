package exh.recs.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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

    private val orderedSources = listOf(1L to "MangaDex", 2L to "Comick", 3L to "Batoto")

    // --- sourcePrioritySummary (KMK
    // Regression coverage for the centralized privacy branch: the summary's topSourceLabel must
    // never equal a raw source name when evaluationModeEnabled is true, on every input shape below.

    @Test
    fun `no enabled sources yields a null label regardless of Evaluation Mode`() {
        val summaryOff = RecommendationSettingsSectionSummaries.sourcePrioritySummary(orderedSources, setOf(1L, 2L, 3L), evaluationModeEnabled = false)
        val summaryOn = RecommendationSettingsSectionSummaries.sourcePrioritySummary(orderedSources, setOf(1L, 2L, 3L), evaluationModeEnabled = true)
        assertEquals(0, summaryOff.enabledCount)
        assertEquals(null, summaryOff.topSourceLabel)
        assertEquals(0, summaryOn.enabledCount)
        assertEquals(null, summaryOn.topSourceLabel)
    }

    @Test
    fun `all sources disabled yields a null label`() {
        val summary = RecommendationSettingsSectionSummaries.sourcePrioritySummary(orderedSources, setOf(1L, 2L, 3L), evaluationModeEnabled = true)
        assertEquals(null, summary.topSourceLabel)
    }

    @Test
    fun `one enabled source with Evaluation Mode off preserves the real display name`() {
        val summary = RecommendationSettingsSectionSummaries.sourcePrioritySummary(orderedSources, setOf(2L, 3L), evaluationModeEnabled = false)
        assertEquals(1, summary.enabledCount)
        assertEquals("MangaDex", summary.topSourceLabel)
    }

    @Test
    fun `disabled first source with a later enabled source promotes that source, in either mode`() {
        val off = RecommendationSettingsSectionSummaries.sourcePrioritySummary(orderedSources, setOf(1L), evaluationModeEnabled = false)
        assertEquals("Comick", off.topSourceLabel)
        val on = RecommendationSettingsSectionSummaries.sourcePrioritySummary(orderedSources, setOf(1L), evaluationModeEnabled = true)
        assertEquals(exh.util.EvaluationModeFormatter.sourceLabel(2L), on.topSourceLabel)
    }

    @Test
    fun `Evaluation Mode true with a numeric source ID never returns the raw source name`() {
        val summary = RecommendationSettingsSectionSummaries.sourcePrioritySummary(orderedSources, emptySet(), evaluationModeEnabled = true)
        assertEquals(3, summary.enabledCount)
        assertEquals(exh.util.EvaluationModeFormatter.sourceLabel(1L), summary.topSourceLabel)
        assertTrue(summary.topSourceLabel != "MangaDex", "Evaluation Mode must never surface the raw source name")
        assertTrue(summary.topSourceLabel != "Comick" && summary.topSourceLabel != "Batoto", "must not leak any other order's raw name either")
    }

    @Test
    fun `Evaluation Mode false preserves the real display name even with several sources`() {
        val summary = RecommendationSettingsSectionSummaries.sourcePrioritySummary(orderedSources, emptySet(), evaluationModeEnabled = false)
        assertEquals("MangaDex", summary.topSourceLabel)
    }

    @Test
    fun `source summary output contains no raw source name when Evaluation Mode is true, across every disabled combination`() {
        val rawNames = orderedSources.map { it.second }.toSet()
        val allSubsets = (0..7).map { mask ->
            orderedSources.filterIndexed { index, _ -> (mask shr index) and 1 == 1 }.map { it.first }.toSet()
        }
        for (disabled in allSubsets) {
            val summary = RecommendationSettingsSectionSummaries.sourcePrioritySummary(orderedSources, disabled, evaluationModeEnabled = true)
            if (summary.topSourceLabel != null) {
                assertTrue(summary.topSourceLabel !in rawNames, "leaked a raw source name for disabled=$disabled: ${summary.topSourceLabel}")
            }
        }
    }
}
// KMK <--

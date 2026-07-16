package exh.recs.settings

import tachiyomi.domain.taste.model.TagPreference
import tachiyomi.domain.taste.model.TagTaste

// KMK v0.8.7 -->
/**
 * Pure helpers that derive the small pieces of state each Recommendation Settings section header
 * summary (plan section 5.2) is built from. Kept separate from the Compose layer (which only adds
 * `stringResource(...)` formatting around these) so the "summary reflects current state, updates
 * after a preference change" contract is directly unit testable without Compose.
 */
object RecommendationSettingsSectionSummaries {

    data class TagCounts(val preferred: Int, val blocked: Int)

    /** Counts of PREFER and BLOCK tag preferences. This codebase's [TagPreference] has no distinct "neutral" tier — only PREFER/DISLIKE/BLOCK — so the plan's "neutral" example maps to nothing counted here (documented deviation). */
    fun tagCounts(tags: List<TagTaste>): TagCounts {
        var preferred = 0
        var blocked = 0
        for (tag in tags) {
            when (TagPreference.fromValue(tag.preference)) {
                TagPreference.PREFER -> preferred++
                TagPreference.BLOCK -> blocked++
                else -> {}
            }
        }
        return TagCounts(preferred, blocked)
    }

    data class SourcePriorityCounts(val enabledCount: Int, val topSourceName: String?)

    /** @param orderedSourceIdsAndNames every source in its current priority order, paired with its display name. @param disabledSourceIds the currently disabled subset. */
    fun sourcePriorityCounts(orderedSourceIdsAndNames: List<Pair<Long, String>>, disabledSourceIds: Set<Long>): SourcePriorityCounts {
        val enabled = orderedSourceIdsAndNames.filter { (id, _) -> id !in disabledSourceIds }
        return SourcePriorityCounts(enabled.size, enabled.firstOrNull()?.second)
    }
}
// KMK <--

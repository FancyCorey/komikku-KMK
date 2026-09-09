package exh.recs.settings

import tachiyomi.domain.taste.model.TagPreference
import tachiyomi.domain.taste.model.TagTaste

// KMK v0.8.7 -->
/**
 * Pure helpers that derive the small pieces of state each Recommendation Settings section header
 * summary (documented behavior) is built from. Kept separate from the Compose layer (which only adds
 * `stringResource(...)` formatting around these) so the "summary reflects current state, updates
 * after a preference change" contract is directly unit testable without Compose.
 */
object RecommendationSettingsSectionSummaries {

    data class TagCounts(val preferred: Int, val blocked: Int)

    /** Counts of PREFER and BLOCK tag preferences. This codebase's [TagPreference] has no distinct "neutral" tier — only PREFER/DISLIKE/BLOCK — so the behavior contract's "neutral" example maps to nothing counted here (documented deviation). */
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

    // KMK -->
    /**
     * Already-safe-to-render presentation model for the "For You sources" section summary.
     * [topSourceLabel] is never the raw source name when [SourcePrioritySummary] was built with
     * `evaluationModeEnabled = true` -- callers must not read a raw name from anywhere else and
     * substitute it in. This centralizes the exact privacy branch that used to live inline in
     * `RecommendationSourcePrioritySettingsScreen.kt`'s Composable body, so every summary call site
     * (index/search/detail/preview) can share one pure, unit-testable decision instead of each
     * re-implementing the Evaluation Mode check.
     */
    data class SourcePrioritySummary(val enabledCount: Int, val topSourceLabel: String?)

    /**
     * @param orderedSourceIdsAndNames every source in its current priority order, paired with its
     * display name, in the exact order used to resolve "the top enabled source."
     * @param disabledSourceIds the currently disabled subset.
     * @param evaluationModeEnabled when true, [SourcePrioritySummary.topSourceLabel] is always a
     * stable opaque [exh.util.EvaluationModeFormatter] label, never [orderedSourceIdsAndNames]'s raw
     * name -- even if the caller passes a source whose id could not be resolved, the fallback below
     * still uses an opaque key derived from that source's own id, never its name.
     */
    fun sourcePrioritySummary(
        orderedSourceIdsAndNames: List<Pair<Long, String>>,
        disabledSourceIds: Set<Long>,
        evaluationModeEnabled: Boolean,
    ): SourcePrioritySummary {
        val enabled = orderedSourceIdsAndNames.filter { (id, _) -> id !in disabledSourceIds }
        val top = enabled.firstOrNull()
        val label = when {
            top == null -> null
            evaluationModeEnabled -> exh.util.EvaluationModeFormatter.sourceLabel(top.first)
            else -> top.second
        }
        return SourcePrioritySummary(enabled.size, label)
    }
    // KMK <--
}
// KMK <--

package exh.recs

import tachiyomi.domain.taste.model.RecommendationDiscoveryProgress

// KMK v0.8.13 -->
/**
 * Pure policy deciding whether a source's persisted [RecommendationQueryStrategyType] hint is still
 * trustworthy, or whether it must be treated as stale and ignored/forgotten.
 *
 * ## Why this exists
 *
 * `recommendationSourceStrategies()` persists the last strategy that produced a visible result per
 * source, so the next refresh can start there as a fast path. But a persisted strategy is only a
 * hint -- if it stops working (the source's catalogue changes, or it was recorded before a scoring/
 * filtering fix), nothing previously cleared it. Two failure modes were confirmed on a live device
 * (v0.8.13 source-retrieval investigation):
 *
 * 1. [RecommendationQueryStrategyType.TEXT_ONLY_TOP_TAGS] is terminal in
 *    [RecommendationQueryPlanner] -- once persisted, [RecommendationQueryPlanner.buildPlans] used to
 *    return *only* that one plan, permanently trapping the source in the least reliable strategy
 *    (searching literal tag words as free text, which most source search backends AND-match against
 *    titles only) with no strict-to-lenient retry.
 * 2. A failed run (`NoMatches`/`FilteredOut`/`Error`) never removed the stale persisted strategy --
 *    `BrowsePersonalRecommendationsScreenModel.load()` only *wrote* a strategy on success, never
 *    forgot one on failure.
 *
 * This object is the single decision point for both problems: [effectiveStartingStrategy] decides
 * what hint (if any) a refresh should actually trust before calling
 * [RecommendationQueryPlanner.buildPlans], and [shouldForgetAfterRun] decides whether a completed
 * run's outcome disproves that hint enough to erase it for next time.
 */
internal object RecommendationStrategyRecoveryPolicy {

    /**
     * Decides which persisted strategy (if any) a refresh should actually start from.
     *
     * @param lastStrategy the raw persisted hint for this source, or null if none is stored.
     * @param recentProgress this source's discovery-progress rows for the current query signature
     * (any page), used to detect a hint that has already proven itself unproductive.
     * @param rememberedCount how many candidate-memory rows this source/query already has -- a
     * non-zero count is independent evidence the persisted strategy previously worked.
     * @param lastRunStatus the most recently recorded run status for this source, or null if none.
     */
    fun effectiveStartingStrategy(
        lastStrategy: RecommendationQueryStrategyType?,
        recentProgress: List<RecommendationDiscoveryProgress>,
        rememberedCount: Int,
        lastRunStatus: RecommendationSourceRunStatus?,
    ): RecommendationQueryStrategyType? {
        if (lastStrategy == null) return null

        val progressForStrategy = recentProgress.filter { it.queryStrategy == lastStrategy.name }
        val hasSuccessfulProgress = progressForStrategy.any {
            it.status == RecommendationDiscoveryProgress.STATUS_SUCCESS && it.visibleCount > 0
        }

        // TEXT_ONLY_TOP_TAGS is terminal in the planner -- trapping a source there forever once it
        // stops producing raw results is exactly the bug this policy exists to prevent. Only trust
        // it again when there is independent evidence it still works: remembered candidate memory,
        // or a progress row proving a past visible success.
        if (lastStrategy == RecommendationQueryStrategyType.TEXT_ONLY_TOP_TAGS) {
            val everFoundRaw = progressForStrategy.any { it.rawCount > 0 || it.visibleCount > 0 }
            if (rememberedCount == 0 && !everFoundRaw) return null
        }

        // The most recent run for this source directly disproving the hint (no matches, filtered to
        // nothing, or errored) is stronger evidence than older progress rows -- unless progress shows
        // this exact strategy has, at some point, produced a visible result.
        if (lastRunStatus != null &&
            lastRunStatus.status in FAILING_STATUSES &&
            !hasSuccessfulProgress
        ) {
            return null
        }

        // Repeated STATUS_EMPTY rows for this exact strategy (no raw results at all, more than once)
        // is the same signal as a stale terminal lock without needing a fresh lastRunStatus.
        val emptyCount = progressForStrategy.count { it.status == RecommendationDiscoveryProgress.STATUS_EMPTY }
        if (emptyCount >= 2 && !hasSuccessfulProgress) return null

        return lastStrategy
    }

    /** Statuses that disprove a persisted strategy hint and should trigger [shouldForgetAfterRun]. */
    private val FAILING_STATUSES = setOf(
        RecommendationSourceStatus.NoMatches,
        RecommendationSourceStatus.FilteredOut,
        RecommendationSourceStatus.Error,
        RecommendationSourceStatus.HiddenByDuplicateHandling,
    )

    /**
     * Whether a completed run's [outcome] disproves its persisted strategy hint enough that it
     * should be removed from `recommendationSourceStrategies()` for this source. Only the current
     * source's entry is ever removed -- callers must never clear the whole strategy map.
     */
    fun shouldForgetAfterRun(outcome: RecommendationSourceRunStatus): Boolean =
        outcome.status in FAILING_STATUSES
}
// KMK <--

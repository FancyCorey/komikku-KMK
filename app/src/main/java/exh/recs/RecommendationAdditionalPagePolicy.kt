package exh.recs

// KMK v0.8.13 -->
/**
 * Pure decision for whether `BrowsePersonalRecommendationsScreenModel.searchSource()` should call
 * `discoverAdditionalPage(...)` after a plan's page-1 search.
 *
 * ## Why this exists
 *
 * Confirmed on a live device: `recommendation_discovery_progress` had many rows for the affected source
 * advancing pages 2 through 8+ for `TEXT_ONLY_TOP_TAGS` with `raw_count=0`/`visible_count=0` on every
 * page. `searchSource()` called `discoverAdditionalPage(...)` unconditionally after page 1, gated
 * only by `recommendations.size < minUseful && plan != plans.last()` -- which does not prevent
 * advancement when the plan *is* the last one tried (exactly the stale-terminal-strategy case Phase A
 * fixes), or when page 1's `rawSMangas` was already empty. Probing pages 2-20 of a query that already
 * proved it returns nothing raw on page 1 is wasted work that only reinforces the false "no matches"
 * state -- it can never turn a zero-raw broad text-only query into a useful one.
 */
internal object RecommendationAdditionalPagePolicy {

    /**
     * @param planType the query strategy whose page-1 search just ran.
     * @param pageOneRawCount raw (pre-filter) manga count returned by page 1.
     * @param pageOneVisibleCount visible (post-filter/score) candidate count from page 1.
     * @param lastError a recoverable source error from page 1, if any.
     */
    fun shouldDiscoverAdditionalPage(
        planType: RecommendationQueryStrategyType,
        pageOneRawCount: Int,
        pageOneVisibleCount: Int,
        lastError: Throwable?,
    ): Boolean {
        if (lastError != null) return false
        if (pageOneRawCount == 0) return false
        // Redundant with the pageOneRawCount == 0 check above for the exact TEXT_ONLY_TOP_TAGS case
        // named in the plan, kept as an explicit rule so the broad-text-only case reads as a named
        // decision rather than an accidental side effect of the general zero-raw rule.
        if (planType == RecommendationQueryStrategyType.TEXT_ONLY_TOP_TAGS && pageOneRawCount == 0) return false
        return true
    }
}
// KMK <--

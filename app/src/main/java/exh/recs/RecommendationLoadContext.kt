package exh.recs

// KMK v0.8.6 -->
/**
 * Explicit load context for recommendation/source result loading, replacing ad-hoc Boolean flags.
 *
 * - [FOR_YOU]: the existing BrowsePersonalRecommendationsScreenModel behavior, budgeted by
 *   ForYouResultBudgetPolicy. Unaffected by this enum's introduction.
 * - [GROUP_PREVIEW]: the initial cross-extension group-recommendation row for one source, budgeted
 *   by GroupPreviewBudgetPolicy (default 10). Bounded concurrency and preview timeouts apply here.
 * - [FULL_SOURCE]: a user-expanded source row using existing full paging. Must never inherit the
 *   GROUP_PREVIEW budget, timeout, or concurrency cap.
 *
 * Normal global search (eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchScreenModel) is
 * intentionally outside this model and remains uncapped.
 */
enum class RecommendationLoadContext {
    FOR_YOU,
    GROUP_PREVIEW,
    FULL_SOURCE,
}
// KMK <--

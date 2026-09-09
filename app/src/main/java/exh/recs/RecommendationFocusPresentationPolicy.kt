package exh.recs

// KMK -->
/**
 * Maps a transient focus selection onto an already-produced For You result without changing
 * source loading, durable taste, errors, or persistence.
 *
 * KMK v0.8.21-fix5: R4/AUG-14 completion -- direct product correction (2026-08-25). This used to
 * also own a per-candidate focus-match confidence badge ([FocusMatchIndicator]'s star decoration);
 * that decorative UI was rejected by the product owner and removed entirely (see
 * BrowseBadges.kt/GlobalSearchCardRow.kt), so the badge machinery that only existed to feed it
 * (`FocusMatchBadge`, `focusMatchBadge()`, `FocusedPresentation.badgeFor`) is gone with it. What
 * remains -- mapping a focus selection onto an already-produced result -- is still needed, now to
 * produce a genuinely filtered view (see [RecommendationFocusPolicy]'s own doc) plus a
 * non-mutating "broader" view for the UI's "Show Broader Results" affordance.
 */
internal object RecommendationFocusPresentationPolicy {

    /**
     * [filtered]: the result as it should be shown by default when a focus is active --
     * `PersonalRecommendationResult.Success` with only matching candidates. [broader]: the exact
     * pre-filter result, for the transient "Show Broader Results" UI toggle only; never mutates
     * [RecommendationFocusPolicy.FocusCriteria] or any saved focus mode. [isFiltered] is false
     * when there is no active focus (both fields equal the original [result]).
     */
    data class FocusedPresentation(
        val filtered: PersonalRecommendationResult?,
        val broader: PersonalRecommendationResult?,
        val isFiltered: Boolean,
    )

    fun apply(
        result: PersonalRecommendationResult?,
        criteria: RecommendationFocusPolicy.FocusCriteria,
        aliasMap: Map<String, String> = emptyMap(),
    ): FocusedPresentation {
        if (result == null || !criteria.isActive) return FocusedPresentation(result, result, false)
        return when (result) {
            PersonalRecommendationResult.Loading,
            is PersonalRecommendationResult.Error,
            -> FocusedPresentation(result, result, false)
            is PersonalRecommendationResult.Success -> {
                when (val outcome = RecommendationFocusPolicy.apply(result.result, criteria, aliasMap)) {
                    RecommendationFocusPolicy.Outcome.Cleared -> FocusedPresentation(result, result, false)
                    is RecommendationFocusPolicy.Outcome.Filtered -> FocusedPresentation(
                        filtered = PersonalRecommendationResult.Success(outcome.matched.map { it.recommendation }),
                        broader = PersonalRecommendationResult.Success(outcome.all),
                        isFiltered = true,
                    )
                }
            }
        }
    }
}
// KMK <--

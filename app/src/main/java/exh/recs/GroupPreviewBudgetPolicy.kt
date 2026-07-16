package exh.recs

// KMK v0.8.6 -->
/**
 * Pure resolver for the user-configurable number of visible manga cards shown in the initial
 * per-extension preview of a group (cross-extension) recommendation row.
 *
 * This is a display/result-budget setting only, scoped exclusively to [RecommendationLoadContext.GROUP_PREVIEW].
 * It must never be applied to [RecommendationLoadContext.FOR_YOU] (see [ForYouResultBudgetPolicy]),
 * to [RecommendationLoadContext.FULL_SOURCE] paging, or to normal global search
 * (eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchScreenModel), which remains uncapped.
 *
 * Candidates must already be visibility-filtered and scored before this budget is applied; this
 * policy only trims the tail of an already-ranked list, it never selects raw provider-page order.
 */
object GroupPreviewBudgetPolicy {

    /** Every value the picker offers. Values outside this set (corrupt/future/past preference data) fall back to [DEFAULT]. */
    val SUPPORTED_VALUES = listOf(5, 10, 15, 20, 30)

    const val DEFAULT = 10

    /** Validates a stored/raw preference value, falling back to [DEFAULT] for anything not in [SUPPORTED_VALUES]. */
    fun validate(configuredValue: Int): Int = if (configuredValue in SUPPORTED_VALUES) configuredValue else DEFAULT

    /** Number of ranked candidates to keep for the initial preview row for one source. */
    fun previewCandidateBudget(configuredValue: Int): Int = validate(configuredValue)

    /**
     * Number of candidates from the preview budget that may be enriched (detail requests) for one
     * source's initial row. Enrichment is never larger than the preview budget itself, since there
     * is no point enriching manga that will not be shown in the preview.
     */
    fun previewEnrichmentBudget(configuredValue: Int): Int = validate(configuredValue)

    /** True when [candidateCount] already fits within the preview budget, meaning "open source" would show nothing new. */
    fun isExpansionEligible(configuredValue: Int, candidateCount: Int): Boolean = candidateCount > validate(configuredValue)
}
// KMK <--

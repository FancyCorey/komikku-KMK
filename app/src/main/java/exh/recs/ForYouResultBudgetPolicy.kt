package exh.recs

// KMK v0.8.2 -->
/**
 * Pure resolver for the user-configurable number of visible manga cards per ordinary For You
 * source row.
 *
 * This is a display/result-budget setting only. It must never be used to change source count,
 * source priority, enrichment call counts, Source Evaluation limits, Top Picks limits, or
 * query-attempt limits — every one of those has its own independent constant/policy.
 *
 * The three boosted sources (see `BrowsePersonalRecommendationsScreenModel.BOOSTED_SOURCE_COUNT`)
 * keep their existing 20-result contract as a floor: selecting a smaller normal value can never
 * shrink boosted rows below 20, but selecting 30 raises boosted rows to 30 too.
 */
object ForYouResultBudgetPolicy {

    /** The inclusive range accepted by the shared slider and exact-entry control. */
    const val MIN = 5
    const val MAX = 30

    /** Kept as a named range for callers that need to expose or test all valid values. */
    val SUPPORTED_VALUES = (MIN..MAX).toList()

    const val DEFAULT = 10

    /** The boosted-row floor this policy has always guaranteed (BrowsePersonalRecommendationsScreenModel's prior BOOSTED_RESULTS_PER_SOURCE). */
    const val BOOSTED_MINIMUM = 20

    /** Validates a stored/raw preference value, falling back to [DEFAULT] outside [MIN]..[MAX]. */
    fun validate(configuredValue: Int): Int = if (configuredValue in SUPPORTED_VALUES) configuredValue else DEFAULT

    /**
     * Resolves the visible-card budget for a single source row.
     * @param configuredValue the raw value read from the preference (validated internally).
     * @param isBoosted true for one of the fixed top-N boosted sources.
     */
    fun resolve(configuredValue: Int, isBoosted: Boolean): Int {
        val normal = validate(configuredValue)
        return if (isBoosted) maxOf(normal, BOOSTED_MINIMUM) else normal
    }
}
// KMK <--

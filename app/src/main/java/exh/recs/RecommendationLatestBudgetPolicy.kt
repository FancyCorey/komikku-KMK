package exh.recs

// KMK_CLAUDE_LATEST_CATALOGUE_AND_EXPOSURE_PLAN_2026-08-08 -->
/**
 * Pure resolver for the user-configurable Latest-catalogue exploration budget.
 *
 * The stored value is a **percentage of the refresh's attempted sources** that may additionally be
 * probed for Latest, converted here into an absolute attempt count. It is expressed as a percentage
 * (rather than a raw count) so the exploration share stays proportional as the user's enabled-source
 * list grows or shrinks, which is what the owning proposal's "controlled exploration, not
 * deterministic repetition" decision requires.
 *
 * Mirrors the established [ForYouResultBudgetPolicy] / `GroupPreviewBudgetPolicy` shape: a supported
 * value set, an explicit default, and a `validate()` that falls back rather than crashing on a
 * corrupt or future/legacy preference value.
 *
 * ## Default choice
 *
 * The packet proposed 20% as the starting point, decision-gated on fixture calibration. `20` is
 * therefore [DEFAULT]: with the pipeline's existing `MAX_SOURCE_ATTEMPTS` this yields a small
 * single-digit number of extra single-page probes per refresh, which keeps personalized search
 * clearly dominant while still giving Latest a real chance to contribute. `0` is offered as an
 * explicit off switch so the feature is fully reversible from settings without a code change --
 * this is the behavior kill switch the rollback plan depends on.
 */
object RecommendationLatestBudgetPolicy {

    /** Percentages the picker offers. `0` disables the Latest lane entirely. */
    val SUPPORTED_VALUES = listOf(0, 10, 20, 30, 50)

    /** Default exploration share, per the owning packet. */
    const val DEFAULT = 20

    /**
     * Never probe more than this many sources for Latest in a single refresh, regardless of
     * percentage or source count -- a hard ceiling so a large enabled-source list plus a high
     * percentage can never turn a bounded exploration probe into a crawl.
     */
    const val MAX_ATTEMPTS_PER_REFRESH = 6

    /** Validates a stored/raw preference value, falling back to [DEFAULT]. */
    fun validate(configuredPercent: Int): Int =
        if (configuredPercent in SUPPORTED_VALUES) configuredPercent else DEFAULT

    /**
     * Resolves the absolute number of Latest attempts allowed in one refresh.
     *
     * @param configuredPercent the raw preference value (validated internally).
     * @param attemptedSourceCount how many sources this refresh will actually try.
     * @return `0` when the lane is disabled or when the source count is too small to justify a
     * probe; otherwise at least 1 and never more than [MAX_ATTEMPTS_PER_REFRESH].
     */
    fun resolveAttempts(configuredPercent: Int, attemptedSourceCount: Int): Int {
        val percent = validate(configuredPercent)
        if (percent <= 0 || attemptedSourceCount <= 0) return 0
        val raw = (attemptedSourceCount * percent) / 100
        // A non-zero percentage over at least one source always earns at least one probe, otherwise
        // small source lists would silently never explore at all.
        return raw.coerceIn(1, MAX_ATTEMPTS_PER_REFRESH)
    }

    /** True when the user has switched the Latest lane off entirely. */
    fun isDisabled(configuredPercent: Int): Boolean = validate(configuredPercent) == 0

    // KMK_CLAUDE_LATEST_EXPLORATION_STRUCTURAL_COMPLETION_2026-08-08 -->
    /**
     * Never let more than this many Latest-catalogue candidates compete for one source's displayed
     * row when personalized results are already available. This is the hard ceiling that keeps
     * Latest strictly additive: it bounds the *input* pool handed to the shared scoring/merge step,
     * not just an expected output share, so Latest's maximum possible contribution to a row is fixed
     * regardless of how highly any individual candidate happens to score.
     */
    const val MAX_ADDITIVE_SLOTS_PER_SOURCE = 2

    /**
     * Resolves how many Latest candidates may additionally compete for one source's row when
     * personalized results were already found for that source in the same refresh.
     *
     * Bounded two ways: as a fraction of [displayLimit] (so a bigger visible-card budget can admit
     * proportionally more exploration) and by the absolute [MAX_ADDITIVE_SLOTS_PER_SOURCE] ceiling
     * (so a large budget can never turn exploration into a crawl). Floored at 1 once the lane is
     * enabled, mirroring [resolveAttempts], so a low configured percentage is never silently a no-op.
     *
     * Across every supported [ForYouResultBudgetPolicy] display limit (5/10/15/20/30) and every
     * supported percentage (10/20/30/50), the result never exceeds 2 -- so Latest's worst-case share
     * of one row is 2/5 = 40% (the smallest display limit at the largest percentage) and personalized
     * results always remain the majority. See `RecommendationLatestBudgetPolicyTest` for the
     * exhaustive proof across every supported combination.
     *
     * @return `0` when the lane is disabled or [displayLimit] is non-positive.
     */
    fun resolveAdditiveSlotsPerSource(displayLimit: Int, configuredPercent: Int): Int {
        val percent = validate(configuredPercent)
        if (percent <= 0 || displayLimit <= 0) return 0
        val raw = (displayLimit * percent) / 100
        return raw.coerceIn(1, MAX_ADDITIVE_SLOTS_PER_SOURCE)
    }
    // KMK <--

    // KMK_CLAUDE_LATEST_STRUCTURAL_REPAIR_2026-08-09 -->
    /**
     * Enforces the personalized-majority invariant on the **final, already-ordered display list**.
     *
     * ## Why the input quota alone was not a proof
     *
     * [resolveAdditiveSlotsPerSource] bounds how many Latest candidates may *compete*. Its previous
     * `latestSlots / displayLimit` argument only holds when the personalized lane actually produced a
     * full row. It does not hold when personalized results are sparse: one personalized result plus
     * two admitted Latest results yields a row that is 2/3 Latest, i.e. Latest becomes the majority
     * lane, which the owning proposal forbids. This function closes that hole by deciding the final
     * composition from the *realised* per-lane counts rather than from the budget arithmetic.
     *
     * ## The explicit invariant
     *
     * Let `N` be the number of non-Latest (personalized or Popular-fallback) candidates in the output
     * and `L` the number of Latest candidates. Then:
     *
     * - **`L <= N` whenever any non-Latest candidate exists.** Latest can tie but can never outnumber.
     * - **`L <= [MAX_ADDITIVE_SLOTS_PER_SOURCE]`** always.
     * - **`N + L <= limit`** always.
     * - When **no** non-Latest candidate exists at all, Latest keeps its pre-existing rescue role and
     *   may fill the row -- that path is the original fallback behavior and is deliberately unchanged.
     *
     * Relative ordering is preserved exactly: candidates are accepted in the order given (which is
     * already the scored, reranked order), only skipping those the per-lane allowance cannot admit.
     * Nothing is reordered here, so this composes safely after the exposure reranker.
     */
    fun enforcePersonalizedMajority(
        ordered: List<PersonalRecommendation>,
        limit: Int,
    ): List<PersonalRecommendation> {
        if (limit <= 0) return emptyList()
        if (ordered.isEmpty()) return ordered

        val latestTotal = ordered.count { it.lane == RecommendationDiscoveryLane.LATEST_CATALOGUE }
        val nonLatestTotal = ordered.size - latestTotal
        // Nothing to balance: either no Latest at all, or Latest is acting as the rescue lane because
        // the personalized/Popular chain produced nothing usable.
        if (latestTotal == 0 || nonLatestTotal == 0) return ordered.take(limit)

        val total = minOf(limit, ordered.size)
        // total / 2 is what guarantees L <= N: the remaining slots (ceil(total/2)) always match or
        // exceed the Latest allowance (floor(total/2)).
        val allowedLatest = minOf(latestTotal, MAX_ADDITIVE_SLOTS_PER_SOURCE, total / 2)
        val allowedNonLatest = minOf(nonLatestTotal, total - allowedLatest)

        val out = ArrayList<PersonalRecommendation>(minOf(total, ordered.size))
        var latestTaken = 0
        var nonLatestTaken = 0
        for (candidate in ordered) {
            if (candidate.lane == RecommendationDiscoveryLane.LATEST_CATALOGUE) {
                if (latestTaken < allowedLatest) {
                    out += candidate
                    latestTaken++
                }
            } else if (nonLatestTaken < allowedNonLatest) {
                out += candidate
                nonLatestTaken++
            }
        }
        return out
    }
    // KMK <--
}
// KMK <--

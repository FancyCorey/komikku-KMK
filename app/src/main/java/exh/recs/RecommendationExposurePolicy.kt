package exh.recs

// KMK_CLAUDE_LATEST_CATALOGUE_AND_EXPOSURE_PLAN_2026-08-08 -->
/**
 * Pure, I/O-free policy for local exposure history and the bounded soft ordering penalty derived
 * from it.
 *
 * ## The three concepts this policy keeps separate
 *
 * Per the owning proposal's recorded product decision, these are **not** the same thing and must
 * never collapse into each other:
 *
 * - **Exposed** -- the card was actually present in a loaded, visible result state. Fetching a page
 *   the user never receives as visible UI is *not* exposure.
 * - **Interacted** -- the user opened, rated, added to library, or has a confirmed local tracker
 *   association for the title.
 * - **Explicit negative feedback** -- Not Interested, Dislike, a blocked tag, or a disliked source.
 *
 * Only explicit negative feedback changes *eligibility*, and that is already owned by
 * [RecommendationCandidateVisibilityPolicy]. Exposure may only ever **reorder**. The absence of
 * interaction is an *unknown outcome*, never a dislike, and this policy contains no path that turns
 * it into one.
 *
 * ## What the penalty may and may not do
 *
 * The penalty is bounded by [MAX_PENALTY], decays to zero at the end of the configured window, and
 * is consumed only by [RecommendationDisplayReranker], which is a display-order permutation. Nothing
 * here removes, hides, blocks, or alters a candidate's base relevance score.
 */
object RecommendationExposurePolicy {

    /** Exposure windows the picker offers, in days. */
    val SUPPORTED_WINDOW_DAYS = listOf(7, 14, 30)

    /** Product decision recorded 2026-08-08: 14 days. */
    const val DEFAULT_WINDOW_DAYS = 14

    /** Exposure rows older than the window plus this grace period may be pruned. */
    const val PRUNE_GRACE_DAYS = 7

    /** A candidate seen at least this many times inside the window earns the full [MAX_PENALTY]. */
    const val SATURATION_EXPOSURE_COUNT = 4

    /**
     * Hard ceiling on the soft penalty. Deliberately small: the reranker applies it as a tie-break
     * -level nudge within a source row, so a strongly personalized candidate can never be displaced
     * by novelty alone.
     */
    const val MAX_PENALTY = 1.0

    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

    /** Validates a stored/raw window preference value, falling back to [DEFAULT_WINDOW_DAYS]. */
    fun validateWindowDays(configuredDays: Int): Int =
        if (configuredDays in SUPPORTED_WINDOW_DAYS) configuredDays else DEFAULT_WINDOW_DAYS

    /**
     * Whether the current UI state represents a real, visible exposure event.
     *
     * This is what prevents a fetch-only result, a loading spinner, an error/empty page, or a Compose
     * recomposition from being recorded as exposure. The caller must additionally key its effect on
     * a stable result generation so the same visible slate is recorded at most once.
     */
    fun isVisibleExposureEvent(isLoading: Boolean, hasLoadedState: Boolean, visibleCandidateCount: Int): Boolean =
        !isLoading && hasLoadedState && visibleCandidateCount > 0

    /**
     * Whether [lastExposedAt] still falls inside the [windowDays] window ending at [now].
     *
     * Defensive against malformed stored timestamps: a non-positive timestamp is treated as "no
     * usable exposure data" (false), and a future timestamp (clock change, restored backup from
     * another device) is treated as in-window rather than as a negative age.
     */
    fun isWithinWindow(now: Long, lastExposedAt: Long, windowDays: Int): Boolean {
        if (lastExposedAt <= 0L) return false
        if (lastExposedAt >= now) return true
        val windowMillis = validateWindowDays(windowDays) * MILLIS_PER_DAY
        return (now - lastExposedAt) < windowMillis
    }

    /**
     * Whether a candidate counts as untouched -- i.e. eligible for a soft penalty.
     *
     * Untouched means only "we have no positive interaction signal". It explicitly does **not** mean
     * the user dislikes the title; a disliked title is already excluded upstream by the visibility
     * policy and never reaches the reranker.
     */
    fun isUntouched(isInLibrary: Boolean, isRated: Boolean, isTracked: Boolean, lastInteractionAt: Long?): Boolean =
        !isInLibrary && !isRated && !isTracked && (lastInteractionAt == null || lastInteractionAt <= 0L)

    /**
     * The bounded soft penalty for one candidate.
     *
     * Returns `0.0` (no penalty at all) whenever any of these is true, so the feature always fails
     * open to the existing ordering:
     * - there is no usable exposure record;
     * - the candidate was interacted with;
     * - the last exposure is outside the window (the penalty has expired);
     * - the candidate has been exposed at most once (one sighting is not repetition).
     *
     * Otherwise the penalty scales linearly with repeat sightings up to
     * [SATURATION_EXPOSURE_COUNT] and is then damped by how recent the last sighting was, so a
     * candidate last seen 13 days ago is penalised far less than one seen today.
     */
    fun softPenalty(
        now: Long,
        lastExposedAt: Long,
        exposureCount: Int,
        windowDays: Int,
        isUntouched: Boolean,
    ): Double {
        if (!isUntouched) return 0.0
        if (exposureCount <= 1) return 0.0
        if (!isWithinWindow(now, lastExposedAt, windowDays)) return 0.0

        val repeats = (exposureCount - 1).coerceAtMost(SATURATION_EXPOSURE_COUNT - 1)
        val repeatShare = repeats.toDouble() / (SATURATION_EXPOSURE_COUNT - 1).toDouble()

        val windowMillis = validateWindowDays(windowDays) * MILLIS_PER_DAY
        val age = (now - lastExposedAt).coerceAtLeast(0L)
        val recency = 1.0 - (age.toDouble() / windowMillis.toDouble())

        return (MAX_PENALTY * repeatShare * recency.coerceIn(0.0, 1.0)).coerceIn(0.0, MAX_PENALTY)
    }

    /** The cutoff timestamp before which exposure rows may be pruned. */
    fun pruneBefore(now: Long, windowDays: Int): Long =
        now - ((validateWindowDays(windowDays) + PRUNE_GRACE_DAYS) * MILLIS_PER_DAY)
}
// KMK <--

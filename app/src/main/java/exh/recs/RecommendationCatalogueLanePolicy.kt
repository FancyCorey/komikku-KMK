package exh.recs

/**
 * Pure, I/O-free policy for the bounded Latest-catalogue exploration lane.
 *
 * ## Contract
 *
 * This policy decides **whether** to attempt Latest for a source and **how to classify** whatever
 * came back. It never performs a network call, never touches the database, and never throws. The
 * caller owns the actual `getLatestUpdates(1)` invocation and must route it through
 * [eu.kanade.tachiyomi.source.SourceRuntime] with
 * [eu.kanade.tachiyomi.source.SourceRuntimeOperation.Latest], so cancellation and fatal errors keep
 * propagating and recoverable per-source failures stay isolated exactly as every other source call
 * already is.
 *
 * ## Why capability is a pre-filter, not a proof
 *
 * `Source.supportsLatest` is **self-reported by the extension**. A source can declare `true` and
 * still throw (`UnsupportedOperationException` from the un-overridden default, a network failure, a
 * `LinkageError` from a broken extension), and a source can declare `false` while
 * `getLatestUpdates` would have worked. The flag is therefore used only to avoid a call that
 * certainly cannot succeed; every real outcome is classified from what actually happened. No source
 * is ever special-cased or hardcoded.
 *
 * ## Boundedness
 *
 * At most one Latest page is attempted per eligible source per refresh, and only while the refresh's
 * exploration budget still allows it. Latest never paginates, never retries within a refresh, and
 * never runs for a source the normal pipeline already excluded.
 */
object RecommendationCatalogueLanePolicy {

    /** The single page this lane ever requests. Latest is an exploration probe, not a crawl. */
    const val LATEST_PAGE = 1

    /** Typed outcome of one Latest attempt. Every value is bounded and per-source. */
    enum class LatestOutcome {
        /** The source declares no Latest support, so no call was made. */
        UNSUPPORTED_CAPABILITY,

        /** A call was made and the source rejected it (e.g. `UnsupportedOperationException`). */
        UNSUPPORTED_AT_RUNTIME,

        /** The call succeeded but produced no usable entry. */
        EMPTY,

        /** The call succeeded and produced at least one usable entry. */
        SUCCESS,

        /** A recoverable per-source failure (network, offline, extension linkage, timeout). */
        RECOVERABLE_ERROR,

        /** The refresh's Latest exploration budget was already spent, so no call was made. */
        BUDGET_EXHAUSTED,

        /** The source was not eligible for this lane on other grounds (see [shouldAttempt]). */
        NOT_ELIGIBLE,
    }

    /**
     * Whether to attempt Latest for one source in this refresh.
     *
     * @param supportsLatest the source's self-reported capability flag (pre-filter only).
     * @param sourceIsEligible whether the source already passed the normal For You eligibility
     * (language, ordering, disabled, disliked, quality-disliked). Latest never resurrects a source
     * the pipeline excluded.
     * @param latestAttemptsUsed how many Latest attempts this refresh has already spent.
     * @param latestBudget the refresh's total Latest attempt budget (see
     * [RecommendationLatestBudgetPolicy]). Zero disables the lane entirely.
     */
    fun shouldAttempt(
        supportsLatest: Boolean,
        sourceIsEligible: Boolean,
        latestAttemptsUsed: Int,
        latestBudget: Int,
    ): Boolean = supportsLatest &&
        sourceIsEligible &&
        latestBudget > 0 &&
        latestAttemptsUsed in 0 until latestBudget

    /**
     * Explains why [shouldAttempt] declined, for diagnostics. Returns `null` when the attempt should
     * proceed.
     */
    fun declinedOutcome(
        supportsLatest: Boolean,
        sourceIsEligible: Boolean,
        latestAttemptsUsed: Int,
        latestBudget: Int,
    ): LatestOutcome? = when {
        !sourceIsEligible -> LatestOutcome.NOT_ELIGIBLE
        !supportsLatest -> LatestOutcome.UNSUPPORTED_CAPABILITY
        latestBudget <= 0 || latestAttemptsUsed >= latestBudget -> LatestOutcome.BUDGET_EXHAUSTED
        latestAttemptsUsed < 0 -> LatestOutcome.BUDGET_EXHAUSTED
        else -> null
    }

    /**
     * Classifies a completed Latest attempt.
     *
     * @param failure the recoverable throwable when the call failed, or `null` on success. The caller
     * must already have let [kotlinx.coroutines.CancellationException] and fatal errors propagate --
     * they must never reach this function.
     * @param usableEntryCount the number of entries that survived the caller's own malformed-entry
     * filtering (see [isUsableEntry]).
     */
    fun classify(failure: Throwable?, usableEntryCount: Int): LatestOutcome = when {
        failure is UnsupportedOperationException -> LatestOutcome.UNSUPPORTED_AT_RUNTIME
        failure != null -> LatestOutcome.RECOVERABLE_ERROR
        usableEntryCount > 0 -> LatestOutcome.SUCCESS
        else -> LatestOutcome.EMPTY
    }

    /**
     * Whether one raw catalogue entry is structurally usable. A source can return entries with a
     * blank url or title; those cannot be localized or deduplicated and are dropped before they ever
     * reach the shared visibility/scoring pipeline. This is a structural check only -- it is not a
     * content filter and never substitutes for the real eligibility rules.
     */
    fun isUsableEntry(url: String?, title: String?): Boolean =
        !url.isNullOrBlank() && !title.isNullOrBlank()

    /** True when the outcome should count against the refresh's Latest budget (a call was made). */
    fun consumesBudget(outcome: LatestOutcome): Boolean = when (outcome) {
        LatestOutcome.SUCCESS,
        LatestOutcome.EMPTY,
        LatestOutcome.RECOVERABLE_ERROR,
        LatestOutcome.UNSUPPORTED_AT_RUNTIME,
        -> true
        LatestOutcome.UNSUPPORTED_CAPABILITY,
        LatestOutcome.BUDGET_EXHAUSTED,
        LatestOutcome.NOT_ELIGIBLE,
        -> false
    }
}
// KMK <--

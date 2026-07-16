package exh.recs

// KMK --> v0.7.44: shared strict-to-lenient query attempt policy
/**
 * Typed reason a single query attempt did not produce a usable result.
 *
 * [NONE] means the attempt succeeded (produced at least one relevant result). Every other value
 * is a distinct reason it did not, so callers/diagnostics never have to guess between "the source
 * is broken", "the source has nothing tagged this way", and "results came back but weren't
 * actually relevant".
 */
internal enum class RecommendationQueryFailureKind {
    NONE,
    SOURCE_EXCEPTION,
    FILTER_UNSUPPORTED,
    NO_RAW_RESULTS,
    FILTERED_UNRELATED,
    WEAK_METADATA,
    CANCELLED,
}

/** Outcome of one query attempt, used to decide whether to try the next, more lenient attempt. */
internal data class RecommendationQueryAttemptOutcome(
    val strategy: RecommendationQueryStrategyType,
    val rawCount: Int,
    val enrichedCount: Int,
    val relevantCount: Int,
    val failureKind: RecommendationQueryFailureKind,
) {
    val isUseful: Boolean
        get() = relevantCount > 0 && failureKind != RecommendationQueryFailureKind.CANCELLED
}

/**
 * Pure, Android-free policy shared by For You and grouped Loved/Liked recommendations for
 * deciding what query attempts to try, in what order, and when to stop.
 *
 * This does not perform any network or database work itself — callers execute one
 * [RecommendationQueryPlan] at a time (reusing [GenreFilterMapper]/[GetSearchManga] as before),
 * classify the result with [classify], and use [shouldTryNext] to decide whether to continue
 * down the strict-to-lenient chain from [buildTagAttemptChain].
 */
internal object RecommendationQueryAttemptPolicy {

    /** Hard cap on tag-based attempts per source per row/refresh — never uncapped crawling. */
    const val MAX_TAG_ATTEMPTS = 3

    /**
     * Builds the deterministic, strict-to-lenient tag-based attempt chain for [topTags], capped at
     * [MAX_TAG_ATTEMPTS]. Order: TOP_TAGS_FILTER, then TAG_PAIR (or SINGLE_STRONGEST_TAG when fewer
     * than two tags are available), then TEXT_ONLY_TOP_TAGS. No randomness — same input always
     * produces the same chain. Title-based fallback (a fifth strategy, used by grouped
     * recommendations only) is intentionally not part of this chain; callers append it themselves
     * only after every attempt here has failed to produce a useful result.
     */
    fun buildTagAttemptChain(topTags: List<String>): List<RecommendationQueryPlan> {
        if (topTags.isEmpty()) return emptyList()
        return buildList {
            add(RecommendationQueryPlan(RecommendationQueryStrategyType.TOP_TAGS_FILTER, topTags.take(5)))
            if (topTags.size >= 2) {
                add(RecommendationQueryPlan(RecommendationQueryStrategyType.TAG_PAIR, topTags.take(2)))
            } else {
                add(RecommendationQueryPlan(RecommendationQueryStrategyType.SINGLE_STRONGEST_TAG, topTags.take(1)))
            }
            add(
                RecommendationQueryPlan(
                    RecommendationQueryStrategyType.TEXT_ONLY_TOP_TAGS,
                    topTags.take(3),
                    forceTextOnly = true,
                ),
            )
        }.distinctBy { it.type }.take(MAX_TAG_ATTEMPTS)
    }

    /**
     * Classifies a completed attempt. Callers must rethrow [kotlinx.coroutines.CancellationException]
     * before calling this — cancellation must never be swallowed or classified as a normal failure.
     */
    fun classify(
        strategy: RecommendationQueryStrategyType,
        rawCount: Int,
        enrichedCount: Int,
        relevantCount: Int,
        exceptionOccurred: Boolean,
        filterUnsupported: Boolean = false,
    ): RecommendationQueryAttemptOutcome {
        val failureKind = when {
            exceptionOccurred -> RecommendationQueryFailureKind.SOURCE_EXCEPTION
            filterUnsupported -> RecommendationQueryFailureKind.FILTER_UNSUPPORTED
            rawCount == 0 -> RecommendationQueryFailureKind.NO_RAW_RESULTS
            relevantCount == 0 && enrichedCount > 0 -> RecommendationQueryFailureKind.WEAK_METADATA
            relevantCount == 0 -> RecommendationQueryFailureKind.FILTERED_UNRELATED
            else -> RecommendationQueryFailureKind.NONE
        }
        return RecommendationQueryAttemptOutcome(strategy, rawCount, enrichedCount, relevantCount, failureKind)
    }

    /** Whether the caller should try the next, more lenient attempt after [outcome]. */
    fun shouldTryNext(outcome: RecommendationQueryAttemptOutcome): Boolean =
        !outcome.isUseful && outcome.failureKind != RecommendationQueryFailureKind.CANCELLED
}
// KMK <--

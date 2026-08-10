package exh.recs

// KMK -->

internal enum class RecommendationQueryStrategyType {
    TOP_TAGS_FILTER,
    SINGLE_STRONGEST_TAG,
    TAG_PAIR,
    TEXT_ONLY_TOP_TAGS,
}

internal data class RecommendationQueryPlan(
    val type: RecommendationQueryStrategyType,
    val tags: List<String>,
    val forceTextOnly: Boolean = false,
)

/**
 * Builds a deterministic, capped list of query plans for a single source refresh.
 *
 * At most [MAX_STRATEGIES_PER_SOURCE] plans are returned. The first plan uses the
 * [lastSuccessful] strategy if provided, otherwise [RecommendationQueryStrategyType.TOP_TAGS_FILTER].
 * A single fallback plan is appended only if a fallback makes sense for the primary strategy.
 *
 * Strategy persistence format: `"sourceId=STRATEGY_NAME;sourceId=STRATEGY_NAME"`.
 */
internal object RecommendationQueryPlanner {

    // KMK --> v0.7.44: widened from 2 to 3 so a source that fails one strict attempt still gets a
    // second, more lenient fallback instead of being marked empty/bad too early (shared
    // strict-to-lenient chain principle also used by RecommendationQueryAttemptPolicy for group
    // recommendations). "Last successful" still starts the chain when known, preserving the
    // existing fast-path behavior.
    const val MAX_STRATEGIES_PER_SOURCE = 3
    // KMK <--
    const val MIN_USEFUL_RESULTS_NORMAL = 3
    const val MIN_USEFUL_RESULTS_BOOSTED = 5

    fun parseStrategies(value: String): Map<Long, RecommendationQueryStrategyType> {
        if (value.isBlank()) return emptyMap()
        return value.split(";").mapNotNull { entry ->
            val parts = entry.split("=")
            if (parts.size != 2) return@mapNotNull null
            val id = parts[0].trim().toLongOrNull() ?: return@mapNotNull null
            val strategy = RecommendationQueryStrategyType.entries.firstOrNull { it.name == parts[1].trim() }
                ?: return@mapNotNull null
            id to strategy
        }.toMap()
    }

    fun serializeStrategies(map: Map<Long, RecommendationQueryStrategyType>): String =
        map.entries.joinToString(";") { "${it.key}=${it.value.name}" }

    /**
     * Returns an ordered list of plans to try for a source, capped at [MAX_STRATEGIES_PER_SOURCE].
     * Plans are deterministic — no randomness.
     *
     * KMK v0.8.13: delegates the base strict-to-lenient chain to
     * [RecommendationQueryAttemptPolicy.buildTagAttemptChain] (TOP_TAGS_FILTER, TAG_PAIR or
     * SINGLE_STRONGEST_TAG, TEXT_ONLY_TOP_TAGS) instead of maintaining a second, parallel
     * `fallbackFor()` chain. [lastSuccessful] only rotates that same chain to start at a different
     * point -- it can no longer eliminate the other attempts the way the old terminal
     * `TEXT_ONLY_TOP_TAGS has no fallback` behavior did. Callers must pass a [lastSuccessful] that
     * has already been validated by `RecommendationStrategyRecoveryPolicy` -- a raw, un-recovered
     * persisted hint should never reach this function directly.
     */
    fun buildPlans(
        topTags: List<String>,
        lastSuccessful: RecommendationQueryStrategyType? = null,
    ): List<RecommendationQueryPlan> {
        val chain = RecommendationQueryAttemptPolicy.buildTagAttemptChain(topTags)
        if (chain.isEmpty()) return chain
        val startIndex = lastSuccessful
            ?.let { last -> chain.indexOfFirst { it.type == last } }
            ?.takeIf { it >= 0 }
            ?: 0
        val rotated = chain.drop(startIndex) + chain.take(startIndex)
        return rotated.take(MAX_STRATEGIES_PER_SOURCE)
    }
}
// KMK <--

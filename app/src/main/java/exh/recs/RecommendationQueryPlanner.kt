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

    const val MAX_STRATEGIES_PER_SOURCE = 2
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
     */
    fun buildPlans(
        topTags: List<String>,
        lastSuccessful: RecommendationQueryStrategyType? = null,
    ): List<RecommendationQueryPlan> {
        val primary = planFor(lastSuccessful ?: RecommendationQueryStrategyType.TOP_TAGS_FILTER, topTags)
        val fallback = fallbackFor(primary.type, topTags)
        return buildList {
            add(primary)
            if (fallback != null && fallback.type != primary.type) add(fallback)
        }
    }

    private fun planFor(type: RecommendationQueryStrategyType, topTags: List<String>): RecommendationQueryPlan =
        when (type) {
            RecommendationQueryStrategyType.TOP_TAGS_FILTER ->
                RecommendationQueryPlan(type, topTags.take(5))
            RecommendationQueryStrategyType.SINGLE_STRONGEST_TAG ->
                RecommendationQueryPlan(type, topTags.take(1))
            RecommendationQueryStrategyType.TAG_PAIR ->
                RecommendationQueryPlan(type, topTags.take(2))
            RecommendationQueryStrategyType.TEXT_ONLY_TOP_TAGS ->
                RecommendationQueryPlan(type, topTags.take(3), forceTextOnly = true)
        }

    private fun fallbackFor(
        primary: RecommendationQueryStrategyType,
        topTags: List<String>,
    ): RecommendationQueryPlan? {
        val fallbackType = when (primary) {
            RecommendationQueryStrategyType.TOP_TAGS_FILTER ->
                if (topTags.size >= 2) {
                    RecommendationQueryStrategyType.TAG_PAIR
                } else {
                    RecommendationQueryStrategyType.TEXT_ONLY_TOP_TAGS
                }
            RecommendationQueryStrategyType.TAG_PAIR ->
                RecommendationQueryStrategyType.TEXT_ONLY_TOP_TAGS
            RecommendationQueryStrategyType.SINGLE_STRONGEST_TAG ->
                RecommendationQueryStrategyType.TEXT_ONLY_TOP_TAGS
            RecommendationQueryStrategyType.TEXT_ONLY_TOP_TAGS -> null // most permissive, no fallback
        }
        return fallbackType?.let { planFor(it, topTags) }
    }
}
// KMK <--

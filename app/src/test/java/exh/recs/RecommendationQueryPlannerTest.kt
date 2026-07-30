package exh.recs

// KMK -->
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecommendationQueryPlannerTest {

    private val tags = listOf("action", "romance", "isekai", "fantasy", "comedy")

    @Test
    fun `default strategy with no memory is TOP_TAGS_FILTER`() {
        val plans = RecommendationQueryPlanner.buildPlans(tags, lastSuccessful = null)
        assertEquals(RecommendationQueryStrategyType.TOP_TAGS_FILTER, plans.first().type)
    }

    @Test
    fun `starts with last successful strategy when provided`() {
        val plans = RecommendationQueryPlanner.buildPlans(tags, lastSuccessful = RecommendationQueryStrategyType.TAG_PAIR)
        assertEquals(RecommendationQueryStrategyType.TAG_PAIR, plans.first().type)
    }

    @Test
    fun `at most MAX_STRATEGIES_PER_SOURCE plans are produced`() {
        val plans = RecommendationQueryPlanner.buildPlans(tags)
        assertTrue(plans.size <= RecommendationQueryPlanner.MAX_STRATEGIES_PER_SOURCE)
    }

    @Test
    fun `TOP_TAGS_FILTER fallback chain is TAG_PAIR then TEXT_ONLY when tags available`() {
        // KMK v0.7.44: MAX_STRATEGIES_PER_SOURCE widened to 3, so the chain now continues past
        // the first fallback instead of stopping at TAG_PAIR.
        val plans = RecommendationQueryPlanner.buildPlans(tags)
        assertEquals(3, plans.size)
        assertEquals(RecommendationQueryStrategyType.TOP_TAGS_FILTER, plans[0].type)
        assertEquals(RecommendationQueryStrategyType.TAG_PAIR, plans[1].type)
        assertEquals(RecommendationQueryStrategyType.TEXT_ONLY_TOP_TAGS, plans[2].type)
    }

    // KMK v0.8.13: buildPlans() now delegates to RecommendationQueryAttemptPolicy.buildTagAttemptChain,
    // which always includes SINGLE_STRONGEST_TAG as the second attempt when fewer than two tags are
    // available (previously the old fallbackFor() chain skipped straight from TOP_TAGS_FILTER to
    // TEXT_ONLY_TOP_TAGS for a single tag, never trying SINGLE_STRONGEST_TAG automatically) -- still
    // ends with TEXT_ONLY_TOP_TAGS, still capped at 3.
    @Test
    fun `single tag chain is TOP_TAGS_FILTER, SINGLE_STRONGEST_TAG, then TEXT_ONLY`() {
        val plans = RecommendationQueryPlanner.buildPlans(listOf("action"))
        assertEquals(3, plans.size)
        assertEquals(RecommendationQueryStrategyType.TOP_TAGS_FILTER, plans[0].type)
        assertEquals(RecommendationQueryStrategyType.SINGLE_STRONGEST_TAG, plans[1].type)
        assertEquals(RecommendationQueryStrategyType.TEXT_ONLY_TOP_TAGS, plans[2].type)
    }

    // KMK v0.8.13: TEXT_ONLY_TOP_TAGS is no longer a caller-facing dead end -- buildPlans() now
    // rotates the shared strict-to-lenient chain instead of walking a separate, terminal
    // fallbackFor() chain. A stale TEXT_ONLY_TOP_TAGS persisted hint must never eliminate every
    // other attempt; RecommendationStrategyRecoveryPolicy is responsible for deciding whether the
    // hint should reach buildPlans() at all in the first place.
    @Test
    fun `TEXT_ONLY_TOP_TAGS with a valid recovered last strategy still includes at least one non-text fallback attempt`() {
        val plans = RecommendationQueryPlanner.buildPlans(tags, lastSuccessful = RecommendationQueryStrategyType.TEXT_ONLY_TOP_TAGS)
        assertEquals(3, plans.size)
        assertEquals(RecommendationQueryStrategyType.TEXT_ONLY_TOP_TAGS, plans[0].type)
        assertTrue(plans.drop(1).any { it.type != RecommendationQueryStrategyType.TEXT_ONLY_TOP_TAGS })
    }

    @Test
    fun `no last strategy keeps the strict-to-lenient order`() {
        val plans = RecommendationQueryPlanner.buildPlans(tags, lastSuccessful = null)
        assertEquals(
            listOf(
                RecommendationQueryStrategyType.TOP_TAGS_FILTER,
                RecommendationQueryStrategyType.TAG_PAIR,
                RecommendationQueryStrategyType.TEXT_ONLY_TOP_TAGS,
            ),
            plans.map { it.type },
        )
    }

    @Test
    fun `a valid TAG_PAIR last strategy starts with TAG_PAIR but still includes fallbacks`() {
        val plans = RecommendationQueryPlanner.buildPlans(tags, lastSuccessful = RecommendationQueryStrategyType.TAG_PAIR)
        assertEquals(
            listOf(
                RecommendationQueryStrategyType.TAG_PAIR,
                RecommendationQueryStrategyType.TEXT_ONLY_TOP_TAGS,
                RecommendationQueryStrategyType.TOP_TAGS_FILTER,
            ),
            plans.map { it.type },
        )
    }

    @Test
    fun `a last strategy not present in the current chain falls back to the default order`() {
        // SINGLE_STRONGEST_TAG is only ever produced when fewer than two tags are available -- with
        // 5 tags here the chain never contains it, so it must not silently drop every attempt.
        val plans = RecommendationQueryPlanner.buildPlans(tags, lastSuccessful = RecommendationQueryStrategyType.SINGLE_STRONGEST_TAG)
        assertEquals(RecommendationQueryStrategyType.TOP_TAGS_FILTER, plans.first().type)
        assertEquals(3, plans.size)
    }

    @Test
    fun `plans remain capped at MAX_STRATEGIES_PER_SOURCE regardless of last strategy`() {
        RecommendationQueryStrategyType.entries.forEach { last ->
            val plans = RecommendationQueryPlanner.buildPlans(tags, lastSuccessful = last)
            assertTrue(plans.size <= RecommendationQueryPlanner.MAX_STRATEGIES_PER_SOURCE)
        }
    }

    @Test
    fun `plans are deterministic for same inputs`() {
        val plans1 = RecommendationQueryPlanner.buildPlans(tags)
        val plans2 = RecommendationQueryPlanner.buildPlans(tags)
        assertEquals(plans1, plans2)
    }

    @Test
    fun `TOP_TAGS_FILTER plan uses up to 5 tags`() {
        val manyTags = List(10) { "tag$it" }
        val plan = RecommendationQueryPlanner.buildPlans(manyTags).first()
        assertEquals(5, plan.tags.size)
    }

    @Test
    fun `TAG_PAIR plan uses exactly 2 tags`() {
        val plans = RecommendationQueryPlanner.buildPlans(tags, lastSuccessful = RecommendationQueryStrategyType.TAG_PAIR)
        assertEquals(2, plans.first().tags.size)
    }

    @Test
    fun `TEXT_ONLY plan has forceTextOnly true`() {
        val plans = RecommendationQueryPlanner.buildPlans(tags, lastSuccessful = RecommendationQueryStrategyType.TEXT_ONLY_TOP_TAGS)
        assertTrue(plans.first().forceTextOnly)
    }

    @Test
    fun `non-TEXT_ONLY plans have forceTextOnly false`() {
        val plans = RecommendationQueryPlanner.buildPlans(tags)
        assertTrue(plans.none { it.forceTextOnly && it.type != RecommendationQueryStrategyType.TEXT_ONLY_TOP_TAGS })
    }

    @Test
    fun `parseStrategies handles empty string`() {
        val map = RecommendationQueryPlanner.parseStrategies("")
        assertTrue(map.isEmpty())
    }

    @Test
    fun `parseStrategies round-trips with serialize`() {
        val original = mapOf(
            1L to RecommendationQueryStrategyType.TAG_PAIR,
            2L to RecommendationQueryStrategyType.TEXT_ONLY_TOP_TAGS,
        )
        val serialized = RecommendationQueryPlanner.serializeStrategies(original)
        val parsed = RecommendationQueryPlanner.parseStrategies(serialized)
        assertEquals(original, parsed)
    }

    @Test
    fun `parseStrategies ignores malformed entries`() {
        val map = RecommendationQueryPlanner.parseStrategies("1=TAG_PAIR;bad_entry;2=NONEXISTENT")
        assertEquals(mapOf(1L to RecommendationQueryStrategyType.TAG_PAIR), map)
    }
}
// KMK <--

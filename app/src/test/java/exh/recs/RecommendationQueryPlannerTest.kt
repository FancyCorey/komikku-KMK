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
    fun `TOP_TAGS_FILTER fallback is TAG_PAIR when tags available`() {
        val plans = RecommendationQueryPlanner.buildPlans(tags)
        assertEquals(2, plans.size)
        assertEquals(RecommendationQueryStrategyType.TAG_PAIR, plans[1].type)
    }

    @Test
    fun `TOP_TAGS_FILTER fallback is TEXT_ONLY when only one tag`() {
        val plans = RecommendationQueryPlanner.buildPlans(listOf("action"))
        assertEquals(2, plans.size)
        assertEquals(RecommendationQueryStrategyType.TEXT_ONLY_TOP_TAGS, plans[1].type)
    }

    @Test
    fun `TEXT_ONLY_TOP_TAGS has no fallback`() {
        val plans = RecommendationQueryPlanner.buildPlans(tags, lastSuccessful = RecommendationQueryStrategyType.TEXT_ONLY_TOP_TAGS)
        assertEquals(1, plans.size)
        assertEquals(RecommendationQueryStrategyType.TEXT_ONLY_TOP_TAGS, plans.first().type)
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

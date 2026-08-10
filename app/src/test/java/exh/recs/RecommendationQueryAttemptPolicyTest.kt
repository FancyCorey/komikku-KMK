package exh.recs

// KMK --> v0.7.44: tests for the shared strict-to-lenient query attempt policy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecommendationQueryAttemptPolicyTest {

    @Test
    fun `empty tags returns empty chain`() {
        assertEquals(emptyList<RecommendationQueryPlan>(), RecommendationQueryAttemptPolicy.buildTagAttemptChain(emptyList()))
    }

    @Test
    fun `chain starts with TOP_TAGS_FILTER using up to 5 tags`() {
        val chain = RecommendationQueryAttemptPolicy.buildTagAttemptChain(listOf("a", "b", "c", "d", "e", "f"))
        assertEquals(RecommendationQueryStrategyType.TOP_TAGS_FILTER, chain.first().type)
        assertEquals(listOf("a", "b", "c", "d", "e"), chain.first().tags)
    }

    @Test
    fun `chain uses TAG_PAIR as second attempt when at least two tags exist`() {
        val chain = RecommendationQueryAttemptPolicy.buildTagAttemptChain(listOf("a", "b", "c"))
        assertEquals(RecommendationQueryStrategyType.TAG_PAIR, chain[1].type)
        assertEquals(listOf("a", "b"), chain[1].tags)
    }

    @Test
    fun `chain uses SINGLE_STRONGEST_TAG as second attempt when only one tag exists`() {
        val chain = RecommendationQueryAttemptPolicy.buildTagAttemptChain(listOf("a"))
        assertEquals(RecommendationQueryStrategyType.SINGLE_STRONGEST_TAG, chain[1].type)
        assertEquals(listOf("a"), chain[1].tags)
    }

    @Test
    fun `final attempt is always TEXT_ONLY_TOP_TAGS with forceTextOnly set`() {
        val chain = RecommendationQueryAttemptPolicy.buildTagAttemptChain(listOf("a", "b", "c"))
        val last = chain.last()
        assertEquals(RecommendationQueryStrategyType.TEXT_ONLY_TOP_TAGS, last.type)
        assertTrue(last.forceTextOnly)
        assertEquals(listOf("a", "b", "c"), last.tags)
    }

    @Test
    fun `chain is capped at MAX_TAG_ATTEMPTS`() {
        val chain = RecommendationQueryAttemptPolicy.buildTagAttemptChain(listOf("a", "b", "c"))
        assertEquals(RecommendationQueryAttemptPolicy.MAX_TAG_ATTEMPTS, chain.size)
    }

    @Test
    fun `chain is deterministic across repeated calls`() {
        val tags = listOf("action", "romance", "isekai")
        val first = RecommendationQueryAttemptPolicy.buildTagAttemptChain(tags)
        val second = RecommendationQueryAttemptPolicy.buildTagAttemptChain(tags)
        assertEquals(first, second)
    }

    @Test
    fun `classify with relevant results yields NONE failure kind and isUseful true`() {
        val outcome = RecommendationQueryAttemptPolicy.classify(
            strategy = RecommendationQueryStrategyType.TOP_TAGS_FILTER,
            rawCount = 10,
            enrichedCount = 10,
            relevantCount = 4,
            exceptionOccurred = false,
        )
        assertEquals(RecommendationQueryFailureKind.NONE, outcome.failureKind)
        assertTrue(outcome.isUseful)
    }

    @Test
    fun `classify with exception yields SOURCE_EXCEPTION regardless of counts`() {
        val outcome = RecommendationQueryAttemptPolicy.classify(
            strategy = RecommendationQueryStrategyType.TOP_TAGS_FILTER,
            rawCount = 0,
            enrichedCount = 0,
            relevantCount = 0,
            exceptionOccurred = true,
        )
        assertEquals(RecommendationQueryFailureKind.SOURCE_EXCEPTION, outcome.failureKind)
        assertFalse(outcome.isUseful)
    }

    @Test
    fun `classify with unsupported filter yields FILTER_UNSUPPORTED`() {
        val outcome = RecommendationQueryAttemptPolicy.classify(
            strategy = RecommendationQueryStrategyType.TOP_TAGS_FILTER,
            rawCount = 0,
            enrichedCount = 0,
            relevantCount = 0,
            exceptionOccurred = false,
            filterUnsupported = true,
        )
        assertEquals(RecommendationQueryFailureKind.FILTER_UNSUPPORTED, outcome.failureKind)
    }

    @Test
    fun `classify with zero raw results yields NO_RAW_RESULTS`() {
        val outcome = RecommendationQueryAttemptPolicy.classify(
            strategy = RecommendationQueryStrategyType.TOP_TAGS_FILTER,
            rawCount = 0,
            enrichedCount = 0,
            relevantCount = 0,
            exceptionOccurred = false,
        )
        assertEquals(RecommendationQueryFailureKind.NO_RAW_RESULTS, outcome.failureKind)
    }

    @Test
    fun `classify with raw and enriched results but zero relevant yields WEAK_METADATA`() {
        val outcome = RecommendationQueryAttemptPolicy.classify(
            strategy = RecommendationQueryStrategyType.TOP_TAGS_FILTER,
            rawCount = 8,
            enrichedCount = 8,
            relevantCount = 0,
            exceptionOccurred = false,
        )
        assertEquals(RecommendationQueryFailureKind.WEAK_METADATA, outcome.failureKind)
    }

    @Test
    fun `classify with raw results but no enrichment and zero relevant yields FILTERED_UNRELATED`() {
        val outcome = RecommendationQueryAttemptPolicy.classify(
            strategy = RecommendationQueryStrategyType.TOP_TAGS_FILTER,
            rawCount = 8,
            enrichedCount = 0,
            relevantCount = 0,
            exceptionOccurred = false,
        )
        assertEquals(RecommendationQueryFailureKind.FILTERED_UNRELATED, outcome.failureKind)
    }

    @Test
    fun `shouldTryNext is true for a non-useful non-cancelled outcome`() {
        val outcome = RecommendationQueryAttemptPolicy.classify(
            strategy = RecommendationQueryStrategyType.TOP_TAGS_FILTER,
            rawCount = 0,
            enrichedCount = 0,
            relevantCount = 0,
            exceptionOccurred = false,
        )
        assertTrue(RecommendationQueryAttemptPolicy.shouldTryNext(outcome))
    }

    @Test
    fun `shouldTryNext is false once an attempt is useful`() {
        val outcome = RecommendationQueryAttemptPolicy.classify(
            strategy = RecommendationQueryStrategyType.TOP_TAGS_FILTER,
            rawCount = 5,
            enrichedCount = 5,
            relevantCount = 2,
            exceptionOccurred = false,
        )
        assertFalse(RecommendationQueryAttemptPolicy.shouldTryNext(outcome))
    }

    @Test
    fun `shouldTryNext is false for a cancelled outcome even though not useful`() {
        val cancelled = RecommendationQueryAttemptOutcome(
            strategy = RecommendationQueryStrategyType.TOP_TAGS_FILTER,
            rawCount = 0,
            enrichedCount = 0,
            relevantCount = 0,
            failureKind = RecommendationQueryFailureKind.CANCELLED,
        )
        assertFalse(RecommendationQueryAttemptPolicy.shouldTryNext(cancelled))
    }
}
// KMK <--

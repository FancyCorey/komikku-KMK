package exh.recs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecommendationEffectiveResourcePolicyTest {

    @Test
    fun `normal devices use the bounded default source concurrency`() {
        assertEquals(
            RecommendationEffectiveResourcePolicy.DEFAULT_SOURCE_CONCURRENCY,
            RecommendationEffectiveResourcePolicy.sourceConcurrency(isLowRamDevice = false),
        )
    }

    @Test
    fun `low RAM devices use the stricter source concurrency`() {
        assertEquals(
            RecommendationEffectiveResourcePolicy.LOW_RAM_SOURCE_CONCURRENCY,
            RecommendationEffectiveResourcePolicy.sourceConcurrency(isLowRamDevice = true),
        )
    }

    @Test
    fun `the policy never exceeds the declared aggregate ceiling`() {
        assertEquals(
            RecommendationEffectiveResourcePolicy.MAX_SOURCE_CONCURRENCY,
            maxOf(
                RecommendationEffectiveResourcePolicy.sourceConcurrency(isLowRamDevice = false),
                RecommendationEffectiveResourcePolicy.sourceConcurrency(isLowRamDevice = true),
            ),
        )
    }

    @Test
    fun `normal devices use the bounded default preview concurrency`() {
        assertEquals(
            RecommendationEffectiveResourcePolicy.DEFAULT_PREVIEW_CONCURRENCY,
            RecommendationEffectiveResourcePolicy.previewConcurrency(isLowRamDevice = false),
        )
    }

    @Test
    fun `low RAM devices use the stricter preview concurrency`() {
        assertEquals(
            RecommendationEffectiveResourcePolicy.LOW_RAM_PREVIEW_CONCURRENCY,
            RecommendationEffectiveResourcePolicy.previewConcurrency(isLowRamDevice = true),
        )
    }

    @Test
    fun `preview policy never exceeds its declared aggregate ceiling`() {
        assertEquals(
            RecommendationEffectiveResourcePolicy.MAX_PREVIEW_CONCURRENCY,
            maxOf(
                RecommendationEffectiveResourcePolicy.previewConcurrency(isLowRamDevice = false),
                RecommendationEffectiveResourcePolicy.previewConcurrency(isLowRamDevice = true),
            ),
        )
    }

    @Test
    fun `low RAM devices use reduced preview enrichment concurrency`() {
        assertEquals(
            RecommendationEffectiveResourcePolicy.DEFAULT_PREVIEW_ENRICHMENT_CONCURRENCY,
            RecommendationEffectiveResourcePolicy.previewEnrichmentConcurrency(isLowRamDevice = false),
        )
        assertEquals(
            RecommendationEffectiveResourcePolicy.LOW_RAM_PREVIEW_ENRICHMENT_CONCURRENCY,
            RecommendationEffectiveResourcePolicy.previewEnrichmentConcurrency(isLowRamDevice = true),
        )
        assertTrue(
            RecommendationEffectiveResourcePolicy.previewEnrichmentConcurrency(isLowRamDevice = true) <
                RecommendationEffectiveResourcePolicy.previewEnrichmentConcurrency(isLowRamDevice = false),
        )
    }
}

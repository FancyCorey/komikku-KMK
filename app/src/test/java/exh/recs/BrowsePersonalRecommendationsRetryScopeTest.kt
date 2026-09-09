package exh.recs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BrowsePersonalRecommendationsRetryScopeTest {

    @Test
    fun `inline retry selects every current error row`() {
        val result = retryableRecommendationSourceIds(
            mapOf(
                10L to PersonalRecommendationResult.Error(IllegalStateException("temporary")),
                20L to PersonalRecommendationResult.Error(IllegalStateException("temporary")),
            ),
        )

        assertEquals(setOf(10L, 20L), result)
    }

    @Test
    fun `inline retry excludes successful rows`() {
        val result = retryableRecommendationSourceIds(
            mapOf(
                10L to PersonalRecommendationResult.Error(IllegalStateException("temporary")),
                20L to PersonalRecommendationResult.Success(emptyList()),
                30L to PersonalRecommendationResult.Loading,
            ),
        )

        assertEquals(setOf(10L), result)
    }

    @Test
    fun `empty retry scope is represented explicitly`() {
        assertEquals(emptySet<Long>(), retryableRecommendationSourceIds(emptyMap()))
    }

    @Test
    fun `targeted retry does not apply normal refresh source cap`() {
        val sources = (1L..45L).toList()
        val retryIds = sources.toSet()

        val selected = selectRecommendationCandidates(
            sources = sources,
            retrySourceIds = retryIds,
            maxAttempts = 40,
            sourceId = { it },
        )

        assertEquals(sources, selected)
    }

    @Test
    fun `targeted retry deduplicates repeated source ids`() {
        val sources = listOf(10L, 20L, 10L, 30L, 20L)

        val selected = selectRecommendationCandidates(
            sources = sources,
            retrySourceIds = setOf(10L, 20L, 30L),
            maxAttempts = 40,
            sourceId = { it },
        )

        assertEquals(listOf(10L, 20L, 30L), selected)
    }

    @Test
    fun `normal refresh still applies the bounded source cap`() {
        val sources = (1L..45L).toList()

        val selected = selectRecommendationCandidates(
            sources = sources,
            retrySourceIds = null,
            maxAttempts = 40,
            sourceId = { it },
        )

        assertEquals(sources.take(40), selected)
    }
}

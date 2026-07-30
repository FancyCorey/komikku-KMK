package exh.recs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.RecommendationDiscoveryProgress

// KMK v0.8.13 -->
class RecommendationStrategyRecoveryPolicyTest {

    private fun progress(
        strategy: RecommendationQueryStrategyType,
        status: String,
        rawCount: Int = 0,
        visibleCount: Int = 0,
        page: Int = 1,
    ) = RecommendationDiscoveryProgress(
        sourceId = 1L,
        querySignature = "sig",
        queryTagsJson = "[]",
        queryStrategy = strategy.name,
        page = page,
        evaluatedAt = 0L,
        rawCount = rawCount,
        localizedCount = 0,
        scoredCount = 0,
        visibleCount = visibleCount,
        filteredCount = 0,
        status = status,
        errorMessage = null,
        profileFingerprint = null,
    )

    // --- effectiveStartingStrategy ---

    @Test
    fun `null last strategy returns null`() {
        val result = RecommendationStrategyRecoveryPolicy.effectiveStartingStrategy(
            lastStrategy = null,
            recentProgress = emptyList(),
            rememberedCount = 0,
            lastRunStatus = null,
        )
        assertNull(result)
    }

    @Test
    fun `TEXT_ONLY_TOP_TAGS with empty progress and no memory is forgotten`() {
        val result = RecommendationStrategyRecoveryPolicy.effectiveStartingStrategy(
            lastStrategy = RecommendationQueryStrategyType.TEXT_ONLY_TOP_TAGS,
            recentProgress = emptyList(),
            rememberedCount = 0,
            lastRunStatus = null,
        )
        assertNull(result)
    }

    @Test
    fun `TEXT_ONLY_TOP_TAGS with a successful visible progress row remains usable`() {
        val result = RecommendationStrategyRecoveryPolicy.effectiveStartingStrategy(
            lastStrategy = RecommendationQueryStrategyType.TEXT_ONLY_TOP_TAGS,
            recentProgress = listOf(
                progress(
                    RecommendationQueryStrategyType.TEXT_ONLY_TOP_TAGS,
                    RecommendationDiscoveryProgress.STATUS_SUCCESS,
                    rawCount = 5,
                    visibleCount = 3,
                ),
            ),
            rememberedCount = 0,
            lastRunStatus = null,
        )
        assertEquals(RecommendationQueryStrategyType.TEXT_ONLY_TOP_TAGS, result)
    }

    @Test
    fun `TEXT_ONLY_TOP_TAGS with remembered candidate memory remains usable even with empty progress`() {
        val result = RecommendationStrategyRecoveryPolicy.effectiveStartingStrategy(
            lastStrategy = RecommendationQueryStrategyType.TEXT_ONLY_TOP_TAGS,
            recentProgress = emptyList(),
            rememberedCount = 4,
            lastRunStatus = null,
        )
        assertEquals(RecommendationQueryStrategyType.TEXT_ONLY_TOP_TAGS, result)
    }

    @Test
    fun `TAG_PAIR with recent NoMatches status is forgotten`() {
        val result = RecommendationStrategyRecoveryPolicy.effectiveStartingStrategy(
            lastStrategy = RecommendationQueryStrategyType.TAG_PAIR,
            recentProgress = emptyList(),
            rememberedCount = 0,
            lastRunStatus = RecommendationSourceRunStatus(1L, RecommendationSourceStatus.NoMatches),
        )
        assertNull(result)
    }

    @Test
    fun `TAG_PAIR with recent NoMatches but a proven successful progress row for that strategy stays usable`() {
        val result = RecommendationStrategyRecoveryPolicy.effectiveStartingStrategy(
            lastStrategy = RecommendationQueryStrategyType.TAG_PAIR,
            recentProgress = listOf(
                progress(
                    RecommendationQueryStrategyType.TAG_PAIR,
                    RecommendationDiscoveryProgress.STATUS_SUCCESS,
                    rawCount = 5,
                    visibleCount = 2,
                ),
            ),
            rememberedCount = 0,
            lastRunStatus = RecommendationSourceRunStatus(1L, RecommendationSourceStatus.NoMatches),
        )
        assertEquals(RecommendationQueryStrategyType.TAG_PAIR, result)
    }

    @Test
    fun `Shown status keeps the strategy usable`() {
        val result = RecommendationStrategyRecoveryPolicy.effectiveStartingStrategy(
            lastStrategy = RecommendationQueryStrategyType.TOP_TAGS_FILTER,
            recentProgress = emptyList(),
            rememberedCount = 0,
            lastRunStatus = RecommendationSourceRunStatus(1L, RecommendationSourceStatus.Shown, visibleCount = 4),
        )
        assertEquals(RecommendationQueryStrategyType.TOP_TAGS_FILTER, result)
    }

    @Test
    fun `repeated STATUS_EMPTY for the same strategy is forgotten`() {
        val result = RecommendationStrategyRecoveryPolicy.effectiveStartingStrategy(
            lastStrategy = RecommendationQueryStrategyType.TAG_PAIR,
            recentProgress = listOf(
                progress(RecommendationQueryStrategyType.TAG_PAIR, RecommendationDiscoveryProgress.STATUS_EMPTY, page = 1),
                progress(RecommendationQueryStrategyType.TAG_PAIR, RecommendationDiscoveryProgress.STATUS_EMPTY, page = 2),
            ),
            rememberedCount = 0,
            lastRunStatus = null,
        )
        assertNull(result)
    }

    @Test
    fun `a single STATUS_EMPTY row for the same strategy is not enough to forget it`() {
        val result = RecommendationStrategyRecoveryPolicy.effectiveStartingStrategy(
            lastStrategy = RecommendationQueryStrategyType.TAG_PAIR,
            recentProgress = listOf(
                progress(RecommendationQueryStrategyType.TAG_PAIR, RecommendationDiscoveryProgress.STATUS_EMPTY),
            ),
            rememberedCount = 0,
            lastRunStatus = null,
        )
        assertEquals(RecommendationQueryStrategyType.TAG_PAIR, result)
    }

    // --- shouldForgetAfterRun ---

    @Test
    fun `Shown status does not request forgetting`() {
        assertFalse(
            RecommendationStrategyRecoveryPolicy.shouldForgetAfterRun(
                RecommendationSourceRunStatus(1L, RecommendationSourceStatus.Shown, visibleCount = 3),
            ),
        )
    }

    @Test
    fun `NoMatches, FilteredOut, and Error all request forgetting`() {
        listOf(
            RecommendationSourceStatus.NoMatches,
            RecommendationSourceStatus.FilteredOut,
            RecommendationSourceStatus.Error,
        ).forEach { status ->
            assertTrue(
                RecommendationStrategyRecoveryPolicy.shouldForgetAfterRun(RecommendationSourceRunStatus(1L, status)),
                "$status should request forgetting",
            )
        }
    }

    @Test
    fun `HiddenByDuplicateHandling requests forgetting`() {
        assertTrue(
            RecommendationStrategyRecoveryPolicy.shouldForgetAfterRun(
                RecommendationSourceRunStatus(1L, RecommendationSourceStatus.HiddenByDuplicateHandling),
            ),
        )
    }

    @Test
    fun `Disabled and OutsideAttemptLimit do not request forgetting`() {
        listOf(
            RecommendationSourceStatus.Disabled,
            RecommendationSourceStatus.OutsideAttemptLimit,
        ).forEach { status ->
            assertFalse(
                RecommendationStrategyRecoveryPolicy.shouldForgetAfterRun(RecommendationSourceRunStatus(1L, status)),
                "$status should not request forgetting",
            )
        }
    }
}
// KMK <--

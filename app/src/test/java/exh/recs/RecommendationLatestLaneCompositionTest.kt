package exh.recs

import exh.recs.memory.RecommendationCandidateMemoryRanker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.model.MangaTaste
import tachiyomi.domain.taste.model.RatedMangaVisibility
import tachiyomi.domain.taste.model.TasteProfile

// KMK_CLAUDE_LATEST_STRUCTURAL_REPAIR_2026-08-09 -->
/**
 * Domain A: proves the personalized-majority invariant on the **final merged display list**.
 *
 * The prior pass only proved `latestSlots / displayLimit`, which is not a proof: that ratio says
 * nothing about the realised output when the personalized lane returns fewer results than the row
 * could hold. Every assertion here counts the actual per-lane composition of what
 * [RecommendationCandidateMemoryRanker.merge] returns, i.e. exactly what the row renders.
 *
 * Run with:
 * `./gradlew :app:testDebugUnitTest --tests "*.RecommendationLatestLaneCompositionTest"`
 */
class RecommendationLatestLaneCompositionTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun registerCustomMangaInfoBinding() = TestInjektSupport.ensureCustomMangaInfoBound()
    }

    private val scoringProfile = TasteProfile.EMPTY.copy(learnedTagWeights = mapOf("action" to 2.0))

    private fun manga(id: Long, source: Long = 1L) = Manga.create().copy(
        id = id,
        url = "/m/$id",
        source = source,
        ogTitle = "Manga $id",
        ogGenre = listOf("action"),
    )

    private fun rec(
        id: Long,
        lane: RecommendationDiscoveryLane,
        score: Double = 1.0,
        source: Long = 1L,
    ) = PersonalRecommendation(
        manga = manga(id, source),
        score = score,
        matchedGroups = listOf("action"),
        lane = lane,
    )

    private fun mergeOf(newResults: List<PersonalRecommendation>, limit: Int) =
        RecommendationCandidateMemoryRanker.merge(
            remembered = emptyList(),
            newResults = newResults,
            profile = scoringProfile,
            aliasMap = emptyMap(),
            tasteByKey = emptyMap<MangaTasteKey, MangaTaste>(),
            visibility = RatedMangaVisibility.HIDE_DISLIKED_ONLY,
            seenKeys = emptySet(),
            knownIds = emptySet(),
            limit = limit,
        )

    private fun List<PersonalRecommendation>.latestCount() =
        count { it.lane == RecommendationDiscoveryLane.LATEST_CATALOGUE }

    private fun List<PersonalRecommendation>.nonLatestCount() =
        count { it.lane != RecommendationDiscoveryLane.LATEST_CATALOGUE }

    // ---- Sparse personalized: the case the ratio argument could not cover ----

    @Test
    fun `one personalized result plus two Latest results never lets Latest outnumber personalized`() {
        val merged = mergeOf(
            listOf(
                rec(1L, RecommendationDiscoveryLane.PERSONALIZED, score = 1.0),
                rec(2L, RecommendationDiscoveryLane.LATEST_CATALOGUE, score = 9.0),
                rec(3L, RecommendationDiscoveryLane.LATEST_CATALOGUE, score = 8.0),
            ),
            limit = 5,
        )
        // Even though both Latest candidates outscore the personalized one, the realised row may
        // contain at most as many Latest as personalized results.
        assertTrue(merged.latestCount() <= merged.nonLatestCount(), "Latest outnumbered personalized: $merged")
        assertEquals(1, merged.nonLatestCount())
        assertEquals(1, merged.latestCount())
    }

    @Test
    fun `two personalized results plus two Latest results keep Latest at or below personalized`() {
        val merged = mergeOf(
            listOf(
                rec(1L, RecommendationDiscoveryLane.PERSONALIZED, score = 1.0),
                rec(2L, RecommendationDiscoveryLane.PERSONALIZED, score = 1.0),
                rec(3L, RecommendationDiscoveryLane.LATEST_CATALOGUE, score = 9.0),
                rec(4L, RecommendationDiscoveryLane.LATEST_CATALOGUE, score = 8.0),
            ),
            limit = 5,
        )
        assertEquals(2, merged.nonLatestCount())
        assertEquals(2, merged.latestCount())
        assertTrue(merged.latestCount() <= merged.nonLatestCount())
    }

    @Test
    fun `a full personalized set still admits Latest but keeps personalized in the majority`() {
        val personalized = (1L..5L).map { rec(it, RecommendationDiscoveryLane.PERSONALIZED, score = 1.0) }
        val latest = (10L..12L).map { rec(it, RecommendationDiscoveryLane.LATEST_CATALOGUE, score = 9.0) }
        val merged = mergeOf(personalized + latest, limit = 5)

        assertEquals(5, merged.size)
        // 2/5 = 40%: Latest contributes, personalized stays the strict majority.
        assertEquals(2, merged.latestCount())
        assertEquals(3, merged.nonLatestCount())
        assertTrue(merged.latestCount() < merged.nonLatestCount())
    }

    @Test
    fun `empty personalized results let Latest keep its pre-existing rescue role`() {
        val merged = mergeOf(
            (1L..3L).map { rec(it, RecommendationDiscoveryLane.LATEST_CATALOGUE, score = 5.0) },
            limit = 5,
        )
        // No personalized candidate exists at all, so this is the original fallback behavior and
        // Latest may fill the row -- deliberately unchanged.
        assertEquals(3, merged.size)
        assertEquals(3, merged.latestCount())
    }

    @Test
    fun `Popular fallback counts as non-Latest and can hold the majority on its own`() {
        val merged = mergeOf(
            listOf(
                rec(1L, RecommendationDiscoveryLane.POPULAR_CATALOGUE, score = 1.0),
                rec(2L, RecommendationDiscoveryLane.POPULAR_CATALOGUE, score = 1.0),
                rec(3L, RecommendationDiscoveryLane.LATEST_CATALOGUE, score = 9.0),
                rec(4L, RecommendationDiscoveryLane.LATEST_CATALOGUE, score = 9.0),
                rec(5L, RecommendationDiscoveryLane.LATEST_CATALOGUE, score = 9.0),
            ),
            limit = 5,
        )
        assertEquals(2, merged.nonLatestCount())
        assertTrue(merged.latestCount() <= merged.nonLatestCount())
    }

    // ---- Exhaustive invariant over the realised output ----

    @Test
    fun `across every supported display limit and Latest percentage the final list keeps personalized dominant`() {
        val displayLimits = listOf(5, 10, 15, 20, 30)
        for (limit in displayLimits) {
            for (percent in RecommendationLatestBudgetPolicy.SUPPORTED_VALUES) {
                val slots = RecommendationLatestBudgetPolicy.resolveAdditiveSlotsPerSource(limit, percent)
                // Vary how sparse the personalized lane is, including the pathological 1-result case.
                for (personalizedCount in 0..limit) {
                    val personalized = (1L..personalizedCount.toLong())
                        .map { rec(it, RecommendationDiscoveryLane.PERSONALIZED, score = 1.0) }
                    val latest = (1..slots)
                        .map { rec(1000L + it, RecommendationDiscoveryLane.LATEST_CATALOGUE, score = 9.0) }
                    val merged = mergeOf(personalized + latest, limit = limit)

                    if (merged.nonLatestCount() > 0) {
                        assertTrue(
                            merged.latestCount() <= merged.nonLatestCount(),
                            "limit=$limit percent=$percent personalized=$personalizedCount -> $merged",
                        )
                    }
                    assertTrue(
                        merged.latestCount() <= RecommendationLatestBudgetPolicy.MAX_ADDITIVE_SLOTS_PER_SOURCE,
                        "Latest exceeded the absolute per-source ceiling: $merged",
                    )
                    assertTrue(merged.size <= limit, "merged exceeded the display limit: $merged")
                }
            }
        }
    }

    @Test
    fun `a disabled Latest lane contributes no additive slots and leaves the row purely personalized`() {
        assertEquals(0, RecommendationLatestBudgetPolicy.resolveAdditiveSlotsPerSource(5, 0))
        val merged = mergeOf(
            (1L..3L).map { rec(it, RecommendationDiscoveryLane.PERSONALIZED, score = 1.0) },
            limit = 5,
        )
        assertEquals(0, merged.latestCount())
        assertEquals(3, merged.nonLatestCount())
    }

    @Test
    fun `an empty Latest contribution leaves the personalized row completely unchanged`() {
        val personalized = (1L..4L).map { rec(it, RecommendationDiscoveryLane.PERSONALIZED, score = 1.0) }
        val withNoLatest = mergeOf(personalized, limit = 5)
        assertEquals(4, withNoLatest.size)
        assertEquals(0, withNoLatest.latestCount())
    }

    // ---- Ordering, dedup, permutation ----

    @Test
    fun `enforcement preserves relative order and never invents or duplicates a candidate`() {
        val input = listOf(
            rec(1L, RecommendationDiscoveryLane.LATEST_CATALOGUE, score = 9.0),
            rec(2L, RecommendationDiscoveryLane.PERSONALIZED, score = 8.0),
            rec(3L, RecommendationDiscoveryLane.LATEST_CATALOGUE, score = 7.0),
            rec(4L, RecommendationDiscoveryLane.PERSONALIZED, score = 6.0),
            rec(5L, RecommendationDiscoveryLane.LATEST_CATALOGUE, score = 5.0),
        )
        val out = RecommendationLatestBudgetPolicy.enforcePersonalizedMajority(input, limit = 5)

        // Output is a subsequence of the input: same relative order, no reordering, no duplicates.
        val inputIds = input.map { it.manga.id }
        val outIds = out.map { it.manga.id }
        assertEquals(outIds, outIds.sortedBy { inputIds.indexOf(it) })
        assertEquals(outIds.distinct(), outIds)
        assertTrue(outIds.all { it in inputIds })
        assertTrue(out.latestCount() <= out.nonLatestCount())
    }

    @Test
    fun `enforcement is deterministic across repeated identical calls`() {
        val input = listOf(
            rec(1L, RecommendationDiscoveryLane.PERSONALIZED, score = 5.0),
            rec(2L, RecommendationDiscoveryLane.LATEST_CATALOGUE, score = 5.0),
            rec(3L, RecommendationDiscoveryLane.LATEST_CATALOGUE, score = 5.0),
            rec(4L, RecommendationDiscoveryLane.PERSONALIZED, score = 5.0),
        )
        val first = RecommendationLatestBudgetPolicy.enforcePersonalizedMajority(input, limit = 4)
        val second = RecommendationLatestBudgetPolicy.enforcePersonalizedMajority(input, limit = 4)
        assertEquals(first.map { it.manga.id }, second.map { it.manga.id })
    }

    @Test
    fun `the same manga discovered by both lanes is deduplicated once, keeping the fresh lane`() {
        // Both entries resolve to the same local manga id, so the merge's id-keyed dedup collapses
        // them; the later (fresh) entry wins, including its lane.
        val merged = mergeOf(
            listOf(
                rec(1L, RecommendationDiscoveryLane.PERSONALIZED, score = 1.0),
                rec(1L, RecommendationDiscoveryLane.LATEST_CATALOGUE, score = 1.0),
            ),
            limit = 5,
        )
        assertEquals(1, merged.size)
        assertEquals(1L, merged.single().manga.id)
    }

    @Test
    fun `a zero or negative limit yields an empty row rather than an unbounded one`() {
        val input = listOf(rec(1L, RecommendationDiscoveryLane.PERSONALIZED))
        assertTrue(RecommendationLatestBudgetPolicy.enforcePersonalizedMajority(input, limit = 0).isEmpty())
        assertTrue(RecommendationLatestBudgetPolicy.enforcePersonalizedMajority(input, limit = -1).isEmpty())
    }

    @Test
    fun `hard filtering still runs before lane balancing so a blocked candidate never reaches the row`() {
        val blockingProfile = TasteProfile.EMPTY.copy(
            learnedTagWeights = mapOf("action" to 2.0),
            blockedGroups = setOf("action"),
        )
        val merged = RecommendationCandidateMemoryRanker.merge(
            remembered = emptyList(),
            newResults = listOf(
                rec(1L, RecommendationDiscoveryLane.PERSONALIZED),
                rec(2L, RecommendationDiscoveryLane.LATEST_CATALOGUE),
            ),
            profile = blockingProfile,
            aliasMap = emptyMap(),
            tasteByKey = emptyMap<MangaTasteKey, MangaTaste>(),
            visibility = RatedMangaVisibility.HIDE_DISLIKED_ONLY,
            seenKeys = emptySet(),
            knownIds = emptySet(),
            limit = 5,
        )
        // Every candidate carries only the blocked tag, so nothing survives scoring -- the Latest
        // candidate is not exempt from the blocked-tag filter.
        assertTrue(merged.isEmpty(), "blocked candidates leaked into the row: $merged")
    }

    @Test
    fun `a known-manga candidate is excluded from both lanes before balancing`() {
        val merged = RecommendationCandidateMemoryRanker.merge(
            remembered = emptyList(),
            newResults = listOf(
                rec(1L, RecommendationDiscoveryLane.PERSONALIZED),
                rec(2L, RecommendationDiscoveryLane.LATEST_CATALOGUE),
            ),
            profile = scoringProfile,
            aliasMap = emptyMap(),
            tasteByKey = emptyMap<MangaTasteKey, MangaTaste>(),
            visibility = RatedMangaVisibility.HIDE_DISLIKED_ONLY,
            seenKeys = emptySet(),
            knownIds = setOf(1L, 2L),
            limit = 5,
        )
        assertTrue(merged.isEmpty(), "known manga leaked into the row: $merged")
    }
}
// KMK <--

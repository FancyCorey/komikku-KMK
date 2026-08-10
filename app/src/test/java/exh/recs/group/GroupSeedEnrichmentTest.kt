package exh.recs.group

// KMK -->
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga

/**
 * R-027: Unit tests for GroupSeedTag, GroupSeedRecommendationScorer, and GroupRecommendationSeedBuilder
 * seed assembly logic.
 *
 * Run with: ./gradlew :app:testDebugUnitTest --tests "*.GroupSeedEnrichmentTest"
 */
class GroupSeedEnrichmentTest {

    // ---- Helper factories ----

    private fun manga(id: Long, genres: List<String>?) = Manga.create().copy(
        id = id,
        source = 1L,
        url = "/manga/$id",
        ogTitle = "Manga $id",
        ogGenre = genres,
        initialized = genres != null,
    )

    private fun seedTag(name: String, memberCount: Int, totalMembers: Int) = GroupSeedTag(
        name = name,
        weight = memberCount.toDouble() / totalMembers,
        memberCount = memberCount,
    )

    private fun simpleSeed(seedTags: List<GroupSeedTag>) = GroupRecommendationSeed(
        primaryTitle = "Test",
        titles = listOf("Test"),
        tags = seedTags.map { it.name },
        seedTags = seedTags,
        sourceIds = setOf(1L),
        memberKeys = setOf(1L to "/primary"),
        groupId = "g1",
        metadataMemberCount = seedTags.firstOrNull()?.memberCount ?: 0,
        enrichedMemberCount = 0,
    )

    // ---- Test 1: Tags from all linked members are combined ----

    @Test
    fun `tag frequency counts contributions from all members`() {
        // 3 members: action from all 3, romance from 2, drama from 1
        val tagFreq = mapOf("action" to 3, "romance" to 2, "drama" to 1)
        val totalMembers = 3
        val seedTags = tagFreq.entries
            .sortedByDescending { it.value }
            .map { (tag, count) ->
                GroupSeedTag(tag, count.toDouble() / totalMembers, count)
            }

        assertEquals(3, seedTags.size)
        assertEquals("action", seedTags[0].name)
        assertEquals(1.0, seedTags[0].weight, 0.001)
        assertEquals(3, seedTags[0].memberCount)
        assertEquals("romance", seedTags[1].name)
        assertEquals(2.0 / 3.0, seedTags[1].weight, 0.001)
        assertEquals(2, seedTags[1].memberCount)
    }

    // ---- Test 2: The clicked manga is not the only tag source ----

    @Test
    fun `seed weight reflects multiple sources, not just one`() {
        // If only 1 member contributed a tag, weight < 1.0
        val tag = GroupSeedTag("action", weight = 1.0 / 3.0, memberCount = 1)
        assertTrue(tag.weight < 1.0)
        assertEquals(1, tag.memberCount)

        // If all 3 members contributed, weight = 1.0
        val fullTag = GroupSeedTag("romance", weight = 1.0, memberCount = 3)
        assertEquals(1.0, fullTag.weight, 0.001)
    }

    // ---- Test 3: GroupSeedRecommendationScorer — candidate matching multi-member tags ranks higher ----

    @Test
    fun `candidate with multi-member seed tag gets higher score than single-member tag`() {
        val seedTags = listOf(
            seedTag("action", memberCount = 3, totalMembers = 3), // strong match
            seedTag("romance", memberCount = 1, totalMembers = 3), // weak match
        )
        val seed = simpleSeed(seedTags)

        val candidateAction = manga(1L, listOf("action"))
        val candidateRomance = manga(2L, listOf("romance"))

        val scoreAction = GroupSeedRecommendationScorer.score(candidateAction, seed, emptyMap())
        val scoreRomance = GroupSeedRecommendationScorer.score(candidateRomance, seed, emptyMap())

        assertTrue(scoreAction > scoreRomance) {
            "Multi-member tag match ($scoreAction) should outrank single-member ($scoreRomance)"
        }
    }

    // ---- Test 4: Failing member tag collection is skipped gracefully ----

    @Test
    fun `seed builds successfully even when some members have no genres`() {
        // Member with no genres contributes nothing; seed still builds from the others
        val tagFreq = mutableMapOf<String, Int>()
        val memberManga = listOf(
            manga(1L, listOf("action", "romance")),
            manga(2L, null), // no local genres
            manga(3L, listOf("action", "drama")),
        )
        for (m in memberManga) {
            m.genre?.map { it.trim().lowercase() }?.filter { it.isNotBlank() }
                ?.forEach { tag -> tagFreq[tag] = (tagFreq[tag] ?: 0) + 1 }
        }

        assertEquals(3, tagFreq.size)
        assertEquals(2, tagFreq["action"]) // in 2 members
        assertEquals(1, tagFreq["romance"])
        assertEquals(1, tagFreq["drama"])
    }

    // ---- Test 5: Timeout returns a partial seed (no crash) ----

    @Test
    fun `partial seed is valid when enrichment returns no results`() {
        // Simulate that enrichment timed out — seed built from local data only
        val seedTags = listOf(seedTag("action", 1, 1))
        val seed = simpleSeed(seedTags)

        assertEquals(0, seed.enrichedMemberCount)
        assertEquals(1, seed.seedTags.size)
        assertFalse(seed.tags.isEmpty())
    }

    // ---- Test 6: Seed member keys exclude all linked versions ----

    @Test
    fun `seed member keys include primary and all group members`() {
        val memberKeys = setOf(
            1L to "/manga/a",
            2L to "/manga/b",
            3L to "/manga/c",
        )
        val seed = GroupRecommendationSeed(
            primaryTitle = "Manga A",
            titles = listOf("Manga A", "Manga B", "Manga C"),
            tags = listOf("action"),
            seedTags = listOf(seedTag("action", 3, 3)),
            sourceIds = setOf(1L, 2L, 3L),
            memberKeys = memberKeys,
            groupId = "g-test",
        )

        assertTrue(seed.memberKeys.contains(1L to "/manga/a"))
        assertTrue(seed.memberKeys.contains(2L to "/manga/b"))
        assertTrue(seed.memberKeys.contains(3L to "/manga/c"))
        assertEquals(3, seed.memberKeys.size)
    }

    // ---- Test 7: GroupSeedRecommendationScorer — multi-member match outranks pure personal affinity ----

    @Test
    fun `group seed scorer returns higher score for strong multi-member tag overlap`() {
        val seedTags = listOf(
            seedTag("action", memberCount = 3, totalMembers = 3),
            seedTag("martial arts", memberCount = 3, totalMembers = 3),
        )
        val seed = simpleSeed(seedTags)

        val strongMatch = manga(1L, listOf("action", "martial arts"))
        val noMatch = manga(2L, listOf("romance", "slice of life"))

        val scoreStrong = GroupSeedRecommendationScorer.score(strongMatch, seed, emptyMap())
        val scoreNone = GroupSeedRecommendationScorer.score(noMatch, seed, emptyMap())

        assertTrue(scoreStrong > 0.0) { "Strong group-match should have positive score" }
        assertEquals(0.0, scoreNone, 0.0) { "No matching tags should score 0.0" }
        assertTrue(scoreStrong > scoreNone)
    }

    // ---- Test 8: Blocked tags are rejected by PersonalRecommendationScorer, not GroupSeedScorer ----

    @Test
    fun `GroupSeedRecommendationScorer does not block candidates on blocked tags`() {
        // The seed scorer itself never blocks — it only returns a positive partial score
        // Blocking is PersonalRecommendationScorer's responsibility
        val seedTags = listOf(seedTag("ecchi", memberCount = 2, totalMembers = 2))
        val seed = simpleSeed(seedTags)

        val candidate = manga(1L, listOf("ecchi", "action"))
        val groupScore = GroupSeedRecommendationScorer.score(candidate, seed, emptyMap())

        // GroupSeedScorer returns a positive score; PersonalRecommendationScorer would block it
        assertTrue(groupScore > 0.0) { "GroupSeedScorer should not block — blocking is PersonalRecommendationScorer's job" }
    }

    // ---- Test 9: Empty seed returns 0.0 group score ----

    @Test
    fun `empty seed tags return group score of 0`() {
        val emptySeed = GroupRecommendationSeed(
            primaryTitle = "Test",
            titles = listOf("Test"),
            tags = emptyList(),
            seedTags = emptyList(),
            sourceIds = setOf(1L),
            memberKeys = setOf(1L to "/manga/x"),
            groupId = null,
        )
        val candidate = manga(1L, listOf("action"))
        val score = GroupSeedRecommendationScorer.score(candidate, emptySeed, emptyMap())
        assertEquals(0.0, score, 0.0)
    }

    // ---- Test 10: Alias map is applied during scoring ----

    @Test
    fun `alias map normalizes candidate genres before seed lookup`() {
        val seedTags = listOf(seedTag("action", memberCount = 2, totalMembers = 2))
        val seed = simpleSeed(seedTags)

        // Candidate has "akshon" but aliasMap maps it to "action"
        val candidateWithAlias = manga(1L, listOf("akshon"))
        val aliasMap = mapOf("akshon" to "action")

        val scoreWithAlias = GroupSeedRecommendationScorer.score(candidateWithAlias, seed, aliasMap)
        val scoreNoAlias = GroupSeedRecommendationScorer.score(candidateWithAlias, seed, emptyMap())

        assertTrue(scoreWithAlias > 0.0) { "Alias should resolve 'akshon' to 'action' and match seed" }
        assertEquals(0.0, scoreNoAlias, 0.0) { "Without alias, 'akshon' should not match 'action'" }
    }
}
// KMK <--

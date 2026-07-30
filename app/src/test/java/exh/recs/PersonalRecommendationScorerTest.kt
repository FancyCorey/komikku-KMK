package exh.recs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.model.TagPreference
import tachiyomi.domain.taste.model.TasteProfile

class PersonalRecommendationScorerTest {

    // --- Helpers ---

    private fun manga(genres: List<String>, sourceId: Long = 1L): Manga =
        Manga.create().copy(ogGenre = genres.ifEmpty { null }, source = sourceId)

    private fun profile(
        learnedTagWeights: Map<String, Double> = emptyMap(),
        explicitTagPreferences: Map<String, Int> = emptyMap(),
        sourceAffinity: Map<Long, Double> = emptyMap(),
        blockedGroups: Set<String> = emptySet(),
    ) = TasteProfile(learnedTagWeights, explicitTagPreferences, sourceAffinity, blockedGroups)

    // --- Tests ---

    @Test
    fun `blocked tag causes hard rejection before scoring`() {
        val candidate = manga(listOf("Harem"))
        val p = profile(blockedGroups = setOf("harem"))

        val result = PersonalRecommendationScorer.score(candidate, p, emptyMap())

        assertTrue(result.blocked)
        assertEquals(0.0, result.score, 0.001)
    }

    @Test
    fun `blocked group via alias causes hard rejection`() {
        // Candidate has "Yuri"; alias maps "yuri" -> "girls_love"; profile blocks "girls_love"
        val candidate = manga(listOf("Yuri"))
        val aliasMap = mapOf("yuri" to "girls_love")
        val p = profile(blockedGroups = setOf("girls_love"))

        val result = PersonalRecommendationScorer.score(candidate, p, aliasMap)

        assertTrue(result.blocked)
    }

    @Test
    fun `preferred tag gives positive score`() {
        val candidate = manga(listOf("Villainess"))
        val p = profile(explicitTagPreferences = mapOf("villainess" to TagPreference.PREFER.value))

        val result = PersonalRecommendationScorer.score(candidate, p, emptyMap())

        assertFalse(result.blocked)
        assertTrue(result.score > 0.0) { "Preferred tag should give positive score, got ${result.score}" }
    }

    @Test
    fun `disliked tag gives negative score`() {
        val candidate = manga(listOf("Harem"))
        val p = profile(explicitTagPreferences = mapOf("harem" to TagPreference.DISLIKE.value))

        val result = PersonalRecommendationScorer.score(candidate, p, emptyMap())

        assertFalse(result.blocked)
        assertTrue(result.score < 0.0) { "Disliked tag should give negative score, got ${result.score}" }
    }

    @Test
    fun `explicit prefer outweighs learned weight for the same tag`() {
        val candidate = manga(listOf("Action"))
        val learnedOnly = profile(learnedTagWeights = mapOf("action" to 2.0))
        val withExplicit = profile(
            learnedTagWeights = mapOf("action" to 2.0),
            explicitTagPreferences = mapOf("action" to TagPreference.PREFER.value),
        )

        val learnedScore = PersonalRecommendationScorer.score(candidate, learnedOnly, emptyMap()).score
        val combinedScore = PersonalRecommendationScorer.score(candidate, withExplicit, emptyMap()).score

        assertTrue(combinedScore > learnedScore) {
            "Explicit prefer ($combinedScore) should exceed learned-only ($learnedScore)"
        }
    }

    @Test
    fun `learned positive weight from loved manga raises score`() {
        val candidate = manga(listOf("Action", "Fantasy"))
        val p = profile(learnedTagWeights = mapOf("action" to 2.0, "fantasy" to 1.5))

        val result = PersonalRecommendationScorer.score(candidate, p, emptyMap())

        assertTrue(result.score > 0.0)
        assertTrue(result.matchedGroups.isNotEmpty())
    }

    @Test
    fun `learned negative weight from disliked manga lowers score`() {
        val candidate = manga(listOf("Harem"))
        val p = profile(learnedTagWeights = mapOf("harem" to -4.0))

        val result = PersonalRecommendationScorer.score(candidate, p, emptyMap())

        assertFalse(result.blocked)
        assertTrue(result.score < 0.0)
    }

    @Test
    fun `source affinity is a weak positive signal`() {
        val candidate = manga(listOf("Action"), sourceId = 42L)
        val p = profile(
            learnedTagWeights = mapOf("action" to 1.0),
            sourceAffinity = mapOf(42L to 0.5),
        )
        val pNoAffinity = profile(learnedTagWeights = mapOf("action" to 1.0))

        val withAffinity = PersonalRecommendationScorer.score(candidate, p, emptyMap()).score
        val withoutAffinity = PersonalRecommendationScorer.score(candidate, pNoAffinity, emptyMap()).score

        assertTrue(withAffinity > withoutAffinity) { "Source affinity should add a small positive bonus" }
        // Source affinity alone (0.5) should be much smaller than a loved-tag weight (2.0)
        assertTrue(withAffinity - withoutAffinity < 1.0) { "Source affinity should be weak" }
    }

    @Test
    fun `candidate with no genres scores zero from tag components`() {
        val candidate = manga(emptyList())
        val p = profile(
            learnedTagWeights = mapOf("action" to 2.0),
            explicitTagPreferences = mapOf("villainess" to TagPreference.PREFER.value),
        )

        val result = PersonalRecommendationScorer.score(candidate, p, emptyMap())

        assertFalse(result.blocked)
        assertEquals(0.0, result.score, 0.001) { "No genres → tag scores all zero" }
    }

    @Test
    fun `Yuri and Girls Love map to same group key girls_love via alias`() {
        val yuri = manga(listOf("Yuri"))
        val girlsLove = manga(listOf("Girls Love"))
        val aliasMap = mapOf("yuri" to "girls_love", "girls love" to "girls_love")
        val p = profile(explicitTagPreferences = mapOf("girls_love" to TagPreference.PREFER.value))

        val yuriScore = PersonalRecommendationScorer.score(yuri, p, aliasMap).score
        val glScore = PersonalRecommendationScorer.score(girlsLove, p, aliasMap).score

        assertEquals(yuriScore, glScore, 0.001) { "Yuri and Girls Love should score identically via alias" }
        assertTrue(yuriScore > 0.0)
    }

    @Test
    fun `rankCandidates sorts by score descending and respects limit`() {
        val candidates = listOf(
            manga(listOf("Villainess")),
            manga(listOf("Action")),
            manga(listOf("Romance")),
        )
        val p = profile(
            explicitTagPreferences = mapOf("villainess" to TagPreference.PREFER.value),
            learnedTagWeights = mapOf("action" to 1.0),
        )

        val ranked = PersonalRecommendationScorer.rankCandidates(candidates, p, emptyMap(), limit = 2)

        assertEquals(2, ranked.size)
        assertTrue(ranked[0].score >= ranked[1].score) { "Results should be sorted descending by score" }
    }

    @Test
    fun `rankCandidates excludes blocked candidates`() {
        val blocked = manga(listOf("Harem"))
        // KMK v0.8.13: gave "good" a matching learned tag so it has positive taste evidence and
        // remains eligible under the new default relevance gate -- this test is about the block
        // filter specifically, not about evidence gating (covered separately below).
        val good = manga(listOf("Action"))
        val p = profile(blockedGroups = setOf("harem"), learnedTagWeights = mapOf("action" to 1.0))

        val ranked = PersonalRecommendationScorer.rankCandidates(listOf(blocked, good), p, emptyMap())

        assertTrue(ranked.none { it.manga.genre?.contains("Harem") == true }) { "Blocked candidate should not appear" }
        assertEquals(1, ranked.size)
    }

    // KMK v0.8.13: positive taste evidence gate -->

    @Test
    fun `source-affinity-only candidate is excluded by default`() {
        // No preferred/learned tags at all -- only a positive sourceAffinity contribution.
        val candidate = manga(listOf("Something Unrelated"), sourceId = 99L)
        val p = profile(sourceAffinity = mapOf(99L to 0.8))

        val ranked = PersonalRecommendationScorer.rankCandidates(listOf(candidate), p, emptyMap())

        assertTrue(ranked.isEmpty()) { "A candidate whose only positive signal is source affinity must not be eligible by default" }
    }

    @Test
    fun `source-affinity-only candidate is included only when requirePositiveTasteEvidence is false`() {
        val candidate = manga(listOf("Something Unrelated"), sourceId = 99L)
        val p = profile(sourceAffinity = mapOf(99L to 0.8))

        val ranked = PersonalRecommendationScorer.rankCandidates(
            listOf(candidate),
            p,
            emptyMap(),
            requirePositiveTasteEvidence = false,
        )

        assertEquals(1, ranked.size)
    }

    @Test
    fun `candidate with a preferred tag and source affinity remains included and ranks higher than an otherwise equal candidate without source affinity`() {
        val withAffinity = manga(listOf("Villainess"), sourceId = 42L)
        val withoutAffinity = manga(listOf("Villainess"), sourceId = 43L)
        val p = profile(
            explicitTagPreferences = mapOf("villainess" to TagPreference.PREFER.value),
            sourceAffinity = mapOf(42L to 0.5),
        )

        val ranked = PersonalRecommendationScorer.rankCandidates(listOf(withoutAffinity, withAffinity), p, emptyMap())

        assertEquals(2, ranked.size) { "Both candidates have positive taste evidence, so both remain eligible" }
        assertEquals(42L, ranked.first().manga.source) { "The candidate with source affinity should rank first" }
    }

    @Test
    fun `blocked candidate remains excluded regardless of requirePositiveTasteEvidence`() {
        val blocked = manga(listOf("Harem"))
        val p = profile(blockedGroups = setOf("harem"))

        val strict = PersonalRecommendationScorer.rankCandidates(listOf(blocked), p, emptyMap())
        val loose = PersonalRecommendationScorer.rankCandidates(listOf(blocked), p, emptyMap(), requirePositiveTasteEvidence = false)

        assertTrue(strict.isEmpty())
        assertTrue(loose.isEmpty())
    }

    @Test
    fun `learned positive weight alone counts as positive evidence`() {
        val candidate = manga(listOf("Action"))
        val p = profile(learnedTagWeights = mapOf("action" to 1.5))

        val ranked = PersonalRecommendationScorer.rankCandidates(listOf(candidate), p, emptyMap())

        assertEquals(1, ranked.size)
    }

    @Test
    fun `disliked-only negative-only candidate is excluded`() {
        val candidate = manga(listOf("Harem"))
        val p = profile(explicitTagPreferences = mapOf("harem" to TagPreference.DISLIKE.value))

        val ranked = PersonalRecommendationScorer.rankCandidates(listOf(candidate), p, emptyMap())

        assertTrue(ranked.isEmpty()) { "A negative-only match has no positive evidence and must not be eligible" }
    }
    // KMK <--
}

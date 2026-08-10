package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.model.SourceEvaluationKeys
import tachiyomi.domain.taste.model.SourceEvaluationMetadataConfidence
import tachiyomi.domain.taste.model.SourceEvaluationVerdict
import tachiyomi.domain.taste.model.TagPreference
import tachiyomi.domain.taste.model.TasteProfile

/**
 * Tests for [SourceEvaluationScorer].
 *
 * v0.7.42: this scorer evaluates catalogue fit only (Popular/Latest samples, decision D1) and
 * reuses the real [exh.recs.PersonalRecommendationScorer] taste-matching contract per catalogue
 * item instead of an ad-hoc set-membership approximation (decision D4).
 *
 * v0.7.47: catalogue samples are bounded-enriched via `getMangaDetails()` before reaching this
 * scorer (see [SourceEvaluationCatalogueEnricher] for enrichment-specific tests). This scorer's own
 * coverage focuses on the split evidence counters, the metadata-sparse -> NEEDS_MANUAL_REVIEW gate,
 * and the noisy/adult false-positive gate on STRONG_FIT — see
 * docs/recommendations/KMK.md.
 *
 * Run with: ./gradlew :app:testDebugUnitTest --tests "*.SourceEvaluationScorerTest"
 */
class SourceEvaluationScorerTest {

    private fun manga(genres: List<String>?, id: Long = 1L, title: String = "Manga $id"): Manga =
        Manga.create().copy(id = id, ogTitle = title, ogGenre = genres, source = 1L, url = "/m/$id")

    private fun profile(
        learnedTagWeights: Map<String, Double> = emptyMap(),
        explicitTagPreferences: Map<String, Int> = emptyMap(),
        blockedGroups: Set<String> = emptySet(),
    ) = TasteProfile(learnedTagWeights, explicitTagPreferences, emptyMap(), blockedGroups)

    private fun score(
        catalogueSamples: List<Manga>,
        tasteProfile: TasteProfile = profile(),
        popularCount: Int = catalogueSamples.size,
        latestCount: Int = 0,
        errorCount: Int = 0,
        aliasMap: Map<String, String> = emptyMap(),
    ) = SourceEvaluationScorer.score(
        extensionName = "Test Extension",
        pkgName = "eu.kanade.test",
        signatureHash = "sig",
        sourceId = 123L,
        sourceName = "Test Source",
        lang = "en",
        baseUrl = "https://example.com",
        repoName = "test-repo",
        sourceCount = 1,
        isNsfw = false,
        catalogueSamples = catalogueSamples,
        popularCount = popularCount,
        latestCount = latestCount,
        errorCount = errorCount,
        tasteProfile = tasteProfile,
        aliasMap = aliasMap,
    )

    // ---- Catalogue-only sampling (no search signal) ----

    @Test
    fun `search fields are always zero — search is measured separately by SourceRecommendationFitProbe`() {
        val result = score(listOf(manga(listOf("action"), id = 1L)))
        assertEquals(0, result.searchCount)
        assertEquals(0, result.searchSuccessCount)
        assertEquals(0.0, result.searchReliabilityScore, 0.001)
    }

    @Test
    fun `likedTitleMatchCount is always zero — not implemented, retained for schema compatibility`() {
        val result = score(listOf(manga(listOf("action"), id = 1L)))
        assertEquals(0, result.likedTitleMatchCount)
    }

    @Test
    fun `sampleCount reflects catalogue samples only`() {
        val samples = listOf(manga(listOf("action"), id = 1L), manga(listOf("drama"), id = 2L))
        val result = score(samples)
        assertEquals(2, result.sampleCount)
    }

    // ---- Split evidence counters (v0.7.47) ----

    @Test
    fun `catalogue item matching a learned tag counts as a preferred match`() {
        val samples = listOf(manga(listOf("action"), id = 1L))
        val result = score(samples, tasteProfile = profile(learnedTagWeights = mapOf("action" to 2.0)))
        assertEquals(1, result.positiveCandidateCount)
        assertEquals(1, result.learnedPositiveGroupHitCount)
    }

    @Test
    fun `catalogue item matching an explicit preferred tag counts toward explicitPreferredGroupHitCount`() {
        val samples = listOf(manga(listOf("action"), id = 1L))
        val result = score(
            samples,
            tasteProfile = profile(explicitTagPreferences = mapOf("action" to TagPreference.PREFER.value)),
        )
        assertEquals(1, result.explicitPreferredGroupHitCount)
        assertEquals(1, result.positiveCandidateCount)
    }

    @Test
    fun `catalogue item with a blocked tag is hard-excluded, not soft-penalized`() {
        val samples = listOf(manga(listOf("harem"), id = 1L))
        val result = score(samples, tasteProfile = profile(blockedGroups = setOf("harem")))
        assertEquals(1, result.blockedCandidateCount)
        assertEquals(0, result.positiveCandidateCount)
    }

    @Test
    fun `catalogue item with a disliked tag counts as negative but is not hard-blocked`() {
        val samples = listOf(manga(listOf("drama"), id = 1L))
        val result = score(
            samples,
            tasteProfile = profile(explicitTagPreferences = mapOf("drama" to TagPreference.DISLIKE.value)),
        )
        assertEquals(1, result.negativeCandidateCount)
        assertEquals(0, result.blockedCandidateCount)
    }

    @Test
    fun `catalogue item with no taste signal is neither preferred nor blocked`() {
        val samples = listOf(manga(listOf("random_unrelated_tag"), id = 1L))
        val result = score(samples, tasteProfile = profile(learnedTagWeights = mapOf("action" to 2.0)))
        assertEquals(0, result.positiveCandidateCount)
        assertEquals(0, result.blockedCandidateCount)
    }

    // ---- Backward-compatible mirrors ----

    @Test
    fun `preferredTagMatchCount mirrors positiveCandidateCount`() {
        val samples = listOf(manga(listOf("action"), id = 1L), manga(listOf("random"), id = 2L))
        val result = score(samples, tasteProfile = profile(learnedTagWeights = mapOf("action" to 2.0)))
        assertEquals(result.positiveCandidateCount, result.preferredTagMatchCount)
    }

    @Test
    fun `blockedTagMatchCount mirrors blockedCandidateCount`() {
        val samples = listOf(manga(listOf("harem"), id = 1L))
        val result = score(samples, tasteProfile = profile(blockedGroups = setOf("harem")))
        assertEquals(result.blockedCandidateCount, result.blockedTagMatchCount)
    }

    // ---- catalogue_metadata_confidence ----

    @Test
    fun `all samples with genre tags yields HIGH confidence`() {
        val samples = (1L..5L).map { manga(listOf("action"), id = it) }
        val result = score(samples)
        assertEquals(SourceEvaluationMetadataConfidence.HIGH, result.catalogueMetadataConfidence)
    }

    @Test
    fun `no samples with genre tags yields UNKNOWN confidence when samples exist but all are empty`() {
        val samples = (1L..5L).map { manga(null, id = it) }
        val result = score(samples)
        assertEquals(SourceEvaluationMetadataConfidence.UNKNOWN, result.catalogueMetadataConfidence)
    }

    @Test
    fun `zero catalogue samples yields UNKNOWN confidence`() {
        val result = score(emptyList())
        assertEquals(SourceEvaluationMetadataConfidence.UNKNOWN, result.catalogueMetadataConfidence)
    }

    @Test
    fun `a minority of samples with genre tags yields LOW confidence`() {
        val samples = listOf(manga(listOf("action"), id = 1L)) + (2L..5L).map { manga(null, id = it) }
        val result = score(samples)
        assertEquals(SourceEvaluationMetadataConfidence.LOW, result.catalogueMetadataConfidence)
    }

    // ---- Metadata-sparse false negative (audit finding: Elf Toon-like sources) ----

    @Test
    fun `metadata-sparse source with many samples and no tags returns NEEDS_MANUAL_REVIEW, not confident WEAK`() {
        val samples = (1L..28L).map { manga(null, id = it) }
        val result = score(samples, tasteProfile = profile(learnedTagWeights = mapOf("action" to 2.0)))
        assertEquals(SourceEvaluationVerdict.NEEDS_MANUAL_REVIEW, result.verdict)
        assertNotEquals(SourceEvaluationVerdict.WEAK, result.verdict)
    }

    @Test
    fun `low-confidence metadata with some positive evidence returns NEEDS_MANUAL_REVIEW`() {
        val samples = listOf(manga(listOf("action"), id = 1L)) + (2L..10L).map { manga(null, id = it) }
        val result = score(samples, tasteProfile = profile(learnedTagWeights = mapOf("action" to 2.0)))
        assertEquals(SourceEvaluationMetadataConfidence.LOW, result.catalogueMetadataConfidence)
        assertEquals(SourceEvaluationVerdict.NEEDS_MANUAL_REVIEW, result.verdict)
    }

    @Test
    fun `detail-enriched samples with user-preferred tags can become STRONG_FIT`() {
        val samples = (1L..15L).map { manga(listOf("action"), id = it) }
        val result = score(
            samples,
            tasteProfile = profile(explicitTagPreferences = mapOf("action" to TagPreference.PREFER.value)),
        )
        assertEquals(SourceEvaluationVerdict.STRONG_FIT, result.verdict)
    }

    // ---- Noisy/adult false positive (audit finding: KaliScan-like sources) ----

    @Test
    fun `source with Action plus blocked BL alias does not become STRONG_FIT`() {
        // 15 action samples would normally be a strong catalogue fit, but a third of the catalogue
        // is hard-blocked via the boys_love alias group (Yaoi -> boys_love), which must gate the
        // verdict down from STRONG_FIT.
        val positiveSamples = (1L..10L).map { manga(listOf("action"), id = it) }
        val blockedSamples = (11L..15L).map { manga(listOf("yaoi"), id = it) }
        val aliasMap = mapOf("yaoi" to "boys_love")
        val result = score(
            positiveSamples + blockedSamples,
            tasteProfile = profile(
                explicitTagPreferences = mapOf("action" to TagPreference.PREFER.value),
                blockedGroups = setOf("boys_love"),
            ),
            aliasMap = aliasMap,
        )
        assertNotEquals(SourceEvaluationVerdict.STRONG_FIT, result.verdict)
        assertEquals(5, result.blockedCandidateCount)
    }

    @Test
    fun `mixed source with Action Martial Arts plus Yaoi Smut Adult Ecchi does not become STRONG_FIT`() {
        val positive = (1L..8L).map { manga(listOf("action", "martial arts"), id = it) }
        val adultHeavy = (9L..15L).map { manga(listOf("yaoi", "smut", "adult", "ecchi"), id = it) }
        val result = score(
            positive + adultHeavy,
            tasteProfile = profile(explicitTagPreferences = mapOf("action" to TagPreference.PREFER.value)),
        )
        assertNotEquals(SourceEvaluationVerdict.STRONG_FIT, result.verdict)
        assertTrue(result.adultSignalCandidateCount >= 7) { "expected adult signal candidates from yaoi/smut/adult tags" }
    }

    @Test
    fun `Adult Smut Mature Ecchi are counted as adult signal candidates without merging into explicit_heavy alone`() {
        val samples = listOf(
            manga(listOf("mature"), id = 1L),
            manga(listOf("ecchi"), id = 2L),
        )
        val result = score(samples)
        // Mature/Ecchi alone (without hentai/porn-level terms) should not push explicitScore to
        // EXPLICIT_HEAVY on their own — ecchi/mature stay a distinct, softer signal.
        assertNotEquals(SourceEvaluationVerdict.EXPLICIT_HEAVY, result.verdict)
    }

    // ---- Verdict thresholds (catalogue-only) ----

    @Test
    fun `no taste signal with samples yields WEAK, not an error`() {
        val samples = (1L..15L).map { manga(listOf("random_unrelated_tag"), id = it) }
        val result = score(samples, tasteProfile = profile(learnedTagWeights = mapOf("action" to 2.0)))
        assertEquals(SourceEvaluationVerdict.WEAK, result.verdict)
    }

    @Test
    fun `zero samples with errors yields ERROR`() {
        val result = score(emptyList(), errorCount = 1)
        assertEquals(SourceEvaluationVerdict.ERROR, result.verdict)
    }

    @Test
    fun `explicit-heavy extension name biases toward EXPLICIT_HEAVY even with few samples`() {
        val result = SourceEvaluationScorer.score(
            extensionName = "Hentai Reader",
            pkgName = "eu.kanade.hentai",
            signatureHash = "sig",
            sourceId = 1L,
            sourceName = "Hentai Source",
            lang = "en",
            baseUrl = null,
            repoName = null,
            sourceCount = 1,
            isNsfw = true,
            catalogueSamples = listOf(manga(listOf("hentai"), id = 1L)),
            popularCount = 1,
            latestCount = 0,
            errorCount = 0,
            tasteProfile = profile(),
        )
        assertEquals(SourceEvaluationVerdict.EXPLICIT_HEAVY, result.verdict)
    }

    @Test
    fun `evaluationVersion is always the current constant`() {
        val result = score(listOf(manga(listOf("action"), id = 1L)))
        assertEquals(SourceEvaluationKeys.CURRENT_VERSION, result.evaluationVersion)
    }

    @Test
    fun `enrichment evidence counters are threaded through into the evaluation record`() {
        val result = SourceEvaluationScorer.score(
            extensionName = "Test Extension",
            pkgName = "eu.kanade.test",
            signatureHash = "sig",
            sourceId = 123L,
            sourceName = "Test Source",
            lang = "en",
            baseUrl = null,
            repoName = null,
            sourceCount = 1,
            isNsfw = false,
            catalogueSamples = listOf(manga(listOf("action"), id = 1L)),
            popularCount = 1,
            latestCount = 0,
            errorCount = 0,
            tasteProfile = profile(),
            detailEnrichmentAttemptCount = 5,
            detailEnrichmentSuccessCount = 3,
        )
        assertEquals(5, result.detailEnrichmentAttemptCount)
        assertEquals(3, result.detailEnrichmentSuccessCount)
    }

    private fun assertTrue(condition: Boolean, message: () -> String) {
        org.junit.jupiter.api.Assertions.assertTrue(condition, message)
    }
}

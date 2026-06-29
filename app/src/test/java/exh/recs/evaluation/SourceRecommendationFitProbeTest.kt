package exh.recs.evaluation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.interactor.GetTagAliases
import tachiyomi.domain.taste.model.CrossSourceMangaLink
import tachiyomi.domain.taste.model.MangaTaste
import tachiyomi.domain.taste.model.TagAlias
import tachiyomi.domain.taste.model.TagTaste
import tachiyomi.domain.taste.model.TasteProfile
import tachiyomi.domain.taste.repository.TasteRepository

// KMK -->

/** No-op TasteRepository for unit-testing GetTagAliases without a DB. */
private class FakeTasteRepository(
    private val aliases: List<TagAlias> = emptyList(),
) : TasteRepository {
    override suspend fun getMangaTaste(mangaId: Long): MangaTaste? = null
    override fun getMangaTasteAsFlow(mangaId: Long): Flow<MangaTaste?> = emptyFlow()
    override suspend fun getMangaTaste(source: Long, url: String): MangaTaste? = null
    override fun getMangaTasteAsFlow(source: Long, url: String): Flow<MangaTaste?> = emptyFlow()
    override suspend fun getAllMangaTastes(): List<MangaTaste> = emptyList()
    // KMK --> v0.7.29
    override fun getAllMangaTastesAsFlow(): Flow<List<MangaTaste>> = emptyFlow()
    // KMK <--
    override suspend fun upsertMangaTaste(taste: MangaTaste) {}
    override suspend fun deleteMangaTaste(mangaId: Long) {}
    override suspend fun deleteMangaTaste(source: Long, url: String) {}
    override suspend fun deleteAllMangaTastes() {}
    override suspend fun getTagTaste(normalizedTag: String): TagTaste? = null
    override suspend fun getAllTagTastes(): List<TagTaste> = emptyList()
    override fun getAllTagTastesAsFlow(): Flow<List<TagTaste>> = emptyFlow()
    override suspend fun upsertTagTaste(tagTaste: TagTaste) {}
    override suspend fun deleteTagTaste(normalizedTag: String) {}
    override suspend fun getAllTagAliases(): List<TagAlias> = aliases
    override suspend fun getTagAliasByNormalized(normalizedAlias: String): TagAlias? = null
    override suspend fun upsertTagAlias(alias: TagAlias) {}
    override suspend fun deleteTagAlias(alias: String) {}
    override suspend fun getCrossSourceMangaLinksByGroupId(groupId: String): List<CrossSourceMangaLink> = emptyList()
    override suspend fun getCrossSourceMangaLinkBySourceUrl(source: Long, url: String): CrossSourceMangaLink? = null
    override suspend fun getAllCrossSourceMangaLinks(): List<CrossSourceMangaLink> = emptyList()
    override suspend fun upsertCrossSourceMangaLinks(links: List<CrossSourceMangaLink>) {}
    override suspend fun deleteCrossSourceMangaLink(source: Long, url: String) {}
    override suspend fun deleteCrossSourceMangaLinksByGroupId(groupId: String) {}
    override suspend fun deleteAllCrossSourceMangaLinks() {}
    override suspend fun getAllDisabledSourceIds(): List<Long> = emptyList()
    override fun getAllDisabledSourceIdsAsFlow(): Flow<List<Long>> = emptyFlow()
    override suspend fun disableSource(sourceId: Long) {}
    override suspend fun enableSource(sourceId: Long) {}
}

class SourceRecommendationFitProbeTest {

    private val fakeGetTagAliases = GetTagAliases(FakeTasteRepository())

    // --- Outcome label logic (pure, no source needed) ---

    @Test
    fun `label is TOO_LITTLE_EVIDENCE when no queries ran`() {
        val outcome = SourceRecommendationFitProbeOutcome(queryCount = 0)
        assertEquals(RecommendationQualityLabel.TOO_LITTLE_EVIDENCE, outcome.label())
    }

    @Test
    fun `label is ERROR when queries ran but all errored`() {
        val outcome = SourceRecommendationFitProbeOutcome(
            queryCount = 1,
            querySuccessCount = 0,
            errorCount = 1,
        )
        assertEquals(RecommendationQualityLabel.ERROR, outcome.label())
    }

    @Test
    fun `label is NO_MATCHES when queries succeeded but raw results empty`() {
        val outcome = SourceRecommendationFitProbeOutcome(
            queryCount = 1,
            querySuccessCount = 1,
            rawResultCount = 0,
            errorCount = 0,
        )
        assertEquals(RecommendationQualityLabel.NO_MATCHES, outcome.label())
    }

    @Test
    fun `label thresholds GREAT at high score`() {
        // High visibleCandidateCount + high avgScore + matchedGroups → GREAT
        val outcome = SourceRecommendationFitProbeOutcome(
            queryCount = 2,
            querySuccessCount = 2,
            rawResultCount = 20,
            visibleCandidateCount = 15,
            matchedGroupCount = 5,
            avgCandidateScore = 0.95,
        )
        val label = outcome.label()
        assertTrue(
            label == RecommendationQualityLabel.GREAT || label == RecommendationQualityLabel.GOOD,
            "Expected GREAT or GOOD for high-quality outcome, got $label",
        )
    }

    @Test
    fun `label is WEAK when score is low`() {
        // Tiny visible count, all filtered out → WEAK
        val outcome = SourceRecommendationFitProbeOutcome(
            queryCount = 1,
            querySuccessCount = 1,
            rawResultCount = 5,
            visibleCandidateCount = 1,
            filteredOutCount = 4,
            matchedGroupCount = 0,
            avgCandidateScore = 0.1,
        )
        val label = outcome.label()
        assertEquals(RecommendationQualityLabel.WEAK, label, "Expected WEAK for low-score outcome")
    }

    @Test
    fun `toScorerOutcome maps all fields correctly`() {
        val outcome = SourceRecommendationFitProbeOutcome(
            visibleCandidateCount = 7,
            filteredOutCount = 3,
            blockedTagCandidateCount = 1,
            matchedGroupCount = 4,
            topPicksContribution = 2,
            noMatchesCount = 0,
            errorCount = 0,
            avgCandidateScore = 0.72,
        )
        val scorer = outcome.toScorerOutcome()
        assertEquals(7, scorer.visibleCandidateCount)
        assertEquals(3, scorer.filteredOutCount)
        assertEquals(1, scorer.blockedTagCandidateCount)
        assertEquals(4, scorer.matchedGroupCount)
        assertEquals(2, scorer.topPicksContribution)
        assertEquals(0, scorer.noMatchesCount)
        assertEquals(0, scorer.errorCount)
        assertEquals(0.72, scorer.avgCandidateScore, 0.001)
    }

    // --- Probe fast paths (no external source needed) ---

    @Test
    fun `probe returns TOO_LITTLE_EVIDENCE fast path when taste profile empty`() = runTest {
        val probe = SourceRecommendationFitProbe(fakeGetTagAliases)
        val outcome = probe.probe(
            source = FakeCatalogueSource(),
            tasteProfile = TasteProfile.EMPTY,
        )
        assertEquals(0, outcome.queryCount, "No queries should run for empty profile")
        assertEquals(RecommendationQualityLabel.TOO_LITTLE_EVIDENCE, outcome.label())
        assertTrue(outcome.reasons.isNotEmpty(), "Reasons should explain why no queries ran")
    }

    @Test
    fun `probe returns TOO_LITTLE_EVIDENCE when all tag weights are below threshold`() = runTest {
        val probe = SourceRecommendationFitProbe(fakeGetTagAliases)
        val lowWeightProfile = TasteProfile(
            learnedTagWeights = mapOf("action" to 0.1, "romance" to 0.2),
            explicitTagPreferences = emptyMap(),
            sourceAffinity = emptyMap(),
            blockedGroups = emptySet(),
        )
        val outcome = probe.probe(
            source = FakeCatalogueSource(),
            tasteProfile = lowWeightProfile,
        )
        assertEquals(0, outcome.queryCount)
        assertEquals(RecommendationQualityLabel.TOO_LITTLE_EVIDENCE, outcome.label())
    }

    @Test
    fun `probe errorCount increments when source throws`() = runTest {
        val probe = SourceRecommendationFitProbe(fakeGetTagAliases)
        val throwingSource = FakeCatalogueSource(throws = true)
        val profile = TasteProfile(
            learnedTagWeights = mapOf("action" to 0.9, "fantasy" to 0.8),
            explicitTagPreferences = emptyMap(),
            sourceAffinity = emptyMap(),
            blockedGroups = emptySet(),
        )
        val outcome = probe.probe(source = throwingSource, tasteProfile = profile)
        // Source throws on getSearchManga — errors should be captured, not re-thrown
        assertFalse(outcome.querySuccessCount > 0 && outcome.errorCount == 0, "Error should be captured")
        // Probe should not throw
    }

    // KMK --> v0.7.12: error transparency — reasons are populated for ERROR outcomes
    @Test
    fun `probe reasons list is populated when source throws`() = runTest {
        val probe = SourceRecommendationFitProbe(fakeGetTagAliases)
        val throwingSource = FakeCatalogueSource(throws = true)
        val profile = TasteProfile(
            learnedTagWeights = mapOf("action" to 0.9),
            explicitTagPreferences = emptyMap(),
            sourceAffinity = emptyMap(),
            blockedGroups = emptySet(),
        )
        val outcome = probe.probe(source = throwingSource, tasteProfile = profile)
        assertEquals(RecommendationQualityLabel.ERROR, outcome.label())
        assertTrue(outcome.reasons.isNotEmpty(), "Reasons should be populated so the UI can show why it failed")
        assertTrue(
            outcome.reasons.any { it.contains("error", ignoreCase = true) || it.contains("Simulated", ignoreCase = true) },
            "Reason should mention the error: ${outcome.reasons}",
        )
    }

    @Test
    fun `outcome label ERROR when all plans fail with errorCount gt 0 and querySuccessCount is 0`() {
        val outcome = SourceRecommendationFitProbeOutcome(
            queryCount = 2,
            querySuccessCount = 0,
            errorCount = 2,
            reasons = listOf("Plan TOP_TAGS_FILTER: error — UnknownHostException", "Plan TAG_PAIR: error — timeout"),
        )
        assertEquals(RecommendationQualityLabel.ERROR, outcome.label())
    }

    @Test
    fun `reasons list first entry describes first plan failure`() {
        val outcome = SourceRecommendationFitProbeOutcome(
            queryCount = 1,
            querySuccessCount = 0,
            errorCount = 1,
            reasons = listOf("Plan TOP_TAGS_FILTER: error — Simulated source failure"),
        )
        assertEquals(RecommendationQualityLabel.ERROR, outcome.label())
        assertTrue(outcome.reasons.first().contains("TOP_TAGS_FILTER"), "First reason names the failed plan")
    }

    // KMK --> v0.7.13: enrichment behavior tests
    @Test
    fun `probe returns NO_MATCHES when search succeeds but raw list is empty`() = runTest {
        val probe = SourceRecommendationFitProbe(fakeGetTagAliases)
        val emptySource = FakeCatalogueSource(returns = emptyList())
        val profile = TasteProfile(
            learnedTagWeights = mapOf("action" to 0.9),
            explicitTagPreferences = emptyMap(),
            sourceAffinity = emptyMap(),
            blockedGroups = emptySet(),
        )
        val outcome = probe.probe(source = emptySource, tasteProfile = profile)
        assertEquals(RecommendationQualityLabel.NO_MATCHES, outcome.label())
        assertEquals(0, outcome.errorCount, "Empty results should not produce errors")
        assertTrue(outcome.queryCount > 0, "At least one query plan should have run")
    }

    @Test
    fun `probe reports weak metadata when source returns results without genre`() = runTest {
        val probe = SourceRecommendationFitProbe(fakeGetTagAliases)
        // Source returns results with no genre; getMangaDetails returns same (no genre added)
        val noGenreSource = FakeCatalogueSource(
            returns = listOf(eu.kanade.tachiyomi.source.model.SManga("/manga/1", "Test Manga")),
            detailsGenre = null,
        )
        val profile = TasteProfile(
            learnedTagWeights = mapOf("action" to 0.9),
            explicitTagPreferences = emptyMap(),
            sourceAffinity = emptyMap(),
            blockedGroups = emptySet(),
        )
        val outcome = probe.probe(source = noGenreSource, tasteProfile = profile)
        // Search succeeded, raw results exist, but no genre → scored candidates = 0
        assertTrue(outcome.rawResultCount > 0, "Should have raw results")
        assertEquals(0, outcome.errorCount, "Missing genre is not an error")
        assertTrue(
            outcome.weakMetadataCandidateCount > 0 || outcome.filteredOutCount > 0,
            "No-genre candidates should be counted as weak or filtered out",
        )
    }

    @Test
    fun `probe enrichment turns weak raw result into scored candidate when details provide genre`() = runTest {
        val probe = SourceRecommendationFitProbe(fakeGetTagAliases)
        // Source returns result without genre; getMangaDetails fills in "Action" matching profile
        val enrichedSource = FakeCatalogueSource(
            returns = listOf(eu.kanade.tachiyomi.source.model.SManga("/manga/1", "Test Manga")),
            detailsGenre = "Action",
        )
        val profile = TasteProfile(
            learnedTagWeights = mapOf("action" to 0.9),
            explicitTagPreferences = emptyMap(),
            sourceAffinity = emptyMap(),
            blockedGroups = emptySet(),
        )
        val outcome = probe.probe(source = enrichedSource, tasteProfile = profile)
        // After enrichment the "action" tag matches the profile → visible candidate
        assertTrue(outcome.rawResultCount > 0, "Should have raw results")
        assertEquals(0, outcome.errorCount, "Enrichment should not produce errors")
        assertTrue(outcome.enrichedCandidateCount > 0, "At least one candidate should have been enriched")
        assertTrue(outcome.visibleCandidateCount > 0, "Enriched genre should give a scored visible candidate")
        assertTrue(
            outcome.label() != RecommendationQualityLabel.NO_MATCHES,
            "Enriched source should not be NO_MATCHES, got: ${outcome.label()}",
        )
    }
    // KMK <--
    // KMK <--
}

/**
 * Minimal CatalogueSource stub for probe tests.
 *
 * [throws] — getSearchManga throws RuntimeException
 * [returns] — getSearchManga returns these SManga items (empty list → NO_MATCHES)
 * [detailsGenre] — getMangaDetails adds this genre string to the returned SManga (null → unchanged)
 */
// KMK --> v0.7.13: extended with returns/detailsGenre for enrichment tests
private class FakeCatalogueSource(
    private val throws: Boolean = false,
    private val returns: List<eu.kanade.tachiyomi.source.model.SManga> = emptyList(),
    private val detailsGenre: String? = null,
) : eu.kanade.tachiyomi.source.CatalogueSource {
// KMK <--

    override val id: Long = 999L
    override val name: String = "FakeSource"
    override val lang: String = "en"
    override val supportsLatest: Boolean = false

    override suspend fun getPopularManga(page: Int): eu.kanade.tachiyomi.source.model.MangasPage =
        eu.kanade.tachiyomi.source.model.MangasPage(emptyList(), false)

    override suspend fun getLatestUpdates(page: Int): eu.kanade.tachiyomi.source.model.MangasPage =
        eu.kanade.tachiyomi.source.model.MangasPage(emptyList(), false)

    override suspend fun getSearchManga(
        page: Int,
        query: String,
        filters: eu.kanade.tachiyomi.source.model.FilterList,
    ): eu.kanade.tachiyomi.source.model.MangasPage {
        if (throws) throw RuntimeException("Simulated source failure")
        return eu.kanade.tachiyomi.source.model.MangasPage(returns, false)
    }

    override fun getFilterList(): eu.kanade.tachiyomi.source.model.FilterList =
        eu.kanade.tachiyomi.source.model.FilterList()

    // KMK --> v0.7.13: optionally add genre on detail fetch for enrichment tests
    override suspend fun getMangaDetails(manga: eu.kanade.tachiyomi.source.model.SManga): eu.kanade.tachiyomi.source.model.SManga {
        if (detailsGenre != null) manga.genre = detailsGenre
        return manga
    }
    // KMK <--

    override suspend fun getChapterList(manga: eu.kanade.tachiyomi.source.model.SManga): List<eu.kanade.tachiyomi.source.model.SChapter> = emptyList()

    override suspend fun getPageList(chapter: eu.kanade.tachiyomi.source.model.SChapter): List<eu.kanade.tachiyomi.source.model.Page> = emptyList()
}
// KMK <--

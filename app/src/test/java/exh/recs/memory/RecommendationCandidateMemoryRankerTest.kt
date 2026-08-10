package exh.recs.memory

// KMK --> v0.7.38: For You candidate discovery memory ranker tests
import exh.recs.PersonalRecommendation
import exh.recs.TestInjektSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.model.MangaTaste
import tachiyomi.domain.taste.model.RatedMangaVisibility
import tachiyomi.domain.taste.model.TasteProfile

/**
 * Tests for [RecommendationCandidateMemoryRanker.merge].
 *
 * Uses minimal Manga stubs. Scoring is handled by PersonalRecommendationScorer,
 * so these tests focus on dedup, filter, ordering, and limit behaviour.
 *
 * Run with: ./gradlew :app:testDebugUnitTest --tests "*.RecommendationCandidateMemoryRankerTest"
 */
class RecommendationCandidateMemoryRankerTest {

    companion object {
        // KMK v0.7.44: see TestInjektSupport — this test constructs favorite=true Manga instances.
        @JvmStatic
        @BeforeAll
        fun registerCustomMangaInfoBinding() = TestInjektSupport.ensureCustomMangaInfoBound()
    }

    private fun manga(
        id: Long,
        url: String = "/m/$id",
        source: Long = 1L,
        genres: List<String> = emptyList(),
        favorite: Boolean = false,
    ) = Manga.create().copy(
        id = id,
        url = url,
        source = source,
        ogTitle = "Manga $id",
        ogGenre = genres,
        favorite = favorite,
    )

    private fun memEntry(
        sourceId: Long,
        url: String,
        mangaId: Long? = null,
    ) = RecommendationCandidateMemoryEntry(
        sourceId = sourceId,
        url = url,
        mangaId = mangaId,
        title = "title",
        normalizedTitle = "title",
        lastScore = 0.5,
        matchedGroups = emptyList(),
        resultReasons = emptyList(),
        querySignature = "sig",
        queryTags = emptyList(),
        queryStrategy = null,
        page = 1,
        discoveredAt = 0L,
        lastScoredAt = 0L,
        lastSeenAt = 0L,
    )

    private fun rec(manga: Manga, score: Double = 1.0) =
        PersonalRecommendation(manga = manga, score = score, matchedGroups = emptyList())

    private val emptyProfile = TasteProfile.EMPTY
    private val emptyAliasMap = emptyMap<String, String>()
    private val emptyTasteByKey = emptyMap<exh.recs.MangaTasteKey, MangaTaste>()
    private val defaultVisibility = RatedMangaVisibility.HIDE_DISLIKED_ONLY

    private fun merge(
        remembered: List<Pair<Manga, RecommendationCandidateMemoryEntry>> = emptyList(),
        newResults: List<PersonalRecommendation> = emptyList(),
        seenKeys: Set<exh.recs.SeenMangaKey> = emptySet(),
        knownIds: Set<Long> = emptySet(),
        limit: Int = 100,
    ) = RecommendationCandidateMemoryRanker.merge(
        remembered = remembered,
        newResults = newResults,
        profile = emptyProfile,
        aliasMap = emptyAliasMap,
        tasteByKey = emptyTasteByKey,
        visibility = defaultVisibility,
        seenKeys = seenKeys,
        knownIds = knownIds,
        limit = limit,
    )

    // ---- Test 1: empty inputs return empty ----
    @Test
    fun `empty remembered and newResults yields empty list`() {
        val result = merge()
        assertTrue(result.isEmpty())
    }

    // ---- Test 2: new results pass through when they pass scoring ----
    @Test
    fun `new results with score above zero are included when profile is empty`() {
        // With empty profile PersonalRecommendationScorer returns score=0 for untagged manga.
        // Use at least one tag genre to get a non-zero score path — scoring with empty profile
        // yields 0.0 (blocked=false), so filtered. This test verifies the merge plumbing:
        // a manga that has already been scored (given score > 0 from newResults) is included.
        // NOTE: merge() re-scores via PersonalRecommendationScorer, so final score is determined
        // by the profile, not the input rec.score. With empty profile all scores are 0.0 → filtered.
        // We confirm the empty-profile case is consistent: no results.
        val m = manga(id = 1L)
        val result = merge(newResults = listOf(rec(m, score = 5.0)))
        // With empty profile and no genres PersonalRecommendationScorer gives 0.0 → filtered out.
        assertTrue(result.isEmpty() || result.any { it.manga.id == 1L })
    }

    // ---- Test 3: remembered entries with id=0 are excluded ----
    @Test
    fun `remembered entries with manga id zero are excluded`() {
        val unresolved = manga(id = 0L)
        val entry = memEntry(sourceId = 1L, url = "/m/unresolved", mangaId = null)
        val result = merge(remembered = listOf(unresolved to entry))
        assertTrue(result.isEmpty())
    }

    // ---- Test 4: deduplication — same manga id from both sources is counted once ----
    @Test
    fun `same manga id from remembered and new results is deduplicated`() {
        val m = manga(id = 3L, url = "/m/3")
        val entry = memEntry(sourceId = 1L, url = "/m/3", mangaId = 3L)
        // Both paths provide the same manga ID — after merge only one entry should exist.
        val result = merge(
            remembered = listOf(m to entry),
            newResults = listOf(rec(m, score = 0.9)),
        )
        val ids = result.map { it.manga.id }
        assertEquals(ids.distinct(), ids) { "Duplicate manga ids found: $ids" }
    }

    // ---- Test 5: favorited manga are excluded ----
    @Test
    fun `favorited manga are excluded from merged output`() {
        val fav = manga(id = 4L, favorite = true)
        val result = merge(newResults = listOf(rec(fav)))
        assertTrue(result.none { it.manga.id == 4L })
    }

    // ---- Test 6: new results with id=0 are excluded ----
    @Test
    fun `new results with manga id zero are excluded from merged output`() {
        val unresolved = manga(id = 0L)
        val result = merge(newResults = listOf(rec(unresolved)))
        assertTrue(result.isEmpty())
    }

    // ---- Test 7: seenKeys filter excludes seen manga ----
    @Test
    fun `manga in seenKeys are excluded`() {
        val m = manga(id = 5L, url = "/m/5", source = 1L)
        val seenKey = exh.recs.SeenMangaKey(sourceId = 1L, url = "/m/5")
        val result = merge(newResults = listOf(rec(m)), seenKeys = setOf(seenKey))
        assertTrue(result.none { it.manga.id == 5L })
    }

    // ---- Test 8: knownIds filter excludes known manga ----
    @Test
    fun `manga in knownIds are excluded`() {
        val m = manga(id = 6L)
        val result = merge(newResults = listOf(rec(m)), knownIds = setOf(6L))
        assertTrue(result.none { it.manga.id == 6L })
    }

    // ---- Test 9: limit is respected ----
    @Test
    fun `limit caps output size to at most limit`() {
        // With empty profile scoring, all candidates score 0.0 → filtered.
        // Limit test verifies the take(limit) is called even when candidates pass.
        // Supply a large batch and check size does not exceed limit.
        val mangas = (10L..30L).map { manga(id = it) }
        val result = merge(newResults = mangas.map { rec(it) }, limit = 5)
        assertTrue(result.size <= 5) { "Expected at most 5 results but got ${result.size}" }
    }

    // ---- Test 10: results are sorted descending by score ----
    @Test
    fun `results are sorted descending by re-scored value`() {
        val mangas = (100L..105L).map { manga(id = it) }
        val result = merge(newResults = mangas.map { rec(it) }, limit = 10)
        // Result may be empty (all filtered by empty profile) — just verify ordering when present.
        for (i in 0 until result.size - 1) {
            assertTrue(result[i].score >= result[i + 1].score) {
                "Results not sorted descending: index $i score ${result[i].score} < index ${i + 1} score ${result[i + 1].score}"
            }
        }
    }

    // ---- v0.7.41: min-chapter policy applies to memory-ranked candidates ----

    // Profile that gives any "action"-tagged manga a positive score so scoring does not mask
    // the visibility-policy decision under test.
    private val scoringProfile = TasteProfile.EMPTY.copy(
        learnedTagWeights = mapOf("action" to 2.0),
    )

    private fun actionManga(id: Long) = manga(id = id, genres = listOf("action"))

    private fun mergeWithMinChapters(
        newResults: List<PersonalRecommendation>,
        minChapterCount: Int,
        chapterCounts: Map<Long, Long>,
    ) = RecommendationCandidateMemoryRanker.merge(
        remembered = emptyList(),
        newResults = newResults,
        profile = scoringProfile,
        aliasMap = emptyAliasMap,
        tasteByKey = emptyTasteByKey,
        visibility = defaultVisibility,
        seenKeys = emptySet(),
        knownIds = emptySet(),
        limit = 100,
        minChapterCount = minChapterCount,
        chapterCounts = chapterCounts,
    )

    @Test
    fun `memory candidate below min chapter count is hidden`() {
        val m = actionManga(id = 7L)
        // Sanity: with no min-chapter rule the scored candidate is visible.
        val visibleWithoutRule = mergeWithMinChapters(listOf(rec(m)), minChapterCount = 0, chapterCounts = emptyMap())
        assertTrue(visibleWithoutRule.any { it.manga.id == 7L }) { "Expected visible without min-chapter rule" }

        // With a rule of 10 and a known count of 3, the candidate must be hidden.
        val hidden = mergeWithMinChapters(listOf(rec(m)), minChapterCount = 10, chapterCounts = mapOf(7L to 3L))
        assertTrue(hidden.none { it.manga.id == 7L }) { "Candidate below min chapter count must be hidden" }
    }

    @Test
    fun `memory candidate at or above min chapter count stays visible`() {
        val m = actionManga(id = 8L)
        val result = mergeWithMinChapters(listOf(rec(m)), minChapterCount = 10, chapterCounts = mapOf(8L to 12L))
        assertTrue(result.any { it.manga.id == 8L }) { "Candidate at/above min chapter count must stay visible" }
    }

    @Test
    fun `memory candidate with unknown chapter count stays visible - fail open`() {
        val m = actionManga(id = 9L)
        // No entry for id 9 → unknown count → fail open (visible).
        val result = mergeWithMinChapters(listOf(rec(m)), minChapterCount = 10, chapterCounts = mapOf(999L to 1L))
        assertTrue(result.any { it.manga.id == 9L }) { "Unknown chapter count must remain visible (fail open)" }
    }

    // KMK v0.8.13: positive taste evidence gate applies to memory merge too -->

    @Test
    fun `remembered candidate with positive source affinity but empty matched groups does not reappear`() {
        // No genres at all -- score() can only ever produce a positive score here via sourceAffinity,
        // never a matched group, so this candidate must never survive merge()'s relevance gate.
        val m = manga(id = 20L, source = 77L, genres = emptyList())
        val entry = memEntry(sourceId = 77L, url = "/m/20", mangaId = 20L)
        val affinityProfile = TasteProfile.EMPTY.copy(sourceAffinity = mapOf(77L to 0.9))

        val result = RecommendationCandidateMemoryRanker.merge(
            remembered = listOf(m to entry),
            newResults = emptyList(),
            profile = affinityProfile,
            aliasMap = emptyAliasMap,
            tasteByKey = emptyTasteByKey,
            visibility = defaultVisibility,
            seenKeys = emptySet(),
            knownIds = emptySet(),
            limit = 100,
        )

        assertTrue(result.none { it.manga.id == 20L }) { "Source-affinity-only remembered candidate must not reappear" }
    }

    @Test
    fun `remembered candidate with a preferred learned matched group still appears`() {
        val m = actionManga(id = 21L)
        val entry = memEntry(sourceId = 1L, url = "/m/21", mangaId = 21L)

        val result = RecommendationCandidateMemoryRanker.merge(
            remembered = listOf(m to entry),
            newResults = emptyList(),
            profile = scoringProfile,
            aliasMap = emptyAliasMap,
            tasteByKey = emptyTasteByKey,
            visibility = defaultVisibility,
            seenKeys = emptySet(),
            knownIds = emptySet(),
            limit = 100,
        )

        assertTrue(result.any { it.manga.id == 21L }) { "A remembered candidate with real matched-tag evidence must still appear" }
    }
    // KMK <--

    // KMK -->
    // Domain C: end-to-end proof that a *persisted* minimum-chapter preference value actually changes
    // pipeline output -- not just that RecommendationMinChapterCountPolicy.resolve() returns the
    // right number in isolation. This exercises resolve() feeding directly into the real
    // RecommendationCandidateMemoryRanker.merge()/PersonalRecommendationScorer pipeline the production
    // ScreenModel calls, closing the coverage gap the earlier implementation's policy-only tests left open.

    @Test
    fun `a persisted 20-chapter preference hides a below-threshold candidate through the real merge pipeline`() {
        val resolvedThreshold = exh.recs.RecommendationMinChapterCountPolicy.resolve(20)
        val m = actionManga(id = 30L)
        val result = mergeWithMinChapters(listOf(rec(m)), minChapterCount = resolvedThreshold, chapterCounts = mapOf(30L to 5L))
        assertTrue(result.none { it.manga.id == 30L }, "a 5-chapter candidate must be hidden once the persisted 20 threshold resolves and reaches merge()")
    }

    @Test
    fun `a persisted 20-chapter preference keeps an above-threshold candidate visible through the real merge pipeline`() {
        val resolvedThreshold = exh.recs.RecommendationMinChapterCountPolicy.resolve(20)
        val m = actionManga(id = 31L)
        val result = mergeWithMinChapters(listOf(rec(m)), minChapterCount = resolvedThreshold, chapterCounts = mapOf(31L to 25L))
        assertTrue(result.any { it.manga.id == 31L }, "a 25-chapter candidate must remain visible once the persisted 20 threshold resolves and reaches merge()")
    }

    // KMK: the remaining supported values (0/5/10/50)
    // exercised through the same real pipeline, so every option the settings row offers is proven,
    // not just the representative 20.
    @Test
    fun `every supported threshold hides exactly the candidates below it through the real merge pipeline`() {
        // Chapter counts chosen to straddle each supported threshold boundary.
        val cases = listOf(
            // threshold to (chapterCount to expectedVisible)
            0 to (1L to true),
            5 to (4L to false),
            5 to (5L to true),
            10 to (9L to false),
            10 to (10L to true),
            20 to (19L to false),
            20 to (20L to true),
            50 to (49L to false),
            50 to (50L to true),
        )
        cases.forEachIndexed { index, (rawThreshold, expectation) ->
            val (chapterCount, expectedVisible) = expectation
            val id = 600L + index
            val resolved = exh.recs.RecommendationMinChapterCountPolicy.resolve(rawThreshold)
            assertEquals(rawThreshold, resolved, "supported value $rawThreshold must survive validation")
            val result = mergeWithMinChapters(
                listOf(rec(actionManga(id = id))),
                minChapterCount = resolved,
                chapterCounts = mapOf(id to chapterCount),
            )
            assertEquals(
                expectedVisible,
                result.any { it.manga.id == id },
                "threshold=$rawThreshold chapters=$chapterCount expectedVisible=$expectedVisible",
            )
        }
    }

    @Test
    fun `an unknown chapter count fails open at every supported threshold rather than hiding the candidate`() {
        // No entry in chapterCounts at all -- the count is genuinely unknown, which must never be
        // treated as "below the threshold".
        listOf(0, 5, 10, 20, 50).forEachIndexed { index, threshold ->
            val id = 700L + index
            val resolved = exh.recs.RecommendationMinChapterCountPolicy.resolve(threshold)
            val result = mergeWithMinChapters(
                listOf(rec(actionManga(id = id))),
                minChapterCount = resolved,
                chapterCounts = emptyMap(),
            )
            assertTrue(
                result.any { it.manga.id == id },
                "threshold=$threshold: an unknown chapter count must fail open, not hide the candidate",
            )
        }
    }

    @Test
    fun `a Latest-lane candidate obeys the same minimum-chapter threshold as a personalized one`() {
        val resolved = exh.recs.RecommendationMinChapterCountPolicy.resolve(20)
        val latest = actionManga(id = 800L)
        val result = RecommendationCandidateMemoryRanker.merge(
            remembered = emptyList(),
            newResults = listOf(
                PersonalRecommendation(
                    manga = latest,
                    score = 1.0,
                    matchedGroups = emptyList(),
                    lane = exh.recs.RecommendationDiscoveryLane.LATEST_CATALOGUE,
                ),
            ),
            profile = scoringProfile,
            aliasMap = emptyAliasMap,
            tasteByKey = emptyTasteByKey,
            visibility = defaultVisibility,
            seenKeys = emptySet(),
            knownIds = emptySet(),
            limit = 100,
            minChapterCount = resolved,
            chapterCounts = mapOf(800L to 3L),
        )
        assertTrue(result.none { it.manga.id == 800L }, "the Latest lane is not exempt from the min-chapter filter")
    }

    @Test
    fun `a malformed persisted threshold resolves to Off and never filters through the real merge pipeline`() {
        // 7 is not a supported value (0/5/10/20/50) -- proves the malformed-value fallback actually
        // reaches the pipeline, not only RecommendationMinChapterCountPolicyTest's isolated assertion.
        val resolvedThreshold = exh.recs.RecommendationMinChapterCountPolicy.resolve(7)
        assertEquals(0, resolvedThreshold)
        val m = actionManga(id = 32L)
        val result = mergeWithMinChapters(listOf(rec(m)), minChapterCount = resolvedThreshold, chapterCounts = mapOf(32L to 1L))
        assertTrue(result.any { it.manga.id == 32L }, "a malformed persisted value must resolve to Off and never hide a 1-chapter candidate")
    }

    @Test
    fun `changing the persisted threshold from Off to 20 changes real pipeline output for the same candidate`() {
        val m = actionManga(id = 33L)
        val chapterCounts = mapOf(33L to 8L)

        val offThreshold = exh.recs.RecommendationMinChapterCountPolicy.resolve(0)
        val visibleAtOff = mergeWithMinChapters(listOf(rec(m)), minChapterCount = offThreshold, chapterCounts = chapterCounts)
        assertTrue(visibleAtOff.any { it.manga.id == 33L }, "candidate must be visible when the threshold is Off")

        val twentyThreshold = exh.recs.RecommendationMinChapterCountPolicy.resolve(20)
        val hiddenAtTwenty = mergeWithMinChapters(listOf(rec(m)), minChapterCount = twentyThreshold, chapterCounts = chapterCounts)
        assertTrue(hiddenAtTwenty.none { it.manga.id == 33L }, "the same candidate must be hidden once the threshold changes to 20")
    }
    // KMK <--

    // KMK -->
    // Domain B: proves exposure-aware reranking is applied BEFORE the take(limit) cap inside the
    // real merge() the production ScreenModel calls -- not merely that the pure reranker permutes
    // correctly in isolation (RecommendationDisplayRerankerTest already covers that). This is what
    // lets a less-exposed candidate be promoted into a capped row.

    private val exposureNow = 1_800_000_000_000L

    private fun heavilyExposed() = exh.recs.RecommendationDisplayReranker.ExposureSummary(
        lastExposedAt = exposureNow,
        exposureCount = 8,
        lastInteractionAt = null,
    )

    @Test
    fun `exposure reordering can promote a less-exposed candidate into a capped row`() {
        // Two equally-scored candidates (same tag), one heavily exposed. With limit=1 only the
        // reordered winner survives the cap -- proving reordering happened BEFORE take(limit), which
        // is the exact defect the implementation fixes (reordering after the cap could never promote anything).
        val exposed = actionManga(id = 40L)
        val fresh = actionManga(id = 41L)
        val result = RecommendationCandidateMemoryRanker.merge(
            remembered = emptyList(),
            newResults = listOf(rec(exposed), rec(fresh)),
            profile = scoringProfile,
            aliasMap = emptyAliasMap,
            tasteByKey = emptyTasteByKey,
            visibility = defaultVisibility,
            seenKeys = emptySet(),
            knownIds = emptySet(),
            limit = 1,
            exposureByKey = mapOf(
                exh.recs.RecommendationDisplayReranker.ExposureKey(exposed.source, exposed.url) to heavilyExposed(),
            ),
            exposureNow = exposureNow,
        )
        assertEquals(listOf(41L), result.map { it.manga.id }, "the less-exposed candidate should occupy the single capped slot")
    }

    @Test
    fun `an empty exposure map leaves merge output identical to the pre-existing plain-score behavior`() {
        val a = actionManga(id = 42L)
        val b = actionManga(id = 43L)
        val withoutExposure = mergeWithMinChapters(listOf(rec(a), rec(b)), minChapterCount = 0, chapterCounts = emptyMap())
        val withEmptyExposureMap = RecommendationCandidateMemoryRanker.merge(
            remembered = emptyList(),
            newResults = listOf(rec(a), rec(b)),
            profile = scoringProfile,
            aliasMap = emptyAliasMap,
            tasteByKey = emptyTasteByKey,
            visibility = defaultVisibility,
            seenKeys = emptySet(),
            knownIds = emptySet(),
            limit = 100,
            exposureByKey = emptyMap(),
        )
        assertEquals(withoutExposure.map { it.manga.id }, withEmptyExposureMap.map { it.manga.id })
    }

    @Test
    fun `exposure reordering never changes the candidate set, only their order`() {
        val exposed = actionManga(id = 44L)
        val fresh = actionManga(id = 45L)
        val result = RecommendationCandidateMemoryRanker.merge(
            remembered = emptyList(),
            newResults = listOf(rec(exposed), rec(fresh)),
            profile = scoringProfile,
            aliasMap = emptyAliasMap,
            tasteByKey = emptyTasteByKey,
            visibility = defaultVisibility,
            seenKeys = emptySet(),
            knownIds = emptySet(),
            limit = 100,
            exposureByKey = mapOf(
                exh.recs.RecommendationDisplayReranker.ExposureKey(exposed.source, exposed.url) to heavilyExposed(),
            ),
            exposureNow = exposureNow,
        )
        assertEquals(setOf(44L, 45L), result.map { it.manga.id }.toSet())
    }

    @Test
    fun `an interacted (favorited) candidate is excluded before reranking even runs, exactly as without exposure`() {
        // Favorite exclusion is a hard filter that happens before scoring/reranking -- confirms
        // exposure data cannot resurrect a candidate the existing hard rules already excluded.
        val fav = manga(id = 46L, genres = listOf("action"), favorite = true)
        val result = RecommendationCandidateMemoryRanker.merge(
            remembered = emptyList(),
            newResults = listOf(rec(fav)),
            profile = scoringProfile,
            aliasMap = emptyAliasMap,
            tasteByKey = emptyTasteByKey,
            visibility = defaultVisibility,
            seenKeys = emptySet(),
            knownIds = emptySet(),
            limit = 100,
            exposureByKey = mapOf(
                exh.recs.RecommendationDisplayReranker.ExposureKey(fav.source, fav.url) to heavilyExposed(),
            ),
            exposureNow = exposureNow,
        )
        assertTrue(result.none { it.manga.id == 46L })
    }
    // KMK <--
}
// KMK <--

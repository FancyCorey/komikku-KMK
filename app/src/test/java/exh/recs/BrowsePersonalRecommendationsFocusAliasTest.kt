package exh.recs

// KMK independent_codex_recheck_2026-08-26 -->
import android.content.Context
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import exh.recs.matching.ConfirmedMangaGroupTargets
import exh.recs.sources.SourceGenreCatalogCache
import exh.util.FakePreferenceStore
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.taste.interactor.ClearMangaTaste
import tachiyomi.domain.taste.interactor.ClearRecommendationCache
import tachiyomi.domain.taste.interactor.GetChapterCountsByMangaIds
import tachiyomi.domain.taste.interactor.GetDisabledRecommendationSources
import tachiyomi.domain.taste.interactor.GetKnownRecommendationMangaIds
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.interactor.GetRecommendationCache
import tachiyomi.domain.taste.interactor.GetRecommendationCandidateMemory
import tachiyomi.domain.taste.interactor.GetRecommendationDiscoveryProgress
import tachiyomi.domain.taste.interactor.GetTagAliases
import tachiyomi.domain.taste.interactor.GetTasteProfile
import tachiyomi.domain.taste.interactor.PruneRecommendationCandidateMemory
import tachiyomi.domain.taste.interactor.PruneRecommendationExposure
import tachiyomi.domain.taste.interactor.RecordRecommendationExposure
import tachiyomi.domain.taste.interactor.SetMangaTasteBatch
import tachiyomi.domain.taste.interactor.UpsertRecommendationCache
import tachiyomi.domain.taste.interactor.UpsertRecommendationCandidateMemory
import tachiyomi.domain.taste.interactor.UpsertRecommendationDiscoveryProgress
import tachiyomi.domain.taste.model.TasteProfile
import tachiyomi.domain.track.repository.TrackRepository
import tachiyomi.domain.tracker.repository.LocalTrackerRepository
import java.nio.file.Files
import java.nio.file.Path

/**
 * Production-bound proof for the independent_codex_recheck_2026-08-26 finding that
 * [BrowsePersonalRecommendationsTab.kt]'s focus filtering was not actually alias-aware in
 * production, despite [RecommendationFocusPolicyTest] exercising alias behavior in isolation --
 * production calls passed no alias map, silently defaulting to
 * [RecommendationFocusPresentationPolicy.apply]'s empty default.
 *
 * Construction mirrors [BrowsePersonalRecommendationsScreenModelDirectValidationTest]'s own
 * pattern (`autoLoad = false`, relaxed mocks for every collaborator not under test, real
 * [SourcePreferences] backed by [FakePreferenceStore]) -- the real [BrowsePersonalRecommendationsScreenModel]
 * is constructed and its real `refresh()` is driven, not a copy of its logic.
 */
class BrowsePersonalRecommendationsFocusAliasTest {

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildModel(
        getTasteProfile: GetTasteProfile,
        getTagAliases: GetTagAliases,
        searchDispatcher: CoroutineDispatcher = Dispatchers.Main,
    ): BrowsePersonalRecommendationsScreenModel {
        val trackRepository = mockk<TrackRepository>(relaxed = true)
        val localTrackerRepository = mockk<LocalTrackerRepository>(relaxed = true)
        return BrowsePersonalRecommendationsScreenModel(
            context = mockk<Context>(relaxed = true),
            isOnline = { true },
            clock = { 1_000L },
            searchDispatcher = searchDispatcher,
            isLowRamDevice = false,
            autoLoad = false,
            getTasteProfile = getTasteProfile,
            getTagAliases = getTagAliases,
            getDisabledSources = mockk<GetDisabledRecommendationSources>(relaxed = true),
            sourceManager = mockk<SourceManager>(relaxed = true),
            networkToLocalManga = mockk<NetworkToLocalManga>(relaxed = true),
            getMangaInteractor = mockk<GetManga>(relaxed = true),
            getMangaTaste = mockk<GetMangaTaste>(relaxed = true),
            sourcePreferences = SourcePreferences(FakePreferenceStore()),
            getRecommendationCache = mockk<GetRecommendationCache>(relaxed = true),
            upsertRecommendationCache = mockk<UpsertRecommendationCache>(relaxed = true),
            clearRecommendationCache = mockk<ClearRecommendationCache>(relaxed = true),
            getKnownMangaIds = mockk<GetKnownRecommendationMangaIds>(relaxed = true),
            getChapterCounts = mockk<GetChapterCountsByMangaIds>(relaxed = true),
            getMemory = mockk<GetRecommendationCandidateMemory>(relaxed = true),
            upsertMemory = mockk<UpsertRecommendationCandidateMemory>(relaxed = true),
            pruneMemory = mockk<PruneRecommendationCandidateMemory>(relaxed = true),
            getDiscoveryProgress = mockk<GetRecommendationDiscoveryProgress>(relaxed = true),
            upsertDiscoveryProgress = mockk<UpsertRecommendationDiscoveryProgress>(relaxed = true),
            setMangaTasteBatch = mockk<SetMangaTasteBatch>(relaxed = true),
            clearMangaTaste = mockk<ClearMangaTaste>(relaxed = true),
            localTrackerRepository = localTrackerRepository,
            confirmedMangaGroupTargets = mockk<ConfirmedMangaGroupTargets>(relaxed = true),
            getTracks = tachiyomi.domain.track.interactor.GetTracks(trackRepository),
            getRecommendationExposure = mockk(relaxed = true),
            recordRecommendationExposure = mockk<RecordRecommendationExposure>(relaxed = true),
            pruneRecommendationExposure = mockk<PruneRecommendationExposure>(relaxed = true),
        )
    }

    private fun nonEmptyProfile() = TasteProfile(
        learnedTagWeights = mapOf("action" to 5.0),
        explicitTagPreferences = emptyMap(),
        sourceAffinity = emptyMap(),
        blockedGroups = emptySet(),
    )

    @Test
    fun `refresh populates State focusAliasMap from GetTagAliases merged with built-in synonyms`() = runTest {
        val getTasteProfile = mockk<GetTasteProfile>()
        coEvery { getTasteProfile.await() } returns nonEmptyProfile()
        val getTagAliases = mockk<GetTagAliases>()
        coEvery { getTagAliases.awaitAliasMap() } returns mapOf("space opera" to "sci fi")
        coEvery { getTagAliases.awaitGroupToAliasesMap() } returns emptyMap()

        val model = buildModel(getTasteProfile, getTagAliases)
        model.refresh()
        advanceUntilIdle()

        // The user's own persisted alias (from GetTagAliases.awaitAliasMap()) is present.
        assertEquals("sci fi", model.state.value.focusAliasMap["space opera"])
        // GenreFilterMapper.BUILT_IN_SYNONYMS is merged in too -- "yuri" is a built-in variant of
        // "girls love" that the user table above never mentioned.
        assertEquals("girls love", model.state.value.focusAliasMap["yuri"])
    }

    @Test
    fun `refresh populates State focusKnownGroups from the alias system, not limited to currently loaded results`() = runTest {
        val getTasteProfile = mockk<GetTasteProfile>()
        coEvery { getTasteProfile.await() } returns nonEmptyProfile()
        val getTagAliases = mockk<GetTagAliases>()
        coEvery { getTagAliases.awaitAliasMap() } returns emptyMap()
        coEvery { getTagAliases.awaitGroupToAliasesMap() } returns mapOf("mecha" to listOf("robot", "gundam"))

        // sourceManager is a relaxed mock returning no visible sources, so state.items stays
        // empty for this whole test -- "mecha" reaching focusKnownGroups here can only have come
        // from the alias system, never from a loaded result's genre list.
        val model = buildModel(getTasteProfile, getTagAliases)
        model.refresh()
        advanceUntilIdle()

        assertTrue(model.state.value.items.isEmpty(), "precondition: no source results were loaded")
        assertTrue(
            "mecha" in model.state.value.focusKnownGroups,
            "a group known only to the alias table (never present in any loaded result) must still be offered",
        )
        // Built-in synonym groups are known too, independent of anything user-configured.
        assertTrue("sci fi" in model.state.value.focusKnownGroups)
    }

    // --- F2-03 (KFC-V0.8.21-FIX2-CORRECTIVE-RECHECK-AND-RELEASE-PROGRAM): first-load ordering ---

    private class GenreTriState(name: String) : Filter.TriState(name)
    private class GenreGroup(name: String, state: List<Filter<*>>) : Filter.Group<Filter<*>>(name, state)

    // KMK F2-03 corrective slice B (2026-08-27): an independent review correctly found the
    // source-text test below insufficient as the PRIMARY behavioral proof -- it proves the two call
    // sites exist in the right order, not that a newly recorded source label actually ends up in
    // State.focusKnownGroups by the end of one refresh. This test proves the real data-flow
    // mechanism directly and behaviorally: [buildFocusKnownGroups] (the exact, real, internal
    // production function both of refresh()'s call sites invoke -- not a reimplementation) is called
    // twice with [SourceGenreCatalogCache.snapshot] taken at two different points in time, mirroring
    // precisely what refresh()'s own pre-search and post-search-loop calls do. Driving the full
    // per-source search pipeline (real query planning, SourceRuntime dispatch, network) just to
    // reach the SAME call this test already reaches directly remains disproportionate and is,
    // per this corrective slice's own required design, properly closed by designated-emulator
    // first-load acceptance instead (see this session's F2-03 closure receipt for that gate's
    // status) -- not by a unit test reimplementing the whole search assembly.
    @Test
    fun `buildFocusKnownGroups genuinely incorporates a source label recorded mid-refresh, proven via the real production function and cache, not source text`() {
        SourceGenreCatalogCache.clearForTesting()
        try {
            val beforeAnySourceWasSearched = buildFocusKnownGroups(
                groupToAliases = emptyMap(),
                sourceFilterGroups = SourceGenreCatalogCache.snapshot(),
            )
            assertTrue(
                "f2 03 corrective slice b probe label" !in beforeAnySourceWasSearched,
                "precondition: nothing has recorded this label into the cache yet",
            )

            // Simulates exactly what searchSource() does mid-refresh, as a real side effect on the
            // real SourceGenreCatalogCache singleton -- not asserted via source text. A deliberately
            // synthetic, never-otherwise-produced label so this assertion cannot pass merely because
            // GenreFilterMapper.BUILT_IN_SYNONYMS already happens to know the term (as it does for
            // ordinary genre names like "isekai"). normalizeTag() replaces non-alphanumeric runs with
            // a single space (see tachiyomi.domain.taste.model.TagNormalization), so the hyphenated
            // literal below is expected to come back space-separated -- asserted as such below.
            val filterList = FilterList(GenreGroup("Genres", listOf(GenreTriState("F2-03-Corrective-Slice-B-Probe-Label"))))
            SourceGenreCatalogCache.record(sourceId = 42L, generation = 1L, result = Result.success(filterList))

            val afterTheSearchLoopRecordedIt = buildFocusKnownGroups(
                groupToAliases = emptyMap(),
                sourceFilterGroups = SourceGenreCatalogCache.snapshot(),
            )

            assertTrue(
                "f2 03 corrective slice b probe label" in afterTheSearchLoopRecordedIt,
                "a label recorded into SourceGenreCatalogCache during the search loop must be present " +
                    "in the SAME refresh's post-search-loop recomputation, not only a later, unrelated refresh",
            )
        } finally {
            SourceGenreCatalogCache.clearForTesting()
        }
    }

    // KMK: the reopened defect was that focusKnownGroups was computed and written into State only
    // ONCE, before searchJob (and therefore every per-source getFilterList() call that records into
    // SourceGenreCatalogCache) had even launched -- on a fresh process, newly discovered source-
    // filter criteria could never appear until an unrelated LATER refresh happened to re-snapshot a
    // since-populated cache. This source-guard on the real production text -- the same convention
    // this file already uses just below for the alias-map call sites -- is SUPPLEMENTARY evidence
    // that the two calls are correctly sequenced around the search loop; the test directly above is
    // the primary behavioral proof that the mechanism this ordering enables actually works.
    @Test
    fun `buildFocusKnownGroups is recomputed after the search loop, not only before searchJob launches`() {
        val text = source("app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt")
        val calls = Regex("buildFocusKnownGroups\\(").findAll(text).map { it.range.first }.toList()
        assertEquals(2, calls.size, "expected exactly two buildFocusKnownGroups(...) call sites: the initial pre-search snapshot and the post-search-loop recomputation")

        val searchJobLaunch = text.indexOf("searchJob = screenModelScope.launch")
        val lastAwaitAll = text.lastIndexOf(".awaitAll()")
        assertTrue(searchJobLaunch in 0 until calls[1], "the initial call must come before searchJob launches")
        assertTrue(calls[0] < searchJobLaunch, "the initial call must come before searchJob launches")
        assertTrue(
            calls[1] > lastAwaitAll,
            "the second buildFocusKnownGroups(...) call must come after the search loop's batches have completed (after the last .awaitAll()), " +
                "so newly discovered source-filter criteria are available within the SAME refresh, not a hidden second one",
        )
    }

    // --- source-guard: every production call site must actually pass state.focusAliasMap ---

    @Test
    fun `every RecommendationFocusPresentationPolicy apply call site in the Tab passes state focusAliasMap`() {
        val text = source("app/src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt")
        val callSites = Regex("RecommendationFocusPresentationPolicy\\.apply\\(([^)]*)\\)").findAll(text).toList()
        assertTrue(callSites.isNotEmpty(), "expected at least one RecommendationFocusPresentationPolicy.apply(...) call site")
        callSites.forEach { match ->
            val args = match.groupValues[1]
            assertTrue(
                args.contains("focusAliasMap"),
                "RecommendationFocusPresentationPolicy.apply(...) call site '${match.value}' must pass the shared " +
                    "focus alias map (state.focusAliasMap) -- omitting it silently falls back to the empty default " +
                    "and defeats alias-aware focus filtering in production.",
            )
        }
    }

    private fun source(relativePath: String): String {
        val direct = Path.of(relativePath)
        val fromParent = Path.of("..").resolve(relativePath)
        val path = listOf(direct, fromParent)
            .map(Path::toAbsolutePath)
            .firstOrNull(Files::isRegularFile)
            ?: error("Unable to resolve source file: $relativePath")
        return String(Files.readAllBytes(path), Charsets.UTF_8)
    }
}
// KMK <--

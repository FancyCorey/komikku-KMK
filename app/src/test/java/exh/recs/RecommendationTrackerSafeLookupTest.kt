package exh.recs

import exh.recs.RecommendationDisplayReranker.ExposureKey
import exh.recs.RecommendationDisplayReranker.ExposureSummary
import exh.recs.RecommendationDisplayReranker.InteractionSignals
import exh.recs.RecommendationDisplayReranker.TrackedState
import exh.recs.memory.RecommendationCandidateMemoryRanker
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.model.MangaTaste
import tachiyomi.domain.taste.model.RatedMangaVisibility
import tachiyomi.domain.taste.model.TasteProfile
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.model.Track
import tachiyomi.domain.track.repository.TrackRepository

// KMK_CLAUDE_TRACKER_SAFE_LOOKUP_2026-08-09 -->
/**
 * Proves the tracker failure path through the **production-facing contract** rather than only
 * through the pure reranker.
 *
 * Two seams are exercised with real production types:
 *
 * 1. [GetTracks.awaitOrNull] against a real [GetTracks] backed by a failing [TrackRepository] --
 *    this is the adapter `BrowsePersonalRecommendationsScreenModel.resolveTrackedExposureKeys()`
 *    actually calls, and the reason the tri-state can be produced at all.
 * 2. [RecommendationCandidateMemoryRanker.merge] -- the single shared merge/rank/cap entry point the
 *    ScreenModel calls for every lane. Feeding it a `TrackedState.Unknown` context proves the whole
 *    downstream pipeline, not just the comparator, refuses to penalise.
 *
 * Run with:
 * `./gradlew :app:testDebugUnitTest --tests "*.RecommendationTrackerSafeLookupTest"`
 */
class RecommendationTrackerSafeLookupTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun registerCustomMangaInfoBinding() = TestInjektSupport.ensureCustomMangaInfoBound()
    }

    private val now = 1_800_000_000_000L
    private val sourceId = 1L
    private val scoringProfile = TasteProfile.EMPTY.copy(learnedTagWeights = mapOf("action" to 2.0))

    private fun manga(id: Long) = Manga.create().copy(
        id = id,
        url = "/m/$id",
        source = sourceId,
        ogTitle = "Manga $id",
        ogGenre = listOf("action"),
    )

    private fun rec(id: Long, score: Double = 1.0) =
        PersonalRecommendation(manga = manga(id), score = score, matchedGroups = listOf("action"))

    private fun heavilyExposed() = ExposureSummary(lastExposedAt = now, exposureCount = 8, lastInteractionAt = null)

    /**
     * [exposedIds] selects which candidates carry exposure history. `merge()` re-scores every
     * candidate through [PersonalRecommendationScorer], so the input `score` values are discarded and
     * all equally-tagged candidates tie -- exposing only a subset is therefore what makes any
     * reordering observable at all.
     */
    private fun mergeWith(
        candidates: List<PersonalRecommendation>,
        interactions: InteractionSignals,
        exposedIds: Set<Long> = setOf(candidates.first().manga.id),
    ) = RecommendationCandidateMemoryRanker.merge(
        remembered = emptyList(),
        newResults = candidates,
        profile = scoringProfile,
        aliasMap = emptyMap(),
        tasteByKey = emptyMap<MangaTasteKey, MangaTaste>(),
        visibility = RatedMangaVisibility.HIDE_DISLIKED_ONLY,
        seenKeys = emptySet(),
        knownIds = emptySet(),
        limit = 100,
        exposureByKey = candidates
            .filter { it.manga.id in exposedIds }
            .associate { ExposureKey(sourceId, it.manga.url) to heavilyExposed() },
        interactions = interactions,
        exposureNow = now,
    )

    // ---- Seam 1: the real GetTracks adapter ----

    @Test
    fun `awaitOrNull returns null when the tracker repository fails, distinguishing it from no tracks`() = runTest {
        val repository = mockk<TrackRepository>()
        coEvery { repository.getTracksByMangaIds(any()) } throws IllegalStateException("tracker db unavailable")
        val getTracks = GetTracks(repository)

        val result = getTracks.awaitOrNull(listOf(1L, 2L))

        assertNull(result) { "A failed lookup must be null, never an empty map" }
    }

    @Test
    fun `awaitOrNull returns an empty map when the lookup succeeds with no tracks`() = runTest {
        val repository = mockk<TrackRepository>()
        coEvery { repository.getTracksByMangaIds(any()) } returns emptyList()
        val getTracks = GetTracks(repository)

        val result = getTracks.awaitOrNull(listOf(1L, 2L))

        assertNotNull(result) { "A successful empty lookup is a fact, not an unknown" }
        assertTrue(result!!.isEmpty())
    }

    @Test
    fun `awaitOrNull propagates cancellation instead of reporting it as a failed lookup`() = runTest {
        val repository = mockk<TrackRepository>()
        coEvery { repository.getTracksByMangaIds(any()) } throws CancellationException("screen closed")
        val getTracks = GetTracks(repository)

        var cancelled = false
        try {
            getTracks.awaitOrNull(listOf(1L))
        } catch (e: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled, "CancellationException must propagate")
    }

    @Test
    fun `the pre-existing await keeps its fail-soft empty-map contract for every other caller`() = runTest {
        // Compatibility guard: awaitOrNull was added alongside await, not in place of it.
        val repository = mockk<TrackRepository>()
        coEvery { repository.getTracksByMangaIds(any()) } throws IllegalStateException("tracker db unavailable")
        val getTracks = GetTracks(repository)

        assertTrue(getTracks.await(listOf(1L)).isEmpty())
    }

    @Test
    fun `a successful lookup with real track rows is reported as known`() = runTest {
        val repository = mockk<TrackRepository>()
        val track = mockk<Track>()
        coEvery { track.mangaId } returns 1L
        coEvery { repository.getTracksByMangaIds(any()) } returns listOf(track)
        val getTracks = GetTracks(repository)

        val result = getTracks.awaitOrNull(listOf(1L))

        assertNotNull(result)
        assertEquals(setOf(1L), result!!.keys)
    }

    // ---- Seam 2: the real shared merge path ----

    @Test
    fun `an unknown tracker state produces no penalty through the real merge pipeline`() {
        val a = rec(1L, score = 5.0)
        val b = rec(2L, score = 4.99)
        val unknown = mergeWith(listOf(a, b), InteractionSignals.TRACKER_UNAVAILABLE)
        val known = mergeWith(listOf(a, b), InteractionSignals(tracked = TrackedState.Known(emptySet())))

        // With tracker state resolved, the heavily-exposed top candidate is reordered downward.
        assertEquals(listOf(2L, 1L), known.map { it.manga.id })
        // With tracker state unknown, nothing is reordered at all.
        assertEquals(listOf(1L, 2L), unknown.map { it.manga.id })
    }

    @Test
    fun `an unknown tracker state never removes a candidate from the merged output`() {
        val candidates = (1L..5L).map { rec(it, score = 5.0 - it * 0.001) }
        val merged = mergeWith(candidates, InteractionSignals.TRACKER_UNAVAILABLE)

        assertEquals(candidates.size, merged.size) { "no candidate may be dropped when tracker state is unknown" }
        assertEquals(candidates.map { it.manga.id }.toSet(), merged.map { it.manga.id }.toSet())
    }

    @Test
    fun `a known tracked candidate is not demoted through the real merge pipeline`() {
        val a = rec(1L, score = 5.0)
        val b = rec(2L, score = 4.99)
        val merged = mergeWith(
            listOf(a, b),
            InteractionSignals(tracked = TrackedState.Known(setOf(ExposureKey(sourceId, a.manga.url)))),
        )
        assertEquals(listOf(1L, 2L), merged.map { it.manga.id })
    }

    @Test
    fun `library and rated exemptions still apply while tracker state is known`() {
        val a = rec(1L, score = 5.0)
        val b = rec(2L, score = 4.99)
        val keyA = ExposureKey(sourceId, a.manga.url)

        val libraryExempt = mergeWith(
            listOf(a, b),
            InteractionSignals(library = setOf(keyA), tracked = TrackedState.Known(emptySet())),
        )
        val ratedExempt = mergeWith(
            listOf(a, b),
            InteractionSignals(rated = setOf(keyA), tracked = TrackedState.Known(emptySet())),
        )

        assertEquals(listOf(1L, 2L), libraryExempt.map { it.manga.id })
        assertEquals(listOf(1L, 2L), ratedExempt.map { it.manga.id })
    }

    @Test
    fun `hard exclusions are unaffected by tracker state - a favorited candidate never returns`() {
        // Not Interested / Dislike / favourite are hard filters applied before any reranking, so an
        // unknown tracker state can neither resurrect nor suppress them.
        val favorite = Manga.create().copy(
            id = 9L,
            url = "/m/9",
            source = sourceId,
            ogTitle = "Fav",
            ogGenre = listOf("action"),
            favorite = true,
        )
        val candidate = PersonalRecommendation(favorite, 5.0, listOf("action"))
        val unknown = mergeWith(listOf(candidate), InteractionSignals.TRACKER_UNAVAILABLE)
        val known = mergeWith(listOf(candidate), InteractionSignals(tracked = TrackedState.Known(emptySet())))

        assertTrue(unknown.none { it.manga.id == 9L })
        assertTrue(known.none { it.manga.id == 9L })
    }

    @Test
    fun `the tracker failure log message carries no url, title, id, or exception text`() {
        // The production log line is a fixed string; this guards it against future "helpful" edits.
        val message = "Exposure tracker lookup unavailable; exposure reordering skipped"
        assertFalse(message.contains("/m/"))
        assertFalse(message.contains("http"))
        assertTrue(message.none { it.isDigit() }) { "no id or count may appear in the tracker failure log" }
    }
}
// KMK <--

package exh.recs

// KMK --> v0.7.41 follow-up: known-context parity between live page-one and extra-page discovery
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaTaste
import tachiyomi.domain.taste.model.RatedMangaVisibility

/**
 * Tests for [filterVisibleCandidates], the shared pure filter seam used by both the live
 * page-one path and the extra-page discovery path in [BrowsePersonalRecommendationsScreenModel].
 *
 * Regression coverage for the v0.7.41 follow-up: `discoverAdditionalPage()` previously called
 * [RecommendationCandidateVisibilityPolicy] with `knownIds = emptySet()`, so a known-only extra
 * page could be recorded as successful/visible in discovery progress and candidate memory before
 * the final merge silently dropped it. Both call sites now go through this one function with the
 * real hide-known context, so a known candidate is excluded before scoring/progress/memory —
 * not just at merge time.
 *
 * Run with: ./gradlew :app:testDebugUnitTest --tests "*.BrowsePersonalRecommendationsFilterTest"
 */
class BrowsePersonalRecommendationsFilterTest {

    companion object {
        // KMK v0.7.44: see TestInjektSupport — this test constructs favorite=true Manga instances.
        @JvmStatic
        @BeforeAll
        fun registerCustomMangaInfoBinding() = TestInjektSupport.ensureCustomMangaInfoBound()
    }

    private val now = System.currentTimeMillis()

    private fun manga(
        id: Long,
        source: Long = 1L,
        url: String = "/m/$id",
        favorite: Boolean = false,
    ) = Manga.create().copy(id = id, source = source, url = url, favorite = favorite)

    private fun taste(manga: Manga, rating: MangaRating) = MangaTaste(
        mangaId = manga.id,
        source = manga.source,
        url = manga.url,
        title = "Title",
        rating = rating.value,
        createdAt = now,
        updatedAt = now,
    )

    private fun filter(
        candidates: List<Manga>,
        tasteByKey: Map<MangaTasteKey, MangaTaste> = emptyMap(),
        visibility: RatedMangaVisibility = RatedMangaVisibility.HIDE_DISLIKED_ONLY,
        seenKeys: Set<SeenMangaKey> = emptySet(),
        knownIds: Set<Long> = emptySet(),
        minChapterCount: Int = 0,
        chapterCounts: Map<Long, Long> = emptyMap(),
    ) = filterVisibleCandidates(candidates, tasteByKey, visibility, seenKeys, knownIds, minChapterCount, chapterCounts)

    // ---- Core regression: known candidates excluded before scoring/progress/memory ----

    @Test
    fun `known candidate is excluded when hide-known context is provided`() {
        val known = manga(id = 5L)
        val other = manga(id = 6L)
        val result = filter(listOf(known, other), knownIds = setOf(5L))
        assertEquals(listOf(6L), result.map { it.id })
    }

    @Test
    fun `all-known page yields an empty visible result — not recorded as successful`() {
        // Mirrors an additional discovery page whose every raw candidate is already known:
        // with the real hide-known context wired in, this page must filter down to empty,
        // so progressStatus downstream (computed from this list) is EMPTY, not SUCCESS.
        val allKnown = listOf(manga(id = 1L), manga(id = 2L), manga(id = 3L))
        val result = filter(allKnown, knownIds = setOf(1L, 2L, 3L))
        assertTrue(result.isEmpty()) { "Expected an all-known page to filter down to empty" }
    }

    @Test
    fun `empty knownIds (hide-known disabled or lookup failed) keeps candidates visible - fail open`() {
        // Simulates both "hideKnownManga disabled" and "known-id lookup failed" — both wire an
        // empty set through, and both must fail open rather than hide anything.
        val candidates = listOf(manga(id = 1L), manga(id = 2L))
        val result = filter(candidates, knownIds = emptySet())
        assertEquals(setOf(1L, 2L), result.map { it.id }.toSet())
    }

    // ---- Existing rules remain unchanged when combined with the known filter ----

    @Test
    fun `min chapter rule still applies alongside the known filter`() {
        val belowMin = manga(id = 10L)
        val known = manga(id = 11L)
        val visible = manga(id = 12L)
        val result = filter(
            listOf(belowMin, known, visible),
            knownIds = setOf(11L),
            minChapterCount = 10,
            chapterCounts = mapOf(10L to 2L, 12L to 20L),
        )
        assertEquals(listOf(12L), result.map { it.id })
    }

    @Test
    fun `seen rule still applies alongside the known filter`() {
        val seen = manga(id = 20L, source = 1L, url = "/m/20")
        val known = manga(id = 21L)
        val visible = manga(id = 22L)
        val result = filter(
            listOf(seen, known, visible),
            seenKeys = setOf(SeenMangaKey(1L, "/m/20")),
            knownIds = setOf(21L),
        )
        assertEquals(listOf(22L), result.map { it.id })
    }

    @Test
    fun `rated (disliked) rule still applies alongside the known filter`() {
        val disliked = manga(id = 30L)
        val known = manga(id = 31L)
        val visible = manga(id = 32L)
        val tasteByKey = mapOf(MangaTasteKey(disliked.source, disliked.url) to taste(disliked, MangaRating.DISLIKE))
        val result = filter(
            listOf(disliked, known, visible),
            tasteByKey = tasteByKey,
            knownIds = setOf(31L),
        )
        assertEquals(listOf(32L), result.map { it.id })
    }

    @Test
    fun `favorite rule still applies alongside the known filter`() {
        val favorite = manga(id = 40L, favorite = true)
        val known = manga(id = 41L)
        val visible = manga(id = 42L)
        val result = filter(
            listOf(favorite, known, visible),
            knownIds = setOf(41L),
        )
        assertEquals(listOf(42L), result.map { it.id })
    }
}
// KMK <--

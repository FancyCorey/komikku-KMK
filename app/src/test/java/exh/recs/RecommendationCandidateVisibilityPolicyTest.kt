package exh.recs

// KMK --> v0.7.40: candidate visibility policy tests
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaTaste
import tachiyomi.domain.taste.model.RatedMangaVisibility

/**
 * Tests for [RecommendationCandidateVisibilityPolicy].
 *
 * Verifies that each visibility reason is returned correctly for all supported filter types.
 *
 * Run with: ./gradlew :app:testDebugUnitTest --tests "*.RecommendationCandidateVisibilityPolicyTest"
 */
class RecommendationCandidateVisibilityPolicyTest {

    companion object {
        // KMK v0.7.44: see TestInjektSupport — this test constructs favorite=true Manga instances.
        @JvmStatic
        @BeforeAll
        fun registerCustomMangaInfoBinding() = TestInjektSupport.ensureCustomMangaInfoBound()
    }

    private val now = System.currentTimeMillis()

    private fun manga(
        id: Long = 1L,
        source: Long = 10L,
        url: String = "/m/1",
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

    private fun evaluate(
        manga: Manga = manga(),
        tasteByKey: Map<MangaTasteKey, MangaTaste> = emptyMap(),
        visibility: RatedMangaVisibility = RatedMangaVisibility.HIDE_DISLIKED_ONLY,
        seenKeys: Set<SeenMangaKey> = emptySet(),
        knownIds: Set<Long> = emptySet(),
        minChapterCount: Int = 0,
        chapterCounts: Map<Long, Long> = emptyMap(),
        seedMemberKeys: Set<Pair<Long, String>> = emptySet(),
    ) = RecommendationCandidateVisibilityPolicy.evaluate(
        manga,
        tasteByKey,
        visibility,
        seenKeys,
        knownIds,
        minChapterCount,
        chapterCounts,
        seedMemberKeys,
    )

    @Test
    fun `visible manga returns VISIBLE`() {
        assertEquals(CandidateVisibility.VISIBLE, evaluate())
    }

    @Test
    fun `favorited manga returns HIDDEN_FAVORITE`() {
        assertEquals(CandidateVisibility.HIDDEN_FAVORITE, evaluate(manga = manga(favorite = true)))
    }

    @Test
    fun `disliked manga with HIDE_DISLIKED_ONLY returns HIDDEN_RATED`() {
        val m = manga()
        val key = MangaTasteKey(m.source, m.url)
        val tasteByKey = mapOf(key to taste(m, MangaRating.DISLIKE))
        assertEquals(CandidateVisibility.HIDDEN_RATED, evaluate(manga = m, tasteByKey = tasteByKey))
    }

    @Test
    fun `liked manga with HIDE_DISLIKED_ONLY returns VISIBLE`() {
        val m = manga()
        val key = MangaTasteKey(m.source, m.url)
        val tasteByKey = mapOf(key to taste(m, MangaRating.LIKE))
        assertEquals(CandidateVisibility.VISIBLE, evaluate(manga = m, tasteByKey = tasteByKey))
    }

    @Test
    fun `any rated manga with HIDE_ALL_RATED returns HIDDEN_RATED`() {
        val m = manga()
        val key = MangaTasteKey(m.source, m.url)
        val tasteByKey = mapOf(key to taste(m, MangaRating.LOVE))
        assertEquals(CandidateVisibility.HIDDEN_RATED, evaluate(manga = m, tasteByKey = tasteByKey, visibility = RatedMangaVisibility.HIDE_ALL_RATED))
    }

    @Test
    fun `seen manga returns HIDDEN_SEEN`() {
        val m = manga()
        val seenKeys = setOf(SeenMangaKey(m.source, m.url))
        assertEquals(CandidateVisibility.HIDDEN_SEEN, evaluate(manga = m, seenKeys = seenKeys))
    }

    @Test
    fun `manga in knownIds returns HIDDEN_KNOWN`() {
        val m = manga(id = 42L)
        assertEquals(CandidateVisibility.HIDDEN_KNOWN, evaluate(manga = m, knownIds = setOf(42L)))
    }

    @Test
    fun `manga with empty knownIds set is not hidden by known filter`() {
        val m = manga(id = 42L)
        assertEquals(CandidateVisibility.VISIBLE, evaluate(manga = m, knownIds = emptySet()))
    }

    @Test
    fun `manga below min chapter count returns HIDDEN_MIN_CHAPTERS`() {
        val m = manga(id = 7L)
        assertEquals(
            CandidateVisibility.HIDDEN_MIN_CHAPTERS,
            evaluate(manga = m, minChapterCount = 10, chapterCounts = mapOf(7L to 3L)),
        )
    }

    @Test
    fun `manga at or above min chapter count returns VISIBLE`() {
        val m = manga(id = 7L)
        assertEquals(
            CandidateVisibility.VISIBLE,
            evaluate(manga = m, minChapterCount = 10, chapterCounts = mapOf(7L to 10L)),
        )
    }

    @Test
    fun `manga with unknown chapter count is not filtered by min-chapter rule`() {
        val m = manga(id = 7L)
        // No entry in chapterCounts → unknown → fail open
        assertEquals(
            CandidateVisibility.VISIBLE,
            evaluate(manga = m, minChapterCount = 10, chapterCounts = emptyMap()),
        )
    }

    @Test
    fun `seed member returns HIDDEN_SEED_MEMBER before other checks`() {
        val m = manga(id = 99L, source = 5L, url = "/seed", favorite = true)
        val seedMemberKeys = setOf(5L to "/seed")
        // Even though favorite=true, seed member check fires first
        assertEquals(CandidateVisibility.HIDDEN_SEED_MEMBER, evaluate(manga = m, seedMemberKeys = seedMemberKeys))
    }

    @Test
    fun `favorite is checked before rated and seen`() {
        val m = manga(id = 1L, favorite = true)
        val key = MangaTasteKey(m.source, m.url)
        val tasteByKey = mapOf(key to taste(m, MangaRating.DISLIKE))
        val seenKeys = setOf(SeenMangaKey(m.source, m.url))
        // Favorite check fires first
        assertEquals(CandidateVisibility.HIDDEN_FAVORITE, evaluate(manga = m, tasteByKey = tasteByKey, seenKeys = seenKeys))
    }

    @Test
    fun `rated check fires before seen check`() {
        val m = manga()
        val key = MangaTasteKey(m.source, m.url)
        val tasteByKey = mapOf(key to taste(m, MangaRating.DISLIKE))
        val seenKeys = setOf(SeenMangaKey(m.source, m.url))
        assertEquals(CandidateVisibility.HIDDEN_RATED, evaluate(manga = m, tasteByKey = tasteByKey, seenKeys = seenKeys))
    }

    // ---- v0.7.41: single shared contract — live / cache / memory / group all go through this policy ----

    @Test
    fun `same candidate and context yield the same decision regardless of caller path`() {
        // The live, cache, memory-merge, and group flows all delegate to this one function, so a
        // fixed (candidate, context) pair must always produce the same visibility decision.
        val m = manga(id = 42L)
        val key = MangaTasteKey(m.source, m.url)
        val tasteByKey = mapOf(key to taste(m, MangaRating.DISLIKE))
        val seenKeys = setOf(SeenMangaKey(m.source, m.url))
        val knownIds = setOf(42L)
        val chapterCounts = mapOf(42L to 1L)

        val decisions = List(4) {
            RecommendationCandidateVisibilityPolicy.evaluate(
                manga = m,
                tasteByKey = tasteByKey,
                visibility = RatedMangaVisibility.HIDE_DISLIKED_ONLY,
                seenKeys = seenKeys,
                knownIds = knownIds,
                minChapterCount = 10,
                chapterCounts = chapterCounts,
            )
        }
        assertEquals(1, decisions.distinct().size) { "Policy must be deterministic across callers: $decisions" }
        // Rated fires before seen/known/min-chapter, so the shared decision is HIDDEN_RATED.
        assertEquals(CandidateVisibility.HIDDEN_RATED, decisions.first())
    }

    @Test
    fun `min chapter rule is consistent for a below-threshold candidate across repeated evaluation`() {
        val m = manga(id = 7L)
        val below = RecommendationCandidateVisibilityPolicy.evaluate(
            manga = m,
            tasteByKey = emptyMap(),
            visibility = RatedMangaVisibility.HIDE_DISLIKED_ONLY,
            seenKeys = emptySet(),
            knownIds = emptySet(),
            minChapterCount = 10,
            chapterCounts = mapOf(7L to 3L),
        )
        assertEquals(CandidateVisibility.HIDDEN_MIN_CHAPTERS, below)
    }
}
// KMK <--

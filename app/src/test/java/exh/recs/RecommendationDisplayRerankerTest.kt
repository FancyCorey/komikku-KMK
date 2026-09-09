package exh.recs

import exh.recs.RecommendationDisplayReranker.ExposureKey
import exh.recs.RecommendationDisplayReranker.ExposureSummary
import exh.recs.RecommendationDisplayReranker.InteractionSignals
import exh.recs.RecommendationDisplayReranker.TrackedState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga

/**
 * Reranker invariant tests. Each named invariant in [RecommendationDisplayReranker]'s KDoc has a
 * direct test here: permutation-never-deletion, base score never mutated, strong personalized match
 * never displaced by novelty alone, deterministic/jitter-free ordering, and fail-open behavior.
 *
 * migrated from url-only keys to the full
 * `(sourceId, url)` [ExposureKey], and extended with the three independent positive-interaction
 * exemptions (library / rated / tracked) plus a cross-source url-collision test.
 */
class RecommendationDisplayRerankerTest {

    private val day = 24L * 60L * 60L * 1000L
    private val now = 1_800_000_000_000L
    private val sourceA = 1L
    private val sourceB = 2L

    private fun rec(url: String, score: Double, source: Long = sourceA) = PersonalRecommendation(
        manga = Manga.create()
            .copy(id = (source * 1_000_000 + url.hashCode()), source = source, url = url, ogTitle = url),
        score = score,
        matchedGroups = emptyList(),
    )

    private fun key(url: String, source: Long = sourceA) = ExposureKey(source, url)

    /** Heavily-exposed, very recent, untouched -- the maximum penalty case. */
    private fun heavilyExposed() = ExposureSummary(lastExposedAt = now, exposureCount = 8, lastInteractionAt = null)

    private fun rerank(
        candidates: List<PersonalRecommendation>,
        exposureByKey: Map<ExposureKey, ExposureSummary>,
        interactions: InteractionSignals = InteractionSignals.NONE,
        windowDays: Int = RecommendationExposurePolicy.DEFAULT_WINDOW_DAYS,
    ) = RecommendationDisplayReranker.rerank(
        candidates = candidates,
        exposureByKey = exposureByKey,
        interactions = interactions,
        now = now,
        windowDays = windowDays,
    )

    // --- Invariant 1: permutation, never deletion ---

    @Test
    fun `output is always a permutation of the input, never a deletion`() {
        val input = listOf(rec("/a", 5.0), rec("/b", 4.9), rec("/c", 4.8))
        val out = rerank(input, mapOf(key("/a") to heavilyExposed()))
        assertTrue(RecommendationDisplayReranker.isPermutationOf(input, out))
        assertEquals(input.size, out.size)
    }

    @Test
    fun `an exposed candidate remains present after a soft penalty`() {
        val input = listOf(rec("/a", 5.0), rec("/b", 4.9))
        val out = rerank(input, mapOf(key("/a") to heavilyExposed()))
        assertTrue(out.any { it.manga.url == "/a" })
    }

    // --- Invariant 2: base score is never mutated ---

    @Test
    fun `base relevance scores are never mutated by reranking`() {
        val input = listOf(rec("/a", 5.0), rec("/b", 4.9))
        val out = rerank(input, mapOf(key("/a") to heavilyExposed()))
        assertEquals(5.0, out.first { it.manga.url == "/a" }.score)
        assertEquals(4.9, out.first { it.manga.url == "/b" }.score)
    }

    // --- Invariant 3: novelty alone cannot displace a strong match ---

    @Test
    fun `a strongly personalized match outranks a novelty-only candidate despite full exposure`() {
        // Score gap far exceeds MAX_PENALTY, so the exposed candidate keeps first place.
        val input = listOf(rec("/strong", 100.0), rec("/weak", 1.0))
        val out = rerank(input, mapOf(key("/strong") to heavilyExposed()))
        assertEquals("/strong", out.first().manga.url)
    }

    @Test
    fun `the penalty can only reorder candidates that were already close in score`() {
        val input = listOf(rec("/a", 5.0), rec("/b", 4.99))
        val out = rerank(input, mapOf(key("/a") to heavilyExposed()))
        assertEquals("/b", out.first().manga.url)
    }

    // --- Invariant 6: the three positive-interaction exemptions ---

    @Test
    fun `a library candidate is never demoted no matter how often it was shown`() {
        val input = listOf(rec("/a", 5.0), rec("/b", 4.99))
        val out = rerank(
            input,
            mapOf(key("/a") to heavilyExposed()),
            InteractionSignals(library = setOf(key("/a"))),
        )
        assertEquals("/a", out.first().manga.url)
    }

    @Test
    fun `a rated candidate is never demoted no matter how often it was shown`() {
        val input = listOf(rec("/a", 5.0), rec("/b", 4.99))
        val out = rerank(
            input,
            mapOf(key("/a") to heavilyExposed()),
            InteractionSignals(rated = setOf(key("/a"))),
        )
        assertEquals("/a", out.first().manga.url)
    }

    @Test
    fun `a known tracked candidate is never demoted no matter how often it was shown`() {
        val input = listOf(rec("/a", 5.0), rec("/b", 4.99))
        val out = rerank(
            input,
            mapOf(key("/a") to heavilyExposed()),
            InteractionSignals(tracked = TrackedState.Known(setOf(key("/a")))),
        )
        assertEquals("/a", out.first().manga.url)
    }

    @Test
    fun `a known untracked candidate may be exposure-reranked`() {
        // Tracker state was genuinely resolved and this candidate is not tracked, so the ordinary
        // exposure penalty applies -- this is the case that must keep working.
        val input = listOf(rec("/a", 5.0), rec("/b", 4.99))
        val out = rerank(
            input,
            mapOf(key("/a") to heavilyExposed()),
            InteractionSignals(tracked = TrackedState.Known(emptySet())),
        )
        assertEquals("/b", out.first().manga.url)
    }

    // The tri-state's whole reason for existing.

    @Test
    fun `an unknown tracker state produces no penalty at all`() {
        val input = listOf(rec("/a", 5.0), rec("/b", 4.99))
        val out = rerank(
            input,
            mapOf(key("/a") to heavilyExposed()),
            InteractionSignals(tracked = TrackedState.Unknown),
        )
        // Identical to the input: unknown tracker state must never let a possibly-tracked title be
        // demoted. Under the previous empty-set model this returned ["/b", "/a"].
        assertEquals(listOf("/a", "/b"), out.map { it.manga.url })
    }

    @Test
    fun `TRACKER_UNAVAILABLE is an identity permutation that removes nothing`() {
        val input = listOf(rec("/a", 5.0), rec("/b", 4.99), rec("/c", 4.98))
        val out = rerank(
            input,
            input.associate { key(it.manga.url) to heavilyExposed() },
            InteractionSignals.TRACKER_UNAVAILABLE,
        )
        assertEquals(input.map { it.manga.url }, out.map { it.manga.url })
        assertTrue(RecommendationDisplayReranker.isPermutationOf(input, out))
        assertEquals(input.size, out.size)
    }

    @Test
    fun `an unknown tracker state still leaves library and rated exemptions structurally intact`() {
        // Nothing is penalised, so the exemptions are trivially honoured -- but they must also still
        // be carried through rather than being dropped from the signal object.
        val signals = InteractionSignals(
            library = setOf(key("/a")),
            rated = setOf(key("/b")),
            tracked = TrackedState.Unknown,
        )
        assertEquals(setOf(key("/a")), signals.library)
        assertEquals(setOf(key("/b")), signals.rated)
        val input = listOf(rec("/a", 5.0), rec("/b", 4.99))
        assertEquals(input, rerank(input, mapOf(key("/a") to heavilyExposed()), signals))
    }

    @Test
    fun `InteractionSignals defaults to the safe unknown tracker state`() {
        // A caller that forgets to resolve tracker state must get the safe behavior, not the unsafe
        // "assume untracked" one.
        assertEquals(TrackedState.Unknown, InteractionSignals().tracked)
    }

    // --- Identity: (sourceId, url), never url alone ---

    @Test
    fun `two sources sharing the same url do not inherit each other's exposure penalty`() {
        // Same relative url, different sources -- a routine real-world collision.
        val fromA = rec("/manga/one-piece", 5.0, source = sourceA)
        val fromB = rec("/manga/one-piece", 4.99, source = sourceB)
        val out = rerank(
            listOf(fromA, fromB),
            // Only source A's copy has been shown repeatedly.
            mapOf(key("/manga/one-piece", sourceA) to heavilyExposed()),
        )
        // B is promoted because only A carries the penalty. Under the old url-only map both would
        // have been penalised identically and the order would have been unchanged.
        assertEquals(sourceB, out.first().manga.source)
    }

    @Test
    fun `an exposure record for a different source is ignored entirely`() {
        val input = listOf(rec("/a", 5.0, sourceA), rec("/b", 4.99, sourceA))
        val out = rerank(input, mapOf(key("/a", sourceB) to heavilyExposed()))
        // The record belongs to source B, so source A's candidates keep their plain score order.
        assertEquals(listOf("/a", "/b"), out.map { it.manga.url })
    }

    // --- Invariant 4: determinism ---

    @Test
    fun `identical inputs produce identical ordering across repeated refreshes`() {
        val input = listOf(rec("/a", 5.0), rec("/b", 4.99), rec("/c", 4.98))
        val exposure = mapOf(key("/a") to heavilyExposed())
        val first = rerank(input, exposure).map { it.manga.url }
        val second = rerank(input, exposure).map { it.manga.url }
        assertEquals(first, second)
    }

    @Test
    fun `equal effective scores keep their original relative order`() {
        val input = listOf(rec("/a", 5.0), rec("/b", 5.0), rec("/c", 5.0))
        val out = rerank(input, mapOf(key("/zzz") to heavilyExposed()))
        assertEquals(listOf("/a", "/b", "/c"), out.map { it.manga.url })
    }

    // --- Invariant 5: fails open ---

    @Test
    fun `an empty exposure map is an identity permutation`() {
        val input = listOf(rec("/a", 1.0), rec("/b", 2.0))
        assertEquals(input, rerank(input, emptyMap()))
    }

    @Test
    fun `exposure entries that all resolve to zero penalty leave the order untouched`() {
        val input = listOf(rec("/a", 5.0), rec("/b", 4.9))
        // Exposed once, long outside the window -- no penalty.
        val stale = ExposureSummary(lastExposedAt = now - 400L * day, exposureCount = 1, lastInteractionAt = null)
        assertEquals(input, rerank(input, mapOf(key("/a") to stale)))
    }

    @Test
    fun `malformed exposure timestamps fail open rather than reordering`() {
        val input = listOf(rec("/a", 5.0), rec("/b", 4.9))
        val malformed = ExposureSummary(lastExposedAt = -1L, exposureCount = -5, lastInteractionAt = null)
        assertEquals(input, rerank(input, mapOf(key("/a") to malformed)))
    }

    @Test
    fun `single-item and empty rows are returned unchanged`() {
        val single = listOf(rec("/a", 1.0))
        assertEquals(single, rerank(single, mapOf(key("/a") to heavilyExposed())))
        assertTrue(rerank(emptyList(), mapOf(key("/a") to heavilyExposed())).isEmpty())
    }

    // --- Row-shape guarantees ---

    @Test
    fun `the row keeps the same card count so per-source quotas and caps stay valid`() {
        val input = (1..10).map { rec("/m$it", 5.0 - it * 0.001) }
        val out = rerank(input, input.associate { key(it.manga.url) to heavilyExposed() })
        assertEquals(10, out.size)
    }

    @Test
    fun `isPermutationOf rejects a genuine deletion`() {
        val input = listOf(rec("/a", 1.0), rec("/b", 2.0))
        assertTrue(!RecommendationDisplayReranker.isPermutationOf(input, input.drop(1)))
    }

    @Test
    fun `a longer configured window keeps a penalty alive that a shorter window would have expired`() {
        val input = listOf(rec("/a", 5.0), rec("/b", 4.99))
        val exposedTenDaysAgo =
            ExposureSummary(lastExposedAt = now - 10L * day, exposureCount = 8, lastInteractionAt = null)
        val shortWindow = rerank(input, mapOf(key("/a") to exposedTenDaysAgo), windowDays = 7)
        val longWindow = rerank(input, mapOf(key("/a") to exposedTenDaysAgo), windowDays = 30)
        // Outside a 7-day window the exposure is forgotten; inside a 30-day window it still reorders.
        assertEquals(listOf("/a", "/b"), shortWindow.map { it.manga.url })
        assertNotEquals(shortWindow.map { it.manga.url }, longWindow.map { it.manga.url })
    }
}
// KMK <--

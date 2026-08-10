package exh.recs

// KMK -->
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga

class CombinedPicksAccumulatorTest {

    private lateinit var acc: CombinedPicksAccumulator

    @BeforeEach
    fun setup() {
        acc = CombinedPicksAccumulator()
    }

    // --- Helpers ---

    private fun manga(
        id: Long,
        source: Long = 1L,
        url: String = "/manga/$id",
        title: String = "Manga $id",
        author: String? = null,
        artist: String? = null,
        genres: List<String>? = null,
    ): Manga = Manga.create().copy(
        id = id,
        source = source,
        url = url,
        ogTitle = title,
        ogAuthor = author,
        ogArtist = artist,
        ogGenre = genres,
    )

    private fun rec(manga: Manga, score: Double, groups: List<String> = emptyList()) =
        PersonalRecommendation(manga, score, groups)

    // --- Basic accumulation ---

    @Test
    fun `isEmpty returns true initially`() {
        assertTrue(acc.isEmpty())
    }

    @Test
    fun `same source+url increments occurrence count`() {
        val m = manga(1L)
        acc.add(listOf(rec(m, 1.0)), sourceId = 10L)
        acc.add(listOf(rec(m, 0.5)), sourceId = 11L)

        val results = acc.rank(emptySet(), cap = 10)
        assertEquals(1, results.size)
    }

    @Test
    fun `different source+url stays as two entries even with same title`() {
        val m1 = manga(1L, source = 10L, url = "/a", title = "Same Title")
        val m2 = manga(2L, source = 20L, url = "/b", title = "Same Title")
        acc.add(listOf(rec(m1, 1.0)), sourceId = 10L)
        acc.add(listOf(rec(m2, 1.0)), sourceId = 20L)

        val results = acc.rank(emptySet(), cap = 10)
        assertEquals(2, results.size)
    }

    @Test
    fun `best score wins when same manga added twice with different scores`() {
        val m = manga(1L)
        acc.add(listOf(rec(m, 0.4)), sourceId = 10L)
        acc.add(listOf(rec(m, 0.9)), sourceId = 11L)

        val results = acc.rank(emptySet(), cap = 10)
        assertEquals(0.9, results.first().score, 0.001)
    }

    @Test
    fun `higher personal score beats weak repeated candidate`() {
        val highScore = manga(1L)
        val repeated = manga(2L)
        // highScore: 3.0, appears once. repeated: 1.0, appears 3x → 1.0 + 2*0.3 = 1.6
        acc.add(listOf(rec(highScore, 3.0)), sourceId = 10L)
        acc.add(listOf(rec(repeated, 1.0)), sourceId = 10L)
        acc.add(listOf(rec(repeated, 1.0)), sourceId = 11L)
        acc.add(listOf(rec(repeated, 1.0)), sourceId = 12L)

        val results = acc.rank(emptySet(), cap = 10)
        assertEquals(highScore.id, results.first().manga.id)
    }

    @Test
    fun `occurrence bonus raises repeated manga when personal scores are close`() {
        val single = manga(1L)
        val repeated = manga(2L)
        // single: 1.4, repeated: 1.0 + 2*0.3 = 1.6 → repeated wins
        acc.add(listOf(rec(single, 1.4)), sourceId = 10L)
        acc.add(listOf(rec(repeated, 1.0)), sourceId = 10L)
        acc.add(listOf(rec(repeated, 1.0)), sourceId = 11L)
        acc.add(listOf(rec(repeated, 1.0)), sourceId = 12L)

        val results = acc.rank(emptySet(), cap = 10)
        assertEquals(repeated.id, results.first().manga.id)
    }

    @Test
    fun `boosted bonus applied when contributing source is boosted`() {
        val boostedManga = manga(1L)
        val normalManga = manga(2L)
        // boostedManga raw=1.1, combined=1.1+0.2=1.3 > normalManga=1.2
        acc.add(listOf(rec(boostedManga, 1.1)), sourceId = 99L)
        acc.add(listOf(rec(normalManga, 1.2)), sourceId = 50L)

        val results = acc.rank(boostedSourceIds = setOf(99L), cap = 10)
        assertEquals(boostedManga.id, results.first().manga.id)
    }

    @Test
    fun `boosted bonus does not overpower much stronger personal score`() {
        val highScore = manga(1L)
        val boostedWeak = manga(2L)
        // highScore: 3.0, no boost. boostedWeak: 0.8 + 0.2 = 1.0 — highScore still wins
        acc.add(listOf(rec(highScore, 3.0)), sourceId = 10L)
        acc.add(listOf(rec(boostedWeak, 0.8)), sourceId = 99L)

        val results = acc.rank(boostedSourceIds = setOf(99L), cap = 10)
        assertEquals(highScore.id, results.first().manga.id)
    }

    @Test
    fun `cap limits returned results`() {
        repeat(10) { i -> acc.add(listOf(rec(manga(i.toLong()), 1.0)), sourceId = 1L) }

        val results = acc.rank(emptySet(), cap = 5)
        assertEquals(5, results.size)
    }

    @Test
    fun `clear empties all buckets and work-key map`() {
        val m = manga(1L, title = "Title", author = "Author")
        acc.add(listOf(rec(m, 1.0)), sourceId = 1L)
        acc.clear()
        assertTrue(acc.isEmpty())
        assertEquals(0, acc.rank(emptySet(), cap = 10).size)
        // After clear, same manga from a new source should start fresh
        val m2 = manga(2L, source = 99L, title = "Title", author = "Author")
        acc.add(listOf(rec(m2, 1.0)), sourceId = 99L)
        assertEquals(1, acc.rank(emptySet(), cap = 10).size)
    }

    @Test
    fun `matched groups are merged across occurrences`() {
        val m = manga(1L)
        acc.add(listOf(rec(m, 1.0, listOf("action"))), sourceId = 10L)
        acc.add(listOf(rec(m, 0.8, listOf("fantasy"))), sourceId = 11L)

        val result = acc.rank(emptySet(), cap = 10).first()
        assertTrue("action" in result.matchedGroups)
        assertTrue("fantasy" in result.matchedGroups)
    }

    @Test
    fun `rank returns empty list when accumulator is empty`() {
        assertTrue(acc.rank(emptySet(), cap = 10).isEmpty())
    }

    @Test
    fun `occurrence constants have expected values`() {
        assertEquals(0.3, CombinedPicksAccumulator.OCCURRENCE_BONUS, 0.001)
        assertEquals(0.2, CombinedPicksAccumulator.BOOSTED_BONUS, 0.001)
    }

    // --- Conservative work-key dedup ---

    @Test
    fun `exact title plus exact author merges across different sources`() {
        val m1 = manga(1L, source = 10L, url = "/s10/a", title = "The Dragon King", author = "Park Soo")
        val m2 = manga(2L, source = 20L, url = "/s20/b", title = "The Dragon King", author = "Park Soo")
        acc.add(listOf(rec(m1, 1.0)), sourceId = 10L)
        acc.add(listOf(rec(m2, 0.8)), sourceId = 20L)

        val results = acc.rank(emptySet(), cap = 10)
        assertEquals(1, results.size)
        // Occurrence count reflected — merged occurrence count = 2, bonus applied
    }

    @Test
    fun `exact title plus exact artist merges across different sources`() {
        val m1 = manga(1L, source = 10L, url = "/s10/a", title = "Tower Climb", artist = "Lee Jin")
        val m2 = manga(2L, source = 20L, url = "/s20/b", title = "Tower Climb", artist = "Lee Jin")
        acc.add(listOf(rec(m1, 1.0)), sourceId = 10L)
        acc.add(listOf(rec(m2, 0.9)), sourceId = 20L)

        val results = acc.rank(emptySet(), cap = 10)
        assertEquals(1, results.size)
    }

    @Test
    fun `same title same author different artist merges via author key`() {
        val m1 = manga(1L, source = 10L, url = "/a", title = "Gate", author = "Kim Author", artist = "Lee Artist")
        val m2 = manga(2L, source = 20L, url = "/b", title = "Gate", author = "Kim Author", artist = "Other Artist")
        acc.add(listOf(rec(m1, 1.0)), sourceId = 10L)
        acc.add(listOf(rec(m2, 0.9)), sourceId = 20L)

        val results = acc.rank(emptySet(), cap = 10)
        assertEquals(1, results.size)
    }

    @Test
    fun `same title same artist different author merges via artist key`() {
        val m1 = manga(1L, source = 10L, url = "/a", title = "Quest", author = "Author A", artist = "Shared Artist")
        val m2 = manga(2L, source = 20L, url = "/b", title = "Quest", author = "Author B", artist = "Shared Artist")
        acc.add(listOf(rec(m1, 1.0)), sourceId = 10L)
        acc.add(listOf(rec(m2, 0.9)), sourceId = 20L)

        val results = acc.rank(emptySet(), cap = 10)
        assertEquals(1, results.size)
    }

    @Test
    fun `exact title with blank author and blank artist does not merge`() {
        val m1 = manga(1L, source = 10L, url = "/s10/a", title = "Solo Hunter", author = null, artist = null)
        val m2 = manga(2L, source = 20L, url = "/s20/b", title = "Solo Hunter", author = null, artist = null)
        acc.add(listOf(rec(m1, 1.0)), sourceId = 10L)
        acc.add(listOf(rec(m2, 0.8)), sourceId = 20L)

        val results = acc.rank(emptySet(), cap = 10)
        assertEquals(2, results.size)
    }

    @Test
    fun `exact title with different author and different artist does not merge`() {
        val m1 = manga(1L, source = 10L, url = "/a", title = "Reborn", author = "Kim A", artist = "Art A")
        val m2 = manga(2L, source = 20L, url = "/b", title = "Reborn", author = "Kim B", artist = "Art B")
        acc.add(listOf(rec(m1, 1.0)), sourceId = 10L)
        acc.add(listOf(rec(m2, 0.9)), sourceId = 20L)

        val results = acc.rank(emptySet(), cap = 10)
        assertEquals(2, results.size)
    }

    @Test
    fun `similar title does not merge`() {
        val m1 = manga(1L, source = 10L, url = "/a", title = "Solo Leveling", author = "Chu-Gong")
        val m2 = manga(2L, source = 20L, url = "/b", title = "Solo Leveling (Comic)", author = "Chu-Gong")
        acc.add(listOf(rec(m1, 1.0)), sourceId = 10L)
        acc.add(listOf(rec(m2, 0.9)), sourceId = 20L)

        // Normalized titles differ: "solo leveling" vs "solo leveling  comic"
        val results = acc.rank(emptySet(), cap = 10)
        assertEquals(2, results.size)
    }

    @Test
    fun `merged duplicate keeps best score and merged matched groups`() {
        val m1 = manga(1L, source = 10L, url = "/a", title = "Gate", author = "Sao Dao")
        val m2 = manga(2L, source = 20L, url = "/b", title = "Gate", author = "Sao Dao")
        acc.add(listOf(rec(m1, 0.8, listOf("action"))), sourceId = 10L)
        acc.add(listOf(rec(m2, 1.5, listOf("fantasy"))), sourceId = 20L)

        val results = acc.rank(emptySet(), cap = 10)
        assertEquals(1, results.size)
        assertEquals(1.5, results.first().score, 0.001)
        assertTrue("action" in results.first().matchedGroups)
        assertTrue("fantasy" in results.first().matchedGroups)
    }

    // --- Conservative work-key helper ---

    @Test
    fun `conservativeWorkKeys returns empty set when title is blank`() {
        val m = manga(1L, title = "   ", author = "Author")
        assertTrue(CombinedPicksAccumulator.conservativeWorkKeys(m).isEmpty())
    }

    @Test
    fun `conservativeWorkKeys returns empty set when both author and artist are blank`() {
        val m = manga(1L, title = "Title", author = null, artist = null)
        assertTrue(CombinedPicksAccumulator.conservativeWorkKeys(m).isEmpty())
    }

    @Test
    fun `conservativeWorkKeys returns author key when author is present`() {
        val m = manga(1L, title = "Title", author = "Author X")
        val keys = CombinedPicksAccumulator.conservativeWorkKeys(m)
        assertTrue(keys.any { it.contains("|author:") })
    }

    @Test
    fun `conservativeWorkKeys returns artist key when artist is present`() {
        val m = manga(1L, title = "Title", artist = "Artist X")
        val keys = CombinedPicksAccumulator.conservativeWorkKeys(m)
        assertTrue(keys.any { it.contains("|artist:") })
    }

    @Test
    fun `conservativeWorkKeys returns both keys when both author and artist are present`() {
        val m = manga(1L, title = "Title", author = "Author X", artist = "Artist X")
        val keys = CombinedPicksAccumulator.conservativeWorkKeys(m)
        assertEquals(2, keys.size)
        assertTrue(keys.any { it.contains("|author:") })
        assertTrue(keys.any { it.contains("|artist:") })
    }

    @Test
    fun `conservativeWorkKeys produces same author key for same normalized title and author`() {
        val m1 = manga(1L, title = "The Dragon King!", author = "Park Soo-chan")
        val m2 = manga(2L, title = "the dragon king", author = "park soo chan")
        val k1 = CombinedPicksAccumulator.conservativeWorkKeys(m1).filter { it.contains("|author:") }
        val k2 = CombinedPicksAccumulator.conservativeWorkKeys(m2).filter { it.contains("|author:") }
        assertEquals(k1, k2)
    }

    @Test
    fun `conservativeWorkKeys produces different author keys for different authors`() {
        val m1 = manga(1L, title = "Reborn", author = "Kim A")
        val m2 = manga(2L, title = "Reborn", author = "Kim B")
        val k1 = CombinedPicksAccumulator.conservativeWorkKeys(m1)
        val k2 = CombinedPicksAccumulator.conservativeWorkKeys(m2)
        assertTrue(k1.intersect(k2).isEmpty())
    }

    @Test
    fun `conservativeWorkKeys does not throw on unusual characters in metadata`() {
        val m = manga(1L, title = "Title: A Journey (Vol. 1-∞)", author = "Björn Ångström")
        // Should not throw
        CombinedPicksAccumulator.conservativeWorkKeys(m)
    }
}
// KMK <--

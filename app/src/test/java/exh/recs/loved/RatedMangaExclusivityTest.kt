package exh.recs.loved

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaTaste

// KMK -->
/**
 * Tests for rated-manga cross-source group display exclusivity (v0.7.37).
 *
 * DB-level invariant (one rating per manga_id, unique index on source+url) is enforced
 * by manga_taste.sq and is not tested here. These tests cover the display-level fix:
 * [resolveLinkedGroupRatingConflicts] ensures a confirmed cross-source link group does not
 * appear in multiple rating tabs simultaneously.
 */
class RatedMangaExclusivityTest {

    private fun taste(
        mangaId: Long,
        source: Long,
        url: String,
        rating: Int,
        updatedAt: Long,
    ) = MangaTaste(
        mangaId = mangaId,
        source = source,
        url = url,
        title = "Title $mangaId",
        rating = rating,
        createdAt = 0L,
        updatedAt = updatedAt,
    )

    private val rLove = MangaRating.LOVE.value
    private val rLike = MangaRating.LIKE.value
    private val rDislike = MangaRating.DISLIKE.value

    // --- standalone entries (no link group) ---

    @Test
    fun `standalone entries are always kept regardless of rating`() {
        val tastes = listOf(
            taste(1L, 10L, "/a", rLove, 100L),
            taste(2L, 10L, "/b", rLike, 200L),
        )
        val result = resolveLinkedGroupRatingConflicts(tastes, emptyMap())
        assertEquals(2, result.size)
    }

    // --- single-rating link groups (no conflict) ---

    @Test
    fun `link group where all members share same rating keeps all members`() {
        val linkGroupByKey = mapOf(
            "10|/a" to "g1",
            "20|/a" to "g1",
        )
        val tastes = listOf(
            taste(1L, 10L, "/a", rLove, 100L),
            taste(2L, 20L, "/a", rLove, 200L),
        )
        val result = resolveLinkedGroupRatingConflicts(tastes, linkGroupByKey)
        assertEquals(2, result.size)
    }

    // --- mixed-rating link groups (conflict resolution) ---

    @Test
    fun `link group with love newer than like keeps only love member`() {
        val linkGroupByKey = mapOf(
            "10|/a" to "g1",
            "20|/b" to "g1",
        )
        val tastes = listOf(
            taste(1L, 10L, "/a", rLove, 300L), // newer
            taste(2L, 20L, "/b", rLike, 100L), // older
        )
        val result = resolveLinkedGroupRatingConflicts(tastes, linkGroupByKey)
        assertEquals(1, result.size)
        assertEquals(rLove, result[0].rating)
        assertEquals(10L, result[0].source)
    }

    @Test
    fun `link group with like newer than love keeps only like member`() {
        val linkGroupByKey = mapOf(
            "10|/a" to "g1",
            "20|/b" to "g1",
        )
        val tastes = listOf(
            taste(1L, 10L, "/a", rLove, 100L), // older
            taste(2L, 20L, "/b", rLike, 400L), // newer
        )
        val result = resolveLinkedGroupRatingConflicts(tastes, linkGroupByKey)
        assertEquals(1, result.size)
        assertEquals(rLike, result[0].rating)
        assertEquals(20L, result[0].source)
    }

    @Test
    fun `link group with dislike newest suppresses love and like members`() {
        val linkGroupByKey = mapOf(
            "10|/a" to "g1",
            "20|/b" to "g1",
            "30|/c" to "g1",
        )
        val tastes = listOf(
            taste(1L, 10L, "/a", rLove, 100L),
            taste(2L, 20L, "/b", rLike, 200L),
            taste(3L, 30L, "/c", rDislike, 500L), // newest
        )
        val result = resolveLinkedGroupRatingConflicts(tastes, linkGroupByKey)
        assertEquals(1, result.size)
        assertEquals(rDislike, result[0].rating)
    }

    // --- mixed standalone and grouped entries ---

    @Test
    fun `standalone entries are unaffected by conflict resolution in other groups`() {
        val linkGroupByKey = mapOf(
            "10|/a" to "g1",
            "20|/b" to "g1",
        )
        val tastes = listOf(
            taste(1L, 10L, "/a", rLove, 100L),
            taste(2L, 20L, "/b", rLike, 200L), // wins for g1
            taste(3L, 30L, "/c", rLove, 50L), // standalone, no group
        )
        val result = resolveLinkedGroupRatingConflicts(tastes, linkGroupByKey)
        assertEquals(2, result.size)
        val ratings = result.map { it.rating }.toSet()
        assertTrue(rLike in ratings)
        assertTrue(rLove in ratings)
        val sources = result.map { it.source }.toSet()
        assertTrue(30L in sources) // standalone kept
        assertFalse(10L in sources) // love group member suppressed
    }

    // --- rating overwrite semantics (display-level) ---

    @Test
    fun `rating overwrite - if same-group entry is rerated from love to like, only like tab shows group`() {
        val linkGroupByKey = mapOf("10|/a" to "g1")
        // Only one member in this group, so "overwrite" is represented by updatedAt change
        val tastes = listOf(
            taste(1L, 10L, "/a", rLike, 300L), // previously love, now like (newer)
        )
        val result = resolveLinkedGroupRatingConflicts(tastes, linkGroupByKey)
        // Single member group — always kept
        assertEquals(1, result.size)
        assertEquals(rLike, result[0].rating)
    }

    @Test
    fun `empty tastes returns empty list`() {
        val result = resolveLinkedGroupRatingConflicts(emptyList(), emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `taste with key not in linkGroupByKey is treated as standalone`() {
        val linkGroupByKey = mapOf("99|/z" to "g99") // different key
        val tastes = listOf(taste(1L, 10L, "/a", rLove, 100L))
        val result = resolveLinkedGroupRatingConflicts(tastes, linkGroupByKey)
        assertEquals(1, result.size) // kept as standalone
    }
}
// KMK <--

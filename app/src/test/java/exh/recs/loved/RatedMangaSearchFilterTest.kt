package exh.recs.loved

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.model.MangaTaste

// KMK v0.8.10 -->
class RatedMangaSearchFilterTest {

    private fun taste(source: Long = 1L, url: String = "/manga/x", title: String = "Taste Title", rating: Int = 2) =
        MangaTaste(mangaId = 1L, source = source, url = url, title = title, rating = rating, createdAt = 0L, updatedAt = 0L)

    private fun item(
        source: Long = 1L,
        url: String = "/manga/x",
        tasteTitle: String = "Taste Title",
        mangaTitle: String? = "Manga Title",
        versionCount: Int = 1,
    ) = LovedDisplayItem(
        taste = taste(source = source, url = url, title = tasteTitle),
        manga = mangaTitle?.let { Manga.create().copy(ogTitle = it) },
        versionCount = versionCount,
    )

    @Test
    fun `blank query matches every item - no filtering`() {
        val items = listOf(item(mangaTitle = "One Piece"), item(mangaTitle = "Naruto"))
        assertEquals(items, RatedMangaSearchFilter.filter(items, "", sourceNameOf = { null }))
        assertEquals(items, RatedMangaSearchFilter.filter(items, "   ", sourceNameOf = { null }))
    }

    @Test
    fun `matches the displayed manga title, case-insensitively`() {
        val target = item(mangaTitle = "One Piece")
        val other = item(url = "/manga/y", mangaTitle = "Naruto")
        val result = RatedMangaSearchFilter.filter(listOf(target, other), "one piece", sourceNameOf = { null })
        assertEquals(listOf(target), result)
    }

    @Test
    fun `matches a substring anywhere in the title`() {
        val target = item(mangaTitle = "The Beginning After The End")
        val result = RatedMangaSearchFilter.filter(listOf(target), "after the end", sourceNameOf = { null })
        assertEquals(listOf(target), result)
    }

    @Test
    fun `falls back to the taste title when manga is null - not yet resolved locally`() {
        val target = item(mangaTitle = null, tasteTitle = "Unresolved Title")
        val result = RatedMangaSearchFilter.filter(listOf(target), "unresolved", sourceNameOf = { null })
        assertEquals(listOf(target), result)
    }

    @Test
    fun `matches the alternate taste title even when the manga title differs, for example after a title edit`() {
        val target = item(mangaTitle = "Renamed Title", tasteTitle = "Original Title")
        val result = RatedMangaSearchFilter.filter(listOf(target), "original", sourceNameOf = { null })
        assertEquals(listOf(target), result)
    }

    @Test
    fun `matches the resolved source name when the caller supplies one`() {
        val target = item(source = 42L, mangaTitle = "Some Manga")
        val result = RatedMangaSearchFilter.filter(
            listOf(target),
            "mangadex",
            sourceNameOf = { sourceId -> if (sourceId == 42L) "MangaDex" else null },
        )
        assertEquals(listOf(target), result)
    }

    @Test
    fun `a query matching nothing returns an empty list, distinct from an unfiltered result`() {
        val items = listOf(item(mangaTitle = "One Piece"))
        val result = RatedMangaSearchFilter.filter(items, "nonexistent query", sourceNameOf = { null })
        assertTrue(result.isEmpty())
    }

    @Test
    fun `query is trimmed and normalized - surrounding whitespace and case do not affect matching`() {
        val target = item(mangaTitle = "Fullmetal Alchemist")
        val result = RatedMangaSearchFilter.filter(listOf(target), "  FULLMETAL  ", sourceNameOf = { null })
        assertEquals(listOf(target), result)
    }

    @Test
    fun `filtering never mutates the input list or its items`() {
        val items = listOf(item(mangaTitle = "One Piece"), item(url = "/manga/y", mangaTitle = "Naruto"))
        val snapshot = items.toList()
        RatedMangaSearchFilter.filter(items, "one", sourceNameOf = { null })
        assertEquals(snapshot, items)
    }

    @Test
    fun `works identically regardless of which rating tier the items came from - the filter itself is rating-agnostic`() {
        // RatedMangaSearchFilter has no rating-specific branching; LovedDisplayItem doesn't even
        // carry the rating tier (LovedMangaScreenModel filters by rating before display items are
        // ever built) -- this documents that the filter behaves the same for Loved/Liked/Disliked.
        val loveItem = item(mangaTitle = "Loved Manga").copy(taste = taste(rating = 2))
        val dislikeItem = item(url = "/manga/y", mangaTitle = "Disliked Manga").copy(taste = taste(url = "/manga/y", rating = -1))
        assertEquals(listOf(loveItem), RatedMangaSearchFilter.filter(listOf(loveItem, dislikeItem), "loved", sourceNameOf = { null }))
        assertEquals(listOf(dislikeItem), RatedMangaSearchFilter.filter(listOf(loveItem, dislikeItem), "disliked", sourceNameOf = { null }))
    }
}
// KMK <--

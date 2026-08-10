package exh.recs.links

import exh.recs.TestInjektSupport
import exh.recs.loved.RatedMangaKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.model.CrossSourceMangaLink
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaTaste

/**
 * Tests for [LinkedVersionListBuilder].
 *
 * Run with: ./gradlew :app:testDebugUnitTest --tests "*.LinkedVersionListBuilderTest"
 */
class LinkedVersionListBuilderTest {

    companion object {
        // KMK v0.8.20: see TestInjektSupport — this test constructs favorite=true Manga instances.
        @JvmStatic
        @BeforeAll
        fun registerCustomMangaInfoBinding() = TestInjektSupport.ensureCustomMangaInfoBound()
    }

    private fun link(source: Long, url: String, title: String = "t-$url", updatedAt: Long = 0L) =
        CrossSourceMangaLink(source = source, url = url, groupId = "g", title = title, createdAt = 0L, updatedAt = updatedAt)

    private fun manga(id: Long, title: String, favorite: Boolean = false) =
        Manga.create().copy(id = id, ogTitle = title, favorite = favorite, source = 1L, url = "/m/$id")

    private fun taste(mangaId: Long, rating: MangaRating) =
        MangaTaste(mangaId = mangaId, source = 1L, url = "/m/$mangaId", title = "t", rating = rating.value, createdAt = 0L, updatedAt = 0L)

    @Test
    fun `installed source with resolved manga marks isInstalled true and exposes manga data`() {
        val members = listOf(
            LinkedVersionListBuilder.MemberInput(
                link = link(1, "/a"),
                sourceName = "MangaDex",
                lang = "en",
                manga = manga(10, "A", favorite = true),
                taste = taste(10, MangaRating.LOVE),
            ),
        )
        val rows = LinkedVersionListBuilder.build(members, primary = null)
        val row = rows.single()
        assertTrue(row.isInstalled)
        assertEquals("MangaDex", row.sourceName)
        assertEquals("A", row.title)
        assertTrue(row.isFavorite)
        assertEquals(MangaRating.LOVE, row.rating)
        assertEquals(10L, row.mangaId)
    }

    @Test
    fun `missing uninstalled source marks isInstalled false without crashing`() {
        val members = listOf(
            LinkedVersionListBuilder.MemberInput(
                link = link(2, "/b", title = "Fallback Title"),
                sourceName = null,
                lang = "",
                manga = null,
                taste = null,
            ),
        )
        val rows = LinkedVersionListBuilder.build(members, primary = null)
        val row = rows.single()
        assertFalse(row.isInstalled)
        assertNull(row.sourceName)
        assertNull(row.mangaId)
        // Falls back to the link's own title when no local manga row is resolvable.
        assertEquals("Fallback Title", row.title)
        assertNull(row.rating)
        assertFalse(row.isFavorite)
    }

    @Test
    fun `isPrimary is set only for the matching key`() {
        val members = listOf(
            LinkedVersionListBuilder.MemberInput(link(1, "/a"), "S1", "en", null, null),
            LinkedVersionListBuilder.MemberInput(link(2, "/b"), "S2", "en", null, null),
        )
        val rows = LinkedVersionListBuilder.build(members, primary = RatedMangaKey(2, "/b"))
        assertTrue(rows.first { it.key == RatedMangaKey(2, "/b") }.isPrimary)
        assertFalse(rows.first { it.key == RatedMangaKey(1, "/a") }.isPrimary)
    }

    @Test
    fun `primary row sorts first`() {
        val members = listOf(
            LinkedVersionListBuilder.MemberInput(link(1, "/a", updatedAt = 100L), "S1", "en", null, null),
            LinkedVersionListBuilder.MemberInput(link(2, "/b", updatedAt = 50L), "S2", "en", null, null),
        )
        val rows = LinkedVersionListBuilder.build(members, primary = RatedMangaKey(2, "/b"))
        assertEquals(RatedMangaKey(2, "/b"), rows.first().key)
    }
}

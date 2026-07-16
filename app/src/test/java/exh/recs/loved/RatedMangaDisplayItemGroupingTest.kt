package exh.recs.loved

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.model.MangaTaste

/**
 * Tests for the group-transparency fields ([LovedDisplayItem.confirmedGroupId],
 * [LovedDisplayItem.memberKeys], [LovedDisplayItem.hasConfirmedGroup]) added in v0.8.0 to
 * [buildGroupedItems] / [buildFlatItems], and specifically the plan's acceptance criterion that
 * "See Group Recommendations" (driven by [LovedDisplayItem.hasConfirmedGroup]) is visible only for
 * *confirmed* linked groups (user-verified cross-source links) with 2+ versions — never for
 * metadata-similarity groupings alone.
 *
 * Run with: ./gradlew :app:testDebugUnitTest --tests "*.RatedMangaDisplayItemGroupingTest"
 */
class RatedMangaDisplayItemGroupingTest {

    private fun entry(mangaId: Long, source: Long, url: String, title: String, rating: Int = 2): LovedMangaEntry {
        val taste = MangaTaste(mangaId, source, url, title, rating, 0L, 0L)
        val manga = Manga.create().copy(id = mangaId, ogTitle = title, source = source, url = url)
        return LovedMangaEntry(taste, manga)
    }

    @Test
    fun `two entries with a confirmed link group expose hasConfirmedGroup and both member keys`() {
        val a = entry(1, 1, "/a", "Alpha")
        val b = entry(2, 2, "/b", "Alpha Different Enough Title") // deliberately not title-similar
        val linkGroupByKey = mapOf("1|/a" to "group-1", "2|/b" to "group-1")

        val items = buildGroupedItems(listOf(a, b), linkGroupByKey, emptyMap())

        assertEquals(1, items.size)
        val item = items.single()
        assertTrue(item.hasConfirmedGroup)
        assertEquals("group-1", item.confirmedGroupId)
        assertEquals(setOf(RatedMangaKey(1, "/a"), RatedMangaKey(2, "/b")), item.memberKeys.toSet())
        assertEquals(2, item.versionCount)
    }

    @Test
    fun `a single unlinked entry is never a confirmed group`() {
        val a = entry(1, 1, "/a", "Solo")
        val items = buildGroupedItems(listOf(a), emptyMap(), emptyMap())
        val item = items.single()
        assertFalse(item.hasConfirmedGroup)
        assertNull(item.confirmedGroupId)
        assertEquals(1, item.versionCount)
    }

    @Test
    fun `metadata-similarity grouping without a confirmed link is NOT treated as a confirmed group`() {
        // Same title + same author, no link group — groups via LovedMangaDuplicateGrouper's
        // metadata-similarity tiers, which must NOT be exposed as hasConfirmedGroup (that flag is
        // reserved for user-verified LINK_GROUP identity, per the v0.8.0 plan's UX contract).
        val a = MangaTaste(1, 1, "/a", "Same Title", 2, 0L, 0L)
        val mangaA = Manga.create().copy(id = 1, ogTitle = "Same Title", ogAuthor = "Author X", source = 1, url = "/a")
        val b = MangaTaste(2, 2, "/b", "Same Title", 2, 0L, 0L)
        val mangaB = Manga.create().copy(id = 2, ogTitle = "Same Title", ogAuthor = "Author X", source = 2, url = "/b")

        val items = buildGroupedItems(
            listOf(LovedMangaEntry(a, mangaA), LovedMangaEntry(b, mangaB)),
            linkGroupByKey = emptyMap(),
            primaryByGroupId = emptyMap(),
        )

        assertEquals(1, items.size)
        val item = items.single()
        assertEquals(2, item.versionCount) // still grouped for display...
        assertFalse(item.hasConfirmedGroup) // ...but not a confirmed group for menu/action purposes
        assertNull(item.confirmedGroupId)
    }

    @Test
    fun `flat display still exposes hasConfirmedGroup for a confirmed link group with 2+ visible members`() {
        val a = entry(1, 1, "/a", "Alpha")
        val b = entry(2, 2, "/b", "Beta")
        val linkGroupByKey = mapOf("1|/a" to "group-1", "2|/b" to "group-1")

        val items = buildFlatItems(listOf(a, b), linkGroupByKey)

        assertEquals(2, items.size)
        assertTrue(items.all { it.hasConfirmedGroup })
        assertTrue(items.all { it.confirmedGroupId == "group-1" })
        // Flat display never shows the version badge, by existing (pre-v0.8.0) contract.
        assertTrue(items.all { it.versionCount == 1 })
    }

    @Test
    fun `flat display does not mark a lone linked member as a confirmed group`() {
        // Only one member of "group-1" is currently loaded in this rating tier/installed-source
        // set — a single visible member is not "2+ versions" and must not offer group actions.
        val a = entry(1, 1, "/a", "Alpha")
        val linkGroupByKey = mapOf("1|/a" to "group-1")

        val items = buildFlatItems(listOf(a), linkGroupByKey)

        assertFalse(items.single().hasConfirmedGroup)
    }
}

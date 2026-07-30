package exh.recs.loved

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.CrossSourceMangaLink

// KMK v0.8.11 -->
/**
 * Tests for [mergeLinkWritesIntoMap] -- the pure helper `LovedMangaScreenModel.mergeSelectedIntoGroup`/
 * `removeSelectedFromGroup`/`ungroup` use to reflect a group write immediately in `linkGroupByKey`
 * instead of waiting for an unrelated `getMangaTaste.subscribeAll()` reload (the confirmed root cause
 * of the reported "Group doesn't group the selection" bug -- see `LovedMangaScreenModel.kt`'s
 * class-level comment above `mergeSelectedIntoGroup()`).
 *
 * `LovedMangaScreenModel` itself cannot be constructed in this project's pure-JVM unit test
 * environment (Injekt-bootstrapped dependencies, no Robolectric), matching the same limitation
 * already documented for other Injekt-heavy screen models in `CURRENT_STATE.md` -- so this test
 * targets the extracted pure map-update logic directly.
 */
class LovedMangaScreenModelLinkWritesTest {

    private fun link(source: Long, url: String, groupId: String) =
        CrossSourceMangaLink(source = source, url = url, groupId = groupId, title = "t", createdAt = 0L, updatedAt = 0L)

    @Test
    fun `writes for a brand-new group are added to the map`() {
        val current = emptyMap<String, String>()
        val writes = listOf(link(1, "/a", "new-group"), link(2, "/b", "new-group"))

        val updated = mergeLinkWritesIntoMap(current, writes)

        assertEquals("new-group", updated["1|/a"])
        assertEquals("new-group", updated["2|/b"])
    }

    @Test
    fun `a write for a key that already had a different group overwrites it`() {
        val current = mapOf("1|/a" to "old-group")
        val writes = listOf(link(1, "/a", "new-group"))

        val updated = mergeLinkWritesIntoMap(current, writes)

        assertEquals("new-group", updated["1|/a"])
    }

    @Test
    fun `merging two existing groups reflects every folded member, not just the selected subset`() {
        // Mirrors RatedGroupMergePlannerTest's "merges two existing groups" scenario: group-b's
        // unselected member (/e) is still folded into group-a by the planner's own writes.
        val current = mapOf(
            "1|/a" to "group-b",
            "2|/c" to "group-a",
            "4|/d" to "group-a",
            "5|/e" to "group-b",
        )
        val writes = listOf(link(1, "/a", "group-a"), link(2, "/c", "group-a"), link(5, "/e", "group-a"))

        val updated = mergeLinkWritesIntoMap(current, writes)

        assertEquals("group-a", updated["1|/a"])
        assertEquals("group-a", updated["2|/c"])
        assertEquals("group-a", updated["5|/e"])
        assertEquals("group-a", updated["4|/d"], "unselected pre-existing target-group member is untouched, still correct")
    }

    @Test
    fun `an empty writes list leaves the map unchanged`() {
        val current = mapOf("1|/a" to "group-a")

        val updated = mergeLinkWritesIntoMap(current, emptyList())

        assertEquals(current, updated)
    }

    @Test
    fun `keys not touched by any write are preserved`() {
        val current = mapOf("1|/a" to "group-a", "9|/z" to "group-z")
        val writes = listOf(link(1, "/a", "group-a-renamed"))

        val updated = mergeLinkWritesIntoMap(current, writes)

        assertEquals("group-a-renamed", updated["1|/a"])
        assertEquals("group-z", updated["9|/z"])
    }
}
// KMK <--

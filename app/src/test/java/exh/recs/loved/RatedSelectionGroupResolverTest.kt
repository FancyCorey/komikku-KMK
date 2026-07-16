package exh.recs.loved

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.MangaTaste

// KMK v0.8.7 -->
class RatedSelectionGroupResolverTest {

    private fun item(source: Long, url: String, groupId: String?) = LovedDisplayItem(
        taste = MangaTaste(mangaId = source * 1000, source = source, url = url, title = url, rating = 1, createdAt = 0L, updatedAt = 0L),
        manga = null,
        versionCount = 1,
        confirmedGroupId = groupId,
        hasConfirmedGroup = groupId != null,
    )

    private val a = item(1L, "/a", "group-1")
    private val b = item(2L, "/b", "group-1")
    private val c = item(3L, "/c", "group-2")
    private val ungrouped = item(4L, "/d", null)
    private val items = listOf(a, b, c, ungrouped)

    @Test
    fun `empty selection resolves to no group`() {
        assertNull(RatedSelectionGroupResolver.resolveSingleGroup(items, emptySet()))
    }

    @Test
    fun `a single grouped item's selection resolves to its group`() {
        assertEquals("group-1", RatedSelectionGroupResolver.resolveSingleGroup(items, setOf(a.key)))
    }

    @Test
    fun `multiple items from the same group resolve to that group`() {
        assertEquals("group-1", RatedSelectionGroupResolver.resolveSingleGroup(items, setOf(a.key, b.key)))
    }

    @Test
    fun `items from two different groups conflict and resolve to no group`() {
        assertNull(RatedSelectionGroupResolver.resolveSingleGroup(items, setOf(a.key, c.key)))
    }

    @Test
    fun `an ungrouped item alone resolves to no group`() {
        assertNull(RatedSelectionGroupResolver.resolveSingleGroup(items, setOf(ungrouped.key)))
    }

    @Test
    fun `a grouped item mixed with an ungrouped item conflicts and resolves to no group`() {
        assertNull(RatedSelectionGroupResolver.resolveSingleGroup(items, setOf(a.key, ungrouped.key)))
    }

    @Test
    fun `a selected key with no matching item is treated as a conflict, not a crash`() {
        val staleKey = RatedMangaKey(99L, "/missing")
        assertNull(RatedSelectionGroupResolver.resolveSingleGroup(items, setOf(a.key, staleKey)))
    }
}
// KMK <--

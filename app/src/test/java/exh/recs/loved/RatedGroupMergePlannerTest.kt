package exh.recs.loved

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.CrossSourceMangaLink

/**
 * Tests for [RatedGroupMergePlanner].
 *
 * Run with: ./gradlew :app:testDebugUnitTest --tests "*.RatedGroupMergePlannerTest"
 */
class RatedGroupMergePlannerTest {

    private fun link(source: Long, url: String, groupId: String, title: String = "t-$url") =
        CrossSourceMangaLink(source = source, url = url, groupId = groupId, title = title, createdAt = 0L, updatedAt = 0L)

    @Test
    fun `fewer than 2 selected returns null`() {
        val selected = listOf(RatedGroupMergePlanner.SelectedEntry(RatedMangaKey(1, "/a"), "A", null))
        val plan = RatedGroupMergePlanner.plan(selected, emptyMap(), now = 1L) { "new" }
        assertNull(plan)
    }

    @Test
    fun `creates new group when none selected had one`() {
        val selected = listOf(
            RatedGroupMergePlanner.SelectedEntry(RatedMangaKey(1, "/a"), "A", null),
            RatedGroupMergePlanner.SelectedEntry(RatedMangaKey(2, "/b"), "B", null),
        )
        val plan = RatedGroupMergePlanner.plan(selected, emptyMap(), now = 100L) { "generated-group" }
        requireNotNull(plan)
        assertTrue(plan.isNewGroup)
        assertEquals("generated-group", plan.targetGroupId)
        assertEquals(2, plan.writes.size)
        assertTrue(plan.writes.all { it.groupId == "generated-group" })
        assertEquals(setOf("/a", "/b"), plan.writes.map { it.url }.toSet())
    }

    @Test
    fun `reuses the single existing group when exactly one selected entry has one`() {
        val selected = listOf(
            RatedGroupMergePlanner.SelectedEntry(RatedMangaKey(1, "/a"), "A", "group-1"),
            RatedGroupMergePlanner.SelectedEntry(RatedMangaKey(2, "/b"), "B", null),
        )
        val existingMembers = mapOf("group-1" to listOf(link(1, "/a", "group-1")))
        val plan = RatedGroupMergePlanner.plan(selected, existingMembers, now = 100L) { "unused" }
        requireNotNull(plan)
        assertEquals("group-1", plan.targetGroupId)
        assertTrue(plan.mergedGroupIds.isEmpty())
        assertEquals(2, plan.writes.size)
    }

    @Test
    fun `merges two existing groups into the lexicographically first target and folds every member of the merged-away group`() {
        val selected = listOf(
            RatedGroupMergePlanner.SelectedEntry(RatedMangaKey(1, "/a"), "A", "group-b"),
            RatedGroupMergePlanner.SelectedEntry(RatedMangaKey(2, "/c"), "C", "group-a"),
        )
        // group-b has an extra member (/e) that was NOT part of the manual selection — it must
        // still be folded into the target (every member of a merged-away group, not just the
        // selected subset), so the merge is genuine, not partial.
        val existingMembers = mapOf(
            "group-a" to listOf(link(2, "/c", "group-a"), link(4, "/d", "group-a")),
            "group-b" to listOf(link(1, "/a", "group-b"), link(5, "/e", "group-b")),
        )
        val plan = RatedGroupMergePlanner.plan(selected, existingMembers, now = 200L) { "unused" }
        requireNotNull(plan)
        assertEquals("group-a", plan.targetGroupId) // "group-a" < "group-b" lexicographically
        assertEquals(setOf("group-b"), plan.mergedGroupIds)
        // /a and /e come from the fully-folded merged-away group-b; /c is the selected target-group
        // member. /d (an unselected member already correctly in the target group) needs no rewrite.
        assertEquals(setOf("/a", "/c", "/e"), plan.writes.map { it.url }.toSet())
        assertTrue(plan.writes.all { it.groupId == "group-a" })
    }

    @Test
    fun `never merges by title — only manual selection and existing group ids decide grouping`() {
        // Two entries share an identical title but neither is selected together with the other,
        // and neither carries an existing group id equal to the other's — the planner must not
        // invent a shared group from the title alone.
        val selectedA = listOf(
            RatedGroupMergePlanner.SelectedEntry(RatedMangaKey(1, "/a"), "Same Title", null),
            RatedGroupMergePlanner.SelectedEntry(RatedMangaKey(2, "/b"), "Different Title", null),
        )
        val planA = RatedGroupMergePlanner.plan(selectedA, emptyMap(), now = 1L) { "group-x" }
        requireNotNull(planA)
        assertTrue(planA.writes.all { it.title != "Same Title" || it.url == "/a" })

        val selectedB = listOf(
            RatedGroupMergePlanner.SelectedEntry(RatedMangaKey(3, "/c"), "Same Title", null),
            RatedGroupMergePlanner.SelectedEntry(RatedMangaKey(4, "/d"), "Same Title", null),
        )
        val planB = RatedGroupMergePlanner.plan(selectedB, emptyMap(), now = 1L) { "group-y" }
        requireNotNull(planB)
        // These are grouped ONLY because they were both manually selected together in one call,
        // not because the planner scanned for matching titles elsewhere.
        assertEquals(setOf("group-y"), planB.writes.map { it.groupId }.toSet())
    }

    @Test
    fun `preserves existing title and createdAt when rewriting a folded member`() {
        val selected = listOf(
            RatedGroupMergePlanner.SelectedEntry(RatedMangaKey(1, "/a"), "Fallback A", "group-a"),
            RatedGroupMergePlanner.SelectedEntry(RatedMangaKey(2, "/b"), "Fallback B", "group-b"),
        )
        val existingMembers = mapOf(
            "group-a" to listOf(link(1, "/a", "group-a", title = "Original A").copy(createdAt = 42L)),
            "group-b" to listOf(link(2, "/b", "group-b", title = "Original B").copy(createdAt = 99L)),
        )
        val plan = RatedGroupMergePlanner.plan(selected, existingMembers, now = 500L) { "unused" }
        requireNotNull(plan)
        val a = plan.writes.first { it.url == "/a" }
        assertEquals("Original A", a.title)
        assertEquals(42L, a.createdAt)
    }
}

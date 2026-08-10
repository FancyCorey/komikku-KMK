package exh.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.CrossSourceGroupPrimary
import tachiyomi.domain.taste.model.CrossSourceMangaLink

// KMK v0.8.20 -->
/** Pure-logic coverage for [groupUndoLinkConflicts]/[groupUndoPrimaryConflicts]. Real DB-touching restore is covered in [GroupUndoServiceRestoreTest]. */
class GroupUndoServiceTest {

    private fun link(groupId: String) = CrossSourceMangaLink(source = 1L, url = "/m/1", groupId = groupId, title = "t", createdAt = 0L, updatedAt = 0L)
    private fun primary(source: Long) = CrossSourceGroupPrimary(groupId = "g1", source = source, url = "/m/$source", updatedAt = 0L)

    @Test
    fun `no conflict when current link groupId still matches the expected post-action groupId`() {
        val expected = GroupLinkSnapshot.from(link("g1"))
        assertFalse(groupUndoLinkConflicts(expected, link("g1")))
    }

    @Test
    fun `conflict when the link's groupId changed since the action was journaled`() {
        val expected = GroupLinkSnapshot.from(link("g1"))
        assertTrue(groupUndoLinkConflicts(expected, link("g2")))
    }

    @Test
    fun `conflict when the expected link row no longer exists (was deleted)`() {
        val expected = GroupLinkSnapshot.from(link("g1"))
        assertTrue(groupUndoLinkConflicts(expected, null))
    }

    @Test
    fun `no conflict when both expected and current link rows are absent`() {
        assertFalse(groupUndoLinkConflicts(null, null))
    }

    @Test
    fun `no conflict when current primary still matches the expected post-action primary`() {
        val expected = GroupPrimarySnapshot.from(primary(1L))
        assertFalse(groupUndoPrimaryConflicts(expected, primary(1L)))
    }

    @Test
    fun `conflict when the primary now points at a different manga`() {
        val expected = GroupPrimarySnapshot.from(primary(1L))
        assertTrue(groupUndoPrimaryConflicts(expected, primary(2L)))
    }

    @Test
    fun `conflict when the expected primary was cleared since journaling`() {
        val expected = GroupPrimarySnapshot.from(primary(1L))
        assertTrue(groupUndoPrimaryConflicts(expected, null))
    }
}
// KMK <--

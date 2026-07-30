package exh.recs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK v0.8.16 -->
class KmkRecsReleaseNotesGroupingPolicyTest {

    private val sample = """
        ## KMK-Recs v0.8.16

        Newest.

        ## KMK-Recs v0.8.15-fix1

        Second newest.

        ## KMK-Recs v0.7.9

        Older.

        ## KMK-Recs v0.6.19

        Oldest.
    """.trimIndent()

    @Test
    fun `family extraction collapses patch and fix suffixes into the same family`() {
        assertEquals("v0.8", KmkRecsReleaseNotesGroupingPolicy.familyOf("v0.8.16"))
        assertEquals("v0.8", KmkRecsReleaseNotesGroupingPolicy.familyOf("v0.8.15-fix1"))
        assertEquals("v0.7", KmkRecsReleaseNotesGroupingPolicy.familyOf("v0.7.9"))
    }

    @Test
    fun `every heading from the real changelog appears in exactly one group`() {
        val headings = Regex("(?m)^\\s*## KMK-Recs (v\\S+)\\s*$")
            .findAll(KmkRecsReleaseNotes.MARKDOWN)
            .map { it.groupValues[1] }
            .toList()
        val groups = KmkRecsReleaseNotesGroupingPolicy.group(KmkRecsReleaseNotes.MARKDOWN)
        val grouped = groups.flatMap { it.sections.map { s -> s.version } }
        assertEquals(headings.size, grouped.size, "no historical version should be dropped or duplicated")
        assertEquals(headings.toSet(), grouped.toSet())
    }

    @Test
    fun `newest heading is in the default-expanded group`() {
        val groups = KmkRecsReleaseNotesGroupingPolicy.group(sample)
        val newestGroup = groups.first { it.sections.any { s -> s.version == "v0.8.16" } }
        assertTrue(newestGroup.expandedByDefault)
    }

    @Test
    fun `older families are marked collapsed by default`() {
        val groups = KmkRecsReleaseNotesGroupingPolicy.group(sample)
        val olderGroups = groups.filterNot { it.expandedByDefault }
        assertTrue(olderGroups.isNotEmpty())
        olderGroups.forEach { assertFalse(it.expandedByDefault) }
    }

    @Test
    fun `no private or public wording appears in collapsed summaries`() {
        val groups = KmkRecsReleaseNotesGroupingPolicy.group(KmkRecsReleaseNotes.MARKDOWN)
        groups.forEach { group ->
            val lower = group.summary.lowercase()
            assertFalse(lower.contains("private"))
            assertFalse(lower.contains("public build"))
            assertFalse(lower.contains("internal build"))
        }
    }

    @Test
    fun `v0_8 and v0_7 families group multiple sections without losing any`() {
        val groups = KmkRecsReleaseNotesGroupingPolicy.group(sample)
        val v08 = groups.first { it.family == "v0.8" }
        assertEquals(2, v08.sections.size)
        assertEquals(listOf("v0.8.16", "v0.8.15-fix1"), v08.sections.map { it.version })
    }
}
// KMK <--

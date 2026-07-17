package exh.recs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK v0.8.9 -->
/**
 * Tests for [KmkRecsReleaseNotes]. `MARKDOWN` remains a plain string (not a structured model) — see
 * the v0.8.9 implementation report and the in-code comment on `KmkRecsReleaseNotes.MARKDOWN` for why
 * that was the safer choice given `WhatsNewScreen`'s existing `MarkdownRender`/`GFMFlavourDescriptor`
 * renderer already fully supports the required hierarchy. These tests parse the same heading pattern
 * the renderer treats as a version boundary (`## KMK-Recs vX.Y.Z`) so regressions in history
 * ordering/completeness/duplication are still caught mechanically, without a structured data model.
 */
class KmkRecsReleaseNotesTest {

    private val headingRegex = Regex("(?m)^\\s*## KMK-Recs (v\\S+)\\s*$")

    private fun headings(): List<String> = headingRegex.findAll(KmkRecsReleaseNotes.MARKDOWN).map { it.groupValues[1] }.toList()

    @Test
    fun `the current VERSION_NAME appears as the first (newest) heading`() {
        val first = headings().first()
        assertEquals(KmkRecsReleaseNotes.VERSION_NAME.removePrefix("KMK-Recs "), first)
    }

    @Test
    fun `no duplicate version headings`() {
        val all = headings()
        assertEquals(all.size, all.toSet().size, "duplicate version heading(s) found: ${all.groupingBy { it }.eachCount().filterValues { it > 1 }}")
    }

    @Test
    fun `every v0_8_x version from v0_8_0 through the previous release is present`() {
        val all = headings().toSet()
        val expected = listOf(
            "v0.8.0", "v0.8.1-fix1", "v0.8.1-fix2", "v0.8.1-fix3", "v0.8.1-fix4",
            "v0.8.2", "v0.8.3", "v0.8.4", "v0.8.5", "v0.8.6", "v0.8.7", "v0.8.8",
        )
        val missing = expected.filterNot { it in all }
        assertTrue(missing.isEmpty(), "missing historical v0.8.x entries: $missing")
    }

    @Test
    fun `history is substantial - no accidental truncation of older entries`() {
        // Regression guard: as of the v0.8.10 Phase G audit this file has 84 entries going back to
        // v0.4.2 (confirmed by counting real "## KMK-Recs vX.Y.Z" headings, not the v0.8.10 plan's
        // stated "76"). A truncation bug (e.g. an accidentally-closed triple-quoted string) would
        // silently drop most of them; the >= bound intentionally still passes as future versions add
        // more entries, without needing to be bumped every release.
        assertTrue(headings().size >= 84, "expected at least 84 historical entries, found ${headings().size}")
    }

    @Test
    fun `headings are in strictly descending chronological order as written (newest-first)`() {
        // The renderer relies on source order for "newest first" -- verify the file wasn't
        // accidentally reordered. v0.8.9 is expected to be exactly first.
        val all = headings()
        assertEquals("v0.8.9", all[0])
        assertEquals("v0.8.8", all[1])
        assertEquals("v0.8.7", all[2])
    }

    @Test
    fun `the new v0_8_9 entry uses the official What's Changed structure`() {
        val v089Section = KmkRecsReleaseNotes.MARKDOWN.substringAfter("## KMK-Recs v0.8.9").substringBefore("## KMK-Recs v0.8.8")
        assertTrue(v089Section.contains("#### What's Changed"))
        assertTrue(v089Section.contains("##### New"))
        assertTrue(v089Section.contains("##### Improve"))
    }

    @Test
    fun `the v0_8_9 entry omits an empty Fix heading rather than rendering a blank section`() {
        val v089Section = KmkRecsReleaseNotes.MARKDOWN.substringAfter("## KMK-Recs v0.8.9").substringBefore("## KMK-Recs v0.8.8")
        assertFalse(v089Section.contains("##### Fix"))
    }

    @Test
    fun `no internal build-channel terminology appears anywhere in the rendered changelog`() {
        val lower = KmkRecsReleaseNotes.MARKDOWN.lowercase()
        val forbidden = listOf("private build", "public build", "development artifact", "internal build")
        forbidden.forEach { term ->
            assertFalse(lower.contains(term), "forbidden build-channel term found: \"$term\"")
        }
    }
}
// KMK <--

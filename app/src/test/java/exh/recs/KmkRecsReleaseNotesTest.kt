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

    private fun headingMatches(): List<MatchResult> = headingRegex.findAll(KmkRecsReleaseNotes.MARKDOWN).toList()

    private fun headings(): List<String> = headingMatches().map { it.groupValues[1] }

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
            "v0.8.2", "v0.8.3", "v0.8.4", "v0.8.5", "v0.8.6", "v0.8.7", "v0.8.8", "v0.8.9",
        )
        val missing = expected.filterNot { it in all }
        assertTrue(missing.isEmpty(), "missing historical v0.8.x entries: $missing")
    }

    // KMK v0.8.10-fix9 -->
    @Test
    fun `known historical versions across the full history still exist after the changelog conversion`() {
        val all = headings().toSet()
        val expected = listOf(
            "v0.8.11", "v0.8.10-fix9", "v0.8.10", "v0.8.9", "v0.8.8", "v0.8.0",
            "v0.7.47", "v0.7.45", "v0.7.0", "v0.6.8", "v0.5.0", "v0.4.2",
        )
        val missing = expected.filterNot { it in all }
        assertTrue(missing.isEmpty(), "missing historical entries: $missing")
    }

    private fun sectionBodies(): List<Pair<String, String>> {
        val matches = headingMatches()
        return matches.mapIndexed { i, m ->
            val start = m.range.first
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else KmkRecsReleaseNotes.MARKDOWN.length
            m.groupValues[1] to KmkRecsReleaseNotes.MARKDOWN.substring(start, end)
        }
    }

    @Test
    fun `every version section contains a What's Changed heading`() {
        val missing = sectionBodies().filterNot { (_, body) -> body.contains("#### What's Changed") }.map { it.first }
        assertTrue(missing.isEmpty(), "sections missing '#### What's Changed': $missing")
    }

    @Test
    fun `every version section contains at least one New, Improve, or Fix category heading`() {
        val categoryRegex = Regex("##### (New|Improve|Fix)")
        val missing = sectionBodies().filterNot { (_, body) -> categoryRegex.containsMatchIn(body) }.map { it.first }
        assertTrue(missing.isEmpty(), "sections missing a category heading: $missing")
    }

    @Test
    fun `no version section has a flat bullet between its summary and What's Changed`() {
        val offenders = sectionBodies().filter { (_, body) ->
            val pre = body.substringBefore("#### What's Changed")
            Regex("(?m)^\\s*- ").containsMatchIn(pre)
        }.map { it.first }
        assertTrue(offenders.isEmpty(), "sections with a flat bullet before '#### What's Changed': $offenders")
    }

    @Test
    fun `the rendered changelog no longer contains stale wording about historical formatting decisions`() {
        val stale = listOf(
            "preserved exactly as it was written",
            "flat-bullet format",
            "reconfirmed this decision",
        )
        stale.forEach { phrase ->
            assertFalse(
                KmkRecsReleaseNotes.MARKDOWN.contains(phrase),
                "stale phrase still present in rendered changelog: \"$phrase\"",
            )
        }
    }
    // KMK <--

    @Test
    fun `history is substantial - no accidental truncation of older entries`() {
        // Regression guard: as of v0.8.11 this file has 87 entries going back to v0.4.2 (confirmed
        // by counting real "## KMK-Recs vX.Y.Z" headings). A truncation bug (e.g. an accidentally-
        // closed triple-quoted string) would silently drop most of them; the >= bound intentionally
        // still passes as future versions add more entries, without needing to be bumped every
        // release.
        assertTrue(headings().size >= 87, "expected at least 87 historical entries, found ${headings().size}")
    }

    @Test
    fun `headings are in strictly descending chronological order as written (newest-first)`() {
        // The renderer relies on source order for "newest first" -- verify the file wasn't
        // accidentally reordered. v0.8.20-fix5 is expected to be exactly first.
        val all = headings()
        assertEquals("v0.8.20-fix5", all[0])
        assertEquals("v0.8.20-fix4", all[1])
        assertEquals("v0.8.20-fix3", all[2])
        assertEquals("v0.8.20-fix2", all[3])
        assertEquals("v0.8.20-fix1", all[4])
        assertEquals("v0.8.20", all[5])
        assertEquals("v0.8.19", all[6])
        assertEquals("v0.8.18-fix1", all[7])
        assertEquals("v0.8.18", all[8])
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
    fun `the new v0_8_10 entry uses the official What's Changed structure with all three sub-headings`() {
        val v0810Section = KmkRecsReleaseNotes.MARKDOWN.substringAfter("## KMK-Recs v0.8.10").substringBefore("## KMK-Recs v0.8.9")
        assertTrue(v0810Section.contains("#### What's Changed"))
        assertTrue(v0810Section.contains("##### New"))
        assertTrue(v0810Section.contains("##### Improve"))
        assertTrue(v0810Section.contains("##### Fix"))
    }

    @Test
    fun `no internal build-channel terminology appears anywhere in the rendered changelog`() {
        val lower = KmkRecsReleaseNotes.MARKDOWN.lowercase()
        val forbidden = listOf("private build", "public build", "development artifact", "internal build")
        forbidden.forEach { term ->
            assertFalse(lower.contains(term), "forbidden build-channel term found: \"$term\"")
        }
    }

    // KMK v0.8.16: Find best version was moved out of the taste/rating dropdown into its own visible
    // manga action. Historical entries (e.g. v0.7.8, v0.6.20) legitimately used "rating menu" wording
    // for their own era and must not be rewritten -- only the current (newest) entry must not describe
    // the *current* placement that way.
    @Test
    fun `the current newest entry never describes Find best version as part of a rating menu`() {
        val sections = sectionBodies()
        val currentSection = sections.first().second.lowercase()
        assertFalse(currentSection.contains("rating menu"), "current changelog entry should not describe Find best version as inside a rating menu")
    }
}
// KMK <--

package exh.recs.matching

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SameMangaIdentityEvidencePolicyTest {

    @Test
    fun `same record key is exact identity`() {
        val assessment = assess(origin(), origin())

        assertEquals(SameMangaIdentityDecision.EXACT, assessment.decision)
        assertTrue(SameMangaIdentityReason.RECORD_KEY_EQUAL in assessment.reasons)
    }

    @Test
    fun `canonical title and author agreement is likely but not exact across sources`() {
        val assessment = assess(
            origin(title = "Caf\u00e9", author = "Author"),
            candidate(title = "Cafe\u0301", author = "AUTHOR"),
        )

        assertEquals(SameMangaIdentityDecision.LIKELY, assessment.decision)
        assertTrue(SameMangaIdentityReason.TITLE_EXACT_CANONICAL in assessment.reasons)
        assertTrue(SameMangaIdentityReason.CONTRIBUTOR_EXACT_AUTHOR in assessment.reasons)
    }

    @Test
    fun `compatibility-only title remains uncertain even with author`() {
        val assessment = assess(
            origin(title = "\uff21\uff22\uff23", author = "Author"),
            candidate(title = "ABC", author = "Author"),
        )

        assertEquals(SameMangaIdentityDecision.UNCERTAIN, assessment.decision)
        assertTrue(SameMangaIdentityReason.TITLE_EXACT_COMPATIBILITY in assessment.reasons)
    }

    @Test
    fun `title-only agreement remains uncertain`() {
        val assessment = assess(origin(title = "Shared Title"), candidate(title = "Shared Title"))

        assertEquals(SameMangaIdentityDecision.UNCERTAIN, assessment.decision)
        assertTrue(SameMangaIdentityReason.CONTRIBUTOR_MISSING in assessment.reasons)
    }

    @Test
    fun `common prefix with no corroboration remains uncertain`() {
        val assessment = assess(
            origin(title = "Legend of the Hero North"),
            candidate(title = "Legend of the Hero South"),
        )

        assertEquals(SameMangaIdentityDecision.UNCERTAIN, assessment.decision)
    }

    @Test
    fun `original title or alias can supply canonical agreement`() {
        val assessment = assess(
            origin(title = "Translated Name", original = "Original Name", aliases = listOf("\u539f\u4f5c"), author = "A"),
            candidate(title = "\u539f\u4f5c", author = "A"),
        )

        assertEquals(SameMangaIdentityDecision.LIKELY, assessment.decision)
        assertTrue(SameMangaIdentityReason.TITLE_EXACT_CANONICAL in assessment.reasons)
    }

    @Test
    fun `same title with disjoint contributors is conflict`() {
        val assessment = assess(
            origin(title = "Shared", author = "Author A"),
            candidate(title = "Shared", author = "Author B"),
        )

        assertEquals(SameMangaIdentityDecision.CONFLICT, assessment.decision)
        assertTrue(SameMangaIdentityReason.CONTRIBUTOR_CONFLICT in assessment.reasons)
    }

    @Test
    fun `matching artist can corroborate when authors are absent`() {
        val assessment = assess(
            origin(title = "Shared", artist = "Artist"),
            candidate(title = "Shared", artist = "Artist"),
        )

        assertEquals(SameMangaIdentityDecision.LIKELY, assessment.decision)
        assertTrue(SameMangaIdentityReason.CONTRIBUTOR_EXACT_ARTIST in assessment.reasons)
    }

    @Test
    fun `matching artist prevents conflict when author metadata differs`() {
        val assessment = assess(
            origin(title = "Shared", author = "A", artist = "Common"),
            candidate(title = "Shared", author = "B", artist = "Common"),
        )

        assertEquals(SameMangaIdentityDecision.LIKELY, assessment.decision)
        assertFalse(SameMangaIdentityReason.CONTRIBUTOR_CONFLICT in assessment.reasons)
    }

    @Test
    fun `different season markers are conflict even when base title and author match`() {
        val assessment = assess(
            origin(title = "Series Season 1", author = "A"),
            candidate(title = "Series Season 2", author = "A"),
        )

        assertEquals(SameMangaIdentityDecision.CONFLICT, assessment.decision)
        assertTrue(SameMangaIdentityReason.PART_MARKER_CONFLICT in assessment.reasons)
    }

    @Test
    fun `roman and decimal part markers agree`() {
        val assessment = assess(
            origin(title = "Series Part II", author = "A"),
            candidate(title = "Series Part 2", author = "A"),
        )

        assertEquals(SameMangaIdentityDecision.LIKELY, assessment.decision)
        assertTrue(SameMangaIdentityReason.PART_MARKER_AGREEMENT in assessment.reasons)
    }

    @Test
    fun `genre and status support do not promote title-only evidence`() {
        val assessment = assess(
            origin(title = "Shared", genres = listOf("Action", "Fantasy"), status = 1L),
            candidate(title = "Shared", genres = listOf("action", "Drama"), status = 1L),
        )

        assertEquals(SameMangaIdentityDecision.UNCERTAIN, assessment.decision)
        assertEquals(1, assessment.sharedGenreCount)
        assertTrue(SameMangaIdentityReason.GENRE_SUPPORT in assessment.reasons)
        assertTrue(SameMangaIdentityReason.STATUS_AGREEMENT in assessment.reasons)
    }

    @Test
    fun `exact chapter sets produce exact alignment evidence only`() {
        val assessment = assess(
            origin(chapters = listOf(1.0, 2.0, 3.0)),
            candidate(chapters = listOf(1.0, 2.0, 3.0)),
        )

        assertTrue(SameMangaIdentityReason.CHAPTER_EXACT_ALIGNMENT in assessment.reasons)
        assertEquals(0.0, assessment.chapterOffset)
        assertEquals(SameMangaIdentityDecision.UNCERTAIN, assessment.decision)
    }

    @Test
    fun `stable chapter shift is reported without becoming identity proof`() {
        val assessment = assess(
            origin(chapters = listOf(1.0, 2.0, 3.0)),
            candidate(chapters = listOf(2.0, 3.0, 4.0)),
        )

        assertTrue(SameMangaIdentityReason.CHAPTER_SHIFTED_ALIGNMENT in assessment.reasons)
        assertEquals(1.0, assessment.chapterOffset)
        assertEquals(SameMangaIdentityDecision.UNCERTAIN, assessment.decision)
    }

    @Test
    fun `chapter gaps report conflict without becoming global identity conflict`() {
        val assessment = assess(
            origin(chapters = listOf(1.0, 2.0, 4.0)),
            candidate(chapters = listOf(1.0, 3.0, 4.0)),
        )

        assertTrue(SameMangaIdentityReason.CHAPTER_CONFLICT in assessment.reasons)
        assertEquals(SameMangaIdentityDecision.UNCERTAIN, assessment.decision)
        assertNull(assessment.chapterOffset)
    }

    @Test
    fun `duplicate and nonfinite chapter values are ignored deterministically`() {
        val assessment = assess(
            origin(chapters = listOf(Double.NaN, -1.0, 1.0, 2.0, 2.0)),
            candidate(chapters = listOf(1.0, 2.0, Double.POSITIVE_INFINITY)),
        )

        assertTrue(SameMangaIdentityReason.CHAPTER_EXACT_ALIGNMENT in assessment.reasons)
    }

    @Test
    fun `missing chapter data is explicit`() {
        val assessment = assess(origin(chapters = emptyList()), candidate(chapters = listOf(1.0)))

        assertTrue(SameMangaIdentityReason.CHAPTER_MISSING in assessment.reasons)
    }

    @Test
    fun `user rejection overrides positive evidence`() {
        val assessment = assess(
            origin(title = "Shared", author = "A"),
            candidate(title = "Shared", author = "A", userDecision = SameMangaUserDecision.REJECTED),
        )

        assertEquals(SameMangaIdentityDecision.REJECTED, assessment.decision)
        assertTrue(SameMangaIdentityReason.USER_REJECTED in assessment.reasons)
    }

    @Test
    fun `explicit user confirmation is exact while retaining conflicts for review`() {
        val assessment = assess(
            origin(title = "Series Season 1", author = "A"),
            candidate(
                title = "Series Season 2",
                author = "B",
                userDecision = SameMangaUserDecision.CONFIRMED,
            ),
        )

        assertEquals(SameMangaIdentityDecision.EXACT, assessment.decision)
        assertTrue(SameMangaIdentityReason.USER_CONFIRMED in assessment.reasons)
        assertTrue(SameMangaIdentityReason.PART_MARKER_CONFLICT in assessment.reasons)
    }

    @Test
    fun `reasons are stable enum order without duplicates`() {
        val assessment = assess(
            origin(title = "Series Part 1", author = "A", genres = listOf("Action"), status = 1L),
            candidate(title = "Series Part 1", author = "A", genres = listOf("Action"), status = 1L),
        )

        assertEquals(assessment.reasons.distinct(), assessment.reasons)
        assertEquals(assessment.reasons.sortedBy(SameMangaIdentityReason::ordinal), assessment.reasons)
        assertEquals(IdentityDecisionVersion(1), assessment.version)
    }

    @Test
    fun `assessment output does not retain raw private metadata`() {
        val rawValues = listOf("Private Title", "Private Author", "Private Artist", "/private/url")
        val assessment = assess(
            origin(title = rawValues[0], author = rawValues[1], artist = rawValues[2]),
            candidate(title = rawValues[0], author = rawValues[1], artist = rawValues[2], url = rawValues[3]),
        )
        val fields = SameMangaIdentityAssessment::class.java.declaredFields
        val rendered = assessment.toString()

        assertTrue(fields.none { it.type == String::class.java })
        assertTrue(fields.none { it.name.lowercase() in setOf("rawtitle", "author", "artist", "url", "description") })
        assertTrue(rawValues.none(rendered::contains))
    }

    private fun assess(
        origin: SameMangaIdentitySnapshot,
        candidate: SameMangaIdentitySnapshot,
    ) = SameMangaIdentityEvidencePolicy.assess(origin, candidate)

    private fun origin(
        title: String = "Origin",
        original: String = title,
        aliases: List<String> = emptyList(),
        author: String? = null,
        artist: String? = null,
        status: Long? = null,
        genres: List<String> = emptyList(),
        chapters: List<Double> = emptyList(),
    ) = SameMangaIdentitySnapshot(
        source = 1L,
        url = "/origin",
        displayTitle = title,
        originalTitle = original,
        aliases = aliases,
        author = author,
        artist = artist,
        status = status,
        genres = genres,
        chapterNumbers = chapters,
    )

    private fun candidate(
        title: String = "Candidate",
        original: String = title,
        aliases: List<String> = emptyList(),
        author: String? = null,
        artist: String? = null,
        status: Long? = null,
        genres: List<String> = emptyList(),
        chapters: List<Double> = emptyList(),
        url: String = "/candidate",
        userDecision: SameMangaUserDecision = SameMangaUserDecision.NONE,
    ) = SameMangaIdentitySnapshot(
        source = 2L,
        url = url,
        displayTitle = title,
        originalTitle = original,
        aliases = aliases,
        author = author,
        artist = artist,
        status = status,
        genres = genres,
        chapterNumbers = chapters,
        userDecision = userDecision,
    )
}

package exh.recs.matching

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga

class SameMangaCandidateEvidenceAttacherTest {

    @Test
    fun `clearly conflicting live candidate is not plausible for contextual search`() {
        assertFalse(
            SameMangaCandidateEvidenceAttacher.isPlausible(
                origin = manga(1, "/origin", "Resurrection Boy"),
                candidate = manga(2, "/candidate", "Cairo"),
            ),
        )
    }

    @Test
    fun `title-compatible live candidate remains selectable for contextual search`() {
        assertTrue(
            SameMangaCandidateEvidenceAttacher.isPlausible(
                origin = manga(1, "/origin", "Resurrection Boy"),
                candidate = manga(2, "/candidate", "Resurrection Boy"),
            ),
        )
    }

    @Test
    fun `confirmed title-different candidate remains selectable for contextual search`() {
        val candidate = manga(2, "/candidate", "Fixture Pair A")

        val success = SameMangaCandidateEvidenceAttacher.attach(
            origin = manga(1, "/origin", "Fixture Pair B"),
            candidates = listOf(candidate),
            confirmedKeys = setOf(MangaIdentityKey(candidate.source, candidate.url)),
        )

        assertEquals(listOf(candidate), success.results)
    }

    @Test
    fun `only plausible candidates receive evidence under their record keys`() {
        val first = manga(2, "/first", "Shared", author = "Author")
        val second = manga(3, "/second", "Different")

        val success = SameMangaCandidateEvidenceAttacher.attach(
            origin = manga(1, "/origin", "Shared", author = "Author"),
            candidates = listOf(first, second),
        )

        assertEquals(
            setOf(MangaIdentityKey(2, "/first")),
            success.evidence.keys,
        )
        assertEquals(success.results.size, success.evidence.size)
    }

    @Test
    fun `decision rank orders likely before uncertain and excludes conflicts`() {
        val likely = manga(2, "/likely", "Shared", author = "Author")
        val uncertain = manga(3, "/uncertain", "Shared")
        val conflict = manga(4, "/conflict", "Shared", author = "Different")

        val success = SameMangaCandidateEvidenceAttacher.attach(
            origin = manga(1, "/origin", "Shared", author = "Author"),
            candidates = listOf(conflict, uncertain, likely),
        )

        assertEquals(listOf("/likely", "/uncertain"), success.results.map(Manga::url))
        assertEquals(
            listOf(
                SameMangaIdentityDecision.LIKELY,
                SameMangaIdentityDecision.UNCERTAIN,
            ),
            success.results.map { success.evidence.getValue(MangaIdentityKey(it.source, it.url)).decision },
        )
    }

    @Test
    fun `evidence strength orders candidates within one decision`() {
        val weaker = manga(2, "/weaker", "Shared Extra")
        val stronger = manga(3, "/stronger", "Shared")

        val success = SameMangaCandidateEvidenceAttacher.attach(
            origin = manga(1, "/origin", "Shared", genres = listOf("Action")),
            candidates = listOf(weaker, stronger),
        )

        assertEquals(listOf("/stronger"), success.results.map(Manga::url))
        assertTrue(success.evidence.values.all { it.decision != SameMangaIdentityDecision.CONFLICT })
    }

    @Test
    fun `stable input order breaks complete evidence ties`() {
        val first = manga(2, "/first", "Shared")
        val second = manga(3, "/second", "Shared")

        val success = SameMangaCandidateEvidenceAttacher.attach(
            origin = manga(1, "/origin", "Shared"),
            candidates = listOf(second, first),
        )

        assertEquals(listOf("/second", "/first"), success.results.map(Manga::url))
        assertEquals(success.results.map { MangaIdentityKey(it.source, it.url) }, success.evidence.keys.toList())
    }

    @Test
    fun `duplicate record keys are retained once with aligned evidence`() {
        val first = manga(2, "/same", "Shared")
        val duplicate = manga(2, "/same", "Changed")

        val success = SameMangaCandidateEvidenceAttacher.attach(
            origin = manga(1, "/origin", "Shared"),
            candidates = listOf(first, duplicate),
        )

        assertEquals(listOf(first), success.results)
        assertEquals(1, success.evidence.size)
    }

    @Test
    fun `assessment values retain no raw candidate metadata`() {
        val raw = listOf("Private Title", "Private Author", "Private Artist", "/private/url")
        val success = SameMangaCandidateEvidenceAttacher.attach(
            origin = manga(1, "/origin", raw[0], author = raw[1], artist = raw[2]),
            candidates = listOf(manga(2, raw[3], raw[0], author = raw[1], artist = raw[2])),
        )
        val assessment = success.evidence.values.single()
        val rendered = assessment.toString()

        assertTrue(raw.none(rendered::contains))
        assertFalse(SameMangaIdentityReason.CHAPTER_EXACT_ALIGNMENT in assessment.reasons)
        assertTrue(SameMangaIdentityReason.CHAPTER_MISSING in assessment.reasons)
    }

    private fun manga(
        source: Long,
        url: String,
        title: String,
        author: String? = null,
        artist: String? = null,
        genres: List<String> = emptyList(),
    ) = Manga.create().copy(
        source = source,
        url = url,
        ogTitle = title,
        ogAuthor = author,
        ogArtist = artist,
        ogGenre = genres,
    )
}

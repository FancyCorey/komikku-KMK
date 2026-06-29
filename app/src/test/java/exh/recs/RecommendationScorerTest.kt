package exh.recs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga

class RecommendationScorerTest {

    @Test
    fun `exact title and identical tags score highest`() {
        val source = manga("My Hero Academia", listOf("Action", "Shounen", "Superpower"))
        val perfect = manga("My Hero Academia", listOf("Action", "Shounen", "Superpower"))
        val score = RecommendationScorer.score(source, perfect)
        assertEquals(1.0, score, 0.001)
    }

    @Test
    fun `tag overlap raises score above title-only match`() {
        val source = manga("Naruto", listOf("Action", "Adventure", "Ninja"))
        val withTags = manga("Boruto", listOf("Action", "Adventure", "Ninja"))
        val noTags = manga("Boruto", emptyList())

        val taggedScore = RecommendationScorer.score(source, withTags)
        val untaggedScore = RecommendationScorer.score(source, noTags)

        assertTrue(taggedScore > untaggedScore) {
            "Tagged candidate ($taggedScore) should outscore untagged candidate ($untaggedScore)"
        }
    }

    @Test
    fun `title-only fallback is used when candidate has no tags`() {
        val source = manga("One Piece", listOf("Action", "Adventure", "Pirates"))
        val noTags = manga("One Piece", emptyList())

        val score = RecommendationScorer.score(source, noTags)

        // Falls back to pure title similarity, which is 1.0 for identical titles
        assertEquals(1.0, score, 0.001)
    }

    @Test
    fun `title-only fallback is used when source has no tags`() {
        val source = manga("One Piece", emptyList())
        val withTags = manga("One Piece", listOf("Action", "Adventure"))

        val score = RecommendationScorer.score(source, withTags)

        assertEquals(1.0, score, 0.001)
    }

    @Test
    fun `completely different title and no shared tags scores near zero`() {
        val source = manga("Romantic Comedy ABC", listOf("Romance", "Comedy", "School"))
        val unrelated = manga("Mech Battle XYZ", listOf("Sci-Fi", "Mecha", "Action"))

        val score = RecommendationScorer.score(source, unrelated)

        assertTrue(score < 0.2) { "Unrelated manga should score low, got $score" }
    }

    @Test
    fun `case and punctuation differences are normalized`() {
        val source = manga("attack on titan", listOf("action", "dark fantasy"))
        val candidate = manga("Attack on Titan", listOf("Action", "Dark Fantasy"))

        val score = RecommendationScorer.score(source, candidate)
        assertEquals(1.0, score, 0.001)
    }

    @Test
    fun `partial tag overlap scores between zero and full match`() {
        val source = manga("Manga A", listOf("Action", "Romance", "Comedy", "School"))
        val halfOverlap = manga("Manga A", listOf("Action", "Romance", "Thriller", "Horror"))
        val noOverlap = manga("Manga A", listOf("Thriller", "Horror", "Mystery", "Crime"))
        val fullOverlap = manga("Manga A", listOf("Action", "Romance", "Comedy", "School"))

        val fullScore = RecommendationScorer.score(source, fullOverlap)
        val halfScore = RecommendationScorer.score(source, halfOverlap)
        val noneScore = RecommendationScorer.score(source, noOverlap)

        assertTrue(fullScore > halfScore) { "Full overlap ($fullScore) should beat half overlap ($halfScore)" }
        assertTrue(halfScore > noneScore) { "Half overlap ($halfScore) should beat no overlap ($noneScore)" }
    }

    @Test
    fun `both title and tags empty scores as maximum similarity`() {
        val source = manga("", emptyList())
        val candidate = manga("", emptyList())

        val score = RecommendationScorer.score(source, candidate)
        assertEquals(1.0, score, 0.001)
    }

    private fun manga(title: String, tags: List<String>): Manga {
        return Manga.create().copy(
            ogTitle = title,
            ogGenre = tags.ifEmpty { null },
        )
    }
}

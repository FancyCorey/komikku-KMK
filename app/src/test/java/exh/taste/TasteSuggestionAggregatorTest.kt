package exh.taste

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.interactor.RatedMangaGenres
import tachiyomi.domain.taste.interactor.TasteSuggestionAggregator
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.TagAlias

// KMK v0.8.10 -->
class TasteSuggestionAggregatorTest {

    private fun rated(rating: MangaRating, vararg genres: String) =
        RatedMangaGenres(ratingValue = rating.value, genres = genres.toList())

    @Test
    fun `fewer rated manga than the evidence floor produces no suggestions and reports insufficient data`() {
        val ratedManga = listOf(rated(MangaRating.LOVE, "Romance"))
        val result = TasteSuggestionAggregator.aggregate(ratedManga, emptyList(), emptyList())
        assertTrue(result.preferred.isEmpty())
        assertTrue(result.blocked.isEmpty())
        assertTrue(result.hasInsufficientData)
    }

    @Test
    fun `a tag seen on exactly the evidence floor across Love-rated manga becomes a preferred suggestion`() {
        val ratedManga = listOf(
            rated(MangaRating.LOVE, "Romance"),
            rated(MangaRating.LOVE, "Romance"),
            rated(MangaRating.LIKE, "Romance"),
        )
        val result = TasteSuggestionAggregator.aggregate(ratedManga, emptyList(), emptyList())
        assertEquals(1, result.preferred.size)
        assertEquals("Romance", result.preferred.single().displayName)
        assertEquals(3, result.preferred.single().evidenceCount)
        assertTrue(result.blocked.isEmpty())
        assertFalse(result.hasInsufficientData)
    }

    @Test
    fun `a tag below the evidence floor is never suggested - never infer from one isolated Dislike`() {
        val ratedManga = listOf(
            rated(MangaRating.DISLIKE, "Horror"),
            rated(MangaRating.LOVE, "Romance"),
            rated(MangaRating.LOVE, "Romance"),
        )
        val result = TasteSuggestionAggregator.aggregate(ratedManga, emptyList(), emptyList())
        assertTrue(result.blocked.none { it.displayName == "Horror" })
    }

    @Test
    fun `three Dislike-rated manga sharing a tag become a blocked suggestion`() {
        val ratedManga = listOf(
            rated(MangaRating.DISLIKE, "Horror"),
            rated(MangaRating.DISLIKE, "Horror"),
            rated(MangaRating.DISLIKE, "Horror"),
        )
        val result = TasteSuggestionAggregator.aggregate(ratedManga, emptyList(), emptyList())
        assertEquals(1, result.blocked.size)
        assertEquals("Horror", result.blocked.single().displayName)
        assertTrue(result.blocked.single().netWeight < 0)
    }

    @Test
    fun `a tag already explicitly set is never suggested again`() {
        val ratedManga = listOf(
            rated(MangaRating.LOVE, "Romance"),
            rated(MangaRating.LOVE, "Romance"),
            rated(MangaRating.LOVE, "Romance"),
        )
        val result = TasteSuggestionAggregator.aggregate(ratedManga, emptyList(), listOf("romance"))
        assertTrue(result.preferred.isEmpty())
    }

    @Test
    fun `aliases collapse two spellings into one candidate, using the alias system's own normalization`() {
        val ratedManga = listOf(
            rated(MangaRating.LOVE, "Yuri"),
            rated(MangaRating.LOVE, "Girls Love"),
            rated(MangaRating.LIKE, "Yuri"),
        )
        val aliases = listOf(
            TagAlias(alias = "Yuri", normalizedAlias = "yuri", groupKey = "girls-love-group", displayName = "Girls' Love"),
            TagAlias(alias = "Girls Love", normalizedAlias = "girls love", groupKey = "girls-love-group", displayName = "Girls' Love"),
        )
        val result = TasteSuggestionAggregator.aggregate(ratedManga, aliases, emptyList())
        assertEquals(1, result.preferred.size)
        assertEquals(3, result.preferred.single().evidenceCount)
    }

    @Test
    fun `an already-set alias-resolved tag excludes suggestions under any of its spellings`() {
        val ratedManga = listOf(
            rated(MangaRating.LOVE, "Yuri"),
            rated(MangaRating.LOVE, "Girls Love"),
            rated(MangaRating.LIKE, "Yuri"),
        )
        val aliases = listOf(
            TagAlias(alias = "Yuri", normalizedAlias = "yuri", groupKey = "girls-love-group", displayName = "Girls' Love"),
            TagAlias(alias = "Girls Love", normalizedAlias = "girls love", groupKey = "girls-love-group", displayName = "Girls' Love"),
        )
        // The user already explicitly set a preference under the "girls love" spelling.
        val result = TasteSuggestionAggregator.aggregate(ratedManga, aliases, listOf("girls love"))
        assertTrue(result.preferred.isEmpty())
    }

    @Test
    fun `preferred suggestions are ranked by strongest net weight first`() {
        val ratedManga = listOf(
            rated(MangaRating.LOVE, "Romance"),
            rated(MangaRating.LOVE, "Romance"),
            rated(MangaRating.LOVE, "Romance"),
            rated(MangaRating.LIKE, "Comedy"),
            rated(MangaRating.LIKE, "Comedy"),
            rated(MangaRating.LIKE, "Comedy"),
        )
        val result = TasteSuggestionAggregator.aggregate(ratedManga, emptyList(), emptyList())
        assertEquals(listOf("Romance", "Comedy"), result.preferred.map { it.displayName })
    }

    @Test
    fun `manga with unresolvable genres (never reached the aggregator - e_g_ purged row) simply contribute nothing`() {
        // Simulates GetTasteSuggestions.await() skipping a manga whose row was purged --
        // represented here simply as an entry with an empty genre list.
        val ratedManga = listOf(
            RatedMangaGenres(ratingValue = MangaRating.LOVE.value, genres = emptyList()),
            rated(MangaRating.LOVE, "Romance"),
            rated(MangaRating.LOVE, "Romance"),
            rated(MangaRating.LOVE, "Romance"),
        )
        val result = TasteSuggestionAggregator.aggregate(ratedManga, emptyList(), emptyList())
        assertEquals(1, result.preferred.size)
    }

    @Test
    fun `aggregation never mutates its inputs`() {
        val ratedManga = listOf(rated(MangaRating.LOVE, "Romance"), rated(MangaRating.LOVE, "Romance"), rated(MangaRating.LOVE, "Romance"))
        val snapshot = ratedManga.toList()
        TasteSuggestionAggregator.aggregate(ratedManga, emptyList(), emptyList())
        assertEquals(snapshot, ratedManga)
    }
}

private fun assertFalse(condition: Boolean) = org.junit.jupiter.api.Assertions.assertFalse(condition)
// KMK <--

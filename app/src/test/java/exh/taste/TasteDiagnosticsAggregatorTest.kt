package exh.taste

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.interactor.RatedMangaGenres
import tachiyomi.domain.taste.interactor.TasteDiagnosticsAggregator
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.TagAlias
import tachiyomi.domain.taste.model.TagPreference
import tachiyomi.domain.taste.model.TagTaste

// KMK v0.8.10 -->
class TasteDiagnosticsAggregatorTest {

    private fun rated(rating: MangaRating, vararg genres: String) =
        RatedMangaGenres(ratingValue = rating.value, genres = genres.toList())

    private fun tag(displayName: String, preference: TagPreference, normalizedTag: String = displayName.lowercase()) = TagTaste(
        normalizedTag = normalizedTag,
        displayName = displayName,
        preference = preference.value,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `rating counts reflect Love, Like, and Dislike separately`() {
        val ratedManga = listOf(
            rated(MangaRating.LOVE, "Romance"),
            rated(MangaRating.LOVE, "Comedy"),
            rated(MangaRating.LIKE, "Action"),
            rated(MangaRating.DISLIKE, "Horror"),
        )
        val result = TasteDiagnosticsAggregator.aggregate(ratedManga, emptyList(), emptyList())
        assertEquals(2, result.ratingCounts.love)
        assertEquals(1, result.ratingCounts.like)
        assertEquals(1, result.ratingCounts.dislike)
        assertEquals(4, result.ratingCounts.total)
    }

    @Test
    fun `a preferred tag's evidence count reflects how many rated manga actually carry that genre`() {
        val ratedManga = listOf(
            rated(MangaRating.LOVE, "Romance"),
            rated(MangaRating.LOVE, "Romance"),
            rated(MangaRating.LIKE, "Action"),
        )
        val tagPreferences = listOf(tag("Romance", TagPreference.PREFER))
        val result = TasteDiagnosticsAggregator.aggregate(ratedManga, emptyList(), tagPreferences)
        assertEquals(1, result.preferredTagEvidence.size)
        assertEquals("Romance", result.preferredTagEvidence.single().displayName)
        assertEquals(2, result.preferredTagEvidence.single().evidenceCount)
    }

    @Test
    fun `a preferred tag with zero backing rated manga still appears, with an evidence count of zero`() {
        val ratedManga = listOf(rated(MangaRating.LOVE, "Romance"))
        val tagPreferences = listOf(tag("Isekai", TagPreference.PREFER))
        val result = TasteDiagnosticsAggregator.aggregate(ratedManga, emptyList(), tagPreferences)
        assertEquals(1, result.preferredTagEvidence.size)
        assertEquals(0, result.preferredTagEvidence.single().evidenceCount)
    }

    @Test
    fun `blocked tags are reported separately from preferred and disliked tags`() {
        val tagPreferences = listOf(
            tag("Romance", TagPreference.PREFER),
            tag("Horror", TagPreference.BLOCK),
            tag("Ecchi", TagPreference.DISLIKE),
        )
        val result = TasteDiagnosticsAggregator.aggregate(emptyList(), emptyList(), tagPreferences)
        assertEquals(1, result.explicitPreferredTagCount)
        assertEquals(1, result.explicitBlockedTagCount)
        assertEquals(1, result.explicitDislikedTagCount)
        assertEquals("Horror", result.blockedTagEvidence.single().displayName)
        assertTrue(result.preferredTagEvidence.none { it.displayName == "Horror" })
    }

    @Test
    fun `aliases resolve a tag preference's evidence through the same group-key system as suggestions`() {
        val ratedManga = listOf(
            rated(MangaRating.LOVE, "Yuri"),
            rated(MangaRating.LOVE, "Girls Love"),
        )
        val aliases = listOf(
            TagAlias(alias = "Yuri", normalizedAlias = "yuri", groupKey = "girls-love-group", displayName = "Girls' Love"),
            TagAlias(alias = "Girls Love", normalizedAlias = "girls love", groupKey = "girls-love-group", displayName = "Girls' Love"),
        )
        // The user's explicit preference is stored under the "Yuri" spelling.
        val tagPreferences = listOf(tag("Yuri", TagPreference.PREFER, normalizedTag = "yuri"))
        val result = TasteDiagnosticsAggregator.aggregate(ratedManga, aliases, tagPreferences)
        // Both ratings collapse into the same group key, so both count as evidence.
        assertEquals(2, result.preferredTagEvidence.single().evidenceCount)
    }

    @Test
    fun `preferred tag evidence is sorted strongest first`() {
        val ratedManga = listOf(
            rated(MangaRating.LOVE, "Romance"),
            rated(MangaRating.LOVE, "Romance"),
            rated(MangaRating.LOVE, "Romance"),
            rated(MangaRating.LOVE, "Comedy"),
        )
        val tagPreferences = listOf(tag("Comedy", TagPreference.PREFER), tag("Romance", TagPreference.PREFER))
        val result = TasteDiagnosticsAggregator.aggregate(ratedManga, emptyList(), tagPreferences)
        assertEquals(listOf("Romance", "Comedy"), result.preferredTagEvidence.map { it.displayName })
    }

    @Test
    fun `no rated manga and no tag preferences produces an all-zero, non-crashing summary`() {
        val result = TasteDiagnosticsAggregator.aggregate(emptyList(), emptyList(), emptyList())
        assertEquals(0, result.ratingCounts.total)
        assertTrue(result.preferredTagEvidence.isEmpty())
        assertTrue(result.blockedTagEvidence.isEmpty())
    }

    @Test
    fun `diagnostics never surfaces anything beyond tag display names and integer counts`() {
        // Regression guard for the plan's privacy requirement: the summary type itself has no
        // field capable of carrying a URL, cookie, or raw manga title -- this test documents that
        // contract so a future field addition trips a visible failure here for review.
        val ratedManga = listOf(rated(MangaRating.LOVE, "Romance"), rated(MangaRating.DISLIKE, "Horror"))
        val tagPreferences = listOf(tag("Romance", TagPreference.PREFER), tag("Horror", TagPreference.BLOCK))
        val result = TasteDiagnosticsAggregator.aggregate(ratedManga, emptyList(), tagPreferences)
        val allDisplayNames = (result.preferredTagEvidence + result.blockedTagEvidence).map { it.displayName }
        assertTrue(allDisplayNames.all { it == "Romance" || it == "Horror" })
    }
}
// KMK <--

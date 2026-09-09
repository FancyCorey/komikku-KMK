package eu.kanade.tachiyomi.ui.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.MangaRating

class ChapterCompletionRatingPolicyTest {

    @Test
    fun `an unrated work applies the selected rating and preserves configured follow-up`() {
        assertEquals(
            ChapterCompletionRatingPolicy.Decision.APPLY_WITH_FOLLOW_UP,
            ChapterCompletionRatingPolicy.decide(
                existingRatings = listOf(null),
                selectedRating = MangaRating.LOVE,
                followUpEnabled = true,
            ),
        )
    }

    @Test
    fun `an exact existing rating is an explicit no-op for every rating state`() {
        MangaRating.values().forEach { rating ->
            assertEquals(
                ChapterCompletionRatingPolicy.Decision.NO_OP_ALREADY_RATED,
                ChapterCompletionRatingPolicy.decide(
                    existingRatings = listOf(rating),
                    selectedRating = rating,
                    followUpEnabled = true,
                ),
                "rating=$rating",
            )
        }
    }

    @Test
    fun `a changed existing rating applies once and preserves configured follow-up`() {
        assertEquals(
            ChapterCompletionRatingPolicy.Decision.APPLY_WITH_FOLLOW_UP,
            ChapterCompletionRatingPolicy.decide(
                existingRatings = listOf(MangaRating.LIKE),
                selectedRating = MangaRating.DISLIKE,
                followUpEnabled = true,
            ),
        )
    }

    @Test
    fun `a changed rating applies without follow-up when the setting is disabled`() {
        assertEquals(
            ChapterCompletionRatingPolicy.Decision.APPLY_WITHOUT_FOLLOW_UP,
            ChapterCompletionRatingPolicy.decide(
                existingRatings = listOf(MangaRating.NOT_INTERESTED),
                selectedRating = MangaRating.LOVE,
                followUpEnabled = false,
            ),
        )
    }

    @Test
    fun `a mixed linked-version group is a change even when one target already matches`() {
        assertEquals(
            ChapterCompletionRatingPolicy.Decision.APPLY_WITH_FOLLOW_UP,
            ChapterCompletionRatingPolicy.decide(
                existingRatings = listOf(MangaRating.LOVE, MangaRating.LIKE),
                selectedRating = MangaRating.LOVE,
                followUpEnabled = true,
            ),
        )
    }
}

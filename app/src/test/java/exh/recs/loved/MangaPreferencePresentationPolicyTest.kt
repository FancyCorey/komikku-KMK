package exh.recs.loved

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR

// KMK_CLAUDE_POST_SCREENSHOT_UI_PLAN_2026-08-07 Patch 4, extended
// KMK_CLAUDE_NOT_INTERESTED_STRUCTURAL_PEER_PLAN_2026-08-07 -->
/**
 * [MangaPreferencePresentationPolicy] makes the manga-detail primary rate action's precedence
 * explicit and directly testable, per the plan's requirement that the button visibly reflect Not
 * Interested rather than only an ordinary [MangaRating]. Covers every combination of no state, each
 * ordinary rating, Not Interested only, and the coexistence/transition case where both a rating and
 * Not Interested are stored simultaneously, plus the shared dropdown action list/labels used by
 * every [MangaPreferenceAction] surface.
 */
class MangaPreferencePresentationPolicyTest {

    @Test
    fun `no state at all resolves to the neutral marker`() {
        val presentation = MangaPreferencePresentationPolicy.resolve(rating = null, isNotInterested = false)

        assertEquals(MangaPreferencePresentationPolicy.Marker.NONE, presentation.marker)
        assertEquals(KMR.strings.taste_rating, presentation.titleRes)
        assertFalse(presentation.selected)
        assertEquals(MR.strings.not_selected, presentation.accessibilityStateDescriptionRes)
    }

    @Test
    fun `LOVE with no Not Interested state resolves to the love marker`() {
        val presentation = MangaPreferencePresentationPolicy.resolve(rating = MangaRating.LOVE, isNotInterested = false)

        assertEquals(MangaPreferencePresentationPolicy.Marker.LOVE, presentation.marker)
        assertEquals(KMR.strings.taste_love, presentation.titleRes)
        assertTrue(presentation.selected)
        assertEquals(MR.strings.selected, presentation.accessibilityStateDescriptionRes)
    }

    @Test
    fun `LIKE with no Not Interested state resolves to the like marker`() {
        val presentation = MangaPreferencePresentationPolicy.resolve(rating = MangaRating.LIKE, isNotInterested = false)

        assertEquals(MangaPreferencePresentationPolicy.Marker.LIKE, presentation.marker)
        assertEquals(KMR.strings.taste_like, presentation.titleRes)
        assertTrue(presentation.selected)
    }

    @Test
    fun `DISLIKE with no Not Interested state resolves to the dislike marker`() {
        val presentation = MangaPreferencePresentationPolicy.resolve(rating = MangaRating.DISLIKE, isNotInterested = false)

        assertEquals(MangaPreferencePresentationPolicy.Marker.DISLIKE, presentation.marker)
        assertEquals(KMR.strings.taste_dislike, presentation.titleRes)
        assertTrue(presentation.selected)
    }

    @Test
    fun `Not Interested alone resolves to the not-interested marker`() {
        val presentation = MangaPreferencePresentationPolicy.resolve(rating = null, isNotInterested = true)

        assertEquals(MangaPreferencePresentationPolicy.Marker.NOT_INTERESTED, presentation.marker)
        assertEquals(KMR.strings.rec_mark_seen, presentation.titleRes)
        assertTrue(presentation.selected)
        assertEquals(MR.strings.selected, presentation.accessibilityStateDescriptionRes)
    }

    @Test
    fun `coexistence -- Not Interested takes visual precedence over an underlying rating`() {
        // Not Interested and MangaRating are stored independently (SeenRecommendationMangaStore vs.
        // the MangaTaste table); this proves the explicit, documented precedence decision from the
        // private ledger's Batch A Finding 2 -- the primary button shows Not Interested, never a
        // contradictory or ambiguous combined state, when both are present.
        for (rating in listOf(MangaRating.LOVE, MangaRating.LIKE, MangaRating.DISLIKE)) {
            val presentation = MangaPreferencePresentationPolicy.resolve(rating = rating, isNotInterested = true)

            assertEquals(
                MangaPreferencePresentationPolicy.Marker.NOT_INTERESTED,
                presentation.marker,
                "Not Interested must take precedence over $rating",
            )
            assertEquals(KMR.strings.rec_mark_seen, presentation.titleRes)
            assertTrue(presentation.selected)
        }
    }

    @Test
    fun `transition -- clearing Not Interested while a rating remains reveals the underlying rating`() {
        // Simulates the true -> false transition: once Not Interested is cleared, precedence falls
        // through to whatever rating (if any) remains underneath -- no state is silently lost.
        val whileActive = MangaPreferencePresentationPolicy.resolve(rating = MangaRating.LIKE, isNotInterested = true)
        val afterClearing = MangaPreferencePresentationPolicy.resolve(rating = MangaRating.LIKE, isNotInterested = false)

        assertEquals(MangaPreferencePresentationPolicy.Marker.NOT_INTERESTED, whileActive.marker)
        assertEquals(MangaPreferencePresentationPolicy.Marker.LIKE, afterClearing.marker)
        assertEquals(KMR.strings.taste_like, afterClearing.titleRes)
    }

    @Test
    fun `dropdownActions returns Love, Like, Dislike, then Not Interested in one shared stable order`() {
        val actions = MangaPreferencePresentationPolicy.dropdownActions()

        assertEquals(
            listOf(
                MangaPreferenceAction.Rating(MangaRating.LOVE),
                MangaPreferenceAction.Rating(MangaRating.LIKE),
                MangaPreferenceAction.Rating(MangaRating.DISLIKE),
                MangaPreferenceAction.NotInterested,
            ),
            actions,
        )
    }

    @Test
    fun `labelFor returns the matching static label for each rating action`() {
        assertEquals(KMR.strings.taste_love, MangaPreferencePresentationPolicy.labelFor(MangaPreferenceAction.Rating(MangaRating.LOVE)))
        assertEquals(KMR.strings.taste_like, MangaPreferencePresentationPolicy.labelFor(MangaPreferenceAction.Rating(MangaRating.LIKE)))
        assertEquals(
            KMR.strings.taste_dislike,
            MangaPreferencePresentationPolicy.labelFor(MangaPreferenceAction.Rating(MangaRating.DISLIKE)),
        )
    }

    @Test
    fun `notInterestedToggleLabel switches between mark and undo labels based on current state`() {
        assertEquals(KMR.strings.rec_mark_seen, MangaPreferencePresentationPolicy.notInterestedToggleLabel(isNotInterested = false))
        assertEquals(KMR.strings.rec_clear_seen, MangaPreferencePresentationPolicy.notInterestedToggleLabel(isNotInterested = true))
    }
}
// KMK <--

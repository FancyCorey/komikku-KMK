package exh.recs.loved

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR

/**
 * [MangaPreferencePresentationPolicy] makes the manga-detail primary rate action's presentation
 * explicit and directly testable. Covers every [MangaRating] value (including
 * [MangaRating.NOT_INTERESTED], a real fourth rating-family member) plus the shared dropdown
 * action list/labels used by every [MangaPreferenceAction] surface.
 *
 * KMK v0.8.21-fix3: R1 correction -- [MangaPreferencePresentationPolicy.resolve] now takes a
 * single `rating: MangaRating?` parameter. The prior two-parameter `resolve(rating,
 * isNotInterested)` and its "coexistence"/"transition" tests are removed: Not Interested and an
 * ordinary rating were never actually independent state in the corrected architecture (both are
 * the same `MangaTaste.rating` column), so a test asserting they could simultaneously hold two
 * different values would be asserting an invariant violation, not a real behavior.
 */
class MangaPreferencePresentationPolicyTest {

    @Test
    fun `no state at all resolves to the neutral marker`() {
        val presentation = MangaPreferencePresentationPolicy.resolve(rating = null)

        assertEquals(MangaPreferencePresentationPolicy.Marker.NONE, presentation.marker)
        assertEquals(KMR.strings.taste_rating, presentation.titleRes)
        assertFalse(presentation.selected)
        assertEquals(MR.strings.not_selected, presentation.accessibilityStateDescriptionRes)
    }

    @Test
    fun `LOVE resolves to the love marker`() {
        val presentation = MangaPreferencePresentationPolicy.resolve(rating = MangaRating.LOVE)

        assertEquals(MangaPreferencePresentationPolicy.Marker.LOVE, presentation.marker)
        assertEquals(KMR.strings.taste_love, presentation.titleRes)
        assertTrue(presentation.selected)
        assertEquals(MR.strings.selected, presentation.accessibilityStateDescriptionRes)
    }

    @Test
    fun `LIKE resolves to the like marker`() {
        val presentation = MangaPreferencePresentationPolicy.resolve(rating = MangaRating.LIKE)

        assertEquals(MangaPreferencePresentationPolicy.Marker.LIKE, presentation.marker)
        assertEquals(KMR.strings.taste_like, presentation.titleRes)
        assertTrue(presentation.selected)
    }

    @Test
    fun `DISLIKE resolves to the dislike marker`() {
        val presentation = MangaPreferencePresentationPolicy.resolve(rating = MangaRating.DISLIKE)

        assertEquals(MangaPreferencePresentationPolicy.Marker.DISLIKE, presentation.marker)
        assertEquals(KMR.strings.taste_dislike, presentation.titleRes)
        assertTrue(presentation.selected)
    }

    @Test
    fun `NOT_INTERESTED resolves to the not-interested marker, as a real rating value`() {
        val presentation = MangaPreferencePresentationPolicy.resolve(rating = MangaRating.NOT_INTERESTED)

        assertEquals(MangaPreferencePresentationPolicy.Marker.NOT_INTERESTED, presentation.marker)
        assertEquals(KMR.strings.rec_mark_seen, presentation.titleRes)
        assertTrue(presentation.selected)
        assertEquals(MR.strings.selected, presentation.accessibilityStateDescriptionRes)
    }

    @Test
    fun `resolve accepts exactly one rating -- it is structurally impossible to report two markers active at once`() {
        // R1 adversarial coverage: resolve()'s signature is `(rating: MangaRating?)`, a single
        // nullable value. There is no second parameter through which a caller could ever supply a
        // contradictory "also Not Interested" flag alongside an ordinary rating -- every one of the
        // five possible inputs (null + the four MangaRating values) maps to exactly one Marker.
        val allInputs = listOf(null) + MangaRating.entries
        val markers = allInputs.map { MangaPreferencePresentationPolicy.resolve(it).marker }

        assertEquals(allInputs.size, markers.size)
        assertEquals(
            setOf(
                MangaPreferencePresentationPolicy.Marker.NONE,
                MangaPreferencePresentationPolicy.Marker.LOVE,
                MangaPreferencePresentationPolicy.Marker.LIKE,
                MangaPreferencePresentationPolicy.Marker.DISLIKE,
                MangaPreferencePresentationPolicy.Marker.NOT_INTERESTED,
            ),
            markers.toSet(),
        )
    }

    @Test
    fun `dropdownActions returns Love, Like, Dislike, then Not Interested, all as Rating, in one shared stable order`() {
        val actions = MangaPreferencePresentationPolicy.dropdownActions()

        assertEquals(
            listOf(
                MangaPreferenceAction.Rating(MangaRating.LOVE),
                MangaPreferenceAction.Rating(MangaRating.LIKE),
                MangaPreferenceAction.Rating(MangaRating.DISLIKE),
                MangaPreferenceAction.Rating(MangaRating.NOT_INTERESTED),
            ),
            actions,
        )
        // Every action is the same MangaPreferenceAction.Rating shape -- Not Interested is not a
        // structurally distinct action variant (R1 correction; MangaPreferenceAction.NotInterested
        // no longer exists).
        assertTrue(actions.all { it is MangaPreferenceAction.Rating })
    }

    @Test
    fun `labelFor returns the matching static label for every rating action, including Not Interested`() {
        assertEquals(KMR.strings.taste_love, MangaPreferencePresentationPolicy.labelFor(MangaPreferenceAction.Rating(MangaRating.LOVE)))
        assertEquals(KMR.strings.taste_like, MangaPreferencePresentationPolicy.labelFor(MangaPreferenceAction.Rating(MangaRating.LIKE)))
        assertEquals(
            KMR.strings.taste_dislike,
            MangaPreferencePresentationPolicy.labelFor(MangaPreferenceAction.Rating(MangaRating.DISLIKE)),
        )
        assertEquals(
            KMR.strings.rec_mark_seen,
            MangaPreferencePresentationPolicy.labelFor(MangaPreferenceAction.Rating(MangaRating.NOT_INTERESTED)),
        )
    }
}
// KMK <--

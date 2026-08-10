package exh.recs.loved

import dev.icerock.moko.resources.StringResource
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR

// KMK, extended
// KMK
/**
 * Shared presentation policy for every [MangaPreferenceAction] surface (detail-page primary button
 * and dropdown, and any future caller that wants the same four-action list). Not Interested
 * ([exh.recs.SeenRecommendationMangaStore]) and an ordinary [MangaRating] (the `MangaTaste` table)
 * are stored independently and can coexist -- this policy makes the primary button's *presentation*
 * precedence explicit and directly testable: Not Interested is shown whenever active, regardless of
 * any rating stored underneath it. No stored data changes as a result of this policy; it only
 * decides what is displayed and which actions are offered.
 */
object MangaPreferencePresentationPolicy {

    enum class Marker { NOT_INTERESTED, LOVE, LIKE, DISLIKE, NONE }

    data class MangaPreferencePresentation(
        val marker: Marker,
        val titleRes: StringResource,
        /** True whenever the button should render in its "active"/selected color. */
        val selected: Boolean,
        /** Compose `stateDescription` semantics text -- announced by accessibility services alongside the title. */
        val accessibilityStateDescriptionRes: StringResource,
    )

    /** [rating] is only consulted when [isNotInterested] is false. */
    fun resolve(rating: MangaRating?, isNotInterested: Boolean): MangaPreferencePresentation {
        if (isNotInterested) {
            return MangaPreferencePresentation(Marker.NOT_INTERESTED, KMR.strings.rec_mark_seen, selected = true, MR.strings.selected)
        }
        return when (rating) {
            MangaRating.LOVE -> MangaPreferencePresentation(Marker.LOVE, KMR.strings.taste_love, selected = true, MR.strings.selected)
            MangaRating.LIKE -> MangaPreferencePresentation(Marker.LIKE, KMR.strings.taste_like, selected = true, MR.strings.selected)
            MangaRating.DISLIKE -> MangaPreferencePresentation(Marker.DISLIKE, KMR.strings.taste_dislike, selected = true, MR.strings.selected)
            null -> MangaPreferencePresentation(Marker.NONE, KMR.strings.taste_rating, selected = false, MR.strings.not_selected)
        }
    }

    /**
     * The four actions the detail-page dropdown offers, in one shared, structurally stable order:
     * Love, Like, Dislike, then Not Interested. Rendering this as one data-driven list (rather than
     * four hand-coded `DropdownMenuItem` blocks) is what makes Not Interested a structural peer of
     * the three ordinary ratings instead of a bolted-on standalone action.
     */
    fun dropdownActions(): List<MangaPreferenceAction> = listOf(
        MangaPreferenceAction.Rating(MangaRating.LOVE),
        MangaPreferenceAction.Rating(MangaRating.LIKE),
        MangaPreferenceAction.Rating(MangaRating.DISLIKE),
        MangaPreferenceAction.NotInterested,
    )

    /** Static label for a [MangaPreferenceAction.Rating] dropdown row -- unaffected by current state. */
    fun labelFor(action: MangaPreferenceAction.Rating): StringResource = when (action.value) {
        MangaRating.LOVE -> KMR.strings.taste_love
        MangaRating.LIKE -> KMR.strings.taste_like
        MangaRating.DISLIKE -> KMR.strings.taste_dislike
    }

    /** Dynamic label for the Not Interested dropdown row -- "Not interested" or "Undo not interested" depending on current state. */
    fun notInterestedToggleLabel(isNotInterested: Boolean): StringResource =
        if (isNotInterested) KMR.strings.rec_clear_seen else KMR.strings.rec_mark_seen
}
// KMK <--

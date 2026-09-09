package exh.recs.loved

import dev.icerock.moko.resources.StringResource
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR

/**
 * Shared presentation policy for every [MangaPreferenceAction] surface (detail-page primary button
 * and dropdown, and any future caller that wants the same four-action list).
 *
 * KMK v0.8.21-fix3: R1 correction -- [MangaRating] (the `MangaTaste` table) is the single owner of
 * all four states (Love/Like/Dislike/Not Interested); there is no second, independently-actionable
 * store. This policy decides only what is displayed and which actions are offered; it never reads
 * or writes anything itself.
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

    fun resolve(rating: MangaRating?): MangaPreferencePresentation = when (rating) {
        MangaRating.LOVE -> MangaPreferencePresentation(Marker.LOVE, KMR.strings.taste_love, selected = true, MR.strings.selected)
        MangaRating.LIKE -> MangaPreferencePresentation(Marker.LIKE, KMR.strings.taste_like, selected = true, MR.strings.selected)
        MangaRating.DISLIKE -> MangaPreferencePresentation(Marker.DISLIKE, KMR.strings.taste_dislike, selected = true, MR.strings.selected)
        MangaRating.NOT_INTERESTED -> MangaPreferencePresentation(Marker.NOT_INTERESTED, KMR.strings.rec_mark_seen, selected = true, MR.strings.selected)
        null -> MangaPreferencePresentation(Marker.NONE, KMR.strings.taste_rating, selected = false, MR.strings.not_selected)
    }

    /**
     * The four actions the detail-page dropdown offers, in one shared, structurally stable order:
     * Love, Like, Dislike, then Not Interested. All four are [MangaPreferenceAction.Rating] --
     * there is no separately-shaped action for Not Interested.
     */
    fun dropdownActions(): List<MangaPreferenceAction> = listOf(
        MangaPreferenceAction.Rating(MangaRating.LOVE),
        MangaPreferenceAction.Rating(MangaRating.LIKE),
        MangaPreferenceAction.Rating(MangaRating.DISLIKE),
        MangaPreferenceAction.Rating(MangaRating.NOT_INTERESTED),
    )

    /** Static label for a [MangaPreferenceAction.Rating] dropdown row -- unaffected by current state. */
    fun labelFor(action: MangaPreferenceAction.Rating): StringResource = when (action.value) {
        MangaRating.LOVE -> KMR.strings.taste_love
        MangaRating.LIKE -> KMR.strings.taste_like
        MangaRating.DISLIKE -> KMR.strings.taste_dislike
        MangaRating.NOT_INTERESTED -> KMR.strings.rec_mark_seen
    }
}
// KMK <--

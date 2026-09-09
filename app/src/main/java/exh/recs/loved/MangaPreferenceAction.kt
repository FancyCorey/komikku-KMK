package exh.recs.loved

import tachiyomi.domain.taste.model.MangaRating

/**
 * Every user-facing manga taste/preference action, shared across the detail-page dropdown, For You,
 * rated collections, and the reader completion prompt. This is a presentation/dispatch-level model
 * only -- it deliberately does not become part of persisted data.
 *
 * KMK v0.8.21-fix3: R1 correction -- `NotInterested` is no longer a distinct action variant.
 * [MangaRating.NOT_INTERESTED] is a real fourth member of the persisted `MangaTaste.rating`
 * enum (see the AUG-02 migration), so marking a manga Not Interested is now genuinely just
 * `Rating(MangaRating.NOT_INTERESTED)` -- the exact same dispatch path as Love/Like/Dislike, not
 * a structurally separate action routed to a second store. `Clear` covers all four states through
 * one owner. There is exactly one rating-family action shape now, with no case that can leave a
 * manga in two states at once.
 */
sealed interface MangaPreferenceAction {
    data class Rating(val value: MangaRating) : MangaPreferenceAction
    data object Clear : MangaPreferenceAction
}
// KMK <--

package exh.recs.loved

import tachiyomi.domain.taste.model.MangaRating

// KMK_CLAUDE_NOT_INTERESTED_STRUCTURAL_PEER_PLAN_2026-08-07 -->
/**
 * Every user-facing manga taste/preference action, shared across the detail-page dropdown, For You,
 * rated collections, and the reader completion prompt. This is a presentation/dispatch-level model
 * only -- it deliberately does not become part of persisted data. `MangaRating` remains the
 * numeric-value enum persisted in the `MangaTaste` table; `NotInterested` remains backed by
 * [exh.recs.SeenRecommendationMangaStore]'s independent key set. Do not add `NotInterested` as a
 * fourth [MangaRating] value -- that enum's `.value` is persisted taste data, and folding a
 * differently-stored concept into it would require a real migration this model is explicitly meant
 * to avoid needing.
 */
sealed interface MangaPreferenceAction {
    data class Rating(val value: MangaRating) : MangaPreferenceAction
    data object NotInterested : MangaPreferenceAction
    data object Clear : MangaPreferenceAction
}
// KMK <--

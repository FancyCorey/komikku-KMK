package eu.kanade.tachiyomi.ui.reader

import tachiyomi.domain.taste.model.MangaRating

/**
 * Pure policy for the action selected in the completion-rating prompt.
 *
 * The prompt is allowed to show for an already-rated manga so the user can intentionally change
 * its rating. A selected rating is a no-op only when every applicable confirmed target already
 * has that exact rating. This makes the linked-version case explicit: a mixed group is still an
 * actual change and may proceed. Not Interested is a normal [MangaRating] and follows the same
 * rules as Love, Like, and Dislike.
 *
 * State matrix: unrated -> apply; existing same Love/Like/Dislike/Not Interested -> no-op with no
 * follow-up; existing different rating -> apply; mixed confirmed targets -> apply; and any apply
 * decision becomes without-follow-up when the follow-up setting is disabled.
 */
object ChapterCompletionRatingPolicy {

    enum class Decision {
        APPLY_WITHOUT_FOLLOW_UP,
        APPLY_WITH_FOLLOW_UP,
        NO_OP_ALREADY_RATED,
    }

    fun decide(
        existingRatings: List<MangaRating?>,
        selectedRating: MangaRating,
        followUpEnabled: Boolean,
    ): Decision {
        if (existingRatings.isNotEmpty() && existingRatings.all { it == selectedRating }) {
            return Decision.NO_OP_ALREADY_RATED
        }
        return if (followUpEnabled) {
            Decision.APPLY_WITH_FOLLOW_UP
        } else {
            Decision.APPLY_WITHOUT_FOLLOW_UP
        }
    }
}

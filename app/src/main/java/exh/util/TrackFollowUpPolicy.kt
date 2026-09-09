package exh.util

import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.model.Track

// KMK -->
/** Pure eligibility decision for a guarded compensating tracker write. */
object TrackFollowUpPolicy {
    sealed interface RestoreFollowUp {
        data object Offered : RestoreFollowUp
        data class Unavailable(val reason: Reason) : RestoreFollowUp

        enum class Reason {
            MANGA_NOT_FOUND,
            TRACK_NOT_FOUND,
            TRACKER_NOT_LOGGED_IN,
        }
    }

    fun evaluate(
        manga: Manga?,
        track: Track?,
        trackerLoggedIn: Boolean,
    ): RestoreFollowUp {
        if (manga == null) return RestoreFollowUp.Unavailable(RestoreFollowUp.Reason.MANGA_NOT_FOUND)
        if (track == null) return RestoreFollowUp.Unavailable(RestoreFollowUp.Reason.TRACK_NOT_FOUND)
        if (!trackerLoggedIn) return RestoreFollowUp.Unavailable(RestoreFollowUp.Reason.TRACKER_NOT_LOGGED_IN)
        return RestoreFollowUp.Offered
    }
}
// KMK <--

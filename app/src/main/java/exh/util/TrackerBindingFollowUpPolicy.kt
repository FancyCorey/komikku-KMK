package exh.util

import eu.kanade.tachiyomi.data.track.DeletableTracker
import tachiyomi.domain.track.model.Track

// KMK -->
/** Pure conflict/capability gate for the tracker-binding unlink follow-up. */
object TrackerBindingFollowUpPolicy {
    sealed interface Decision {
        data object Offered : Decision
        data class Unavailable(val reason: Reason) : Decision
    }

    enum class Reason {
        MISSING_TRACK,
        TRACKER_NOT_DELETABLE,
        TRACKER_LOGGED_OUT,
        REMOTE_ID_CHANGED,
    }

    fun evaluate(
        currentTrack: Track?,
        deletableTracker: DeletableTracker?,
        trackerLoggedIn: Boolean,
        expectedRemoteId: Long,
    ): Decision {
        if (currentTrack == null) return Decision.Unavailable(Reason.MISSING_TRACK)
        if (currentTrack.remoteId != expectedRemoteId) return Decision.Unavailable(Reason.REMOTE_ID_CHANGED)
        if (deletableTracker == null) return Decision.Unavailable(Reason.TRACKER_NOT_DELETABLE)
        if (!trackerLoggedIn) return Decision.Unavailable(Reason.TRACKER_LOGGED_OUT)
        return Decision.Offered
    }
}
// KMK <--

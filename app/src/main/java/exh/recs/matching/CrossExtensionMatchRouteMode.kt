package exh.recs.matching

// KMK -->
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.tracker.model.LocalTrackedWorkStatus

internal object CrossExtensionMatchRouteMode {
    const val RATING = "rating"
    const val MARK_SEEN = "mark_seen"
    const val FAVORITE = "favorite"
    const val LOCAL_TRACKING = "local_tracking"

    data class RouteArgs(val modeKey: String, val ratingValue: Int? = null, val localStatus: Int? = null)

    // KMK v0.8.21-fix3: R1 correction -- CrossExtensionMatchMode.MarkSeen no longer exists (Not
    // Interested is Rating(NOT_INTERESTED)); fromMode() never emits MARK_SEEN going forward.
    fun fromMode(mode: CrossExtensionMatchMode): RouteArgs = when (mode) {
        is CrossExtensionMatchMode.Rating -> RouteArgs(RATING, mode.rating.value)
        is CrossExtensionMatchMode.LocalTracking -> RouteArgs(LOCAL_TRACKING, localStatus = mode.status.ordinal)
        CrossExtensionMatchMode.Favorite -> RouteArgs(FAVORITE)
    }

    fun toMode(modeKey: String, ratingValue: Int?, localStatus: Int? = null): CrossExtensionMatchMode? = when (modeKey) {
        RATING -> {
            val rating = ratingValue?.let { MangaRating.fromValue(it) } ?: return null
            CrossExtensionMatchMode.Rating(rating)
        }
        // Read-compatibility only, for any route string serialized by a build before this
        // correction (e.g. saved navigation state across an app update).
        MARK_SEEN -> CrossExtensionMatchMode.Rating(MangaRating.NOT_INTERESTED)
        FAVORITE -> CrossExtensionMatchMode.Favorite
        LOCAL_TRACKING -> {
            val status = localStatus?.let { LocalTrackedWorkStatus.entries.getOrNull(it) } ?: return null
            CrossExtensionMatchMode.LocalTracking(status)
        }
        else -> null
    }
}
// KMK <--

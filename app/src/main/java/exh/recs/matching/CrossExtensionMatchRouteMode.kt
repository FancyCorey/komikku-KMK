package exh.recs.matching

// KMK -->
import tachiyomi.domain.taste.model.MangaRating

internal object CrossExtensionMatchRouteMode {
    const val RATING = "rating"
    const val MARK_SEEN = "mark_seen"
    const val FAVORITE = "favorite"

    data class RouteArgs(val modeKey: String, val ratingValue: Int? = null)

    fun fromMode(mode: CrossExtensionMatchMode): RouteArgs = when (mode) {
        is CrossExtensionMatchMode.Rating -> RouteArgs(RATING, mode.rating.value)
        CrossExtensionMatchMode.MarkSeen -> RouteArgs(MARK_SEEN)
        CrossExtensionMatchMode.Favorite -> RouteArgs(FAVORITE)
    }

    fun toMode(modeKey: String, ratingValue: Int?): CrossExtensionMatchMode? = when (modeKey) {
        RATING -> {
            val rating = ratingValue?.let { MangaRating.fromValue(it) } ?: return null
            CrossExtensionMatchMode.Rating(rating)
        }
        MARK_SEEN -> CrossExtensionMatchMode.MarkSeen
        FAVORITE -> CrossExtensionMatchMode.Favorite
        else -> null
    }
}
// KMK <--

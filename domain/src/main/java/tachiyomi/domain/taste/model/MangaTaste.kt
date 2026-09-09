package tachiyomi.domain.taste.model

// KMK -->
data class MangaTaste(
    val mangaId: Long,
    val source: Long,
    val url: String,
    val title: String,
    val rating: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class MangaRating(val value: Int) {
    // KMK v0.8.21-fix2: NOT_INTERESTED joins the shared rating family (AUG-02 redesign) instead of
    // living in a separate seenRecommendationMangaKeys preference store -- see the "AUG-02 frozen
    // contract" in KOMIKKU_FC_AUGUST_PLAN_A_PREFERENCE_AND_RECOMMENDATION_UX_2026-08-23.md for the
    // full migration/backup/journal/cross-version contract this depends on. -2 keeps the
    // negative-value grouping consistent with DISLIKE(-1) without colliding with any existing value
    // or the 0 "no row exists" absence sentinel.
    NOT_INTERESTED(-2),
    DISLIKE(-1),
    LIKE(1),
    LOVE(2),
    ;

    companion object {
        fun fromValue(value: Int): MangaRating? = entries.find { it.value == value }
    }
}
// KMK <--

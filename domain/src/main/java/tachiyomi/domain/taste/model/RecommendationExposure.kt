package tachiyomi.domain.taste.model

/**
 * Local-only record that a For You candidate was actually present in a loaded, visible result state.
 *
 * Identity is [sourceId] + [url] -- never [url] alone, since two different sources can expose an
 * identical relative url path for unrelated manga. [mangaId] is nullable because a candidate can be
 * exposed before it has ever been localized into the manga table.
 *
 * This is a pure exposure/repetition signal, never a taste or interaction record: whether the user
 * actually opened, rated, or added the title is derived live from the existing library/taste/tracker
 * data at reranking time, not stored here.
 */
data class RecommendationExposure(
    val sourceId: Long,
    val url: String,
    val mangaId: Long?,
    val firstExposedAt: Long,
    val lastExposedAt: Long,
    val exposureCount: Int,
)
// KMK <--

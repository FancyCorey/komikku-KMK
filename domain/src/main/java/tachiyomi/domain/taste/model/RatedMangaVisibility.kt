package tachiyomi.domain.taste.model

// KMK -->
/**
 * Controls which rated manga are hidden from the "For You" recommendation tab.
 */
enum class RatedMangaVisibility {
    /** Hide manga rated Love, Like, or Dislike. Original discovery-first behavior. */
    HIDE_ALL_RATED,

    /** Hide only Disliked manga. Liked/loved manga may reappear as rediscovery. (Default) */
    HIDE_DISLIKED_ONLY,

    /** Show all rated manga in For You. */
    SHOW_ALL_RATED,
}
// KMK <--

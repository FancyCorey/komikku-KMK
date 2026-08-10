package tachiyomi.domain.taste.model

// KMK --> v0.7.0: Phase 4 – persistent cross-source manga link groups
data class CrossSourceMangaLink(
    val source: Long,
    val url: String,
    val groupId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)
// KMK <--

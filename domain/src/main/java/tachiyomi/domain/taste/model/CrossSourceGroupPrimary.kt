package tachiyomi.domain.taste.model

// KMK --> v0.8.0: user-selected primary version per confirmed cross-source link group. Stored
// separately from CrossSourceMangaLink so selecting a primary is independent of the link
// upsert/delete lifecycle (see manga_cross_source_group_primary migration 62).
data class CrossSourceGroupPrimary(
    val groupId: String,
    val source: Long,
    val url: String,
    val updatedAt: Long,
)
// KMK <--

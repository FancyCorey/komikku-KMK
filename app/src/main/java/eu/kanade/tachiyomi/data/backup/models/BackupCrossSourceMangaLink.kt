package eu.kanade.tachiyomi.data.backup.models

// KMK --> v0.7.0: Phase 4 – persistent cross-source manga link groups
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupCrossSourceMangaLink(
    @ProtoNumber(1) val source: Long = 0,
    @ProtoNumber(2) val url: String = "",
    @ProtoNumber(3) val groupId: String = "",
    @ProtoNumber(4) val title: String = "",
    @ProtoNumber(5) val updatedAt: Long = 0,
)
// KMK <--

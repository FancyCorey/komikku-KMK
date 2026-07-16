package eu.kanade.tachiyomi.data.backup.models

// KMK --> v0.8.1-fix1: user-selected primary version per confirmed cross-source link group
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupCrossSourceGroupPrimary(
    @ProtoNumber(1) val groupId: String = "",
    @ProtoNumber(2) val source: Long = 0,
    @ProtoNumber(3) val url: String = "",
    @ProtoNumber(4) val updatedAt: Long = 0,
)
// KMK <--

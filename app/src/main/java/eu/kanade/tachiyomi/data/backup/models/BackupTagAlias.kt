package eu.kanade.tachiyomi.data.backup.models

// KMK -->
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupTagAlias(
    @ProtoNumber(1) val alias: String = "",
    @ProtoNumber(2) val groupKey: String = "",
    @ProtoNumber(3) val displayName: String = "",
)
// KMK <--

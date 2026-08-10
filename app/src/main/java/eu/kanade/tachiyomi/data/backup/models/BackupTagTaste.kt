package eu.kanade.tachiyomi.data.backup.models

// KMK -->
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupTagTaste(
    @ProtoNumber(1) val displayName: String = "",
    @ProtoNumber(2) val preference: Int = 0,
    @ProtoNumber(3) val updatedAt: Long = 0,
)
// KMK <--

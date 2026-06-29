package eu.kanade.tachiyomi.data.backup.models

// KMK --> v0.7.28: seen manga keys for For You dismissals
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupSeenMangaKey(
    @ProtoNumber(1) val key: String = "",
)
// KMK <--

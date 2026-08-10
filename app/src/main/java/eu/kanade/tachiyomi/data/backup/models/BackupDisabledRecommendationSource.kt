package eu.kanade.tachiyomi.data.backup.models

// KMK -->
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupDisabledRecommendationSource(
    @ProtoNumber(1) val sourceId: Long = 0,
)
// KMK <--

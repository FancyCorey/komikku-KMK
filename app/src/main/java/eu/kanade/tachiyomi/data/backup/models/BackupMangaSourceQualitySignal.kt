package eu.kanade.tachiyomi.data.backup.models

// KMK -->
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupMangaSourceQualitySignal(
    @ProtoNumber(1) val originSourceId: Long = 0,
    @ProtoNumber(2) val originUrl: String = "",
    @ProtoNumber(3) val originTitle: String = "",
    @ProtoNumber(4) val selectedSourceId: Long = 0,
    @ProtoNumber(5) val selectedUrl: String = "",
    @ProtoNumber(6) val selectedTitle: String = "",
    @ProtoNumber(7) val selectedSourceName: String = "",
    @ProtoNumber(8) val chapterNumber: Double = 0.0,
    @ProtoNumber(9) val chapterName: String = "",
    @ProtoNumber(10) val selectedAt: Long = 0,
    @ProtoNumber(11) val qualitySignalVersion: Int = 1,
)
// KMK <--

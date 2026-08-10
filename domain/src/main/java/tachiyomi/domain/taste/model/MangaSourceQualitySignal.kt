package tachiyomi.domain.taste.model

// KMK --> v0.7.8: user-confirmed source quality signals from Best Version comparisons
data class MangaSourceQualitySignal(
    val id: Long,
    val originSourceId: Long,
    val originUrl: String,
    val originTitle: String,
    val selectedSourceId: Long,
    val selectedUrl: String,
    val selectedTitle: String,
    val selectedSourceName: String,
    val comparedCandidatesJson: String,
    val chapterNumber: Double?,
    val chapterName: String,
    val sampleSize: Int,
    val sampledPagesJson: String,
    val selectedAt: Long,
    val qualitySignalVersion: Int,
)
// KMK <--

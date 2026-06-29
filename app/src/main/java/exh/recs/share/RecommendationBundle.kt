package exh.recs.share

import kotlinx.serialization.Serializable

// KMK -->

@Serializable
enum class RecommendationBundleType {
    TOP_PICKS,
    SOURCE_ROW,
    LOVED_MANGA,
}

@Serializable
data class RecommendationBundleSource(
    val sourceId: Long,
    val sourceName: String? = null,
    val sourceLang: String? = null,
    val extensionPkgName: String? = null,
    val extensionName: String? = null,
    val extensionSignatureHash: String? = null,
    val repoName: String? = null,
)

@Serializable
data class RecommendationBundleItem(
    val title: String,
    val url: String,
    val sourceId: Long,
    val sourceName: String? = null,
    val sourceLang: String? = null,
    val extensionPkgName: String? = null,
    val extensionName: String? = null,
    val extensionSignatureHash: String? = null,
    val repoName: String? = null,
    val thumbnailUrl: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: Int? = null,
    val score: Double? = null,
    val matchedGroups: List<String> = emptyList(),
    val recommendationReason: String? = null,
    val crossSourceGroupId: String? = null,
)

@Serializable
data class RecommendationBundle(
    val schema: String = SCHEMA_ID,
    val schemaVersion: Int = SCHEMA_VERSION,
    val kmkRecsVersion: String,
    val appVersionName: String? = null,
    val createdAt: Long,
    val title: String,
    val description: String? = null,
    val bundleType: RecommendationBundleType,
    val requiredSources: List<RecommendationBundleSource> = emptyList(),
    val items: List<RecommendationBundleItem>,
) {
    companion object {
        const val SCHEMA_ID = "kmk.recommendation.bundle"
        const val SCHEMA_VERSION = 1
    }
}

// KMK <--

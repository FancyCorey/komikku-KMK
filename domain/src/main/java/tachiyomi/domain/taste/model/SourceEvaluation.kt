package tachiyomi.domain.taste.model

// KMK -->
/**
 * Serialized verdict values for SQLDelight storage. Enum names are stable across versions.
 */
enum class SourceEvaluationVerdict(val serialized: String) {
    STRONG_FIT("strong_fit"),
    WORTH_TRYING("worth_trying"),
    NEUTRAL("neutral"),
    WEAK("weak"),
    POOR_SEARCH("poor_search"),
    EXPLICIT_HEAVY("explicit_heavy"),
    ECCHI_HEAVY("ecchi_heavy"),
    REJECTED("rejected"),
    ERROR("error"),
    NEEDS_MANUAL_REVIEW("needs_manual_review"),
    ;

    companion object {
        fun fromSerialized(s: String): SourceEvaluationVerdict =
            entries.find { it.serialized == s } ?: ERROR
    }
}

/** One evaluated record per (extension pkg+sig, source id) combination. */
data class SourceEvaluation(
    /** Stable key: signatureHash|pkgName|sourceId or signatureHash|pkgName for single-source extensions. */
    val evaluationKey: String,
    val sourceId: Long?,
    val extensionPkgName: String,
    val signatureHash: String,
    val extensionName: String,
    val sourceName: String,
    val lang: String,
    val baseUrl: String?,
    val repoName: String?,
    val sourceCount: Int,
    val isNsfw: Boolean,
    /** Monotonically increasing version to invalidate stale evaluations when scoring logic changes. */
    val evaluationVersion: Int,
    val evaluatedAt: Long,
    val expiresAt: Long?,
    // Probe counts
    val sampleCount: Int,
    val popularCount: Int,
    val latestCount: Int,
    val searchCount: Int,
    val searchSuccessCount: Int,
    // Signal counts
    val likedTitleMatchCount: Int,
    val preferredTagMatchCount: Int,
    val blockedTagMatchCount: Int,
    val explicitSignalCount: Int,
    val ecchiSignalCount: Int,
    val errorCount: Int,
    // Scores [0.0 - 1.0]
    val qualityScore: Double,
    val recommendationFitScore: Double,
    val searchReliabilityScore: Double,
    val explicitScore: Double,
    val ecchiScore: Double,
    val verdict: SourceEvaluationVerdict,
    val sampledTitlesJson: String?,
    val sampledTagsJson: String?,
    val errorMessage: String?,
    // KMK --> v0.7.4: extension version at evaluation time for update-reassessment detection
    val extensionVersionName: String? = null,
    val extensionVersionCode: Long? = null,
    val extensionApkName: String? = null,
    // KMK <--
) {
    /** Extension-level key (no source id) for fast lookups when source id is unknown. */
    val extensionKey: String get() = "$signatureHash|$extensionPkgName"
}

object SourceEvaluationKeys {
    const val CURRENT_VERSION = 1

    fun buildKey(signatureHash: String, pkgName: String, sourceId: Long?): String =
        if (sourceId != null) "$signatureHash|$pkgName|$sourceId" else "$signatureHash|$pkgName"
}
// KMK <--

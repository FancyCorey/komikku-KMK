package tachiyomi.domain.taste.model

// KMK --> v0.7.6
/** Recommendation-quality verdict from a bounded For You-like probe. */
enum class RecommendationQualityVerdict(val serialized: String) {
    GREAT("great"),
    GOOD("good"),
    MIXED("mixed"),
    WEAK("weak"),
    NO_MATCHES("no_matches"),
    ERROR("error"),
    TOO_LITTLE_EVIDENCE("too_little_evidence"),
    ;

    companion object {
        fun fromSerialized(s: String): RecommendationQualityVerdict =
            entries.find { it.serialized == s } ?: TOO_LITTLE_EVIDENCE
    }
}

/**
 * Persisted outcome of a bounded recommendation-quality probe for one source.
 *
 * Kept separate from [SourceEvaluation] because source fit and recommendation quality
 * are independent concepts: a source can be a strong fit for your taste but a poor
 * recommendation source (or vice versa).
 *
 * [fitKey] = "${evaluationKey}::rec_fit"
 */
data class SourceRecommendationFit(
    val fitKey: String,
    val evaluationKey: String,
    val sourceId: Long?,
    val extensionPkgName: String,
    val signatureHash: String,
    val extensionName: String,
    val sourceName: String,
    val lang: String,
    val evaluatedAt: Long,
    val queryCount: Int,
    val querySuccessCount: Int,
    val rawResultCount: Int,
    val visibleCandidateCount: Int,
    val filteredOutCount: Int,
    val blockedTagCandidateCount: Int,
    val matchedGroupCount: Int,
    val topPicksContribution: Int,
    val noMatchesCount: Int,
    val errorCount: Int,
    val avgCandidateScore: Double,
    val recommendationQualityScore: Double,
    val verdict: RecommendationQualityVerdict,
    val reasonsJson: String,
    val errorMessage: String?,
    // KMK --> v0.7.42: staleness parity with SourceEvaluation (evaluationVersion/expiresAt)
    /** Monotonically increasing version to invalidate stale fit records when probe logic changes. */
    val evaluationVersion: Int = CURRENT_VERSION,
    val expiresAt: Long? = null,
    // KMK <--
) {
    companion object {
        fun fitKeyFor(evaluationKey: String): String = "$evaluationKey::rec_fit"

        // KMK --> v0.7.42
        /**
         * Current fit-record scoring version. Existing rows written before this field existed are
         * migrated to version 0 (migration 60), which is automatically below this constant, so they
         * are correctly treated as needing reassessment without a manual backfill.
         */
        const val CURRENT_VERSION = 1
        // KMK <--
    }
}
// KMK <--

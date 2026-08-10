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

// KMK --> v0.7.42: catalogue-only metadata confidence (Popular/Latest samples only)
/**
 * How much of the sampled catalogue (Popular/Latest) evidence actually had usable genre/tag
 * metadata, tracked separately from the fit score itself so missing metadata is never silently
 * read as "no matching tags = low fit" (Popular/Latest frequently omit genre — the same issue
 * v0.7.13 already fixed on the search side via enrichment; catalogue samples get no equivalent
 * enrichment pass, so this signal is required instead).
 */
enum class SourceEvaluationMetadataConfidence(val serialized: String) {
    HIGH("high"),
    MODERATE("moderate"),
    LOW("low"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromSerialized(s: String): SourceEvaluationMetadataConfidence =
            entries.find { it.serialized == s } ?: UNKNOWN
    }
}
// KMK <--

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
    // Probe counts (catalogue: popular + latest only, as of v0.7.42 — see below)
    val sampleCount: Int,
    val popularCount: Int,
    val latestCount: Int,
    // KMK --> v0.7.42: no longer populated by a probe (the scorer's own search probe was removed
    // in favor of the separate, honestly-labeled SourceRecommendationFitProbe / SourceRecommendationFit
    // record). Retained as always-0 columns for backward-compatible reads of historical rows rather
    // than a breaking schema removal.
    val searchCount: Int,
    val searchSuccessCount: Int,
    // KMK <--
    // Signal counts
    // KMK --> v0.7.42: never computed (was always 0 before this release too); retained as an
    // always-0 column for the same backward-compatibility reason as searchCount above.
    val likedTitleMatchCount: Int,
    // KMK <--
    val preferredTagMatchCount: Int,
    val blockedTagMatchCount: Int,
    val explicitSignalCount: Int,
    val ecchiSignalCount: Int,
    val errorCount: Int,
    // Scores [0.0 - 1.0]
    /** Catalogue (Popular/Latest) sample quality, as of v0.7.42. Previously pooled with search. */
    val qualityScore: Double,
    /** Catalogue (Popular/Latest) fit against taste, as of v0.7.42. Previously pooled with search. */
    val recommendationFitScore: Double,
    // KMK --> v0.7.42: no longer meaningful — see searchCount. Always written as 0.0.
    val searchReliabilityScore: Double,
    // KMK <--
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
    // KMK --> v0.7.42: catalogue-only metadata confidence (see SourceEvaluationMetadataConfidence)
    val catalogueMetadataConfidence: SourceEvaluationMetadataConfidence = SourceEvaluationMetadataConfidence.UNKNOWN,
    // KMK <--
    // KMK --> v0.7.47: bounded getMangaDetails() enrichment + split evidence counters (migration 61).
    // See docs/recommendations/KMK.md.
    /** How many catalogue candidates were sent to `getMangaDetails()` for enrichment. */
    val detailEnrichmentAttemptCount: Int = 0,
    /** How many of [detailEnrichmentAttemptCount] detail calls returned successfully. */
    val detailEnrichmentSuccessCount: Int = 0,
    /** How many final (post-enrichment) samples had usable genre/tag metadata. */
    val metadataCandidateCount: Int = 0,
    /** How many final samples produced positive taste evidence (explicit-preferred or learned-positive). */
    val positiveCandidateCount: Int = 0,
    /** How many final samples produced disliked/blocked/learned-negative evidence. */
    val negativeCandidateCount: Int = 0,
    /** How many final samples matched an explicit user-preferred tag group. */
    val explicitPreferredGroupHitCount: Int = 0,
    /** How many final samples matched a learned-positive tag group. */
    val learnedPositiveGroupHitCount: Int = 0,
    /** How many final samples were hard-blocked after alias resolution. */
    val blockedCandidateCount: Int = 0,
    /** How many final samples contained adult/explicit/BL/GL/smut-like tag or title signals, before hard filtering. */
    val adultSignalCandidateCount: Int = 0,
    // KMK <--
) {
    /** Extension-level key (no source id) for fast lookups when source id is unknown. */
    val extensionKey: String get() = "$signatureHash|$extensionPkgName"
}

object SourceEvaluationKeys {
    // KMK --> v0.7.47: bumped 2 -> 3. Scoring semantics changed from "list-entry catalogue fit" to
    // "detail-enriched catalogue evidence with split positive/negative/metadata counters" (bounded
    // getMangaDetails() enrichment for Popular/Latest samples, revised fit-score formula, revised
    // verdict gates including NEEDS_MANUAL_REVIEW for metadata-sparse evidence). Every existing row
    // (v1 or v2) was computed under different rules and must be treated as stale under v3.
    // See docs/recommendations/KMK.md.
    const val CURRENT_VERSION = 3
    // KMK <--

    fun buildKey(signatureHash: String, pkgName: String, sourceId: Long?): String =
        if (sourceId != null) "$signatureHash|$pkgName|$sourceId" else "$signatureHash|$pkgName"
}
// KMK <--

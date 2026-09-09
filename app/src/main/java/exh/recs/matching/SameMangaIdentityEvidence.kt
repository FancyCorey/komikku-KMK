package exh.recs.matching

// KMK --> A11.1
/** Versioned interpretation of in-memory same-manga evidence. */
@JvmInline
value class IdentityDecisionVersion(val value: Int) {
    init {
        require(value > 0)
    }
}

enum class SameMangaIdentityDecision {
    EXACT,
    LIKELY,
    UNCERTAIN,
    CONFLICT,
    REJECTED,
}

enum class SameMangaIdentityReason {
    RECORD_KEY_EQUAL,
    TITLE_EXACT_CANONICAL,
    TITLE_EXACT_COMPATIBILITY,
    TITLE_SIMILAR,
    TITLE_MISSING,
    TITLE_CONFLICT,
    CONTRIBUTOR_EXACT_AUTHOR,
    CONTRIBUTOR_EXACT_ARTIST,
    CONTRIBUTOR_MISSING,
    CONTRIBUTOR_CONFLICT,
    PART_MARKER_AGREEMENT,
    PART_MARKER_MISSING,
    PART_MARKER_CONFLICT,
    STATUS_AGREEMENT,
    STATUS_UNKNOWN_OR_DIFFERENT,
    GENRE_SUPPORT,
    CHAPTER_EXACT_ALIGNMENT,
    CHAPTER_SHIFTED_ALIGNMENT,
    CHAPTER_MISSING,
    CHAPTER_CONFLICT,
    USER_CONFIRMED,
    USER_REJECTED,
}

enum class SameMangaUserDecision {
    NONE,
    CONFIRMED,
    REJECTED,
}

/**
 * Local-only input for the pure evidence policy. Raw metadata never appears in
 * [SameMangaIdentityAssessment].
 */
data class SameMangaIdentitySnapshot(
    val source: Long,
    val url: String,
    val displayTitle: String,
    val originalTitle: String = displayTitle,
    val aliases: List<String> = emptyList(),
    val author: String? = null,
    val artist: String? = null,
    val status: Long? = null,
    val genres: List<String> = emptyList(),
    val chapterNumbers: List<Double> = emptyList(),
    val userDecision: SameMangaUserDecision = SameMangaUserDecision.NONE,
)

/**
 * Privacy-bounded output. It contains reasons and bounded observations, never
 * raw title, contributor, source label, URL, description, exception, or page data.
 */
data class SameMangaIdentityAssessment(
    val version: IdentityDecisionVersion,
    val decision: SameMangaIdentityDecision,
    val reasons: List<SameMangaIdentityReason>,
    val titleSimilarity: Double,
    val sharedGenreCount: Int,
    val chapterOffset: Double?,
)
// KMK <--

package exh.recs.evaluation

// KMK -->

/**
 * Stable categories for recommendation-quality probe failures.
 *
 * Separates infrastructure/install/load failures from search-level failures,
 * so the diagnostics summary can distinguish "the source cannot be reached"
 * from "the source returned results but they don't match the profile."
 */
enum class SourceRecommendationProbeFailureKind {
    NONE,
    NO_TASTE_EVIDENCE,
    AVAILABLE_EXTENSION_LIST_EMPTY,
    EXTENSION_NOT_FOUND,
    EXTENSION_MATCH_AMBIGUOUS,
    INSTALL_FAILED_OR_TIMED_OUT,
    INSTALLED_EXTENSION_DID_NOT_LOAD,
    SOURCE_NOT_FOUND,
    SOURCE_MATCH_AMBIGUOUS,
    SEARCH_ERROR,
    SEARCH_TIMED_OUT,
    RAW_RESULTS_EMPTY,
    RESULTS_NO_METADATA,
    ALL_RESULTS_BLOCKED,
    // KMK v0.7.45: non-installed probe refused because the Private installer isn't available
    PRIVATE_INSTALLER_REQUIRED,
    UNKNOWN,
}

/**
 * Pure classifier that maps rec-quality [errorMessage] strings to stable [SourceRecommendationProbeFailureKind].
 *
 * The error messages come from two sources:
 * - Pre-probe stage: [SourceEvaluationScreenModel.evaluateOneForRecQuality] writes fixed-format strings.
 * - Probe stage: [SourceRecommendationFitProbe] writes per-plan reason strings.
 *
 * Matching is case-insensitive substring matching, not regex, to stay simple and testable.
 */
object SourceRecommendationFitFailureClassifier {

    fun classify(errorMessage: String?): SourceRecommendationProbeFailureKind {
        if (errorMessage.isNullOrBlank()) return SourceRecommendationProbeFailureKind.NONE
        return when {
            errorMessage.contains("Available extension list unavailable", ignoreCase = true) ->
                SourceRecommendationProbeFailureKind.AVAILABLE_EXTENSION_LIST_EMPTY
            errorMessage.contains("Extension not found", ignoreCase = true) ->
                SourceRecommendationProbeFailureKind.EXTENSION_NOT_FOUND
            errorMessage.contains("Extension match ambiguous", ignoreCase = true) ->
                SourceRecommendationProbeFailureKind.EXTENSION_MATCH_AMBIGUOUS
            errorMessage.contains("Install failed", ignoreCase = true) ->
                SourceRecommendationProbeFailureKind.INSTALL_FAILED_OR_TIMED_OUT
            errorMessage.contains("did not load", ignoreCase = true) ->
                SourceRecommendationProbeFailureKind.INSTALLED_EXTENSION_DID_NOT_LOAD
            errorMessage.contains("Source not found", ignoreCase = true) ->
                SourceRecommendationProbeFailureKind.SOURCE_NOT_FOUND
            errorMessage.contains("Source match ambiguous", ignoreCase = true) ->
                SourceRecommendationProbeFailureKind.SOURCE_MATCH_AMBIGUOUS
            errorMessage.contains("timed out", ignoreCase = true) ->
                SourceRecommendationProbeFailureKind.SEARCH_TIMED_OUT
            errorMessage.contains("no raw results", ignoreCase = true) ->
                SourceRecommendationProbeFailureKind.RAW_RESULTS_EMPTY
            errorMessage.contains("no genre metadata", ignoreCase = true) ->
                SourceRecommendationProbeFailureKind.RESULTS_NO_METADATA
            errorMessage.contains("blocked by tag", ignoreCase = true) ->
                SourceRecommendationProbeFailureKind.ALL_RESULTS_BLOCKED
            errorMessage.contains("error —", ignoreCase = true) ->
                SourceRecommendationProbeFailureKind.SEARCH_ERROR
            errorMessage.contains("No taste profile", ignoreCase = true) ->
                SourceRecommendationProbeFailureKind.NO_TASTE_EVIDENCE
            errorMessage.contains("Private installer required", ignoreCase = true) ->
                SourceRecommendationProbeFailureKind.PRIVATE_INSTALLER_REQUIRED
            else -> SourceRecommendationProbeFailureKind.UNKNOWN
        }
    }

    /** Whether this failure kind is an infrastructure/install/load issue vs a search-level issue. */
    fun isInstallOrLoadIssue(kind: SourceRecommendationProbeFailureKind): Boolean = when (kind) {
        SourceRecommendationProbeFailureKind.AVAILABLE_EXTENSION_LIST_EMPTY,
        SourceRecommendationProbeFailureKind.EXTENSION_NOT_FOUND,
        SourceRecommendationProbeFailureKind.EXTENSION_MATCH_AMBIGUOUS,
        SourceRecommendationProbeFailureKind.INSTALL_FAILED_OR_TIMED_OUT,
        SourceRecommendationProbeFailureKind.INSTALLED_EXTENSION_DID_NOT_LOAD,
        SourceRecommendationProbeFailureKind.SOURCE_NOT_FOUND,
        SourceRecommendationProbeFailureKind.SOURCE_MATCH_AMBIGUOUS,
        -> true
        else -> false
    }
}
// KMK <--

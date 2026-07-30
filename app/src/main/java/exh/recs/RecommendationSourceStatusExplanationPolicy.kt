package exh.recs

import tachiyomi.i18n.kmk.KMR

// KMK v0.8.17 (Phase C: diagnostic-first For You quality) -->
/**
 * Maps an already-computed [RecommendationSourceStatus] to the KMR string resource holding a plain-
 * language explanation of *why* a source ended up in that state.
 *
 * This does not add any new diagnostic signal to the search pipeline -- [RecommendationSourceStatus]
 * already distinguishes `NoMatches` (the search genuinely returned zero raw candidates) from
 * `FilteredOut` (raw candidates existed but every one was rejected by a filter: known/rated/seen/
 * min-chapter/positive-taste-evidence) at the point [BrowsePersonalRecommendationsScreenModel
 * .finalEmptyOutcome] resolves the terminal state for a source. That distinction was already being
 * computed correctly; it was just never explained to the user beyond a one-word badge ("No matches" /
 * "Often filtered"), which is exactly the "guess a scoring tweak instead of explaining the existing
 * signal" trap the v0.8.17 plan's Phase C explicitly warns against. This policy is the compact
 * explanation layer the plan calls for, built entirely from data the pipeline already has -- no new
 * instrumentation, no scoring change.
 */
object RecommendationSourceStatusExplanationPolicy {

    fun explanationFor(status: RecommendationSourceStatus) = when (status) {
        RecommendationSourceStatus.Shown -> KMR.strings.rec_source_status_explain_shown
        RecommendationSourceStatus.NoMatches -> KMR.strings.rec_source_status_explain_no_matches
        RecommendationSourceStatus.FilteredOut -> KMR.strings.rec_source_status_explain_filtered
        RecommendationSourceStatus.Error -> KMR.strings.rec_source_status_explain_error
        RecommendationSourceStatus.Disabled -> KMR.strings.rec_source_status_explain_disabled
        RecommendationSourceStatus.OutsideAttemptLimit -> KMR.strings.rec_source_status_explain_not_searched_limit
        RecommendationSourceStatus.HiddenByDuplicateHandling -> KMR.strings.rec_source_status_explain_duplicate_hidden
    }
}
// KMK <--

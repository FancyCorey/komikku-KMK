package exh.recs.bestversion

import exh.recs.RecommendationErrorKind

/**
 * Keeps every Best Version preview attempt in a terminal state.
 *
 * The nullable result is retained at the fetch boundary because a stale chapter map or a source
 * that disappeared during the comparison can invalidate the work before a request starts. That
 * condition is still a completed attempt from the screen's perspective: it must be represented as
 * a stable error rather than leaving the row in Loading forever. Both the initial load and retry
 * path use this owner so their state transitions cannot drift apart.
 */
object BestVersionPreviewTerminalStatePolicy {
    fun resolve(result: CandidatePreviewState?): CandidatePreviewState =
        result ?: CandidatePreviewState.PreviewError(
            BestVersionErrorReason.Recommendation(RecommendationErrorKind.Internal),
        )
}

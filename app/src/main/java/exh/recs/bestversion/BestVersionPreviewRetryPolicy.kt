package exh.recs.bestversion

// KMK R2-AUG-05-ACTUAL-BEST-VERSION-FAILURE-PATH -->
/**
 * Pure per-page retry/generation bookkeeping for the reader-quality candidate preview pager
 * ([BestVersionCompareScreen]'s `FullscreenCandidatePreviewDialog`). Extracted so the "retry a single
 * failed page" and "a superseded in-flight load must never overwrite a newer state" behaviors are
 * directly testable without Compose/Coil.
 *
 * Each page index has its own retry generation counter, starting at 0. Pressing Retry on a page bumps
 * that page's generation; any load that was started under an older generation is stale and must be
 * ignored if it resolves after a newer retry has already been requested for the same page. This
 * mirrors the reader's own page-load generation-guard shape (a slow/late result from a superseded
 * load must never clobber newer UI state), scoped down to what a bounded, read-only preview needs:
 * no chapter transitions, no preloading, no persistence -- just this page, this dialog, this generation.
 */
object BestVersionPreviewRetryPolicy {

    /** Returns [tokens] with [pageIndex]'s retry generation bumped by one. */
    fun requestRetry(tokens: Map<Int, Int>, pageIndex: Int): Map<Int, Int> =
        tokens + (pageIndex to (tokens[pageIndex] ?: 0) + 1)

    /** The current retry generation for [pageIndex] (0 if it has never been retried). */
    fun currentGeneration(tokens: Map<Int, Int>, pageIndex: Int): Int = tokens[pageIndex] ?: 0

    /**
     * True when [resultGeneration] (the generation a load was started under) is still [pageIndex]'s
     * current generation -- i.e. no newer retry has superseded it while it was in flight. A caller
     * must discard/ignore a result for which this returns false rather than applying it to UI state.
     */
    fun isCurrent(tokens: Map<Int, Int>, pageIndex: Int, resultGeneration: Int): Boolean =
        currentGeneration(tokens, pageIndex) == resultGeneration

    /** Clamps a requested pager page index into the valid `[0, pageCount)` range (or -1 if empty). */
    fun clampPageIndex(requested: Int, pageCount: Int): Int {
        if (pageCount <= 0) return -1
        return requested.coerceIn(0, pageCount - 1)
    }
}
// KMK <--

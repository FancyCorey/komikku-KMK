package exh.recs.memory

// KMK --> v0.7.38: For You candidate discovery memory
import tachiyomi.domain.taste.model.RecommendationDiscoveryProgress

/**
 * Pure helper that decides which additional page to probe for a source during a refresh.
 *
 * Discovery progression (v0.7.40):
 * - Page 1 is always handled by the existing cache/live search path and is recorded in the
 *   progress table on each live probe.
 * - [nextPageToProbe] accepts full progress records so it can classify retryable vs permanent
 *   failures and apply bounded exponential backoff via [RecommendationRetryClassifier].
 * - A retryable page whose [RecommendationDiscoveryProgress.nextRetryAt] has not passed is
 *   NOT probed this refresh (returns null), preventing unbounded retry storms.
 * - A permanent failure or unsupported page is skipped; discovery advances to the next page.
 * - Cap: no page beyond [MAX_DISCOVERY_PAGE_PER_SOURCE_QUERY] is ever probed.
 *
 * v0.7.38: used knownPages from candidate memory — empty/filtered pages not tracked.
 * v0.7.39: uses evaluatedPages (Set<Int>) from discovery progress — all outcomes tracked, but
 *          error pages were permanently skipped.
 * v0.7.40: uses full progress records — retryable errors are retried with backoff; permanent
 *          and unsupported errors advance discovery to the next page.
 * v0.7.41: retry selection runs before the page cap so a due retry of the final page (page 20)
 *          is allowed, while the cap still blocks creating a NEW page past it (page 21). A
 *          retryable error still waiting for its retry time blocks advancement instead of
 *          skipping ahead. Exhausted/permanent/unsupported/success/empty/filtered/duplicate
 *          records are all advanceable.
 */
object RecommendationDiscoveryPlanner {

    /** Maximum additional discovery pages probed per source per refresh (page 1 is separate). */
    const val MAX_NEW_PAGES_PER_SOURCE_REFRESH = 1

    /** Maximum new candidates processed per source per discovery page. */
    const val MAX_NEW_CANDIDATES_PER_DISCOVERY_PAGE = 20

    /** Hard cap on page number probed per source/query across all refreshes. */
    const val MAX_DISCOVERY_PAGE_PER_SOURCE_QUERY = 20

    /**
     * Returns the next page to probe (or retry), or null if nothing should be probed this refresh.
     *
     * Decision order:
     * 1. No records → null (page 1 not yet evaluated).
     * 2. Lowest still-pending retryable-error page (STATUS_ERROR, retryable kind, attempts < MAX):
     *    - due (now >= nextRetryAt, or nextRetryAt is null) → return that page (retry, allowed even
     *      at the cap page so a due retry of page 20 runs);
     *    - not yet due → null (do NOT advance past a retryable error waiting for its retry time).
     * 3. No pending retry → advance to frontier + 1 as a NEW page, subject to the page cap.
     *    Exhausted / permanent / unsupported / success / empty / filtered / duplicate records are
     *    all advanceable. The cap applies only to the new page, so page 21 is never created.
     */
    fun nextPageToProbe(
        progressRecords: List<RecommendationDiscoveryProgress>,
        nowMs: Long,
    ): Int? {
        if (progressRecords.isEmpty()) return null

        // A retryable error keeps discovery pinned to its page until it is retried or exhausted.
        // Sequential discovery keeps this at the frontier, but scan defensively for the lowest one.
        val pendingRetry = progressRecords
            .filter {
                it.status == RecommendationDiscoveryProgress.STATUS_ERROR &&
                    RecommendationRetryClassifier.isRetryable(it.failureKind, it.attemptCount)
            }
            .minByOrNull { it.page }

        if (pendingRetry != null) {
            // A null nextRetryAt means "retry now" (no scheduled backoff recorded).
            val isDue = pendingRetry.nextRetryAt?.let { it <= nowMs } ?: true
            return if (isDue) pendingRetry.page else null
        }

        // No pending retry: advance to a NEW page past the frontier, bounded by the cap.
        val maxEvaluated = progressRecords.maxOf { it.page }
        val nextPage = maxEvaluated + 1
        return if (nextPage > MAX_DISCOVERY_PAGE_PER_SOURCE_QUERY) null else nextPage
    }

    /**
     * Returns true if [newPageUrls] is entirely a subset of [previouslyKnownUrls] —
     * meaning this page returned only candidates we already know about.
     */
    fun isExhausted(newPageUrls: Set<String>, previouslyKnownUrls: Set<String>): Boolean {
        if (newPageUrls.isEmpty()) return true
        return newPageUrls.all { it in previouslyKnownUrls }
    }
}
// KMK <--

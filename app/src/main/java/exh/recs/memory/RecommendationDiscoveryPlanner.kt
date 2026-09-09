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

    // KMK --> EC-04 2026-09-01: configurable discovery-effort policy, planning half.
    /**
     * Plans up to [maxAdditionalPages] pages to probe THIS refresh, given [progressRecords] as they
     * stood before this refresh's additional-page probing began. [MAX_NEW_PAGES_PER_SOURCE_REFRESH]
     * was previously an unenforced constant (the single call site simply called [nextPageToProbe]
     * once); this is the seam that makes a configurable multi-page-per-refresh effort level actually
     * possible without changing [nextPageToProbe]'s own single-page contract or its callers'
     * existing usage.
     *
     * Each planned page after the first assumes the previous planned page in THIS list succeeds
     * (recorded as [tachiyomi.domain.taste.model.RecommendationDiscoveryProgress.STATUS_SUCCESS])
     * and becomes part of the frontier for computing the next one — this is what lets a configured
     * effort level advance several pages within one refresh instead of needing one refresh per page.
     * The caller is expected to probe pages one at a time in the returned order and MUST stop early
     * (not probe a later page in this plan) if an earlier probe in the same pass indicates the query
     * is retrying/blocked/exhausted, since the real outcome may not match the assumed SUCCESS this
     * function plans against; this function only decides how many pages to ATTEMPT, never bypasses
     * the real per-page result/backoff handling in `discoverAdditionalPage`.
     *
     * A pending retryable-error page (see [nextPageToProbe]) can only be the FIRST planned page —
     * once returned, the simulated frontier advances past it for subsequent iterations, exactly as
     * a real successful probe would, so this never plans two pages sharing the same page number.
     * The existing [MAX_DISCOVERY_PAGE_PER_SOURCE_QUERY] lifetime cap still applies to every planned
     * page via [nextPageToProbe] itself.
     */
    fun planAdditionalPages(
        progressRecords: List<RecommendationDiscoveryProgress>,
        maxAdditionalPages: Int,
        nowMs: Long,
    ): List<Int> {
        if (maxAdditionalPages <= 0) return emptyList()
        val plan = mutableListOf<Int>()
        var simulatedRecords = progressRecords
        repeat(maxAdditionalPages) {
            val next = nextPageToProbe(simulatedRecords, nowMs) ?: return plan
            plan += next
            // Replace (not append alongside) any existing record for this page -- e.g. a retryable-
            // error record just planned as a retry -- exactly as a real progress-table upsert would,
            // so the next iteration's pendingRetry scan doesn't keep finding the stale error record.
            simulatedRecords = simulatedRecords.filterNot { it.page == next } + RecommendationDiscoveryProgress(
                sourceId = 0L,
                querySignature = "",
                queryTagsJson = "",
                queryStrategy = null,
                page = next,
                evaluatedAt = nowMs,
                rawCount = 0,
                localizedCount = 0,
                scoredCount = 0,
                visibleCount = 0,
                filteredCount = 0,
                status = RecommendationDiscoveryProgress.STATUS_SUCCESS,
                errorMessage = null,
                profileFingerprint = null,
            )
        }
        return plan
    }
    // KMK <--
}
// KMK <--

// KMK --> EC-04 2026-09-01: configurable discovery-effort policy.
/**
 * User-configurable bound on how many additional discovery pages [planAdditionalPages] plans per
 * source per refresh. [additionalPagesPerRefresh] is expressed as page count, not a raw source-
 * page or a candidate count, because [RecommendationDiscoveryPlanner.MAX_NEW_CANDIDATES_PER_DISCOVERY_PAGE]
 * already bounds candidates-per-page and source page sizes are not universal (confirmed variable:
 * 11-22 raw candidates per page across real installed sources, see the receipt below) — "N pages"
 * is the only unit that stays meaningful across every source.
 *
 * Values are chosen from a real, on-device measurement pass against this program's actual restored
 * profile and its real installed extensions (not a guess), recorded in full in the canonical gate
 * receipt `ec_04_discovery_effort_measurement_2026_09_01`:
 * - Per-page yield ([tachiyomi.domain.taste.model.RecommendationDiscoveryProgress.visibleCount],
 *   averaged across 9 real sources with up to 20 evaluated pages each) stays meaningfully positive
 *   through roughly page 15 (~8.8 at page 1 declining to ~5.3-6.4 through page ~15) before dropping
 *   sharply toward page 20 (~2.4) — a real, measured diminishing-returns curve, not an assumption.
 * - Storage cost per discovered candidate is small and bounded: ~246 bytes of logical row content
 *   measured directly (667 real rows / 163,904 bytes), and `recommendation_candidate_memory`
 *   already prunes to a per-source cap via `pruneOldestBySource`, so even [EXTENDED] cannot grow
 *   storage unboundedly.
 * - Network cost is exactly [additionalPagesPerRefresh] extra HTTP requests per source per refresh
 *   — linear and already structurally bounded by the existing per-refresh/lifetime page caps.
 *
 * [STANDARD] is the DEFAULT, not [OFF]: it reproduces the exact page-count
 * ([RecommendationDiscoveryPlanner.MAX_NEW_PAGES_PER_SOURCE_REFRESH]) this feature has always run
 * at before this preference existed, so introducing configurability does not silently change
 * already-accepted behavior for existing users. [OFF] and [EXTENDED] are the genuinely new,
 * measurement-justified choices this requirement asked for. Nothing beyond [EXTENDED] (e.g. eagerly
 * sweeping toward the full lifetime cap in one refresh) is offered: the measured yield curve above
 * shows real but clearly diminishing value there, better served by the existing incremental one-
 * page-per-refresh-at-minimum crawl over time than by an eager burst every refresh.
 */
enum class DiscoveryEffortLevel(val storedValue: String, val additionalPagesPerRefresh: Int) {
    OFF("off", 0),
    STANDARD("standard", RecommendationDiscoveryPlanner.MAX_NEW_PAGES_PER_SOURCE_REFRESH),
    EXTENDED("extended", 3),
    ;

    companion object {
        val DEFAULT = STANDARD

        fun resolve(storedValue: String): DiscoveryEffortLevel =
            entries.firstOrNull { it.storedValue == storedValue } ?: DEFAULT
    }
}
// KMK <--

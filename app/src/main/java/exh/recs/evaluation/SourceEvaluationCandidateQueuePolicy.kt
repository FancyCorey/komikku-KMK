package exh.recs.evaluation

// KMK --> v0.8.1-fix3
/**
 * Pure, Android-free policy that classifies a candidate pool into the stale/outdated-reassessment
 * queue, independent of the user's `skipAlreadyEvaluated`/`reEvaluateStale` option toggles.
 *
 * This exists because [SourceEvaluationCandidateFilter.applyOptions] treats a stale extension as
 * "already evaluated, hidden" whenever `reEvaluateStale == false` (the default) — which is correct
 * for the *unassessed* queue, but means stale rows have nowhere to go: they are neither offered as
 * unassessed candidates nor exposed as a distinct, continuable reassessment queue. This policy
 * builds that second queue explicitly.
 *
 * See `docs/recommendations/KMK_RECS_V0_8_1_FIX3_SOURCE_EVALUATION_CONTINUATION_FIX_PLAN.md`.
 */
object SourceEvaluationCandidateQueuePolicy {

    /**
     * Candidates from [pool] whose extension has existing evaluation rows that are ALL stale
     * (expired or computed under an older [tachiyomi.domain.taste.model.SourceEvaluationKeys.CURRENT_VERSION]).
     * Order follows [SourceEvaluationCandidateFilter.CandidatePoolResult.allEligible].
     */
    fun staleCandidates(
        pool: SourceEvaluationCandidateFilter.CandidatePoolResult,
        now: Long,
    ): List<EvaluationCandidate> = pool.allEligible.filter { c ->
        val extKey = "${c.extension.signatureHash}|${c.extension.pkgName}"
        val evals = pool.evaluationsByExtensionKey[extKey]
        !evals.isNullOrEmpty() && SourceEvaluationCandidateFilter.isStale(evals, now)
    }
}
// KMK <--

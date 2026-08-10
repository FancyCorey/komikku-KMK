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
 * See `docs/recommendations/KMK.md`.
 */
object SourceEvaluationCandidateQueuePolicy {

    /**
     * Candidates from [pool] whose extension has existing evaluation rows that are ALL stale
     * (expired or computed under an older [tachiyomi.domain.taste.model.SourceEvaluationKeys.CURRENT_VERSION]).
     * Order follows [SourceEvaluationCandidateFilter.CandidatePoolResult.allEligible].
     *
     * KMK v0.8.15-fix1: root-cause fix for the live-device "Reassess outdated advances 25 -> 15,
     * reports Evaluation completed, but source_evaluation is unchanged" bug. [pool.allEligible]
     * deliberately does NOT apply option-based filters such as explicit/adult blocking (see
     * [SourceEvaluationCandidateFilter.CandidatePoolResult]'s doc) -- those are applied downstream by
     * [SourceEvaluationCandidateFilter.applyOptions] for the *unassessed* queue only. The stale queue
     * used to skip that step entirely, so an explicit extension the user has blocked could still be
     * built into `state.staleCandidates`. [SourceEvaluationRunner.start] then skipped it before any
     * database write (correctly, since blocking is honored) but still advanced the stale cursor past
     * it -- so a batch of N stale candidates where M were blocked could advance the cursor by N while
     * durably writing nothing for those M. Filtering blocked-explicit candidates out here, before they
     * ever reach the runner, means the actionable-only list this function returns is exactly what the
     * runner will actually attempt (mirroring how `applyOptions` already behaves for the unassessed
     * queue) -- no separate skip-without-write path is needed for this reason anymore.
     *
     * @param includeExplicit mirrors `SourceEvaluationOptions.includeExplicitCandidates` -- when
     * false and [SourceEvaluationCandidateFilter.CandidatePoolResult.blockExplicit] is true, explicit
     * candidates are excluded from the actionable stale list (they remain visible only via the
     * separate outside-run/excluded disclosure, never as part of the actionable count/button).
     */
    fun staleCandidates(
        pool: SourceEvaluationCandidateFilter.CandidatePoolResult,
        now: Long,
        includeExplicit: Boolean = true,
    ): List<EvaluationCandidate> {
        val shouldHideExplicit = pool.blockExplicit && !includeExplicit
        return pool.allEligible.filter { c ->
            val extKey = "${c.extension.signatureHash}|${c.extension.pkgName}"
            if (shouldHideExplicit && extKey in pool.explicitExtensionKeys) return@filter false
            val evals = pool.evaluationsByExtensionKey[extKey]
            !evals.isNullOrEmpty() && SourceEvaluationCandidateFilter.isStale(evals, now)
        }
    }
}
// KMK <--

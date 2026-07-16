package exh.recs.evaluation

import tachiyomi.domain.taste.model.SourceEvaluation

// KMK v0.8.8 -->
/**
 * Root-cause fix for the reported "visible Outdated rows lead to immediate failure/zero candidates"
 * bug in Source Evaluation reassessment.
 *
 * ## Traced pipeline and confirmed root cause
 *
 * The per-row "Outdated — reassess needed" label the user sees in the past-results list is computed
 * by [SourceEvaluationDisplayPolicy.state] purely from the stored [SourceEvaluation] row itself
 * (its `evaluationVersion`/`expiresAt`/`verdict`) — it has no knowledge of whether that extension is
 * currently reachable for a fresh run.
 *
 * The "Continue reassessing outdated" queue, however, is built from
 * [SourceEvaluationCandidateQueuePolicy.staleCandidates], which filters
 * [SourceEvaluationCandidateFilter.CandidatePoolResult.allEligible] — and [SourceEvaluationCandidateFilter.buildPool]
 * unconditionally excludes any extension that is: already installed, untrusted, language-mismatched
 * against the user's *current* recommendation-language filter, nsfw-disabled, disliked/quality-
 * disliked, or quarantined-unsafe.
 *
 * These two computations use **completely disjoint eligibility criteria** — this is exactly the
 * "count query and work query disagree" root-cause class the plan predicted, just not a literal
 * count-vs-work-query pair in one function; it's a *display* classification (row-level, no pool
 * awareness) versus a *work* candidate pool (pool-aware, no evaluation-history display awareness).
 *
 * Concretely: a source can show "Outdated — reassess needed" (correct — its stored evaluation really
 * is stale) while being **permanently unreachable** by "Continue reassessing outdated" because the
 * user has since installed it, changed their recommendation-language filter, or disliked it — with
 * no error, and no candidates, because from the queue's point of view there was never anything to
 * process. This is not a crash and not a generic failure; it is a real, silent, structural mismatch
 * between what is *shown* and what is *reachable*.
 *
 * ## The fix
 *
 * This is NOT "one shared policy that replaces both computations" — the display policy correctly
 * answers a different question ("is this stored verdict stale?") than the candidate pool ("can I run
 * a fresh evaluation for this extension right now?"), and collapsing them would either make currently
 * -installed extensions wrongly disappear from evaluation history, or make the reassessment queue
 * incorrectly try to evaluate installed extensions (Source Evaluation is explicitly a *non-installed*
 * discovery tool — see [SourceEvaluationCandidateFilter.buildPool]'s `installedPkgNames` exclusion,
 * which must not be removed).
 *
 * Instead, this reconciles the two: given every currently-stale [SourceEvaluation] row and the actual
 * eligible candidate pool, it reports exactly how many visible "outdated" rows are workable right now
 * versus permanently unreachable this run — so the UI can show an honest, specific explanation
 * ("N outdated, but M of them are for extensions you've since installed/excluded — reassess those
 * from Source Priority instead") instead of a silent zero-candidate no-op that looks like a failure.
 */
object SourceEvaluationOutdatedReconciliation {

    data class Result(
        /** Every extension key whose stored evaluation(s) are stale (OUTDATED_VERSION or EXPIRED per [SourceEvaluationDisplayPolicy]), regardless of current pool eligibility. */
        val totalOutdatedExtensionKeys: Set<String>,
        /** The subset of [totalOutdatedExtensionKeys] that are actually present in the current eligible candidate pool — these are what "Continue reassessing outdated" will actually process. */
        val workableOutdatedExtensionKeys: Set<String>,
    ) {
        val totalOutdatedCount: Int get() = totalOutdatedExtensionKeys.size
        val workableOutdatedCount: Int get() = workableOutdatedExtensionKeys.size

        /** Visible "Outdated" rows that "Continue reassessing outdated" cannot and will not touch this run — the exact set the bug's silent zero-candidate symptom was hiding. */
        val unreachableOutdatedCount: Int get() = totalOutdatedCount - workableOutdatedCount

        /** True exactly when every visible outdated row is unreachable — the queue would show/behave as if there is nothing to do despite N visible "Outdated" rows. This is the precise condition that must never be reported as a generic "evaluation failed." */
        val allOutdatedAreUnreachable: Boolean get() = totalOutdatedCount > 0 && workableOutdatedCount == 0
    }

    fun reconcile(
        allEvaluations: List<SourceEvaluation>,
        pool: SourceEvaluationCandidateFilter.CandidatePoolResult,
        now: Long,
    ): Result {
        val totalOutdatedKeys = allEvaluations
            .groupBy { it.extensionKey }
            .filterValues { evals -> evals.any { SourceEvaluationDisplayPolicy.isStaleForRanking(it, now) } }
            .keys

        val eligibleKeys = pool.allEligible.map { "${it.extension.signatureHash}|${it.extension.pkgName}" }.toSet()
        val workableKeys = totalOutdatedKeys.intersect(eligibleKeys)

        return Result(
            totalOutdatedExtensionKeys = totalOutdatedKeys,
            workableOutdatedExtensionKeys = workableKeys,
        )
    }
}
// KMK <--

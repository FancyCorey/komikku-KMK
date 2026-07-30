package exh.recs.evaluation

// KMK v0.8.12 -->
/**
 * Pure extraction of the "show the stale-reassessment completion card" condition from
 * `SourceEvaluationScreen.kt`, formalized and unit-tested per the v0.8.12 plan's Workstream C2.
 *
 * Direct code inspection (v0.8.12 Workstream C investigation) confirmed the underlying condition was
 * already correct going into this pass -- it only reports completion once a stale reassessment run has
 * actually happened ([DisplayState] considers `continuationCursorStaleIsSet`), not merely because
 * every remaining outdated row happens to be currently unreachable. This object exists to make that
 * condition directly testable rather than only reachable through a Compose item-list assembly, and to
 * give future edits one place to change instead of two copies (the screen's `itemKeysInOrder` anchor
 * list and its actual `item {}` block previously each inlined the same boolean expression).
 *
 * KMK v0.8.13-fix1: [evaluate] replaces the single-Boolean [shouldShowCompletion] with a three-state
 * [DisplayState] so the completion card's own copy can distinguish "every actionable outdated source
 * was reassessed, and there was nothing else excluded" from "every actionable outdated source was
 * reassessed, but N outdated rows remain excluded (installed/language-filtered/disliked/quarantined)
 * and this run could never touch them." Reported live-device confusion: the screen could show a
 * "N of M outdated source(s) can't be reassessed here right now" note right next to a generic
 * "Evaluation completed" card, which read as if the excluded rows had also been processed.
 * [shouldShowCompletion] is kept, now implemented in terms of [evaluate], so existing callers/tests
 * that only need the old yes/no signal are unaffected.
 */
object SourceEvaluationStaleCompletionDisplayPolicy {

    sealed interface DisplayState {
        /** No completion card should be shown -- either a run is in progress, actionable work remains, or no run has ever completed. */
        data object Hidden : DisplayState

        /** Every actionable outdated source was reassessed this run, and no outdated rows were excluded. */
        data object CompletedAllActionable : DisplayState

        /** Every actionable outdated source was reassessed this run, but at least one outdated row remains excluded (installed/language-filtered/disliked/quarantined) and was never in scope for this run. */
        data object CompletedWithUnreachableRemaining : DisplayState
    }

    /**
     * @param staleCandidatesEmpty true when the current workable stale-reassessment pool
     * (`state.staleCandidates`) is empty.
     * @param continuationCursorStaleIsSet true when a stale reassessment run has actually completed
     * at least one batch (`state.continuationCursorStale != null`) -- this is what distinguishes
     * "just finished the actionable work" from "there was never any actionable work to begin with."
     * @param isRunning true while an evaluation is currently in progress.
     * @param hasUnreachableOutdated true when at least one visible "outdated" row is excluded from
     * this run's eligible candidate pool -- see [SourceEvaluationOutdatedReconciliation.Result.hasUnreachableOutdated].
     */
    fun evaluate(
        staleCandidatesEmpty: Boolean,
        continuationCursorStaleIsSet: Boolean,
        isRunning: Boolean,
        hasUnreachableOutdated: Boolean,
    ): DisplayState {
        val shouldShow = staleCandidatesEmpty && continuationCursorStaleIsSet && !isRunning
        return when {
            !shouldShow -> DisplayState.Hidden
            hasUnreachableOutdated -> DisplayState.CompletedWithUnreachableRemaining
            else -> DisplayState.CompletedAllActionable
        }
    }

    /** Backward-compatible yes/no signal, implemented in terms of [evaluate]. */
    fun shouldShowCompletion(
        staleCandidatesEmpty: Boolean,
        continuationCursorStaleIsSet: Boolean,
        isRunning: Boolean,
    ): Boolean = evaluate(staleCandidatesEmpty, continuationCursorStaleIsSet, isRunning, hasUnreachableOutdated = false) != DisplayState.Hidden
}
// KMK <--

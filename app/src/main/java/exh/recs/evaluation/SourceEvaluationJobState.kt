package exh.recs.evaluation

import kotlinx.coroutines.flow.MutableStateFlow

// KMK -->
/**
 * Process-scoped singleton bridging [SourceEvaluationJob] and [SourceEvaluationScreenModel].
 *
 * The WorkManager job writes progress here; the screen model reads from it.
 * This allows the screen model to reconnect to an active run after the Source Evaluation
 * screen is navigated away from and back.
 *
 * All fields are safe to read/write from any thread because StateFlow is thread-safe
 * and candidate/options are only written before the job is enqueued.
 */
object SourceEvaluationJobState {
    /** Current state of the active evaluation run. Null means no run has started or it was reset. */
    val activeQueueState = MutableStateFlow<SourceEvaluationQueueState?>(null)

    /** Candidates staged for the next job. Populated by ScreenModel before enqueue. */
    @Volatile
    var pendingCandidates: List<EvaluationCandidate>? = null

    /** Options staged for the next job. Populated by ScreenModel before enqueue. */
    @Volatile
    var pendingOptions: SourceEvaluationOptions? = null

    // KMK: previously held the whole `SourceEvaluationRunner` (which retains a `Context`) in this
    // process-scoped singleton -- flagged by Android Lint's StaticFieldLeak. The runner's Context
    // is contractually Application-level (CoroutineWorker's constructor parameter), so this was
    // never a real leaked-Activity-context bug, but the singleton had no structural need to hold
    // the whole runner either: the only external read of it (SourceEvaluationScreenModel's
    // continuation-cursor update) only ever needed completedCandidateKeys, a plain snapshot.
    // SourceEvaluationJob now copies that snapshot in directly instead of publishing the runner
    // object itself, so this singleton never retains a Context-carrying instance at all.
    /** Snapshot of the completed candidate keys from the run that just finished. */
    @Volatile
    var lastCompletedCandidateKeys: Set<String> = emptySet()

    // KMK --> v0.7.6: continuation cursor support
    /** Filter fingerprint for the pending run. Used to advance cursor after completion. */
    @Volatile
    var pendingCursorFingerprint: String? = null

    /** Full (unsliced) candidate list. Used to advance cursor after completion. */
    @Volatile
    var pendingAllCandidates: List<EvaluationCandidate>? = null
    // KMK <--

    // KMK --> v0.8.1-fix3: distinguishes which continuation queue/cursor slot this run belongs to,
    // so a stale-reassessment batch does not overwrite (or get overwritten by) the normal
    // unassessed-queue cursor. See SOURCE_EVALUATION_CONTINUATION_FIX_PLAN.
    /** True when the pending/active run is a stale/outdated reassessment batch, not the normal unassessed queue. */
    @Volatile
    var pendingIsStaleRun: Boolean = false
    // KMK <--

    fun reset() {
        pendingCandidates = null
        pendingOptions = null
        lastCompletedCandidateKeys = emptySet()
        activeQueueState.value = null
        // KMK --> v0.7.6
        pendingCursorFingerprint = null
        pendingAllCandidates = null
        // KMK <--
        // KMK --> v0.8.1-fix3
        pendingIsStaleRun = false
        // KMK <--
    }
}
// KMK <--

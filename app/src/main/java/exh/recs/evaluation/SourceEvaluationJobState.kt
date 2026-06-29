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

    /** The currently running runner, if any. Used by the job to cancel on WorkManager stop. */
    @Volatile
    var activeRunner: SourceEvaluationRunner? = null

    // KMK --> v0.7.6: continuation cursor support
    /** Filter fingerprint for the pending run. Used to advance cursor after completion. */
    @Volatile
    var pendingCursorFingerprint: String? = null

    /** Full (unsliced) candidate list. Used to advance cursor after completion. */
    @Volatile
    var pendingAllCandidates: List<EvaluationCandidate>? = null
    // KMK <--

    fun reset() {
        pendingCandidates = null
        pendingOptions = null
        activeRunner = null
        activeQueueState.value = null
        // KMK --> v0.7.6
        pendingCursorFingerprint = null
        pendingAllCandidates = null
        // KMK <--
    }
}
// KMK <--

package exh.recs.evaluation

import kotlinx.coroutines.flow.MutableStateFlow
import tachiyomi.domain.taste.model.SourceEvaluation

// KMK -->
/**
 * Progress state for a running/finished background For You search compatibility check.
 *
 * Mirrors [SourceEvaluationQueueState] in shape but is intentionally a separate type: it tracks
 * a flat list of [SourceEvaluation] targets rather than extension-install phases, since a
 * compatibility check probes an already-known source rather than evaluating a whole extension.
 */
data class SourceRecommendationQualityQueueState(
    val status: Status = Status.Idle,
    val totalCount: Int = 0,
    val completedCount: Int = 0,
    val currentSourceName: String? = null,
    val errorMessage: String? = null,
    // KMK --> v0.7.45: public-safety fix — non-installed (temporary-install) probes now require the
    // Private installer. When it's unavailable, those targets are refused rather than silently
    // installed via Shizuku/Current (where PromptRequired cleanup could leave the extension behind
    // with only a log line). This counts how many targets were skipped for that reason so the UI can
    // show a clear message instead of the user only seeing generic per-source errors.
    val nonInstalledSkippedCount: Int = 0,
    // KMK <--
) {
    enum class Status { Idle, Running, Cancelling, Completed, Cancelled, Failed }

    val isIdle: Boolean get() = status == Status.Idle || status == Status.Completed || status == Status.Cancelled || status == Status.Failed
    val isRunning: Boolean get() = status == Status.Running || status == Status.Cancelling
    val isTerminal: Boolean get() = status == Status.Completed || status == Status.Cancelled || status == Status.Failed
}

/**
 * Process-scoped singleton bridging [SourceRecommendationQualityJob] and [SourceEvaluationScreenModel].
 *
 * Follows the same non-durable, process-scoped pattern as [SourceEvaluationJobState]: state is lost
 * on process death, and [SourceRecommendationQualityJob] reports [SourceRecommendationQualityQueueState.Status.Failed]
 * with an explicit "state lost" message in that case rather than silently marking sources as checked.
 */
object SourceRecommendationQualityJobState {
    val activeQueueState = MutableStateFlow<SourceRecommendationQualityQueueState?>(null)

    /** Targets staged for the next job. Populated by ScreenModel before enqueue. */
    @Volatile
    var pendingTargets: List<SourceEvaluation>? = null

    // KMK: previously held the running SourceRecommendationQualityRunner (which retains a
    // Context) here -- flagged by Android Lint's StaticFieldLeak. Confirmed unused: nothing
    // outside SourceRecommendationQualityJob ever read this field (cancellation is invoked via
    // the job's own local `runner` variable, not through this singleton), so it was pure dead
    // retention with no functional purpose. Removed rather than replaced.

    fun reset() {
        pendingTargets = null
        activeQueueState.value = null
    }
}
// KMK <--

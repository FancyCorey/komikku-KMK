package exh.recs.evaluation

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

data class SourceEvaluationContinuationMetadata(
    val fingerprint: String,
    val allCandidates: List<EvaluationCandidate>,
    val isStaleRun: Boolean,
)

interface SourceEvaluationJobGateway {
    val queueState: StateFlow<SourceEvaluationQueueState?>
    val lastCompletedCandidateKeys: Set<String>
    val continuationMetadata: SourceEvaluationContinuationMetadata?

    fun start(
        candidates: List<EvaluationCandidate>,
        options: SourceEvaluationOptions,
        continuationMetadata: SourceEvaluationContinuationMetadata,
    )

    fun cancel()
    fun isRunning(): Boolean
    fun reset()
}

class WorkManagerSourceEvaluationJobGateway(
    private val context: Context,
) : SourceEvaluationJobGateway {
    override val queueState: StateFlow<SourceEvaluationQueueState?>
        get() = SourceEvaluationJobState.activeQueueState

    override val lastCompletedCandidateKeys: Set<String>
        get() = SourceEvaluationJobState.lastCompletedCandidateKeys

    override val continuationMetadata: SourceEvaluationContinuationMetadata?
        get() {
            val fingerprint = SourceEvaluationJobState.pendingCursorFingerprint ?: return null
            val allCandidates = SourceEvaluationJobState.pendingAllCandidates ?: return null
            return SourceEvaluationContinuationMetadata(
                fingerprint = fingerprint,
                allCandidates = allCandidates,
                isStaleRun = SourceEvaluationJobState.pendingIsStaleRun,
            )
        }

    override fun start(
        candidates: List<EvaluationCandidate>,
        options: SourceEvaluationOptions,
        continuationMetadata: SourceEvaluationContinuationMetadata,
    ) {
        SourceEvaluationJobState.beginRun(candidates, options, continuationMetadata)
        SourceEvaluationJob.start(context)
    }

    override fun cancel() {
        SourceEvaluationJob.cancel(context)
        SourceEvaluationJobState.cancelActive()
    }

    override fun isRunning(): Boolean = SourceEvaluationJob.isRunning(context)

    override fun reset() {
        SourceEvaluationJobState.reset()
    }
}

package exh.recs.evaluation

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.kmk.KMR

// KMK -->
class SourceEvaluationJob(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    private val notifier = SourceEvaluationNotifier(context)

    override suspend fun doWork(): Result {
        val candidates = SourceEvaluationJobState.pendingCandidates
        val options = SourceEvaluationJobState.pendingOptions

        if (candidates == null || options == null || candidates.isEmpty()) {
            logcat(LogPriority.WARN) {
                "KMK SourceEvaluationJob: no pending candidates/options (process may have restarted)"
            }
            // KMK --> v0.7.11: use KMR string
            SourceEvaluationJobState.activeQueueState.value = SourceEvaluationQueueState(
                status = SourceEvaluationQueueState.Status.Failed,
                errorMessage = context.stringResource(KMR.strings.source_evaluation_state_lost_error),
            )
            // KMK <--
            return Result.failure()
        }

        setForegroundSafely()

        val runner = SourceEvaluationRunner(context)
        SourceEvaluationJobState.activeRunner = runner
        runner.start(candidates, options)

        return try {
            runner.state
                .onEach { queueState ->
                    SourceEvaluationJobState.activeQueueState.value = queueState
                    notifier.updateProgress(queueState)
                }
                .first { it.isTerminal }

            val finalState = SourceEvaluationJobState.activeQueueState.value
            if (finalState?.status == SourceEvaluationQueueState.Status.Completed) {
                notifier.showComplete(finalState.strongFitCount)
            } else {
                notifier.dismissProgress()
            }
            Result.success()
        } catch (e: CancellationException) {
            runner.cancel()
            SourceEvaluationJobState.activeQueueState.value =
                SourceEvaluationJobState.activeQueueState.value
                    ?.copy(status = SourceEvaluationQueueState.Status.Cancelled)
            notifier.dismissProgress()
            Result.success()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "KMK SourceEvaluationJob: unexpected error" }
            SourceEvaluationJobState.activeQueueState.value =
                SourceEvaluationJobState.activeQueueState.value
                    ?.copy(status = SourceEvaluationQueueState.Status.Failed, errorMessage = e.message)
            notifier.dismissProgress()
            Result.failure()
        } finally {
            SourceEvaluationJobState.activeRunner = null
            SourceEvaluationJobState.pendingCandidates = null
            SourceEvaluationJobState.pendingOptions = null
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val currentState = SourceEvaluationJobState.activeQueueState.value
            ?: SourceEvaluationQueueState(status = SourceEvaluationQueueState.Status.Running)
        return ForegroundInfo(
            Notifications.ID_SOURCE_EVALUATION_PROGRESS,
            notifier.buildProgressNotification(currentState).build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    companion object {
        private const val TAG_JOB = "SourceEvaluationJob"
        private const val UNIQUE_WORK_NAME = "SourceEvaluationJob:active"

        fun isRunning(context: Context): Boolean =
            context.workManager.isRunning(TAG_JOB)

        fun start(context: Context) {
            val request = OneTimeWorkRequestBuilder<SourceEvaluationJob>()
                .addTag(TAG_JOB)
                .build()
            context.workManager.enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            context.workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
// KMK <--

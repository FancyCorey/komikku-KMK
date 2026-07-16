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

// KMK --> v0.7.43
/**
 * Background WorkManager job for For You search compatibility checks.
 *
 * Mirrors [SourceEvaluationJob]'s shape exactly, but is a distinct worker with its own tag/unique
 * work name so it never competes with, or is cancelled by, the full Source Evaluation job. This is
 * what lets a manual "check missing/outdated/all promising sources" action keep running after the
 * user leaves the Source Evaluation screen.
 */
class SourceRecommendationQualityJob(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    private val notifier = SourceRecommendationQualityNotifier(context)

    override suspend fun doWork(): Result {
        val targets = SourceRecommendationQualityJobState.pendingTargets

        if (targets.isNullOrEmpty()) {
            logcat(LogPriority.WARN) {
                "KMK SourceRecommendationQualityJob: no pending targets (process may have restarted)"
            }
            SourceRecommendationQualityJobState.activeQueueState.value = SourceRecommendationQualityQueueState(
                status = SourceRecommendationQualityQueueState.Status.Failed,
                errorMessage = context.stringResource(KMR.strings.source_evaluation_state_lost_error),
            )
            return Result.failure()
        }

        setForegroundSafely()

        val runner = SourceRecommendationQualityRunner(context)
        SourceRecommendationQualityJobState.activeRunner = runner
        runner.start(targets, installerModeFromInputData())

        return try {
            runner.state
                .onEach { queueState ->
                    SourceRecommendationQualityJobState.activeQueueState.value = queueState
                    notifier.updateProgress(queueState)
                }
                .first { it.isTerminal }

            val finalState = SourceRecommendationQualityJobState.activeQueueState.value
            if (finalState?.status == SourceRecommendationQualityQueueState.Status.Completed) {
                notifier.showComplete(finalState.completedCount)
            } else {
                notifier.dismissProgress()
            }
            Result.success()
        } catch (e: CancellationException) {
            runner.cancel()
            SourceRecommendationQualityJobState.activeQueueState.value =
                SourceRecommendationQualityJobState.activeQueueState.value
                    ?.copy(status = SourceRecommendationQualityQueueState.Status.Cancelled)
            notifier.dismissProgress()
            Result.success()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "KMK SourceRecommendationQualityJob: unexpected error" }
            SourceRecommendationQualityJobState.activeQueueState.value =
                SourceRecommendationQualityJobState.activeQueueState.value
                    ?.copy(status = SourceRecommendationQualityQueueState.Status.Failed, errorMessage = e.message)
            notifier.dismissProgress()
            Result.failure()
        } finally {
            SourceRecommendationQualityJobState.activeRunner = null
            SourceRecommendationQualityJobState.pendingTargets = null
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val currentState = SourceRecommendationQualityJobState.activeQueueState.value
            ?: SourceRecommendationQualityQueueState(status = SourceRecommendationQualityQueueState.Status.Running)
        return ForegroundInfo(
            Notifications.ID_SOURCE_RECOMMENDATION_QUALITY_PROGRESS,
            notifier.buildProgressNotification(currentState).build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private fun installerModeFromInputData(): SourceEvaluationInstallerPolicy.InstallerMode {
        val name = inputData.getString(KEY_INSTALLER_MODE)
        return SourceEvaluationInstallerPolicy.InstallerMode.entries.find { it.name == name }
            ?: SourceEvaluationInstallerPolicy.InstallerMode.PRIVATE
    }

    companion object {
        private const val TAG_JOB = "SourceRecommendationQualityJob"
        private const val UNIQUE_WORK_NAME = "SourceRecommendationQualityJob:active"
        private const val KEY_INSTALLER_MODE = "installer_mode"

        fun isRunning(context: Context): Boolean =
            context.workManager.isRunning(TAG_JOB)

        fun start(context: Context, installerMode: SourceEvaluationInstallerPolicy.InstallerMode) {
            val request = OneTimeWorkRequestBuilder<SourceRecommendationQualityJob>()
                .addTag(TAG_JOB)
                .setInputData(
                    androidx.work.Data.Builder()
                        .putString(KEY_INSTALLER_MODE, installerMode.name)
                        .build(),
                )
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

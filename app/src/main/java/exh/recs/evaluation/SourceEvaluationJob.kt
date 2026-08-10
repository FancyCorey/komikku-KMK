package exh.recs.evaluation

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker.Result
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.BuildConfig
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
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

/**
 * Narrow test seam over [SourceEvaluationNotifier] -- covers only the methods
 * [runSourceEvaluationWork] actually calls, so a fake can be substituted in a JVM unit test
 * without constructing a real [android.content.Context] or [androidx.work.CoroutineWorker].
 * [SourceEvaluationNotifier] implements this unchanged; production callers are unaffected.
 */
internal interface SourceEvaluationWorkerNotifier {
    fun updateProgress(queueState: SourceEvaluationQueueState)
    fun dismissProgress()
    fun showComplete(strongFitCount: Int)
}

/**
 * The exact cancellation/failure/success control flow of [SourceEvaluationJob.doWork] (once
 * candidates/options are resolved), extracted so it can be exercised directly in a JVM unit test
 * (see `SourceEvaluationJobCancellationTest`) without a real [androidx.work.CoroutineWorker]
 * instance -- this project has neither Robolectric nor `androidx.work:work-testing` (confirmed
 * absent from the dependency graph before adding this seam). [SourceEvaluationJob.doWork] calls
 * this with the real [SourceEvaluationRunner] and [SourceEvaluationJobState] wiring, so production
 * behavior is unchanged.
 */
internal suspend fun runSourceEvaluationWork(
    collectUntilTerminal: suspend (onState: (SourceEvaluationQueueState) -> Unit) -> Unit,
    notifier: SourceEvaluationWorkerNotifier,
    getState: () -> SourceEvaluationQueueState?,
    setState: (SourceEvaluationQueueState?) -> Unit,
    cancelRunner: () -> Unit,
    finallyCleanup: () -> Unit,
    logUnexpectedError: (Throwable) -> Unit,
): Result {
    return try {
        collectUntilTerminal { queueState ->
            setState(queueState)
            notifier.updateProgress(queueState)
        }

        val finalState = getState()
        if (finalState?.status == SourceEvaluationQueueState.Status.Completed) {
            notifier.showComplete(finalState.strongFitCount)
        } else {
            notifier.dismissProgress()
        }
        Result.success()
    } catch (e: CancellationException) {
        // KMK: previously caught here and converted into Result.success() ("assume success
        // although cancelled"), which reported a cancelled run as succeeded to WorkManager and
        // any external observer of this work's WorkInfo. Rethrowing after the same cleanup lets
        // CoroutineWorker's own cancellation handling report the run as actually cancelled; the
        // finally block below still runs cleanup either way.
        cancelRunner()
        setState(getState()?.copy(status = SourceEvaluationQueueState.Status.Cancelled))
        notifier.dismissProgress()
        throw e
    } catch (e: Exception) {
        logUnexpectedError(e)
        setState(getState()?.copy(status = SourceEvaluationQueueState.Status.Failed, errorMessage = SourceEvaluationProbeErrorClassifier.classifyToStorageKey(e)))
        notifier.dismissProgress()
        Result.failure()
    } finally {
        finallyCleanup()
    }
}

// Pure decision
// extracted so the debug/release gating logic is directly unit-testable without WorkManager or a
// real Context. The fixture path activates only when BOTH conditions hold -- BuildConfig.DEBUG is
// a compile-time-per-variant constant that folds to `false` for every non-debug build type
// (release, releaseTest, foss, preview, benchmark all derive from release; see app/build.gradle.kts),
// so this branch is unreachable and dead-code-eliminable outside a debug build regardless of the
// preference value. Neither condition alone activates the fixture.
internal fun selectSourceEvaluationRunner(
    isDebugBuild: Boolean,
    fixtureMode: SourceEvaluationDebugFixtureMode,
    realRunnerProvider: () -> SourceEvaluationRunnerContract,
    fixtureRunnerProvider: (SourceEvaluationDebugFixtureMode) -> SourceEvaluationRunnerContract,
): SourceEvaluationRunnerContract =
    if (isDebugBuild && fixtureMode != SourceEvaluationDebugFixtureMode.OFF) {
        fixtureRunnerProvider(fixtureMode)
    } else {
        realRunnerProvider()
    }

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

        val runner = selectSourceEvaluationRunner(
            isDebugBuild = BuildConfig.DEBUG,
            fixtureMode = SourceEvaluationDebugFixtureMode.fromPrefValue(
                Injekt.get<SourcePreferences>().evaluationFixtureFailureMode().get(),
            ),
            realRunnerProvider = { SourceEvaluationRunner(context) },
            fixtureRunnerProvider = { mode -> SourceEvaluationDebugFixtureRunner(mode) },
        )
        runner.start(candidates, options)

        return runSourceEvaluationWork(
            collectUntilTerminal = { onState ->
                runner.state
                    .onEach { onState(it) }
                    .first { it.isTerminal }
            },
            notifier = notifier,
            getState = { SourceEvaluationJobState.activeQueueState.value },
            setState = { SourceEvaluationJobState.activeQueueState.value = it },
            cancelRunner = { runner.cancel() },
            finallyCleanup = {
                // KMK: snapshot only what SourceEvaluationScreenModel actually needs (see
                // SourceEvaluationJobState.lastCompletedCandidateKeys) instead of publishing the
                // whole runner object.
                SourceEvaluationJobState.lastCompletedCandidateKeys = runner.completedCandidateKeys
                SourceEvaluationJobState.pendingCandidates = null
                SourceEvaluationJobState.pendingOptions = null
            },
            logUnexpectedError = { logcat(LogPriority.ERROR) { "KMK SourceEvaluationJob: unexpected error" } },
        )
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

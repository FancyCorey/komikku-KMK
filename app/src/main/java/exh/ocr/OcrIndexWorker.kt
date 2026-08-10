package exh.ocr

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker.Result
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

// KMK --> OCR v0.1.1 (updated from v0.1.0)

/**
 * Narrow test seam over [OcrNotifier] -- covers only the methods
 * [runOcrIndexWork] actually calls, so a fake can be substituted in a JVM unit test without
 * constructing a real [android.content.Context] or [androidx.work.CoroutineWorker]. [OcrNotifier]
 * implements this unchanged; production callers are unaffected.
 */
internal interface OcrIndexWorkerNotifier {
    fun updateProgress(progress: OcrIndexProgress)
    fun dismissProgress()
    fun showComplete(recognizedPages: Int, emptyPages: Int, failedPages: Int)
}

/**
 * The exact cancellation/failure/success control flow of [OcrIndexWorker.doWork], extracted so it
 * can be exercised directly in a JVM unit test (see `OcrIndexWorkerCancellationTest`) without a
 * real [androidx.work.CoroutineWorker] instance -- this project has neither Robolectric nor
 * `androidx.work:work-testing` (confirmed absent from the dependency graph before adding this
 * seam), and adding either was judged unnecessary once this narrow extraction made a direct test
 * possible. [OcrIndexWorker.doWork] calls this with the real [OcrIndexService.runIndexing] and
 * [OcrJobState] wiring, so production behavior is unchanged.
 */
internal suspend fun runOcrIndexWork(
    runIndexing: suspend (onProgress: OcrProgressCallback) -> Unit,
    notifier: OcrIndexWorkerNotifier,
    publishProgress: (OcrIndexProgress) -> Unit,
    markCancelled: (OcrIndexProgress) -> Unit,
    markFailed: (OcrIndexProgress) -> Unit,
    markNotRunning: () -> Unit,
    logUnexpectedError: (Throwable) -> Unit,
): Result {
    var lastProgress = OcrIndexProgress()
    return try {
        runIndexing { progress ->
            lastProgress = progress
            notifier.updateProgress(progress)
            publishProgress(progress)
        }
        if (lastProgress.isCancelled) {
            notifier.dismissProgress()
        } else {
            notifier.showComplete(lastProgress.recognizedPages, lastProgress.emptyPages, lastProgress.failedPages)
        }
        Result.success()
    } catch (e: CancellationException) {
        // KMK: previously caught here and converted into Result.success() ("assume success
        // although cancelled"), which reported a cancelled run as succeeded to WorkManager and
        // any external observer of this work's WorkInfo. Rethrowing after the same cleanup lets
        // CoroutineWorker's own cancellation handling report the run as actually cancelled.
        markCancelled(lastProgress)
        notifier.dismissProgress()
        throw e
    } catch (e: Exception) {
        logUnexpectedError(e)
        markFailed(lastProgress.copy(isFailed = true, lastError = OcrErrorClassifier.classifyToStorageKey(e)))
        notifier.dismissProgress()
        Result.failure()
    } finally {
        markNotRunning()
    }
}

class OcrIndexWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    private val notifier = OcrNotifier(context)

    override suspend fun doWork(): Result {
        val scopeType = inputData.getString(KEY_SCOPE_TYPE)
        val mangaId = inputData.getLong(KEY_MANGA_ID, -1L)
        val retryModeStr = inputData.getString(KEY_RETRY_MODE) ?: RETRY_MODE_DEFAULT
        val maxPages = inputData.getInt(KEY_MAX_PAGES, 0)

        val scope: OcrScope = when (scopeType) {
            SCOPE_SINGLE_MANGA -> OcrScope.SingleManga(mangaId)
            else -> OcrScope.AllDownloaded
        }

        val retryMode: OcrRetryMode = when (retryModeStr) {
            RETRY_MODE_FORCE_ALL -> OcrRetryMode.FORCE_ALL
            else -> OcrRetryMode.SKIP_SUCCESS_RETRY_EMPTY_FAILED
        }

        setForegroundSafely()

        val service = OcrIndexService(context)

        return runOcrIndexWork(
            runIndexing = { onProgress -> service.runIndexing(scope, retryMode, maxPages, onProgress) },
            notifier = notifier,
            publishProgress = { OcrJobState.activeProgress.value = it },
            markCancelled = { OcrJobState.activeProgress.value = it.copy(isCancelled = true) },
            markFailed = { OcrJobState.activeProgress.value = it },
            markNotRunning = { OcrJobState.isRunning.value = false },
            logUnexpectedError = { e -> logcat(LogPriority.ERROR, e) { "OcrIndexWorker: unexpected error" } },
        )
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val progress = OcrJobState.activeProgress.value ?: OcrIndexProgress(isRunning = true)
        return ForegroundInfo(
            Notifications.ID_OCR_INDEX_PROGRESS,
            notifier.buildProgressNotification(progress).build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    companion object {
        private const val TAG_JOB = "OcrIndexWorker"
        private const val UNIQUE_WORK_NAME = "OcrIndexWorker:active"
        private const val KEY_SCOPE_TYPE = "scope_type"
        private const val KEY_MANGA_ID = "manga_id"
        private const val KEY_RETRY_MODE = "retry_mode"
        private const val KEY_MAX_PAGES = "max_pages"
        private const val SCOPE_SINGLE_MANGA = "single"
        private const val SCOPE_ALL = "all"
        private const val RETRY_MODE_DEFAULT = "default"
        private const val RETRY_MODE_FORCE_ALL = "force_all"

        fun isRunning(context: Context): Boolean =
            context.workManager.isRunning(TAG_JOB)

        fun start(
            context: Context,
            scope: OcrScope,
            retryMode: OcrRetryMode = OcrRetryMode.SKIP_SUCCESS_RETRY_EMPTY_FAILED,
            maxPages: Int = 0,
        ) {
            val scopeData = when (scope) {
                is OcrScope.SingleManga -> workDataOf(
                    KEY_SCOPE_TYPE to SCOPE_SINGLE_MANGA,
                    KEY_MANGA_ID to scope.mangaId,
                )
                OcrScope.AllDownloaded -> workDataOf(KEY_SCOPE_TYPE to SCOPE_ALL)
            }
            val retryData = workDataOf(
                KEY_RETRY_MODE to when (retryMode) {
                    OcrRetryMode.FORCE_ALL -> RETRY_MODE_FORCE_ALL
                    else -> RETRY_MODE_DEFAULT
                },
                KEY_MAX_PAGES to maxPages,
            )
            val data = Data.Builder()
                .putAll(scopeData)
                .putAll(retryData)
                .build()

            val request = OneTimeWorkRequestBuilder<OcrIndexWorker>()
                .addTag(TAG_JOB)
                .setInputData(data)
                .build()
            context.workManager.enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
            OcrJobState.isRunning.value = true
            OcrJobState.activeProgress.value = OcrIndexProgress(isRunning = true)
        }

        fun cancel(context: Context) {
            context.workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}

// KMK <--

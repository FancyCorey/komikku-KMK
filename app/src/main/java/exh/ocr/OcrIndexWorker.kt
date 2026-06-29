package exh.ocr

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
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
        var lastProgress = OcrIndexProgress()

        return try {
            service.runIndexing(scope, retryMode, maxPages) { progress ->
                lastProgress = progress
                notifier.updateProgress(progress)
                OcrJobState.activeProgress.value = progress
            }
            if (lastProgress.isCancelled) {
                notifier.dismissProgress()
            } else {
                notifier.showComplete(lastProgress.recognizedPages, lastProgress.emptyPages, lastProgress.failedPages)
            }
            Result.success()
        } catch (e: CancellationException) {
            OcrJobState.activeProgress.value = lastProgress.copy(isCancelled = true)
            notifier.dismissProgress()
            Result.success()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "OcrIndexWorker: unexpected error" }
            OcrJobState.activeProgress.value = lastProgress.copy(isFailed = true, lastError = e.message)
            notifier.dismissProgress()
            Result.failure()
        } finally {
            OcrJobState.isRunning.value = false
        }
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

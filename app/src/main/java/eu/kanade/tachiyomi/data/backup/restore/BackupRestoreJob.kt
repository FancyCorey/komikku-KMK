package eu.kanade.tachiyomi.data.backup.restore

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.tachiyomi.data.BackupRestoreStatus
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import exh.util.NonUndoableEvent
import exh.util.NonUndoableEventJournal
import exh.util.NonUndoableEventType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class BackupRestoreJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val notifier = BackupNotifier(context)

    // KMK -->
    private val backupRestoreStatus: BackupRestoreStatus = Injekt.get()
    // KMK <--

    override suspend fun doWork(): Result {
        val uri = inputData.getString(LOCATION_URI_KEY)?.toUri()
        val options = inputData.getBooleanArray(OPTIONS_KEY)?.let { RestoreOptions.fromBooleanArray(it) }

        if (uri == null || options == null) {
            return Result.failure()
        }

        // KMK -->
        backupRestoreStatus.start()
        // KMK <--

        val isSync = inputData.getBoolean(SYNC_KEY, false)

        setForegroundSafely()

        return try {
            val outcome = BackupRestorer(context, notifier, isSync).restore(uri, options)
            // KMK Code-Only Completion Plan 2026-07-31: a non-undoable Action History event is
            // recorded only for a manual (non-sync), fully successful restore -- never for a sync-
            // triggered restore (the user didn't consciously choose to restore a backup in that
            // case) and never for BackupRestoreOutcome.PartialSuccess (see that type's own doc for
            // why partial failure isn't represented as an event). Cancellation and ordinary failure
            // never reach this line at all, since restore() still throws for those exactly as
            // before this change. Decision itself lives in the pure, directly-testable
            // shouldRecordBackupRestoreEvent().
            if (shouldRecordBackupRestoreEvent(isSync, outcome)) {
                NonUndoableEventJournal.record(
                    NonUndoableEvent(
                        id = NonUndoableEvent.newId(),
                        timestamp = System.currentTimeMillis(),
                        eventType = NonUndoableEventType.BACKUP_RESTORED,
                    ),
                )
            }
            Result.success()
        } catch (e: CancellationException) {
            // KMK: previously caught here and converted into Result.success() ("Assume success
            // although cancelled") -- see LibraryUpdateJob.doWork() for the identical fix and its
            // full rationale. Rethrowing lets CoroutineWorker report the run as actually cancelled
            // instead of succeeded; the cancellation-specific notification is still shown first.
            notifier.showRestoreError(context.stringResource(MR.strings.restoring_backup_canceled))
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            notifier.showRestoreError(context.stringResource(MR.strings.restoring_backup_error))
            Result.failure()
        } finally {
            context.cancelNotification(Notifications.ID_RESTORE_PROGRESS)
            // KMK -->
            backupRestoreStatus.stop()
            // KMK <--
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_RESTORE_PROGRESS,
            notifier.showRestoreProgress().build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    companion object {
        fun isRunning(context: Context): Boolean {
            return context.workManager.isRunning(TAG)
        }

        fun start(
            context: Context,
            uri: Uri,
            options: RestoreOptions,
            sync: Boolean = false,
        ) {
            val inputData = workDataOf(
                LOCATION_URI_KEY to uri.toString(),
                SYNC_KEY to sync,
                OPTIONS_KEY to options.asBooleanArray(),
            )
            val request = OneTimeWorkRequestBuilder<BackupRestoreJob>()
                .addTag(TAG)
                .setInputData(inputData)
                .build()
            context.workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.KEEP, request)
        }

        fun stop(context: Context) {
            context.workManager.cancelUniqueWork(TAG)
            // KMK -->
            val backupRestoreStatus: BackupRestoreStatus = Injekt.get()
            runBlocking { backupRestoreStatus.stop() }
            // KMK <--
        }
    }
}

private const val TAG = "BackupRestore"

private const val LOCATION_URI_KEY = "location_uri" // String
private const val SYNC_KEY = "sync" // Boolean
private const val OPTIONS_KEY = "options" // BooleanArray

package eu.kanade.tachiyomi.data.backup.create

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreJob
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import exh.util.WorkerUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class BackupCreateJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val notifier = BackupNotifier(context)

    override suspend fun doWork(): Result {
        val isAutoBackup = inputData.getBoolean(IS_AUTO_BACKUP_KEY, true)

        if (isAutoBackup && BackupRestoreJob.isRunning(context)) return Result.retry()

        val uri = inputData.getString(LOCATION_URI_KEY)?.toUri()
            ?: getAutomaticBackupLocation()
            ?: return Result.failure()

        setForegroundSafely()

        val options = inputData.getBooleanArray(OPTIONS_KEY)?.let { BackupOptions.fromBooleanArray(it) }
            ?: BackupOptions()

        return try {
            val location = BackupCreator(context, isAutoBackup).backup(uri, options)
            if (!isAutoBackup) {
                notifier.showBackupComplete(UniFile.fromUri(context, location.toUri())!!)
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            if (!isAutoBackup) notifier.showBackupError(context.stringResource(MR.strings.creating_backup_error))
            Result.failure()
        } finally {
            context.cancelNotification(Notifications.ID_BACKUP_PROGRESS)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_BACKUP_PROGRESS,
            notifier.showBackupProgress().build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private fun getAutomaticBackupLocation(): Uri? {
        val storageManager = Injekt.get<StorageManager>()
        return storageManager.getAutomaticBackupsDirectory()?.uri
    }

    companion object {
        fun isManualJobRunning(context: Context): Boolean {
            return context.workManager.isRunning(TAG_MANUAL)
        }

        fun setupTask(context: Context, prefInterval: Int? = null) {
            val backupPreferences = Injekt.get<BackupPreferences>()
            val interval = prefInterval ?: backupPreferences.backupInterval().get()
            if (interval > 0) {
                val constraints = Constraints(
                    requiresBatteryNotLow = true,
                )

                val request = PeriodicWorkRequestBuilder<BackupCreateJob>(
                    interval.toLong(),
                    TimeUnit.HOURS,
                    10,
                    TimeUnit.MINUTES,
                )
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                    .addTag(TAG_AUTO)
                    .setConstraints(constraints)
                    .setInputData(workDataOf(IS_AUTO_BACKUP_KEY to true))
                    .build()

                context.workManager.enqueueUniquePeriodicWork(TAG_AUTO, ExistingPeriodicWorkPolicy.UPDATE, request)
            } else {
                context.workManager.cancelUniqueWork(TAG_AUTO)
            }
        }

        /**
         * @return the enqueued request's id, for [awaitManualJobTerminalState]. Note: because this
         * uses [ExistingWorkPolicy.KEEP], if a manual backup is already in flight under [TAG_MANUAL],
         * this new request is dropped and the returned id will not correspond to the job that is
         * actually running -- callers awaiting this id in that case will see it stay unfinished
         * (never observed by [awaitManualJobTerminalState], since [androidx.work.WorkManager] has no
         * record of a dropped request) rather than silently reporting a false terminal state.
         */
        fun startNow(context: Context, uri: Uri, options: BackupOptions): java.util.UUID {
            val inputData = workDataOf(
                IS_AUTO_BACKUP_KEY to false,
                LOCATION_URI_KEY to uri.toString(),
                OPTIONS_KEY to options.asBooleanArray(),
            )
            val request = OneTimeWorkRequestBuilder<BackupCreateJob>()
                .addTag(TAG_MANUAL)
                // KMK: `WorkInfo` (the
                // object every `getWorkInfosFor...` query returns) never exposes a job's input data --
                // only its id, state, tags, and output/progress data survive the query boundary. The
                // destination `uri` is therefore also added as a queryable tag (not only as input
                // data) so a later process (after this one died) can find the specific job that was
                // writing to a given `uri` via [findManualJobIdForUri] -- see that function's KDoc.
                .addTag(locationUriTag(uri))
                .setInputData(inputData)
                .build()
            context.workManager.enqueueUniqueWork(TAG_MANUAL, ExistingWorkPolicy.KEEP, request)
            return request.id
        }

        // KMK: closes the race where the
        // app process dies after [startNow] enqueues the manual backup job but before the caller
        // persists the returned request id (`BackupCleanupRecoveryStore.attachWorkRequest`) -- without
        // this, a durable recovery record left `IN_PROGRESS` with no attached id had no way to
        // discover whether a real WorkManager job was still actively writing to its `uri`, and treating
        // it as immediately resolved risked exposing Remove for a document a still-running job was
        // writing to. `TAG_MANUAL` is the *unique work name* -- `startNow` uses
        // `ExistingWorkPolicy.KEEP`, so at most one job can ever be enqueued/running under it at a
        // time -- but `getWorkInfosForUniqueWork` can still return more than one historical entry
        // before WorkManager prunes finished records, so this matches specifically on the `uri`-derived
        // tag (never on file paths, filenames, or any other content) and prefers a still-active match
        // over a finished one, so a genuinely active job is never mistaken for a stale finished one
        // that merely shares the unique work name.
        suspend fun findManualJobIdForUri(context: Context, uri: Uri): java.util.UUID? {
            return withContext(Dispatchers.IO) {
                val tag = locationUriTag(uri)
                val infos = context.workManager.getWorkInfosForUniqueWork(TAG_MANUAL).get()
                val matches = infos.filter { it.tags.contains(tag) }
                (matches.firstOrNull { !it.state.isFinished } ?: matches.firstOrNull())?.id
            }
        }

        /** Never contains anything beyond the SAF `uri` this build already persists/tags elsewhere. */
        private fun locationUriTag(uri: Uri): String = "$LOCATION_URI_TAG_PREFIX$uri"

        // KMK -->
        /**
         * Returns true if a periodic job is currently scheduled.
         * @param context The application context.
         * @return True if a periodic job is scheduled, false otherwise.
         * @throws Exception If there is an error retrieving the work info.
         */
        suspend fun isPeriodicBackupScheduled(context: Context): Boolean {
            return WorkerUtil.isPeriodicJobScheduled(context, TAG_AUTO)
        }

        // KMK: this
        // project depends on `work-runtime` only, not `work-runtime-ktx` (confirmed via
        // gradle/androidx.versions.toml), so no Flow-based WorkInfo observation is available -- polls
        // the same blocking `getWorkInfoById(id).get()` API `WorkManager.isRunning` already uses
        // elsewhere in this file, wrapped in Dispatchers.IO with a delay() loop. Returns null if
        // WorkManager has no record of [id] at all (e.g. a KEEP-dropped enqueue, see [startNow]'s
        // KDoc) rather than looping forever or fabricating a state.
        suspend fun awaitManualJobTerminalState(context: Context, id: java.util.UUID): WorkInfo.State? {
            return withContext(Dispatchers.IO) {
                var result: WorkInfo.State? = null
                var polling = true
                while (polling) {
                    ensureActive()
                    val info = context.workManager.getWorkInfoById(id).get()
                    if (info == null || info.state.isFinished) {
                        result = info?.state
                        polling = false
                    } else {
                        delay(500)
                    }
                }
                result
            }
        }
        // KMK <--
    }
}

private const val TAG_AUTO = "BackupCreator"
private const val TAG_MANUAL = "$TAG_AUTO:manual"
private const val LOCATION_URI_TAG_PREFIX = "$TAG_MANUAL:location_uri:"

private const val IS_AUTO_BACKUP_KEY = "is_auto_backup" // Boolean
private const val LOCATION_URI_KEY = "location_uri" // String
private const val OPTIONS_KEY = "options" // BooleanArray

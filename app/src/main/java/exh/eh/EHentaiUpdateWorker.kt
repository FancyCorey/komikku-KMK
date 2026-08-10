package exh.eh

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.data.library.LibraryUpdateNotifier
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.source.online.all.EHentai
import eu.kanade.tachiyomi.util.system.isConnectedToWifi
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import exh.debug.DebugToggles
import exh.eh.EHentaiUpdateWorkerConstants.UPDATES_PER_ITERATION
import exh.log.ResettableLogger
import exh.log.safeXLogTag
import exh.metadata.metadata.EHentaiSearchMetadata
import exh.source.ExhPreferences
import exh.util.cancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_CHARGING
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_NETWORK_NOT_METERED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_ONLY_ON_WIFI
import tachiyomi.domain.manga.interactor.GetExhFavoriteMangaWithMetadata
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.interactor.InsertFlatMetadata
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.days

class EHentaiUpdateWorker(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {
    private val exhPreferences: ExhPreferences by injectLazy()
    private val libraryPreferences: LibraryPreferences by injectLazy()
    private val sourceManager: SourceManager by injectLazy()
    private val updateHelper: EHentaiUpdateHelper by injectLazy()
    private val logger = ResettableLogger { safeXLogTag() }
    private val updateMangaFromRemote: UpdateMangaFromRemote by injectLazy()
    private val getChaptersByMangaId: GetChaptersByMangaId by injectLazy()
    private val getFlatMetadataById: GetFlatMetadataById by injectLazy()
    private val insertFlatMetadata: InsertFlatMetadata by injectLazy()
    private val getExhFavoriteMangaWithMetadata: GetExhFavoriteMangaWithMetadata by injectLazy()

    private val updateNotifier by lazy { EHentaiUpdateNotifier(context) }
    private val libraryUpdateNotifier by lazy { LibraryUpdateNotifier(context) }

    // KMK -->
    /**
     * This worker is enqueued with [ExistingPeriodicWorkPolicy] ([scheduleBackground]), so it is
     * periodic work, not one-time work. WorkManager 2.7+ (this project pins 2.11.1, see
     * `gradle/androidx.versions.toml`) supports [Result.retry] for periodic work: a retried run is
     * re-attempted within the current period using the request's backoff policy, and only once
     * [runAttemptCount] exceeds [MAX_RUN_ATTEMPTS] do we fall back to [Result.failure] -- which,
     * for periodic work, only marks *this run* failed and does not cancel or unschedule future
     * periodic runs. Silently returning [Result.success] on a real failure (the previous
     * behavior) was untruthful: it told WorkManager the run succeeded, discarding its own
     * retry/backoff machinery and any external observer of this work's result.
     */
    override suspend fun doWork(): Result {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P &&
                requiresWifiConnection(exhPreferences) &&
                !context.isConnectedToWifi()
            ) {
                logger()?.d("Required Wi-Fi connection unavailable, retrying with WorkManager backoff...")
                Result.retry()
            } else {
                setForegroundSafely()
                startUpdating()
                logger()?.d("Update job completed!")
                Result.success()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            when (EHentaiUpdateWorkerResultPolicy.classify(runAttemptCount, MAX_RUN_ATTEMPTS)) {
                EHentaiUpdateWorkerResultPolicy.Outcome.Retry -> {
                    logger()?.d("Update failed (attempt ${runAttemptCount + 1}/$MAX_RUN_ATTEMPTS), retrying with WorkManager backoff...", e)
                    Result.retry()
                }
                EHentaiUpdateWorkerResultPolicy.Outcome.Failure -> {
                    logger()?.e("Update failed after $MAX_RUN_ATTEMPTS attempts, giving up until the next periodic run.", e)
                    Result.failure()
                }
            }
        } finally {
            updateNotifier.cancelProgressNotification()
        }
    }
    // KMK <--

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_EHENTAI_PROGRESS,
            updateNotifier.progressNotificationBuilder.build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private suspend fun startUpdating() {
        logger()?.d("Update job started!")
        val startTime = System.currentTimeMillis()

        logger()?.d("Finding manga with metadata...")
        val metadataManga = getExhFavoriteMangaWithMetadata.await()

        logger()?.d("Filtering manga and raising metadata...")
        val curTime = System.currentTimeMillis()
        val allMeta = metadataManga.asFlow().cancellable().mapNotNull { manga ->
            val meta = getFlatMetadataById.await(manga.id)
                ?: return@mapNotNull null

            val raisedMeta = meta.raise<EHentaiSearchMetadata>()

            // Don't update galleries too frequently
            if (raisedMeta.aged ||
                (
                    curTime - raisedMeta.lastUpdateCheck < MIN_BACKGROUND_UPDATE_FREQ &&
                        DebugToggles.RESTRICT_EXH_GALLERY_UPDATE_CHECK_FREQUENCY.enabled
                    )
            ) {
                return@mapNotNull null
            }

            val chapter = getChaptersByMangaId.await(manga.id).minByOrNull {
                it.dateUpload
            }

            UpdateEntry(manga, raisedMeta, chapter)
        }.toList().sortedBy { it.meta.lastUpdateCheck }

        logger()?.d("Found %s manga to update, starting updates!", allMeta.size)
        val mangaMetaToUpdateThisIter = allMeta.take(UPDATES_PER_ITERATION)

        var failuresThisIteration = 0
        var updatedThisIteration = 0
        val updatedManga = mutableListOf<Pair<Manga, Array<Chapter>>>()
        val modifiedThisIteration = mutableSetOf<Long>()

        try {
            for ((index, entry) in mangaMetaToUpdateThisIter.withIndex()) {
                val (manga, meta) = entry
                if (failuresThisIteration > MAX_UPDATE_FAILURES) {
                    logger()?.w("Too many update failures, aborting...")
                    break
                }

                logger()?.d(
                    "Updating gallery (index: %s, manga.id: %s, meta.gId: %s, meta.gToken: %s, failures-so-far: %s, modifiedThisIteration.size: %s)...",
                    index,
                    manga.id,
                    meta.gId,
                    meta.gToken,
                    failuresThisIteration,
                    modifiedThisIteration.size,
                )

                if (manga.id in modifiedThisIteration) {
                    // We already processed this manga!
                    logger()?.w("Gallery already updated this iteration, skipping...")
                    updatedThisIteration++
                    continue
                }

                val (new, chapters) = try {
                    updateNotifier.showProgressNotification(
                        manga,
                        updatedThisIteration + failuresThisIteration,
                        mangaMetaToUpdateThisIter.size,
                    )
                    updateEntryAndGetChapters(manga)
                } catch (e: GalleryNotUpdatedException) {
                    if (e.network) {
                        failuresThisIteration++

                        logger()?.e("> Network error while updating gallery!", e)
                        logger()?.e(
                            "> (manga.id: %s, meta.gId: %s, meta.gToken: %s, failures-so-far: %s)",
                            manga.id,
                            meta.gId,
                            meta.gToken,
                            failuresThisIteration,
                        )
                    }

                    continue
                }

                if (chapters.isEmpty()) {
                    logger()?.e(
                        "No chapters found for gallery (manga.id: %s, meta.gId: %s, meta.gToken: %s, failures-so-far: %s)!",
                        manga.id,
                        meta.gId,
                        meta.gToken,
                        failuresThisIteration,
                    )

                    continue
                }

                // Find accepted root and discard others
                val (acceptedRoot, discardedRoots, exhNew) =
                    updateHelper.findAcceptedRootAndDiscardOthers(manga.source, chapters)

                if (new.isNotEmpty() && manga.id == acceptedRoot.manga.id) {
                    libraryPreferences.newUpdatesCount().getAndSet { it + new.size }
                    updatedManga += acceptedRoot.manga to new.toTypedArray()
                } else if (exhNew.isNotEmpty() && updatedManga.none { it.first.id == acceptedRoot.manga.id }) {
                    libraryPreferences.newUpdatesCount().getAndSet { it + exhNew.size }
                    updatedManga += acceptedRoot.manga to exhNew.toTypedArray()
                }

                modifiedThisIteration += acceptedRoot.manga.id
                modifiedThisIteration += discardedRoots.map { it.manga.id }
                updatedThisIteration++
            }
        } finally {
            exhPreferences.exhAutoUpdateStats().set(
                Json.encodeToString(
                    EHentaiUpdaterStats(
                        startTime,
                        allMeta.size,
                        updatedThisIteration,
                    ),
                ),
            )

            updateNotifier.cancelProgressNotification()
            if (updatedManga.isNotEmpty()) {
                libraryUpdateNotifier.showUpdateNotifications(updatedManga)
            }
        }
    }

    // New, current
    private suspend fun updateEntryAndGetChapters(manga: Manga): Pair<List<Chapter>, List<Chapter>> {
        val source = sourceManager.get(manga.source) as? EHentai
            ?: throw GalleryNotUpdatedException(false, IllegalStateException("Missing EH-based source (${manga.source})!"))

        try {
            val result = updateMangaFromRemote(
                source = source,
                manga = manga,
                fetchDetails = true,
                fetchChapters = true,
            ).getOrThrow()
            return result.newChapters to getChaptersByMangaId.await(manga.id)
        } catch (t: Throwable) {
            if (t is EHentai.GalleryNotFoundException) {
                val meta = getFlatMetadataById.await(manga.id)?.raise<EHentaiSearchMetadata>()
                if (meta != null) {
                    // Age dead galleries
                    logger()?.d("Aged %s - notfound", manga.id)
                    meta.aged = true
                    insertFlatMetadata.await(meta)
                }
                throw GalleryNotUpdatedException(false, t)
            }
            throw GalleryNotUpdatedException(true, t)
        }
    }

    companion object {
        // KMK: bound on doWork() Result.retry() attempts before falling back to Result.failure()
        // for this periodic run -- see doWork()'s doc comment.
        const val MAX_RUN_ATTEMPTS = 3

        private const val MAX_UPDATE_FAILURES = 5

        private val MIN_BACKGROUND_UPDATE_FREQ = 1.days.inWholeMilliseconds

        private const val TAG = "EHBackgroundUpdater"

        private val logger = ResettableLogger { safeXLogTag("EHUpdaterScheduler") }

        fun launchBackgroundTest(context: Context) {
            context.workManager.enqueue(
                OneTimeWorkRequestBuilder<EHentaiUpdateWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .addTag(TAG)
                    .build(),
            )
        }

        fun scheduleBackground(context: Context, prefInterval: Int? = null, prefRestrictions: Set<String>? = null) {
            val exhPreferences = Injekt.get<ExhPreferences>()
            val interval = prefInterval ?: exhPreferences.exhAutoUpdateFrequency().get()
            if (interval > 0) {
                val restrictions = prefRestrictions ?: exhPreferences.exhAutoUpdateRequirements().get()
                val networkType = if (DEVICE_NETWORK_NOT_METERED in restrictions) {
                    NetworkType.UNMETERED
                } else {
                    NetworkType.CONNECTED
                }
                val networkRequest = NetworkRequest.Builder().apply {
                    removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    if (DEVICE_ONLY_ON_WIFI in restrictions) {
                        addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    }
                    if (DEVICE_NETWORK_NOT_METERED in restrictions) {
                        addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                    }
                }
                    .build()

                val constraints = Constraints.Builder()
                    // 'networkRequest' only applies to Android 9+, otherwise 'networkType' is used
                    .setRequiredNetworkRequest(networkRequest, networkType)
                    .setRequiresCharging(DEVICE_CHARGING in restrictions)
                    .setRequiresBatteryNotLow(true)
                    .build()

                val request = PeriodicWorkRequestBuilder<EHentaiUpdateWorker>(
                    interval.toLong(),
                    TimeUnit.HOURS,
                    10,
                    TimeUnit.MINUTES,
                )
                    .addTag(TAG)
                    .setConstraints(constraints)
                    .build()

                context.workManager.enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.UPDATE, request)
                logger()?.d("Successfully scheduled background update job!")
            } else {
                cancelBackground(context)
            }
        }

        fun cancelBackground(context: Context) {
            context.workManager.cancelAllWorkByTag(TAG)
        }
    }

    private fun requiresWifiConnection(exhPreferences: ExhPreferences): Boolean {
        val restrictions = exhPreferences.exhAutoUpdateRequirements().get()
        return DEVICE_ONLY_ON_WIFI in restrictions
    }
}

data class UpdateEntry(val manga: Manga, val meta: EHentaiSearchMetadata, val rootChapter: Chapter?)

object EHentaiUpdateWorkerConstants {
    const val UPDATES_PER_ITERATION = 50

    val GALLERY_AGE_TIME = 365.days.inWholeMilliseconds
}

// KMK -->
/**
 * Pure decision boundary for [EHentaiUpdateWorker.doWork]'s ordinary-exception path: below the
 * attempt cap, WorkManager should retry this periodic run with backoff; at or beyond the cap, the
 * run should report [Result.failure] instead so [runAttemptCount] never grows unbounded and a
 * genuinely permanent failure eventually surfaces rather than retrying forever.
 */
object EHentaiUpdateWorkerResultPolicy {
    sealed interface Outcome {
        data object Retry : Outcome
        data object Failure : Outcome
    }

    fun classify(runAttemptCount: Int, maxAttempts: Int): Outcome =
        if (runAttemptCount < maxAttempts) Outcome.Retry else Outcome.Failure
}
// KMK <--

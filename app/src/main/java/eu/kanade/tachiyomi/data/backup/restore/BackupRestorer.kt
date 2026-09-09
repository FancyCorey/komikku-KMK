package eu.kanade.tachiyomi.data.backup.restore

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.BackupDecoder
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupAlternateSourceBridge
import eu.kanade.tachiyomi.data.backup.models.BackupAlternateSourceBridgeMapping
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupCrossSourceGroupPrimary
import eu.kanade.tachiyomi.data.backup.models.BackupCrossSourceIdentityDecision
import eu.kanade.tachiyomi.data.backup.models.BackupCrossSourceMangaLink
import eu.kanade.tachiyomi.data.backup.models.BackupDisabledRecommendationSource
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.BackupFeed
import eu.kanade.tachiyomi.data.backup.models.BackupLocalTrackedWork
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupMangaSourceQualitySignal
import eu.kanade.tachiyomi.data.backup.models.BackupMangaTaste
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSavedFocusMode
import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import eu.kanade.tachiyomi.data.backup.models.BackupSeenMangaKey
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BackupTagAlias
import eu.kanade.tachiyomi.data.backup.models.BackupTagTaste
import eu.kanade.tachiyomi.data.backup.restore.restorers.CategoriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.ExtensionStoreRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.FeedRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.LocalTrackerBackupRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.PreferenceRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.SavedSearchRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.TasteRestorer
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupRestorer(
    private val context: Context,
    private val notifier: BackupNotifier,
    private val isSync: Boolean,

    private val categoriesRestorer: CategoriesRestorer = CategoriesRestorer(),
    private val preferenceRestorer: PreferenceRestorer = PreferenceRestorer(context),
    private val extensionStoreRestorer: ExtensionStoreRestorer = ExtensionStoreRestorer(),
    private val mangaRestorer: MangaRestorer = MangaRestorer(isSync),
    // SY -->
    private val savedSearchRestorer: SavedSearchRestorer = SavedSearchRestorer(),
    // SY <--
    // KMK -->
    private val feedRestorer: FeedRestorer = FeedRestorer(),
    private val tasteRestorer: TasteRestorer = TasteRestorer(),
    private val localTrackerBackupRestorer: LocalTrackerBackupRestorer = LocalTrackerBackupRestorer(Injekt.get()),
    // KMK <--
) {

    private var restoreAmount = 0
    private var restoreProgress = 0
    private val bookkeepingMutex = Mutex()
    private val errors = mutableListOf<Pair<Date, String>>()

    /**
     * Mapping of source ID to source name from backup data
     */
    private var sourceMapping: Map<Long, String> = emptyMap()

    // KMK Code-Only Completion Plan 2026-07-31: returns a typed BackupRestoreOutcome instead of Unit
    // so a caller can distinguish a fully clean restore from one where individual items failed --
    // see BackupRestoreOutcome's own doc. This does NOT change this function's existing exception
    // propagation: restoreFromFile() still throws normally (cancellation or an unisolated-section
    // failure) exactly as before; only the no-exception completion path now carries a typed result.
    suspend fun restore(uri: Uri, options: RestoreOptions): BackupRestoreOutcome {
        val startTime = System.currentTimeMillis()

        restoreFromFile(uri, options)

        val time = System.currentTimeMillis() - startTime

        val logFile = writeErrorLog()

        notifier.showRestoreComplete(
            time,
            errors.size,
            logFile.parent,
            logFile.name,
            isSync,
        )

        return if (errors.isEmpty()) {
            BackupRestoreOutcome.Success(restoreProgress)
        } else {
            BackupRestoreOutcome.PartialSuccess(restoreProgress, errors.size)
        }
    }

    private suspend fun restoreFromFile(uri: Uri, options: RestoreOptions) {
        val backup = BackupDecoder(context).decode(uri)
        restoreDecodedBackup(backup, options)
    }

    // KMK AG15-F4: the file-decoding boundary (BackupDecoder against a real content Uri) is not
    // unit-testable without Robolectric, but everything past it is a plain function of an already
    // decoded Backup -- split out so an orchestration test can drive it directly with a Backup built
    // in Kotlin (or round-tripped through real protobuf, like TasteBackupEndToEndRoundTripTest does
    // for the taste-only path), without needing a real Context/Uri/ContentResolver.
    internal suspend fun restoreDecodedBackup(backup: Backup, options: RestoreOptions) {
        // Store source mapping for error messages
        val backupMaps = backup.backupSources
        sourceMapping = backupMaps.associate { it.sourceId to it.name }

        if (options.libraryEntries) {
            restoreAmount += backup.backupManga.size
        }
        if (options.categories) {
            restoreAmount += 1
        }
        // SY -->
        if (options.savedSearchesFeeds) {
            restoreAmount += 1
        }
        // SY <--
        // KMK -->
        if (options.tasteProfile) {
            restoreAmount += 1
        }
        // KMK <--
        if (options.appSettings) {
            restoreAmount += 1
        }
        if (options.extensionStores) {
            restoreAmount += backup.backupExtensionStores.size
        }
        if (options.sourceSettings) {
            restoreAmount += 1
        }
        if (options.localTracker && backup.backupLocalTrackedWorks.isNotEmpty()) {
            restoreAmount += 1
        }

        coroutineScope {
            if (options.categories) {
                restoreCategories(backup.backupCategories)
            }
            // SY -->
            if (options.savedSearchesFeeds) {
                restoreSavedSearches(
                    backup.backupSavedSearches,
                    // KMK -->
                    backup.backupFeeds,
                    // KMK <--
                )
            }
            // SY <--
            if (options.appSettings) {
                restoreAppPreferences(backup.backupPreferences, backup.backupCategories.takeIf { options.categories })
            }
            if (options.sourceSettings) {
                restoreSourcePreferences(backup.backupSourcePreferences)
            }
            // KMK -->
            val mangaJob =
                // KMK <--
                if (options.libraryEntries) {
                    restoreManga(backup.backupManga, if (options.categories) backup.backupCategories else emptyList())
                    // KMK -->
                } else {
                    null
                    // KMK <--
                }
            if (options.extensionStores) {
                restoreExtensionStores(backup.backupExtensionStores)
            }
            // KMK -->
            if (options.tasteProfile) {
                restoreTasteProfile(
                    backup.backupMangaTastes,
                    backup.backupTagTastes,
                    backup.backupTagAliases,
                    backup.backupDisabledRecommendationSources,
                    // KMK --> v0.7.0: Phase 4
                    backup.backupCrossSourceMangaLinks,
                    // KMK <--
                    // KMK --> v0.7.16: Best Version quality signals
                    backup.backupMangaSourceQualitySignals,
                    // KMK <--
                    // KMK --> v0.7.28: seen manga keys
                    backup.backupSeenMangaKeys,
                    // KMK <--
                    // KMK --> v0.8.1-fix1: user-selected primary version per confirmed link group
                    backup.backupCrossSourceGroupPrimaries,
                    // KMK <--
                    backup.backupCrossSourceIdentityDecisions,
                    backup.backupAlternateSourceBridges,
                    backup.backupAlternateSourceBridgeMappings,
                    backup.backupSavedFocusModes,
                    mangaJob,
                )
            }
            if (options.localTracker && backup.backupLocalTrackedWorks.isNotEmpty()) {
                restoreLocalTracker(backup.backupLocalTrackedWorks)
            }
            // KMK <--

            // TODO: optionally trigger online library + tracker update
        }
    }

    private suspend fun recordError(message: String) {
        bookkeepingMutex.withLock {
            errors.add(Date() to message)
        }
    }

    private suspend fun recordProgress(label: String) {
        val progress = bookkeepingMutex.withLock {
            restoreProgress += 1
            restoreProgress
        }
        with(notifier) {
            showRestoreProgress(label, progress, restoreAmount, isSync)
                .show(Notifications.ID_RESTORE_PROGRESS)
        }
    }

    context(scope: CoroutineScope)
    private /* KMK --> */suspend /* KMK <-- */ fun restoreCategories(backupCategories: List<BackupCategory>) {
        scope.ensureActive()
        categoriesRestorer(backupCategories)

        recordProgress(context.stringResource(MR.strings.categories))
    }

    // SY -->
    private fun CoroutineScope.restoreSavedSearches(
        backupSavedSearches: List<BackupSavedSearch>,
        // KMK -->
        backupFeeds: List<BackupFeed>,
        // KMK <--
    ) = launch {
        ensureActive()
        savedSearchRestorer.restoreSavedSearches(backupSavedSearches)
        // KMK -->
        feedRestorer.restoreFeeds(backupFeeds)
        // KMK <--

        recordProgress(context.stringResource(KMR.strings.saved_searches_feeds))
    }
    // SY <--

    private fun CoroutineScope.restoreManga(
        backupMangas: List<BackupManga>,
        backupCategories: List<BackupCategory>,
    ) = launch {
        mangaRestorer.sortByNew(backupMangas)
            .forEach {
                ensureActive()

                try {
                    mangaRestorer.restore(it, backupCategories)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val sourceName = sourceMapping[it.source] ?: it.source.toString()
                    recordError("${it.title} [$sourceName]: ${context.stringResource(MR.strings.unknown_error)}")
                }

                recordProgress(it.title)
            }
    }

    private fun CoroutineScope.restoreAppPreferences(
        preferences: List<BackupPreference>,
        categories: List<BackupCategory>?,
    ) = launch {
        ensureActive()
        preferenceRestorer.restoreApp(
            preferences,
            categories,
        )

        recordProgress(context.stringResource(MR.strings.app_settings))
    }

    private fun CoroutineScope.restoreSourcePreferences(preferences: List<BackupSourcePreferences>) = launch {
        ensureActive()
        preferenceRestorer.restoreSource(preferences)

        recordProgress(context.stringResource(MR.strings.source_settings))
    }

    private fun CoroutineScope.restoreExtensionStores(
        backupExtensionStores: List<BackupExtensionStore>,
    ) = launch {
        backupExtensionStores
            .forEach {
                ensureActive()

                try {
                    extensionStoreRestorer(it)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    recordError("Error Adding Repo: ${it.name} : ${context.stringResource(MR.strings.unknown_error)}")
                }

                recordProgress(context.stringResource(MR.strings.extensionStores))
            }
    }

    // KMK -->
    private fun CoroutineScope.restoreTasteProfile(
        backupMangaTastes: List<BackupMangaTaste>,
        backupTagTastes: List<BackupTagTaste>,
        backupTagAliases: List<BackupTagAlias>,
        backupDisabledSources: List<BackupDisabledRecommendationSource>,
        // KMK --> v0.7.0: Phase 4
        backupCrossSourceMangaLinks: List<BackupCrossSourceMangaLink>,
        // KMK <--
        // KMK --> v0.7.16: Best Version quality signals
        backupMangaSourceQualitySignals: List<BackupMangaSourceQualitySignal>,
        // KMK <--
        // KMK --> v0.7.28: seen manga keys
        backupSeenMangaKeys: List<BackupSeenMangaKey>,
        // KMK <--
        // KMK --> v0.8.1-fix1: user-selected primary version per confirmed link group
        backupCrossSourceGroupPrimaries: List<BackupCrossSourceGroupPrimary>,
        // KMK <--
        backupCrossSourceIdentityDecisions: List<BackupCrossSourceIdentityDecision>,
        backupAlternateSourceBridges: List<BackupAlternateSourceBridge>,
        backupAlternateSourceBridgeMappings: List<BackupAlternateSourceBridgeMapping>,
        // KMK v0.8.21-fix2: AUG-14 slice 3 -- saved For You focus modes
        backupSavedFocusModes: List<BackupSavedFocusMode>,
        mangaJob: Job?,
    ) = launch {
        // Manga tastes resolve by (url, source) — wait until library entries exist locally
        mangaJob?.join()
        ensureActive()
        // KMK F2-02: delegates to TasteRestorer.restoreTasteProfileBundle -- the exact same call
        // sequence that used to be duplicated inline here, now a single owned/directly-testable
        // function (see its own doc comment for why this extraction closed a real coverage gap).
        val tasteErrors = tasteRestorer.restoreTasteProfileBundle(
            backupMangaTastes = backupMangaTastes,
            backupTagTastes = backupTagTastes,
            backupTagAliases = backupTagAliases,
            backupDisabledSources = backupDisabledSources,
            backupCrossSourceMangaLinks = backupCrossSourceMangaLinks,
            backupCrossSourceGroupPrimaries = backupCrossSourceGroupPrimaries,
            backupCrossSourceIdentityDecisions = backupCrossSourceIdentityDecisions,
            backupAlternateSourceBridges = backupAlternateSourceBridges,
            backupAlternateSourceBridgeMappings = backupAlternateSourceBridgeMappings,
            backupMangaSourceQualitySignals = backupMangaSourceQualitySignals,
            backupSeenMangaKeys = backupSeenMangaKeys,
            backupSavedFocusModes = backupSavedFocusModes,
        )
        tasteErrors.forEach { recordError(it) }

        recordProgress(context.stringResource(KMR.strings.taste_backup_option))
    }
    // KMK <--

    private fun CoroutineScope.restoreLocalTracker(backupLocalTrackedWorks: List<BackupLocalTrackedWork>) = launch {
        ensureActive()
        localTrackerBackupRestorer.restore(backupLocalTrackedWorks).forEach { error ->
            recordError(error)
        }
        recordProgress(context.stringResource(KMR.strings.local_tracker_backup_option))
    }

    private fun writeErrorLog(): File {
        try {
            if (errors.isNotEmpty()) {
                val file = context.createFileInCacheDir("komikku_restore_error.txt")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

                file.bufferedWriter().use { out ->
                    errors.forEach { (date, message) ->
                        out.write("[${sdf.format(date)}] $message\n")
                    }
                }
                return file
            }
        } catch (_: Exception) {
            // Empty
        }
        return File("")
    }
}

package eu.kanade.tachiyomi.data.backup.create

import android.content.Context
import android.net.Uri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.backup.BackupFileValidator
import eu.kanade.tachiyomi.data.backup.create.creators.CategoriesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.ExtensionStoresBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.FeedBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.LocalTrackerBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.MangaBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.PreferenceBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.SavedSearchBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.SourcesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.TasteBackupCreator
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
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BackupTagAlias
import eu.kanade.tachiyomi.data.backup.models.BackupTagTaste
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import okio.buffer
import okio.gzip
import okio.sink
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.interactor.GetMergedManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

internal fun writeBackupFile(file: UniFile, byteArray: ByteArray) {
    file.openOutputStream()
        .also {
            // Force overwrite old file
            (it as? FileOutputStream)?.channel?.truncate(0)
        }
        .sink().gzip().buffer().use {
            it.write(byteArray)
        }
}

fun interface BackupFileWriter {
    fun write(file: UniFile, byteArray: ByteArray)
}

class BackupCreator(
    private val context: Context,
    private val isAutoBackup: Boolean,

    private val parser: ProtoBuf = Injekt.get(),
    private val getFavorites: GetFavorites = Injekt.get(),
    private val backupPreferences: BackupPreferences = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),

    private val categoriesBackupCreator: CategoriesBackupCreator = CategoriesBackupCreator(),
    private val mangaBackupCreator: MangaBackupCreator = MangaBackupCreator(),
    private val preferenceBackupCreator: PreferenceBackupCreator = PreferenceBackupCreator(),
    private val extensionStoresBackupCreator: ExtensionStoresBackupCreator = ExtensionStoresBackupCreator(),
    private val sourcesBackupCreator: SourcesBackupCreator = SourcesBackupCreator(),
    // KMK -->
    private val feedBackupCreator: FeedBackupCreator = FeedBackupCreator(),
    private val tasteBackupCreator: TasteBackupCreator = TasteBackupCreator(),
    private val localTrackerBackupCreator: LocalTrackerBackupCreator = LocalTrackerBackupCreator(),
    // KMK <--
    // SY -->
    private val savedSearchBackupCreator: SavedSearchBackupCreator = SavedSearchBackupCreator(),
    private val getMergedManga: GetMergedManga = Injekt.get(),
    // SY <--
    internal val fileWriter: BackupFileWriter = BackupFileWriter(::writeBackupFile),
) {

    suspend fun backup(uri: Uri, options: BackupOptions): String {
        var file: UniFile? = null
        try {
            file = if (isAutoBackup) {
                // Get dir of file and create
                val dir = UniFile.fromUri(context, uri)

                // Delete older backups
                dir?.listFiles { _, filename -> FILENAME_REGEX.matches(filename) }
                    .orEmpty()
                    .sortedByDescending { it.name }
                    .drop(MAX_AUTO_BACKUPS - 1)
                    .forEach { it.delete() }

                // Create new file to place backup
                dir?.createFile(getFilename())
            } else {
                UniFile.fromUri(context, uri)
            }

            if (file == null || !file.isFile) {
                throw IllegalStateException(context.stringResource(MR.strings.create_backup_file_error))
            }

            val nonFavoriteManga = if (options.readEntries) mangaRepository.getReadMangaNotInLibrary() else emptyList()
            // SY -->
            val mergedManga = getMergedManga.await()
            // SY <--
            val backupManga =
                backupMangas(getFavorites.await() + nonFavoriteManga /* SY --> */ + mergedManga /* SY <-- */, options)

            val backup = Backup(
                backupManga = backupManga,
                backupCategories = backupCategories(options),
                backupSources = backupSources(backupManga),
                backupPreferences = backupAppPreferences(options),
                backupExtensionStores = backupExtensionStores(options),
                backupSourcePreferences = backupSourcePreferences(options),

                // SY -->
                backupSavedSearches = backupSavedSearches(options),
                // SY <--

                // KMK -->
                backupFeeds = backupFeeds(options),
                backupMangaTastes = backupMangaTastes(options),
                backupTagTastes = backupTagTastes(options),
                backupTagAliases = backupTagAliases(options),
                backupDisabledRecommendationSources = backupDisabledRecommendationSources(options),
                // KMK --> v0.7.0: Phase 4
                backupCrossSourceMangaLinks = backupCrossSourceMangaLinks(options),
                // KMK <--
                // KMK --> v0.7.28: seen manga keys
                backupSeenMangaKeys = backupSeenMangaKeys(options),
                // KMK <--
                // KMK --> v0.8.1-fix1: user-selected primary version per confirmed link group
                backupCrossSourceGroupPrimaries = backupCrossSourceGroupPrimaries(options),
                // KMK <--
                backupCrossSourceIdentityDecisions = backupCrossSourceIdentityDecisions(options),
                backupAlternateSourceBridges = backupAlternateSourceBridges(options),
                backupAlternateSourceBridgeMappings = backupAlternateSourceBridgeMappings(options),
                backupLocalTrackedWorks = backupLocalTrackedWorks(options),
                backupSavedFocusModes = backupSavedFocusModes(options),
                // KMK <--
            )

            val byteArray = parser.encodeToByteArray(Backup.serializer(), backup)
            if (byteArray.isEmpty()) {
                throw IllegalStateException(context.stringResource(MR.strings.empty_backup_error))
            }

            fileWriter.write(file, byteArray)
            val fileUri = file.uri

            // Make sure it's a valid backup file
            BackupFileValidator(context).validate(fileUri)

            if (isAutoBackup) {
                backupPreferences.lastAutoBackupTimestamp().set(Instant.now().toEpochMilli())
            }

            return fileUri.toString()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            file?.delete()
            throw e
        }
    }

    suspend fun backupCategories(options: BackupOptions): List<BackupCategory> {
        if (!options.categories) return emptyList()

        return categoriesBackupCreator()
    }

    suspend fun backupMangas(mangas: List<Manga>, options: BackupOptions): List<BackupManga> {
        if (!options.libraryEntries) return emptyList()

        return mangaBackupCreator(mangas, options)
    }

    fun backupSources(mangas: List<BackupManga>): List<BackupSource> {
        return sourcesBackupCreator(mangas)
    }

    /* KMK --> */ suspend /* KMK <-- */ fun backupAppPreferences(options: BackupOptions): List<BackupPreference> {
        if (!options.appSettings) return emptyList()

        return preferenceBackupCreator.createApp(includePrivatePreferences = options.privateSettings)
    }

    suspend fun backupExtensionStores(options: BackupOptions): List<BackupExtensionStore> {
        if (!options.extensionStores) return emptyList()

        return extensionStoresBackupCreator()
    }

    fun backupSourcePreferences(options: BackupOptions): List<BackupSourcePreferences> {
        if (!options.sourceSettings) return emptyList()

        return preferenceBackupCreator.createSource(includePrivatePreferences = options.privateSettings)
    }

    // SY -->
    suspend fun backupSavedSearches(options: BackupOptions): List<BackupSavedSearch> {
        if (!options.savedSearchesFeeds) return emptyList()

        return savedSearchBackupCreator()
    }
    // SY <--

    // KMK -->
    /**
     * Backup global Popular/Latest feeds
     */
    suspend fun backupFeeds(options: BackupOptions): List<BackupFeed> {
        if (!options.savedSearchesFeeds) return emptyList()

        return feedBackupCreator()
    }

    suspend fun backupMangaTastes(options: BackupOptions): List<BackupMangaTaste> {
        if (!options.tasteProfile) return emptyList()
        return tasteBackupCreator.backupMangaTastes()
    }

    suspend fun backupTagTastes(options: BackupOptions): List<BackupTagTaste> {
        if (!options.tasteProfile) return emptyList()
        return tasteBackupCreator.backupTagTastes()
    }

    suspend fun backupTagAliases(options: BackupOptions): List<BackupTagAlias> {
        if (!options.tasteProfile) return emptyList()
        return tasteBackupCreator.backupTagAliases()
    }

    suspend fun backupDisabledRecommendationSources(options: BackupOptions): List<BackupDisabledRecommendationSource> {
        if (!options.tasteProfile) return emptyList()
        return tasteBackupCreator.backupDisabledRecommendationSources()
    }

    // KMK --> v0.7.0: Phase 4
    suspend fun backupCrossSourceMangaLinks(options: BackupOptions): List<BackupCrossSourceMangaLink> {
        if (!options.tasteProfile) return emptyList()
        return tasteBackupCreator.backupCrossSourceMangaLinks()
    }
    // KMK <--

    // KMK --> v0.7.16: Best Version quality signals
    suspend fun backupMangaSourceQualitySignals(options: BackupOptions): List<BackupMangaSourceQualitySignal> {
        if (!options.tasteProfile) return emptyList()
        return tasteBackupCreator.backupMangaSourceQualitySignals()
    }
    // KMK <--

    // KMK --> v0.8.1-fix1: user-selected primary version per confirmed link group
    suspend fun backupCrossSourceGroupPrimaries(options: BackupOptions): List<BackupCrossSourceGroupPrimary> {
        if (!options.tasteProfile) return emptyList()
        return tasteBackupCreator.backupCrossSourceGroupPrimaries()
    }
    // KMK <--

    suspend fun backupCrossSourceIdentityDecisions(options: BackupOptions): List<BackupCrossSourceIdentityDecision> {
        if (!options.tasteProfile) return emptyList()
        return tasteBackupCreator.backupCrossSourceIdentityDecisions()
    }

    suspend fun backupAlternateSourceBridges(options: BackupOptions): List<BackupAlternateSourceBridge> {
        if (!options.tasteProfile) return emptyList()
        return tasteBackupCreator.backupAlternateSourceBridges()
    }

    suspend fun backupAlternateSourceBridgeMappings(options: BackupOptions): List<BackupAlternateSourceBridgeMapping> {
        if (!options.tasteProfile) return emptyList()
        return tasteBackupCreator.backupAlternateSourceBridgeMappings()
    }

    suspend fun backupLocalTrackedWorks(options: BackupOptions): List<BackupLocalTrackedWork> {
        if (!options.localTracker) return emptyList()
        return localTrackerBackupCreator()
    }

    // KMK --> v0.7.28: seen manga keys
    suspend fun backupSeenMangaKeys(options: BackupOptions): List<BackupSeenMangaKey> {
        if (!options.tasteProfile) return emptyList()
        return tasteBackupCreator.backupSeenMangaKeys()
    }
    // KMK <--
    // KMK v0.8.21-fix2: AUG-14 slice 3 -- saved For You focus modes
    fun backupSavedFocusModes(options: BackupOptions): List<BackupSavedFocusMode> {
        if (!options.tasteProfile) return emptyList()
        return tasteBackupCreator.backupSavedFocusModes()
    }
    // KMK <--
    // KMK <--

    companion object {
        private const val MAX_AUTO_BACKUPS: Int = 4
        private val FILENAME_REGEX = """${BuildConfig.APPLICATION_ID}_\d{4}-\d{2}-\d{2}_\d{2}-\d{2}.tachibk""".toRegex()

        fun getFilename(): String {
            val date = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ENGLISH).format(Date())
            return "${BuildConfig.APPLICATION_ID}_$date.tachibk"
        }
    }
}

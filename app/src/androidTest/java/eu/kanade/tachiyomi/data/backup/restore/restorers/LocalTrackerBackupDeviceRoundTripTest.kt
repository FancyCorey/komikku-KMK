package eu.kanade.tachiyomi.data.backup.restore.restorers

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreOutcome
import eu.kanade.tachiyomi.data.backup.restore.BackupRestorer
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import eu.kanade.tachiyomi.testutil.DisposableTestEnvironmentGuard
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.domain.tracker.model.LocalTrackedWork
import tachiyomi.domain.tracker.model.LocalTrackedWorkList
import tachiyomi.domain.tracker.model.LocalTrackedWorkSource
import tachiyomi.domain.tracker.model.LocalTrackedWorkSourceConfirmation
import tachiyomi.domain.tracker.model.LocalTrackedWorkSourceProgress
import tachiyomi.domain.tracker.model.LocalTrackedWorkStatus
import tachiyomi.domain.tracker.repository.LocalTrackerRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * Disposable-device complement to the host local-tracker backup wire test. It proves the real
 * BackupCreator file write, BackupDecoder ContentResolver read, and BackupRestorer orchestration
 * path while scoping every mutation to one uniquely generated local-tracker work.
 */
@RunWith(AndroidJUnit4::class)
class LocalTrackerBackupDeviceRoundTripTest {

    private fun backupOptions() = BackupOptions(
        libraryEntries = false,
        categories = false,
        chapters = false,
        tracking = false,
        history = false,
        readEntries = false,
        appSettings = false,
        extensionStores = false,
        sourceSettings = false,
        privateSettings = false,
        customInfo = false,
        savedSearchesFeeds = false,
        tasteProfile = false,
        localTracker = true,
    )

    private fun restoreOptions() = RestoreOptions(
        libraryEntries = false,
        categories = false,
        appSettings = false,
        extensionStores = false,
        sourceSettings = false,
        savedSearchesFeeds = false,
        tasteProfile = false,
        localTracker = true,
    )

    @Test
    fun localTrackerGraphSurvivesRealBackupAndRestore() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        DisposableTestEnvironmentGuard.assumeDisposableEnvironment(context)

        val repository = Injekt.get<LocalTrackerRepository>()
        val suffix = System.currentTimeMillis()
        val workId = "device-local-tracker-$suffix"
        val source = 910_555_000_000_000_003L
        val sourceUrl = "/kmk-device-round-trip/$suffix"
        val chapterUrl = "$sourceUrl/chapter-12"
        val backupFile = File(context.cacheDir, "$workId.tachibk")
        val now = System.currentTimeMillis()
        val listCreatedAt = now - 500L

        val work = LocalTrackedWork(
            id = workId,
            title = "KMK Device Round Trip",
            normalizedTitle = "kmk device round trip",
            status = LocalTrackedWorkStatus.READING,
            lastChapterSource = source,
            lastChapterNumber = 12.0,
            lastChapterUrl = chapterUrl,
            lastChapterLabel = "Chapter 12",
            lastProgressAt = now,
            score = 87.0,
            startDate = now - 1_000,
            finishDate = null,
            createdAt = now,
            updatedAt = now,
        )
        val sourceRow = LocalTrackedWorkSource(
            workId = workId,
            source = source,
            url = sourceUrl,
            title = work.title,
            confidence = 100,
            confirmation = LocalTrackedWorkSourceConfirmation.USER_CONFIRMED,
            inheritanceOptedOut = true,
            createdAt = now,
            updatedAt = now,
        )
        val progress = LocalTrackedWorkSourceProgress(
            workId = workId,
            source = source,
            url = sourceUrl,
            chapterNumber = 12.0,
            chapterUrl = chapterUrl,
            chapterLabel = "Chapter 12",
            progressAt = now,
            inheritedFromSource = source + 1,
            inheritedFromUrl = "$sourceUrl/origin",
            updatedAt = now,
        )

        try {
            assertEquals(null, repository.getWork(workId))
            repository.upsertWork(work)
            repository.upsertSource(sourceRow)
            repository.upsertSourceProgress(progress)
            repository.replaceListEntries(workId, listOf(LocalTrackedWorkList("Reading", listCreatedAt)))

            check(backupFile.createNewFile()) { "could not pre-create the backup file" }
            BackupCreator(context = context, isAutoBackup = false).backup(Uri.fromFile(backupFile), backupOptions())
            assertTrue("backup file must be non-empty", backupFile.length() > 0)

            repository.deleteWork(workId)
            assertEquals(null, repository.getWork(workId))

            val outcome = BackupRestorer(
                context = context,
                notifier = BackupNotifier(context),
                isSync = false,
            ).restore(Uri.fromFile(backupFile), restoreOptions())
            assertTrue("expected clean restore, got $outcome", outcome is BackupRestoreOutcome.Success)

            val restored = repository.getWork(workId)
            assertEquals(work.title, restored?.title)
            assertEquals(work.score, restored?.score)
            assertEquals(listOf("Reading"), repository.getLists(workId))
            assertEquals(listOf(LocalTrackedWorkList("Reading", listCreatedAt)), repository.getListEntries(workId))
            assertEquals(sourceRow, repository.getSources(workId).single())
            assertEquals(progress, repository.getSourceProgress(workId, source, sourceUrl))
        } finally {
            repository.getWork(workId)?.let { repository.deleteWork(workId) }
            backupFile.delete()
        }
    }
}

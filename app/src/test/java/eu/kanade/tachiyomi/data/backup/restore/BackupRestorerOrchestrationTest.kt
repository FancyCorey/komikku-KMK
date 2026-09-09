package eu.kanade.tachiyomi.data.backup.restore

import android.content.Context
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCrossSourceGroupPrimary
import eu.kanade.tachiyomi.data.backup.models.BackupCrossSourceMangaLink
import eu.kanade.tachiyomi.data.backup.models.BackupLocalTrackedWork
import eu.kanade.tachiyomi.data.backup.models.BackupLocalTrackedWorkList
import eu.kanade.tachiyomi.data.backup.models.BackupLocalTrackedWorkSource
import eu.kanade.tachiyomi.data.backup.models.BackupLocalTrackedWorkSourceProgress
import eu.kanade.tachiyomi.data.backup.models.BackupMangaTaste
import eu.kanade.tachiyomi.data.backup.restore.restorers.LocalTrackerBackupRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.TasteRestorer
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.data.AndroidDatabaseHandler
import tachiyomi.data.Chapters
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.History
import tachiyomi.data.Mangas
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.UpdateStrategyColumnAdapter
import tachiyomi.data.manga.MangaRepositoryImpl
import tachiyomi.data.taste.TasteRepositoryImpl
import tachiyomi.data.tracker.LocalTrackerRepositoryImpl
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.taste.interactor.GetCrossSourceGroupPrimary
import tachiyomi.domain.taste.interactor.GetCrossSourceMangaLinks
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.interactor.UpsertCrossSourceMangaLinks
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.repository.TasteRepository
import tachiyomi.domain.tracker.model.LocalTrackedWorkSourceConfirmation
import tachiyomi.domain.tracker.model.LocalTrackedWorkStatus
import tachiyomi.domain.tracker.repository.LocalTrackerRepository

/**
 * AG15-F4: BackupRestorer orchestration was previously covered only per-component
 * (LocalTrackerBackupRestorerTest, TasteBackupEndToEndRoundTripTest) and, at the source-text level,
 * for its bookkeeping mutex (BackupRestorerBookkeepingSourceTest). None of those exercise the real
 * BackupRestorer wiring itself -- that options gating correctly selects which restorer runs on which
 * backup slice, and that the result is one restored graph with the right work/source/progress/list
 * (local tracker) and group/rating (taste) counts.
 *
 * The file-decoding boundary (BackupDecoder against a real content Uri) needs a real Android
 * Context/ContentResolver and is out of reach for this pure-JVM test module (no Robolectric here);
 * [BackupRestorer.restoreDecodedBackup] is the internal seam extracted for exactly this purpose, so
 * this drives the real BackupRestorer class from an already-decoded (and, for extra rigor, real
 * protobuf-wire-round-tripped) [Backup] instead.
 */
class BackupRestorerOrchestrationTest {

    private fun freshLocalTrackerRepository(): LocalTrackerRepositoryImpl {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.create(driver)
        val database = Database(
            driver = driver,
            historyAdapter = History.Adapter(last_readAdapter = DateColumnAdapter),
            mangasAdapter = Mangas.Adapter(
                genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = UpdateStrategyColumnAdapter,
                memoAdapter = MemoColumnAdapter,
            ),
            chaptersAdapter = Chapters.Adapter(memoAdapter = MemoColumnAdapter),
        )
        return LocalTrackerRepositoryImpl(AndroidDatabaseHandler(database, driver))
    }

    private class TasteRepositories(val taste: TasteRepositoryImpl, val manga: MangaRepositoryImpl)

    /**
     * Taste and manga repositories MUST share one database: restoring a manga rating without a
     * matching local manga row falls back to inserting a minimal one through [MangaRepository]
     * (see [TasteRestorer.restoreMinimalRatedMangaIdentity]) -- a separately-mocked manga repository
     * would make every rating restore silently fail with "missing or invalid manga identity"
     * instead of exercising the real path.
     */
    private fun freshTasteRepositories(): TasteRepositories {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.create(driver)
        val database = Database(
            driver = driver,
            historyAdapter = History.Adapter(last_readAdapter = DateColumnAdapter),
            mangasAdapter = Mangas.Adapter(
                genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = UpdateStrategyColumnAdapter,
                memoAdapter = MemoColumnAdapter,
            ),
            chaptersAdapter = Chapters.Adapter(memoAdapter = MemoColumnAdapter),
        )
        val handler = AndroidDatabaseHandler(database, driver)
        return TasteRepositories(taste = TasteRepositoryImpl(handler), manga = MangaRepositoryImpl(handler))
    }

    private fun tasteRestorer(tasteRepository: TasteRepository, mangaRepository: MangaRepository) = TasteRestorer(
        tasteRepository = tasteRepository,
        alternateSourceBridgeRepository = mockk(relaxed = true),
        mangaRepository = mangaRepository,
        getManga = GetManga(mangaRepository),
        getMangaTaste = GetMangaTaste(tasteRepository),
        getTagTaste = mockk(relaxed = true),
        getTagAliases = mockk(relaxed = true),
        getDisabledSources = mockk(relaxed = true),
        setSourceEnabled = mockk(relaxed = true),
        upsertTagAlias = mockk(relaxed = true),
        getCrossSourceMangaLinks = GetCrossSourceMangaLinks(tasteRepository),
        upsertCrossSourceMangaLinks = UpsertCrossSourceMangaLinks(tasteRepository),
        getCrossSourceGroupPrimary = GetCrossSourceGroupPrimary(tasteRepository),
        getMangaSourceQualitySignals = mockk(relaxed = true),
        upsertMangaSourceQualitySignal = mockk(relaxed = true),
        sourcePreferences = mockk(relaxed = true),
    )

    // Every BackupRestorer sub-restorer default resolves several Injekt.get() collaborators AT
    // CONSTRUCTION time, before RestoreOptions ever gates whether it is actually invoked -- so each
    // one this test does not exercise must still be overridden with a relaxed mock, not left as the
    // Injekt-backed default (which throws InjektionException with no Injekt bootstrap in a unit test).
    private fun restorer(
        localTrackerRepository: LocalTrackerRepository,
        tasteRepositories: TasteRepositories,
    ) = BackupRestorer(
        context = mockk<Context>(relaxed = true),
        notifier = mockk<BackupNotifier>(relaxed = true),
        isSync = false,
        categoriesRestorer = mockk(relaxed = true),
        preferenceRestorer = mockk(relaxed = true),
        extensionStoreRestorer = mockk(relaxed = true),
        mangaRestorer = mockk<MangaRestorer>(relaxed = true),
        savedSearchRestorer = mockk(relaxed = true),
        feedRestorer = mockk(relaxed = true),
        tasteRestorer = tasteRestorer(tasteRepositories.taste, tasteRepositories.manga),
        localTrackerBackupRestorer = LocalTrackerBackupRestorer(localTrackerRepository),
    )

    private fun roundTripThroughWire(backup: Backup): Backup {
        val bytes = ProtoBuf.encodeToByteArray(Backup.serializer(), backup)
        return ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes)
    }

    @Test
    fun `restoring a decoded backup drives both the local tracker and taste owners and restores the full graph`() = runTest {
        val now = System.currentTimeMillis()
        val backupWork = BackupLocalTrackedWork(
            id = "orch-work-1",
            title = "Orchestration Fixture",
            normalizedTitle = "orchestration fixture",
            status = LocalTrackedWorkStatus.READING.name,
            createdAt = now,
            updatedAt = now,
            sources = listOf(
                BackupLocalTrackedWorkSource(
                    source = 10L,
                    url = "/orch/manga",
                    title = "Orchestration Fixture",
                    confidence = 100,
                    confirmation = LocalTrackedWorkSourceConfirmation.USER_CONFIRMED.name,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
            hasSources = true,
            sourceProgress = listOf(
                BackupLocalTrackedWorkSourceProgress(
                    source = 10L,
                    url = "/orch/manga",
                    chapterNumber = 5.0,
                    hasChapterNumber = true,
                    chapterUrl = "/orch/manga/chapter-5",
                    chapterLabel = "Chapter 5",
                    progressAt = now,
                    updatedAt = now,
                ),
            ),
            hasSourceProgress = true,
            lists = listOf(BackupLocalTrackedWorkList(name = "reading", createdAt = now)),
            hasLists = true,
        )

        val groupId = "orch-group-1"
        val backupTaste = BackupMangaTaste(
            mangaId = 9001L,
            source = 20L,
            url = "/orch/taste-manga",
            title = "Orchestration Taste Fixture",
            rating = MangaRating.LOVE.value,
            updatedAt = now,
        )
        val backupLink = BackupCrossSourceMangaLink(
            source = 20L,
            url = "/orch/taste-manga",
            groupId = groupId,
            title = "Orchestration Taste Fixture",
            updatedAt = now,
        )
        val backupPrimary = BackupCrossSourceGroupPrimary(groupId = groupId, source = 20L, url = "/orch/taste-manga", updatedAt = now)

        val backup = roundTripThroughWire(
            Backup(
                backupManga = emptyList(),
                backupLocalTrackedWorks = listOf(backupWork),
                backupMangaTastes = listOf(backupTaste),
                backupCrossSourceMangaLinks = listOf(backupLink),
                backupCrossSourceGroupPrimaries = listOf(backupPrimary),
            ),
        )
        assertEquals(1, backup.backupLocalTrackedWorks.size)
        assertEquals(1, backup.backupMangaTastes.size)

        val localTrackerRepository = freshLocalTrackerRepository()
        val tasteRepositories = freshTasteRepositories()
        val options = RestoreOptions(
            libraryEntries = false,
            categories = false,
            appSettings = false,
            extensionStores = false,
            sourceSettings = false,
            savedSearchesFeeds = false,
            tasteProfile = true,
            localTracker = true,
        )

        restorer(localTrackerRepository, tasteRepositories).restoreDecodedBackup(backup, options)

        // Local tracker: work, source, progress, and list counts.
        val restoredWork = localTrackerRepository.getWork("orch-work-1")
        assertTrue(restoredWork != null, "expected the local tracked work to be restored")
        assertEquals(1, localTrackerRepository.getSources("orch-work-1").size)
        assertEquals(1, localTrackerRepository.getSourceProgressForWork("orch-work-1").size)
        assertEquals(1, localTrackerRepository.getListEntries("orch-work-1").size)

        // Taste: group (cross-source link/primary) and rating counts.
        val taste = tasteRepositories.taste
        assertEquals(1, taste.getAllMangaTastes().size)
        assertEquals(1, taste.getAllCrossSourceMangaLinks().size)
        assertEquals(1, taste.getAllCrossSourceGroupPrimaries().size)
        assertEquals(MangaRating.LOVE.value, taste.getAllMangaTastes().single().rating)
    }
}

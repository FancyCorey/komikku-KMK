package eu.kanade.tachiyomi.ui.manga

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.domain.track.model.LocalTrackingActionPolicy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
import tachiyomi.data.tracker.LocalTrackerRepositoryImpl
import tachiyomi.domain.tracker.model.LocalTrackedWork
import tachiyomi.domain.tracker.model.LocalTrackedWorkSource
import tachiyomi.domain.tracker.model.LocalTrackedWorkSourceConfirmation
import tachiyomi.domain.tracker.model.LocalTrackedWorkStatus
import tachiyomi.domain.tracker.repository.LocalTrackerRepository
import java.util.UUID

/**
 * Tests for the local-tracking status/list workflow's exact write sequence, mirroring
 * [eu.kanade.tachiyomi.ui.manga.track.TrackInfoDialogHomeScreen.Model.setLocalTrackingStatus]/
 * [eu.kanade.tachiyomi.ui.manga.track.TrackInfoDialogHomeScreen.Model.removeLocalTracking] verbatim
 * against a real in-memory SQLite [LocalTrackerRepositoryImpl] (the same fixture
 * [LocalTrackerRepositoryTest] uses), rather than instantiating the large-constructor screen model
 * itself.
 *
 * KMK v0.8.21-fix2: this file previously mirrored [MangaScreenModel]'s now-removed
 * `trackLocally`/`setLocalTrackingStatus`/`removeLocalTracking` -- that ownership moved to
 * `TrackInfoDialogHomeScreen.Model` as part of making Local a peer entry in the Tracking dialog
 * (see `TrackerEntry`'s doc). The write sequence itself also changed behaviorally: there is no
 * longer a separate "track with hardcoded READING" action distinct from "set a status" -- a single
 * `setLocalTrackingStatus(status)` both creates the work (using whichever status the user picked
 * from the dialog, not a hardcoded default) and updates it, so tapping Local always opens the same
 * status-choice dialog instead of writing a status automatically on first tap.
 */
class MangaScreenModelLocalTrackingStatusTest {

    private fun repository(): LocalTrackerRepository {
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

    private val source = 5L
    private val url = "/manga/local-tracking-status-test"
    private val title = "Example"

    /**
     * Mirrors [eu.kanade.tachiyomi.ui.manga.track.TrackInfoDialogHomeScreen.Model.setLocalTrackingStatus]'s
     * exact write sequence: creates the work with [status] (not a hardcoded default) if none exists
     * yet for this source+url, or updates the existing work's status otherwise.
     */
    private suspend fun setLocalTrackingStatus(
        repository: LocalTrackerRepository,
        status: LocalTrackedWorkStatus,
        workTitle: String = title,
    ) {
        val now = System.currentTimeMillis()
        val existingId = repository.getWorkIdBySourceUrl(source, url)
        if (existingId != null) {
            val work = repository.getWork(existingId) ?: return
            repository.upsertWork(work.copy(status = status, updatedAt = now))
        } else {
            val workId = UUID.randomUUID().toString()
            repository.upsertWork(
                LocalTrackedWork(
                    id = workId,
                    title = workTitle,
                    normalizedTitle = workTitle.trim().lowercase(),
                    status = status,
                    lastChapterSource = null,
                    lastChapterNumber = null,
                    lastChapterUrl = null,
                    lastChapterLabel = null,
                    lastProgressAt = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            repository.upsertSource(
                LocalTrackedWorkSource(
                    workId = workId,
                    source = source,
                    url = url,
                    title = workTitle,
                    confidence = 100,
                    confirmation = LocalTrackedWorkSourceConfirmation.USER_CONFIRMED,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    /** Mirrors [eu.kanade.tachiyomi.ui.manga.track.TrackInfoDialogHomeScreen.Model.removeLocalTracking]'s exact write sequence. */
    private suspend fun removeLocalTracking(repository: LocalTrackerRepository) {
        repository.getWorkIdBySourceUrl(source, url)?.let { repository.deleteWork(it) }
    }

    @Test
    fun `first status pick from the dialog creates a work with exactly that status, not a hardcoded default`() = runTest {
        val repository = repository()

        setLocalTrackingStatus(repository, LocalTrackedWorkStatus.PLANNED)

        val workId = repository.getWorkIdBySourceUrl(source, url)
        assertEquals(LocalTrackedWorkStatus.PLANNED, repository.getWork(workId!!)!!.status)
    }

    @Test
    fun `every status in the shared status order can be selected from the status list workflow`() = runTest {
        val repository = repository()
        setLocalTrackingStatus(repository, LocalTrackedWorkStatus.READING)

        LocalTrackingActionPolicy.statusOrder.forEach { status ->
            setLocalTrackingStatus(repository, status)
            val workId = repository.getWorkIdBySourceUrl(source, url)!!
            assertEquals(status, repository.getWork(workId)!!.status)
        }
    }

    @Test
    fun `changing status preserves recorded chapter progress`() = runTest {
        val repository = repository()
        setLocalTrackingStatus(repository, LocalTrackedWorkStatus.READING)
        val workId = repository.getWorkIdBySourceUrl(source, url)!!
        repository.recordProgress(workId, source, 5.0, "/chapter-5", "Chapter 5", System.currentTimeMillis())

        setLocalTrackingStatus(repository, LocalTrackedWorkStatus.ON_HOLD)

        val work = repository.getWork(workId)!!
        assertEquals(LocalTrackedWorkStatus.ON_HOLD, work.status)
        assertEquals(5.0, work.lastChapterNumber)
        assertEquals("/chapter-5", work.lastChapterUrl)
    }

    @Test
    fun `removing local tracking clears the association and re-tracking starts fresh`() = runTest {
        val repository = repository()
        setLocalTrackingStatus(repository, LocalTrackedWorkStatus.READING)
        val workId = repository.getWorkIdBySourceUrl(source, url)!!
        repository.recordProgress(workId, source, 5.0, "/chapter-5", "Chapter 5", System.currentTimeMillis())
        setLocalTrackingStatus(repository, LocalTrackedWorkStatus.COMPLETED)

        removeLocalTracking(repository)

        assertNull(repository.getWorkIdBySourceUrl(source, url))
        assertNull(repository.getWork(workId))

        // Re-tracking creates a brand-new entry, not a resurrection of the removed one.
        setLocalTrackingStatus(repository, LocalTrackedWorkStatus.READING)
        val newWorkId = repository.getWorkIdBySourceUrl(source, url)!!
        val newWork = repository.getWork(newWorkId)!!
        assertEquals(LocalTrackedWorkStatus.READING, newWork.status)
        assertNull(newWork.lastChapterNumber)
    }

    @Test
    fun `duplicate status selection is idempotent`() = runTest {
        val repository = repository()
        setLocalTrackingStatus(repository, LocalTrackedWorkStatus.READING)

        setLocalTrackingStatus(repository, LocalTrackedWorkStatus.PLANNED)
        setLocalTrackingStatus(repository, LocalTrackedWorkStatus.PLANNED)

        val workId = repository.getWorkIdBySourceUrl(source, url)!!
        assertEquals(LocalTrackedWorkStatus.PLANNED, repository.getWork(workId)!!.status)
    }

    @Test
    fun `removing local tracking does not affect an unrelated tracked manga`() = runTest {
        val repository = repository()
        setLocalTrackingStatus(repository, LocalTrackedWorkStatus.READING, workTitle = "Example")
        val otherUrl = "/manga/other"
        val now = System.currentTimeMillis()
        val otherWorkId = UUID.randomUUID().toString()
        repository.upsertWork(
            LocalTrackedWork(
                id = otherWorkId, title = "Other", normalizedTitle = "other", status = LocalTrackedWorkStatus.READING,
                lastChapterSource = null, lastChapterNumber = null, lastChapterUrl = null,
                lastChapterLabel = null, lastProgressAt = null, createdAt = now, updatedAt = now,
            ),
        )
        repository.upsertSource(
            LocalTrackedWorkSource(
                workId = otherWorkId,
                source = source,
                url = otherUrl,
                title = "Other",
                confidence = 100,
                confirmation = LocalTrackedWorkSourceConfirmation.USER_CONFIRMED,
                createdAt = now,
                updatedAt = now,
            ),
        )

        removeLocalTracking(repository)

        assertNull(repository.getWorkIdBySourceUrl(source, url))
        assertEquals(otherWorkId, repository.getWorkIdBySourceUrl(source, otherUrl))
    }
}

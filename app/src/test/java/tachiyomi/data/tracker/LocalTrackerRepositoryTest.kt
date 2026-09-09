package tachiyomi.data.tracker

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
import tachiyomi.domain.tracker.model.LocalTrackedWork
import tachiyomi.domain.tracker.model.LocalTrackedWorkList
import tachiyomi.domain.tracker.model.LocalTrackedWorkSource
import tachiyomi.domain.tracker.model.LocalTrackedWorkSourceConfirmation
import tachiyomi.domain.tracker.model.LocalTrackedWorkSourceProgress
import tachiyomi.domain.tracker.model.LocalTrackedWorkStatus

class LocalTrackerRepositoryTest {

    private fun repository(): LocalTrackerRepositoryImpl {
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

    private fun work(id: String = "work-1") = LocalTrackedWork(
        id = id,
        title = "Example",
        normalizedTitle = "example",
        status = LocalTrackedWorkStatus.READING,
        lastChapterSource = null,
        lastChapterNumber = null,
        lastChapterUrl = null,
        lastChapterLabel = null,
        lastProgressAt = null,
        createdAt = 1_000,
        updatedAt = 1_000,
    )

    private fun source(workId: String = "work-1", url: String = "/example") = LocalTrackedWorkSource(
        workId = workId,
        source = 1,
        url = url,
        title = "Example",
        confidence = 100,
        confirmation = LocalTrackedWorkSourceConfirmation.USER_CONFIRMED,
        createdAt = 1_000,
        updatedAt = 1_000,
    )

    @Test
    fun `work and source link survive without a manga row`() = runTest {
        val repository = repository()
        repository.upsertWork(work())
        repository.upsertSource(source())

        assertEquals(work(), repository.getWork("work-1"))
        assertEquals(listOf(source()), repository.getSources("work-1"))
    }

    @Test
    fun `local metadata survives database round trip`() = runTest {
        val repository = repository()
        val expected = work().copy(
            score = 8.5,
            startDate = 1_100,
            finishDate = 2_100,
        )

        repository.upsertWork(expected)

        assertEquals(expected, repository.getWork(expected.id))
    }

    @Test
    fun `custom lists are trimmed case deduplicated and replace atomically`() = runTest {
        val repository = repository()
        repository.upsertWork(work())
        repository.replaceLists("work-1", listOf(" Reading ", "reading", "Paused"))

        assertEquals(listOf("Paused", "Reading"), repository.getLists("work-1"))
    }

    @Test
    fun `custom list creation times survive entry round trip`() = runTest {
        val repository = repository()
        repository.upsertWork(work())
        val entries = listOf(
            LocalTrackedWorkList("Reading", 1_100),
            LocalTrackedWorkList("Paused", 1_200),
        )

        repository.replaceListEntries("work-1", entries)

        assertEquals(entries.sortedBy { it.name.lowercase() }, repository.getListEntries("work-1"))
    }

    @Test
    fun `one source entry cannot be attached to two local works`() = runTest {
        val repository = repository()
        repository.upsertWork(work("work-1"))
        repository.upsertWork(work("work-2"))
        repository.upsertSource(source("work-1"))

        var rejected = false
        try {
            repository.upsertSource(source("work-2"))
        } catch (_: Exception) {
            rejected = true
        }
        assertTrue(rejected)
        assertEquals(emptyList<LocalTrackedWorkSource>(), repository.getSources("work-2"))
    }

    @Test
    fun `consolidating works preserves canonical identity and dependent rows`() = runTest {
        val repository = repository()
        val canonical = work("work-1").copy(score = 7.0, startDate = 1_100)
        val duplicate = work("work-2").copy(score = 8.5, finishDate = 3_100, updatedAt = 3_000)
        repository.upsertWork(canonical)
        repository.upsertWork(duplicate)
        repository.upsertSource(source("work-1", "/canonical"))
        repository.upsertSource(source("work-2", "/duplicate").copy(inheritanceOptedOut = true))
        repository.upsertSourceProgress(
            LocalTrackedWorkSourceProgress(
                workId = "work-2",
                source = 1,
                url = "/duplicate",
                chapterNumber = 12.0,
                chapterUrl = "/duplicate/ch-12",
                chapterLabel = "Chapter 12",
                progressAt = 2_500,
                updatedAt = 2_500,
            ),
        )
        val duplicateList = LocalTrackedWorkList("Favorites", 1_500)
        repository.replaceListEntries("work-2", listOf(duplicateList))

        repository.consolidateWork("work-1", "work-2")

        val merged = repository.getWork("work-1")!!
        assertEquals(canonical.id, merged.id)
        assertEquals("Example", merged.title)
        assertEquals("example", merged.normalizedTitle)
        assertEquals(8.5, merged.score)
        assertEquals(canonical.startDate, merged.startDate)
        assertEquals(duplicate.finishDate, merged.finishDate)
        assertEquals(minOf(canonical.createdAt, duplicate.createdAt), merged.createdAt)
        assertEquals(maxOf(canonical.updatedAt, duplicate.updatedAt), merged.updatedAt)
        assertNull(repository.getWork("work-2"))
        assertEquals(setOf("/canonical", "/duplicate"), repository.getSources("work-1").map { it.url }.toSet())
        assertEquals("work-1", repository.getWorkIdBySourceUrl(1, "/duplicate"))
        val mergedSource = repository.getSources("work-1").single { it.url == "/duplicate" }
        assertEquals(100, mergedSource.confidence)
        assertEquals(LocalTrackedWorkSourceConfirmation.USER_CONFIRMED, mergedSource.confirmation)
        assertTrue(mergedSource.inheritanceOptedOut)
        assertEquals(duplicateList.createdAt, repository.getListEntries("work-1").single().createdAt)
        val mergedProgress = repository.getSourceProgress("work-1", 1, "/duplicate")!!
        assertEquals(12.0, mergedProgress.chapterNumber)
        assertEquals("/duplicate/ch-12", mergedProgress.chapterUrl)
        assertEquals("Chapter 12", mergedProgress.chapterLabel)
        assertEquals(2_500L, mergedProgress.progressAt)
        assertEquals(2_500L, mergedProgress.updatedAt)
        assertEquals(listOf(duplicateList), repository.getListEntries("work-1"))

        repository.consolidateWork("work-1", "work-2")

        assertEquals(merged, repository.getWork("work-1"))
        assertEquals(setOf("/canonical", "/duplicate"), repository.getSources("work-1").map { it.url }.toSet())
        assertEquals(listOf(duplicateList), repository.getListEntries("work-1"))
    }

    @Test
    fun `deleting local work removes only its local links and lists`() = runTest {
        val repository = repository()
        repository.upsertWork(work("work-1"))
        repository.upsertWork(work("work-2"))
        repository.upsertSource(source("work-1"))
        repository.upsertSource(source("work-2", "/other"))
        repository.replaceLists("work-1", listOf("Reading"))

        repository.deleteWork("work-1")

        assertEquals(null, repository.getWork("work-1"))
        assertEquals(emptyList<LocalTrackedWorkSource>(), repository.getSources("work-1"))
        assertEquals(emptyList<String>(), repository.getLists("work-1"))
        assertEquals(listOf(source("work-2", "/other")), repository.getSources("work-2"))
    }

    @Test
    fun `progress resolves by source url and never regresses recognized chapter number`() = runTest {
        val repository = repository()
        repository.upsertWork(work())
        repository.upsertSource(source())

        assertEquals("work-1", repository.getWorkIdBySourceUrl(1, "/example"))
        repository.recordProgress("work-1", 1, 10.0, "/chapter-10", "Chapter 10", 2_000)
        repository.recordProgress("work-1", 1, 9.0, "/chapter-9", "Chapter 9", 3_000)

        val retained = repository.getWork("work-1")!!
        assertEquals(10.0, retained.lastChapterNumber)
        assertEquals("/chapter-10", retained.lastChapterUrl)

        repository.recordProgress("work-1", 1, 11.0, "/chapter-11", "Chapter 11", 4_000)
        val advanced = repository.getWork("work-1")!!
        assertEquals(11.0, advanced.lastChapterNumber)
        assertEquals("/chapter-11", advanced.lastChapterUrl)
    }

    @Test
    fun `same chapter progress is timestamp monotonic at work level`() = runTest {
        val repository = repository()
        repository.upsertWork(work())
        repository.upsertSource(source())

        repository.recordProgress("work-1", 1, 12.0, "/chapter-12-newer", "Chapter 12", 4_000)
        repository.recordProgress("work-1", 1, 12.0, "/chapter-12-older", "Chapter 12", 3_000)

        val retained = repository.getWork("work-1")!!
        assertEquals(12.0, retained.lastChapterNumber)
        assertEquals("/chapter-12-newer", retained.lastChapterUrl)
        assertEquals(4_000L, retained.lastProgressAt)
    }

    @Test
    fun `advancing chapter does not regress work update timestamp`() = runTest {
        val repository = repository()
        repository.upsertWork(work())
        repository.upsertSource(source())

        repository.recordProgress("work-1", 1, 12.0, "/chapter-12", "Chapter 12", 4_000)
        repository.recordProgress("work-1", 1, 13.0, "/chapter-13", "Chapter 13", 3_000)

        val retained = repository.getWork("work-1")!!
        assertEquals(13.0, retained.lastChapterNumber)
        assertEquals(3_000L, retained.lastProgressAt)
        assertEquals(4_000L, retained.updatedAt)
    }

    @Test
    fun `first recorded progress derives the local start date`() = runTest {
        val repository = repository()
        repository.upsertWork(work())
        repository.upsertSource(source())

        repository.recordProgress("work-1", 1, 1.0, "/chapter-1", "Chapter 1", 2_000)

        assertEquals(2_000L, repository.getWork("work-1")!!.startDate)
    }

    @Test
    fun `first recorded progress moves planned work into reading without overriding terminal status`() = runTest {
        val repository = repository()
        repository.upsertWork(work().copy(status = LocalTrackedWorkStatus.PLANNED))
        repository.upsertSource(source())

        repository.recordProgress("work-1", 1, 1.0, "/chapter-1", "Chapter 1", 2_000)

        assertEquals(LocalTrackedWorkStatus.READING, repository.getWork("work-1")!!.status)
        LocalTrackedWorkStatus.values()
            .filter { it != LocalTrackedWorkStatus.PLANNED && it != LocalTrackedWorkStatus.READING }
            .forEach { status ->
                repository.upsertWork(work().copy(status = status))
                repository.recordProgress("work-1", 1, 2.0, "/chapter-2-${status.name}", "Chapter 2", 3_000)
                assertEquals(status, repository.getWork("work-1")!!.status)
            }
    }

    @Test
    fun `unknown chapter labels do not replace recognized progress`() = runTest {
        val repository = repository()
        repository.upsertWork(work())
        repository.upsertSource(source())
        repository.recordProgress("work-1", 1, 4.0, "/chapter-4", "Chapter 4", 2_000)

        repository.recordProgress("work-1", 1, null, "/special", "Special", 3_000)

        val retained = repository.getWork("work-1")!!
        assertEquals(4.0, retained.lastChapterNumber)
        assertEquals("/chapter-4", retained.lastChapterUrl)
    }

    @Test
    fun `source progress preserves target identity and inheritance provenance`() = runTest {
        val repository = repository()
        repository.upsertWork(work())
        repository.upsertSource(source())
        val progress = LocalTrackedWorkSourceProgress(
            workId = "work-1",
            source = 1,
            url = "/example",
            chapterNumber = 12.0,
            chapterUrl = "/target-chapter-12",
            chapterLabel = "Chapter 12",
            progressAt = 2_000,
            inheritedFromSource = 2,
            inheritedFromUrl = "/origin",
            updatedAt = 2_000,
        )

        repository.upsertSourceProgress(progress)

        assertEquals(progress, repository.getSourceProgress("work-1", 1, "/example"))
        repository.setInheritanceOptedOut("work-1", 1, "/example", optedOut = true, updatedAt = 3_000)
        assertTrue(repository.getSources("work-1").single().inheritanceOptedOut)
    }

    @Test
    fun `source progress requires an existing source link`() = runTest {
        val repository = repository()
        repository.upsertWork(work())
        val progress = LocalTrackedWorkSourceProgress(
            workId = "work-1",
            source = 1,
            url = "/missing",
            chapterNumber = 12.0,
            chapterUrl = "/chapter-12",
            chapterLabel = "Chapter 12",
            progressAt = 2_000,
            updatedAt = 2_000,
        )

        var rejected = false
        try {
            repository.upsertSourceProgress(progress)
        } catch (_: IllegalStateException) {
            rejected = true
        }

        assertTrue(rejected)
        assertNull(repository.getSourceProgress("work-1", 1, "/missing"))
    }

    @Test
    fun `stale source metadata cannot overwrite newer confirmation or opt out`() = runTest {
        val repository = repository()
        repository.upsertWork(work())
        repository.upsertSource(
            source().copy(
                confirmation = LocalTrackedWorkSourceConfirmation.SUGGESTED,
                inheritanceOptedOut = true,
                updatedAt = 3_000,
            ),
        )

        repository.upsertSource(
            source().copy(
                confirmation = LocalTrackedWorkSourceConfirmation.USER_CONFIRMED,
                inheritanceOptedOut = false,
                updatedAt = 2_000,
            ),
        )

        val retained = repository.getSources("work-1").single()
        assertEquals(LocalTrackedWorkSourceConfirmation.SUGGESTED, retained.confirmation)
        assertTrue(retained.inheritanceOptedOut)
        assertEquals(3_000, retained.updatedAt)
    }

    @Test
    fun `stale source progress cannot overwrite newer child fields`() = runTest {
        val repository = repository()
        repository.upsertWork(work())
        repository.upsertSource(source())
        val newer = LocalTrackedWorkSourceProgress(
            workId = "work-1",
            source = 1,
            url = "/example",
            chapterNumber = 12.0,
            chapterUrl = "/chapter-12",
            chapterLabel = "Chapter 12",
            progressAt = 4_000,
            updatedAt = 4_000,
        )
        repository.upsertSourceProgress(newer)

        repository.upsertSourceProgress(
            newer.copy(
                chapterNumber = 11.0,
                chapterUrl = "/chapter-11",
                chapterLabel = "Chapter 11",
                progressAt = 3_000,
                updatedAt = 3_000,
            ),
        )

        assertEquals(newer, repository.getSourceProgress("work-1", 1, "/example"))
    }

    @Test
    fun `recorded source progress never regresses or loses a recognized chapter`() = runTest {
        val repository = repository()
        repository.upsertWork(work())
        repository.upsertSource(source())

        val chapter12 = LocalTrackedWorkSourceProgress(
            workId = "work-1",
            source = 1,
            url = "/example",
            chapterNumber = 12.0,
            chapterUrl = "/chapter-12",
            chapterLabel = "Chapter 12",
            progressAt = 2_000,
            updatedAt = 2_000,
        )
        repository.recordSourceProgress(chapter12)

        repository.recordSourceProgress(
            chapter12.copy(
                chapterNumber = 11.0,
                chapterUrl = "/chapter-11",
                chapterLabel = "Chapter 11",
                progressAt = 3_000,
                updatedAt = 3_000,
            ),
        )
        repository.recordSourceProgress(
            chapter12.copy(
                chapterNumber = null,
                chapterUrl = "/special",
                chapterLabel = "Special",
                progressAt = 4_000,
                updatedAt = 4_000,
            ),
        )

        assertEquals(chapter12, repository.getSourceProgress("work-1", 1, "/example"))

        val chapter13 = chapter12.copy(
            chapterNumber = 13.0,
            chapterUrl = "/chapter-13",
            chapterLabel = "Chapter 13",
            progressAt = 5_000,
            updatedAt = 5_000,
        )
        repository.recordSourceProgress(chapter13)

        assertEquals(chapter13, repository.getSourceProgress("work-1", 1, "/example"))
    }

    @Test
    fun `recorded source progress requires an existing source link`() = runTest {
        val repository = repository()
        repository.upsertWork(work())
        val progress = LocalTrackedWorkSourceProgress(
            workId = "work-1",
            source = 1,
            url = "/missing",
            chapterNumber = 12.0,
            chapterUrl = "/chapter-12",
            chapterLabel = "Chapter 12",
            progressAt = 2_000,
            updatedAt = 2_000,
        )

        var rejected = false
        try {
            repository.recordSourceProgress(progress)
        } catch (_: IllegalStateException) {
            rejected = true
        }

        assertTrue(rejected)
        assertNull(repository.getSourceProgress("work-1", 1, "/missing"))
    }

    @Test
    fun `recorded unknown source progress is timestamp monotonic`() = runTest {
        val repository = repository()
        repository.upsertWork(work())
        repository.upsertSource(source())

        val unknown = LocalTrackedWorkSourceProgress(
            workId = "work-1",
            source = 1,
            url = "/example",
            chapterNumber = null,
            chapterUrl = "/special-1",
            chapterLabel = "Special 1",
            progressAt = 2_000,
            updatedAt = 2_000,
        )
        repository.recordSourceProgress(unknown)
        repository.recordSourceProgress(
            unknown.copy(
                chapterUrl = "/special-0",
                chapterLabel = "Special 0",
                progressAt = 1_000,
                updatedAt = 1_000,
            ),
        )

        assertEquals(unknown, repository.getSourceProgress("work-1", 1, "/example"))
    }

    @Test
    fun `recorded same chapter progress is timestamp monotonic`() = runTest {
        val repository = repository()
        repository.upsertWork(work())
        repository.upsertSource(source())

        val newer = LocalTrackedWorkSourceProgress(
            workId = "work-1",
            source = 1,
            url = "/example",
            chapterNumber = 12.0,
            chapterUrl = "/chapter-12-newer",
            chapterLabel = "Chapter 12 (newer)",
            progressAt = 3_000,
            updatedAt = 3_000,
        )
        repository.recordSourceProgress(newer)
        repository.recordSourceProgress(
            newer.copy(
                chapterUrl = "/chapter-12-older",
                chapterLabel = "Chapter 12 (older)",
                progressAt = 2_000,
                updatedAt = 2_000,
            ),
        )

        assertEquals(newer, repository.getSourceProgress("work-1", 1, "/example"))
    }

    @Test
    fun `recorded higher chapter does not regress source update timestamp`() = runTest {
        val repository = repository()
        repository.upsertWork(work())
        repository.upsertSource(source())

        val chapter12 = LocalTrackedWorkSourceProgress(
            workId = "work-1",
            source = 1,
            url = "/example",
            chapterNumber = 12.0,
            chapterUrl = "/chapter-12",
            chapterLabel = "Chapter 12",
            progressAt = 4_000,
            updatedAt = 4_000,
        )
        repository.recordSourceProgress(chapter12)
        repository.recordSourceProgress(
            chapter12.copy(
                chapterNumber = 13.0,
                chapterUrl = "/chapter-13",
                chapterLabel = "Chapter 13",
                progressAt = 3_000,
                updatedAt = 3_000,
            ),
        )

        val retained = repository.getSourceProgress("work-1", 1, "/example")!!
        assertEquals(13.0, retained.chapterNumber)
        assertEquals(3_000L, retained.progressAt)
        assertEquals(4_000L, retained.updatedAt)
    }

    @Test
    fun `deleting a source also deletes its source progress`() = runTest {
        val repository = repository()
        repository.upsertWork(work())
        repository.upsertSource(source())
        repository.upsertSourceProgress(
            LocalTrackedWorkSourceProgress(
                workId = "work-1",
                source = 1,
                url = "/example",
                chapterNumber = null,
                chapterUrl = "/special",
                chapterLabel = "Special",
                progressAt = 2_000,
                updatedAt = 2_000,
            ),
        )

        repository.deleteSource("work-1", 1, "/example")

        assertNull(repository.getSourceProgress("work-1", 1, "/example"))
    }
}

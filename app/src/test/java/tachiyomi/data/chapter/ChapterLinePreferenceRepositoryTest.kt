package tachiyomi.data.chapter

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.flow.first
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
import tachiyomi.domain.chapter.model.ChapterLinePreference

class ChapterLinePreferenceRepositoryTest {

    private fun repository(
        driver: JdbcSqliteDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY),
        initialize: Boolean = true,
    ): ChapterLinePreferenceRepositoryImpl {
        if (initialize) Database.Schema.create(driver)
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
        return ChapterLinePreferenceRepositoryImpl(AndroidDatabaseHandler(database, driver))
    }

    private fun preference(
        scanlator: String? = " Flame  Scans ",
        updatedAt: Long = 2_000,
    ) = ChapterLinePreference(
        mangaId = 1L,
        sourceId = 42L,
        preferredScanlator = scanlator,
        anchorChapterUrl = "/chapter-1",
        anchorChapterNumber = 1.0,
        confirmedAt = 1_000,
        updatedAt = updatedAt,
    )

    @Test
    fun `save exposes preference through one-shot and flow reads`() = runTest {
        val repository = repository()
        repository.save(preference())

        assertEquals(preference(scanlator = "Flame Scans"), repository.getOnce(1L, 42L))
        assertEquals(preference(scanlator = "Flame Scans"), repository.get(1L, 42L).first())
    }

    @Test
    fun `upsert replaces only the same manga and source scope`() = runTest {
        val repository = repository()
        repository.save(preference())
        repository.save(preference(scanlator = "Other", updatedAt = 3_000))

        assertEquals(preference(scanlator = "Other", updatedAt = 3_000), repository.getOnce(1L, 42L))
        assertNull(repository.getOnce(2L, 42L))
        assertNull(repository.getOnce(1L, 43L))
    }

    @Test
    fun `clear removes only the requested scope`() = runTest {
        val repository = repository()
        repository.save(preference())
        repository.save(preference().copy(sourceId = 43L))

        repository.clear(1L, 42L)

        assertNull(repository.getOnce(1L, 42L))
        assertEquals(preference(scanlator = "Flame Scans").copy(sourceId = 43L), repository.getOnce(1L, 43L))
    }

    @Test
    fun `blank scanlator is stored as absent and remains a safe anchor-only preference`() = runTest {
        val repository = repository()
        repository.save(preference(scanlator = "   "))

        assertEquals(preference(scanlator = null), repository.getOnce(1L, 42L))
    }

    @Test
    fun `a new repository instance restores the same preference after process recreation`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val firstProcess = repository(driver)
        firstProcess.save(preference())

        val recreatedProcess = repository(driver, initialize = false)

        assertEquals(preference(scanlator = "Flame Scans"), recreatedProcess.getOnce(1L, 42L))
    }

    @Test
    fun `malformed preference input is rejected without changing stored state`() = runTest {
        val repository = repository()
        repository.save(preference())

        val failure = runCatching {
            repository.save(preference().copy(anchorChapterUrl = " "))
        }.exceptionOrNull()
        assertEquals(IllegalArgumentException::class, failure!!::class)
        assertEquals(preference(scanlator = "Flame Scans"), repository.getOnce(1L, 42L))
    }
}

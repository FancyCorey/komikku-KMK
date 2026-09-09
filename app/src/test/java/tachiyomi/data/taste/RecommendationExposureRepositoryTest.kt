package tachiyomi.data.taste

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.data.AndroidDatabaseHandler
import tachiyomi.data.Chapters
import tachiyomi.data.Database
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.History
import tachiyomi.data.Mangas
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.UpdateStrategyColumnAdapter

/**
 * Real SQLDelight coverage for [RecommendationExposureRepositoryImpl] against an in-memory SQLite
 * database -- proves idempotent increment, pruning, clearing, and source-identity collision
 * avoidance through the actual generated queries, not a fake.
 */
class RecommendationExposureRepositoryTest {

    private fun openHandler(): DatabaseHandler {
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
        return AndroidDatabaseHandler(database, driver)
    }

    @Test
    fun `recording a new key creates a row with count 1`() = runTest {
        val repo = RecommendationExposureRepositoryImpl(openHandler())
        repo.recordExposureBatch(listOf(1L to "/m/a"), mapOf(1L to "/m/a" to 100L), timestamp = 1000L)

        val rows = repo.getBySourceUrls(listOf(1L to "/m/a"))
        assertEquals(1, rows.size)
        assertEquals(1, rows.single().exposureCount)
        assertEquals(1000L, rows.single().firstExposedAt)
        assertEquals(1000L, rows.single().lastExposedAt)
        assertEquals(100L, rows.single().mangaId)
    }

    @Test
    fun `recording the same key twice increments the count and updates lastExposedAt but not firstExposedAt`() = runTest {
        val repo = RecommendationExposureRepositoryImpl(openHandler())
        val key = 1L to "/m/a"
        repo.recordExposureBatch(listOf(key), mapOf(key to 100L), timestamp = 1000L)
        repo.recordExposureBatch(listOf(key), mapOf(key to 100L), timestamp = 2000L)

        val row = repo.getBySourceUrls(listOf(key)).single()
        assertEquals(2, row.exposureCount)
        assertEquals(1000L, row.firstExposedAt, "first-exposed timestamp must not move on a repeat sighting")
        assertEquals(2000L, row.lastExposedAt)
    }

    @Test
    fun `recording is idempotent-safe across many repeats -- count matches call count exactly`() = runTest {
        val repo = RecommendationExposureRepositoryImpl(openHandler())
        val key = 1L to "/m/a"
        repeat(5) { i -> repo.recordExposureBatch(listOf(key), mapOf(key to 100L), timestamp = 1000L + i) }

        assertEquals(5, repo.getBySourceUrls(listOf(key)).single().exposureCount)
    }

    @Test
    fun `two different sources with the identical relative url never collide`() = runTest {
        val repo = RecommendationExposureRepositoryImpl(openHandler())
        repo.recordExposureBatch(listOf(1L to "/manga/1"), mapOf(1L to "/manga/1" to 10L), timestamp = 1000L)
        repo.recordExposureBatch(listOf(2L to "/manga/1"), mapOf(2L to "/manga/1" to 20L), timestamp = 1000L)

        val sourceOne = repo.getBySourceUrls(listOf(1L to "/manga/1")).single()
        val sourceTwo = repo.getBySourceUrls(listOf(2L to "/manga/1")).single()
        assertEquals(1, sourceOne.exposureCount)
        assertEquals(1, sourceTwo.exposureCount)
        assertEquals(10L, sourceOne.mangaId)
        assertEquals(20L, sourceTwo.mangaId)
    }

    @Test
    fun `a batch records every distinct key in one call`() = runTest {
        val repo = RecommendationExposureRepositoryImpl(openHandler())
        val keys = listOf(1L to "/m/a", 1L to "/m/b", 2L to "/m/a")
        repo.recordExposureBatch(keys, keys.associateWith { null }, timestamp = 1000L)

        assertEquals(3, repo.getBySourceUrls(keys).size)
    }

    @Test
    fun `pruning removes only rows older than the cutoff`() = runTest {
        val repo = RecommendationExposureRepositoryImpl(openHandler())
        repo.recordExposureBatch(listOf(1L to "/old"), mapOf(1L to "/old" to null), timestamp = 1000L)
        repo.recordExposureBatch(listOf(1L to "/new"), mapOf(1L to "/new" to null), timestamp = 5000L)

        repo.pruneOlderThan(cutoff = 3000L)

        val remaining = repo.getBySource(1L)
        assertEquals(listOf("/new"), remaining.map { it.url })
    }

    @Test
    fun `pruning never touches a row at or after the cutoff`() = runTest {
        val repo = RecommendationExposureRepositoryImpl(openHandler())
        repo.recordExposureBatch(listOf(1L to "/at-cutoff"), mapOf(1L to "/at-cutoff" to null), timestamp = 3000L)

        repo.pruneOlderThan(cutoff = 3000L)

        assertEquals(1, repo.getBySource(1L).size, "a row exactly at the cutoff must survive (only strictly-older rows are removed)")
    }

    @Test
    fun `deleteBySource removes only that source's rows`() = runTest {
        val repo = RecommendationExposureRepositoryImpl(openHandler())
        repo.recordExposureBatch(listOf(1L to "/a"), mapOf(1L to "/a" to null), timestamp = 1000L)
        repo.recordExposureBatch(listOf(2L to "/a"), mapOf(2L to "/a" to null), timestamp = 1000L)

        repo.deleteBySource(1L)

        assertTrue(repo.getBySource(1L).isEmpty())
        assertEquals(1, repo.getBySource(2L).size)
    }

    @Test
    fun `clear (deleteAll) is the exposure-history clear action and removes every row`() = runTest {
        val repo = RecommendationExposureRepositoryImpl(openHandler())
        repo.recordExposureBatch(listOf(1L to "/a", 2L to "/b"), mapOf(1L to "/a" to null, 2L to "/b" to null), timestamp = 1000L)

        repo.deleteAll()

        assertTrue(repo.getBySource(1L).isEmpty())
        assertTrue(repo.getBySource(2L).isEmpty())
    }

    @Test
    fun `an empty key list is a safe no-op for get, record, and does not throw`() = runTest {
        val repo = RecommendationExposureRepositoryImpl(openHandler())
        assertTrue(repo.getBySourceUrls(emptyList()).isEmpty())
        repo.recordExposureBatch(emptyList(), emptyMap(), timestamp = 1000L)
        assertTrue(repo.getBySource(1L).isEmpty())
    }

    @Test
    fun `a nonexistent key returns nothing rather than throwing`() = runTest {
        val repo = RecommendationExposureRepositoryImpl(openHandler())
        assertTrue(repo.getBySourceUrls(listOf(99L to "/nowhere")).isEmpty())
    }

    @Test
    fun `mangaId can be null and is preserved as null`() = runTest {
        val repo = RecommendationExposureRepositoryImpl(openHandler())
        repo.recordExposureBatch(listOf(1L to "/unresolved"), mapOf(1L to "/unresolved" to null), timestamp = 1000L)

        assertNull(repo.getBySourceUrls(listOf(1L to "/unresolved")).single().mangaId)
    }
}
// KMK <--

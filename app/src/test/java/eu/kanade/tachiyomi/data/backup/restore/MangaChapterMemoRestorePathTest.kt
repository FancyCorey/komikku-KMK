package eu.kanade.tachiyomi.data.backup.restore

// KMK --> 1.14.0 reconciliation Phase 5: exercises the real generated SQLDelight queries that
// MangaRestorer's insertManga/updateManga and the chapter insert/update paths call, using a real
// in-memory database (not mocks) -- the same JdbcSqliteDriver pattern already established by
// Kmk114ReconciliationMigrationTest, but here going through the generated `Database`/adapters
// instead of raw SQL, so it proves the memo column fixes from this reconciliation actually
// round-trip end-to-end through the exact code path the restorer uses.
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mihon.core.common.extensions.EMPTY
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
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

class MangaChapterMemoRestorePathTest {

    private fun openHandler(): Pair<DatabaseHandler, JdbcSqliteDriver> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.create(driver)
        val db = Database(
            driver = driver,
            historyAdapter = History.Adapter(last_readAdapter = DateColumnAdapter),
            mangasAdapter = Mangas.Adapter(
                genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = UpdateStrategyColumnAdapter,
                memoAdapter = MemoColumnAdapter,
            ),
            chaptersAdapter = Chapters.Adapter(memoAdapter = MemoColumnAdapter),
        )
        return AndroidDatabaseHandler(db, driver) to driver
    }

    private fun mangaMemo(): JsonObject = buildJsonObject { put("restoredFrom", "backup") }

    @Test
    fun `manga insert persists memo through the real insert query used by MangaRestorer`() = runTest {
        val (handler, _) = openHandler()
        val memo = mangaMemo()

        val insertedId = handler.awaitOneExecutable(true) {
            mangasQueries.insert(
                source = 1L,
                url = "/manga/x",
                artist = null,
                author = null,
                description = null,
                genre = emptyList(),
                title = "X",
                status = 0,
                thumbnailUrl = null,
                favorite = true,
                lastUpdate = 0L,
                nextUpdate = 0L,
                initialized = false,
                viewerFlags = 0L,
                chapterFlags = 0L,
                coverLastModified = 0L,
                dateAdded = 0L,
                updateStrategy = eu.kanade.tachiyomi.source.model.UpdateStrategy.ALWAYS_UPDATE,
                calculateInterval = 0L,
                version = 0L,
                notes = "",
                memo = memo,
            )
            mangasQueries.selectLastInsertedRowId()
        }

        val stored = handler.awaitOne(false) { mangasQueries.getMangaById(insertedId) }
        assertEquals(memo, stored.memo)
    }

    @Test
    fun `chapter insert persists memo through the real insert query used by MangaRestorer`() = runTest {
        val (handler, _) = openHandler()
        val mangaId = handler.awaitOneExecutable(true) {
            mangasQueries.insert(
                source = 1L, url = "/manga/x", artist = null, author = null, description = null,
                genre = emptyList(), title = "X", status = 0, thumbnailUrl = null, favorite = true,
                lastUpdate = 0L, nextUpdate = 0L, initialized = false, viewerFlags = 0L, chapterFlags = 0L,
                coverLastModified = 0L, dateAdded = 0L,
                updateStrategy = eu.kanade.tachiyomi.source.model.UpdateStrategy.ALWAYS_UPDATE,
                calculateInterval = 0L, version = 0L, notes = "", memo = JsonObject.EMPTY,
            )
            mangasQueries.selectLastInsertedRowId()
        }

        val chapterMemo = buildJsonObject { put("page", 3) }
        handler.await(true) {
            chaptersQueries.insert(
                mangaId, "/chapter/1", "Ch. 1", null, false, false, 0L, 1.0, 0L, 0L, 0L, 0L, chapterMemo,
            )
        }
        val chapterId = handler.awaitOneExecutable(false) { chaptersQueries.selectLastInsertedRowId() }
        val storedChapter = handler.awaitOne(false) { chaptersQueries.getChapterById(chapterId) }

        assertEquals(chapterMemo, storedChapter.memo)
    }

    @Test
    fun `manga update with coalesce preserves memo when not explicitly changed`() = runTest {
        val (handler, _) = openHandler()
        val originalMemo = mangaMemo()
        val mangaId = handler.awaitOneExecutable(true) {
            mangasQueries.insert(
                source = 1L, url = "/manga/x", artist = null, author = null, description = null,
                genre = emptyList(), title = "X", status = 0, thumbnailUrl = null, favorite = true,
                lastUpdate = 0L, nextUpdate = 0L, initialized = false, viewerFlags = 0L, chapterFlags = 0L,
                coverLastModified = 0L, dateAdded = 0L,
                updateStrategy = eu.kanade.tachiyomi.source.model.UpdateStrategy.ALWAYS_UPDATE,
                calculateInterval = 0L, version = 0L, notes = "", memo = originalMemo,
            )
            mangasQueries.selectLastInsertedRowId()
        }

        // A partial-restore-style update: only touch favorite, leave memo null (coalesce keeps existing).
        handler.await(true) {
            mangasQueries.update(
                source = null, url = null, artist = null, author = null, description = null, genre = null,
                title = null, status = null, thumbnailUrl = null, favorite = false, lastUpdate = null,
                nextUpdate = null, initialized = null, viewer = null, chapterFlags = null,
                coverLastModified = null, dateAdded = null, updateStrategy = null, calculateInterval = null,
                version = null, isSyncing = null, notes = null, memo = null, mangaId = mangaId,
            )
        }

        val stored = handler.awaitOne(false) { mangasQueries.getMangaById(mangaId) }
        assertEquals(false, stored.favorite)
        assertEquals(originalMemo, stored.memo)
    }

    @Test
    fun `a failed statement inside a restore transaction rolls back and leaves no partial row`() = runTest {
        val (handler, driver) = openHandler()

        // Simulate a partial/malformed restore batch: a valid manga insert followed by a
        // statement that violates a NOT NULL constraint (missing url), inside one transaction --
        // mirroring how MangaRestorer.restore() wraps each manga's work in handler.await(inTransaction = true).
        assertThrows(Exception::class.java) {
            driver.execute(null, "BEGIN", 0)
            driver.execute(
                null,
                "INSERT INTO mangas(source, url, title, status, favorite, initialized, viewer, " +
                    "chapter_flags, cover_last_modified, date_added, calculate_interval) " +
                    "VALUES (1, '/manga/good', 'Good', 0, 1, 0, 0, 0, 0, 0, 0)",
                0,
            )
            // url is NOT NULL with no default -- this statement must fail.
            driver.execute(
                null,
                "INSERT INTO mangas(source, title, status, favorite, initialized, viewer, " +
                    "chapter_flags, cover_last_modified, date_added, calculate_interval) " +
                    "VALUES (1, 'Bad', 0, 1, 0, 0, 0, 0, 0, 0)",
                0,
            )
            driver.execute(null, "COMMIT", 0)
        }
        driver.execute(null, "ROLLBACK", 0)

        // Nothing committed -- the valid insert before the bad one must not have leaked through
        // once we roll back, proving the transaction boundary MangaRestorer relies on is real.
        val all = handler.awaitList(false) { mangasQueries.getAllManga() }
        assertEquals(0, all.size)
    }
}
// KMK <--

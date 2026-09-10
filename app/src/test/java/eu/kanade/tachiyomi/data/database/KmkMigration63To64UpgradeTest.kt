package eu.kanade.tachiyomi.data.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.data.Database

/**
 * Covers the **real production `63 -> 64` upgrade**, which no other test exercised.
 *
 * ## Why this file exists (the gap it closes)
 *
 * `KmkMigration64ExposureTest` applies raw `.sqm` text for `46..62` and then `64`, **skipping 63
 * entirely**. That is not the production upgrade path: a real device at schema 63 runs 63's effects
 * and then 64's. The skip was not arbitrary -- `63.sqm` cannot be executed as raw SQL at all. It
 * carries SQLDelight `import` directives (`import kotlin.Boolean;`) and column-adapter syntax
 * (`is_legacy INTEGER AS Boolean`, `memo BLOB AS JsonObject`), none of which SQLite understands, and
 * it depends on upstream tables (`extension_repos`, `mangas`, `chapters`) that the KMK `46..62` range
 * never creates.
 *
 * ## How this test reaches the real path anyway
 *
 * Instead of re-parsing `.sqm` text, this drives **`Database.Schema.migrate(...)`** -- the generated
 * SQLDelight migrator that ships in the app and is the actual code a device executes. SQLDelight has
 * already stripped the imports and adapter annotations at codegen time, so the emitted statements are
 * plain SQL.
 *
 * Note the SQLDelight version convention: migration file `N` runs when
 * `oldVersion <= N && newVersion > N`. So the real production **file 63** is
 * `migrate(driver, 63, 64)`, and the real production **file 64** is `migrate(driver, 64, 65)`.
 * `Database.Schema.version` includes all later additive migrations.
 *
 * The upstream tables 63 needs are created here with their genuine shapes: `extension_repos` is
 * copied verbatim from migration `32.sqm`, and `mangas`/`chapters` are minimal stand-ins carrying
 * only what 63's `ALTER TABLE ... ADD COLUMN memo` requires.
 *
 * Everything runs in-memory. No device database is touched.
 *
 * Run with:
 * `./gradlew :app:testDebugUnitTest --tests "*.KmkMigration63To64UpgradeTest"`
 */
class KmkMigration63To64UpgradeTest {

    private companion object {
        const val TABLE = "recommendation_exposure"

        /** SQLDelight applies file N when `oldVersion <= N && newVersion > N`. */
        const val FILE_63_OLD = 63L
        const val FILE_63_NEW = 64L
        const val FILE_64_OLD = 64L
        const val FILE_64_NEW = 65L

        val REQUIRED_COLUMNS = listOf(
            "source_id",
            "url",
            "manga_id",
            "first_exposed_at",
            "last_exposed_at",
            "exposure_count",
        )

        val PRIMARY_KEY_COLUMNS = listOf("source_id", "url")

        val REQUIRED_INDEXES = listOf(
            "recommendation_exposure_last_exposed_index",
            "recommendation_exposure_source_index",
        )
    }

    private fun openDriver() = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

    private fun JdbcSqliteDriver.exec(sql: String) = execute(null, sql, 0)

    private fun JdbcSqliteDriver.executeSqlScript(sql: String) {
        sql.replace(Regex("\\s+AS\\s+(Boolean|JsonObject)"), "")
            .lines()
            .filterNot { it.trim().startsWith("--") || it.trim().startsWith("import ") }
            .joinToString("\n")
            .split(";")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { stmt -> exec(stmt) }
    }

    private fun JdbcSqliteDriver.executeMigrationFile(migrationNum: Int) {
        val resource = javaClass.classLoader?.getResourceAsStream("$migrationNum.sqm")
            ?: error("Migration $migrationNum.sqm not found on test classpath")
        executeSqlScript(resource.bufferedReader().readText())
    }

    private fun JdbcSqliteDriver.tableNames(): Set<String> {
        val names = mutableSetOf<String>()
        executeQuery(
            identifier = null,
            sql = "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",
            mapper = { cursor ->
                while (cursor.next().value) {
                    cursor.getString(0)?.let(names::add)
                }
                QueryResult.Value(Unit)
            },
            parameters = 0,
        )
        return names
    }

    private fun JdbcSqliteDriver.indexNames(table: String): Set<String> {
        val names = mutableSetOf<String>()
        executeQuery(
            identifier = null,
            sql = "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='$table'",
            mapper = { cursor ->
                while (cursor.next().value) {
                    cursor.getString(0)?.let(names::add)
                }
                QueryResult.Value(Unit)
            },
            parameters = 0,
        )
        return names
    }

    private fun JdbcSqliteDriver.columnNames(table: String): Set<String> {
        val names = mutableSetOf<String>()
        executeQuery(
            identifier = null,
            sql = "PRAGMA table_info($table)",
            mapper = { cursor ->
                while (cursor.next().value) {
                    cursor.getString(1)?.let(names::add)
                }
                QueryResult.Value(Unit)
            },
            parameters = 0,
        )
        return names
    }

    private fun JdbcSqliteDriver.primaryKeyColumns(table: String): List<String> {
        val pk = mutableListOf<Pair<Long, String>>()
        executeQuery(
            identifier = null,
            sql = "PRAGMA table_info($table)",
            mapper = { cursor ->
                while (cursor.next().value) {
                    val name = cursor.getString(1)
                    val ordinal = cursor.getLong(5) ?: 0L
                    if (name != null && ordinal > 0L) pk += ordinal to name
                }
                QueryResult.Value(Unit)
            },
            parameters = 0,
        )
        return pk.sortedBy { it.first }.map { it.second }
    }

    private fun JdbcSqliteDriver.scalar(sql: String): Long {
        var value = -1L
        executeQuery(
            identifier = null,
            sql = sql,
            mapper = { cursor ->
                assertTrue(cursor.next().value)
                value = cursor.getLong(0) ?: -1L
                QueryResult.Value(Unit)
            },
            parameters = 0,
        )
        return value
    }

    /**
     * Builds a database in the genuine **pre-63** state: the upstream tables migration 63 operates on
     * plus the full KMK `46..62` range, with representative pre-existing rows so data preservation is
     * observable across both upgrades.
     */
    private fun buildPre63Database(): JdbcSqliteDriver {
        val driver = openDriver()

        // extension_repos: verbatim from migration 32.sqm -- the real shape 63's SELECT reads.
        driver.exec(
            """
            CREATE TABLE extension_repos (
                base_url TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                short_name TEXT,
                website TEXT NOT NULL,
                signing_key_fingerprint TEXT UNIQUE NOT NULL
            )
            """.trimIndent(),
        )
        // Minimal upstream tables carrying only what 63's ALTER TABLE ADD COLUMN needs.
        driver.exec("CREATE TABLE mangas (_id INTEGER NOT NULL PRIMARY KEY, title TEXT NOT NULL)")
        driver.exec("CREATE TABLE chapters (_id INTEGER NOT NULL PRIMARY KEY, manga_id INTEGER NOT NULL)")

        // Pre-existing upstream rows.
        driver.exec(
            """
            INSERT INTO extension_repos(base_url, name, short_name, website, signing_key_fingerprint)
            VALUES ('https://example.invalid/repo', 'Repo One', 'R1', 'https://example.invalid', 'fp-1')
            """.trimIndent(),
        )
        driver.exec("INSERT INTO mangas(_id, title) VALUES (11, 'Pre-existing manga')")
        driver.exec("INSERT INTO chapters(_id, manga_id) VALUES (21, 11)")

        // The KMK range, applied as raw .sqm text (these files are plain SQL).
        for (n in 46..62) driver.executeMigrationFile(n)

        // A representative KMK row that must survive both upgrades.
        driver.exec(
            """
            INSERT INTO manga_cross_source_link(source, url, group_id, title, created_at, updated_at)
            VALUES (7, '/survives', 'group-63-64', 'Survivor', 0, 0)
            """.trimIndent(),
        )
        return driver
    }

    // ---- The real 63 -> 64 upgrade ----

    @Test
    fun `a schema-63 database gains the exposure table via the real production 64 migration`() {
        val driver = buildPre63Database()

        // Real production migration file 63.
        Database.Schema.migrate(driver, FILE_63_OLD, FILE_63_NEW)

        // Sanity: we are genuinely at schema 63 and the exposure table does NOT exist yet. This is
        // the precondition the previous test never established.
        assertTrue("extension_store" in driver.tableNames()) { "migration 63 did not run" }
        assertFalse(TABLE in driver.tableNames()) { "$TABLE must not exist before migration 64" }

        // Real production migration file 64.
        Database.Schema.migrate(driver, FILE_64_OLD, FILE_64_NEW)

        assertTrue(TABLE in driver.tableNames()) { "migration 64 did not create $TABLE" }
    }

    @Test
    fun `the 63 to 64 upgrade produces every required column`() {
        val driver = buildPre63Database()
        Database.Schema.migrate(driver, FILE_63_OLD, FILE_63_NEW)
        Database.Schema.migrate(driver, FILE_64_OLD, FILE_64_NEW)

        val columns = driver.columnNames(TABLE)
        for (column in REQUIRED_COLUMNS) {
            assertTrue(column in columns) { "Missing column '$column' after 63 -> 64. Found: $columns" }
        }
    }

    @Test
    fun `the 63 to 64 upgrade produces the composite (source_id, url) primary key`() {
        val driver = buildPre63Database()
        Database.Schema.migrate(driver, FILE_63_OLD, FILE_63_NEW)
        Database.Schema.migrate(driver, FILE_64_OLD, FILE_64_NEW)

        assertEquals(PRIMARY_KEY_COLUMNS, driver.primaryKeyColumns(TABLE))
    }

    @Test
    fun `the 63 to 64 upgrade produces both indexes`() {
        val driver = buildPre63Database()
        Database.Schema.migrate(driver, FILE_63_OLD, FILE_63_NEW)
        Database.Schema.migrate(driver, FILE_64_OLD, FILE_64_NEW)

        val indexes = driver.indexNames(TABLE)
        for (index in REQUIRED_INDEXES) {
            assertTrue(index in indexes) { "Missing index '$index' after 63 -> 64. Found: $indexes" }
        }
    }

    @Test
    fun `migration 63's own effects survive the subsequent 64 upgrade`() {
        val driver = buildPre63Database()
        Database.Schema.migrate(driver, FILE_63_OLD, FILE_63_NEW)
        Database.Schema.migrate(driver, FILE_64_OLD, FILE_64_NEW)

        // 63 converted extension_repos -> extension_store and dropped the old table.
        assertTrue("extension_store" in driver.tableNames())
        assertFalse("extension_repos" in driver.tableNames())
        assertEquals(
            1L,
            driver.scalar("SELECT COUNT(*) FROM extension_store WHERE index_url = 'https://example.invalid/repo/repo.json'"),
        ) { "63's converted extension_store row was lost during the 64 upgrade" }

        // 63 added the memo columns.
        assertTrue("memo" in driver.columnNames("mangas"))
        assertTrue("memo" in driver.columnNames("chapters"))
    }

    @Test
    fun `unrelated pre-existing rows survive the full 63 to 64 upgrade`() {
        val driver = buildPre63Database()
        Database.Schema.migrate(driver, FILE_63_OLD, FILE_63_NEW)
        Database.Schema.migrate(driver, FILE_64_OLD, FILE_64_NEW)

        assertEquals(1L, driver.scalar("SELECT COUNT(*) FROM mangas WHERE _id = 11"))
        assertEquals(1L, driver.scalar("SELECT COUNT(*) FROM chapters WHERE _id = 21"))
        assertEquals(
            1L,
            driver.scalar("SELECT COUNT(*) FROM manga_cross_source_link WHERE group_id = 'group-63-64'"),
        ) { "A pre-existing KMK row was lost during the 63 -> 64 upgrade" }
    }

    @Test
    fun `unrelated tables all survive the full 63 to 64 upgrade`() {
        val driver = buildPre63Database()
        Database.Schema.migrate(driver, FILE_63_OLD, FILE_63_NEW)
        val tablesAfter63 = driver.tableNames()

        Database.Schema.migrate(driver, FILE_64_OLD, FILE_64_NEW)
        val tablesAfter64 = driver.tableNames()

        for (table in tablesAfter63) {
            assertTrue(table in tablesAfter64) { "Table '$table' disappeared during the 64 upgrade" }
        }
        assertTrue(TABLE in tablesAfter64)
    }

    @Test
    fun `re-running the real 64 migration on an already-upgraded database is a no-op, not an error`() {
        val driver = buildPre63Database()
        Database.Schema.migrate(driver, FILE_63_OLD, FILE_63_NEW)
        Database.Schema.migrate(driver, FILE_64_OLD, FILE_64_NEW)

        driver.exec(
            """
            INSERT INTO recommendation_exposure(source_id, url, manga_id, first_exposed_at, last_exposed_at, exposure_count)
            VALUES (1, '/idempotent', NULL, 5, 5, 3)
            """.trimIndent(),
        )

        // IF NOT EXISTS makes the replay safe; the pre-existing row must also be untouched.
        Database.Schema.migrate(driver, FILE_64_OLD, FILE_64_NEW)

        assertTrue(TABLE in driver.tableNames())
        assertEquals(
            3L,
            driver.scalar("SELECT exposure_count FROM recommendation_exposure WHERE source_id=1 AND url='/idempotent'"),
        ) { "Re-running migration 64 must not reset existing exposure rows" }
    }

    @Test
    fun `a combined 63-through-64 upgrade in one call reaches the same end state`() {
        // A device several versions behind upgrades in a single migrate() call rather than stepwise.
        val driver = buildPre63Database()
        Database.Schema.migrate(driver, FILE_63_OLD, FILE_64_NEW)

        assertTrue("extension_store" in driver.tableNames())
        assertFalse("extension_repos" in driver.tableNames())
        assertTrue(TABLE in driver.tableNames())
        assertEquals(PRIMARY_KEY_COLUMNS, driver.primaryKeyColumns(TABLE))
        for (index in REQUIRED_INDEXES) {
            assertTrue(index in driver.indexNames(TABLE))
        }
    }

    // ---- Fresh-install path stays valid alongside the upgrade path ----

    @Test
    fun `the fresh Database Schema create path still yields the same exposure shape as the upgrade`() {
        val fresh = openDriver()
        Database.Schema.create(fresh)

        val upgraded = buildPre63Database()
        Database.Schema.migrate(upgraded, FILE_63_OLD, FILE_64_NEW)

        assertEquals(
            fresh.columnNames(TABLE),
            upgraded.columnNames(TABLE),
        ) { "Fresh-install and upgraded schemas disagree on $TABLE columns" }
        assertEquals(fresh.primaryKeyColumns(TABLE), upgraded.primaryKeyColumns(TABLE))
        assertEquals(fresh.indexNames(TABLE), upgraded.indexNames(TABLE))
    }

    @Test
    fun `the generated schema includes additive migrations after 65`() {
        assertTrue(Database.Schema.version >= 66L)
    }

    // ---- Repository query shapes against the genuinely-upgraded schema ----

    @Test
    fun `insert, increment, prune, and clear all work against the 63 to 64 upgraded schema`() {
        val driver = buildPre63Database()
        Database.Schema.migrate(driver, FILE_63_OLD, FILE_64_NEW)

        fun record(sourceId: Long, url: String, timestamp: Long) = driver.exec(
            """
            INSERT INTO recommendation_exposure(source_id, url, manga_id, first_exposed_at, last_exposed_at, exposure_count)
            VALUES ($sourceId, '$url', NULL, $timestamp, $timestamp, 1)
            ON CONFLICT(source_id, url) DO UPDATE SET
                last_exposed_at = $timestamp,
                exposure_count = exposure_count + 1
            """.trimIndent(),
        )

        record(1L, "/a", 1_000L)
        assertEquals(1L, driver.scalar("SELECT exposure_count FROM recommendation_exposure WHERE source_id=1 AND url='/a'"))

        record(1L, "/a", 2_000L)
        assertEquals(
            2L,
            driver.scalar("SELECT exposure_count FROM recommendation_exposure WHERE source_id=1 AND url='/a'"),
        ) { "ON CONFLICT must increment, not duplicate" }
        assertEquals(
            1_000L,
            driver.scalar("SELECT first_exposed_at FROM recommendation_exposure WHERE source_id=1 AND url='/a'"),
        ) { "first_exposed_at must be pinned across repeat exposures" }

        // Composite identity: the same url under a different source is a distinct row.
        record(2L, "/a", 3_000L)
        assertEquals(2L, driver.scalar("SELECT COUNT(*) FROM recommendation_exposure WHERE url='/a'"))

        // Strict < cutoff, matching pruneOlderThan.
        driver.exec("DELETE FROM recommendation_exposure WHERE last_exposed_at < 3000")
        assertEquals(1L, driver.scalar("SELECT COUNT(*) FROM recommendation_exposure"))

        // deleteAll backs "Clear repeat history" and must not touch anything else.
        driver.exec("DELETE FROM recommendation_exposure")
        assertEquals(0L, driver.scalar("SELECT COUNT(*) FROM recommendation_exposure"))
        assertEquals(
            1L,
            driver.scalar("SELECT COUNT(*) FROM manga_cross_source_link WHERE group_id = 'group-63-64'"),
        ) { "Clearing exposure history must never touch another table" }
        assertEquals(1L, driver.scalar("SELECT COUNT(*) FROM mangas WHERE _id = 11"))
    }
}
// KMK <--

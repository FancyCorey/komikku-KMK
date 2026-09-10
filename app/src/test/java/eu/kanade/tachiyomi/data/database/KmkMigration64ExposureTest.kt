package eu.kanade.tachiyomi.data.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.data.Database

/**
 * Dedicated coverage for migration `64.sqm`, which creates the local-only `recommendation_exposure`
 * table.
 *
 * `KmkMigrationTest` covers migrations 46..62 as one contiguous KMK range. Migration 63 is the
 * upstream 1.14.0 reconciliation (it deliberately ALTERs upstream tables and carries SQLDelight
 * `import` directives), so folding 63/64 into that range would break that test's own
 * "KMK migrations do not ALTER upstream tables" invariant. This file therefore covers 64 directly
 * rather than widening a range whose invariants do not apply to 63.
 *
 * Everything here runs against an in-memory SQLite database. No device database is touched.
 *
 * Run with:
 * `./gradlew :app:testDebugUnitTest --tests "*.KmkMigration64ExposureTest"`
 */
class KmkMigration64ExposureTest {

    private companion object {
        const val TABLE = "recommendation_exposure"

        val REQUIRED_COLUMNS = listOf(
            "source_id",
            "url",
            "manga_id",
            "first_exposed_at",
            "last_exposed_at",
            "exposure_count",
        )

        /** Composite primary key -- identity is (source_id, url), never url alone. */
        val PRIMARY_KEY_COLUMNS = listOf("source_id", "url")

        val REQUIRED_INDEXES = listOf(
            "recommendation_exposure_last_exposed_index",
            "recommendation_exposure_source_index",
        )
    }

    private fun openDriver() = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

    private fun JdbcSqliteDriver.executeSqlScript(sql: String) {
        sql.replace(Regex("\\s+AS\\s+(Boolean|JsonObject)"), "")
            .lines()
            .filterNot { it.trim().startsWith("--") || it.trim().startsWith("import ") }
            .joinToString("\n")
            .split(";")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { stmt -> execute(null, stmt, 0) }
    }

    private fun JdbcSqliteDriver.executeMigration(migrationNum: Int) {
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

    /** Columns participating in the primary key, ordered by their `pk` ordinal. */
    private fun JdbcSqliteDriver.primaryKeyColumns(table: String): List<String> {
        val pk = mutableListOf<Pair<Long, String>>()
        executeQuery(
            identifier = null,
            sql = "PRAGMA table_info($table)",
            mapper = { cursor ->
                while (cursor.next().value) {
                    val name = cursor.getString(1)
                    val pkOrdinal = cursor.getLong(5) ?: 0L
                    if (name != null && pkOrdinal > 0L) pk += pkOrdinal to name
                }
                QueryResult.Value(Unit)
            },
            parameters = 0,
        )
        return pk.sortedBy { it.first }.map { it.second }
    }

    private fun JdbcSqliteDriver.countRows(sql: String): Long {
        var count = -1L
        executeQuery(
            identifier = null,
            sql = sql,
            mapper = { cursor ->
                assertTrue(cursor.next().value)
                count = cursor.getLong(0) ?: -1L
                QueryResult.Value(Unit)
            },
            parameters = 0,
        )
        return count
    }

    // ---- File-level contract ----

    @Test
    fun `migration 64 is present on the test classpath`() {
        val resource = javaClass.classLoader?.getResourceAsStream("64.sqm")
        assertNotNull(resource) { "64.sqm not found on test classpath" }
        resource?.close()
    }

    @Test
    fun `migration 64 is additive only - no DROP and no ALTER of any table`() {
        val content = javaClass.classLoader?.getResourceAsStream("64.sqm")
            ?.bufferedReader()?.readText()
            ?: error("64.sqm not found")
        val statements = content.replace(Regex("\\s+AS\\s+(Boolean|JsonObject)"), "").lines()
            .filterNot { it.trim().startsWith("--") || it.trim().startsWith("import ") }
            .joinToString("\n")
            .split(";")
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }

        assertTrue(statements.none { it.startsWith("DROP") }) { "64.sqm must not DROP anything" }
        assertTrue(statements.none { it.startsWith("ALTER TABLE") }) { "64.sqm must not ALTER any table" }
        assertTrue(statements.isNotEmpty())
    }

    @Test
    fun `migration 64 CREATE statements use IF NOT EXISTS, matching the project idempotency contract`() {
        val content = javaClass.classLoader?.getResourceAsStream("64.sqm")
            ?.bufferedReader()?.readText()
            ?: error("64.sqm not found")
        val creates = content.lines()
            .filter { it.trimStart().uppercase().startsWith("CREATE ") }
        assertTrue(creates.isNotEmpty()) { "64.sqm should contain CREATE statements" }
        for (stmt in creates) {
            assertTrue(stmt.uppercase().contains("IF NOT EXISTS")) {
                "64.sqm has a CREATE without IF NOT EXISTS: $stmt"
            }
        }
    }

    @Test
    fun `migration 64 carries a KMK marker comment`() {
        val content = javaClass.classLoader?.getResourceAsStream("64.sqm")
            ?.bufferedReader()?.readText()
            ?: error("64.sqm not found")
        assertTrue(content.contains("KMK")) { "64.sqm is missing a KMK comment marker" }
    }

    // ---- Applied-schema shape ----

    @Test
    fun `migration 64 creates the recommendation_exposure table`() {
        val driver = openDriver()
        driver.executeMigration(64)
        assertTrue(TABLE in driver.tableNames())
    }

    @Test
    fun `migration 64 creates every required column`() {
        val driver = openDriver()
        driver.executeMigration(64)
        val columns = driver.columnNames(TABLE)
        for (column in REQUIRED_COLUMNS) {
            assertTrue(column in columns) { "Missing column '$column' after migration 64. Found: $columns" }
        }
    }

    @Test
    fun `migration 64 uses the composite (source_id, url) primary key, never url alone`() {
        val driver = openDriver()
        driver.executeMigration(64)
        assertEquals(PRIMARY_KEY_COLUMNS, driver.primaryKeyColumns(TABLE))
    }

    @Test
    fun `migration 64 creates both required indexes`() {
        val driver = openDriver()
        driver.executeMigration(64)
        val indexes = driver.indexNames(TABLE)
        for (index in REQUIRED_INDEXES) {
            assertTrue(index in indexes) { "Missing index '$index' after migration 64. Found: $indexes" }
        }
    }

    @Test
    fun `migration 64 is idempotent - applying it twice does not error`() {
        val driver = openDriver()
        driver.executeMigration(64)
        driver.executeMigration(64)
        assertTrue(TABLE in driver.tableNames())
    }

    // ---- Real upgrade path from the prior schema ----

    @Test
    fun `an existing database upgrades through 64 and keeps its unrelated tables`() {
        val driver = openDriver()
        for (n in 46..62) driver.executeMigration(n)
        val tablesBefore = driver.tableNames()
        assertTrue(TABLE !in tablesBefore) { "recommendation_exposure must not exist before migration 64" }

        driver.executeMigration(64)

        val tablesAfter = driver.tableNames()
        assertTrue(TABLE in tablesAfter)
        // Every pre-existing table survives the upgrade.
        for (table in tablesBefore) {
            assertTrue(table in tablesAfter) { "Table '$table' disappeared during migration 64" }
        }
    }

    @Test
    fun `an existing database upgrades through 64 without losing unrelated rows`() {
        val driver = openDriver()
        for (n in 46..50) driver.executeMigration(n) // 50 creates manga_cross_source_link
        driver.execute(
            null,
            """
            INSERT INTO manga_cross_source_link(source, url, group_id, title, created_at, updated_at)
            VALUES (7, '/pre-existing', 'group-64', 'Pre-existing', 0, 0)
            """.trimIndent(),
            0,
        )
        for (n in 51..62) driver.executeMigration(n)
        driver.executeMigration(64)

        assertEquals(
            1L,
            driver.countRows("SELECT COUNT(*) FROM manga_cross_source_link WHERE group_id = 'group-64'"),
        ) { "A pre-existing row was lost while upgrading through migration 64" }
    }

    @Test
    fun `a fresh database receives the table through the normal SQLDelight schema path`() {
        // Fresh installs never run .sqm files -- they get the canonical .sq schema. This proves the
        // two paths agree, which is the failure mode a migration-only test would miss entirely.
        val driver = openDriver()
        Database.Schema.create(driver)

        assertTrue(TABLE in driver.tableNames())
        val columns = driver.columnNames(TABLE)
        for (column in REQUIRED_COLUMNS) {
            assertTrue(column in columns) { "Fresh schema is missing column '$column'. Found: $columns" }
        }
        assertEquals(PRIMARY_KEY_COLUMNS, driver.primaryKeyColumns(TABLE))
        val indexes = driver.indexNames(TABLE)
        for (index in REQUIRED_INDEXES) {
            assertTrue(index in indexes) { "Fresh schema is missing index '$index'. Found: $indexes" }
        }
    }

    // ---- Repository query shapes against the migrated schema ----

    @Test
    fun `insert, upsert-increment, prune, and clear all work against the migrated schema`() {
        val driver = openDriver()
        for (n in 46..62) driver.executeMigration(n)
        driver.executeMigration(64)

        // Mirrors recommendation_exposure.sq's recordExposure: insert, then increment on conflict.
        fun record(sourceId: Long, url: String, timestamp: Long) {
            driver.execute(
                null,
                """
                INSERT INTO recommendation_exposure(source_id, url, manga_id, first_exposed_at, last_exposed_at, exposure_count)
                VALUES ($sourceId, '$url', NULL, $timestamp, $timestamp, 1)
                ON CONFLICT(source_id, url) DO UPDATE SET
                    last_exposed_at = $timestamp,
                    exposure_count = exposure_count + 1
                """.trimIndent(),
                0,
            )
        }

        record(1L, "/a", 1_000L)
        assertEquals(1L, driver.countRows("SELECT exposure_count FROM recommendation_exposure WHERE source_id=1 AND url='/a'"))

        record(1L, "/a", 2_000L)
        assertEquals(
            2L,
            driver.countRows("SELECT exposure_count FROM recommendation_exposure WHERE source_id=1 AND url='/a'"),
        ) { "ON CONFLICT must increment rather than duplicate" }
        assertEquals(
            1_000L,
            driver.countRows("SELECT first_exposed_at FROM recommendation_exposure WHERE source_id=1 AND url='/a'"),
        ) { "first_exposed_at must not move on a repeat exposure" }

        // The composite key means an identical url under a different source is a distinct row.
        record(2L, "/a", 3_000L)
        assertEquals(2L, driver.countRows("SELECT COUNT(*) FROM recommendation_exposure WHERE url='/a'"))

        // pruneOlderThan uses a strict < cutoff.
        driver.execute(null, "DELETE FROM recommendation_exposure WHERE last_exposed_at < 3000", 0)
        assertEquals(
            1L,
            driver.countRows("SELECT COUNT(*) FROM recommendation_exposure"),
        ) { "Prune must remove only rows strictly older than the cutoff" }

        // deleteAll backs the user-facing "Clear repeat history" action.
        driver.execute(null, "DELETE FROM recommendation_exposure", 0)
        assertEquals(0L, driver.countRows("SELECT COUNT(*) FROM recommendation_exposure"))
    }

    @Test
    fun `clearing exposure rows leaves unrelated tables untouched`() {
        val driver = openDriver()
        for (n in 46..50) driver.executeMigration(n)
        driver.execute(
            null,
            """
            INSERT INTO manga_cross_source_link(source, url, group_id, title, created_at, updated_at)
            VALUES (7, '/keep', 'group-keep', 'Keep', 0, 0)
            """.trimIndent(),
            0,
        )
        for (n in 51..62) driver.executeMigration(n)
        driver.executeMigration(64)
        driver.execute(
            null,
            """
            INSERT INTO recommendation_exposure(source_id, url, manga_id, first_exposed_at, last_exposed_at, exposure_count)
            VALUES (1, '/x', NULL, 1, 1, 1)
            """.trimIndent(),
            0,
        )

        driver.execute(null, "DELETE FROM recommendation_exposure", 0)

        assertEquals(0L, driver.countRows("SELECT COUNT(*) FROM recommendation_exposure"))
        assertEquals(
            1L,
            driver.countRows("SELECT COUNT(*) FROM manga_cross_source_link WHERE group_id = 'group-keep'"),
        ) { "Clearing exposure history must never touch any other table" }
    }
}
// KMK <--

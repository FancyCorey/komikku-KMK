package eu.kanade.tachiyomi.data.database

// KMK -->
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * R-026: Verifies that all KMK SQLDelight migrations (46–55) apply cleanly, in order,
 * against an in-memory SQLite database, and that the resulting schema contains the expected
 * tables. Also verifies idempotency (IF NOT EXISTS) and ALTER TABLE correctness.
 *
 * Run with: ./gradlew :app:testDebugUnitTest --tests "*.KmkMigrationTest"
 */
class KmkMigrationTest {

    companion object {
        private val KMK_MIGRATION_RANGE = 46..55

        // Tables introduced by each KMK migration (ALTER TABLE migrations add no new tables)
        private val NEW_TABLES_BY_MIGRATION: Map<Int, List<String>> = mapOf(
            46 to listOf("manga_taste", "tag_taste", "tag_alias", "recommendation_cache", "recommendation_disabled_source"),
            47 to listOf("source_evaluation"),
            48 to listOf("source_evaluation_probe_marker", "source_evaluation_unsafe_source"),
            49 to listOf("unsafe_extension_package"),
            50 to listOf("manga_cross_source_link"),
            51 to emptyList(), // ALTER TABLE source_evaluation – adds version columns
            52 to listOf("source_recommendation_fit"),
            53 to listOf("manga_source_quality_signal"),
            54 to listOf("ocr_indexed_page"),
            55 to emptyList(), // ALTER TABLE ocr_indexed_page – adds status columns
        )

        // table name → columns added by the ALTER TABLE migration for that migration number
        private val ALTER_MIGRATION_TABLE: Map<Int, String> = mapOf(
            51 to "source_evaluation",
            55 to "ocr_indexed_page",
        )
        private val ALTER_MIGRATION_COLUMNS: Map<Int, List<String>> = mapOf(
            51 to listOf("extension_version_name", "extension_version_code", "extension_apk_name"),
            55 to listOf("recognized_text_length", "recognized_word_count", "ocr_status"),
        )

        val ALL_KMK_TABLES: Set<String> = NEW_TABLES_BY_MIGRATION.values.flatten().toSet()
    }

    private fun openDriver() = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

    private fun JdbcSqliteDriver.executeMigration(migrationNum: Int) {
        val resource = javaClass.classLoader?.getResourceAsStream("$migrationNum.sqm")
            ?: error("Migration $migrationNum.sqm not found on test classpath")
        val sql = resource.bufferedReader().readText()
        executeSqlScript(sql)
    }

    private fun JdbcSqliteDriver.executeSqlScript(sql: String) {
        sql.lines()
            .filterNot { it.trim().startsWith("--") }
            .joinToString("\n")
            .split(";")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { stmt -> execute(null, stmt, 0) }
    }

    private fun JdbcSqliteDriver.tableNames(): Set<String> {
        val names = mutableSetOf<String>()
        executeQuery(
            identifier = null,
            sql = "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",
            mapper = { cursor ->
                while (cursor.next().value) {
                    cursor.getString(0)?.let { names.add(it) }
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
                    cursor.getString(1)?.let { names.add(it) }
                }
                QueryResult.Value(Unit)
            },
            parameters = 0,
        )
        return names
    }

    // ---- Structural tests (no SQLite execution needed) ----

    @Test
    fun `all KMK migration files 46-55 are present on classpath`() {
        for (n in KMK_MIGRATION_RANGE) {
            val resource = javaClass.classLoader?.getResourceAsStream("$n.sqm")
            assertNotNull(resource) { "Migration $n.sqm not found on test classpath" }
            resource?.close()
        }
    }

    @Test
    fun `KMK migration range 46-55 is exactly 10 files`() {
        assertEquals(10, KMK_MIGRATION_RANGE.count())
    }

    @Test
    fun `KMK migration files contain KMK markers`() {
        for (n in KMK_MIGRATION_RANGE) {
            val content = javaClass.classLoader?.getResourceAsStream("$n.sqm")
                ?.bufferedReader()?.readText()
                ?: error("Migration $n.sqm not found")
            assertTrue(content.contains("KMK")) {
                "Migration $n.sqm is missing a KMK comment marker"
            }
        }
    }

    @Test
    fun `CREATE TABLE migrations use IF NOT EXISTS for idempotency`() {
        for (n in KMK_MIGRATION_RANGE) {
            val content = javaClass.classLoader?.getResourceAsStream("$n.sqm")
                ?.bufferedReader()?.readText()
                ?: error("Migration $n.sqm not found")
            val createStatements = content.lines()
                .filter { it.trimStart().uppercase().startsWith("CREATE TABLE") }
            for (stmt in createStatements) {
                assertTrue(stmt.uppercase().contains("IF NOT EXISTS")) {
                    "Migration $n.sqm has CREATE TABLE without IF NOT EXISTS: $stmt"
                }
            }
        }
    }

    @Test
    fun `KMK migrations do not DROP tables or ALTER upstream tables`() {
        for (n in KMK_MIGRATION_RANGE) {
            val content = javaClass.classLoader?.getResourceAsStream("$n.sqm")
                ?.bufferedReader()?.readText()
                ?: error("Migration $n.sqm not found")
            val statements = content.lines()
                .filterNot { it.trim().startsWith("--") }
                .joinToString("\n")
                .split(";")
                .map { it.trim().uppercase() }
                .filter { it.isNotBlank() }

            for (stmt in statements) {
                assertFalse(stmt.startsWith("DROP TABLE")) {
                    "Migration $n.sqm contains DROP TABLE: $stmt"
                }
                if (stmt.startsWith("ALTER TABLE")) {
                    val targetTable = stmt.removePrefix("ALTER TABLE").trim().split(" ").first().lowercase()
                    assertTrue(ALL_KMK_TABLES.contains(targetTable)) {
                        "Migration $n.sqm alters non-KMK table '$targetTable' — potential rebase conflict"
                    }
                }
            }
        }
    }

    // ---- Execution tests (require JdbcSqliteDriver + sqlite-driver dep) ----

    @Test
    fun `all KMK migrations 46-55 apply cleanly to empty database in sequence`() {
        val driver = openDriver()
        for (n in KMK_MIGRATION_RANGE) {
            driver.executeMigration(n)
        }
        val tables = driver.tableNames()
        for (table in ALL_KMK_TABLES) {
            assertTrue(tables.contains(table)) {
                "Table '$table' missing after applying all KMK migrations"
            }
        }
    }

    @Test
    fun `each migration introduces only its expected tables`() {
        val driver = openDriver()
        var seenTables = driver.tableNames()

        for (n in KMK_MIGRATION_RANGE) {
            driver.executeMigration(n)
            val afterTables = driver.tableNames()
            val newTables = afterTables - seenTables
            val expected = NEW_TABLES_BY_MIGRATION[n]!!.toSet()
            assertEquals(expected, newTables) {
                "Migration $n introduced unexpected tables. Expected $expected but got $newTables"
            }
            seenTables = afterTables
        }
    }

    @Test
    fun `migration 51 adds version columns to source_evaluation`() {
        val driver = openDriver()
        for (n in 46..51) driver.executeMigration(n)
        val table = ALTER_MIGRATION_TABLE[51]!!
        val expectedCols = ALTER_MIGRATION_COLUMNS[51]!!
        val actual = driver.columnNames(table)
        for (col in expectedCols) {
            assertTrue(actual.contains(col)) {
                "Migration 51: column '$col' missing from '$table'. Found: $actual"
            }
        }
    }

    @Test
    fun `migration 55 adds status columns to ocr_indexed_page`() {
        val driver = openDriver()
        for (n in 46..55) driver.executeMigration(n)
        val table = ALTER_MIGRATION_TABLE[55]!!
        val expectedCols = ALTER_MIGRATION_COLUMNS[55]!!
        val actual = driver.columnNames(table)
        for (col in expectedCols) {
            assertTrue(actual.contains(col)) {
                "Migration 55: column '$col' missing from '$table'. Found: $actual"
            }
        }
    }

    @Test
    fun `IF NOT EXISTS makes CREATE TABLE idempotent - re-running CREATE TABLE does not error`() {
        val driver = openDriver()
        for (n in KMK_MIGRATION_RANGE) driver.executeMigration(n)

        // Re-run only CREATE TABLE statements (not indexes or ALTER TABLE — migrations run exactly once
        // in production, but IF NOT EXISTS on CREATE TABLE guards against the historical case where
        // application code created a table before the migration file existed, e.g. migration 47).
        for (n in KMK_MIGRATION_RANGE) {
            val content = javaClass.classLoader?.getResourceAsStream("$n.sqm")
                ?.bufferedReader()?.readText()
                ?: error("Migration $n.sqm not found")
            content.lines()
                .filterNot { it.trim().startsWith("--") }
                .joinToString("\n")
                .split(";")
                .map { it.trim() }
                .filter { it.uppercase().startsWith("CREATE TABLE") }
                .forEach { stmt -> driver.execute(null, stmt, 0) }
        }
        // No exception = IF NOT EXISTS is consistently applied to all CREATE TABLE statements
        assertTrue(true)
    }

    @Test
    fun `manga_source_quality_signal has all required columns after migration 53`() {
        val driver = openDriver()
        for (n in 46..53) driver.executeMigration(n)
        val cols = driver.columnNames("manga_source_quality_signal")
        val required = listOf(
            "id", "origin_source_id", "origin_url", "origin_title",
            "selected_source_id", "selected_url", "selected_title", "selected_source_name",
            "chapter_number", "chapter_name", "selected_at", "quality_signal_version",
        )
        for (col in required) {
            assertTrue(cols.contains(col)) {
                "manga_source_quality_signal missing column '$col'. Found: $cols"
            }
        }
    }

    @Test
    fun `all expected KMK tables present after full migration sequence`() {
        val driver = openDriver()
        for (n in KMK_MIGRATION_RANGE) driver.executeMigration(n)
        val tables = driver.tableNames()
        val expected = setOf(
            "manga_taste",
            "tag_taste",
            "tag_alias",
            "recommendation_cache",
            "recommendation_disabled_source",
            "source_evaluation",
            "source_evaluation_probe_marker",
            "source_evaluation_unsafe_source",
            "unsafe_extension_package",
            "manga_cross_source_link",
            "source_recommendation_fit",
            "manga_source_quality_signal",
            "ocr_indexed_page",
        )
        for (table in expected) {
            assertTrue(tables.contains(table)) { "Expected KMK table '$table' not found after full migration" }
        }
    }
}
// KMK <--

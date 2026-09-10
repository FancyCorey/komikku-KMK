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
 * R-026: Verifies that all KMK SQLDelight migrations (47–63) apply cleanly, in order,
 * against an in-memory SQLite database, and that the resulting schema contains the expected
 * tables. Also verifies idempotency (IF NOT EXISTS) and ALTER TABLE correctness.
 *
 * Run with: ./gradlew :app:testDebugUnitTest --tests "*.KmkMigrationTest"
 */
class KmkMigrationTest {

    companion object {
        private val KMK_MIGRATION_RANGE = 47..63

        // Tables introduced by each KMK migration (ALTER TABLE migrations add no new tables)
        private val NEW_TABLES_BY_MIGRATION: Map<Int, List<String>> = mapOf(
            47 to listOf("manga_taste", "tag_taste", "tag_alias", "recommendation_cache", "recommendation_disabled_source"),
            48 to listOf("source_evaluation"),
            49 to listOf("source_evaluation_probe_marker", "source_evaluation_unsafe_source"),
            50 to listOf("unsafe_extension_package"),
            51 to listOf("manga_cross_source_link"),
            52 to emptyList(), // ALTER TABLE source_evaluation – adds version columns
            53 to listOf("source_recommendation_fit"),
            54 to listOf("manga_source_quality_signal"),
            55 to listOf("ocr_indexed_page"),
            56 to emptyList(), // ALTER TABLE ocr_indexed_page – adds status columns
            // KMK --> v0.7.38: For You candidate discovery memory
            57 to listOf("recommendation_candidate_memory"),
            // KMK <--
            // KMK --> v0.7.39: For You rolling discovery progress
            58 to listOf("recommendation_discovery_progress"),
            // KMK <--
            // KMK --> v0.7.40: retry metadata columns (ALTER TABLE, no new table)
            59 to emptyList(),
            // KMK <--
            // KMK --> v0.7.42: Source Evidence Redesign (ALTER TABLE, no new tables)
            60 to emptyList(),
            61 to emptyList(),
            // KMK <--
            // KMK --> v0.7.48: Source Evaluation tag enrichment + scoring fix (ALTER TABLE, no new table)
            62 to emptyList(),
            // KMK <--
            // KMK --> v0.8.0: Rated Manga bulk selection + group actions
            63 to listOf("manga_cross_source_group_primary"),
            // KMK <--
        )

        // table name → columns added by the ALTER TABLE migration for that migration number
        private val ALTER_MIGRATION_TABLE: Map<Int, String> = mapOf(
            52 to "source_evaluation",
            56 to "ocr_indexed_page",
            // KMK --> v0.7.40
            59 to "recommendation_discovery_progress",
            // KMK <--
            // KMK --> v0.7.42
            60 to "source_evaluation",
            61 to "source_recommendation_fit",
            // KMK <--
            // KMK --> v0.7.48
            62 to "source_evaluation",
            // KMK <--
        )
        private val ALTER_MIGRATION_COLUMNS: Map<Int, List<String>> = mapOf(
            52 to listOf("extension_version_name", "extension_version_code", "extension_apk_name"),
            56 to listOf("recognized_text_length", "recognized_word_count", "ocr_status"),
            // KMK --> v0.7.40
            59 to listOf("attempt_count", "next_retry_at", "failure_kind"),
            // KMK <--
            // KMK --> v0.7.42
            60 to listOf("catalogue_metadata_confidence"),
            61 to listOf("evaluation_version", "expires_at"),
            // KMK <--
            // KMK --> v0.7.48
            62 to listOf(
                "detail_enrichment_attempt_count", "detail_enrichment_success_count",
                "metadata_candidate_count", "positive_candidate_count", "negative_candidate_count",
                "explicit_preferred_group_hit_count", "learned_positive_group_hit_count",
                "blocked_candidate_count", "adult_signal_candidate_count",
            ),
            // KMK <--
        )

        // KMK --> v0.7.38
        val RECOMMENDATION_CANDIDATE_MEMORY_COLUMNS = listOf(
            "source_id", "url", "manga_id", "title", "thumbnail_url", "normalized_title",
            "last_score", "matched_groups_json", "result_reasons_json",
            "query_signature", "query_tags_json", "query_strategy",
            "page", "discovered_at", "last_scored_at", "last_seen_at",
            "profile_fingerprint", "filtered_reason",
        )
        // KMK <--
        // KMK --> v0.7.39
        val RECOMMENDATION_DISCOVERY_PROGRESS_COLUMNS = listOf(
            "source_id", "query_signature", "query_tags_json", "query_strategy",
            "page", "evaluated_at", "raw_count", "localized_count",
            "scored_count", "visible_count", "filtered_count",
            "status", "error_message", "profile_fingerprint",
        )
        // KMK <--

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
        sql.replace(Regex("\\s+AS\\s+(Boolean|JsonObject)"), "")
            .lines()
            .filterNot { it.trim().startsWith("--") || it.trim().startsWith("import ") }
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
    fun `all KMK migration files 47-63 are present on classpath`() {
        for (n in KMK_MIGRATION_RANGE) {
            val resource = javaClass.classLoader?.getResourceAsStream("$n.sqm")
            assertNotNull(resource) { "Migration $n.sqm not found on test classpath" }
            resource?.close()
        }
    }

    @Test
    fun `KMK migration range 47-63 is exactly 17 files`() {
        assertEquals(17, KMK_MIGRATION_RANGE.count())
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
                .filterNot { it.trim().startsWith("--") || it.trim().startsWith("import ") }
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
    fun `all KMK migrations 47-63 apply cleanly to empty database in sequence`() {
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
    fun `migration 52 adds version columns to source_evaluation`() {
        val driver = openDriver()
        for (n in 47..52) driver.executeMigration(n)
        val table = ALTER_MIGRATION_TABLE[52]!!
        val expectedCols = ALTER_MIGRATION_COLUMNS[52]!!
        val actual = driver.columnNames(table)
        for (col in expectedCols) {
            assertTrue(actual.contains(col)) {
                "Migration 52: column '$col' missing from '$table'. Found: $actual"
            }
        }
    }

    @Test
    fun `migration 56 adds status columns to ocr_indexed_page`() {
        val driver = openDriver()
        for (n in 47..56) driver.executeMigration(n)
        val table = ALTER_MIGRATION_TABLE[56]!!
        val expectedCols = ALTER_MIGRATION_COLUMNS[56]!!
        val actual = driver.columnNames(table)
        for (col in expectedCols) {
            assertTrue(actual.contains(col)) {
                "Migration 56: column '$col' missing from '$table'. Found: $actual"
            }
        }
    }

    // KMK --> v0.7.38: migration 57 — recommendation_candidate_memory
    @Test
    fun `migration 57 adds recommendation_candidate_memory with all required columns`() {
        val driver = openDriver()
        for (n in 47..57) driver.executeMigration(n)
        val tables = driver.tableNames()
        assertTrue(tables.contains("recommendation_candidate_memory")) {
            "recommendation_candidate_memory table not found after migration 57. Tables: $tables"
        }
        val actual = driver.columnNames("recommendation_candidate_memory")
        for (col in RECOMMENDATION_CANDIDATE_MEMORY_COLUMNS) {
            assertTrue(actual.contains(col)) {
                "Migration 57: column '$col' missing from recommendation_candidate_memory. Found: $actual"
            }
        }
    }
    // KMK <--

    // KMK --> v0.7.39: migration 58 — recommendation_discovery_progress
    @Test
    fun `migration 58 adds recommendation_discovery_progress with all required columns`() {
        val driver = openDriver()
        for (n in 47..58) driver.executeMigration(n)
        val tables = driver.tableNames()
        assertTrue(tables.contains("recommendation_discovery_progress")) {
            "recommendation_discovery_progress table not found after migration 58. Tables: $tables"
        }
        val actual = driver.columnNames("recommendation_discovery_progress")
        for (col in RECOMMENDATION_DISCOVERY_PROGRESS_COLUMNS) {
            assertTrue(actual.contains(col)) {
                "Migration 58: column '$col' missing from recommendation_discovery_progress. Found: $actual"
            }
        }
    }
    // KMK <--

    // KMK --> v0.7.40: migration 59 — retry metadata columns on recommendation_discovery_progress
    @Test
    fun `migration 59 adds retry metadata columns to recommendation_discovery_progress`() {
        val driver = openDriver()
        for (n in 47..59) driver.executeMigration(n)
        val table = ALTER_MIGRATION_TABLE[59]!!
        val expectedCols = ALTER_MIGRATION_COLUMNS[59]!!
        val actual = driver.columnNames(table)
        for (col in expectedCols) {
            assertTrue(actual.contains(col)) {
                "Migration 59: column '$col' missing from '$table'. Found: $actual"
            }
        }
    }
    // KMK <--

    // KMK --> v0.7.42: migration 60 — catalogue metadata confidence on source_evaluation
    @Test
    fun `migration 60 adds catalogue_metadata_confidence to source_evaluation`() {
        val driver = openDriver()
        for (n in 47..60) driver.executeMigration(n)
        val table = ALTER_MIGRATION_TABLE[60]!!
        val expectedCols = ALTER_MIGRATION_COLUMNS[60]!!
        val actual = driver.columnNames(table)
        for (col in expectedCols) {
            assertTrue(actual.contains(col)) {
                "Migration 60: column '$col' missing from '$table'. Found: $actual"
            }
        }
    }
    // KMK <--

    // KMK --> v0.7.42: migration 61 — version/expiry columns on source_recommendation_fit
    @Test
    fun `migration 61 adds evaluation_version and expires_at to source_recommendation_fit`() {
        val driver = openDriver()
        for (n in 47..61) driver.executeMigration(n)
        val table = ALTER_MIGRATION_TABLE[61]!!
        val expectedCols = ALTER_MIGRATION_COLUMNS[61]!!
        val actual = driver.columnNames(table)
        for (col in expectedCols) {
            assertTrue(actual.contains(col)) {
                "Migration 61: column '$col' missing from '$table'. Found: $actual"
            }
        }
    }
    // KMK <--

    // KMK --> v0.7.48: migration 62 — detail-enrichment + split evidence counters on source_evaluation
    @Test
    fun `migration 62 adds detail-enrichment and evidence counter columns to source_evaluation`() {
        val driver = openDriver()
        for (n in 47..62) driver.executeMigration(n)
        val table = ALTER_MIGRATION_TABLE[62]!!
        val expectedCols = ALTER_MIGRATION_COLUMNS[62]!!
        val actual = driver.columnNames(table)
        for (col in expectedCols) {
            assertTrue(actual.contains(col)) {
                "Migration 62: column '$col' missing from '$table'. Found: $actual"
            }
        }
    }

    @Test
    fun `migration 62 defaults existing source_evaluation rows to zero evidence counts`() {
        val driver = openDriver()
        for (n in 47..48) driver.executeMigration(n)
        // Insert a pre-migration-62 row using only columns that existed at migration 48.
        driver.execute(
            null,
            """
            INSERT INTO source_evaluation(
                evaluation_key, extension_pkg_name, signature_hash, extension_name, source_name, lang,
                source_count, is_nsfw, evaluation_version, evaluated_at, sample_count, popular_count,
                latest_count, search_count, search_success_count, liked_title_match_count,
                preferred_tag_match_count, blocked_tag_match_count, explicit_signal_count,
                ecchi_signal_count, error_count, quality_score, recommendation_fit_score,
                search_reliability_score, explicit_score, ecchi_score, verdict
            ) VALUES (
                'legacy-key', 'eu.kanade.legacy', 'sig', 'Legacy Ext', 'Legacy Source', 'en',
                1, 0, 1, 0, 5, 5, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0.5, 0.5, 0.0, 0.0, 0.0, 'weak'
            )
            """.trimIndent(),
            0,
        )
        for (n in 49..62) driver.executeMigration(n)

        var attemptCount = -1L
        driver.executeQuery(
            identifier = null,
            sql = "SELECT detail_enrichment_attempt_count, positive_candidate_count, adult_signal_candidate_count " +
                "FROM source_evaluation WHERE evaluation_key = 'legacy-key'",
            mapper = { cursor ->
                assertTrue(cursor.next().value) { "Legacy row missing after migration 62" }
                attemptCount = cursor.getLong(0) ?: -1L
                assertEquals(0L, cursor.getLong(1)) { "positive_candidate_count should default to 0" }
                assertEquals(0L, cursor.getLong(2)) { "adult_signal_candidate_count should default to 0" }
                QueryResult.Value(Unit)
            },
            parameters = 0,
        )
        assertEquals(0L, attemptCount) { "detail_enrichment_attempt_count should default to 0" }
    }
    // KMK <--

    // KMK --> v0.8.0: migration 63 — manga_cross_source_group_primary
    @Test
    fun `migration 63 adds manga_cross_source_group_primary table`() {
        val driver = openDriver()
        for (n in 47..63) driver.executeMigration(n)
        val tables = driver.tableNames()
        assertTrue(tables.contains("manga_cross_source_group_primary")) {
            "manga_cross_source_group_primary table not found after migration 63. Tables: $tables"
        }
        val cols = driver.columnNames("manga_cross_source_group_primary")
        for (col in listOf("group_id", "source", "url", "updated_at")) {
            assertTrue(cols.contains(col)) {
                "migration 63: column '$col' missing from manga_cross_source_group_primary. Found: $cols"
            }
        }
    }

    @Test
    fun `migration 63 does not lose existing manga_cross_source_link rows`() {
        val driver = openDriver()
        for (n in 47..51) driver.executeMigration(n) // 51 creates manga_cross_source_link
        driver.execute(
            null,
            """
            INSERT INTO manga_cross_source_link(source, url, group_id, title, created_at, updated_at)
            VALUES (1, '/a', 'group-1', 'A', 0, 0)
            """.trimIndent(),
            0,
        )
        for (n in 52..63) driver.executeMigration(n)

        var rowCount = -1L
        driver.executeQuery(
            identifier = null,
            sql = "SELECT COUNT(*) FROM manga_cross_source_link WHERE group_id = 'group-1'",
            mapper = { cursor ->
                assertTrue(cursor.next().value)
                rowCount = cursor.getLong(0) ?: -1L
                QueryResult.Value(Unit)
            },
            parameters = 0,
        )
        assertEquals(1L, rowCount) { "Existing manga_cross_source_link row was lost after migration 63" }
    }
    // KMK <--

    @Test
    fun `IF NOT EXISTS makes CREATE TABLE idempotent - re-running CREATE TABLE does not error`() {
        val driver = openDriver()
        for (n in KMK_MIGRATION_RANGE) driver.executeMigration(n)

        // Re-run only CREATE TABLE statements (not indexes or ALTER TABLE — migrations run exactly once
        // in production, but IF NOT EXISTS on CREATE TABLE guards against the historical case where
        // application code created a table before the migration file existed, e.g. migration 48).
        for (n in KMK_MIGRATION_RANGE) {
            val content = javaClass.classLoader?.getResourceAsStream("$n.sqm")
                ?.bufferedReader()?.readText()
                ?: error("Migration $n.sqm not found")
            content.lines()
                .filterNot { it.trim().startsWith("--") || it.trim().startsWith("import ") }
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
    fun `manga_source_quality_signal has all required columns after migration 54`() {
        val driver = openDriver()
        for (n in 47..54) driver.executeMigration(n)
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
            // KMK --> v0.7.38
            "recommendation_candidate_memory",
            // KMK <--
        )
        for (table in expected) {
            assertTrue(tables.contains(table)) { "Expected KMK table '$table' not found after full migration" }
        }
    }
}
// KMK <--


package eu.kanade.tachiyomi.data.database

// KMK -->
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists

/**
 * Verifies the append-only migration bridge (migration 63) that ports the two remaining
 * official Komikku 1.14.0 schema changes into the KMK schema (KMK occupies 45-62; this is
 * appended after that ceiling and never renumbers or overwrites KMK's own migrations):
 *
 * - extension_repos -> extension_store conversion
 * - mangas.memo / chapters.memo columns
 *
 * The official 1.14.0 performance-index half of upstream's own migration 45 is intentionally
 * NOT re-applied here: it was verified byte-identical to KMK's existing 45.sqm during the
 * reconciliation audit, so applying it again would be redundant, not additive.
 *
 * A note on the baseline: numbered .sqm migration files here (as in every SQLDelight app) are
 * deltas relative to a version-0 schema that predates the migration history and is not itself
 * recoverable from any single .sqm file -- migration 1.sqm itself already assumes `mangas`
 * exists. [KmkMigrationTest] avoids this by only exercising migrations 46-62, none of which
 * touch the pre-existing `mangas`/`chapters`/`extension_repos` tables directly. Migration 63
 * does (ALTER TABLE mangas/chapters, and a real extension_repos -> extension_store conversion),
 * so these tests seed a realistic pre-migration-63 baseline by hand -- the `mangas`/`chapters`
 * schema exactly as of migration 62 (i.e. the current .sq definition minus the `memo` column
 * migration 63 adds) plus the official `extension_repos` table -- rather than attempting an
 * unsound "replay from an empty database" that no real installation, and no other test in this
 * suite, actually performs.
 *
 * Run with: ./gradlew :app:testDebugUnitTest --tests "*.Kmk114ReconciliationMigrationTest"
 */
class Kmk114ReconciliationMigrationTest {

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
            // Strip SQL comments and SQLDelight-only "import ...;" codegen directives (used to
            // resolve `AS Boolean` / `AS JsonObject` column type mappings at compile time) --
            // neither is valid raw SQLite syntax, and this test executes .sqm files directly
            // against a JDBC driver, bypassing SQLDelight's own codegen/execution layer.
            .filterNot { it.trim().startsWith("--") || it.trim().startsWith("import ") }
            .joinToString("\n")
            // Strip the two SQLDelight Kotlin-type-mapping annotations migration 63 introduces
            // to this test suite (KMK's own 46-62 migrations never used them). This is a
            // narrow, known-content substitution, not a general SQLDelight-dialect translator --
            // it must not touch a real SQL "AS" alias (e.g. in a SELECT list), so it only
            // matches immediately after the two raw SQLite storage-class keywords migration 63
            // actually uses this annotation on.
            .replace(Regex("""\bINTEGER AS Boolean\b"""), "INTEGER")
            .replace(Regex("""\bBLOB AS JsonObject\b"""), "BLOB")
            .split(";")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { stmt -> execute(null, stmt, 0) }
    }

    /**
     * Hand-seeded baseline: `mangas`/`chapters` exactly as of migration 62 (current .sq shape
     * minus the `memo` column migration 63 adds), plus the official `extension_repos` table.
     * This is the real starting point migration 63 is written against.
     */
    private fun JdbcSqliteDriver.seedPreMigration63Baseline() {
        executeSqlScript(
            """
            CREATE TABLE mangas(
                _id INTEGER NOT NULL PRIMARY KEY,
                source INTEGER NOT NULL,
                url TEXT NOT NULL,
                artist TEXT,
                author TEXT,
                description TEXT,
                genre TEXT,
                title TEXT NOT NULL,
                status INTEGER NOT NULL,
                thumbnail_url TEXT,
                favorite INTEGER NOT NULL,
                last_update INTEGER,
                next_update INTEGER,
                initialized INTEGER NOT NULL,
                viewer INTEGER NOT NULL,
                chapter_flags INTEGER NOT NULL,
                cover_last_modified INTEGER NOT NULL,
                date_added INTEGER NOT NULL,
                filtered_scanlators TEXT,
                update_strategy INTEGER NOT NULL DEFAULT 0,
                calculate_interval INTEGER DEFAULT 0 NOT NULL,
                last_modified_at INTEGER NOT NULL DEFAULT 0,
                favorite_modified_at INTEGER,
                version INTEGER NOT NULL DEFAULT 0,
                is_syncing INTEGER NOT NULL DEFAULT 0,
                notes TEXT NOT NULL DEFAULT ""
            );
            CREATE TABLE chapters(
                _id INTEGER NOT NULL PRIMARY KEY,
                manga_id INTEGER NOT NULL,
                url TEXT NOT NULL,
                name TEXT NOT NULL,
                scanlator TEXT,
                read INTEGER NOT NULL,
                bookmark INTEGER NOT NULL,
                last_page_read INTEGER NOT NULL,
                chapter_number REAL NOT NULL,
                source_order INTEGER NOT NULL,
                date_fetch INTEGER NOT NULL,
                date_upload INTEGER NOT NULL,
                last_modified_at INTEGER NOT NULL DEFAULT 0,
                version INTEGER NOT NULL DEFAULT 0,
                is_syncing INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(manga_id) REFERENCES mangas (_id)
                ON DELETE CASCADE
            );
            CREATE TABLE extension_repos (
                base_url TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                short_name TEXT,
                website TEXT NOT NULL,
                signing_key_fingerprint TEXT UNIQUE NOT NULL
            );
            """.trimIndent(),
        )
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

    private fun JdbcSqliteDriver.longScalar(sql: String): Long {
        var result = -1L
        executeQuery(
            identifier = null,
            sql = sql,
            mapper = { cursor ->
                assertTrue(cursor.next().value) { "Query returned no rows: $sql" }
                result = cursor.getLong(0) ?: -1L
                QueryResult.Value(Unit)
            },
            parameters = 0,
        )
        return result
    }

    private fun JdbcSqliteDriver.stringScalar(sql: String): String? {
        var result: String? = null
        executeQuery(
            identifier = null,
            sql = sql,
            mapper = { cursor ->
                assertTrue(cursor.next().value) { "Query returned no rows: $sql" }
                result = cursor.getString(0)
                QueryResult.Value(Unit)
            },
            parameters = 0,
        )
        return result
    }

    // ---- Fixture 1: fresh install equivalent -- baseline + KMK's own 46-62 history + 63 ----

    @Test
    fun `fresh KMK install schema (baseline plus 46-62 plus 63) has extension_store and memo columns`() {
        val driver = openDriver()
        driver.seedPreMigration63Baseline()
        for (n in 46..63) driver.executeMigration(n)

        val tables = driver.tableNames()
        assertTrue(tables.contains("extension_store")) { "extension_store missing after fresh install" }
        assertFalse(tables.contains("extension_repos")) { "extension_repos should not exist after fresh install" }

        val mangaCols = driver.columnNames("mangas")
        val chapterCols = driver.columnNames("chapters")
        assertTrue(mangaCols.contains("memo")) { "mangas.memo missing after fresh install" }
        assertTrue(chapterCols.contains("memo")) { "chapters.memo missing after fresh install" }
    }

    @Test
    fun `extension_store has the official column set after migration 63`() {
        val driver = openDriver()
        driver.seedPreMigration63Baseline()
        driver.executeMigration(63)
        val cols = driver.columnNames("extension_store")
        for (col in listOf(
            "index_url",
            "name",
            "badge_label",
            "signing_key",
            "contact_website",
            "contact_discord",
            "is_legacy",
            "extension_list_url",
        )) {
            assertTrue(cols.contains(col)) { "extension_store missing column '$col'. Found: $cols" }
        }
    }

    // ---- Fixture 2: a database at the official 1.13.6 baseline (extension_repos populated,
    // no KMK tables at all -- migration 63 must not depend on any KMK-specific state) ----

    @Test
    fun `official 1_13_6-shaped database (no KMK tables) upgrades cleanly through migration 63`() {
        val driver = openDriver()
        driver.seedPreMigration63Baseline()

        val baselineTables = driver.tableNames()
        assertTrue(baselineTables.contains("extension_repos")) { "extension_repos should exist in the seeded baseline" }
        assertFalse(baselineTables.contains("extension_store")) { "extension_store should not exist yet" }
        assertFalse(baselineTables.contains("manga_taste")) { "No KMK tables should exist in this fixture" }

        // Seed a real official-shaped extension_repos row before continuing the upgrade.
        driver.execute(
            null,
            """
            INSERT INTO extension_repos(base_url, name, short_name, website, signing_key_fingerprint)
            VALUES ('https://raw.githubusercontent.com/kmk/extensions-repo', 'KMK Extensions', 'KMK', 'https://kmk.example.com', 'FINGERPRINT-1')
            """.trimIndent(),
            0,
        )

        driver.executeMigration(63)

        val upgradedTables = driver.tableNames()
        assertFalse(upgradedTables.contains("extension_repos")) { "extension_repos should be dropped after migration 63" }
        assertTrue(upgradedTables.contains("extension_store")) { "extension_store should exist after migration 63" }

        // The seeded repo must have survived the conversion.
        val storeCount = driver.longScalar("SELECT COUNT(*) FROM extension_store")
        assertEquals(1L, storeCount) { "extension_repos row was not converted into extension_store" }
        val convertedName = driver.stringScalar("SELECT name FROM extension_store LIMIT 1")
        assertEquals("KMK Extensions", convertedName) { "Converted extension_store row has wrong name" }
        val convertedIndexUrl = driver.stringScalar("SELECT index_url FROM extension_store LIMIT 1")
        assertEquals(
            "https://raw.githubusercontent.com/kmk/extensions-repo/repo.json",
            convertedIndexUrl,
        ) { "Converted extension_store row has wrong index_url" }
    }

    @Test
    fun `database with no configured repositories upgrades through migration 63 with an empty extension_store`() {
        val driver = openDriver()
        driver.seedPreMigration63Baseline()
        driver.executeMigration(63)
        assertEquals(0L, driver.longScalar("SELECT COUNT(*) FROM extension_store"))
        assertFalse(driver.tableNames().contains("extension_repos"))
    }

    // ---- Fixture 3: KMK-populated database (baseline + 46-62 + real rating/group/OCR/
    // discovery/source-evaluation/repo data) upgrading through 63 without data loss ----

    @Test
    fun `KMK-populated database preserves ratings, groups, OCR, discovery, and source-evaluation data through migration 63`() {
        val driver = openDriver()
        driver.seedPreMigration63Baseline()
        for (n in 46..62) driver.executeMigration(n)

        // Seed one row of real user data per KMK feature area, plus an extension_repos row,
        // matching what an actual upgrading KMK v0.8.x installation would have.
        driver.execute(
            null,
            """
            INSERT INTO extension_repos(base_url, name, short_name, website, signing_key_fingerprint)
            VALUES ('https://example.com/repo', 'Example Repo', 'Example', 'https://example.com', 'FP-EXAMPLE')
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            INSERT INTO manga_taste(manga_id, source, url, title, rating, created_at, updated_at)
            VALUES (1, 1, '/manga/1', 'Test Manga', 5, 0, 0)
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            INSERT INTO manga_cross_source_link(source, url, group_id, title, created_at, updated_at)
            VALUES (1, '/a', 'group-1', 'A', 0, 0)
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            INSERT INTO manga_cross_source_group_primary(group_id, source, url, updated_at)
            VALUES ('group-1', 1, '/a', 0)
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            INSERT INTO ocr_indexed_page(
                manga_id, source_id, manga_url, manga_title, chapter_id, chapter_url, chapter_name,
                page_index, page_identity, engine_key, engine_version, raw_text, normalized_text,
                indexed_at, recognized_text_length, recognized_word_count, ocr_status
            ) VALUES (
                1, 1, '/manga/1', 'Test Manga', 1, '/ch/1', 'Chapter 1',
                0, 'identity-1', 'mlkit', '1.0', 'hello world', 'hello world',
                0, 11, 2, 'success'
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            INSERT INTO recommendation_discovery_progress(
                source_id, query_signature, query_tags_json, query_strategy,
                page, evaluated_at, raw_count, localized_count,
                scored_count, visible_count, filtered_count,
                status, error_message, profile_fingerprint,
                attempt_count, next_retry_at, failure_kind
            ) VALUES (
                1, 'sig-1', '[]', 'default',
                0, 0, 10, 10,
                5, 5, 5,
                'completed', NULL, 'fp-1',
                0, NULL, NULL
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            INSERT INTO source_evaluation(
                evaluation_key, extension_pkg_name, signature_hash, extension_name, source_name, lang,
                source_count, is_nsfw, evaluation_version, evaluated_at, sample_count, popular_count,
                latest_count, search_count, search_success_count, liked_title_match_count,
                preferred_tag_match_count, blocked_tag_match_count, explicit_signal_count,
                ecchi_signal_count, error_count, quality_score, recommendation_fit_score,
                search_reliability_score, explicit_score, ecchi_score, verdict,
                detail_enrichment_attempt_count, detail_enrichment_success_count,
                metadata_candidate_count, positive_candidate_count, negative_candidate_count,
                explicit_preferred_group_hit_count, learned_positive_group_hit_count,
                blocked_candidate_count, adult_signal_candidate_count
            ) VALUES (
                'kmk-key', 'eu.kanade.kmk', 'sig', 'KMK Ext', 'KMK Source', 'en',
                1, 0, 1, 0, 5, 5, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0.5, 0.5, 0.0, 0.0, 0.0, 'weak',
                0, 0, 0, 0, 0, 0, 0, 0, 0
            )
            """.trimIndent(),
            0,
        )

        // Now continue the upgrade through the new migration bridge.
        driver.executeMigration(63)

        // Every KMK table's data must survive untouched.
        assertEquals(1L, driver.longScalar("SELECT COUNT(*) FROM manga_taste WHERE manga_id = 1")) {
            "manga_taste row lost during migration 63"
        }
        assertEquals(1L, driver.longScalar("SELECT COUNT(*) FROM manga_cross_source_link WHERE group_id = 'group-1'")) {
            "manga_cross_source_link row lost during migration 63"
        }
        assertEquals(1L, driver.longScalar("SELECT COUNT(*) FROM manga_cross_source_group_primary WHERE group_id = 'group-1'")) {
            "manga_cross_source_group_primary row lost during migration 63"
        }
        assertEquals(1L, driver.longScalar("SELECT COUNT(*) FROM ocr_indexed_page WHERE manga_id = 1")) {
            "ocr_indexed_page row lost during migration 63"
        }
        assertEquals(1L, driver.longScalar("SELECT COUNT(*) FROM recommendation_discovery_progress WHERE source_id = 1")) {
            "recommendation_discovery_progress row lost during migration 63"
        }
        assertEquals(1L, driver.longScalar("SELECT COUNT(*) FROM source_evaluation WHERE evaluation_key = 'kmk-key'")) {
            "source_evaluation row lost during migration 63"
        }

        // The extension_repos row must have been converted, not dropped silently.
        assertEquals(1L, driver.longScalar("SELECT COUNT(*) FROM extension_store")) {
            "extension_repos row was not converted for a KMK-populated database"
        }
        assertFalse(driver.tableNames().contains("extension_repos")) {
            "extension_repos should be dropped after migration 63"
        }

        assertTrue(driver.columnNames("mangas").contains("memo"))
        assertTrue(driver.columnNames("chapters").contains("memo"))
    }

    // ---- Idempotency: CREATE TABLE re-run safety (ALTER TABLE ADD COLUMN is guaranteed
    // single-execution by SQLDelight's own migration-version tracking, same as every other
    // migration in this history -- this test matches KmkMigrationTest's own established pattern
    // of only re-running the idempotency-guarded CREATE TABLE statements). ----

    @Test
    fun `migration 63 CREATE TABLE is idempotent - re-running it does not error`() {
        val driver = openDriver()
        driver.seedPreMigration63Baseline()
        driver.executeMigration(63)

        val content = javaClass.classLoader?.getResourceAsStream("63.sqm")
            ?.bufferedReader()?.readText()
            ?: error("Migration 63.sqm not found")
        content.replace(Regex("\\s+AS\\s+(Boolean|JsonObject)"), "").lines()
            .filterNot { it.trim().startsWith("--") || it.trim().startsWith("import ") }
            .joinToString("\n")
            .replace(Regex("""\bINTEGER AS Boolean\b"""), "INTEGER")
            .replace(Regex("""\bBLOB AS JsonObject\b"""), "BLOB")
            .split(";")
            .map { it.trim() }
            .filter { it.uppercase().startsWith("CREATE TABLE") }
            .forEach { stmt -> driver.execute(null, stmt, 0) }

        // No exception = IF NOT EXISTS is applied correctly on migration 63's CREATE TABLE.
        assertTrue(driver.tableNames().contains("extension_store"))
    }

    @Test
    fun `reopening a migrated database preserves data (simulated close and reopen on the same file)`() {
        val path = createTempFile(suffix = ".db")
        try {
            val url = "jdbc:sqlite:${path.toAbsolutePath()}"
            val first = JdbcSqliteDriver(url)
            first.seedPreMigration63Baseline()
            for (n in 46..63) first.executeMigration(n)
            first.execute(
                null,
                """
                INSERT INTO manga_taste(manga_id, source, url, title, rating, created_at, updated_at)
                VALUES (1, 1, '/manga/1', 'Persisted Manga', 5, 0, 0)
                """.trimIndent(),
                0,
            )
            first.close()

            // Reopen the same physical database -- migrations must not be re-applied (schema
            // already at 63) and the previously written row must still be there.
            val reopened = JdbcSqliteDriver(url)
            assertEquals(
                1L,
                reopened.longScalar("SELECT COUNT(*) FROM manga_taste WHERE title = 'Persisted Manga'"),
            ) { "Data did not survive close+reopen of the migrated database" }
            assertTrue(reopened.tableNames().contains("extension_store"))
            assertFalse(reopened.tableNames().contains("extension_repos"))
            reopened.close()
        } finally {
            path.deleteIfExists()
        }
    }
}
// KMK <--

package eu.kanade.tachiyomi.data.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verifies that a real version-68 local-tracker graph survives the local-tracker migration
 * chain through the current version. The fixture is intentionally small and disposable; the
 * installed-artifact round trip remains a separate acceptance gate.
 */
class KmkLocalTrackerMigration68To74Test {

    private fun openDriver() = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

    private fun JdbcSqliteDriver.executeMigration(number: Int) {
        val resource = javaClass.classLoader?.getResourceAsStream("$number.sqm")
            ?: error("Migration $number.sqm not found on test classpath")
        resource.bufferedReader().use { executeSqlScript(it.readText()) }
    }

    private fun JdbcSqliteDriver.executeSqlScript(sql: String) {
        sql.lines()
            .filterNot { it.trim().startsWith("--") }
            .joinToString("\n")
            .split(";")
            .map(String::trim)
            .filter(String::isNotBlank)
            .forEach { execute(null, it, 0) }
    }

    private fun JdbcSqliteDriver.executeFixture() {
        val resource = javaClass.classLoader?.getResourceAsStream("backup/migration/local_tracker_v68.sql")
            ?: error("Version-68 local-tracker fixture not found")
        resource.bufferedReader().use { executeSqlScript(it.readText()) }
    }

    private fun JdbcSqliteDriver.longScalar(sql: String): Long {
        var value = Long.MIN_VALUE
        executeQuery(
            identifier = null,
            sql = sql,
            mapper = { cursor ->
                assertTrue(cursor.next().value) { "Expected scalar result for $sql" }
                value = cursor.getLong(0) ?: Long.MIN_VALUE
                QueryResult.Value(Unit)
            },
            parameters = 0,
        )
        return value
    }

    private fun JdbcSqliteDriver.stringScalar(sql: String): String? {
        var value: String? = null
        executeQuery(
            identifier = null,
            sql = sql,
            mapper = { cursor ->
                assertTrue(cursor.next().value) { "Expected scalar result for $sql" }
                value = cursor.getString(0)
                QueryResult.Value(Unit)
            },
            parameters = 0,
        )
        return value
    }

    @Test
    fun `version 68 local tracker graph survives migration through 74`() {
        val driver = openDriver()
        driver.executeMigration(68)
        driver.executeFixture()

        driver.executeMigration(69)
        driver.executeMigration(70)
        driver.executeMigration(71)
        driver.executeMigration(72)
        driver.executeMigration(73)

        driver.execute(
            null,
            """
            INSERT INTO local_tracked_work_source_progress(
                work_id, source, url, chapter_number, chapter_url, chapter_label,
                progress_at, inherited_from_source, inherited_from_url, updated_at
            ) VALUES (
                'fixture-work-68', 101, '/fixture/manga', 12.0, '/fixture/chapter-12',
                'Chapter 12', 1_500, NULL, NULL, 2_000
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            INSERT INTO local_tracked_work_source_progress(
                work_id, source, url, chapter_number, chapter_url, chapter_label,
                progress_at, inherited_from_source, inherited_from_url, updated_at
            ) VALUES (
                'fixture-work-68', 999, '/orphan', 4.0, '/orphan/chapter-4',
                'Chapter 4', 1_500, NULL, NULL, 2_000
            )
            """.trimIndent(),
            0,
        )

        driver.executeMigration(74)

        assertEquals(1L, driver.longScalar("SELECT COUNT(*) FROM local_tracked_work"))
        assertEquals(1L, driver.longScalar("SELECT COUNT(*) FROM local_tracked_work_source"))
        assertEquals(1L, driver.longScalar("SELECT COUNT(*) FROM local_tracked_work_list"))
        assertEquals(1L, driver.longScalar("SELECT COUNT(*) FROM local_tracked_work_source_progress"))
        assertEquals("Fixture Work", driver.stringScalar("SELECT title FROM local_tracked_work"))
        assertEquals("reading", driver.stringScalar("SELECT normalized_name FROM local_tracked_work_list"))
        assertEquals(
            "/fixture/chapter-12",
            driver.stringScalar("SELECT chapter_url FROM local_tracked_work_source_progress"),
        )
        assertNull(driver.stringScalar("SELECT score FROM local_tracked_work"))
    }
}

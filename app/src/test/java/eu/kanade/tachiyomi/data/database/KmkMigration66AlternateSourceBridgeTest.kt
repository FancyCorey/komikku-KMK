package eu.kanade.tachiyomi.data.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KmkMigration66AlternateSourceBridgeTest {

    private fun driver() = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

    private fun JdbcSqliteDriver.executeMigration() {
        val resource = javaClass.classLoader?.getResourceAsStream("66.sqm") ?: error("Missing 66.sqm")
        resource.bufferedReader().readText().lines()
            .filterNot { it.trimStart().startsWith("--") }
            .joinToString("\n")
            .split(';')
            .map(String::trim)
            .filter(String::isNotBlank)
            .forEach { execute(null, it, 0) }
    }

    private fun JdbcSqliteDriver.columns(table: String): Set<String> {
        val values = mutableSetOf<String>()
        executeQuery(null, "PRAGMA table_info($table)", { cursor ->
            while (cursor.next().value) cursor.getString(1)?.let(values::add)
            QueryResult.Value(Unit)
        }, 0)
        return values
    }

    @Test
    fun `migration 66 is an additive version 1 bridge foundation`() {
        val resource = javaClass.classLoader?.getResourceAsStream("66.sqm")
        assertNotNull(resource)
        val text = requireNotNull(resource).bufferedReader().readText().uppercase()
        assertTrue("DROP " !in text)
        assertTrue("ALTER TABLE" !in text)
    }

    @Test
    fun `migration 66 creates the exact version 1 bridge columns`() {
        val migrated = driver().also { it.executeMigration() }

        assertEquals(
            setOf(
                "primary_source",
                "primary_url",
                "alternate_source",
                "alternate_url",
                "version",
                "offset_milli",
                "offset_state",
                "continuation_primary_chapter_url",
                "automatic_return",
                "review_state",
                "created_at",
                "updated_at",
                "deleted_at",
            ),
            migrated.columns("alternate_source_bridge"),
        )
        assertEquals(
            setOf(
                "primary_source",
                "primary_url",
                "alternate_source",
                "alternate_url",
                "target_id",
                "primary_chapter_url",
                "alternate_chapter_url",
                "relation",
                "state",
                "offset_milli",
                "version",
                "created_at",
                "updated_at",
                "deleted_at",
            ),
            migrated.columns("alternate_source_bridge_mapping"),
        )
    }

    @Test
    fun `database constraints reject self pairs and unsafe automatic return`() {
        val driver = driver().also { it.executeMigration() }
        val valid = "INSERT INTO alternate_source_bridge VALUES (1, '/a', 2, '/b', 1, NULL, NULL, NULL, 0, 'CURRENT', 1, 1, NULL)"
        driver.execute(null, valid, 0)
        assertThrows(Exception::class.java) {
            driver.execute(null, valid.replace("2, '/b'", "1, '/a'"), 0)
        }
        assertThrows(Exception::class.java) {
            driver.execute(null, valid.replace("NULL, 0, 'CURRENT'", "NULL, 1, 'CURRENT'"), 0)
        }
    }
}

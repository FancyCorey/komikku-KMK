package eu.kanade.tachiyomi.data.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.data.Database

class KmkMigration67AlternateSourceBridgeAnchorsTest {

    private fun driver() = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

    private fun JdbcSqliteDriver.executeMigration(number: Int) {
        val resource = javaClass.classLoader?.getResourceAsStream("$number.sqm") ?: error("Missing $number.sqm")
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

    private fun JdbcSqliteDriver.long(sql: String): Long {
        var result = -1L
        executeQuery(null, sql, { cursor ->
            check(cursor.next().value)
            result = requireNotNull(cursor.getLong(0))
            QueryResult.Value(Unit)
        }, 0)
        return result
    }

    @Test
    fun `migration 67 is present in the current schema`() {
        assertNotNull(javaClass.classLoader?.getResourceAsStream("67.sqm"))
        assertTrue(Database.Schema.version >= 68L)
    }

    @Test
    fun `fresh and migrated schemas expose the same bridge columns`() {
        val fresh = driver().also(Database.Schema::create)
        val migrated = driver().also {
            it.executeMigration(66)
            it.executeMigration(67)
        }

        assertEquals(fresh.columns("alternate_source_bridge"), migrated.columns("alternate_source_bridge"))
        assertEquals(fresh.columns("alternate_source_bridge_mapping"), migrated.columns("alternate_source_bridge_mapping"))
    }

    @Test
    fun `migration preserves legacy rows but disables unsafe automatic return`() {
        val driver = driver().also { it.executeMigration(66) }
        driver.execute(
            null,
            "INSERT INTO alternate_source_bridge VALUES (1, '/p', 2, '/a', 1, NULL, NULL, '/next', 1, 'CURRENT', 1, 2, NULL)",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO alternate_source_bridge_mapping VALUES (1, '/p', 2, '/a', '123e4567-e89b-42d3-a456-426614174000', NULL, '/alt', 'PRIMARY_MISSING', 'CONFIRMED', NULL, 1, 1, 2, NULL)",
            0,
        )

        driver.executeMigration(67)

        assertEquals(1L, driver.long("SELECT version FROM alternate_source_bridge"))
        assertEquals(0L, driver.long("SELECT automatic_return FROM alternate_source_bridge"))
        assertEquals(1L, driver.long("SELECT version FROM alternate_source_bridge_mapping"))
        assertEquals(1L, driver.long("SELECT COUNT(*) FROM alternate_source_bridge_mapping"))
    }

    @Test
    fun `version 2 constraints require exact anchors and paired return boundaries`() {
        val driver = driver().also {
            it.executeMigration(66)
            it.executeMigration(67)
        }
        assertThrows(Exception::class.java) {
            driver.execute(
                null,
                "INSERT INTO alternate_source_bridge VALUES (1, '/p', 2, '/a', 2, NULL, NULL, '/next', NULL, 1, 'CURRENT', 1, 2, NULL)",
                0,
            )
        }
        assertThrows(Exception::class.java) {
            driver.execute(
                null,
                "INSERT INTO alternate_source_bridge_mapping VALUES (1, '/p', 2, '/a', '123e4567-e89b-42d3-a456-426614174000', NULL, '/alt', NULL, NULL, 'PRIMARY_MISSING', 'CONFIRMED', NULL, 2, 1, 2, NULL)",
                0,
            )
        }
    }
}

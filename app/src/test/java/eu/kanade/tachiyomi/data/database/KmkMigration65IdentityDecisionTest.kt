package eu.kanade.tachiyomi.data.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.data.Database

class KmkMigration65IdentityDecisionTest {

    private val table = "manga_cross_source_identity_decision"

    private fun driver() = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

    private fun JdbcSqliteDriver.executeMigration(number: Int) {
        val resource = javaClass.classLoader?.getResourceAsStream("$number.sqm") ?: error("Missing $number.sqm")
        resource.bufferedReader().readText()
            .replace(Regex("\\s+AS\\s+(Boolean|JsonObject)"), "")
            .lines()
            .filterNot { it.trimStart().startsWith("--") }
            .joinToString("\n")
            .split(';')
            .map(String::trim)
            .filter(String::isNotBlank)
            .forEach { execute(null, it, 0) }
    }

    private fun JdbcSqliteDriver.scalar(sql: String): Long {
        var value = -1L
        executeQuery(null, sql, { cursor ->
            check(cursor.next().value)
            value = cursor.getLong(0) ?: -1L
            QueryResult.Value(Unit)
        }, 0)
        return value
    }

    @Test
    fun `migration 65 remains present and additive after later schema additions`() {
        val resource = javaClass.classLoader?.getResourceAsStream("65.sqm")
        assertNotNull(resource)
        val text = requireNotNull(resource).bufferedReader().readText().uppercase()
        assertTrue("DROP " !in text)
        assertTrue("ALTER TABLE" !in text)
        assertTrue("CREATE TABLE IF NOT EXISTS" in text)
        assertTrue(Database.Schema.version >= 66L)
    }

    @Test
    fun `upgrade creates identity table without promoting or changing legacy groups`() {
        val driver = driver()
        for (number in 46..62) driver.executeMigration(number)
        driver.execute(null, "INSERT INTO manga_cross_source_link VALUES (1, '/legacy', 'g', 'Legacy', 1, 1)", 0)
        driver.executeMigration(64)
        driver.executeMigration(65)
        assertEquals(1L, driver.scalar("SELECT COUNT(*) FROM manga_cross_source_link"))
        assertEquals(0L, driver.scalar("SELECT COUNT(*) FROM $table"))
    }

    @Test
    fun `fresh and migrated schemas expose the same identity columns`() {
        fun columns(driver: JdbcSqliteDriver): Set<String> {
            val values = mutableSetOf<String>()
            driver.executeQuery(null, "PRAGMA table_info($table)", { cursor ->
                while (cursor.next().value) cursor.getString(1)?.let(values::add)
                QueryResult.Value(Unit)
            }, 0)
            return values
        }
        val fresh = driver().also(Database.Schema::create)
        val migrated = driver().also { it.executeMigration(65) }
        assertEquals(columns(fresh), columns(migrated))
        assertEquals(12, columns(fresh).size)
    }

    @Test
    fun `database constraints reject noncanonical self and unknown-enum rows`() {
        fun insertSql(leftSource: Long, leftUrl: String, rightSource: Long, rightUrl: String, decision: String) =
            "INSERT INTO $table VALUES ($leftSource, '$leftUrl', $rightSource, '$rightUrl', '$decision', 1, 1, " +
                "'USER_CONFIRMATION', 'CURRENT', 1, 1, NULL)"
        val driver = driver().also { it.executeMigration(65) }
        assertThrows(Exception::class.java) { driver.execute(null, insertSql(2, "/b", 1, "/a", "USER_CONFIRMED"), 0) }
        assertThrows(Exception::class.java) { driver.execute(null, insertSql(1, "/a", 1, "/a", "USER_CONFIRMED"), 0) }
        assertThrows(Exception::class.java) { driver.execute(null, insertSql(1, "/a", 2, "/b", "AUTOMATIC"), 0) }
        driver.execute(null, insertSql(1, "/a", 2, "/b", "USER_CONFIRMED"), 0)
        assertEquals(1L, driver.scalar("SELECT COUNT(*) FROM $table"))
    }
}


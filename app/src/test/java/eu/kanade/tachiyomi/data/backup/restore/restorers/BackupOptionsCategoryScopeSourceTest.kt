package eu.kanade.tachiyomi.data.backup.restore.restorers

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

// KMK F2-02 corrective slice A (KFC-V0.8.21-FIX2-CORRECTIVE-RECHECK-AND-RELEASE-PROGRAM) -->
/**
 * Source-guard proof (required work item 4) that
 * [BackupIdentityDecisionDeviceRoundTripTest]'s device backup/restore calls only ever enable
 * `tasteProfile` -- every other [eu.kanade.tachiyomi.data.backup.create.BackupOptions]/
 * [eu.kanade.tachiyomi.data.backup.restore.RestoreOptions] category stays explicitly `false`. Reads
 * the real androidTest source text (this is a structural, not behavioral, guard -- the runtime
 * before/after row-count assertions inside the device test itself are the behavioral complement)
 * so a future edit that widens the category set back toward `BackupOptions()`/`RestoreOptions()`
 * defaults, or flips any of these to `true`, fails this test immediately rather than silently
 * reintroducing the exact hazard this corrective slice closed.
 */
class BackupOptionsCategoryScopeSourceTest {

    private val text: String by lazy {
        stripComments(
            source("app/src/androidTest/java/eu/kanade/tachiyomi/data/backup/restore/restorers/BackupIdentityDecisionDeviceRoundTripTest.kt"),
        )
    }

    @Test
    fun `the device test never constructs a bare default BackupOptions or RestoreOptions`() {
        // Negative lookbehind for a preceding letter so this does not false-positive on the
        // minimalBackupOptions()/minimalRestoreOptions() helper function NAMES, which legitimately
        // contain "BackupOptions()"/"RestoreOptions()" as a trailing substring of their own identifier.
        assertTrue(
            !Regex("""(?<![A-Za-z])BackupOptions\(\s*\)""").containsMatchIn(text),
            "must never call bare BackupOptions() -- every category defaults to true",
        )
        assertTrue(
            !Regex("""(?<![A-Za-z])RestoreOptions\(\s*\)""").containsMatchIn(text),
            "must never call bare RestoreOptions() -- every category defaults to true",
        )
    }

    @Test
    fun `minimalBackupOptions disables every category except tasteProfile`() {
        val body = functionBody("minimalBackupOptions")
        val falseFields = setOf(
            "libraryEntries", "categories", "chapters", "tracking", "history", "readEntries",
            "appSettings", "extensionStores", "sourceSettings", "privateSettings", "customInfo",
            "savedSearchesFeeds", "localTracker",
        )
        falseFields.forEach { field ->
            assertTrue(
                Regex("""$field\s*=\s*false""").containsMatchIn(body),
                "minimalBackupOptions() must set $field = false",
            )
        }
        assertTrue(Regex("""tasteProfile\s*=\s*true""").containsMatchIn(body), "minimalBackupOptions() must set tasteProfile = true")
        assertTrue(!Regex("""tasteProfile\s*=\s*false""").containsMatchIn(body), "minimalBackupOptions() must not disable tasteProfile")
    }

    @Test
    fun `minimalRestoreOptions disables every category except tasteProfile`() {
        val body = functionBody("minimalRestoreOptions")
        val falseFields = setOf(
            "libraryEntries",
            "categories",
            "appSettings",
            "extensionStores",
            "sourceSettings",
            "savedSearchesFeeds",
            "localTracker",
        )
        falseFields.forEach { field ->
            assertTrue(
                Regex("""$field\s*=\s*false""").containsMatchIn(body),
                "minimalRestoreOptions() must set $field = false",
            )
        }
        assertTrue(Regex("""tasteProfile\s*=\s*true""").containsMatchIn(body), "minimalRestoreOptions() must set tasteProfile = true")
        assertTrue(!Regex("""tasteProfile\s*=\s*false""").containsMatchIn(body), "minimalRestoreOptions() must not disable tasteProfile")
    }

    @Test
    fun `the test calls the disposable-environment guard before any repository mutation`() {
        val guardIndex = text.indexOf("DisposableTestEnvironmentGuard.assumeDisposableEnvironment(")
        assertTrue(guardIndex >= 0, "expected a DisposableTestEnvironmentGuard.assumeDisposableEnvironment(...) call")

        val firstMutation = listOf(
            "upsertCrossSourceIdentityDecisions(",
            "backupCreator.backup(",
            "replaceCrossSourceIdentityDecision(",
            "backupRestorer.restore(",
        ).mapNotNull { needle -> text.indexOf(needle).takeIf { it >= 0 } }.minOrNull()

        assertTrue(firstMutation != null, "expected at least one repository mutation call site in the test")
        assertTrue(
            guardIndex < firstMutation!!,
            "the disposable-environment guard must run before any repository mutation, not after",
        )
    }

    @Test
    fun `backup and restore call sites pass the minimal options helpers, not a raw options literal`() {
        assertTrue(text.contains("backupCreator.backup(Uri.fromFile(backupFile), minimalBackupOptions())"))
        assertTrue(text.contains("backupRestorer.restore(Uri.fromFile(backupFile), minimalRestoreOptions())"))
    }

    private fun functionBody(functionName: String): String {
        val start = text.indexOf("private fun $functionName(")
        assertTrue(start >= 0, "$functionName not found")
        val end = text.indexOf("\n    private fun ", start + 1).let { if (it < 0) text.indexOf("\n    @Test", start + 1) else it }
        assertTrue(end > start, "could not bound $functionName's body")
        return text.substring(start, end)
    }

    private fun stripComments(input: String): String {
        val noBlockComments = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL).replace(input, " ")
        return noBlockComments.lineSequence().joinToString(separator = "\n") { line ->
            val idx = line.indexOf("//")
            if (idx >= 0) line.substring(0, idx) else line
        }
    }

    private fun source(relativePath: String): String {
        val direct = Path.of(relativePath)
        val fromParent = Path.of("..").resolve(relativePath)
        val path = listOf(direct, fromParent)
            .map(Path::toAbsolutePath)
            .firstOrNull(Files::isRegularFile)
            ?: error("Unable to resolve source file: $relativePath")
        return String(Files.readAllBytes(path), Charsets.UTF_8)
    }
}
// KMK <--

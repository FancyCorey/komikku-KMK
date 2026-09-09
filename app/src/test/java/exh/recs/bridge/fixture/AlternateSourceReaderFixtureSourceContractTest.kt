package exh.recs.bridge.fixture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class AlternateSourceReaderFixtureSourceContractTest {
    @Test
    fun `build profile is empty by default and configurable only for debug`() {
        val source = File("build.gradle.kts").readText()

        assertTrue(
            Regex(
                """buildConfigField\(\s*"String",\s*"ALTERNATE_SOURCE_READER_FIXTURE_PROFILE",\s*buildConfigString\(""\),?\s*\)""",
            ).containsMatchIn(source),
        )
        assertEquals(1, source.lineSequence().count { "kmk.alternateSourceReaderFixture.profile" in it })
        val debugBlock = source.substringAfter("val debug by getting {").substringBefore("val release by getting {")
        assertTrue("kmk.alternateSourceReaderFixture.profile" in debugBlock)
    }

    @Test
    fun `settings controls require debug developer evaluation and isolated runtime gates`() {
        val source = File(
            "src/main/java/eu/kanade/presentation/more/settings/screen/SettingsAdvancedScreen.kt",
        ).readText()
        val gate = source.substringAfter("val alternateSourceReaderFixtureAvailable =").substringBefore("val alternateSourceReaderFixtureRunning")

        assertTrue("BuildConfig.DEBUG" in gate)
        assertTrue("developerOptionsEnabled" in gate)
        assertTrue("evaluationModeEnabled" in gate)
        assertTrue("installedExtensions" in gate || source.contains("installedExtensionsFlow.collectAsStateWithLifecycle()"))
        assertTrue("alternateSourceReaderFixtureRuntime.controlsAvailable(" in gate)
        assertTrue(source.contains("sourcePreferences.alternateSourceReaderFixtureScenario()"))
        assertTrue(source.contains("}.collectAsState()"))
        assertFalse(source.contains("alternateSourceReaderFixtureRuntime.scenario()"))
        assertFalse(gate.contains("source.name"))
        assertFalse(gate.contains("packageName"))

        val module = File("src/main/java/eu/kanade/tachiyomi/di/AppModule.kt").readText()
        assertEquals(1, module.lineSequence().count { "addSingletonFactory { AlternateSourceReaderFixtureRuntime(app) }" in it })
        assertFalse(source.contains("scope.launch { alternateSourceReaderFixtureRuntime"))
    }

    @Test
    fun `fixture extension owns only exact paired routes and loopback page urls`() {
        val source = File(
            "../fixture-sources-to-try-extension/src/main/java/app/komikku/fixture/sources/FixtureSource.kt",
        ).readText()

        assertTrue(source.contains("/kmk-fixture/f2/origin"))
        assertTrue(source.contains("/kmk-fixture/f2/target"))
        assertTrue(source.contains("http://127.0.0.1:38291"))
        assertTrue(source.contains("class FixtureSource : HttpSource()"))
        assertTrue(source.contains("override val baseUrl = LOOPBACK_BASE"))
        assertTrue(source.contains("if (manga.url != fixture.mangaUrl) return SMangaUpdate(manga, chapters)"))
        assertTrue(source.contains("val chapterNumber = fixture.chapterNumber(chapter.url) ?: return emptyList()"))
        assertFalse(source.contains("0.0.0.0"))
        assertFalse(source.contains("https://"))
    }

    @Test
    fun `fixture manifest leaves extension name as a string-compatible application label`() {
        val manifest = File("../fixture-sources-to-try-extension/src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android:label=\"@string/fixture_source_name\""))
        assertFalse(manifest.contains("android:name=\"tachiyomix.name\""))
        assertFalse(manifest.contains("android:resource=\"@string/fixture_source_name\""))
    }
}

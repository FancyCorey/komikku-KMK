package exh.recs.bestversion.fixture

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class BestVersionPairedFixtureSettingsSourceTest {
    @Test
    fun `fixture command status stays visible while shared fixture work is running`() {
        val source = File(
            "src/main/java/eu/kanade/presentation/more/settings/screen/SettingsAdvancedScreen.kt",
        ).readText()
        val bestVersionAction = source
            .substringAfter("title = stringResource(KMR.strings.best_version_paired_fixture_open)")
            .substringBefore("if (alternateSourceReaderFixtureAvailable)")

        assertTrue(source.contains("commandController.state.collectAsStateWithLifecycle()"))
        assertTrue(source.contains("val anyFixtureRunning = bestVersionFixtureRunning || alternateSourceReaderFixtureRunning"))
        assertTrue(bestVersionAction.contains("subtitle = bestVersionFixtureStatus"))
        assertTrue(bestVersionAction.contains("if (!anyFixtureRunning)"))
        assertFalse(bestVersionAction.contains("enabled = !anyFixtureRunning"))
    }

    @Test
    fun `fixture selectors reject profile mutation without hiding their rows`() {
        val source = File(
            "src/main/java/eu/kanade/presentation/more/settings/screen/SettingsAdvancedScreen.kt",
        ).readText()
        val fixtureSection = source
            .substringAfter("title = stringResource(KMR.strings.alternate_source_reader_fixture_scenario_title)")
            .substringBefore("Preference.PreferenceItem.SwitchPreference(")

        assertTrue(fixtureSection.contains("onValueChanged = { !anyFixtureRunning }"))
        assertFalse(fixtureSection.contains("enabled = !alternateSourceReaderFixtureRunning"))
        assertFalse(fixtureSection.contains("enabled = !anyFixtureRunning"))
    }
}

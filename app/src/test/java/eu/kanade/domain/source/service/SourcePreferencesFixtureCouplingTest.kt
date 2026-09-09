package eu.kanade.domain.source.service

import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseDebugFixtureMode
import exh.recs.evaluation.SourceEvaluationDebugFixtureMode
import exh.util.FakePreferenceStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

// KMK -->
// KMK: direct coverage that SourcePreferences.browseFixtureFailureMode() and
// SourcePreferences.evaluationFixtureFailureMode() are two independent preference keys, so changing
// one can never activate the other's debug fixture. This is the structural fix for the bug where
// BrowseSourceScreenModel.createSourcePagingSource() read the Source-Evaluation-named preference,
// meaning any non-off Source Evaluation debug mode silently also activated the unrelated Browse
// failure fixture.
class SourcePreferencesFixtureCouplingTest {

    private fun sourcePreferences() = SourcePreferences(FakePreferenceStore())

    @Test
    fun `changing evaluationFixtureFailureMode does not change browseFixtureFailureMode`() {
        val prefs = sourcePreferences()
        assertEquals("off", prefs.browseFixtureFailureMode().get())

        prefs.evaluationFixtureFailureMode().set(SourceEvaluationDebugFixtureMode.PER_SOURCE_ERROR.prefValue)

        assertEquals("off", prefs.browseFixtureFailureMode().get())
        assertSame(
            BrowseDebugFixtureMode.OFF,
            BrowseDebugFixtureMode.fromPrefValue(prefs.browseFixtureFailureMode().get()),
        )
    }

    @Test
    fun `changing browseFixtureFailureMode does not change evaluationFixtureFailureMode`() {
        val prefs = sourcePreferences()
        assertEquals("off", prefs.evaluationFixtureFailureMode().get())

        prefs.browseFixtureFailureMode().set(BrowseDebugFixtureMode.SOURCE_UNAVAILABLE.prefValue)

        assertEquals("off", prefs.evaluationFixtureFailureMode().get())
        assertSame(
            SourceEvaluationDebugFixtureMode.OFF,
            SourceEvaluationDebugFixtureMode.fromPrefValue(prefs.evaluationFixtureFailureMode().get()),
        )
    }

    @Test
    fun `BrowseDebugFixtureMode fromPrefValue falls back to OFF for an unrecognized value`() {
        assertSame(BrowseDebugFixtureMode.OFF, BrowseDebugFixtureMode.fromPrefValue("garbage"))
    }
}
// KMK <--

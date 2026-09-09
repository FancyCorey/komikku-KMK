package exh.recs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.TasteProfile

class ForYouDebugFixtureTest {
    @Test
    fun `release gate and unknown preference resolve to off`() {
        assertEquals(ForYouDebugFixtureMode.OFF, ForYouDebugFixture.resolveMode(false, "top_picks"))
        assertEquals(ForYouDebugFixtureMode.OFF, ForYouDebugFixture.resolveMode(true, "unknown"))
    }

    @Test
    fun `fixture supplies generic deterministic sources instead of real inputs`() {
        val sources = ForYouDebugFixture.sources(ForYouDebugFixtureMode.TOP_PICKS, emptyList())

        assertEquals(listOf("Fixture Source A", "Fixture Source B"), sources.map { it.name })
        assertTrue(sources.all { it.lang == "en" })
        assertTrue(ForYouDebugFixture.sourceIds(ForYouDebugFixtureMode.TOP_PICKS).containsAll(sources.map { it.id }))
    }

    @Test
    fun `partial fixture supplies one success and two independent failures`() {
        val sources = ForYouDebugFixture.sources(ForYouDebugFixtureMode.TOP_PICKS_PARTIAL_FAILURE, emptyList())

        assertEquals(
            listOf("Fixture Source A", "Fixture Source B", "Fixture Source C"),
            sources.map { it.name },
        )
        assertEquals(3, ForYouDebugFixture.sourceIds(ForYouDebugFixtureMode.TOP_PICKS_PARTIAL_FAILURE).size)
    }

    @Test
    fun `fixture profile avoids reading the normal profile`() = kotlinx.coroutines.test.runTest {
        var normalProfileRead = false

        val profile = ForYouDebugFixture.profile(ForYouDebugFixtureMode.TOP_PICKS) {
            normalProfileRead = true
            TasteProfile.EMPTY
        }

        assertFalse(normalProfileRead)
        assertFalse(profile.isEmpty())
    }

    @Test
    fun `off fixture preserves normal source and profile inputs`() = kotlinx.coroutines.test.runTest {
        val normalSources = emptyList<eu.kanade.tachiyomi.source.Source>()
        var normalProfileRead = false

        assertEquals(normalSources, ForYouDebugFixture.sources(ForYouDebugFixtureMode.OFF, normalSources))
        assertEquals(
            TasteProfile.EMPTY,
            ForYouDebugFixture.profile(ForYouDebugFixtureMode.OFF) {
                normalProfileRead = true
                TasteProfile.EMPTY
            },
        )
        assertTrue(normalProfileRead)
    }
}

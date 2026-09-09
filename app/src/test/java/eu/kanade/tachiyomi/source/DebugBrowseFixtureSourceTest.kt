package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseDeterministicFixtureException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DebugBrowseFixtureSourceTest {

    @Test
    fun `fixture identity is stable and has no online capability`() {
        val source = DebugBrowseFixtureSource()

        assertEquals(DebugBrowseFixtureSource.ID, source.id)
        assertEquals("Browse debug fixture", source.name)
        assertEquals("en", source.lang)
        assertFalse(source.supportsLatest)
    }

    @Test
    fun `fixture visibility requires debug build and source unavailable mode`() {
        assertTrue(shouldExposeDebugBrowseFixture(isDebugBuild = true, mode = "source_unavailable"))
        assertFalse(shouldExposeDebugBrowseFixture(isDebugBuild = true, mode = "off"))
        assertFalse(shouldExposeDebugBrowseFixture(isDebugBuild = false, mode = "source_unavailable"))
        assertFalse(shouldExposeDebugBrowseFixture(isDebugBuild = true, mode = "unknown"))
    }

    @Test
    fun `fixture is a catalogue source so Browse initializes its pager`() {
        assertTrue(DebugBrowseFixtureSource() is CatalogueSource)
    }

    @Test
    fun `fixture source calls expose the typed deterministic failure`() {
        assertThrows(BrowseDeterministicFixtureException::class.java) {
            runBlocking { DebugBrowseFixtureSource().getPopularManga(1) }
        }
    }

    @Test
    fun `active debug fixture identity resolves to the in-memory source`() {
        assertEquals(
            DebugBrowseFixtureSource.ID,
            resolveDebugBrowseFixture(
                sourceKey = DebugBrowseFixtureSource.ID,
                isDebugBuild = true,
                mode = "source_unavailable",
            )?.id,
        )
    }

    @Test
    fun `inactive or unrelated identities do not resolve to the fixture`() {
        assertNull(resolveDebugBrowseFixture(DebugBrowseFixtureSource.ID, true, "off"))
        assertNull(resolveDebugBrowseFixture(1234L, true, "source_unavailable"))
        assertNull(resolveDebugBrowseFixture(DebugBrowseFixtureSource.ID, false, "source_unavailable"))
    }
}

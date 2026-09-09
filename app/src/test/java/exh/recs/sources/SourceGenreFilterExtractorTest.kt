package exh.recs.sources

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK C3 (HR-2026-08-26-FOCUS-CRITERION-CATALOG-COMPLETENESS, E4.3)
class SourceGenreFilterExtractorTest {

    private class GenreTriState(name: String) : Filter.TriState(name)
    private class GenreCheckBox(name: String) : Filter.CheckBox(name)
    private class GenreGroup(name: String, state: List<Filter<*>>) : Filter.Group<Filter<*>>(name, state)
    private class StatusSelect(values: Array<String>) : Filter.Select<String>("Status", values)
    private class TagAutoComplete(values: List<String>) : Filter.AutoComplete(
        name = "Tags",
        hint = "Search tags",
        values = values,
        state = emptyList(),
    )

    @Test
    fun `extracts TriState and CheckBox children of a Group as genre labels`() {
        val filterList = FilterList(
            GenreGroup("Genres", listOf(GenreTriState("Action"), GenreCheckBox("Comedy"))),
        )

        val result = SourceGenreFilterExtractor.extract(filterList)

        assertEquals(setOf("action", "comedy"), result)
    }

    @Test
    fun `extracts AutoComplete candidate values, not its current state`() {
        val filterList = FilterList(TagAutoComplete(values = listOf("Isekai", "Overpowered")))

        val result = SourceGenreFilterExtractor.extract(filterList)

        assertEquals(setOf("isekai", "overpowered"), result)
    }

    @Test
    fun `never extracts Select values -- sort-status pickers must not pollute the genre catalog`() {
        val filterList = FilterList(StatusSelect(arrayOf("Any", "Ongoing", "Completed")))

        val result = SourceGenreFilterExtractor.extract(filterList)

        assertTrue(result.isEmpty(), "Select filters (commonly Status/Sort) must never contribute labels")
    }

    @Test
    fun `mixes Group, AutoComplete, and ignores Select in the same filter list`() {
        val filterList = FilterList(
            GenreGroup("Genres", listOf(GenreTriState("Horror"))),
            StatusSelect(arrayOf("Any", "Ongoing")),
            TagAutoComplete(values = listOf("Zombies")),
        )

        val result = SourceGenreFilterExtractor.extract(filterList)

        assertEquals(setOf("horror", "zombies"), result)
    }

    @Test
    fun `normalizes labels the same way as every other genre matching path`() {
        val filterList = FilterList(GenreGroup("Genres", listOf(GenreTriState("Sci-Fi"))))

        val result = SourceGenreFilterExtractor.extract(filterList)

        assertEquals(setOf("sci fi"), result)
    }

    @Test
    fun `blank child names never contribute an empty label`() {
        val filterList = FilterList(GenreGroup("Genres", listOf(GenreTriState("   "), GenreTriState("Action"))))

        val result = SourceGenreFilterExtractor.extract(filterList)

        assertEquals(setOf("action"), result)
        assertFalse(result.any { it.isBlank() })
    }

    @Test
    fun `an empty filter list extracts no labels`() {
        val result = SourceGenreFilterExtractor.extract(FilterList())

        assertTrue(result.isEmpty())
    }

    @Test
    fun `duplicate labels across multiple groups collapse to one canonical entry`() {
        val filterList = FilterList(
            GenreGroup("Genres A", listOf(GenreTriState("Action"))),
            GenreGroup("Genres B", listOf(GenreTriState("action"))),
        )

        val result = SourceGenreFilterExtractor.extract(filterList)

        assertEquals(setOf("action"), result)
    }

    @Test
    fun `a pathologically large filter list is bounded, never grows unboundedly`() {
        val manyChildren = (1..10_000).map { GenreTriState("Genre$it") }
        val filterList = FilterList(GenreGroup("Genres", manyChildren))

        val result = SourceGenreFilterExtractor.extract(filterList)

        assertTrue(result.size <= 300, "extraction from a single source's filter list must stay bounded, was ${result.size}")
    }
}

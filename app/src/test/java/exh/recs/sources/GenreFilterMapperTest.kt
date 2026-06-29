package exh.recs.sources

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GenreFilterMapperTest {

    // --- Anonymous concrete subclasses of the abstract Filter types ---

    private fun triState(name: String) = object : Filter.TriState(name) {}
    private fun checkBox(name: String, initial: Boolean = false) = object : Filter.CheckBox(name, initial) {}
    private fun select(name: String, vararg values: String) =
        object : Filter.Select<String>(name, arrayOf(*values)) {}
    private fun <V> group(name: String, children: List<V>) =
        object : Filter.Group<V>(name, children) {}
    private fun autoComplete(name: String, values: List<String>) =
        object : Filter.AutoComplete(name, hint = "", values = values, state = emptyList()) {}

    @Test
    fun `triState in group is set to INCLUDE when genre matches`() {
        val actionFilter = triState("Action")
        val romanceFilter = triState("Romance")
        val filterList = FilterList(group("Genre", listOf(actionFilter, romanceFilter)))

        GenreFilterMapper.buildSearch(filterList, listOf("Action"))

        assertEquals(Filter.TriState.STATE_INCLUDE, actionFilter.state)
        assertEquals(Filter.TriState.STATE_IGNORE, romanceFilter.state)
    }

    @Test
    fun `checkBox in group is set to true when genre matches`() {
        val actionFilter = checkBox("Action")
        val romanceFilter = checkBox("Romance")
        val filterList = FilterList(group("Genre", listOf(actionFilter, romanceFilter)))

        GenreFilterMapper.buildSearch(filterList, listOf("Action"))

        assertTrue(actionFilter.state)
        assertEquals(false, romanceFilter.state)
    }

    @Test
    fun `select is set to matching index when genre matches`() {
        val selectFilter = select("Category", "Any", "Action", "Romance")
        val filterList = FilterList(selectFilter)

        GenreFilterMapper.buildSearch(filterList, listOf("Action"))

        assertEquals(1, selectFilter.state)
    }

    @Test
    fun `autoComplete value is added to state when genre matches`() {
        val acFilter = autoComplete("Tags", listOf("Action", "Romance", "Fantasy"))
        val filterList = FilterList(acFilter)

        GenreFilterMapper.buildSearch(filterList, listOf("Action"))

        assertTrue("Action" in acFilter.state)
    }

    @Test
    fun `unmatched genre is added to text query`() {
        val filterList = FilterList()

        val params = GenreFilterMapper.buildSearch(filterList, listOf("Isekai"))

        assertEquals("Isekai", params.textQuery)
    }

    @Test
    fun `mixed genres -- some matched via filter, others fall back to text`() {
        val actionFilter = triState("Action")
        val filterList = FilterList(group("Genre", listOf(actionFilter)))

        val params = GenreFilterMapper.buildSearch(filterList, listOf("Action", "Isekai", "Fantasy"))

        assertEquals(Filter.TriState.STATE_INCLUDE, actionFilter.state)
        assertEquals("Isekai Fantasy", params.textQuery)
    }

    @Test
    fun `normalization -- sci-fi matches filter named sci fi`() {
        val sciFiFilter = triState("Sci Fi")
        val filterList = FilterList(group("Genre", listOf(sciFiFilter)))

        GenreFilterMapper.buildSearch(filterList, listOf("Sci-Fi"))

        assertEquals(Filter.TriState.STATE_INCLUDE, sciFiFilter.state)
    }

    @Test
    fun `empty genre list produces empty text query and unmodified filters`() {
        val actionFilter = triState("Action")
        val filterList = FilterList(group("Genre", listOf(actionFilter)))

        val params = GenreFilterMapper.buildSearch(filterList, emptyList())

        assertEquals(Filter.TriState.STATE_IGNORE, actionFilter.state)
        assertEquals("", params.textQuery)
    }

    // --- Alias-aware matching tests ---

    // Note: aliasCandidates keys must be in normalizeTag() form (lowercase, spaces not underscores),
    // matching what BrowsePersonalRecommendationsScreenModel.topSearchTags() produces.

    @Test
    fun `user alias matches source filter label`() {
        // Source uses "BL"; desired genre key is "boys love" (normalized form of "Boys Love")
        val blFilter = triState("BL")
        val filterList = FilterList(group("Genre", listOf(blFilter)))
        // aliasCandidates key must be normalized: "boys love" not "boys_love"
        GenreFilterMapper.buildSearch(filterList, listOf("boys love"), aliasCandidates = mapOf("boys love" to listOf("BL")))
        assertEquals(Filter.TriState.STATE_INCLUDE, blFilter.state)
    }

    @Test
    fun `built-in synonym matches yuri filter for girls love genre`() {
        // Source uses "Yuri"; desired genre is "girls love" (normalized)
        val yuriFilter = triState("Yuri")
        val filterList = FilterList(group("Genre", listOf(yuriFilter)))
        // Built-in synonyms include Yuri for "girls love" (normalized key)
        GenreFilterMapper.buildSearch(filterList, listOf("girls love"))
        assertEquals(Filter.TriState.STATE_INCLUDE, yuriFilter.state)
    }

    @Test
    fun `built-in synonym matches shounen ai filter for boys love genre`() {
        val shounenAiFilter = triState("Shounen Ai")
        val filterList = FilterList(group("Genre", listOf(shounenAiFilter)))
        GenreFilterMapper.buildSearch(filterList, listOf("boys love"))
        assertEquals(Filter.TriState.STATE_INCLUDE, shounenAiFilter.state)
    }

    @Test
    fun `built-in synonym matches science fiction filter for sci fi genre`() {
        val scienceFilter = triState("Science Fiction")
        val filterList = FilterList(group("Genre", listOf(scienceFilter)))
        GenreFilterMapper.buildSearch(filterList, listOf("sci fi"))
        assertEquals(Filter.TriState.STATE_INCLUDE, scienceFilter.state)
    }

    @Test
    fun `unmatched alias falls back to text query`() {
        val filterList = FilterList()
        val params = GenreFilterMapper.buildSearch(
            filterList,
            listOf("girls love"),
            aliasCandidates = mapOf("girls love" to listOf("Yuri", "GL")),
        )
        // No filters to match against — should fall back to text
        assertTrue(params.textQuery.isNotBlank())
    }

    @Test
    fun `forceTextOnly skips filters and returns text query`() {
        val actionFilter = triState("Action")
        val filterList = FilterList(group("Genre", listOf(actionFilter)))
        val params = GenreFilterMapper.buildSearch(filterList, listOf("Action"), forceTextOnly = true)
        // Filter must NOT be set
        assertEquals(Filter.TriState.STATE_IGNORE, actionFilter.state)
        // Text query must include the genre
        assertTrue("Action" in params.textQuery)
    }

    @Test
    fun `forceTextOnly with alias includes alias in text`() {
        val filterList = FilterList()
        val params = GenreFilterMapper.buildSearch(
            filterList,
            listOf("girls love"),
            aliasCandidates = mapOf("girls love" to listOf("Yuri")),
            forceTextOnly = true,
        )
        assertTrue("girls love" in params.textQuery || "Yuri" in params.textQuery)
    }

    @Test
    fun `exact match still works with alias candidates present`() {
        val actionFilter = triState("Action")
        val filterList = FilterList(group("Genre", listOf(actionFilter)))
        // Alias candidates present for different tag — should not interfere
        GenreFilterMapper.buildSearch(
            filterList,
            listOf("Action"),
            aliasCandidates = mapOf("romance" to listOf("Shoujo")),
        )
        assertEquals(Filter.TriState.STATE_INCLUDE, actionFilter.state)
    }

    // KMK --> v0.7.20: blocked-genre query-time exclusion tests (Phase 7)

    @Test
    fun `blocked genre triState in group is set to STATE_EXCLUDE`() {
        val hentaiFilter = triState("Hentai")
        val actionFilter = triState("Action")
        val filterList = FilterList(group("Genre", listOf(hentaiFilter, actionFilter)))

        GenreFilterMapper.buildSearch(filterList, listOf("Action"), blockedGenres = listOf("Hentai"))

        assertEquals(Filter.TriState.STATE_EXCLUDE, hentaiFilter.state)
        assertEquals(Filter.TriState.STATE_INCLUDE, actionFilter.state)
    }

    @Test
    fun `blocked genre does not downgrade a filter already set to STATE_INCLUDE`() {
        val actionFilter = triState("Action")
        val filterList = FilterList(group("Genre", listOf(actionFilter)))

        // Action is both desired and blocked — INCLUDE wins because code only touches STATE_IGNORE entries
        GenreFilterMapper.buildSearch(filterList, listOf("Action"), blockedGenres = listOf("Action"))

        assertEquals(Filter.TriState.STATE_INCLUDE, actionFilter.state)
    }

    @Test
    fun `blocked genre with no matching filter causes no crash and no mutation`() {
        val actionFilter = triState("Action")
        val filterList = FilterList(group("Genre", listOf(actionFilter)))

        // "Gore" is not in the filter list — should be silently skipped
        GenreFilterMapper.buildSearch(filterList, emptyList(), blockedGenres = listOf("Gore"))

        assertEquals(Filter.TriState.STATE_IGNORE, actionFilter.state)
    }

    @Test
    fun `blocked genre skips CheckBox filters -- exclusion only applies to TriState`() {
        val hentaiCheck = checkBox("Hentai")
        val filterList = FilterList(group("Genre", listOf(hentaiCheck)))

        GenreFilterMapper.buildSearch(filterList, emptyList(), blockedGenres = listOf("Hentai"))

        // CheckBox has no exclude state — must remain false, no crash
        assertEquals(false, hentaiCheck.state)
    }

    @Test
    fun `forceTextOnly skips blocked genre filter application`() {
        val hentaiFilter = triState("Hentai")
        val filterList = FilterList(group("Genre", listOf(hentaiFilter)))

        GenreFilterMapper.buildSearch(
            filterList,
            listOf("Action"),
            forceTextOnly = true,
            blockedGenres = listOf("Hentai"),
        )

        // forceTextOnly must suppress both inclusion AND exclusion filter mutations
        assertEquals(Filter.TriState.STATE_IGNORE, hentaiFilter.state)
    }

    @Test
    fun `built-in synonym matches blocked filter label for exclusion`() {
        // Source uses "Yaoi"; user blocks "boys love" (normalized key)
        val yaoiFilter = triState("Yaoi")
        val filterList = FilterList(group("Genre", listOf(yaoiFilter)))

        GenreFilterMapper.buildSearch(filterList, emptyList(), blockedGenres = listOf("boys love"))

        assertEquals(Filter.TriState.STATE_EXCLUDE, yaoiFilter.state)
    }

    @Test
    fun `empty filterList with blocked genres causes no crash`() {
        // Should complete without exception even when filterList is completely empty
        val params = GenreFilterMapper.buildSearch(FilterList(), emptyList(), blockedGenres = listOf("Hentai", "Gore"))
        assertEquals("", params.textQuery)
    }

    // KMK <--
}

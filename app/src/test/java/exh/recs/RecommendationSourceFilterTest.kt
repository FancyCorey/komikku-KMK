package exh.recs

// KMK -->
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecommendationSourceFilterTest {

    private fun source(id: Long, lang: String, name: String = "Source$id"): CatalogueSource = object : CatalogueSource {
        override val id = id
        override val name = name
        override val lang = lang
        override val supportsLatest = false
        override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(emptyList(), false)
        override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(emptyList(), false)
        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(emptyList(), false)
        override suspend fun getMangaDetails(manga: SManga): SManga = manga
        override suspend fun getChapterList(manga: SManga) = emptyList<eu.kanade.tachiyomi.source.model.SChapter>()
        override suspend fun getPageList(chapter: eu.kanade.tachiyomi.source.model.SChapter) = emptyList<eu.kanade.tachiyomi.source.model.Page>()
        override fun getFilterList(): FilterList = FilterList()
    }

    private val local = source(0L, "")
    private val en1 = source(1L, "en", "MangaFire")
    private val en2 = source(2L, "en", "ThunderScans")
    private val ptBr = source(3L, "pt-br", "MangaFire PT-BR")
    private val ja = source(4L, "ja", "MangaFire JA")
    private val es = source(5L, "es", "MangaFire ES")
    private val es419 = source(6L, "es-419", "MangaFire ES-419")
    private val allSources = listOf(local, en1, en2, ptBr, ja, es, es419)

    // --- normalizeLanguages ---

    @Test
    fun `normalizeLanguages lowercases and trims`() {
        assertEquals(setOf("en"), RecommendationSourceFilter.normalizeLanguages(setOf("EN", " en ")))
    }

    @Test
    fun `normalizeLanguages empty set falls back to default EN`() {
        assertEquals(setOf("en"), RecommendationSourceFilter.normalizeLanguages(emptySet()))
    }

    @Test
    fun `normalizeLanguages blank strings fall back to default EN`() {
        assertEquals(setOf("en"), RecommendationSourceFilter.normalizeLanguages(setOf("  ", "")))
    }

    // --- isLocalSource ---

    @Test
    fun `isLocalSource returns true for id 0`() {
        assertTrue(RecommendationSourceFilter.isLocalSource(local))
    }

    @Test
    fun `isLocalSource returns false for non-zero id`() {
        assertFalse(RecommendationSourceFilter.isLocalSource(en1))
    }

    // --- filterForRecommendations ---

    @Test
    fun `EN-only keeps EN sources`() {
        val result = RecommendationSourceFilter.filterForRecommendations(allSources, setOf("en"))
        assertEquals(listOf(en1, en2), result)
    }

    @Test
    fun `EN-only excludes PT-BR, JA, ES, ES-419`() {
        val result = RecommendationSourceFilter.filterForRecommendations(allSources, setOf("en"))
        assertFalse(result.any { it.id == ptBr.id })
        assertFalse(result.any { it.id == ja.id })
        assertFalse(result.any { it.id == es.id })
        assertFalse(result.any { it.id == es419.id })
    }

    @Test
    fun `multi-language selection keeps all selected languages`() {
        val result = RecommendationSourceFilter.filterForRecommendations(allSources, setOf("en", "pt-br"))
        assertEquals(listOf(en1, en2, ptBr), result)
    }

    @Test
    fun `empty language set falls back to EN`() {
        val result = RecommendationSourceFilter.filterForRecommendations(allSources, emptySet())
        assertEquals(listOf(en1, en2), result)
    }

    @Test
    fun `local source is excluded by default`() {
        val result = RecommendationSourceFilter.filterForRecommendations(allSources, setOf("en"))
        assertFalse(result.any { it.id == 0L })
    }

    @Test
    fun `includeLocal true includes local source when its lang matches`() {
        val localEn = source(0L, "en", "Local Source")
        val sources = listOf(localEn, en1)
        val result = RecommendationSourceFilter.filterForRecommendations(sources, setOf("en"), includeLocal = true)
        assertTrue(result.any { it.id == 0L })
    }

    // --- availableLanguages ---

    @Test
    fun `availableLanguages excludes local source and returns sorted distinct langs`() {
        val langs = RecommendationSourceFilter.availableLanguages(allSources)
        assertFalse(langs.contains(""))
        assertTrue(langs.contains("en"))
        assertTrue(langs.contains("pt-br"))
        assertEquals(langs, langs.sorted())
    }
}
// KMK <--

package exh.recs

// KMK --> v0.7.40: source selection policy tests
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for [RecommendationSourceSelector].
 *
 * Verifies that language filtering, priority ordering, and disabled/disliked exclusion
 * are all applied correctly by the selector.
 *
 * Run with: ./gradlew :app:testDebugUnitTest --tests "*.RecommendationSourceSelectorTest"
 */
class RecommendationSourceSelectorTest {

    private fun source(id: Long, lang: String): CatalogueSource = object : CatalogueSource {
        override val id = id
        override val name = "Source$id"
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

    private val en1 = source(1L, "en")
    private val en2 = source(2L, "en")
    private val en3 = source(3L, "en")
    private val ja1 = source(4L, "ja")
    private val local = source(0L, "all") // Local Source id=0

    // ---- Language filtering ----

    @Test
    fun `select returns only sources matching requested language`() {
        val result = RecommendationSourceSelector.select(
            sources = listOf(en1, en2, ja1),
            languages = setOf("en"),
            storedOrder = emptyList(),
            effectiveDisabledIds = emptySet(),
        )
        assertTrue(result.all { it.lang == "en" })
        assertFalse(result.any { it.id == ja1.id })
    }

    @Test
    fun `select with empty languages falls back to default English`() {
        val result = RecommendationSourceSelector.select(
            sources = listOf(en1, ja1),
            languages = emptySet(),
            storedOrder = emptyList(),
            effectiveDisabledIds = emptySet(),
        )
        assertEquals(listOf(en1.id), result.map { it.id })
    }

    @Test
    fun `select excludes Local Source by default`() {
        val result = RecommendationSourceSelector.select(
            sources = listOf(local, en1),
            languages = setOf("en"),
            storedOrder = emptyList(),
            effectiveDisabledIds = emptySet(),
        )
        assertFalse(result.any { it.id == 0L })
    }

    // ---- Priority ordering ----

    @Test
    fun `select applies stored priority ordering`() {
        val storedOrder = listOf(en3.id, en1.id, en2.id)
        val result = RecommendationSourceSelector.select(
            sources = listOf(en1, en2, en3),
            languages = setOf("en"),
            storedOrder = storedOrder,
            effectiveDisabledIds = emptySet(),
        )
        assertEquals(listOf(en3.id, en1.id, en2.id), result.map { it.id })
    }

    @Test
    fun `select appends sources not in stored order after ordered ones`() {
        val storedOrder = listOf(en1.id)
        val result = RecommendationSourceSelector.select(
            sources = listOf(en1, en2, en3),
            languages = setOf("en"),
            storedOrder = storedOrder,
            effectiveDisabledIds = emptySet(),
        )
        assertEquals(en1.id, result.first().id)
        assertTrue(result.map { it.id }.containsAll(listOf(en2.id, en3.id)))
    }

    // ---- Disabled and disliked exclusion ----

    @Test
    fun `select excludes disabled sources`() {
        val result = RecommendationSourceSelector.select(
            sources = listOf(en1, en2, en3),
            languages = setOf("en"),
            storedOrder = emptyList(),
            effectiveDisabledIds = setOf(en2.id),
        )
        assertFalse(result.any { it.id == en2.id })
        assertEquals(2, result.size)
    }

    @Test
    fun `select excludes disliked sources when combined with disabled`() {
        val result = RecommendationSourceSelector.select(
            sources = listOf(en1, en2, en3),
            languages = setOf("en"),
            storedOrder = emptyList(),
            effectiveDisabledIds = setOf(en1.id, en3.id),
        )
        assertEquals(listOf(en2.id), result.map { it.id })
    }

    // ---- maxSources cap ----

    @Test
    fun `select respects maxSources cap`() {
        val result = RecommendationSourceSelector.select(
            sources = listOf(en1, en2, en3),
            languages = setOf("en"),
            storedOrder = emptyList(),
            effectiveDisabledIds = emptySet(),
            maxSources = 2,
        )
        assertEquals(2, result.size)
    }

    @Test
    fun `select with maxSources=0 returns all matching sources`() {
        val result = RecommendationSourceSelector.select(
            sources = listOf(en1, en2, en3),
            languages = setOf("en"),
            storedOrder = emptyList(),
            effectiveDisabledIds = emptySet(),
            maxSources = 0,
        )
        assertEquals(3, result.size)
    }

    // ---- Multi-language ----

    @Test
    fun `select returns sources from all requested languages`() {
        val result = RecommendationSourceSelector.select(
            sources = listOf(en1, ja1),
            languages = setOf("en", "ja"),
            storedOrder = emptyList(),
            effectiveDisabledIds = emptySet(),
        )
        assertEquals(2, result.size)
    }
}
// KMK <--

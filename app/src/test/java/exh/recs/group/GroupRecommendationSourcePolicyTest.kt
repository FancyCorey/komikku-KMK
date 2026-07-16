package exh.recs.group

// KMK --> v0.7.40: group source selection policy tests
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import exh.recs.RecommendationSourceSelector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verifies that the group-seeded recommendation flow honors the same source selection rules
 * as For You: real user language preference, stored priority order, disabled/disliked exclusion.
 *
 * These tests drive [RecommendationSourceSelector] directly (same helper used by Group flow)
 * to document and lock in the expected behavior without needing the full screen model.
 *
 * Run with: ./gradlew :app:testDebugUnitTest --tests "*.GroupRecommendationSourcePolicyTest"
 */
class GroupRecommendationSourcePolicyTest {

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

    @Test
    fun `group flow honors stored priority ordering`() {
        val storedOrder = listOf(en3.id, en1.id, en2.id)
        val result = RecommendationSourceSelector.select(
            sources = listOf(en1, en2, en3),
            languages = setOf("en"),
            storedOrder = storedOrder,
            effectiveDisabledIds = emptySet(),
            maxSources = 5,
        )
        assertEquals(listOf(en3.id, en1.id, en2.id), result.map { it.id })
    }

    @Test
    fun `group flow excludes disabled sources`() {
        val result = RecommendationSourceSelector.select(
            sources = listOf(en1, en2, en3),
            languages = setOf("en"),
            storedOrder = emptyList(),
            effectiveDisabledIds = setOf(en2.id),
            maxSources = 5,
        )
        assertFalse(result.any { it.id == en2.id })
    }

    @Test
    fun `group flow excludes disliked sources`() {
        val result = RecommendationSourceSelector.select(
            sources = listOf(en1, en2, en3),
            languages = setOf("en"),
            storedOrder = emptyList(),
            effectiveDisabledIds = setOf(en3.id), // disliked treated as effectively disabled
            maxSources = 5,
        )
        assertFalse(result.any { it.id == en3.id })
    }

    @Test
    fun `group flow uses real language preference instead of English default`() {
        val result = RecommendationSourceSelector.select(
            sources = listOf(en1, ja1),
            languages = setOf("ja"),
            storedOrder = emptyList(),
            effectiveDisabledIds = emptySet(),
            maxSources = 5,
        )
        assertEquals(listOf(ja1.id), result.map { it.id })
    }

    @Test
    fun `group flow respects maxSources cap`() {
        val result = RecommendationSourceSelector.select(
            sources = listOf(en1, en2, en3),
            languages = setOf("en"),
            storedOrder = emptyList(),
            effectiveDisabledIds = emptySet(),
            maxSources = 2,
        )
        assertTrue(result.size <= 2)
    }

    @Test
    fun `group flow returns empty when all sources are disabled`() {
        val result = RecommendationSourceSelector.select(
            sources = listOf(en1, en2),
            languages = setOf("en"),
            storedOrder = emptyList(),
            effectiveDisabledIds = setOf(en1.id, en2.id),
            maxSources = 5,
        )
        assertTrue(result.isEmpty())
    }
}
// KMK <--

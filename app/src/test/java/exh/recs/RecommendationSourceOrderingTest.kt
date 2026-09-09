package exh.recs

// KMK -->
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.collections.immutable.toPersistentList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecommendationSourceOrderingTest {

    private fun source(id: Long, name: String = "Source$id"): Source = object : Source {
        override val id = id
        override val name = name
        override val lang = "en"
        override val supportsLatest = false
        override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(emptyList(), false)
        override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(emptyList(), false)
        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(emptyList(), false)
        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<eu.kanade.tachiyomi.source.model.SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ) = eu.kanade.tachiyomi.source.model.SMangaUpdate(manga, emptyList())
        override suspend fun getPageList(chapter: eu.kanade.tachiyomi.source.model.SChapter) = emptyList<eu.kanade.tachiyomi.source.model.Page>()
        override fun getFilterList(): FilterList = FilterList()
    }

    private val s1 = source(1)
    private val s2 = source(2)
    private val s3 = source(3)
    private val s4 = source(4)
    private val s5 = source(5)
    private val allSources = listOf(s1, s2, s3, s4, s5)

    @Test
    fun `parse empty string returns empty list`() {
        assertEquals(emptyList<Long>(), RecommendationSourceOrdering.parse(""))
    }

    @Test
    fun `parse blank string returns empty list`() {
        assertEquals(emptyList<Long>(), RecommendationSourceOrdering.parse("   "))
    }

    @Test
    fun `parse valid ids returns list`() {
        assertEquals(listOf(1L, 2L, 3L), RecommendationSourceOrdering.parse("1,2,3"))
    }

    @Test
    fun `parse removes duplicate ids while preserving order`() {
        assertEquals(listOf(1L, 2L, 3L), RecommendationSourceOrdering.parse("1,2,2,3,1"))
    }

    @Test
    fun `parse drops malformed ids and preserves first occurrence order`() {
        assertEquals(listOf(1L, 3L), RecommendationSourceOrdering.parse("1,notanid,,3,NaN"))
    }

    @Test
    fun `serialize round-trips with parse`() {
        val ids = listOf(3L, 1L, 2L)
        assertEquals(ids, RecommendationSourceOrdering.parse(RecommendationSourceOrdering.serialize(ids)))
    }

    @Test
    fun `apply with empty order returns enabled sources in default order`() {
        val result = RecommendationSourceOrdering.apply(allSources, emptyList(), setOf(3L))
        assertEquals(listOf(s1, s2, s4, s5), result)
    }

    @Test
    fun `apply respects stored order`() {
        val result = RecommendationSourceOrdering.apply(allSources, listOf(3L, 1L, 2L), emptySet())
        assertEquals(listOf(s3, s1, s2, s4, s5), result)
    }

    @Test
    fun `apply excludes disabled sources`() {
        val result = RecommendationSourceOrdering.apply(allSources, listOf(1L, 2L, 3L), setOf(2L))
        assertEquals(listOf(s1, s3, s4, s5), result)
    }

    @Test
    fun `apply ignores stored ids for uninstalled sources`() {
        val result = RecommendationSourceOrdering.apply(listOf(s1, s2), listOf(99L, 1L, 2L), emptySet())
        assertEquals(listOf(s1, s2), result)
    }

    @Test
    fun `apply appends new sources not in stored order`() {
        val result = RecommendationSourceOrdering.apply(allSources, listOf(2L, 1L), emptySet())
        // 2,1 stored; 3,4,5 appended in default order
        assertEquals(listOf(s2, s1, s3, s4, s5), result)
    }

    @Test
    fun `boostedSourceIds returns first BOOSTED_SOURCE_COUNT ids`() {
        val ordered = listOf(s3, s1, s2, s4, s5)
        val boosted = RecommendationSourceOrdering.boostedSourceIds(ordered)
        assertEquals(setOf(3L, 1L, 2L), boosted)
    }

    @Test
    fun `boostedSourceIds with fewer sources than BOOSTED_SOURCE_COUNT returns all`() {
        val ordered = listOf(s1, s2)
        val boosted = RecommendationSourceOrdering.boostedSourceIds(ordered)
        assertEquals(setOf(1L, 2L), boosted)
    }

    @Test
    fun `prioritizeHealthySources moves errors after healthy sources and preserves each order`() {
        val ordered = listOf(s1, s2, s3, s4)
        val statuses = mapOf(
            1L to RecommendationSourceRunStatus(1L, RecommendationSourceStatus.Shown),
            2L to RecommendationSourceRunStatus(2L, RecommendationSourceStatus.Error),
            3L to RecommendationSourceRunStatus(3L, RecommendationSourceStatus.Shown),
            4L to RecommendationSourceRunStatus(4L, RecommendationSourceStatus.Error),
        )

        assertEquals(
            listOf(s1, s3, s2, s4),
            RecommendationSourceOrdering.prioritizeHealthySources(ordered, statuses),
        )
    }

    @Test
    fun `prioritizeHealthySources treats missing status as healthy`() {
        val ordered = listOf(s1, s2, s3)
        val statuses = mapOf(2L to RecommendationSourceRunStatus(2L, RecommendationSourceStatus.Error))

        assertEquals(
            listOf(s1, s3, s2),
            RecommendationSourceOrdering.prioritizeHealthySources(ordered, statuses),
        )
    }

    @Test
    fun `prioritizeHealthySources moves no-match and filtered-out lanes after usable sources`() {
        val ordered = listOf(s1, s2, s3, s4)
        val statuses = mapOf(
            1L to RecommendationSourceRunStatus(1L, RecommendationSourceStatus.NoMatches),
            2L to RecommendationSourceRunStatus(2L, RecommendationSourceStatus.Shown),
            3L to RecommendationSourceRunStatus(3L, RecommendationSourceStatus.FilteredOut),
            4L to RecommendationSourceRunStatus(4L, RecommendationSourceStatus.Error),
        )

        assertEquals(
            listOf(s2, s1, s3, s4),
            RecommendationSourceOrdering.prioritizeHealthySources(ordered, statuses),
        )
    }

    @Test
    fun `applyAll includes disabled sources in order`() {
        val result = RecommendationSourceOrdering.applyAll(allSources, listOf(3L, 1L, 2L))
        assertEquals(listOf(s3, s1, s2, s4, s5), result)
    }

    @Test
    fun `re-enabled source appears in its stored position`() {
        // Source 2 was disabled, re-enable by passing empty disabled set; stored order had it at index 1
        val storedOrder = listOf(1L, 2L, 3L)
        val result = RecommendationSourceOrdering.apply(allSources, storedOrder, emptySet())
        assertEquals(listOf(s1, s2, s3, s4, s5), result)
        assertEquals(1, result.indexOfFirst { it.id == 2L })
    }

    @Test
    fun `BOOSTED_SOURCE_COUNT constant is 3`() {
        assertEquals(3, RecommendationSourceOrdering.BOOSTED_SOURCE_COUNT)
    }

    // --- mergeVisibleOrder ---

    @Test
    fun `mergeVisibleOrder appends hidden ids after visible reordered ids`() {
        // s3, s4 are hidden (different language); visible: s1, s2 reordered to s2, s1
        val existingStored = listOf(1L, 2L, 3L, 4L)
        val visibleOrdered = listOf(2L, 1L)
        val allVisible = setOf(1L, 2L)
        val result = RecommendationSourceOrdering.mergeVisibleOrder(existingStored, visibleOrdered, allVisible)
        assertEquals(listOf(2L, 1L, 3L, 4L), result)
    }

    @Test
    fun `mergeVisibleOrder with no hidden ids returns visible order only`() {
        val existingStored = listOf(1L, 2L, 3L)
        val visibleOrdered = listOf(3L, 1L, 2L)
        val allVisible = setOf(1L, 2L, 3L)
        val result = RecommendationSourceOrdering.mergeVisibleOrder(existingStored, visibleOrdered, allVisible)
        assertEquals(listOf(3L, 1L, 2L), result)
    }

    @Test
    fun `mergeVisibleOrder with empty stored order returns visible order`() {
        val result = RecommendationSourceOrdering.mergeVisibleOrder(
            existingStoredOrder = emptyList(),
            visibleOrderedIds = listOf(1L, 2L),
            allVisibleSourceIds = setOf(1L, 2L),
        )
        assertEquals(listOf(1L, 2L), result)
    }

    @Test
    fun `mergeVisibleOrder deduplicates ids`() {
        val existingStored = listOf(1L, 2L, 1L, 3L)
        val visibleOrdered = listOf(1L, 2L)
        val allVisible = setOf(1L, 2L)
        val result = RecommendationSourceOrdering.mergeVisibleOrder(existingStored, visibleOrdered, allVisible)
        assertEquals(listOf(1L, 2L, 3L), result)
    }

    @Test
    fun `mergeVisibleOrder preserves relative order of hidden ids`() {
        // hidden: s5, s3 (in that relative order in existing)
        val existingStored = listOf(1L, 5L, 2L, 3L, 4L)
        val visibleOrdered = listOf(4L, 1L, 2L)
        val allVisible = setOf(1L, 2L, 4L)
        val result = RecommendationSourceOrdering.mergeVisibleOrder(existingStored, visibleOrdered, allVisible)
        assertEquals(listOf(4L, 1L, 2L, 5L, 3L), result)
    }

    @Test
    fun `retry source replacement preserves order and does not duplicate source id`() {
        val original = source(2, "Original")
        val replacement = source(2, "Retry instance")

        val result = reconcileSourceOrder(
            current = listOf(s1, original).toPersistentList(),
            incoming = listOf(replacement),
        )

        assertEquals(listOf(1L, 2L), result.map { it.id })
        assertEquals("Retry instance", result[1].name)
    }
}
// KMK <--

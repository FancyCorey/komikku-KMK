package exh.recs.loved

// KMK -->
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaTaste

class LovedMangaSortTest {

    private fun taste(
        mangaId: Long,
        source: Long = 10L,
        url: String = "url$mangaId",
        title: String = "Manga $mangaId",
        updatedAt: Long = mangaId * 1000L,
    ) = MangaTaste(
        mangaId = mangaId,
        source = source,
        url = url,
        title = title,
        rating = MangaRating.LOVE.value,
        createdAt = 0L,
        updatedAt = updatedAt,
    )

    private fun entry(taste: MangaTaste, mangaTitle: String? = null): LovedMangaEntry {
        val manga = if (mangaTitle != null) {
            Manga.create().copy(
                id = taste.mangaId,
                source = taste.source,
                url = taste.url,
                ogTitle = mangaTitle,
            )
        } else {
            null
        }
        return LovedMangaEntry(taste = taste, manga = manga)
    }

    private fun successState(
        entries: List<LovedMangaEntry>,
        sortMode: LoveSortMode = LoveSortMode.RECENT,
    ) = LovedMangaScreenModel.State.Success(
        entries = entries,
        groupDuplicates = false,
        linkGroupByKey = emptyMap(),
        sortMode = sortMode,
    )

    @Test
    fun `RECENT preserves load order`() {
        val t1 = taste(1, updatedAt = 3000L)
        val t2 = taste(2, updatedAt = 2000L)
        val t3 = taste(3, updatedAt = 1000L)
        val entries = listOf(entry(t1), entry(t2), entry(t3))
        val state = successState(entries, LoveSortMode.RECENT)
        val result = state.displayItems.map { it.taste.mangaId }
        assertEquals(listOf(1L, 2L, 3L), result)
    }

    @Test
    fun `OLDEST reverses load order`() {
        val t1 = taste(1, updatedAt = 3000L)
        val t2 = taste(2, updatedAt = 2000L)
        val t3 = taste(3, updatedAt = 1000L)
        val entries = listOf(entry(t1), entry(t2), entry(t3))
        val state = successState(entries, LoveSortMode.OLDEST)
        val result = state.displayItems.map { it.taste.mangaId }
        assertEquals(listOf(3L, 2L, 1L), result)
    }

    @Test
    fun `TITLE_AZ sorts by manga title when manga is non-null`() {
        val t1 = taste(1)
        val t2 = taste(2)
        val t3 = taste(3)
        val entries = listOf(
            entry(t1, mangaTitle = "Zorro"),
            entry(t2, mangaTitle = "Apple"),
            entry(t3, mangaTitle = "Mango"),
        )
        val state = successState(entries, LoveSortMode.TITLE_AZ)
        val result = state.displayItems.map { it.taste.mangaId }
        assertEquals(listOf(2L, 3L, 1L), result)
    }

    @Test
    fun `TITLE_AZ falls back to taste title when manga is null`() {
        val t1 = taste(1, title = "Zorro")
        val t2 = taste(2, title = "Apple")
        val t3 = taste(3, title = "Mango")
        val entries = listOf(entry(t1), entry(t2), entry(t3))
        val state = successState(entries, LoveSortMode.TITLE_AZ)
        val result = state.displayItems.map { it.taste.mangaId }
        assertEquals(listOf(2L, 3L, 1L), result)
    }

    @Test
    fun `TITLE_AZ is case-insensitive`() {
        val t1 = taste(1, title = "banana")
        val t2 = taste(2, title = "APPLE")
        val t3 = taste(3, title = "Cherry")
        val entries = listOf(entry(t1), entry(t2), entry(t3))
        val state = successState(entries, LoveSortMode.TITLE_AZ)
        val result = state.displayItems.map { it.taste.mangaId }
        assertEquals(listOf(2L, 1L, 3L), result)
    }

    @Test
    fun `SOURCE sorts by taste source id`() {
        val t1 = taste(1, source = 300L)
        val t2 = taste(2, source = 100L)
        val t3 = taste(3, source = 200L)
        val entries = listOf(entry(t1), entry(t2), entry(t3))
        val state = successState(entries, LoveSortMode.SOURCE)
        val result = state.displayItems.map { it.taste.mangaId }
        assertEquals(listOf(2L, 3L, 1L), result)
    }

    @Test
    fun `sorting preserves all entries — none are dropped`() {
        val entries = (1L..5L).map { entry(taste(it)) }
        LoveSortMode.entries.forEach { mode ->
            val state = successState(entries, mode)
            assertEquals(5, state.displayItems.size, "Mode $mode dropped entries")
        }
    }

    @Test
    fun `empty list produces empty display items for all modes`() {
        LoveSortMode.entries.forEach { mode ->
            val state = successState(emptyList(), mode)
            assertEquals(0, state.displayItems.size, "Mode $mode non-empty for empty input")
        }
    }
}
// KMK <--

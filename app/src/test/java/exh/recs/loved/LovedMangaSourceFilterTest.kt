package exh.recs.loved

// KMK -->
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaTaste

class LovedMangaSourceFilterTest {

    private fun taste(
        mangaId: Long,
        source: Long,
        rating: Int = MangaRating.LOVE.value,
        url: String = "url$mangaId",
    ) = MangaTaste(
        mangaId = mangaId,
        source = source,
        url = url,
        title = "Manga $mangaId",
        rating = rating,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `keeps LOVE entries whose source is installed`() {
        val t = taste(1, source = 10L)
        val result = filterLovedTastesByInstalledSources(listOf(t), installedSourceIds = setOf(10L))
        assertEquals(listOf(t), result)
    }

    @Test
    fun `removes LOVE entries whose source is not installed`() {
        val t = taste(1, source = 99L)
        val result = filterLovedTastesByInstalledSources(listOf(t), installedSourceIds = setOf(10L))
        assertEquals(emptyList<MangaTaste>(), result)
    }

    @Test
    fun `removes LIKE entries even if source is installed`() {
        val t = taste(1, source = 10L, rating = MangaRating.LIKE.value)
        val result = filterLovedTastesByInstalledSources(listOf(t), installedSourceIds = setOf(10L))
        assertEquals(emptyList<MangaTaste>(), result)
    }

    @Test
    fun `removes DISLIKE entries even if source is installed`() {
        val t = taste(1, source = 10L, rating = MangaRating.DISLIKE.value)
        val result = filterLovedTastesByInstalledSources(listOf(t), installedSourceIds = setOf(10L))
        assertEquals(emptyList<MangaTaste>(), result)
    }

    @Test
    fun `preserves entries when multiple installed source IDs exist`() {
        val t1 = taste(1, source = 10L)
        val t2 = taste(2, source = 20L)
        val t3 = taste(3, source = 99L)
        val result = filterLovedTastesByInstalledSources(
            listOf(t1, t2, t3),
            installedSourceIds = setOf(10L, 20L),
        )
        assertEquals(listOf(t1, t2), result)
    }

    @Test
    fun `returns empty list when installed source set is empty`() {
        val t = taste(1, source = 10L)
        val result = filterLovedTastesByInstalledSources(listOf(t), installedSourceIds = emptySet())
        assertEquals(emptyList<MangaTaste>(), result)
    }

    @Test
    fun `does not mutate input list`() {
        val input = mutableListOf(taste(1, source = 10L), taste(2, source = 99L))
        filterLovedTastesByInstalledSources(input, installedSourceIds = setOf(10L))
        assertEquals(2, input.size)
    }

    @Test
    fun `returned list is a different object from input`() {
        val input = listOf(taste(1, source = 10L))
        val result = filterLovedTastesByInstalledSources(input, installedSourceIds = setOf(10L))
        assertNotSame(input, result)
    }

    @Test
    fun `filtering before grouping means versionCount counts only installed-source entries`() {
        // Two entries for the same manga — one from installed source, one from uninstalled.
        // After filtering, only the installed entry remains; grouper should produce versionCount = 1.
        val installed = taste(1, source = 10L, url = "url-a")
        val uninstalled = taste(2, source = 99L, url = "url-b")

        val filtered = filterLovedTastesByInstalledSources(
            listOf(installed, uninstalled),
            installedSourceIds = setOf(10L),
        )
        assertEquals(listOf(installed), filtered)
        // Only 1 entry reaches the grouper → versionCount will be 1, not 2.
        assertEquals(1, filtered.size)
    }

    // KMK --> v0.7.35: generalized filter tests
    @Test
    fun `filterRatedTastesByInstalledSources keeps LIKE entries with matching rating`() {
        val t = taste(1, source = 10L, rating = MangaRating.LIKE.value)
        val result = filterRatedTastesByInstalledSources(
            listOf(t),
            installedSourceIds = setOf(10L),
            allowedRatings = setOf(MangaRating.LIKE.value),
        )
        assertEquals(listOf(t), result)
    }

    @Test
    fun `filterRatedTastesByInstalledSources keeps DISLIKE entries with matching rating`() {
        val t = taste(1, source = 10L, rating = MangaRating.DISLIKE.value)
        val result = filterRatedTastesByInstalledSources(
            listOf(t),
            installedSourceIds = setOf(10L),
            allowedRatings = setOf(MangaRating.DISLIKE.value),
        )
        assertEquals(listOf(t), result)
    }

    @Test
    fun `filterRatedTastesByInstalledSources excludes entries with different rating`() {
        val liked = taste(1, source = 10L, rating = MangaRating.LIKE.value)
        val loved = taste(2, source = 10L, rating = MangaRating.LOVE.value)
        val result = filterRatedTastesByInstalledSources(
            listOf(liked, loved),
            installedSourceIds = setOf(10L),
            allowedRatings = setOf(MangaRating.LIKE.value),
        )
        assertEquals(listOf(liked), result)
    }

    @Test
    fun `filterRatedTastesByInstalledSources excludes entries from uninstalled sources`() {
        val t = taste(1, source = 99L, rating = MangaRating.LIKE.value)
        val result = filterRatedTastesByInstalledSources(
            listOf(t),
            installedSourceIds = setOf(10L),
            allowedRatings = setOf(MangaRating.LIKE.value),
        )
        assertEquals(emptyList<MangaTaste>(), result)
    }

    @Test
    fun `filterLovedTastesByInstalledSources still delegates correctly after refactor`() {
        val loved = taste(1, source = 10L, rating = MangaRating.LOVE.value)
        val liked = taste(2, source = 10L, rating = MangaRating.LIKE.value)
        val result = filterLovedTastesByInstalledSources(listOf(loved, liked), installedSourceIds = setOf(10L))
        assertEquals(listOf(loved), result, "Legacy wrapper must still filter LOVE only")
    }
    // KMK <--
}
// KMK <--

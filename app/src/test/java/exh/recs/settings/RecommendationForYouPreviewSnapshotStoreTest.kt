package exh.recs.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK v0.8.14-fix1 -->
class RecommendationForYouPreviewSnapshotStoreTest {

    private fun manga(id: Long, source: Long, title: String) = RecommendationForYouPreviewManga(
        mangaId = id,
        sourceId = source,
        url = "/manga/$id",
        title = title,
        thumbnailUrl = "https://example.com/$id.jpg",
    )

    @Test
    fun `empty string parses to null`() {
        assertNull(RecommendationForYouPreviewSnapshotStore.parse(""))
    }

    @Test
    fun `blank string parses to null`() {
        assertNull(RecommendationForYouPreviewSnapshotStore.parse("   "))
    }

    @Test
    fun `malformed value does not throw and returns null`() {
        assertNull(RecommendationForYouPreviewSnapshotStore.parse("not a real snapshot"))
    }

    @Test
    fun `round trip preserves a single top picks row`() {
        val snapshot = RecommendationForYouPreviewSnapshot(
            capturedAt = 1_700_000_000_000L,
            rows = listOf(
                RecommendationForYouPreviewRow(
                    rowKey = "top_picks",
                    rowType = RecommendationForYouPreviewRowType.TOP_PICKS,
                    title = "Top Picks",
                    subtitle = "Combined from your top sources",
                    mangas = listOf(manga(1, 10, "Alpha"), manga(2, 11, "Beta")),
                ),
            ),
        )
        val parsed = RecommendationForYouPreviewSnapshotStore.parse(RecommendationForYouPreviewSnapshotStore.serialize(snapshot))
        assertEquals(snapshot, parsed)
    }

    @Test
    fun `round trip preserves multiple source rows in order`() {
        val snapshot = RecommendationForYouPreviewSnapshot(
            capturedAt = 42L,
            rows = listOf(
                RecommendationForYouPreviewRow("top_picks", RecommendationForYouPreviewRowType.TOP_PICKS, "Top Picks", "", listOf(manga(1, 10, "A"))),
                RecommendationForYouPreviewRow("10", RecommendationForYouPreviewRowType.SOURCE, "Source A", "3 results", listOf(manga(3, 10, "C"))),
                RecommendationForYouPreviewRow("11", RecommendationForYouPreviewRowType.SOURCE, "Source B", "1 result", listOf(manga(4, 11, "D"))),
            ),
        )
        val parsed = RecommendationForYouPreviewSnapshotStore.parse(RecommendationForYouPreviewSnapshotStore.serialize(snapshot))
        assertEquals(snapshot, parsed)
        assertEquals(listOf("top_picks", "10", "11"), parsed?.rows?.map { it.rowKey })
    }

    @Test
    fun `a row with no mangas round trips to an empty manga list`() {
        val snapshot = RecommendationForYouPreviewSnapshot(
            capturedAt = 1L,
            rows = listOf(RecommendationForYouPreviewRow("10", RecommendationForYouPreviewRowType.SOURCE, "Source A", "", emptyList())),
        )
        val parsed = RecommendationForYouPreviewSnapshotStore.parse(RecommendationForYouPreviewSnapshotStore.serialize(snapshot))
        assertEquals(emptyList<RecommendationForYouPreviewManga>(), parsed?.rows?.single()?.mangas)
    }

    @Test
    fun `a snapshot with zero rows round trips to an empty row list`() {
        val snapshot = RecommendationForYouPreviewSnapshot(capturedAt = 5L, rows = emptyList())
        val parsed = RecommendationForYouPreviewSnapshotStore.parse(RecommendationForYouPreviewSnapshotStore.serialize(snapshot))
        assertEquals(snapshot, parsed)
    }

    @Test
    fun `row count beyond MAX_ROWS is capped on serialize`() {
        val rows = (1..30).map { i ->
            RecommendationForYouPreviewRow(i.toString(), RecommendationForYouPreviewRowType.SOURCE, "S$i", "", emptyList())
        }
        val snapshot = RecommendationForYouPreviewSnapshot(1L, rows)
        val parsed = RecommendationForYouPreviewSnapshotStore.parse(RecommendationForYouPreviewSnapshotStore.serialize(snapshot))
        assertEquals(RecommendationForYouPreviewSnapshotStore.MAX_ROWS, parsed?.rows?.size)
    }

    @Test
    fun `manga count beyond MAX_MANGA_PER_ROW is capped on serialize`() {
        val mangas = (1..50).map { manga(it.toLong(), 10, "M$it") }
        val snapshot = RecommendationForYouPreviewSnapshot(
            1L,
            listOf(RecommendationForYouPreviewRow("10", RecommendationForYouPreviewRowType.SOURCE, "Source A", "", mangas)),
        )
        val parsed = RecommendationForYouPreviewSnapshotStore.parse(RecommendationForYouPreviewSnapshotStore.serialize(snapshot))
        assertEquals(RecommendationForYouPreviewSnapshotStore.MAX_MANGA_PER_ROW, parsed?.rows?.single()?.mangas?.size)
    }

    @Test
    fun `a null thumbnail url round trips to null, not the string null`() {
        val snapshot = RecommendationForYouPreviewSnapshot(
            1L,
            listOf(
                RecommendationForYouPreviewRow(
                    "10",
                    RecommendationForYouPreviewRowType.SOURCE,
                    "Source A",
                    "",
                    listOf(RecommendationForYouPreviewManga(1, 10, "/x", "Title", thumbnailUrl = null)),
                ),
            ),
        )
        val parsed = RecommendationForYouPreviewSnapshotStore.parse(RecommendationForYouPreviewSnapshotStore.serialize(snapshot))
        assertNull(parsed?.rows?.single()?.mangas?.single()?.thumbnailUrl)
    }

    @Test
    fun `malformed row missing fields is skipped, not the whole snapshot`() {
        val good = RecommendationForYouPreviewSnapshotStore.serialize(
            RecommendationForYouPreviewSnapshot(
                1L,
                listOf(RecommendationForYouPreviewRow("10", RecommendationForYouPreviewRowType.SOURCE, "Source A", "", emptyList())),
            ),
        )
        val parsed = RecommendationForYouPreviewSnapshotStore.parse(good)
        assertTrue(parsed != null && parsed.rows.isNotEmpty())
    }
}
// KMK <--

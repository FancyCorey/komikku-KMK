package exh.recs.settings

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK v0.8.9 -->
class RecommendationSettingsSearchIndexTest {

    private class FakeScreen(val id: String) : Screen {
        override val key: ScreenKey = uniqueScreenKey

        @Composable
        override fun Content() {}
    }

    private fun entry(
        key: String,
        title: String,
        summary: String = "",
        category: String = "",
        synonyms: List<String> = emptyList(),
        available: Boolean = true,
    ) = RecommendationSettingsSearchIndex.Entry(
        key = key,
        title = title,
        summary = summary,
        category = category,
        synonyms = synonyms,
        destination = FakeScreen(key),
        available = available,
    )

    // --- normalization ---

    @Test
    fun `normalize lowercases, strips punctuation, and collapses whitespace`() {
        assertEquals("source priority", RecommendationSettingsSearchIndex.normalize("Source-Priority"))
        assertEquals("source priority", RecommendationSettingsSearchIndex.normalize("SOURCE   PRIORITY"))
        assertEquals("source priority", RecommendationSettingsSearchIndex.normalize("  source, priority!  "))
    }

    @Test
    fun `search is case-insensitive`() {
        val entries = listOf(entry("a", "Source Priority"))
        assertEquals(1, RecommendationSettingsSearchIndex.search(entries, "SOURCE PRIORITY").size)
        assertEquals(1, RecommendationSettingsSearchIndex.search(entries, "source priority").size)
    }

    @Test
    fun `search normalizes punctuation and whitespace in the query too`() {
        val entries = listOf(entry("a", "Source Priority"))
        assertEquals(1, RecommendationSettingsSearchIndex.search(entries, "  source-priority!! ").size)
    }

    // --- title, summary, category, synonym matching ---

    @Test
    fun `matches on title substring`() {
        val entries = listOf(entry("a", "Source Priority"))
        assertEquals(1, RecommendationSettingsSearchIndex.search(entries, "priority").size)
    }

    @Test
    fun `matches on summary substring`() {
        val entries = listOf(entry("a", "For You", summary = "Daily recommendations language selector"))
        assertEquals(1, RecommendationSettingsSearchIndex.search(entries, "daily recommendations").size)
    }

    @Test
    fun `matches on category substring`() {
        val entries = listOf(entry("a", "Reset", category = "Management and Diagnostics"))
        assertEquals(1, RecommendationSettingsSearchIndex.search(entries, "diagnostics").size)
    }

    @Test
    fun `matches on synonym`() {
        val entries = listOf(entry("a", "Source Priority", synonyms = listOf("top three", "extension order")))
        assertEquals(1, RecommendationSettingsSearchIndex.search(entries, "top three").size)
        assertEquals(1, RecommendationSettingsSearchIndex.search(entries, "extension order").size)
    }

    @Test
    fun `no match returns empty list`() {
        val entries = listOf(entry("a", "Source Priority"))
        assertTrue(RecommendationSettingsSearchIndex.search(entries, "completely unrelated term").isEmpty())
    }

    // --- ranking ---

    @Test
    fun `exact title match ranks above a partial summary match`() {
        val entries = listOf(
            entry("summary-match", "Diagnostics", summary = "reset the source priority cache"),
            entry("exact-title", "source priority"),
        )
        val result = RecommendationSettingsSearchIndex.search(entries, "source priority")
        assertEquals("exact-title", result.first().key)
    }

    @Test
    fun `title prefix ranks above title-contains`() {
        val entries = listOf(
            entry("contains", "Best Source Priority Ever"),
            entry("prefix", "Source Priority Settings"),
        )
        val result = RecommendationSettingsSearchIndex.search(entries, "source priority")
        assertEquals("prefix", result.first().key)
    }

    @Test
    fun `title match ranks above synonym match`() {
        val entries = listOf(
            entry("synonym", "Diagnostics", synonyms = listOf("source priority")),
            entry("title", "Source Priority"),
        )
        val result = RecommendationSettingsSearchIndex.search(entries, "source priority")
        assertEquals("title", result.first().key)
    }

    @Test
    fun `synonym match ranks above summary match`() {
        val entries = listOf(
            entry("summary", "Diagnostics", summary = "source priority related cache reset"),
            entry("synonym", "Reordering", synonyms = listOf("source priority")),
        )
        val result = RecommendationSettingsSearchIndex.search(entries, "source priority")
        assertEquals("synonym", result.first().key)
    }

    // --- stable ordering for ties ---

    @Test
    fun `equal-score entries preserve original relative order`() {
        val entries = listOf(
            entry("first", "Alpha Tag"),
            entry("second", "Beta Tag"),
            entry("third", "Gamma Tag"),
        )
        val result = RecommendationSettingsSearchIndex.search(entries, "tag")
        assertEquals(listOf("first", "second", "third"), result.map { it.key })
    }

    // --- empty / blank query ---

    @Test
    fun `blank query returns empty results`() {
        val entries = listOf(entry("a", "Source Priority"))
        assertTrue(RecommendationSettingsSearchIndex.search(entries, "").isEmpty())
        assertTrue(RecommendationSettingsSearchIndex.search(entries, "   ").isEmpty())
    }

    // --- duplicate labels in different categories ---

    @Test
    fun `duplicate-sounding titles in different categories are both returned, distinguishable by category`() {
        val entries = listOf(
            entry("evaluation-cache", "Cache", category = "Source Evaluation"),
            entry("discovery-cache", "Cache", category = "Management and Diagnostics"),
        )
        val result = RecommendationSettingsSearchIndex.search(entries, "cache")
        assertEquals(2, result.size)
        assertEquals(setOf("Source Evaluation", "Management and Diagnostics"), result.map { it.category }.toSet())
    }

    // --- destination identifiers survive search (never mutate/lose the destination) ---

    @Test
    fun `search never mutates entries and preserves their destination`() {
        val original = entry("a", "Source Priority")
        val entries = listOf(original)
        val result = RecommendationSettingsSearchIndex.search(entries, "source")
        assertEquals(original.destination, result.single().destination)
        assertEquals(original, entries.single()) // list itself is untouched
    }

    // --- unavailable entries are still returned, not silently hidden ---

    @Test
    fun `an unavailable entry is still returned by search - never a silent miss`() {
        val entries = listOf(entry("a", "Shizuku Setup", available = false))
        val result = RecommendationSettingsSearchIndex.search(entries, "shizuku")
        assertEquals(1, result.size)
        assertEquals(false, result.single().available)
    }
}
// KMK <--

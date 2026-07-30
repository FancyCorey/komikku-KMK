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
        dedupeKey: String? = null,
    ) = RecommendationSettingsSearchIndex.Entry(
        key = key,
        title = title,
        summary = summary,
        category = category,
        synonyms = synonyms,
        destination = FakeScreen(key),
        available = available,
        dedupeKey = dedupeKey,
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

    // --- KMK v0.8.11: destination/search dedupe (Phase A) ---

    @Test
    fun `logical duplicates sharing a dedupeKey are collapsed to the best-ranked one`() {
        val entries = listOf(
            entry("evaluation_summary_match", "Source Evaluation", summary = "filter results", dedupeKey = "source_evaluation"),
            entry("evaluation_title_match", "Filter", category = "Source Evaluation", dedupeKey = "source_evaluation"),
        )
        val result = RecommendationSettingsSearchIndex.search(entries, "filter")
        assertEquals(1, result.size)
        // The title match ("Filter") outranks the summary match, so it survives the dedupe.
        assertEquals("evaluation_title_match", result.single().key)
    }

    @Test
    fun `a search for an installer or background synonym returns a single Source Evaluation result`() {
        val entries = listOf(
            entry(
                "evaluation",
                "Source Evaluation",
                category = "Source Evaluation",
                synonyms = listOf("installer", "shizuku", "background", "network"),
                dedupeKey = "source_evaluation",
            ),
        )
        val result = RecommendationSettingsSearchIndex.search(entries, "installer")
        assertEquals(1, result.size)
        assertEquals("evaluation", result.single().key)
    }

    @Test
    fun `different controls under the same category are not collapsed by dedupe`() {
        val entries = listOf(
            entry("evaluation_batch_size", "Batch size", category = "Source Evaluation", synonyms = listOf("batch")),
            entry("evaluation_diagnostics", "Evaluation diagnostics", category = "Source Evaluation", synonyms = listOf("batch related diagnostics")),
        )
        // Both entries score on "batch" (one via title, one via synonym) but have distinct
        // (default = key) dedupeKeys, so both must survive.
        val result = RecommendationSettingsSearchIndex.search(entries, "batch")
        assertEquals(2, result.size)
    }

    @Test
    fun `entries without an explicit dedupeKey default to their own key and never collide with each other`() {
        val entries = listOf(
            entry("a", "Source Priority"),
            entry("b", "Source Priority Settings"),
        )
        val result = RecommendationSettingsSearchIndex.search(entries, "source priority")
        assertEquals(2, result.size)
    }

    // KMK v0.8.13-fix1 -->
    // KMK v0.8.14: renamed from "For You"-labeled to "Taste and filters"-labeled -- the real rated/
    // known-manga controls these mirror now live under Taste and filters, not a "For You" category
    // (which is no longer a real destination at all as of this version). Mirrors the real entry
    // set's shape (one category-level entry plus per-control entries) closely enough to prove the
    // "hide" query never resolves to entries sharing an identical title -- the on-device flicker
    // report also described the "hide" results as indistinguishable at a glance.
    private val tasteFilterLikeEntries = listOf(
        entry(
            key = "taste_filters",
            title = "Taste and filters",
            summary = "Ratings visibility, known manga, minimum chapters, and tags",
            category = "Taste and filters",
            synonyms = listOf("hide disliked", "known manga"),
        ),
        entry(
            key = "taste_filters_hide_known_manga",
            title = "Hide known manga",
            summary = "Hide manga you already have in your library or history",
            category = "Taste and filters",
            synonyms = listOf("known manga", "hide known"),
        ),
        entry(
            key = "taste_filters_rated_visibility",
            title = "Ratings and known manga visibility",
            summary = "Choose which rated manga stay visible in For You",
            category = "Taste and filters",
            synonyms = listOf("hide disliked", "hide all rated"),
        ),
    )

    @Test
    fun `searching hide returns distinct entries, not duplicate-looking rows`() {
        val result = RecommendationSettingsSearchIndex.search(tasteFilterLikeEntries, "hide")
        assertTrue(result.size > 1, "expected more than one match for 'hide'")
        assertEquals(result.map { it.title }.distinct().size, result.size, "every matched row must have a distinct title")
    }

    // Mirrors the real Source Evaluation entry set's shape (one category-level entry, anchor-less,
    // plus four anchored per-control entries) -- the exact cluster the plan calls out as previously
    // reading as several near-identical Source Evaluation rows.
    private val sourceEvaluationLikeEntries = listOf(
        entry(
            key = "evaluation",
            title = "Source Evaluation",
            summary = "Temporarily install non-installed extensions to probe their content and learn how well they match your taste profile.",
            category = "Source Evaluation",
            synonyms = listOf("evaluate sources", "reassess"),
        ),
        entry(
            key = "evaluation_batch_size",
            title = "Batch size (sources to evaluate)",
            summary = "How many sources one evaluation run tests",
            category = "Source Evaluation",
            synonyms = listOf("batch size"),
        ),
        entry(
            key = "evaluation_stale_reassessment",
            title = "Reassess outdated sources",
            summary = "Re-evaluate sources whose results are outdated",
            category = "Source Evaluation",
            synonyms = listOf("stale", "outdated"),
        ),
        entry(
            key = "evaluation_diagnostics",
            title = "Evaluation diagnostics",
            summary = "Quarantined extensions, blocked packages, and source runtime health",
            category = "Source Evaluation",
            synonyms = listOf("quarantine"),
        ),
        entry(
            key = "installer_mode",
            title = "Installer mode",
            summary = "How extensions are temporarily installed and cleaned up during evaluation",
            category = "Source Evaluation",
            synonyms = listOf("installer", "shizuku"),
        ),
    ).map { fullEntry -> fullEntry.copy(anchor = if (fullEntry.key == "evaluation") null else fullEntry.key) }

    @Test
    fun `searching fil returns at most one category-level Source Evaluation row plus distinct anchored control rows`() {
        val result = RecommendationSettingsSearchIndex.search(sourceEvaluationLikeEntries, "fil")
        val categoryRows = result.filter { it.anchor == null }
        val controlRows = result.filter { it.anchor != null }
        assertTrue(categoryRows.size <= 1, "expected at most one category-level Source Evaluation row, got ${categoryRows.size}")
        assertEquals(controlRows.map { it.title }.distinct().size, controlRows.size, "every anchored control row must have a distinct title")
    }

    @Test
    fun `every Source Evaluation control row has a distinct title from every other row in its category`() {
        val allTitles = sourceEvaluationLikeEntries.map { it.title }
        assertEquals(allTitles.distinct().size, allTitles.size, "Source Evaluation entries must not share a title")
    }
    // KMK <--

    // KMK v0.8.14 -->
    // Mirrors the approved five-section structure closely enough to prove the search index itself
    // (not just the moved-controls' new categories, already exercised above) never resolves a query
    // to a "For You" or "Matching and versions" category -- neither exists as a real destination
    // anymore. `rememberRecommendationSettingsSearchEntries()` is a @Composable and can't be driven
    // directly in this project's pure-JVM test environment (no Robolectric), so this fixture mirrors
    // its real shape: five category-level entries plus representative per-control entries under the
    // categories that absorbed the retired screens' controls.
    //
    // KMK v0.8.14-fix1: "Sources and languages" renamed to "For You sources"; the language control
    // moved from that category to "Management and diagnostics" -- see
    // RecommendationDiagnosticsSettingsScreen's class doc.
    private val fiveSectionEntries = listOf(
        entry("for_you_sources", "For You sources", category = "For You sources", synonyms = listOf("source order", "source priority")),
        entry("taste_filters", "Taste and filters", category = "Taste and filters", synonyms = listOf("hide disliked", "known manga")),
        entry("evaluation", "Source Evaluation", category = "Source Evaluation", synonyms = listOf("reassess")),
        entry("discovery", "Sources to try", category = "Sources to try", synonyms = listOf("install extensions")),
        entry("diagnostics", "Management and diagnostics", category = "Management and diagnostics", synonyms = listOf("cache", "languages")),
        entry(
            "taste_filters_hide_known_manga",
            "Hide known manga",
            category = "Taste and filters",
            synonyms = listOf("known manga", "hide known"),
        ).copy(anchor = "hide_known_manga"),
        entry(
            "diagnostics_languages",
            "Recommendation languages",
            category = "Management and diagnostics",
            synonyms = listOf("languages", "language filter"),
        ).copy(anchor = "recommendation_languages_content"),
        entry(
            "diagnostics_same_manga",
            "Find other versions",
            category = "Management and diagnostics",
            synonyms = listOf("same manga matching", "find other versions"),
        ).copy(anchor = "same_manga_results_per_source"),
        entry(
            "diagnostics_best_version",
            "Best Version preview pages",
            category = "Management and diagnostics",
            synonyms = listOf("best version", "preview pages"),
        ).copy(anchor = "best_version_sample_size"),
    )

    @Test
    fun `no result category is titled For You`() {
        val result = RecommendationSettingsSearchIndex.search(fiveSectionEntries, "hide")
        assertTrue(result.none { it.category == "For You" }, "no result should ever be categorized under the retired For You destination")
    }

    @Test
    fun `no result category is titled Matching and versions`() {
        val result = RecommendationSettingsSearchIndex.search(fiveSectionEntries, "best version")
        assertTrue(result.none { it.category == "Matching and versions" }, "no result should ever be categorized under the retired Matching and versions destination")
    }

    @Test
    fun `no result category is titled Sources and languages`() {
        val result = RecommendationSettingsSearchIndex.search(fiveSectionEntries, "source order")
        assertTrue(result.none { it.category == "Sources and languages" }, "no result should ever be categorized under the retired 'Sources and languages' name")
    }

    @Test
    fun `searching hide opens a Taste and filters control`() {
        val result = RecommendationSettingsSearchIndex.search(fiveSectionEntries, "hide")
        assertTrue(result.any { it.category == "Taste and filters" && it.anchor == "hide_known_manga" })
    }

    @Test
    fun `searching language opens Management and diagnostics`() {
        val result = RecommendationSettingsSearchIndex.search(fiveSectionEntries, "language")
        assertTrue(result.any { it.category == "Management and diagnostics" && it.anchor == "recommendation_languages_content" })
        assertTrue(result.none { it.category == "For You sources" }, "language no longer belongs to For You sources")
    }

    @Test
    fun `searching same manga or best version opens Management and diagnostics anchors`() {
        val sameManga = RecommendationSettingsSearchIndex.search(fiveSectionEntries, "same manga")
        assertTrue(sameManga.any { it.category == "Management and diagnostics" && it.anchor == "same_manga_results_per_source" })

        val bestVersion = RecommendationSettingsSearchIndex.search(fiveSectionEntries, "best version")
        assertTrue(bestVersion.any { it.category == "Management and diagnostics" && it.anchor == "best_version_sample_size" })
    }
    // KMK <--
}
// KMK <--

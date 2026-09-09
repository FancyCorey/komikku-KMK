package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/** Regression guard for alternate-source correction searches using all enabled sources. */
class GlobalSearchAlternateSourceModeSourceTest {

    @Test
    fun `return-selection search starts with all sources and preserves that preference`() {
        val text = source("app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/globalsearch/GlobalSearchScreenModel.kt")

        assertTrue(
            text.contains("sourceFilter = if (returnSelection) SourceFilter.All else SourceFilter.PinnedOnly"),
            "alternate-source correction must not default to pinned sources only",
        )
        assertTrue(
            text.contains("forceAllSources = returnSelection"),
            "alternate-source correction must remain on all sources when the preference flow emits",
        )
        val screen = source("app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/globalsearch/GlobalSearchScreen.kt")
        assertTrue(
            screen.contains("returnSelection = returnSelection"),
            "alternate-source Global Search must pass return-selection mode to its owner",
        )
        assertTrue(
            screen.contains(
                "listingQuery = state.searchQuery,\n                            returnSelection = returnSelection,",
            ),
            "alternate-source Global Search must preserve return-selection mode for individual-source search",
        )
        val sourceBrowse = source("app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/browse/BrowseSourceScreen.kt")
        assertTrue(
            sourceBrowse.contains("private val returnSelection: Boolean = false"),
            "individual-source search must carry the alternate-source return contract",
        )
        assertTrue(
            sourceBrowse.contains("finishWithSelection(context, manga.id)"),
            "individual-source results must return the selected manga to the reader",
        )
    }

    @Test
    fun `source filter updates local state before the immediate search`() {
        val text = source("app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/globalsearch/SearchScreenModel.kt")

        assertTrue(
            text.contains(
                "mutableState.update { it.copy(sourceFilter = if (forceAllSources) SourceFilter.All else filter) }",
            ),
            "source filtering must update the in-memory state before search reads it",
        )
    }

    private fun source(relativePath: String): String {
        val direct = Path.of(relativePath)
        val fromParent = Path.of("..").resolve(relativePath)
        val path = listOf(direct, fromParent)
            .map(Path::toAbsolutePath)
            .firstOrNull(Files::isRegularFile)
            ?: error("Unable to resolve source file: $relativePath")
        return String(Files.readAllBytes(path), Charsets.UTF_8)
    }
}

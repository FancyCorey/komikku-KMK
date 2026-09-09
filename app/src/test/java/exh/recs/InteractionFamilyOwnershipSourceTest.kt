package exh.recs

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class InteractionFamilyOwnershipSourceTest {
    @Test
    fun `For You uses explicit local selection policy and single-selection gate`() {
        val policy = source("src/main/java/exh/recs/ForYouSelectionPolicy.kt")
        val screen = source("src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt")
        assertTrue(policy.contains("fun <T> longPress"))
        assertTrue(policy.contains("fun <T> toggle"))
        assertTrue(policy.contains("fun <T> clear"))
        assertTrue(policy.contains("fun <T> isSingleSelection"))
        assertTrue(screen.contains("if (selectionMode) onToggleSelectManga(manga) else onClickItem(manga)"))
        assertTrue(screen.contains("ForYouSelectionPolicy.isSingleSelection(selectedManga)"))
    }

    @Test
    fun `History reserves back for selection exit and delegates item toggle`() {
        val history = source("src/main/java/eu/kanade/presentation/history/HistoryScreen.kt")
        assertTrue(history.contains("BackHandler(enabled = state.selectionMode, onBack = toggleSelectionMode)"))
        assertTrue(history.contains("selectionMode -> onHistorySelected"))
        assertTrue(history.contains("fromLongPress = true"))
        assertTrue(history.contains("onCancelActionMode = toggleSelectionMode"))
    }

    @Test
    fun `Library and shared bulk toolbar keep selection separate from mutations`() {
        val library = source("src/main/java/eu/kanade/presentation/library/components/LibraryContent.kt")
        val toolbar = source("src/main/java/eu/kanade/presentation/components/BulkSelectionToolbar.kt")
        assertTrue(library.contains("selection: Set<Long>"))
        assertTrue(library.contains("onToggleSelection: (Category, LibraryManga) -> Unit"))
        assertTrue(library.contains("onToggleRangeSelection: (Category, LibraryManga) -> Unit"))
        assertTrue(toolbar.contains("isActionMode = true"))
        assertTrue(toolbar.contains("onCancelActionMode = onClickClearSelection"))
        assertTrue(toolbar.contains("if (selectedCount > 0)"))
    }

    private fun source(path: String): String = File(path).also {
        assertTrue(it.isFile, "expected source file at $path")
    }.readText()
}

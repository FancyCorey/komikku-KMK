package exh.recs

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class BulkActionAcknowledgmentSourceTest {
    @Test
    fun `For You bulk actions snapshot targets clear selection and await feedback`() {
        val source = source("src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt")
        assertTrue(source.contains("val targets = selectedManga.values.toList()"))
        assertTrue(source.contains("selectedManga = ForYouSelectionPolicy.clear()"))
        assertTrue(source.contains("val outcome = screenModel.rateSelected(targets, rating)"))
        assertTrue(source.contains("val outcome = screenModel.markSelectedNotInterested(targets)"))
        assertTrue(source.contains("val outcome = screenModel.clearSelectedRatings(targets)"))
        assertTrue(source.contains("showBulkActionFeedback(context"))
    }

    @Test
    fun `cross-source follow-up is gated on confirmed success`() {
        val source = source("src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt")
        assertTrue(source.contains("if (single != null && outcome.successCount > 0)"))
        assertTrue(source.contains("pendingRateOtherVersions = single.id to rating"))
        assertTrue(source.contains("onDismissRequest = { pendingRateOtherVersions = null }"))
    }

    private fun source(path: String): String = File(path).also {
        assertTrue(it.isFile, "expected source file at $path")
    }.readText()
}

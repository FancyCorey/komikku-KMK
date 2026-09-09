package eu.kanade.tachiyomi.ui.manga.track

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class LocalTrackingInteractionContractTest {
    private val home = File("src/main/java/eu/kanade/presentation/track/TrackInfoDialogHome.kt").readText()
    private val dialogs = File("src/main/java/eu/kanade/presentation/manga/components/MangaDialogs.kt").readText()
    private val screenModel = File("src/main/java/eu/kanade/tachiyomi/ui/manga/track/TrackInfoDialog.kt").readText()

    @Test
    fun localChapterCellDoesNotReuseDetailsEditor() {
        val local = home.substringAfter("private fun LocalTrackerInfoItem(")
            .substringBefore("@Composable\nprivate fun TrackDetailsPanel")
        assertTrue(
            "onChaptersClick = work.lastChapterNumber?.let" in local,
            "local chapter progress must not open the score/details editor",
        )
    }

    @Test
    fun localDateSelectorOwnsTheDetailsDialogBranch() {
        val selector = dialogs.substringAfter("fun LocalTrackDetailsDialog(")
            .substringBefore("private enum class LocalDateField")
        assertTrue("if (dateField != null)" in selector)
        assertTrue("} else {" in selector)
        assertTrue(selector.indexOf("if (dateField != null)") < selector.indexOf("AlertDialog("))
    }

    @Test
    fun partialExternalReconciliationKeepsTheDialogOpenForRetry() {
        val export = screenModel.substringAfter("fun exportLocalMetadata(")
            .substringBefore("fun saveLocalDetails(")
        assertTrue(
            !export.substringBefore("runLocalTrackingAction").contains("dismissLocalReconciliationDialog()"),
            "reconciliation must remain open while remote writes are attempted",
        )
        assertTrue("if (failedFields.isNotEmpty())" in export)
        assertTrue("pendingExternalWrites" in export)
        assertTrue("dismissLocalReconciliationDialog()" in export.substringAfter("if (failedFields.isNotEmpty())"))
        assertTrue(
            export.indexOf("dismissLocalReconciliationDialog()") > export.indexOf("else {"),
            "successful reconciliation should close only after all writes succeed",
        )
    }

    @Test
    fun partialExternalReconciliationUsesTheSharedRetryPolicyOwner() {
        val export = screenModel.substringAfter("fun exportLocalMetadata(")
            .substringBefore("fun saveLocalDetails(")
        assertTrue("LocalTrackingReconciliationPolicy.fieldsToAttempt" in export)
        assertTrue("LocalTrackingReconciliationPolicy.completedFieldCount" in export)
    }
}

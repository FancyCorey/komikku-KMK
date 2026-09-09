package eu.kanade.tachiyomi.ui.reader

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK AUG-06 duplicate-action fencing -->
/**
 * Tests for [ChapterCompletionRatingActionGate], the confirmed-defect fix for AUG-06 (delayed
 * -action feedback / duplicate fencing): the chapter-completion rating prompt's Love/Like/Dislike/
 * Not interested/dismiss actions previously had no guard against a second tap firing a second
 * concurrent write, and a delayed write could resurrect a dialog the user had already dismissed.
 */
class ChapterCompletionRatingActionGateTest {

    private fun pending(mangaId: Long, isProcessing: Boolean = false) =
        ReaderViewModel.Dialog.ChapterCompletionRating(mangaId, isProcessing)

    @Test
    fun `a fresh pending prompt for the same manga may begin`() {
        assertTrue(ChapterCompletionRatingActionGate.canBegin(pending(1L), mangaId = 1L))
    }

    @Test
    fun `a duplicate tap while already processing is fenced`() {
        assertFalse(ChapterCompletionRatingActionGate.canBegin(pending(1L, isProcessing = true), mangaId = 1L))
    }

    @Test
    fun `an action for a different manga id than the shown dialog is fenced`() {
        assertFalse(ChapterCompletionRatingActionGate.canBegin(pending(1L), mangaId = 2L))
    }

    @Test
    fun `no dialog showing means no action may begin`() {
        assertFalse(ChapterCompletionRatingActionGate.canBegin(null, mangaId = 1L))
    }

    @Test
    fun `an unrelated dialog type means no action may begin`() {
        assertFalse(ChapterCompletionRatingActionGate.canBegin(ReaderViewModel.Dialog.Settings, mangaId = 1L))
    }

    @Test
    fun `a write is still pending while its own processing dialog is showing`() {
        assertTrue(ChapterCompletionRatingActionGate.isStillPending(pending(1L, isProcessing = true), mangaId = 1L))
    }

    @Test
    fun `a write is not pending once the dialog was dismissed - resurrection is fenced`() {
        assertFalse(ChapterCompletionRatingActionGate.isStillPending(null, mangaId = 1L))
    }

    @Test
    fun `a write is not pending once a different dialog has already superseded it`() {
        val supersededBy = ReaderViewModel.Dialog.ChapterCompletionRatingGroupOffer(1L, ratingValue = 2, hasConfirmedGroup = false)
        assertFalse(ChapterCompletionRatingActionGate.isStillPending(supersededBy, mangaId = 1L))
    }

    @Test
    fun `a write is not pending if the shown prompt was never marked processing - begin was skipped`() {
        assertFalse(ChapterCompletionRatingActionGate.isStillPending(pending(1L, isProcessing = false), mangaId = 1L))
    }

    @Test
    fun `a write for a stale manga id never resolves against a newer prompt for a different manga`() {
        // Simulates: prompt for manga 1 was resolved and a fresh prompt for manga 2 opened before
        // manga 1's delayed write callback arrives.
        assertFalse(ChapterCompletionRatingActionGate.isStillPending(pending(2L, isProcessing = true), mangaId = 1L))
    }

    @Test
    fun `begin then resolve round trip models the full single-action lifecycle`() {
        var dialog: ReaderViewModel.Dialog? = pending(5L)

        assertTrue(ChapterCompletionRatingActionGate.canBegin(dialog, mangaId = 5L))
        dialog = pending(5L, isProcessing = true)

        assertTrue(ChapterCompletionRatingActionGate.isStillPending(dialog, mangaId = 5L))
        // A second tap during processing must be fenced.
        assertFalse(ChapterCompletionRatingActionGate.canBegin(dialog, mangaId = 5L))
    }
}
// KMK <--

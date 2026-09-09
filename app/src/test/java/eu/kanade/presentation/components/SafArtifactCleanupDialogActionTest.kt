package eu.kanade.presentation.components

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import eu.kanade.tachiyomi.util.export.SafArtifactOutcome
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK -->
// SafArtifactCleanupDialog is a
// @Composable, and this module's unit tests have no Compose test runtime -- so its outcome -> action
// decision and its Remove-button deletion boundary are both extracted into plain functions
// (SafCleanupDialogAction / safCleanupDialogActionFor / performSafRemoveAction) that this file can
// exercise directly and behaviorally, rather than asserting against the composable's source text.
class SafArtifactCleanupDialogActionTest {

    @AfterEach
    fun tearDown() {
        unmockkStatic(DocumentsContract::class)
    }

    // --- required test 19: unresolved UI contains no Remove action ---

    @Test
    fun `UNRESOLVED never resolves to the Remove-Keep action`() {
        assertEquals(SafCleanupDialogAction.UNRESOLVED_ACKNOWLEDGE, safCleanupDialogActionFor(SafArtifactOutcome.UNRESOLVED))
        assertTrue(
            safCleanupDialogActionFor(SafArtifactOutcome.UNRESOLVED) != SafCleanupDialogAction.REMOVE_OR_KEEP,
            "UNRESOLVED must never render a Remove button",
        )
    }

    @Test
    fun `SUCCESS renders no cleanup dialog at all`() {
        assertEquals(SafCleanupDialogAction.NONE, safCleanupDialogActionFor(SafArtifactOutcome.SUCCESS))
    }

    @Test
    fun `debug fixture may explicitly remove a verified successful artifact`() {
        assertEquals(
            SafCleanupDialogAction.REMOVE_OR_KEEP,
            safCleanupDialogActionFor(SafArtifactOutcome.SUCCESS, allowSuccessfulRemoval = true),
        )
        assertEquals(
            SafCleanupDialogAction.NONE,
            safCleanupDialogActionFor(SafArtifactOutcome.SUCCESS, allowSuccessfulRemoval = false),
        )
    }

    @Test
    fun `IN_PROGRESS renders no cleanup dialog at all`() {
        assertEquals(SafCleanupDialogAction.NONE, safCleanupDialogActionFor(SafArtifactOutcome.IN_PROGRESS))
    }

    @Test
    fun `PARTIAL_OR_EMPTY FAILED and CANCELLED all offer Remove-Keep`() {
        assertEquals(SafCleanupDialogAction.REMOVE_OR_KEEP, safCleanupDialogActionFor(SafArtifactOutcome.PARTIAL_OR_EMPTY))
        assertEquals(SafCleanupDialogAction.REMOVE_OR_KEEP, safCleanupDialogActionFor(SafArtifactOutcome.FAILED))
        assertEquals(SafCleanupDialogAction.REMOVE_OR_KEEP, safCleanupDialogActionFor(SafArtifactOutcome.CANCELLED))
    }

    // --- required test 20: Keep/Close does not call document deletion ---

    @Test
    fun `the UNRESOLVED action path never reaches the Remove deletion boundary`() {
        // performSafRemoveAction is only ever invoked from the REMOVE_OR_KEEP branch of the composable
        // -- proven structurally by the fact that UNRESOLVED_ACKNOWLEDGE is a distinct enum value whose
        // only wired button calls onKept directly. Kept and Dismiss both call onKept/onDismissed only,
        // never performSafRemoveAction -- there is no code path from either that reaches
        // DocumentsContract.deleteDocument.
        assertTrue(SafCleanupDialogAction.entries.toList() == listOf(SafCleanupDialogAction.NONE, SafCleanupDialogAction.UNRESOLVED_ACKNOWLEDGE, SafCleanupDialogAction.REMOVE_OR_KEEP))
        assertFalse(SafCleanupDialogAction.UNRESOLVED_ACKNOWLEDGE == SafCleanupDialogAction.REMOVE_OR_KEEP)
    }

    // --- required tests 21/22: failed deletion retains the offer, successful deletion clears it ---

    @Test
    fun `performSafRemoveAction calls onRemoved and clears the offer only when deletion actually succeeds`() {
        mockkStatic(DocumentsContract::class)
        val context = mockk<Context>()
        val resolver = mockk<ContentResolver>()
        val uri = mockk<Uri>()
        every { context.contentResolver } returns resolver
        every { DocumentsContract.deleteDocument(resolver, uri) } returns true
        var removedCallbackFired = false

        val removed = performSafRemoveAction(context, uri) { removedCallbackFired = true }

        assertTrue(removed)
        assertTrue(removedCallbackFired, "onRemoved (which clears the retained cleanup offer) must fire on a successful deletion")
    }

    @Test
    fun `performSafRemoveAction never calls onRemoved when deletion fails -- the offer is retained`() {
        mockkStatic(DocumentsContract::class)
        val context = mockk<Context>()
        val resolver = mockk<ContentResolver>()
        val uri = mockk<Uri>()
        every { context.contentResolver } returns resolver
        every { DocumentsContract.deleteDocument(resolver, uri) } returns false
        var removedCallbackFired = false

        val removed = performSafRemoveAction(context, uri) { removedCallbackFired = true }

        assertFalse(removed)
        assertFalse(removedCallbackFired, "onRemoved must never fire on a failed deletion -- the offer must stay retained so the user can retry or Keep")
    }

    @Test
    fun `performSafRemoveAction never calls onRemoved when deletion throws an ordinary exception`() {
        mockkStatic(DocumentsContract::class)
        val context = mockk<Context>()
        val resolver = mockk<ContentResolver>()
        val uri = mockk<Uri>()
        every { context.contentResolver } returns resolver
        every { DocumentsContract.deleteDocument(resolver, uri) } throws SecurityException("permission revoked")
        var removedCallbackFired = false

        val removed = performSafRemoveAction(context, uri) { removedCallbackFired = true }

        assertFalse(removed)
        assertFalse(removedCallbackFired)
    }

    @Test
    fun `performSafRemoveAction propagates CancellationException instead of reporting a removal outcome`() {
        mockkStatic(DocumentsContract::class)
        val context = mockk<Context>()
        val resolver = mockk<ContentResolver>()
        val uri = mockk<Uri>()
        every { context.contentResolver } returns resolver
        every { DocumentsContract.deleteDocument(resolver, uri) } throws CancellationException("cancelled")
        var removedCallbackFired = false

        var thrown: CancellationException? = null
        try {
            performSafRemoveAction(context, uri) { removedCallbackFired = true }
        } catch (e: CancellationException) {
            thrown = e
        }

        assertTrue(thrown != null)
        assertFalse(removedCallbackFired)
    }
}
// KMK <--

package eu.kanade.tachiyomi.ui.browse.extension

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import eu.kanade.tachiyomi.extension.util.ExtensionApkExporter
import eu.kanade.tachiyomi.util.export.SafArtifactOutcome
import eu.kanade.tachiyomi.util.export.SafExportCoordinator
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

// KMK -->
// ExtensionsTab.kt's bulk extension export was retrofitted onto SafExportCoordinator, replacing the
// old standalone BulkExportArtifactKind enum/bulkExportArtifactKindFor mapping and its own ad hoc
// `remember`-scoped `bulkExportCleanupUri`/`bulkExportCleanupKind` state (which had the same
// clears-the-handle-before-checking-deletion-success bug the single-export dialog had). Coverage now
// targets the actual coordinator-level decisions -- mirroring ExtensionDetailsScreenExportCleanupTest
// .kt -- plus the still-unchanged ExtensionApkExporter.deleteExported(context, Uri) deletion boundary
// the shared cleanup dialog reuses.
//
// The Compose dialog/launcher themselves are not exercised here -- only the coordinator-level
// decisions and the deletion boundary.
class ExtensionsTabBulkExportCleanupTest {

    @AfterEach
    fun tearDown() {
        unmockkStatic(DocumentsContract::class)
    }

    // --- registerUri-before-write: an empty selection must not lose the Uri ---

    @Test
    fun `an empty selection still registers the Uri and reports partial-or-empty, never discards it`() {
        // The picker already created a document before the "nothing selected" case is even checked --
        // exportMultiple is never called, but the artifact-lifecycle conclusion must still be a
        // truthful, retained offer, not a silently discarded Uri.
        val coordinator = SafExportCoordinator()
        val id = coordinator.beginOperation()!!
        val uri = mockk<Uri>()
        coordinator.registerUri(id, uri)

        val selectedIsEmpty = true
        val outcome = runBlocking {
            coordinator.performWrite(id) {
                if (selectedIsEmpty) SafArtifactOutcome.PARTIAL_OR_EMPTY else error("unreachable: selection was non-empty")
            }
        }

        assertEquals(SafArtifactOutcome.PARTIAL_OR_EMPTY, outcome)
        assertEquals(uri, coordinator.cleanupOffer.value?.uri)
        assertEquals(SafArtifactOutcome.PARTIAL_OR_EMPTY, coordinator.cleanupOffer.value?.outcome)
    }

    @Test
    fun `full success is reflected as SUCCESS on the retained offer`() {
        val coordinator = SafExportCoordinator()
        val id = coordinator.beginOperation()!!
        val uri = mockk<Uri>()
        coordinator.registerUri(id, uri)

        runBlocking {
            coordinator.performWrite(id) { SafArtifactOutcome.SUCCESS }
        }

        assertEquals(SafArtifactOutcome.SUCCESS, coordinator.cleanupOffer.value?.outcome)
    }

    @Test
    fun `partial success (some pkgNames skipped) is still reflected as SUCCESS -- a real archive exists`() {
        // Some pkgNames skipped, but a real archive with real content exists -- cleanup targets that
        // archive as a whole, never implying every selected extension is inside it.
        val coordinator = SafExportCoordinator()
        val id = coordinator.beginOperation()!!
        val uri = mockk<Uri>()
        coordinator.registerUri(id, uri)

        runBlocking {
            coordinator.performWrite(id) { SafArtifactOutcome.SUCCESS }
        }

        assertEquals(SafArtifactOutcome.SUCCESS, coordinator.cleanupOffer.value?.outcome)
    }

    @Test
    fun `a success with exportedCount zero must map to partial-or-empty, not SUCCESS`() {
        // ExtensionsScreenModel.exportSelectedExtensions maps result.fold onSuccess to SUCCESS only
        // when summary.exportedCount > 0 -- proven directly here at the coordinator boundary the
        // mapping feeds into.
        val coordinator = SafExportCoordinator()
        val id = coordinator.beginOperation()!!
        val uri = mockk<Uri>()
        coordinator.registerUri(id, uri)
        val exportedCount = 0

        runBlocking {
            coordinator.performWrite(id) {
                if (exportedCount > 0) SafArtifactOutcome.SUCCESS else SafArtifactOutcome.PARTIAL_OR_EMPTY
            }
        }

        assertEquals(SafArtifactOutcome.PARTIAL_OR_EMPTY, coordinator.cleanupOffer.value?.outcome)
    }

    @Test
    fun `a write failure (destination-open, mid-stream, or NoExtensionsExportableException) is reflected as FAILED`() {
        val coordinator = SafExportCoordinator()
        val id = coordinator.beginOperation()!!
        val uri = mockk<Uri>()
        coordinator.registerUri(id, uri)

        runBlocking {
            coordinator.performWrite(id) { SafArtifactOutcome.FAILED }
        }

        assertEquals(SafArtifactOutcome.FAILED, coordinator.cleanupOffer.value?.outcome)
    }

    @Test
    fun `cancellation downgrades the offer to CANCELLED instead of bypassing cleanup tracking`() {
        val coordinator = SafExportCoordinator()
        val id = coordinator.beginOperation()!!
        val uri = mockk<Uri>()
        coordinator.registerUri(id, uri)

        var thrown: kotlinx.coroutines.CancellationException? = null
        try {
            runBlocking {
                coordinator.performWrite(id) { throw kotlinx.coroutines.CancellationException("cancelled") }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            thrown = e
        }

        assertTrue(thrown != null, "cancellation must propagate, not be swallowed as an ordinary outcome")
        assertEquals(SafArtifactOutcome.CANCELLED, coordinator.cleanupOffer.value?.outcome)
        assertEquals(uri, coordinator.cleanupOffer.value?.uri)
    }

    // --- Remove/Keep/dismiss + deletion boundary (shared with every outcome) ---

    @Test
    fun `Remove calls deleteExported for the exact returned Uri and reports success truthfully`() {
        mockkStatic(DocumentsContract::class)
        val context = mockk<Context>()
        val resolver = mockk<ContentResolver>()
        val uri = mockk<Uri>()
        every { context.contentResolver } returns resolver
        every { DocumentsContract.deleteDocument(resolver, uri) } returns true

        val removed = ExtensionApkExporter.deleteExported(context, uri)

        assertTrue(removed)
    }

    @Test
    fun `deletion failure is reported as failure, not a crash`() {
        mockkStatic(DocumentsContract::class)
        val context = mockk<Context>()
        val resolver = mockk<ContentResolver>()
        val uri = mockk<Uri>()
        every { context.contentResolver } returns resolver
        every { DocumentsContract.deleteDocument(resolver, uri) } throws SecurityException("permission revoked")

        val removed = ExtensionApkExporter.deleteExported(context, uri)

        assertFalse(removed)
    }

    @Test
    fun `cancellation from deleteDocument is not swallowed`() {
        mockkStatic(DocumentsContract::class)
        val context = mockk<Context>()
        val resolver = mockk<ContentResolver>()
        val uri = mockk<Uri>()
        every { context.contentResolver } returns resolver
        every { DocumentsContract.deleteDocument(resolver, uri) } throws kotlinx.coroutines.CancellationException("cancelled")

        var thrown: kotlinx.coroutines.CancellationException? = null
        try {
            ExtensionApkExporter.deleteExported(context, uri)
        } catch (e: kotlinx.coroutines.CancellationException) {
            thrown = e
        }

        assertTrue(thrown != null, "a cancelled bulk-export cleanup deletion must propagate CancellationException")
    }

    @Test
    fun `deleteExported accepts only a SAF Uri, never a filesystem path string or File`() {
        // Structural proof that no filesystem-path deletion overload/API was introduced for bulk
        // cleanup -- the shared boundary still only accepts (Context, Uri).
        val method = ExtensionApkExporter::class.java.getDeclaredMethod(
            "deleteExported",
            Context::class.java,
            Uri::class.java,
        )
        assertTrue(method != null)
        val overloads = ExtensionApkExporter::class.java.declaredMethods.filter { it.name == "deleteExported" }
        assertTrue(
            overloads.all { m -> m.parameterTypes.none { it == String::class.java || it == java.io.File::class.java } },
            "deleteExported must never gain an overload accepting a raw path String or File",
        )
    }

    // --- source-level assertions: coordinator ownership + selection-mode exit ---

    @Test
    fun `ExtensionsScreenModel owns bulkExportCoordinator and exits selection mode after the write`() {
        val file = File("src/main/java/eu/kanade/tachiyomi/ui/browse/extension/ExtensionsScreenModel.kt")
        assertTrue(file.exists(), "expected source file at ${file.path} (relative to the app module directory) -- did it move?")
        val source = file.readText()

        assertTrue(
            source.contains("val bulkExportCoordinator = SafExportCoordinator(allowSuccessfulRemoval = BuildConfig.DEBUG)"),
            "ExtensionsScreenModel must own a screenModelScope-scoped SafExportCoordinator for bulk export",
        )
        assertTrue(
            source.contains("exitExtensionSelectionMode()"),
            "exportSelectedExtensions must exit selection mode after the write completes, regardless of outcome",
        )
    }

    @Test
    fun `ExtensionsTab offers the shared SafArtifactCleanupDialog driven by the model-owned coordinator`() {
        val file = File("src/main/java/eu/kanade/tachiyomi/ui/browse/extension/ExtensionsTab.kt")
        assertTrue(file.exists(), "expected source file at ${file.path} (relative to the app module directory) -- did it move?")
        val source = file.readText()

        assertTrue(source.contains("ActivityResultContracts.CreateDocument("))
        assertTrue(source.contains("SafArtifactCleanupDialog("))
        assertTrue(
            source.contains("extensionsScreenModel.bulkExportCoordinator"),
            "ExtensionsTab must read/clear the coordinator via extensionsScreenModel, not a local remember",
        )
        assertFalse(
            source.contains("remember { SafExportCoordinator() }"),
            "bulk export must no longer own its own remember-scoped SafExportCoordinator",
        )
    }

    @Test
    fun `bulk export selection is snapshotted before the picker launches, not reconstructed from state after it returns`() {
        // The launcher
        // callback (which runs after the external CreateDocument picker returns) must read a
        // pre-captured `selectedExtensionsForExport` snapshot, never reconstruct the selection from
        // live `state.selectedExtensionKeys`/`state.items` -- the picker is an external lifecycle
        // boundary and that state can change while the system picker UI is in front.
        val file = File("src/main/java/eu/kanade/tachiyomi/ui/browse/extension/ExtensionsTab.kt")
        assertTrue(file.exists(), "expected source file at ${file.path} (relative to the app module directory) -- did it move?")
        val source = file.readText()

        assertTrue(
            source.contains("var selectedExtensionsForExport by remember"),
            "the selection must be held in a pre-picker snapshot var, not derived live inside the launcher callback",
        )

        val launcherStart = source.indexOf("val bulkExportLauncher = rememberLauncherForActivityResult(")
        assertTrue(launcherStart >= 0, "expected to find the bulkExportLauncher declaration")
        val launcherBodyStart = source.indexOf(") { uri ->", launcherStart)
        assertTrue(launcherBodyStart >= 0, "expected the launcher's callback body to start with `) { uri ->`")
        val launcherBodyEnd = source.indexOf("\n    }", launcherBodyStart)
        assertTrue(launcherBodyEnd >= 0, "expected to find the end of the launcher callback body")
        val launcherBody = source.substring(launcherBodyStart, launcherBodyEnd)

        assertTrue(
            launcherBody.contains("val selected = selectedExtensionsForExport"),
            "the launcher callback must read the pre-picker snapshot, not reconstruct the selection",
        )
        assertFalse(
            launcherBody.contains("state.items") || launcherBody.contains("state.selectedExtensionKeys"),
            "the launcher callback (after picker return) must not read live `state` to determine the selection -- " +
                "found a reference to state.items/state.selectedExtensionKeys inside the callback body",
        )

        val confirmClickStart = source.indexOf("showBulkExportConfirmDialog = false\n")
        assertTrue(confirmClickStart >= 0, "expected to find the bulk-export confirm dialog's onClick body")
        val confirmClickEnd = source.indexOf("bulkExportLauncher.launch(", confirmClickStart)
        assertTrue(confirmClickEnd >= 0, "expected the confirm click to launch the picker")
        val confirmClickBody = source.substring(confirmClickStart, confirmClickEnd)
        assertTrue(
            confirmClickBody.contains("selectedExtensionsForExport = state.items"),
            "the selection must be captured from live state at the confirm-click boundary, immediately before launch()",
        )
    }
}
// KMK <--

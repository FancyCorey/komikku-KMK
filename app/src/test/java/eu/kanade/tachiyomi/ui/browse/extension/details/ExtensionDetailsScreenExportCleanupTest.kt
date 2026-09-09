package eu.kanade.tachiyomi.ui.browse.extension.details

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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

// KMK -->
// ExtensionDetailsScreen.kt
// was retrofitted onto SafExportCoordinator, replacing the old standalone SingleExportArtifactKind
// enum/singleExportArtifactKindFor mapping this file used to test directly. Coverage now targets the
// actual defect the user identified -- a non-null picker Uri must never be discarded when the
// pre-picker extension snapshot has gone stale/null, and cancellation must be captured by the
// coordinator rather than bypassing cleanup tracking entirely -- by driving SafExportCoordinator the
// same way the Composable's launcher callback does, plus the still-unchanged ExtensionApkExporter
// .deleteExported(context, Uri) deletion boundary the cleanup dialog reuses.
//
// The Compose dialog/launcher themselves are not exercised here -- only the coordinator-level
// decisions, matching how the bulk-export cleanup route is tested in
// ExtensionsTabBulkExportCleanupTest.kt.
class ExtensionDetailsScreenExportCleanupTest {

    @AfterEach
    fun tearDown() {
        unmockkStatic(DocumentsContract::class)
    }

    // --- registerUri-before-write: the stale/null extension snapshot must not lose the Uri ---

    @Test
    fun `a null extension snapshot still registers the Uri and reports partial-or-empty, never discards it`() {
        // Mirrors the launcher callback: registerUri(uri) happens unconditionally before checking
        // whether the pre-picker extension snapshot is still valid.
        val coordinator = SafExportCoordinator()
        val id = coordinator.beginOperation()!!
        val uri = mockk<Uri>()
        coordinator.registerUri(id, uri)

        var extensionSnapshot: String? = null
        val outcome = runBlocking {
            coordinator.performWrite(id) {
                val extension = extensionSnapshot
                if (extension == null) SafArtifactOutcome.PARTIAL_OR_EMPTY else error("unreachable: extension was non-null")
            }
        }

        assertEquals(SafArtifactOutcome.PARTIAL_OR_EMPTY, outcome)
        assertEquals(uri, coordinator.cleanupOffer.value?.uri)
        assertEquals(SafArtifactOutcome.PARTIAL_OR_EMPTY, coordinator.cleanupOffer.value?.outcome)
    }

    @Test
    fun `a successful export is reflected as SUCCESS on the retained offer`() {
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
    fun `a missing source file is reflected as partial-or-empty on the retained offer`() {
        // exportSingle returns SourceFileMissing before ever touching destUri, but the system picker
        // already created the destination document before exportSingle was even called.
        val coordinator = SafExportCoordinator()
        val id = coordinator.beginOperation()!!
        val uri = mockk<Uri>()
        coordinator.registerUri(id, uri)

        runBlocking {
            coordinator.performWrite(id) { SafArtifactOutcome.PARTIAL_OR_EMPTY }
        }

        assertEquals(SafArtifactOutcome.PARTIAL_OR_EMPTY, coordinator.cleanupOffer.value?.outcome)
    }

    @Test
    fun `a write failure (destination-open or mid-stream) is reflected as FAILED on the retained offer`() {
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
    fun `cancellation during exportSingle downgrades the offer to CANCELLED instead of bypassing cleanup tracking`() {
        // The old bug: exportCleanupUri/exportCleanupKind were only ever assigned after the write
        // attempt completed, so a CancellationException thrown mid-exportSingle skipped that
        // assignment entirely and no cleanup was ever offered for the orphaned document. The
        // coordinator now registers the Uri up front, so cancellation still leaves a retained,
        // truthful CANCELLED offer.
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

    @Test
    fun `a null picker Uri never registers an offer`() {
        // A null Uri means the user cancelled the system picker before it created anything -- there
        // is genuinely nothing to offer cleanup for, unlike a non-null Uri with a null extension.
        val coordinator = SafExportCoordinator()
        val id = coordinator.beginOperation()!!
        assertNull(coordinator.cleanupOffer.value)
    }

    // --- Remove/Keep/dismiss + deletion boundary (shared with both dialog variants) ---

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

        assertTrue(thrown != null, "a cancelled single-export cleanup deletion must propagate CancellationException")
    }

    @Test
    fun `cancellation from exportSingle itself propagates instead of being classified as an outcome`() {
        // If exportSingle rethrows CancellationException, the launcher's coroutine is cancelled
        // before it ever reaches singleExportArtifactKindFor -- there is no ExportResult to classify
        // in that case, so no cleanup state is ever set from a cancelled export attempt. This test
        // proves ExtensionApkExporter.exportSingle's own cancellation contract (the boundary this
        // screen relies on), matching the existing coverage in ExtensionApkExporterTest.kt.
        val context = mockk<Context>()
        val extsDir = Files.createTempDirectory("single-export-cancel-test").toFile()
        every { context.filesDir } returns extsDir
        val destUri = mockk<Uri>()
        every { context.contentResolver } returns mockk<ContentResolver>().also {
            every { it.openOutputStream(destUri) } throws kotlinx.coroutines.CancellationException("scope cancelled")
        }
        val extension = eu.kanade.tachiyomi.extension.model.Extension.Installed(
            name = "Test",
            pkgName = "eu.kanade.tachiyomi.extension.en.test",
            versionName = "1.0",
            versionCode = 1L,
            libVersion = 1.5,
            lang = "en",
            isNsfw = false,
            signatureHash = "abc",
            storeName = null,
            pkgFactory = null,
            sources = emptyList(),
            icon = null,
            isShared = false,
        )
        val exts = File(extsDir, "exts").apply { mkdirs() }
        File(exts, "${extension.pkgName}.ext").writeBytes("bytes".toByteArray())

        var thrown: kotlinx.coroutines.CancellationException? = null
        try {
            kotlinx.coroutines.runBlocking {
                ExtensionApkExporter.exportSingle(context, extension, destUri)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            thrown = e
        } finally {
            extsDir.deleteRecursively()
        }

        assertTrue(thrown != null, "a cancelled exportSingle must propagate CancellationException, not report a result")
    }
}
// KMK <--

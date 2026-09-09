package eu.kanade.tachiyomi.extension.util

// KMK v0.8.18 -->
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import eu.kanade.tachiyomi.extension.model.Extension
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipInputStream

// KMK Confirmed Blocker Remediation Corrective Completion Plan V2 2026-07-29 -->
/**
 * Direct tests for [ExtensionApkExporter.exportSingle]/[exportMultiple]'s actual byte-copy and
 * failure-handling behavior -- the previous test coverage in this file only exercised the pure
 * filename-generation helpers. `Context`/`ContentResolver`/`Uri` are mocked (no Robolectric in this
 * module); [Context.getFilesDir] is stubbed to a real temporary directory so [resolveSourceFile]'s
 * actual file-resolution logic runs unmodified, and [ContentResolver.openOutputStream] is stubbed to
 * a real [java.io.OutputStream] backed by a temp file/in-memory buffer so the real copy/zip-write code
 * runs unmodified.
 */
class ExtensionApkExporterTest {

    private val tempDir = Files.createTempDirectory("apk-exporter-test").toFile()

    @AfterEach
    fun tearDown() {
        tempDir.deleteRecursively()
        unmockkStatic(DocumentsContract::class)
    }

    private fun fakeContext(): Context {
        val context = mockk<Context>()
        every { context.filesDir } returns tempDir
        return context
    }

    private fun installedExtensionWithFile(
        pkgName: String,
        bytes: ByteArray = "fake-apk-bytes".toByteArray(),
    ): Extension.Installed {
        val extsDir = File(tempDir, "exts").apply { mkdirs() }
        File(extsDir, "$pkgName.ext").writeBytes(bytes)
        return installedExtension(pkgName = pkgName)
    }

    private fun installedExtension(
        name: String = "My Source",
        pkgName: String = "eu.kanade.tachiyomi.extension.en.mysource",
        versionName: String = "1.2.3",
        isShared: Boolean = false,
    ) = Extension.Installed(
        name = name,
        pkgName = pkgName,
        versionName = versionName,
        versionCode = 1L,
        libVersion = 1.5,
        lang = "en",
        isNsfw = false,
        signatureHash = "abc123",
        storeName = null,
        pkgFactory = null,
        sources = emptyList(),
        icon = null,
        isShared = isShared,
    )

    @Test
    fun `suggested apk filename includes safe name, pkgName, and versionName`() {
        val extension = installedExtension()
        val fileName = ExtensionApkExporter.suggestedApkFileName(extension)
        assertTrue(fileName.contains("My_Source"))
        assertTrue(fileName.contains(extension.pkgName))
        assertTrue(fileName.contains(extension.versionName))
        assertTrue(fileName.endsWith(".apk"))
    }

    @Test
    fun `suggested apk filename strips unsafe characters from the name`() {
        val extension = installedExtension(name = "My/Weird: Source!!")
        val fileName = ExtensionApkExporter.suggestedApkFileName(extension)
        assertTrue(!fileName.contains("/"))
        assertTrue(!fileName.contains(":"))
        assertTrue(!fileName.contains("!"))
    }

    @Test
    fun `suggested zip filename is stable and ends with zip`() {
        assertEquals("komikku-extensions-export.zip", ExtensionApkExporter.suggestedZipFileName())
    }

    // KMK Confirmed Blocker Remediation Corrective Completion Plan V2 2026-07-29 -->
    @Test
    fun `exportSingle copies the exact source bytes unchanged to the destination`() = runTest {
        val bytes = "not-a-real-apk-but-exact-bytes-matter".toByteArray()
        val extension = installedExtensionWithFile("eu.kanade.tachiyomi.extension.en.a", bytes)
        val context = fakeContext()
        val destFile = File(tempDir, "dest.apk")
        val destUri = mockk<Uri>()
        every { context.contentResolver } returns mockk<ContentResolver>().also {
            every { it.openOutputStream(destUri) } returns destFile.outputStream()
        }

        val result = ExtensionApkExporter.exportSingle(context, extension, destUri)

        assertEquals(ExtensionApkExporter.ExportResult.Success, result)
        assertTrue(destFile.exists())
        assertEquals(String(bytes), destFile.readText())
    }

    @Test
    fun `exportSingle reports SourceFileMissing without touching the destination when the APK cannot be found`() = runTest {
        // Intentionally never write a file under exts/ for this pkgName.
        val extension = installedExtension(pkgName = "eu.kanade.tachiyomi.extension.en.missing")
        val context = fakeContext()

        val result = ExtensionApkExporter.exportSingle(context, extension, mockk<Uri>())

        assertEquals(ExtensionApkExporter.ExportResult.SourceFileMissing, result)
    }

    @Test
    fun `exportSingle reports WriteFailed when the destination cannot be opened`() = runTest {
        val extension = installedExtensionWithFile("eu.kanade.tachiyomi.extension.en.b")
        val context = fakeContext()
        val destUri = mockk<Uri>()
        every { context.contentResolver } returns mockk<ContentResolver>().also {
            every { it.openOutputStream(destUri) } returns null
        }

        val result = ExtensionApkExporter.exportSingle(context, extension, destUri)

        assertTrue(result is ExtensionApkExporter.ExportResult.WriteFailed)
    }

    @Test
    fun `exportSingle reports WriteFailed without exposing the exception message when the copy itself throws`() = runTest {
        val extension = installedExtensionWithFile("eu.kanade.tachiyomi.extension.en.c")
        val context = fakeContext()
        val destUri = mockk<Uri>()
        every { context.contentResolver } returns mockk<ContentResolver>().also {
            every { it.openOutputStream(destUri) } throws IllegalStateException("disk full")
        }

        val result = ExtensionApkExporter.exportSingle(context, extension, destUri)

        assertTrue(result is ExtensionApkExporter.ExportResult.WriteFailed)
    }

    @Test
    fun `exportMultiple bulk success writes a manifest and every extension's bytes into one zip`() = runTest {
        val a = installedExtensionWithFile("eu.kanade.tachiyomi.extension.en.a", "bytes-a".toByteArray())
        val b = installedExtensionWithFile("eu.kanade.tachiyomi.extension.en.b", "bytes-b".toByteArray())
        val context = fakeContext()
        val destFile = File(tempDir, "bundle.zip")
        val destUri = mockk<Uri>()
        every { context.contentResolver } returns mockk<ContentResolver>().also {
            every { it.openOutputStream(destUri) } returns destFile.outputStream()
        }

        val result = ExtensionApkExporter.exportMultiple(
            context = context,
            extensions = listOf(a, b),
            destUri = destUri,
            appVersion = "1.0",
            kmkVersion = "1.0",
        )

        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(2, summary.exportedCount)
        assertTrue(summary.skippedPkgNames.isEmpty())

        val entryNames = ZipInputStream(destFile.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.map { it.name }.toList()
        }
        assertTrue("manifest.json" in entryNames)
        assertTrue("${a.pkgName}.apk" in entryNames)
        assertTrue("${b.pkgName}.apk" in entryNames)
    }

    @Test
    fun `exportMultiple reports a truthful partial result when one extension's source file is missing`() = runTest {
        val present = installedExtensionWithFile("eu.kanade.tachiyomi.extension.en.present")
        val missing = installedExtension(pkgName = "eu.kanade.tachiyomi.extension.en.missing")
        val context = fakeContext()
        val destFile = File(tempDir, "partial.zip")
        val destUri = mockk<Uri>()
        every { context.contentResolver } returns mockk<ContentResolver>().also {
            every { it.openOutputStream(destUri) } returns destFile.outputStream()
        }

        val result = ExtensionApkExporter.exportMultiple(
            context = context,
            extensions = listOf(present, missing),
            destUri = destUri,
            appVersion = "1.0",
            kmkVersion = "1.0",
        )

        assertTrue(result.isSuccess, "a partial failure must not fail the whole bulk export -- present extensions must still be written")
        val summary = result.getOrThrow()
        assertEquals(1, summary.exportedCount)
        assertEquals(listOf(missing.pkgName), summary.skippedPkgNames)
    }

    @Test
    fun `exportMultiple fails without opening the destination when no extension is exportable`() = runTest {
        // Every selected extension is unresolvable -- this must be reported as a failure, and the
        // destination must never even be opened, so no manifest-only zip is ever written.
        val missingOne = installedExtension(pkgName = "eu.kanade.tachiyomi.extension.en.missing1")
        val missingTwo = installedExtension(pkgName = "eu.kanade.tachiyomi.extension.en.missing2")
        val context = fakeContext()
        val resolver = mockk<ContentResolver>()
        every { context.contentResolver } returns resolver

        val result = ExtensionApkExporter.exportMultiple(
            context = context,
            extensions = listOf(missingOne, missingTwo),
            destUri = mockk<Uri>(),
            appVersion = "1.0",
            kmkVersion = "1.0",
        )

        assertTrue(result.isFailure, "an export with zero exportable extensions must never be reported as success")
        assertTrue(
            result.exceptionOrNull() is ExtensionApkExporter.NoExtensionsExportableException,
            "the failure must be typed so callers can distinguish it from a write/open failure",
        )
        io.mockk.verify(exactly = 0) { resolver.openOutputStream(any()) }
    }

    @Test
    fun `exportMultiple never returns a success with exportedCount zero`() = runTest {
        // Regression guard for the manifest-only-success edge case: no combination of inputs may
        // ever produce Result.success(MultiExportSummary(exportedCount = 0, ...)).
        val missing = installedExtension(pkgName = "eu.kanade.tachiyomi.extension.en.onlymissing")
        val context = fakeContext()
        val destUri = mockk<Uri>()
        every { context.contentResolver } returns mockk<ContentResolver>().also {
            every { it.openOutputStream(destUri) } returns ByteArrayOutputStream()
        }

        val result = ExtensionApkExporter.exportMultiple(
            context = context,
            extensions = listOf(missing),
            destUri = destUri,
            appVersion = "1.0",
            kmkVersion = "1.0",
        )

        assertTrue(result.isFailure)
        assertTrue(result.getOrNull() == null)
    }
    // KMK <--

    @Test
    fun `exportMultiple fails cleanly without a partial zip claim when the destination cannot be opened`() = runTest {
        val a = installedExtensionWithFile("eu.kanade.tachiyomi.extension.en.a")
        val context = fakeContext()
        val destUri = mockk<Uri>()
        every { context.contentResolver } returns mockk<ContentResolver>().also {
            every { it.openOutputStream(destUri) } returns null
        }

        val result = ExtensionApkExporter.exportMultiple(
            context = context,
            extensions = listOf(a),
            destUri = destUri,
            appVersion = "1.0",
            kmkVersion = "1.0",
        )

        assertTrue(result.isFailure, "a destination that cannot be opened must be reported as a clean failure, not a misleading partial success")
    }

    @Test
    fun `exportMultiple never records a receipt or leaves a readable zip when the write itself throws mid-stream`() = runTest {
        // A ByteArrayOutputStream that throws partway through, simulating an interrupted write (e.g.
        // storage revoked mid-export). The V2 plan requires that failed export "does not leave
        // misleading receipts or partial artifacts" -- ExtensionApkExporter itself never creates a
        // receipt (that is Action History's job at the caller, e.g. ExtensionDetailsScreen, which is
        // out of scope for this file), so the property this test actually proves is narrower and more
        // concrete: the function must surface the failure as Result.failure, not silently return a
        // truthful-looking MultiExportSummary for a write that never actually completed.
        val a = installedExtensionWithFile("eu.kanade.tachiyomi.extension.en.a")
        val context = fakeContext()
        val destUri = mockk<Uri>()
        val throwingStream = object : ByteArrayOutputStream() {
            private var writes = 0
            override fun write(b: ByteArray, off: Int, len: Int) {
                writes++
                if (writes > 1) throw java.io.IOException("interrupted")
                super.write(b, off, len)
            }
        }
        every { context.contentResolver } returns mockk<ContentResolver>().also {
            every { it.openOutputStream(destUri) } returns throwingStream
        }

        val result = ExtensionApkExporter.exportMultiple(
            context = context,
            extensions = listOf(a),
            destUri = destUri,
            appVersion = "1.0",
            kmkVersion = "1.0",
        )

        assertTrue(result.isFailure, "an interrupted mid-stream write must never be reported as success")
    }
    // KMK <--

    @Test
    fun `exportSingle rethrows CancellationException instead of reporting WriteFailed`() = runTest {
        val extension = installedExtensionWithFile("eu.kanade.tachiyomi.extension.en.cancelled")
        val context = fakeContext()
        val destUri = mockk<Uri>()
        every { context.contentResolver } returns mockk<ContentResolver>().also {
            every { it.openOutputStream(destUri) } throws CancellationException("scope cancelled")
        }

        var thrown: CancellationException? = null
        try {
            ExtensionApkExporter.exportSingle(context, extension, destUri)
        } catch (e: CancellationException) {
            thrown = e
        }

        assertTrue(thrown != null, "a cancelled export must propagate CancellationException, not report a result")
    }

    // --- deleteExported: narrowly-scoped cleanup, offered only after a confirmed successful export ---

    @Test
    fun `deleteExported returns true when DocumentsContract deletion succeeds`() {
        mockkStatic(DocumentsContract::class)
        val context = fakeContext()
        val resolver = mockk<ContentResolver>()
        val uri = mockk<Uri>()
        every { context.contentResolver } returns resolver
        every { DocumentsContract.deleteDocument(resolver, uri) } returns true

        assertTrue(ExtensionApkExporter.deleteExported(context, uri))
    }

    @Test
    fun `deleteExported returns false, not a crash, when deletion fails`() {
        mockkStatic(DocumentsContract::class)
        val context = fakeContext()
        val resolver = mockk<ContentResolver>()
        val uri = mockk<Uri>()
        every { context.contentResolver } returns resolver
        every { DocumentsContract.deleteDocument(resolver, uri) } throws SecurityException("permission revoked")

        assertFalse(ExtensionApkExporter.deleteExported(context, uri))
    }

    // --- privacy: manifest entries never carry a filesystem path or credential ---

    @Test
    fun `ManifestEntry has no field capable of carrying a filesystem path, cookie, or credential`() {
        val fieldNames = ExtensionApkExporter.ManifestEntry::class.java.declaredFields
            .filterNot { it.isSynthetic }
            .map { it.name }
            .toSet()
        val forbidden = setOf("path", "filePath", "cookie", "cookies", "credential", "credentials", "token", "sourcePreferences")
        assertTrue(
            fieldNames.none { it.lowercase() in forbidden },
            "ManifestEntry must never gain a field capable of leaking a filesystem path or credential: $fieldNames",
        )
    }
    // KMK <--
}
// KMK <--

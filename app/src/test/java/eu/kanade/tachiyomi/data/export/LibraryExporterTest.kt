package eu.kanade.tachiyomi.data.export

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import java.io.ByteArrayOutputStream

// KMK -->
// Direct coverage for the real defect
// this pass fixed -- `exportToCsv` previously called its `onExportComplete` success callback
// unconditionally, including when `openOutputStream` returned null (a genuine destination-open
// failure), reporting "library exported" for a write that never happened. Now returns a typed
// ExportOutcome the caller must inspect instead.
class LibraryExporterTest {

    private fun manga(title: String) = mockk<Manga> {
        every { this@mockk.title } returns title
        every { author } returns null
        every { artist } returns null
    }

    private fun options() = LibraryExporter.ExportOptions(includeTitle = true, includeAuthor = false, includeArtist = false)

    @Test
    fun `a successful open and write reports Success`() = runTest {
        val context = mockk<Context>()
        val resolver = mockk<ContentResolver>()
        val uri = mockk<Uri>()
        val out = ByteArrayOutputStream()
        every { context.contentResolver } returns resolver
        every { resolver.openOutputStream(uri) } returns out

        val result = LibraryExporter.exportToCsv(context, uri, listOf(manga("Test")), options())

        assertSame(LibraryExporter.ExportOutcome.Success, result)
        assertTrue(out.toByteArray().isNotEmpty(), "the CSV bytes must actually have been written")
    }

    @Test
    fun `a destination-open failure reports WriteFailed, not Success`() = runTest {
        // This is the exact bug this pass fixed: previously the caller's success callback fired
        // unconditionally even when openOutputStream(uri) returned null.
        val context = mockk<Context>()
        val resolver = mockk<ContentResolver>()
        val uri = mockk<Uri>()
        every { context.contentResolver } returns resolver
        every { resolver.openOutputStream(uri) } returns null

        val result = LibraryExporter.exportToCsv(context, uri, listOf(manga("Test")), options())

        assertSame(LibraryExporter.ExportOutcome.WriteFailed, result)
    }

    @Test
    fun `a mid-stream write exception reports WriteFailed, not a crash`() = runTest {
        val context = mockk<Context>()
        val resolver = mockk<ContentResolver>()
        val uri = mockk<Uri>()
        val throwingStream = object : ByteArrayOutputStream() {
            override fun write(b: ByteArray, off: Int, len: Int) {
                throw java.io.IOException("disk full")
            }
        }
        every { context.contentResolver } returns resolver
        every { resolver.openOutputStream(uri) } returns throwingStream

        val result = LibraryExporter.exportToCsv(context, uri, listOf(manga("Test")), options())

        assertSame(LibraryExporter.ExportOutcome.WriteFailed, result)
    }

    @Test
    fun `cancellation propagates instead of being reported as an outcome`() = runTest {
        val context = mockk<Context>()
        val resolver = mockk<ContentResolver>()
        val uri = mockk<Uri>()
        every { context.contentResolver } returns resolver
        every { resolver.openOutputStream(uri) } throws CancellationException("scope cancelled")

        var thrown: CancellationException? = null
        try {
            LibraryExporter.exportToCsv(context, uri, listOf(manga("Test")), options())
        } catch (e: CancellationException) {
            thrown = e
        }

        assertTrue(thrown != null, "a cancelled CSV export must propagate CancellationException, not report a result")
    }
}
// KMK <--

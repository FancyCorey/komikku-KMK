package exh.ocr

// KMK --> v0.7.46: tests for the OCR error classifier
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.FileNotFoundException
import java.io.IOException

class OcrErrorClassifierTest {

    @Test
    fun `CancellationException classifies as Cancelled`() {
        assertEquals(OcrErrorKey.Cancelled, OcrErrorClassifier.classify(CancellationException("cancelled")))
    }

    @Test
    fun `FileNotFoundException classifies as PermissionOrFileAccess`() {
        assertEquals(
            OcrErrorKey.PermissionOrFileAccess,
            OcrErrorClassifier.classify(FileNotFoundException("/data/x.jpg")),
        )
    }

    @Test
    fun `SecurityException classifies as PermissionOrFileAccess`() {
        assertEquals(
            OcrErrorKey.PermissionOrFileAccess,
            OcrErrorClassifier.classify(SecurityException("permission denied")),
        )
    }

    @Test
    fun `OutOfMemoryError classifies as ImageDecode`() {
        assertEquals(OcrErrorKey.ImageDecode, OcrErrorClassifier.classify(OutOfMemoryError("bitmap too large")))
    }

    @Test
    fun `generic IOException classifies as Storage`() {
        assertEquals(OcrErrorKey.Storage, OcrErrorClassifier.classify(IOException("disk error")))
    }

    @Test
    fun `unrecognized exception classifies as Internal`() {
        assertEquals(OcrErrorKey.Internal, OcrErrorClassifier.classify(RuntimeException("something else")))
    }

    @Test
    fun `classifyToStorageKey returns the stable storage key string`() {
        assertEquals("ocr_error_cancelled", OcrErrorClassifier.classifyToStorageKey(CancellationException()))
        assertEquals("ocr_error_storage", OcrErrorClassifier.classifyToStorageKey(IOException()))
        assertEquals("ocr_error_internal", OcrErrorClassifier.classifyToStorageKey(RuntimeException()))
    }

    @Test
    fun `every OcrErrorKey storage key round-trips through fromStorageKey`() {
        for (key in OcrErrorKey.entries) {
            assertEquals(key, OcrErrorKey.fromStorageKey(key.storageKey))
        }
    }

    @Test
    fun `fromStorageKey returns null for unrecognized legacy raw exception text`() {
        // Pre-v0.7.46 rows stored raw e.message text, e.g. a real stack-trace-derived string.
        assertNull(OcrErrorKey.fromStorageKey("java.io.FileNotFoundException: /storage/x.jpg (Permission denied)"))
        assertNull(OcrErrorKey.fromStorageKey("Unknown error"))
        assertNull(OcrErrorKey.fromStorageKey(null))
    }
}
// KMK <--

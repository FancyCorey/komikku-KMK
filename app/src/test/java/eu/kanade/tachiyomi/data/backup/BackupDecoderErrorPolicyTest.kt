package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.protobuf.ProtoBuf
import okio.Buffer
import okio.buffer
import okio.gzip
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipException

// KMK v0.8.10 -->
/**
 * Covers the [BackupDecoderErrorPolicy] fix for the confirmed pre-existing crash in
 * [BackupDecoder.decode]: a truncated/corrupt gzip stream, or a near-empty file, threw a raw,
 * uncaught [EOFException] that the old code's narrower try/catch (protobuf-decode call only) never
 * saw. `decode()` itself takes an Android [android.net.Uri] + [android.content.Context] and cannot be
 * unit-tested directly without Robolectric (not used in this project) -- so this suite instead: (1)
 * drives the *exact same* okio/protobuf pipeline `decode()` uses against real malformed byte arrays
 * to confirm which real exception types are actually thrown (not assumed), and (2) verifies
 * [BackupDecoderErrorPolicy.isMalformedBackupFailure] classifies each of those real exceptions, plus
 * the negative cases, correctly.
 */
class BackupDecoderErrorPolicyTest {

    private val parser = ProtoBuf

    private fun validBackupBytes(): ByteArray {
        val backup = Backup(backupManga = listOf(BackupManga(source = 1L, url = "/manga/1", title = "Test Manga")))
        return parser.encodeToByteArray(Backup.serializer(), backup)
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    // --- Real exception types actually thrown by the decode() pipeline ---

    @Test
    fun `a truncated protobuf byte array throws SerializationException, which the policy classifies as malformed`() {
        val truncated = validBackupBytes().let { it.copyOf(it.size / 2) }
        val thrown = org.junit.jupiter.api.Assertions.assertThrows(SerializationException::class.java) {
            parser.decodeFromByteArray(Backup.serializer(), truncated)
        }
        assertTrue(BackupDecoderErrorPolicy.isMalformedBackupFailure(thrown))
    }

    @Test
    fun `a truncated gzip stream throws an IOException from the EOFException-ZipException family, which the policy classifies as malformed`() {
        val gzipBytes = gzip(validBackupBytes())
        val truncatedGzip = gzipBytes.copyOf(gzipBytes.size / 2)
        val thrown = org.junit.jupiter.api.Assertions.assertThrows(IOException::class.java) {
            val src: okio.Source = Buffer().write(truncatedGzip)
            src.gzip().buffer().use { it.readByteArray() }
        }
        // Confirmed root cause: this is an EOFException (a subtype of IOException), not a
        // SerializationException -- the exact gap the old code missed.
        assertTrue(thrown is EOFException || thrown is ZipException, "expected EOFException or ZipException, got ${thrown::class.qualifiedName}")
        assertTrue(BackupDecoderErrorPolicy.isMalformedBackupFailure(thrown))
    }

    @Test
    fun `a near-empty file's header peek throws EOFException, which the policy classifies as malformed`() {
        val thrown = org.junit.jupiter.api.Assertions.assertThrows(EOFException::class.java) {
            val src = Buffer().write(ByteArray(1))
            src.peek().apply { require(2) }
        }
        assertTrue(BackupDecoderErrorPolicy.isMalformedBackupFailure(thrown))
    }

    @Test
    fun `a complete, valid gzip-then-protobuf backup decodes cleanly through the same pipeline`() {
        val gzipBytes = gzip(validBackupBytes())
        val decompressed = run {
            val src: okio.Source = Buffer().write(gzipBytes)
            src.gzip().buffer().use { it.readByteArray() }
        }
        val decoded = parser.decodeFromByteArray(Backup.serializer(), decompressed)
        assertTrue(decoded.backupManga.size == 1)
    }

    // --- Classification of exception kinds directly ---

    @Test
    fun `a plain IOException -- e_g_ the app's own JSON-detected message -- is never classified as malformed`() {
        // This is exactly BackupDecoder's own "invalid_backup_file_json" throw -- it must propagate
        // with its specific message unchanged, not be relabeled with the generic message.
        assertFalse(BackupDecoderErrorPolicy.isMalformedBackupFailure(IOException("looks like JSON")))
    }

    @Test
    fun `an unrelated RuntimeException is never classified as malformed -- a real bug must not be masked`() {
        assertFalse(BackupDecoderErrorPolicy.isMalformedBackupFailure(IllegalStateException("unexpected null state")))
    }

    @Test
    fun `a NullPointerException is never classified as malformed`() {
        assertFalse(BackupDecoderErrorPolicy.isMalformedBackupFailure(NullPointerException()))
    }

    @Test
    fun `CancellationException is never something BackupDecoder should classify as a malformed backup`() {
        // BackupDecoder.decode() itself never catches CancellationException at all (rethrows it
        // immediately) -- this pins the policy side of that contract: even if it were passed in,
        // it must not read as "malformed backup."
        assertFalse(BackupDecoderErrorPolicy.isMalformedBackupFailure(CancellationException("cancelled")))
    }
}
// KMK <--

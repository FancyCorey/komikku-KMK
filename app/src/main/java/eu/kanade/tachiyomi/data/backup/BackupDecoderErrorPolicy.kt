package eu.kanade.tachiyomi.data.backup

import kotlinx.serialization.SerializationException
import java.io.EOFException
import java.util.zip.ZipException

// KMK v0.8.10 -->
/**
 * Pure classification of failures from [BackupDecoder.decode]'s file-format detection and
 * protobuf-decoding steps, deliberately decoupled from [android.content.Context]/moko string
 * resource lookup so it is directly unit-testable without Robolectric (which this project does not
 * use).
 *
 * ## The bug this fixes
 *
 * `decode()` already caught [SerializationException] (malformed/truncated protobuf) and converted it
 * to the app's friendly "invalid backup" message. But a truncated or corrupted **gzip** stream throws
 * [EOFException] (confirmed directly: `Buffer().gzip().buffer().readByteArray()` on a truncated gzip
 * byte array throws `java.io.EOFException`, not a [SerializationException]) — and a near-empty file
 * throws the same [EOFException] from the earlier `source.peek().apply { require(2) }` header-sniff
 * call. Neither was inside the old try/catch, so a truncated-but-gzip-magic-prefixed backup file, or
 * a 0-/1-byte file, crashed `decode()` with a raw, uncaught [EOFException] instead of the existing
 * invalid-backup failure path.
 *
 * ## The fix
 *
 * This policy decides which failures are "this file isn't a valid/complete backup" (recoverable,
 * user-facing, mapped to the existing generic invalid-backup message) versus genuine unexpected
 * failures that must propagate unchanged so real bugs and real I/O errors are never silently
 * mislabeled as "invalid backup." [BackupDecoder.decode] wraps its entire detection+decode block
 * (not just the protobuf-decode call) in a try/catch that defers to this policy, and explicitly never
 * catches `kotlinx.coroutines.CancellationException`.
 */
object BackupDecoderErrorPolicy {
    /**
     * True when [throwable] is a known "malformed or truncated backup file" failure: corrupt/
     * truncated gzip ([EOFException] or [ZipException]) or malformed/truncated protobuf
     * ([SerializationException]). False for anything else -- including the app's own already-
     * friendly `IOException` for a detected-JSON backup, and any unrelated exception (a real I/O
     * failure, a programmer error, etc.), both of which must propagate unchanged rather than being
     * relabeled as a generic "invalid backup" message.
     */
    fun isMalformedBackupFailure(throwable: Throwable): Boolean = when (throwable) {
        is SerializationException -> true
        is EOFException -> true
        is ZipException -> true
        else -> false
    }
}
// KMK <--

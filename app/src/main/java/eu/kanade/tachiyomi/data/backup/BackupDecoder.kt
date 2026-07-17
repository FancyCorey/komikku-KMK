package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.models.Backup
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.protobuf.ProtoBuf
import okio.buffer
import okio.gzip
import okio.source
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException

class BackupDecoder(
    private val context: Context,
    private val parser: ProtoBuf = Injekt.get(),
) {
    /**
     * Decode a potentially-gzipped backup.
     */
    fun decode(uri: Uri): Backup {
        return context.contentResolver.openInputStream(uri)!!.use { inputStream ->
            val source = inputStream.source().buffer()

            // KMK v0.8.10: the whole detection+decode block (header peek, optional gzip
            // decompression, protobuf decode) is now inside one try/catch -- previously only the
            // protobuf-decode call was guarded, so a truncated/corrupt gzip stream or a near-empty
            // file threw a raw, uncaught EOFException from the peek()/gzip() calls below instead of
            // the existing invalid-backup failure path. See BackupDecoderErrorPolicy's class doc for
            // the confirmed root cause and the exact exception family this now catches.
            try {
                val peeked = source.peek().apply {
                    require(2)
                }
                val id1id2 = peeked.readShort()
                val backupString = when (id1id2.toInt()) {
                    0x1f8b -> source.gzip().buffer() // 0x1f8b is gzip magic bytes
                    MAGIC_JSON_SIGNATURE1, MAGIC_JSON_SIGNATURE2, MAGIC_JSON_SIGNATURE3 -> {
                        throw IOException(context.stringResource(MR.strings.invalid_backup_file_json))
                    }
                    else -> source
                }.use { it.readByteArray() }

                parser.decodeFromByteArray(Backup.serializer(), backupString)
            } catch (e: CancellationException) {
                // Never treat cancellation as a malformed-backup failure.
                throw e
            } catch (e: Exception) {
                // Only a recognized "this file isn't a valid/complete backup" failure is converted
                // to the generic friendly message; anything else (including the IOException with the
                // JSON-specific message just above, a real I/O failure, or an unrelated programmer
                // error) propagates unchanged rather than being silently mislabeled.
                if (BackupDecoderErrorPolicy.isMalformedBackupFailure(e)) {
                    throw IOException(context.stringResource(MR.strings.invalid_backup_file_unknown))
                }
                throw e
            }
        }
    }

    companion object {
        private const val MAGIC_JSON_SIGNATURE1 = 0x7b7d // `{}`
        private const val MAGIC_JSON_SIGNATURE2 = 0x7b22 // `{"`
        private const val MAGIC_JSON_SIGNATURE3 = 0x7b0a // `{\n`
    }
}

package exh.util

import android.app.Application
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import kotlinx.coroutines.CancellationException
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.security.MessageDigest

// KMK Action History: receipt capture is private, bounded, and record-after-success.
data class PendingCustomCoverUndo(
    val entry: CustomCoverUndoEntry,
    val hadPreviousFile: Boolean,
)

object CustomCoverUndoStorage {
    private const val RECEIPT_DIR = "kmk-action-history/custom-covers"

    fun receiptFile(id: String): File {
        require(id.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid custom-cover receipt id" }
        return File(Injekt.get<Application>().noBackupFilesDir, "$RECEIPT_DIR/$id.cover")
    }

    fun rollbackFile(id: String): File {
        require(id.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid custom-cover receipt id" }
        return File(Injekt.get<Application>().noBackupFilesDir, "$RECEIPT_DIR/$id.rollback")
    }

    fun cleanup(entry: CustomCoverUndoEntry) {
        receiptFile(entry.id).delete()
        rollbackFile(entry.id).delete()
    }
}

object CustomCoverUndoRecorder {
    suspend fun build(
        sourcePreferences: SourcePreferences,
        coverCache: CoverCache,
        mangaId: Long,
    ): PendingCustomCoverUndo? = withIOContext {
        val current = coverCache.getCustomCoverFile(mangaId)
        val previousDigest = current.takeIf { it.isFile }?.let(::sha256)
        val entryId = CustomCoverUndoJournal.newId()
        if (previousDigest != null) {
            val receipt = CustomCoverUndoStorage.receiptFile(entryId)
            receipt.parentFile?.mkdirs()
            try {
                current.copyTo(receipt, overwrite = true)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                receipt.delete()
                return@withIOContext null
            }
        }
        PendingCustomCoverUndo(
            entry = CustomCoverUndoEntry(
                id = entryId,
                timestamp = System.currentTimeMillis(),
                mangaId = mangaId,
                previousDigest = previousDigest,
                expectedPostDigest = null,
            ),
            hadPreviousFile = previousDigest != null,
        )
    }

    suspend fun commit(
        pending: PendingCustomCoverUndo?,
        coverCache: CoverCache,
    ) = withIOContext {
        if (pending == null) return@withIOContext
        val current = coverCache.getCustomCoverFile(pending.entry.mangaId)
        val postDigest = current.takeIf { it.isFile }?.let(::sha256)
        CustomCoverUndoJournal.record(pending.entry.copy(expectedPostDigest = postDigest))
    }

    suspend fun restoreForwardFailure(
        pending: PendingCustomCoverUndo?,
        coverCache: CoverCache,
    ) = withIOContext {
        if (pending == null) return@withIOContext
        val current = coverCache.getCustomCoverFile(pending.entry.mangaId)
        if (pending.hadPreviousFile) {
            val receipt = CustomCoverUndoStorage.receiptFile(pending.entry.id)
            if (receipt.isFile) receipt.copyTo(current, overwrite = true)
        } else {
            current.delete()
        }
        CustomCoverUndoStorage.cleanup(pending.entry)
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

class CustomCoverUndoService(
    private val coverCache: CoverCache = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
) {
    suspend fun undo(entryId: String): GroupUndoResult = withIOContext {
        val entry = CustomCoverUndoJournal.snapshot().find { it.id == entryId }
            ?: return@withIOContext GroupUndoResult.FAILED
        if (!entry.reversible) return@withIOContext GroupUndoResult.CONFLICT

        val current = coverCache.getCustomCoverFile(entry.mangaId)
        val currentDigest = current.takeIf { it.isFile }?.let(CustomCoverUndoRecorder::sha256)
        if (currentDigest != entry.expectedPostDigest) return@withIOContext GroupUndoResult.CONFLICT

        val rollback = current.takeIf { it.isFile }?.let {
            CustomCoverUndoStorage.rollbackFile(entry.id).also { file ->
                file.parentFile?.mkdirs()
                it.copyTo(file, overwrite = true)
            }
        }
        try {
            if (entry.previousDigest == null) {
                if (current.isFile && !current.delete()) {
                    return@withIOContext GroupUndoResult.FAILED
                }
            } else {
                val receipt = CustomCoverUndoStorage.receiptFile(entry.id)
                if (!receipt.isFile || CustomCoverUndoRecorder.sha256(receipt) != entry.previousDigest) {
                    return@withIOContext GroupUndoResult.FAILED
                }
                receipt.copyTo(current, overwrite = true)
            }
            if (!updateManga.awaitUpdateCoverLastModified(entry.mangaId)) {
                rollback?.copyTo(current, overwrite = true) ?: current.delete()
                return@withIOContext GroupUndoResult.FAILED
            }
            CustomCoverUndoJournal.removeById(entry.id)
            CustomCoverUndoStorage.cleanup(entry)
            GroupUndoResult.RESTORED
        } catch (e: CancellationException) {
            rollback?.copyTo(current, overwrite = true) ?: current.delete()
            throw e
        } catch (_: Exception) {
            rollback?.copyTo(current, overwrite = true) ?: current.delete()
            GroupUndoResult.FAILED
        } finally {
            rollback?.delete()
        }
    }
}
